# ---------------------------------------------------------------------------
# Genera la carpeta y el ZIP de entrega de la Actividad 4.
#
#   .\tools\package_delivery.ps1
#
# Resultado:
#   ..\API1ELIASESCALANTEPM_Actividad4_Entrega\
#   ..\API1ELIASESCALANTEPM_Actividad4_Entrega.zip
#
# Incluye el proyecto completo, el APK release, el AAB y el informe.
# Excluye artefactos de build, caches del IDE, logs y credenciales de firma.
# ---------------------------------------------------------------------------
param(
    [string]$OutputRoot = (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
)

$ErrorActionPreference = 'Stop'

$project = Split-Path -Parent $PSScriptRoot
$name = 'API1ELIASESCALANTEPM_Actividad4_Entrega'
$staging = Join-Path $OutputRoot $name
$zip = "$staging.zip"

$apk = Join-Path $project 'app\build\outputs\apk\release\app-release.apk'
$aab = Join-Path $project 'app\build\outputs\bundle\release\app-release.aab'

foreach ($artifact in @($apk, $aab)) {
    if (-not (Test-Path $artifact)) {
        throw "Falta el artefacto: $artifact. Ejecuta :app:assembleRelease :app:bundleRelease."
    }
}

if (Test-Path $staging) { Remove-Item $staging -Recurse -Force }
if (Test-Path $zip) { Remove-Item $zip -Force }
New-Item -ItemType Directory -Force $staging | Out-Null

# /E copia subdirectorios vacios; /XD excluye por nombre en cualquier nivel.
$excludedDirs = @('.gradle', '.idea', '.kotlin', '.git', 'build')
$excludedFiles = @('local.properties', '*.log', '*.jks', '*.keystore', 'keystore.properties')

& robocopy $project $staging /E /XD @excludedDirs /XF @excludedFiles /NFL /NDL /NJH /NJS /NP | Out-Null
if ($LASTEXITCODE -ge 8) { throw "robocopy fallo con codigo $LASTEXITCODE" }

Copy-Item $apk (Join-Path $staging 'API1ELIASESCALANTEPM_Actividad4-release.apk') -Force
Copy-Item $aab (Join-Path $staging 'API1ELIASESCALANTEPM_Actividad4-release.aab') -Force

Compress-Archive -Path $staging -DestinationPath $zip -CompressionLevel Optimal

$apkHash = (Get-FileHash $apk -Algorithm SHA256).Hash
$aabHash = (Get-FileHash $aab -Algorithm SHA256).Hash

Write-Host "Carpeta: $staging"
Write-Host "ZIP:     $zip"
Write-Host ("Tamano ZIP: {0:N2} MB" -f ((Get-Item $zip).Length / 1MB))
Write-Host "APK SHA256: $apkHash"
Write-Host "AAB SHA256: $aabHash"
