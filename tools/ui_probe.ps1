# ---------------------------------------------------------------------------
# Asistente de prueba manual sobre un dispositivo o emulador real.
#
# No forma parte de la aplicacion: sirve para inspeccionar la interfaz con
# uiautomator y enviar toques/texto por adb durante la validacion del APK.
# No contiene credenciales.
#
# Uso:
#   .\ui_probe.ps1 dump
#   .\ui_probe.ps1 tap    "Iniciar sesion"
#   .\ui_probe.ps1 type   "texto sin simbolos"
#   .\ui_probe.ps1 key    BACK
# ---------------------------------------------------------------------------
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet('dump', 'tap', 'type', 'clear', 'key', 'start', 'stop', 'screenshot')]
    [string]$Action,

    [Parameter(Position = 1)]
    [string]$Argument
)

$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$package = 'com.example.api1_eliasescalante_pm'
$uiFile = Join-Path $env:TEMP 'ui_probe.xml'

function Get-UiDocument {
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & $adb shell uiautomator dump /sdcard/ui_probe.xml 2>&1 | Out-Null
    & $adb pull /sdcard/ui_probe.xml $uiFile 2>&1 | Out-Null
    $ErrorActionPreference = $prev
    if (-not (Test-Path $uiFile)) {
        throw 'No se pudo obtener el arbol de interfaz.'
    }
    return [xml](Get-Content $uiFile -Raw -Encoding UTF8)
}

function Get-Nodes {
    (Get-UiDocument).SelectNodes('//node[@text!="" or @content-desc!=""]')
}

function Find-Node([string]$label) {
    $nodes = Get-Nodes
    $exact = $nodes | Where-Object { $_.text -eq $label -or $_.'content-desc' -eq $label }
    if ($exact) { return $exact | Select-Object -First 1 }

    $partial = $nodes | Where-Object {
        ($_.text -and $_.text -like "*$label*") -or
        ($_.'content-desc' -and $_.'content-desc' -like "*$label*")
    }
    return $partial | Select-Object -First 1
}

function Get-Center($node) {
    $m = [regex]::Match($node.bounds, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
    if (-not $m.Success) { throw "Nodo sin bounds: $($node.text)" }
    [pscustomobject]@{
        X = [int](([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2)
        Y = [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)
    }
}

switch ($Action) {
    'dump' {
        Get-Nodes | ForEach-Object {
            $c = Get-Center $_
            '{0,4},{1,-4} | {2}' -f $c.X, $c.Y, ($_.text + $_.'content-desc')
        }
    }
    'tap' {
        $node = Find-Node $Argument
        if (-not $node) { throw "No se encontro el texto: $Argument" }
        $c = Get-Center $node
        & $adb shell input tap $c.X $c.Y | Out-Null
        "tap '$($node.text)' en $($c.X),$($c.Y)"
    }
    'type' {
        # `input text` pierde caracteres con las pilas IME modernas y no
        # escribe '@'. Se envían eventos de tecla uno por uno.
        $map = @{
            ' ' = 62; '.' = 56; '@' = 77; '-' = 69; '_' = 69; '/' = 76
        }
        $failed = @()
        foreach ($ch in $Argument.ToCharArray()) {
            $code = $null
            if ($ch -cmatch '^[a-z]$') {
                $code = 29 + ([int][char]$ch - [int][char]'a')
            } elseif ($ch -cmatch '^[0-9]$') {
                $code = 7 + [int][string]$ch
            } elseif ($map.ContainsKey([string]$ch)) {
                $code = $map[[string]$ch]
            }
            if ($null -eq $code) {
                $failed += $ch
                continue
            }
            & $adb shell input keyevent $code 2>&1 | Out-Null
            Start-Sleep -Milliseconds 80
        }
        if ($failed.Count -gt 0) {
            throw "Caracteres no soportados: $($failed -join '')"
        }
        "texto enviado ($($Argument.Length) caracteres)"
    }
    'clear' {
        # Selecciona todo el campo enfocado y lo borra.
        & $adb shell input keycombination 113 29 2>&1 | Out-Null
        & $adb shell input keyevent 67 2>&1 | Out-Null
        'campo borrado'
    }
    'key' {
        & $adb shell input keyevent $Argument | Out-Null
        "keyevent $Argument"
    }
    'start' {
        & $adb shell am start -n "$package/.MainActivity" | Out-Null
        'actividad iniciada'
    }
    'stop' {
        & $adb shell am force-stop $package | Out-Null
        'aplicacion detenida'
    }
    'screenshot' {
        $target = if ($Argument) { $Argument } else { Join-Path $env:TEMP 'ui_probe.png' }
        & $adb exec-out screencap -p > $target
        "captura guardada en $target"
    }
}
