# ---------------------------------------------------------------------------
# Prueba de acceso cruzado entre dos cuentas reales de Firebase.
#
# Requiere:
#   - Proyecto Firebase con Authentication (correo/contraseña) y Firestore.
#   - Reglas de firestore.rules publicadas.
#   - Dos cuentas de prueba (se pueden crear con -CreateAccounts).
#
# Uso:
#   .\tools\cross_account_check.ps1 `
#       -ProjectId "recordatorios-pm" `
#       -ApiKey "AIza..." `
#       -EmailA "cuenta.a@ejemplo.com" -PasswordA "secretoA1" `
#       -EmailB "cuenta.b@ejemplo.com" -PasswordB "secretoB1" `
#       -CreateAccounts
#
# Resultado esperado:
#   1) A crea un recordatorio propio .......................... 200
#   2) B intenta leer el árbol de A ........................... 403
#   3) B intenta escribir en el árbol de A .................... 403
#   4) A intenta crear un documento con ownerUid de B ......... 403
#   5) B lee su propio árbol .................................. 200
#   6) A limpia su documento de prueba ........................ 200
#
# El script no imprime tokens ni contraseñas.
# ---------------------------------------------------------------------------

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ProjectId,
    [Parameter(Mandatory = $true)][string]$ApiKey,
    [Parameter(Mandatory = $true)][string]$EmailA,
    [Parameter(Mandatory = $true)][string]$PasswordA,
    [Parameter(Mandatory = $true)][string]$EmailB,
    [Parameter(Mandatory = $true)][string]$PasswordB,
    [switch]$CreateAccounts
)

$ErrorActionPreference = 'Stop'
$authBase = 'https://identitytoolkit.googleapis.com/v1/accounts'
$firestoreBase = "https://firestore.googleapis.com/v1/projects/$ProjectId/databases/(default)/documents"

$script:passed = 0
$script:failed = 0

function Write-Result {
    param(
        [string]$Name,
        [int]$Expected,
        [int]$Actual,
        [string]$Detail = ''
    )
    $ok = ($Expected -eq $Actual)
    if ($ok) { $script:passed++ } else { $script:failed++ }
    $tag = if ($ok) { 'OK  ' } else { 'FALLA' }
    Write-Host ("[{0}] {1} -> esperado {2}, obtenido {3}" -f $tag, $Name, $Expected, $Actual)
    if ($Detail) {
        $clean = ($Detail -replace '\s+', ' ').Trim()
        $len = [Math]::Min(240, $clean.Length)
        Write-Host ("       {0}" -f $clean.Substring(0, $len))
    }
}

function Invoke-Json {
    param(
        [string]$Method,
        [string]$Uri,
        [hashtable]$Headers = @{},
        [string]$Body = $null
    )
    try {
        $params = @{
            Method      = $Method
            Uri         = $Uri
            Headers     = $Headers
            ErrorAction = 'Stop'
        }
        if ($Body) {
            $params['Body'] = $Body
            $params['ContentType'] = 'application/json'
        }
        $response = Invoke-WebRequest @params -UseBasicParsing
        return [pscustomobject]@{
            Status  = [int]$response.StatusCode
            Content = $response.Content
        }
    } catch {
        $web = $_.Exception.Response
        if ($web -ne $null) {
            $status = [int]$web.StatusCode
            $stream = $web.GetResponseStream()
            $reader = New-Object System.IO.StreamReader($stream)
            return [pscustomobject]@{
                Status  = $status
                Content = $reader.ReadToEnd()
            }
        }
        throw
    }
}

function New-AuthBody {
    param([string]$Email, [string]$Password)
    $payload = @{
        email             = $Email
        password          = $Password
        returnSecureToken = $true
    } | ConvertTo-Json -Compress
    return $payload
}

function Get-Or-Create-User {
    param(
        [string]$Email,
        [string]$Password,
        [switch]$Create
    )
    $body = New-AuthBody -Email $Email -Password $Password
    if ($Create) {
        $result = Invoke-Json -Method POST -Uri "${authBase}:signUp?key=$ApiKey" -Body $body
        if ($result.Status -eq 200) {
            return ($result.Content | ConvertFrom-Json)
        }
        # Si ya existe, se intenta iniciar sesión.
    }
    $result = Invoke-Json -Method POST -Uri "${authBase}:signInWithPassword?key=$ApiKey" -Body $body
    if ($result.Status -ne 200) {
        throw "No se pudo autenticar $Email. HTTP $($result.Status): $($result.Content)"
    }
    return ($result.Content | ConvertFrom-Json)
}

function New-FirestoreDocumentBody {
    param(
        [string]$Id,
        [string]$OwnerUid,
        [string]$Title
    )
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $document = @{
        fields = @{
            id           = @{ stringValue = $Id }
            ownerUid     = @{ stringValue = $OwnerUid }
            title        = @{ stringValue = $Title }
            category     = @{ stringValue = 'WORK' }
            priority     = @{ stringValue = 'MEDIUM' }
            status       = @{ stringValue = 'PENDING' }
            createdAt    = @{ integerValue = "$now" }
            updatedAt    = @{ integerValue = "$now" }
            schemaVersion = @{ integerValue = '1' }
        }
    }
    return ($document | ConvertTo-Json -Depth 8 -Compress)
}

Write-Host ''
Write-Host '=== Prueba de acceso cruzado en Firestore ===' -ForegroundColor Cyan
Write-Host "Proyecto: $ProjectId"
Write-Host ''

$userA = Get-Or-Create-User -Email $EmailA -Password $PasswordA -Create:$CreateAccounts
$userB = Get-Or-Create-User -Email $EmailB -Password $PasswordB -Create:$CreateAccounts

$uidA = $userA.localId
$uidB = $userB.localId
Write-Host "Cuenta A: $EmailA  uid=$uidA"
Write-Host "Cuenta B: $EmailB  uid=$uidB"
Write-Host ''

$headersA = @{ Authorization = "Bearer $($userA.idToken)" }
$headersB = @{ Authorization = "Bearer $($userB.idToken)" }
$docId = 'cross-account-check-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)

# 1) A crea un documento propio.
$createOwn = Invoke-Json `
    -Method POST `
    -Uri "$firestoreBase/users/$uidA/reminders?documentId=$docId" `
    -Headers $headersA `
    -Body (New-FirestoreDocumentBody -Id $docId -OwnerUid $uidA -Title 'Prueba de acceso cruzado')
Write-Result -Name 'A crea un recordatorio propio' -Expected 200 -Actual $createOwn.Status -Detail $createOwn.Content

# 2) B intenta leer el árbol de A.
$readForeign = Invoke-Json -Method GET -Uri "$firestoreBase/users/$uidA/reminders" -Headers $headersB
Write-Result -Name 'B intenta leer los recordatorios de A' -Expected 403 -Actual $readForeign.Status -Detail $readForeign.Content

# 3) B intenta escribir en el árbol de A.
$writeForeign = Invoke-Json `
    -Method POST `
    -Uri "$firestoreBase/users/$uidA/reminders?documentId=intruso-$docId" `
    -Headers $headersB `
    -Body (New-FirestoreDocumentBody -Id "intruso-$docId" -OwnerUid $uidA -Title 'Intrusión')
Write-Result -Name 'B intenta escribir en el árbol de A' -Expected 403 -Actual $writeForeign.Status -Detail $writeForeign.Content

# 4) A intenta declarar ownerUid de B dentro de su propio árbol.
$spoofOwner = Invoke-Json `
    -Method POST `
    -Uri "$firestoreBase/users/$uidA/reminders?documentId=spoof-$docId" `
    -Headers $headersA `
    -Body (New-FirestoreDocumentBody -Id "spoof-$docId" -OwnerUid $uidB -Title 'Suplantación')
Write-Result -Name 'A intenta un documento con ownerUid de B' -Expected 403 -Actual $spoofOwner.Status -Detail $spoofOwner.Content

# 5) B lee su propio árbol (debe funcionar y estar vacío o solo con sus datos).
$readOwn = Invoke-Json -Method GET -Uri "$firestoreBase/users/$uidB/reminders" -Headers $headersB
Write-Result -Name 'B lee su propio árbol' -Expected 200 -Actual $readOwn.Status -Detail $readOwn.Content

# 6) A elimina su documento de prueba.
$cleanup = Invoke-Json -Method DELETE -Uri "$firestoreBase/users/$uidA/reminders/$docId" -Headers $headersA
Write-Result -Name 'A elimina su documento de prueba' -Expected 200 -Actual $cleanup.Status -Detail $cleanup.Content

Write-Host ''
if ($script:failed -eq 0) {
    Write-Host "RESULTADO: $($script:passed) comprobaciones correctas. Aislamiento verificado." -ForegroundColor Green
    exit 0
} else {
    Write-Host "RESULTADO: $($script:failed) comprobaciones fallaron. Revisá las reglas publicadas." -ForegroundColor Red
    exit 1
}
