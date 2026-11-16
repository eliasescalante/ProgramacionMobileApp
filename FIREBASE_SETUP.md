# Configuración de Firebase

La aplicación usa dos servicios de Firebase como BaaS:

| Servicio | Uso en la aplicación |
|---|---|
| **Firebase Authentication** | Registro e inicio de sesión con correo y contraseña. Provee el `uid` que aísla los datos. |
| **Cloud Firestore** | Copia remota de los recordatorios en `users/{uid}/reminders/{reminderId}`. |

Room sigue siendo la fuente de verdad local. Firestore es la copia remota que
permite recuperar los datos en otro dispositivo.

---

## 1. Crear el proyecto (una sola vez)

1. Entrar a <https://console.firebase.google.com> con una cuenta de Google.
2. **Agregar proyecto** → nombre `recordatorios-pm` → desactivar Google
   Analytics (no se usa) → **Crear proyecto**.
3. **Authentication** → **Comenzar** → pestaña *Sign-in method* →
   habilitar **Correo electrónico/Contraseña**. Dejar deshabilitado el enlace
   por correo (la app no lo usa).
4. **Firestore Database** → **Crear base de datos** → modo **producción** →
   ubicación cercana (por ejemplo `southamerica-east1`). No crear en modo de
   prueba: las reglas de este repositorio ya resuelven el acceso.
5. **Descripción general** → **Agregar app** → **Android**:
   - Nombre: `Recordatorios PM`
   - Package: `com.example.api1_eliasescalante_pm` (debe coincidir con
     `applicationId`; se usa el mismo en debug y release)
   - No hace falta SHA-1 para correo/contraseña.
6. Descargar `google-services.json` y guardarlo en:

   ```
   app/google-services.json
   ```

   El archivo está en `.gitignore`: no se versiona. El proyecto compila sin él
   (las pruebas unitarias funcionan), pero la app muestra la pantalla
   *«Falta configurar Firebase»*.

---

## 2. Publicar las reglas de seguridad

Las reglas están en [`firestore.rules`](firestore.rules).

### Opción A: consola (recomendada para la entrega)

1. Firestore Database → pestaña **Reglas**.
2. Pegar el contenido completo de `firestore.rules`.
3. **Publicar**.

### Opción B: Firebase CLI

```bash
npm install -g firebase-tools
firebase login
firebase init firestore      # elegir el proyecto y el archivo firestore.rules
firebase deploy --only firestore:rules
```

Las reglas deben quedar publicadas **antes** de probar el aislamiento: si se
deja el modo de prueba, cualquier cuenta autenticada podría leer los datos de
otra y la prueba de acceso cruzado fallaría por una configuración incompleta.

---

## 3. Verificar que la configuración llegó al build

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:processDebugGoogleServices --console=plain
```

Debe terminar en `BUILD SUCCESSFUL`. El plugin `com.google.gms.google-services`
se aplica solamente cuando existe `app/google-services.json` (ver
`app/build.gradle.kts`).

---

## 4. Prueba mínima de funcionamiento

1. Instalar la app e iniciar sesión con una cuenta de prueba.
2. Crear un recordatorio.
3. En la consola: Firestore → `users` → `{uid}` → `reminders`.
   Debe existir un documento con el mismo id que muestra el detalle de la app
   y el campo `ownerUid` igual al uid de la cuenta.
4. Modificar el recordatorio en la app y comprobar que `updatedAt` cambió.
5. Instalar la app en un segundo dispositivo/emulador, iniciar sesión con la
   misma cuenta y esperar la sincronización: el recordatorio debe aparecer.
6. Cerrar sesión y comprobar que el listado queda vacío.

---

## 5. Aislamiento entre cuentas

El aislamiento se apoya en tres barreras independientes:

1. **Ruta de Firestore**: cada documento vive bajo `users/{uid}` y el `uid`
   proviene de la sesión (`FirebaseAuth.currentUser.uid`), nunca de la UI.
2. **Reglas de Firestore**: `owns(uid)` exige `request.auth.uid == uid` y
   `validReminder()` exige que `ownerUid` e `id` coincidan con la ruta.
3. **Room**: todas las consultas del DAO reciben `ownerUid`; al iniciar sesión
   se ejecuta `deleteAllExcept(uid)` y al cerrar sesión `deleteAll()`.

La visibilidad de botones no se considera autorización. La prueba de acceso
cruzado con tokens reales está descrita en `INFORME_ACTIVIDAD4.txt` y se puede
repetir con `tools/cross_account_check.ps1`.

### Nota sobre la API key

La API key de `google-services.json` identifica al proyecto, no autoriza el
acceso a datos: quien la conozca no puede leer Firestore sin un token de
usuario válido. Aun así, en producción conviene restringirla en Google Cloud
Console → *APIs & Services* → *Credentials* → *Android key* → limitar a las
APIs de Firebase y al package/SHA de la app.

---

## 6. Versión de Firebase y compatibilidad con Kotlin

El catálogo fija `firebaseBom = "34.12.0"` (auth 24.0.1 y firestore 26.2.0).
No es una versión arbitraria:

- AGP 9 compila con el Kotlin integrado 2.2.10, que solo lee metadatos de
  Kotlin ≤ 2.2.
- Las BOM 35.x y las 34.13.0 en adelante publican `firebase-auth` compilado
  con Kotlin 2.3/2.4. El compilador falla con
  *«Module was compiled with an incompatible version of Kotlin»*.

| BOM | firebase-auth | Metadatos | ¿Compatible con Kotlin 2.2.10? |
|---|---|---|---|
| 35.2.0 | 25.0.0 | 2.4.0 | No |
| 34.18.0 | 24.2.0 | 2.3.0 | No |
| **34.12.0** | **24.0.1** | **2.1.0** | **Sí** |
| 34.0.0 | 24.0.0 | 2.1.0 | Sí |

Las APIs que usa la aplicación (`FirebaseAuth`, `FirebaseFirestore`,
`set/get/delete` y `kotlinx-coroutines-play-services`) no cambiaron entre esas
versiones. Si en el futuro se sube el compilador de Kotlin, se puede volver a
la BOM más reciente y comprobar con:

```powershell
.\gradlew.bat :app:compileDebugKotlin --console=plain
```
