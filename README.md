# Recordatorios Contextuales — Actividad 3

Aplicación Android de recordatorios con **persistencia local (Room + DataStore)**,
**repositorio local-first** y **sincronización con un servicio REST simulado**
(Retrofit/OkHttp). Versión `3.0` (`versionCode 3`).

## 1. Qué incluye

- **DataStore Preferences** con cuatro preferencias persistentes: tema,
  mostrar completados, criterio de orden y filtro de prioridad.
- **Room** como fuente de verdad de los recordatorios, con DAO, consultas
  `Flow`, búsqueda por id y filtros por estado/prioridad.
- **Repositorio local-first**: toda escritura se guarda primero en Room y queda
  `PENDING`; se sincroniza al recuperar la red.
- **Retrofit + OkHttp** con URL base configurable y distinción explícita entre
  éxito, HTTP 4xx, HTTP 5xx, timeout y falta de conexión.
- **Sincronización sin duplicados** mediante `Mutex`, pull antes de push y
  `clientId` estable (UUID local).
- **Borrado offline** con tombstone (`isDeleted`) y eliminación remota diferida.
- **UI Compose** con listado, creación, detalle editable y ajustes.
- **20 pruebas unitarias** ejecutadas correctamente.
- **Prueba instrumentada** del DAO Room incluida y compilada.

## 2. Arquitectura

```
UI (Compose) ──> ViewModel ──> ReminderRepository (interfaz)
                                   │
                    OfflineFirstReminderRepository
                    │            │             │
                 Room DAO    DataStore     Retrofit/OkHttp
                 (verdad)   (preferencias)   (servicio remoto)
```

Capas:

| Paquete | Responsabilidad |
| --- | --- |
| `domain.model` | Modelo de dominio, enums y preferencias. Sin Android. |
| `data.local` | `ReminderEntity`, `ReminderDao`, `AppDatabase`. |
| `data.preferences` | `UserPreferencesRepository` + contrato `UserPreferencesDataSource`. |
| `data.remote` | `ReminderApi`, DTO, `ApiResult`, fuente remota y factory. |
| `data.network` | `NetworkMonitor` (conectividad). |
| `data.mapper` | Mapeos explícitos Entity/DTO/Domain y metadatos del `body`. |
| `data.repository` | Contrato e implementación local-first. |
| `viewmodel` | Estado de UI y acciones. |
| `ui.ui` | Tema, componentes y pantallas Compose. |

`AppContainer` crea una sola instancia de `AppDatabase`, `DataStore`,
`Retrofit` y del repositorio; `ReminderApplication` lo inicializa en
`onCreate`. Compose **nunca** accede al DAO ni a Retrofit.

## 3. Persistencia con DataStore

Archivo: `user_preferences`. Claves tipadas:

| Clave | Tipo | Valores | Predeterminado |
| --- | --- | --- | --- |
| `theme_mode` | String | `SYSTEM`, `LIGHT`, `DARK` | `SYSTEM` |
| `show_completed` | Boolean | `true`/`false` | `true` |
| `sort_order` | String | `DATE_DESC`, `DATE_ASC`, `PRIORITY_DESC`, `TITLE_ASC` | `DATE_DESC` |
| `priority_filter` | String | `ALL`, `HIGH`, `MEDIUM`, `LOW` | `ALL` |

- Instancia única por proceso mediante el delegate `preferencesDataStore`.
- `ReplaceFileCorruptionHandler` recupera un archivo corrupto con preferencias
  vacías.
- Los errores `IOException` de lectura emiten los valores predeterminados.
- Todo valor desconocido cae en el valor por defecto (`fromStorage`).
- Los setters son `suspend` y escriben con `dataStore.edit`.

## 4. Persistencia con Room

Tabla `reminders`:

| Columna | Tipo | Notas |
| --- | --- | --- |
| `id` | TEXT (PK) | UUID local estable (`clientId`). |
| `remoteId` | INTEGER | Id del servicio; índice no único. |
| `title`, `description` | TEXT | Contenido. |
| `category`, `priority`, `status` | TEXT | Enums como texto estable. |
| `dueDate` | INTEGER | Fecha opcional. |
| `locationOrNotice` | TEXT | Ubicación o aviso. |
| `syncState` | TEXT | `PENDING`, `SYNCING`, `SYNCED`, `ERROR`. |
| `syncError` | TEXT | Último error de sincronización. |
| `isDeleted` | INTEGER | Tombstone para borrados offline. |
| `createdAt`, `updatedAt` | INTEGER | Marcas de tiempo. |

Índices: `status`, `priority`, `syncState`, `remoteId`.

El DAO ofrece `observeVisible`, `observeByStatus`, `observeByPriority`,
`observeById`, `observePendingCount`, lecturas puntuales, `upsert`, borrado,
actualización de sync y `markDeleted`.

## 5. Flujo local-first y sincronización

1. La UI envía una acción al ViewModel.
2. El repositorio escribe **primero** en Room con `syncState = PENDING`.
3. La UI se actualiza al instante porque observa `Flow` de Room.
4. Se dispara la sincronización (arranque, retorno de red, botón manual o
   después de cada escritura).
5. `syncNow()` se serializa con un `Mutex`:
   - **Pull** primero (`GET`): reconcilia creaciones cuyo response se perdió.
   - **Push** después: envía solo filas `PENDING`/`ERROR`.
6. Si el servicio confirma, la fila pasa a `SYNCED`; si falla, a `ERROR` con el
   mensaje visible en la tarjeta y en el detalle.

### Cómo se evitan duplicados

- El `Mutex` impide dos sincronizaciones simultáneas.
- Cada fila se envía una sola vez: al confirmarse deja de estar pendiente.
- El `clientId` (UUID local) viaja en el `body` y como campo extra; permite
  reconciliar una creación cuyo response se perdió.
- El pull se ejecuta antes del push y no resucita tombstones.
- Un `404` al actualizar recrea el registro con el mismo `clientId`; un `404`
  al borrar completa el borrado local.
- Los registros remotos sin `clientId` se ignoran.

### Estados visibles

- Banner superior: sincronizando, éxito (enviados/recibidos), offline o error.
- Contador de cambios pendientes.
- Cada tarjeta muestra `Pendiente de envío`, `Sincronizado`, `Sincronizando` o
  `Error de sincronización`, y el detalle muestra el último error.

## 6. Red y manejo de errores

`ApiResult` traduce la respuesta en:

| Caso | Resultado |
| --- | --- |
| 2xx | `Success` |
| 4xx | `ClientError(code, message)` |
| 5xx | `ServerError(code, message)` |
| Timeout | `Timeout` |
| Sin red / DNS / conexión rechazada | `NoConnection` |
| Otro | `UnknownError` |

OkHttp usa timeouts de 10 s (conexión) y 15 s (lectura/escritura). El logging
solo está activo en debug. No hay tokens ni credenciales en el código.

## 7. Configuración de la URL remota

Por defecto: `https://jsonplaceholder.typicode.com/`.

Prioridad:

1. `-PREMOTE_BASE_URL=...`
2. `REMOTE_BASE_URL` en `local.properties`
3. Valor por defecto

Ver [`remote.properties.example`](remote.properties.example) para el contrato y
las limitaciones del servicio de demostración.

## 8. Compilación y ejecución

Requisitos: Android Studio reciente, JDK incluido (JBR), SDK 37 instalado.

```powershell
# En la raíz del proyecto
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'

.\gradlew.bat :app:assembleDebug --console=plain
.\gradlew.bat :app:testDebugUnitTest --console=plain
```

- APK: `app\build\outputs\apk\debug\app-debug.apk`
- Instalación: `adb install -r app\build\outputs\apk\debug\app-debug.apk`

Desde Android Studio: abrir el proyecto, esperar la sincronización de Gradle y
ejecutar la configuración `app`.

> Nota técnica: AGP 9 usa Kotlin integrado y KSP registra sus fuentes con el DSL
> `kotlin.sourceSets`. Por eso `gradle.properties` incluye
> `android.disallowKotlinSourceSets=false`.

## 9. Pruebas

### Unitarias (JVM) — ejecutadas

```powershell
.\gradlew.bat :app:testDebugUnitTest --console=plain
```

Resultado: **20 pruebas, 0 fallos**.

| Clase | Pruebas | Cobertura |
| --- | --- | --- |
| `ReminderMappersTest` | 7 | Entity↔Domain, enums inválidos, metadatos del body, DTO ajeno ignorado, `clientId`. |
| `UserPreferencesMappingTest` | 4 | Valores por defecto, valores válidos, valores inválidos, claves desconocidas. |
| `OfflineFirstReminderRepositoryTest` | 8 | Offline, push único, sync concurrente, error 5xx y recuperación, pull ajeno, 404 al actualizar, tombstone, filtro de completados. |
| `ExampleUnitTest` | 1 | Prueba de plantilla del proyecto. |

Reporte HTML:
`app\build\reports\tests\testDebugUnitTest\index.html`

### Instrumentadas (Room) — compiladas, requieren dispositivo

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

`ReminderDaoTest` contiene 4 pruebas sobre una base Room en memoria (insert y
lectura, filtros/tombstones, contador de pendientes, `remoteId`). Se compiló
correctamente, pero **no se ejecutó** porque el entorno no tenía emulador ni
dispositivo conectado (`adb devices` vacío).

### Checklist manual sugerido

1. Activar modo avión y crear un recordatorio: aparece como pendiente.
2. Desactivar modo avión: el banner sincroniza y la tarjeta pasa a sincronizado.
3. Pulsar “Sync” varias veces: no se duplican registros.
4. Cambiar tema, orden, filtro y “mostrar completados”; cerrar desde Recientes
   y reabrir: las preferencias se conservan.
5. Editar y eliminar sin conexión; al volver la red se envía el cambio.
6. Apuntar la URL a un host inexistente: se muestra error/timeout sin perder
   datos locales.

## 10. Estructura de entrega

- `README.md`: estas instrucciones.
- `INFORME_TECNICO.md`: informe ampliado con decisiones y resultados.
- `remote.properties.example`: configuración del backend.
- Código fuente completo en `app/src`.
- APK de debug generado en `app/build/outputs/apk/debug`.
