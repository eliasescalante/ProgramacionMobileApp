# Informe técnico — Actividad 3

> **Documento histórico.** Describe la versión 3.0 (Retrofit/JSONPlaceholder).
> La versión entregada es la 4.0 con Firebase Authentication y Cloud Firestore;
> el informe vigente es [`INFORME_ACTIVIDAD4.txt`](INFORME_ACTIVIDAD4.txt) y la
> guía rápida es [`README.md`](README.md).


## Recordatorios Contextuales con persistencia local-first y sincronización

**Proyecto:** `API1ELIASESCALANTEPM`
**Versión:** 3.0 (`versionCode 3`)
**Plataforma:** Android, `minSdk 24`, `targetSdk 37`, `compileSdk 37`
**Fecha:** noviembre de 2026

---

## 1. Objetivo

Incorporar al proyecto de recordatorios una arquitectura de datos completa:

1. Preferencias persistentes con **DataStore**.
2. Recordatorios persistentes con **Room** como fuente de verdad.
3. **Repositorio local-first**: funciona sin red y sincroniza después.
4. Comunicación REST con **Retrofit + OkHttp**, con URL configurable y errores
   clasificados.
5. Sincronización **sin duplicados** ante múltiples disparadores.
6. UI funcional que refleje los estados pendiente/confirmado/error.
7. Pruebas, informe e instrucciones de compilación y ejecución.

---

## 2. Estado inicial y estrategia

El proyecto original era una aplicación Compose con recordatorios en memoria:
modelos con `label` y colores en el propio modelo, un repositorio en memoria y
una única pantalla de listado. La compilación base se verificó antes de
modificar nada.

La estrategia fue:

- Mantener el lenguaje visual y la navegación del proyecto.
- Sustituir el repositorio en memoria por capas separadas.
- Conservar el modelo de dominio libre de dependencias de Android.
- Incorporar dependencias con versiones compatibles con AGP 9 y Kotlin 2.2.10.

---

## 3. Arquitectura implementada

### 3.1 Capas

```
Compose (pantallas/componentes)
        │  eventos
        ▼
ViewModel (estado de UI)
        │  casos de uso
        ▼
ReminderRepository (interfaz de dominio)
        │
        ▼
OfflineFirstReminderRepository
   ├── ReminderDao (Room) ............ fuente de verdad de recordatorios
   ├── UserPreferencesDataSource ..... DataStore (preferencias)
   ├── ReminderRemoteDataSource ...... Retrofit/OkHttp
   └── NetworkMonitor ................ conectividad
```

### 3.2 Separación Entity / DTO / Domain

| Tipo | Ubicación | Uso |
| --- | --- | --- |
| `Reminder` | `domain.model` | Modelo que consumen ViewModel y Compose. |
| `ReminderEntity` | `data.local` | Fila de Room; enums como texto. |
| `RemoteReminderDto` | `data.remote.dto` | Respuesta del servicio. |
| `RemoteReminderRequest` | `data.remote.dto` | Cuerpo enviado al servicio. |

Los mapeos son funciones explícitas en `data.mapper.ReminderMappers`; no se usan
anotaciones ni conversores automáticos entre capas.

### 3.3 Inyección de dependencias

`AppContainer` es un contenedor manual con inicialización idempotente y
`synchronized`. Crea:

- Una única `AppDatabase` (`AppDatabase.getInstance`).
- Una única instancia de DataStore (delegate `preferencesDataStore`).
- Un único `Retrofit`/`OkHttpClient`.
- Un único repositorio, que arranca la observación de red.

`ReminderApplication.onCreate()` llama a `AppContainer.init(this)` y está
declarada en el manifest con `android:name=".ReminderApplication"`.

---

## 4. DataStore

### 4.1 Claves y valores

| Clave | Tipo | Predeterminado |
| --- | --- | --- |
| `theme_mode` | String (`SYSTEM`/`LIGHT`/`DARK`) | `SYSTEM` |
| `show_completed` | Boolean | `true` |
| `sort_order` | String (`DATE_DESC`/`DATE_ASC`/`PRIORITY_DESC`/`TITLE_ASC`) | `DATE_DESC` |
| `priority_filter` | String (`ALL`/`HIGH`/`MEDIUM`/`LOW`) | `ALL` |

### 4.2 Garantías

- **Instancia única:** el delegate de extensión de `Context` garantiza un solo
  DataStore por proceso para `user_preferences`.
- **Claves tipadas:** `stringPreferencesKey` y `booleanPreferencesKey`.
- **Valores por defecto:** el mapeo `Preferences.toUserPreferences()` usa
  `UserPreferences()` como respaldo; los enums tienen `fromStorage` tolerante.
- **Errores de lectura:** `catch` emite `emptyPreferences()` ante `IOException`.
- **Archivo corrupto:** `ReplaceFileCorruptionHandler` reemplaza por vacío.
- **Setters suspendidos:** `setThemeMode`, `setShowCompleted`, `setSortOrder`,
  `setPriorityFilter`, `resetToDefaults`.
- **Contrato testeable:** `UserPreferencesDataSource` permite un doble en las
  pruebas del repositorio sin arrastrar DataStore.

El tema se aplica en `MainActivity` observando `repository.preferences`; la UI
de ajustes escribe las cuatro preferencias. El orden y el filtro se aplican en
el repositorio antes de entregar la lista a Compose.

---

## 5. Room

### 5.1 Esquema

Tabla `reminders`, clave primaria `id` (TEXT, UUID local). Columnas: `remoteId`,
`title`, `description`, `category`, `priority`, `status`, `dueDate`,
`locationOrNotice`, `syncState`, `syncError`, `isDeleted`, `createdAt`,
`updatedAt`.

Se guardan los enums como texto (`storageValue`) para que el esquema no dependa
del ordinal del enum. `isDeleted` implementa un tombstone.

Índices: `status`, `priority`, `syncState`, `remoteId` (no único por la
limitación de JSONPlaceholder, ver §10).

### 5.2 DAO

- Flujos: `observeVisible`, `observeByStatus`, `observeByPriority`,
  `observeById`, `observePendingCount`.
- Lecturas: `getById`, `getByRemoteId`, `getAllVisible`, `getPendingForSync`.
- Escrituras: `upsert`, `upsertAll`, `deleteById`, `deleteAll`,
  `updateSyncInfo`, `markDeleted`.

La UI nunca usa el DAO: solo el repositorio.

---

## 6. Flujo local-first

### 6.1 Escritura

1. El ViewModel valida y llama al repositorio.
2. El repositorio genera el UUID/timestamps (la pantalla no los inventa).
3. Guarda en Room con `syncState = PENDING`.
4. La lista se actualiza de inmediato porque observa `Flow` de Room.
5. Se lanza `syncNow()` en segundo plano.

Crear, editar, cambiar estado y borrar siguen el mismo camino. El borrado sin
red no elimina la fila si ya tenía `remoteId`: la marca `isDeleted = 1` y la
oculta de la UI; el `DELETE` se envía después.

### 6.2 Sincronización

`syncNow()` se ejecuta en `Dispatchers.IO` y se serializa con `Mutex`:

1. Si no hay red, publica `SyncStatus.Offline` y no toca Room.
2. **Pull** (`GET`): fusiona registros remotos.
3. **Push**: recorre `PENDING`/`ERROR`.
   - Sin `remoteId` → `POST`.
   - Con `remoteId` → `PUT`.
   - Tombstone → `DELETE`.
4. Cada éxito actualiza `syncState = SYNCED` y `remoteId`.
5. Cada fallo guarda `syncState = ERROR` y el mensaje.
6. Publica `Success(pushed, pulled)`, `Offline` o `Error`.

Disparadores: arranque (primer valor del flujo de red), retorno de conectividad,
botón “Sync” del listado, botón de ajustes y cada escritura local. Todos pasan
por el mismo `Mutex`.

### 6.3 Invariantes contra duplicados

1. El `Mutex` impide ejecuciones simultáneas.
2. Solo se envían filas `PENDING`/`ERROR`; una fila confirmada deja de ser
   elegible.
3. El pull ocurre antes del push.
4. `clientId` estable (UUID) en el `body` y en un campo extra, para reconciliar
   una creación cuyo response se perdió.
5. Los registros remotos sin `clientId` se descartan.
6. Un tombstone no se resucita con el pull.
7. `404` al actualizar → se recrea con el mismo `clientId`; `404` al borrar →
   se completa el borrado local.
8. `syncState` y `syncError` se persisten, no solo se muestran.

---

## 7. Red y errores

`ApiResult` es el contrato que aísla la capa de datos:

| Situación | Tipo | Efecto |
| --- | --- | --- |
| 2xx con cuerpo | `Success` | Marca `SYNCED`. |
| 2xx sin cuerpo (DELETE) | `Success(Unit)` | Completa el borrado. |
| 4xx | `ClientError` | `ERROR` (salvo 404 con la estrategia indicada). |
| 5xx | `ServerError` | `ERROR`, reintentable. |
| `SocketTimeoutException` | `Timeout` | `ERROR`, datos locales intactos. |
| `UnknownHostException`, `ConnectException`, `SocketException`, `SSLException`, `IOException` | `NoConnection` | `Offline`, datos locales intactos. |
| Otro | `UnknownError` | `ERROR`. |

`CancellationException` se propaga para no romper la cancelación de corrutinas.
OkHttp: 10 s de conexión, 15 s de lectura/escritura, logging solo en debug.

---

## 8. Interfaz

- **Listado:** banner de sincronización, resumen de preferencias, tarjetas con
  prioridad/categoría/estado, estado de sync y contador de pendientes; acciones
  Sync y Ajustes; FAB de creación.
- **Creación:** validación de título (4–80), descripción (≤200), categoría,
  prioridad, fecha y ubicación/aviso. Guarda local y vuelve al listado.
- **Detalle:** vista completa, edición en línea, cambio de estado, eliminación
  con confirmación y visualización del error de sync.
- **Ajustes:** tema, mostrar completados, orden, filtro, URL configurada, sync
  manual, restablecer preferencias y borrar datos locales.

El tema se decide con `themeMode` de DataStore y se aplica en el nivel superior.

---

## 9. Pruebas y resultados

### 9.1 Unitarias JVM — ejecutadas

Comando:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest --console=plain
```

**Resultado: 20 pruebas, 0 fallos.**

| Clase | Pruebas | Qué valida |
| --- | --- | --- |
| `ReminderMappersTest` | 7 | Roundtrip Entity↔Domain, enums inválidos, body con metadatos, descripción, DTO ajeno sin `clientId`, `clientId` en request. |
| `UserPreferencesMappingTest` | 4 | Defaults, valores válidos, valores inválidos, claves desconocidas. |
| `OfflineFirstReminderRepositoryTest` | 8 | Creación offline pendiente, push único, dos `syncNow` concurrentes sin duplicar, 5xx→ERROR→recuperación, pull ignora ajenos, 404 recrea sin duplicar fila, tombstone hasta DELETE, filtro de completados. |
| `ExampleUnitTest` | 1 | Plantilla existente del proyecto. |

Reporte: `app/build/reports/tests/testDebugUnitTest/index.html`.

### 9.2 Instrumentadas Room — compiladas, no ejecutadas

Comando previsto:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

`ReminderDaoTest` incluye 4 pruebas con `Room.inMemoryDatabaseBuilder`:
inserción/lectura, filtros y tombstones, contador de pendientes y actualización
de `remoteId`.

La compilación de `:app:compileDebugAndroidTestKotlin` fue exitosa. **La
ejecución no se realizó** porque el entorno de trabajo no tenía emulador ni
dispositivo (`adb devices` sin dispositivos). No se reportan resultados que no
se hayan obtenido.

### 9.3 Compilación

```powershell
.\gradlew.bat :app:assembleDebug --console=plain
```

`BUILD SUCCESSFUL`. APK generado:
`app/build/outputs/apk/debug/app-debug.apk` (~13,9 MB).

---

## 10. Decisiones y limitaciones

### 10.1 JSONPlaceholder como backend simulado

Se eligió `https://jsonplaceholder.typicode.com/` por ser público, estable y no
requerir credenciales. Su contrato solo garantiza `id`, `userId`, `title` y
`body`.

Consecuencias y mitigaciones:

- Los campos propios (categoría, prioridad, estado, fecha, ubicación, `clientId`)
  se envían como campos extra y también se codifican en un bloque
  `[recordatorio]` dentro de `body`. Al leer, se aceptan ambas fuentes.
- Los posts de ejemplo sin `clientId` se ignoran.
- JSONPlaceholder **no persiste** los POST de forma consultable y devuelve
  siempre `id = 101`. Por eso la identidad estable es el UUID local y el índice
  de `remoteId` no es único. La app no depende de releer lo creado.
- DELETE/PUT responden de forma simulada; son suficientes para demostrar el
  flujo, pero no para validar concurrencia real de un backend.
- Con un backend real que devuelva ids únicos y respete `clientId`, el mismo
  código funciona sin cambios; `remote.properties.example` documenta el
  contrato.

### 10.2 Otras decisiones

- **UUID local como PK:** evita colisiones y permite crear offline.
- **Enums como texto:** migraciones de código más seguras.
- **Tombstone:** conserva la intención de borrado sin red.
- **`Mutex` en lugar de WorkManager:** la sincronización es inmediata y el
  requisito no exige trabajos diferidos garantizados por el sistema. WorkManager
  sería la evolución natural para reintentos con backoff.
- **Contenedor manual:** suficiente para el tamaño del proyecto y sin añadir
  Hilt/Koin.
- **Sin secretos:** no hay tokens; la URL es configuración de compilación.

### 10.3 Limitaciones conocidas

- No hay paginación remota: se piden 50 elementos.
- No hay resolución de conflictos por campo; se usa `updatedAt` completo.
- El pull puede competir con una edición local reciente; la estrategia actual
  prioriza el remoto si su `updatedAt` es mayor y hay cambios locales, lo que
  puede descartar una edición local no enviada en ese instante.
- La prueba instrumentada requiere dispositivo/emulador.
- El backend simulado no permite verificar persistencia real ni ids únicos.

---

## 11. Trazabilidad de requisitos

| Requisito | Implementación | Evidencia |
| --- | --- | --- |
| ≥3 preferencias persistentes | 4 claves en DataStore | §4, `UserPreferencesRepository` |
| Instancia única DataStore | delegate `preferencesDataStore` | `UserPreferencesRepository.kt` |
| Claves tipadas | `stringPreferencesKey`/`booleanPreferencesKey` | §4.1 |
| Defaults ante inválidos/error | `toUserPreferences`, `fromStorage`, `catch` | `UserPreferencesMappingTest` |
| Setters suspendidos | métodos `suspend` con `edit` | `UserPreferencesRepository` |
| Room con id estable y campos | `ReminderEntity` | §5 |
| DAO insert/update/delete + Flow + filtro | `ReminderDao` | §5.2 |
| Instancia única Room | `AppDatabase.getInstance` | `AppDatabase.kt` |
| Compose no accede al DAO | ViewModel → repositorio | §3.1 |
| Local-first, Room verdad | escritura primero en Room | §6.1 |
| Offline queda PENDING | `syncState = PENDING` | Prueba de creación offline |
| Sync al recuperar red | `NetworkMonitor.isOnlineFlow` | `start()` |
| DTO/Entity/Domain + mappers | paquetes y `ReminderMappers` | §3.2 |
| Retrofit/OkHttp, URL configurable | `RemoteServiceFactory` + `BuildConfig` | §7, `remote.properties.example` |
| Distinguir 4xx/5xx/timeout/sin conexión | `ApiResult` | §7 |
| Sin secretos | sin tokens en código | §7 |
| Evitar duplicados | mutex + pull/push + clientId | §6.3, prueba concurrente |
| Estado visible | banner + tarjeta + detalle | §8 |
| Pruebas e informe | 20 unitarias, DAO instrumentada, este informe | §9 |

---

## 12. Conclusión

La aplicación cumple los objetivos de persistencia y sincronización: Room es la
fuente de verdad, DataStore conserva las preferencias entre ejecuciones y el
repositorio local-first permite trabajar sin red sin perder cambios. La
sincronización está serializada y reconciliada por `clientId`, con estados
visibles y errores clasificados. Las 20 pruebas unitarias pasan y el APK de
debug se genera correctamente.

El principal riesgo residual es el backend simulado: JSONPlaceholder no
persiste los POST ni devuelve ids únicos. La arquitectura y el contrato están
preparados para sustituirlo por un servicio real mediante configuración, sin
cambiar la UI ni el repositorio.
