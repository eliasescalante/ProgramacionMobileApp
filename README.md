# Recordatorios Contextuales — Actividad 4

Aplicación Android de recordatorios con **Room como fuente local**, **Firebase
Authentication** para la identidad y **Cloud Firestore** como copia remota
multiusuario. Versión **4.0** (`versionCode 4`).

El objetivo de esta actividad es que cada usuario vea únicamente sus propios
recordatorios, pueda recuperarlos en otro dispositivo y siga trabajando sin
conexión, con reglas de seguridad reales del lado del servidor.

---

## 1. Qué incluye

| Requisito de la consigna | Implementación |
|---|---|
| Firebase como BaaS | Authentication (correo/contraseña) + Cloud Firestore |
| Room como fuente local | Toda lectura de la UI sale de Room |
| Sin Firebase en `@Composable` | La UI solo habla con ViewModel y repositorios |
| Aislamiento multiusuario | `ownerUid` en Room + ruta `users/{uid}` + reglas de Firestore |
| Cambios offline | Repositorio local-first con cola `PENDING/ERROR` |
| Recuperación en otro dispositivo | Pull de Firestore al iniciar sesión y sincronizar |
| Sin duplicados al reintentar | `id` estable = id del documento + `set()` idempotente |
| Reglas de seguridad | `firestore.rules` publicadas y verificadas con dos cuentas |
| Release firmado | `assembleRelease` con keystore fuera del repositorio |
| APK + AAB | `app-release.apk` y `app-release.aab` |

Además conserva de la Actividad 3:

- **DataStore Preferences** con tema, mostrar completados, orden y filtro.
- **Navegación Compose** con listado, creación, detalle y ajustes.
- **Borrado offline** con tombstone y eliminación remota diferida.
- **Estados de sincronización** visibles (banner, contador y tarjetas).

---

## 2. Arquitectura

```
┌──────────────────────────── Compose (UI) ────────────────────────────┐
│  AuthScreen   ReminderListScreen   Create   Detail   Settings        │
└───────────────┬──────────────────────────────────────┬───────────────┘
                │ eventos / StateFlow                  │
        ┌───────▼────────┐                      ┌──────▼─────────┐
        │  AuthViewModel │                      │ Reminder*VM    │
        └───────┬────────┘                      └──────┬─────────┘
                │                                     │
        ┌───────▼─────────┐                   ┌───────▼──────────────┐
        │ AuthRepository  │◄── SessionProvider│ ReminderRepository   │
        │ (Firebase Auth) │   (uid de sesión) │ (interfaz)           │
        └───────┬─────────┘                   └───────┬──────────────┘
                │                                     │
                │                          OfflineFirstReminderRepository
                │                          │            │           │
                │                    Room DAO      DataStore   Firestore
                │                   (verdad local) (prefs)    (copia remota)
                │
        FirebaseAuth ──► emite AuthState (Restoring/SignedIn/SignedOut/SessionExpired)
```

Reglas de dependencia:

- **Compose** no conoce `FirebaseAuth`, `FirebaseFirestore`, el DAO ni los
  modelos de datos. Solo usa ViewModels y estado de UI.
- **ViewModels** no conocen Firebase: llaman a `AuthRepository` y
  `ReminderRepository`.
- **Repositorios** son los únicos que coordinan Room, DataStore y Firestore.
- **`SessionProvider`** es la única fuente del `uid`. Viene de
  `FirebaseAuth.currentUser`, nunca de un parámetro de la pantalla.

### Paquetes

| Paquete | Responsabilidad |
|---|---|
| `domain.model` | `Reminder`, `AuthState`, `AuthUser`, `SyncStatus`, preferencias. Sin Android. |
| `data.local` | `ReminderEntity`, `ReminderDao`, `AppDatabase`, migración 1→2. |
| `data.auth` | `AuthDataSource` + `FirebaseAuthDataSource` y traducción de errores. |
| `data.cloud` | `ReminderCloudDataSource`, `FirestoreReminderCloudDataSource`, `CloudResult`. |
| `data.preferences` | DataStore + contrato `UserPreferencesDataSource`. |
| `data.network` | `NetworkMonitor` (ConnectivityManager). |
| `data.mapper` | Mapeos Entity ↔ Domain ↔ Cloud. |
| `data.repository` | `AuthRepository`, `SessionProvider`, `OfflineFirstReminderRepository`. |
| `viewmodel` | Estado de UI y acciones. |
| `ui.ui` | Tema, componentes y pantallas Compose. |
| `navigation` | `AppRoot` (sesión) y `ReminderNavHost`. |

`AppContainer` crea una sola instancia por proceso de `FirebaseAuth`,
`FirebaseFirestore`, Room, DataStore y de ambos repositorios.
`ReminderApplication.onCreate` lo inicializa.

---

## 3. Modelo de datos

### Room — tabla `reminders` (versión 2)

| Columna | Tipo | Notas |
|---|---|---|
| `id` | TEXT (PK) | UUID estable; **mismo id que el documento de Firestore**. |
| `ownerUid` | TEXT | uid de Firebase Authentication dueño. Índice. |
| `title`, `description` | TEXT | Contenido. |
| `category`, `priority`, `status` | TEXT | Enums como texto. |
| `dueDate`, `locationOrNotice` | INTEGER/TEXT | Opcionales. |
| `syncState` | TEXT | `PENDING`, `SYNCING`, `SYNCED`, `ERROR`. |
| `syncError` | TEXT | Último error legible. |
| `isDeleted` | INTEGER | Tombstone para borrados offline. |
| `createdAt`, `updatedAt` | INTEGER | Marcas de tiempo. |
| `syncedAt` | INTEGER | Última confirmación de Firestore. |

Índices: `ownerUid`, (`ownerUid`,`status`), (`ownerUid`,`priority`),
(`ownerUid`,`syncState`), `syncedAt`.

**Todas** las consultas del DAO reciben `ownerUid`. No existe un método que
devuelva filas de todas las cuentas.

La migración `MIGRATION_1_2` agrega `ownerUid` y `syncedAt`. Las filas de la
versión 3 (sin dueño) se descartan: no hay forma segura de asignarlas a una
cuenta.

### Firestore — `users/{uid}/reminders/{reminderId}`

```
users (colección)
└── {uid} (documento, no se usa para datos)
    └── reminders (colección)
        └── {reminderId} (documento)
            ├── id: string            = reminderId
            ├── ownerUid: string      = uid de la ruta
            ├── title: string
            ├── description: string?
            ├── category: WORK|PERSONAL|HEALTH|OTHER
            ├── priority: HIGH|MEDIUM|LOW
            ├── status: PENDING|IN_PROGRESS|COMPLETED
            ├── dueDate: number?
            ├── locationOrNotice: string?
            ├── createdAt: number
            ├── updatedAt: number
            └── schemaVersion: number
```

El documento repite `ownerUid` e `id` para que las reglas puedan validar que el
cuerpo coincide con la ruta. No se usan subcolecciones adicionales.

---

## 4. Sincronización

### Flujo de escritura

1. La pantalla envía la acción al ViewModel.
2. El repositorio obtiene el `uid` de la sesión (`requireUid()`).
3. Escribe **primero** en Room con `syncState = PENDING`.
4. La UI se actualiza al instante: observa un `Flow` de Room.
5. Se dispara `syncNow()` en el scope de la aplicación.

### Flujo de lectura

1. `reminders` combina el `authState` con `dao.observeVisible(uid)` y las
   preferencias.
2. Si no hay sesión, el flujo emite una lista vacía. La pantalla no puede
   mostrar datos de la cuenta anterior.
3. Los filtros de estado, prioridad y orden se aplican en el repositorio.

### Algoritmo de `syncNow()`

```
uid = session.currentUid()            ── sin sesión ──► NoSession
if (!network.isOnline())              ── sin red  ───► Offline
mutex.withLock {
    volver a comprobar sesión y red
    PULL:
      cloud.fetchAll(uid)
        ├─ Success: importar/actualizar por updatedAt (LWW)
        │            borrar local SYNCED ausentes en la nube
        ├─ Unauthenticated: invalidar sesión
        ├─ PermissionDenied: Error no recuperable
        └─ Unavailable: Offline
    PUSH:
      for (fila in Room PENDING/ERROR del uid):
         if (isDeleted) cloud.delete(uid, id)   // idempotente
         else           cloud.upsert(uid, doc)  // set() sobre el mismo id
         éxito → SYNCED + syncedAt
         error recuperable → ERROR + syncError
         error de sesión → invalidar sesión
}
```

Propiedades:

- Un **`Mutex`** serializa las sincronizaciones (arranque, retorno de red,
  botón manual, escritura). Un segundo intento encuentra la cola vacía.
- El **pull va antes del push** y usa `updatedAt` como *last-write-wins*. Una
  fila local pendiente más nueva que la nube se conserva y se envía.
- Un **tombstone** local no se resucita con el pull.
- El `id` es el mismo en Room y Firestore: reintentar hace `set()` sobre el
  mismo documento, **no crea duplicados**.
- Si el pull no trae un documento que estaba `SYNCED`, se interpreta como
  borrado hecho en otro dispositivo y se elimina localmente.
- Al iniciar sesión se ejecuta `dao.deleteAllExcept(uid)`; al cerrar sesión,
  `dao.deleteAll()`.

### Estados que ve el usuario

| `SyncStatus` | Cuándo | Mensaje |
|---|---|---|
| `Idle` | Sin operación | — |
| `Syncing` | En curso | «Sincronizando con Firestore…» |
| `Success` | Pull/push completos | «Sincronizado: N enviados, M recibidos» |
| `Offline` | Sin red o Firestore inaccesible | «Sin conexión… los cambios quedaron guardados» |
| `NoSession` | Sesión ausente o inválida | «Iniciá sesión para sincronizar» |
| `Error` | Rechazo del servidor u otro fallo | Mensaje traducido, con `recoverable` |

La UI nunca ve códigos de Firestore: `CloudResult` y `AuthException` traducen
`PERMISSION_DENIED`, `UNAUTHENTICATED`, `UNAVAILABLE`, etc. en la capa de datos.

---

## 5. Autenticación

`AuthRepository` expone `authState`:

| Estado | Significado | UI |
|---|---|---|
| `Restoring` | Firebase aún no respondió | Pantalla «Restaurando sesión…» |
| `SignedIn(AuthUser)` | Sesión válida | `ReminderNavHost` |
| `SignedOut` | Sin sesión | `AuthScreen` (login/registro) |
| `SessionExpired(reason)` | Token revocado o inválido | `AuthScreen` con aviso |

Casos cubiertos:

- **Registro** y **login** con validación previa de correo/contraseña.
- **Estado en curso**: botón deshabilitado con indicador y texto «Verificando…».
- **Error**: mensaje traducido (`AuthErrorMapper`), sin códigos del proveedor.
- **Sesión activa al reabrir**: Firebase restaura y `reloadSession()` revalida.
- **Pérdida o invalidez**: si Firestore responde `UNAUTHENTICATED` o la
  revalidación falla, se cierra la sesión local y se pasa a `SessionExpired`.
- **Logout**: cierra Firebase Auth, borra la copia local de Room y vuelve a
  `AuthScreen`. Un diálogo de advertencia explica que se eliminan los datos
  locales (la copia autorizada queda en Firestore).

### Qué queda en el dispositivo al cerrar sesión

| Dato | Después del logout |
|---|---|
| Recordatorios en Room | Eliminados (`deleteAll()`). |
| Token de Firebase | Eliminado por `FirebaseAuth.signOut()`. |
| Preferencias de UI (DataStore) | Se conservan (tema, orden, filtro): no son datos personales. |
| Copia en Firestore | Se conserva, protegida por las reglas y accesible solo al volver a iniciar sesión. |
| Contraseña | Nunca se guarda; la gestiona Firebase Authentication. |

---

## 6. Seguridad y aislamiento multiusuario

Tres barreras independientes:

1. **Ruta**: cada documento vive en `users/{uid}` y el `uid` sale de la sesión.
2. **Reglas de Firestore** ([`firestore.rules`](firestore.rules)):
   - `owns(uid)` exige `request.auth.uid == uid`.
   - `validReminder()` exige `ownerUid == uid`, `id == reminderId`, tipos y
     valores válidos.
   - En `update`, `createdAt` no puede cambiar.
   - Todo lo demás está denegado por defecto.
3. **Room**: todas las consultas exigen `ownerUid`; al entrar se purgan filas
   de otras cuentas y al salir se borra todo.

La visibilidad de botones **no** cuenta como autorización. La prueba con dos
cuentas reales está en `tools/cross_account_check.ps1` y su evidencia en
`evidencias/cross_account_check.txt`.

### Resultado de la prueba de acceso cruzado

Cuentas: `pm.recordatorios.a@example.com` y `pm.recordatorios.b@example.com`.

| # | Intento | Esperado | Obtenido |
|---|---|---|---|
| 1 | A crea un recordatorio propio | 200 | 200 |
| 2 | B lee el árbol de A | 403 | 403 |
| 3 | B escribe en el árbol de A | 403 | 403 |
| 4 | A crea un documento con `ownerUid` de B | 403 | 403 |
| 5 | B lee su propio árbol | 200 | 200 |
| 6 | A elimina su documento de prueba | 200 | 200 |

**6/6 comprobaciones correctas.** El servidor rechaza el acceso cruzado aunque
el cliente conozca los ids.

Verificación adicional sin token: un `GET` anónimo a
`users/sonda/reminders` devuelve `403 Missing or insufficient permissions`.

---

## 7. Configuración de Firebase

Ver [`FIREBASE_SETUP.md`](FIREBASE_SETUP.md). Resumen:

1. Proyecto `recordatorios-pm`, app Android
   `com.example.api1_eliasescalante_pm`.
2. **Authentication → Correo electrónico/Contraseña: habilitado.**
3. **Firestore Database** creado en modo nativo.
4. Reglas de [`firestore.rules`](firestore.rules) publicadas.
5. `google-services.json` en `app/` (incluido en la entrega; no se publica en un repositorio abierto).

Sin `google-services.json` el proyecto compila y las pruebas unitarias
funcionan; la app muestra «Falta configurar Firebase» en lugar de caerse.

> **Versión de Firebase:** el catálogo fija la BOM **34.12.0** (auth 24.0.1 y
> firestore 26.2.0). Las BOM 34.13+ y 35.x se compilan con Kotlin 2.3/2.4 y no
> son legibles por el Kotlin 2.2.10 integrado en AGP 9. Ver FIREBASE_SETUP.md §6.

---

## 8. Compilar, instalar y probar

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'

# Debug
.\gradlew.bat :app:assembleDebug --console=plain
.\gradlew.bat :app:testDebugUnitTest --console=plain

# Instalar en emulador/dispositivo
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r `
  app\build\outputs\apk\debug\app-debug.apk
```

### Release firmado

El keystore y `keystore.properties` viven **fuera** del repositorio
(`..\API1ELIASESCALANTEPM-signing\`). Ver
[`RELEASE_SIGNING.md`](RELEASE_SIGNING.md).

```powershell
.\gradlew.bat :app:assembleRelease :app:bundleRelease --console=plain
```

Salidas:

- `app\build\outputs\apk\release\app-release.apk`
- `app\build\outputs\bundle\release\app-release.aab`

### Carpeta y ZIP de entrega

```powershell
.\tools\package_delivery.ps1
```

Copia el proyecto y añade el APK y el AAB con nombres de entrega en
`..\API1ELIASESCALANTEPM_Actividad4_Entrega\` y genera
`API1ELIASESCALANTEPM_Actividad4_Entrega.zip`. Excluye `build/`, `.gradle/`,
`.idea/`, `.kotlin/`, `.git/`, `local.properties`, logs y cualquier keystore o
`keystore.properties`.

---

## 9. Pruebas

### Unitarias (JVM)

```powershell
.\gradlew.bat :app:testDebugUnitTest --console=plain
```

| Clase | Pruebas | Cobertura |
|---|---|---|
| `ReminderMappersTest` | 10 | Entity ↔ Domain ↔ Cloud, enums inválidos, documento ajeno, id incoherente, `ownerUid`. |
| `OfflineFirstReminderRepositoryTest` | 16 | Sin sesión, offline, push único, reintento sin duplicados, pull remoto, LWW, borrado remoto, tombstone, errores, concurrencia. |
| `AuthValidationTest` | 7 | Correo, contraseña, confirmación, normalización. |
| `UserPreferencesMappingTest` | 4 | Valores por defecto, válidos, inválidos y claves desconocidas. |
| `ExampleUnitTest` | 1 | Plantilla del proyecto. |

Reporte HTML: `app\build\reports\tests\testDebugUnitTest\index.html`.

### Instrumentadas (Room y flujo de interfaz)

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

`ReminderDaoTest` ejecuta 8 pruebas sobre Room en memoria: inserción/lectura,
invisibilidad entre dueños, filtros, contador de pendientes, `syncedAt`,
actualización con uid ajeno, `deleteAllExcept` y `deleteAll`.

`ReminderOfflineUiTest` añade una prueba de flujo real sobre Compose:

1. Inicia sesión con una cuenta de prueba.
2. Desconecta los datos del emulador (`svc data disable`).
3. Crea un recordatorio desde la interfaz.
4. Comprueba que la tarjeta queda en **Pendiente**.
5. Reconecta, pulsa **Sync** y verifica que pasa a **Sincronizado** una sola vez.
6. Elimina el recordatorio de prueba y cierra sesión.

Las credenciales no están en el código: se pasan como argumentos de
instrumentación. Sin ellos la prueba se omite.

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.testEmail=cuenta@ejemplo.com" `
  "-Pandroid.testInstrumentationRunnerArguments.testPassword=..." `
  --console=plain
```

Total instrumentado: **10 pruebas** (8 de Room + 1 de plantilla + 1 de flujo
UI), 0 fallos.

### Acceso cruzado

```powershell
$key = (Get-Content app\google-services.json -Raw | ConvertFrom-Json).client[0].api_key[0].current_key
.\tools\cross_account_check.ps1 -ProjectId recordatorios-pm -ApiKey $key `
  -EmailA pm.recordatorios.a@example.com -PasswordA '...' `
  -EmailB pm.recordatorios.b@example.com -PasswordB '...'
```

### Escenarios manuales de sincronización

Ver el informe `INFORME_ACTIVIDAD4.txt` (sección de escenarios) para el detalle
paso a paso y el estado esperado en Room y Firestore.

---

## 10. Estructura del proyecto

```
API1ELIASESCALANTEPM/
├── app/
│   ├── build.gradle.kts          # v4.0, firma release, Firebase
│   ├── google-services.json      # configuración real del proyecto
│   └── src/
│       ├── main/java/.../        # domain, data, viewmodel, ui, navigation
│       ├── test/                 # 38 pruebas unitarias
│       └── androidTest/          # 10 pruebas instrumentadas (Room + flujo UI)
├── gradle/libs.versions.toml
├── firestore.rules               # reglas a publicar
├── FIREBASE_SETUP.md
├── RELEASE_SIGNING.md
├── INFORME_ACTIVIDAD4.txt
├── evidencias/
│   ├── cross_account_check.txt    # 403 reales entre dos cuentas
│   ├── release_verificacion.txt   # firma, badging y SHA-256
│   └── instrumented_tests.txt     # 10 pruebas instrumentadas
└── tools/
    ├── cross_account_check.ps1
    └── ui_probe.ps1               # inspección/tap/type por adb
```

---

## 11. Limitaciones conocidas

- No hay verificación de correo ni recuperación de contraseña: no son parte de
  la consigna.
- El pull descarga todos los recordatorios del usuario (sin paginación); es
  adecuado para el volumen de la actividad.
- La sincronización es explícita (arranque, red, botón, tras cada escritura);
  no se usan listeners en tiempo real de Firestore.
- Las preferencias de UI son locales y no se sincronizan entre dispositivos.
- `adb shell input text` no escribe en los campos de Crear/Detalle de Compose
  en el emulador Android 16; la prueba de flujo usa la acción `SetText` de
  semántica. La escritura manual con teclado funciona con normalidad.
