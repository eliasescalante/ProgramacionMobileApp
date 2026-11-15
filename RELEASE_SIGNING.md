# Firma de la compilación release

El APK y el AAB de entrega se firman con un keystore propio. **Ni el keystore
ni sus contraseñas se guardan dentro del repositorio.**

---

## 1. Ubicación de los archivos

| Archivo | Ubicación | ¿Se versiona? |
|---|---|---|
| Keystore | `C:\Users\yuens\AndroidStudioProjects\API1ELIASESCALANTEPM-signing\release-keystore.jks` | No |
| Credenciales | `C:\Users\yuens\AndroidStudioProjects\API1ELIASESCALANTEPM-signing\keystore.properties` | No |

`app/build.gradle.kts` busca `keystore.properties` en este orden:

1. Ruta indicada con `-PSIGNING_PROPERTIES_FILE=...`.
2. Variable de entorno `RELEASE_SIGNING_PROPERTIES`.
3. `../API1ELIASESCALANTEPM-signing/keystore.properties` (ruta por defecto).
4. `keystore.properties` en la raíz del proyecto (también ignorado por git).

Si no encuentra el archivo, la variante release se genera **sin firmar** y
Gradle avisa con `signingConfig` nulo. Para entregar hay que firmarla.

---

## 2. Generar el keystore (una sola vez)

```powershell
$signingDir = 'C:\Users\yuens\AndroidStudioProjects\API1ELIASESCALANTEPM-signing'
New-Item -ItemType Directory -Force -Path $signingDir | Out-Null

& 'C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe' `
  -genkeypair -v `
  -keystore "$signingDir\release-keystore.jks" `
  -alias recordatorios `
  -keyalg RSA -keysize 2048 -validity 10000 `
  -storetype JKS
```

`keytool` pide la contraseña del almacén, los datos del certificado (nombre,
organización, país) y la contraseña de la clave. Se puede usar la misma
contraseña para ambos, o dos distintas: el archivo de propiedades admite las
dos.

> Guardar una copia del keystore y de `keystore.properties` en un gestor de
> contraseñas o medio externo. Si se pierden, no se pueden publicar
> actualizaciones firmadas con la misma identidad.

---

## 3. Crear `keystore.properties` fuera del repositorio

```properties
storeFile=C:\\Users\\yuens\\AndroidStudioProjects\\API1ELIASESCALANTEPM-signing\\release-keystore.jks
storePassword=CONTRASEÑA_DEL_ALMACEN
keyAlias=recordatorios
keyPassword=CONTRASEÑA_DE_LA_CLAVE
```

En Java `.properties`, la barra invertida es un carácter de escape: por eso la
ruta usa `\\` o `/`.

Comprobar que git no lo ve:

```powershell
git status --short
git check-ignore -v ..\API1ELIASESCALANTEPM-signing\keystore.properties
```

---

## 4. Compilar los artefactos

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'

# APK instalable
.\gradlew.bat :app:assembleRelease --console=plain

# App Bundle para publicación
.\gradlew.bat :app:bundleRelease --console=plain
```

Salidas:

```
app/build/outputs/apk/release/app-release.apk
app/build/outputs/bundle/release/app-release.aab
```

Nombre alternativo con versión (opcional):

```powershell
Copy-Item app\build\outputs\apk\release\app-release.apk `
  ..\API1ELIASESCALANTEPM_Actividad4-release.apk
Copy-Item app\build\outputs\bundle\release\app-release.aab `
  ..\API1ELIASESCALANTEPM_Actividad4-release.aab
```

---

## 5. Verificar la firma

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"

# Usar la versión de build-tools instalada (en este equipo, 36.1.0).
$bt = Get-ChildItem "$sdk\build-tools" -Directory |
      Sort-Object Name -Descending | Select-Object -First 1

& "$($bt.FullName)\apksigner.bat" verify --verbose --print-certs `
  app\build\outputs\apk\release\app-release.apk

& "$($bt.FullName)\aapt2.exe" dump badging `
  app\build\outputs\apk\release\app-release.apk | Select-String "package:|versionCode|versionName"
```

Debe aparecer `Verifies` con el certificado `CN=...` generado con `keytool`,
`versionCode='4'` y `versionName='4.0'`.

---

## 6. Instalar y probar el APK release

```powershell
adb install -r app\build\outputs\apk\release\app-release.apk
adb shell am start -n com.example.api1_eliasescalante_pm/.MainActivity
```

El APK release:

- usa `APP_ENV=production`, `CLOUD_LOGGING=false`;
- no incluye la configuración de tráfico en claro (`usesCleartextTraffic`);
- está optimizado/minificado con R8;
- se firma con el keystore propio, por lo que se instala como aplicación
  independiente aunque exista una versión debug con el mismo `applicationId`.

---

## 7. Qué NO se entrega

- `release-keystore.jks`
- `keystore.properties`
- `local.properties`
- `app/google-services.json` se entrega junto al proyecto para que el build
  funcione, pero no se versiona en git.
