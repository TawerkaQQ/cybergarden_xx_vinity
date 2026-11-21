param(
    [string]$Out
)

$ErrorActionPreference = 'Stop'
$kt = Split-Path $PSScriptRoot -Parent
$root = Split-Path $kt -Parent
if (-not $Out) { $Out = Join-Path $kt 'build\release' }
$started = Get-Date

function Read-LocalSettings {
    $settings = @{}
    $file = Join-Path $kt 'local.properties'
    if (Test-Path $file) {
        foreach ($line in Get-Content $file -Encoding UTF8) {
            if ($line -match '^\s*([\w.]+)\s*=\s*(.*?)\s*$') { $settings[$Matches[1]] = $Matches[2] }
        }
    }
    $settings
}

function ConvertTo-PropertyPath([string]$Path) { $Path -replace '\\', '/' }

function Write-Utf8([string]$Path, [string[]]$Lines) {
    [System.IO.File]::WriteAllLines($Path, $Lines, [System.Text.UTF8Encoding]::new($false))
}

Write-Host 'Сборка: сайт, сервер и desktop' -ForegroundColor Cyan
Push-Location $kt
try {
    & .\gradlew.bat ':app:wasmJsBrowserDistribution' ':server:installDist' ':app:createDistributable' --console=plain --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Сборка не прошла' }
} finally {
    Pop-Location
}

New-Item -ItemType Directory -Force $Out | Out-Null
foreach ($part in 'server', 'web', 'desktop') {
    $target = Join-Path $Out $part
    if (Test-Path $target) { Remove-Item -Recurse -Force $target }
}
Copy-Item -Recurse (Join-Path $kt 'server\build\install\server') (Join-Path $Out 'server')
Copy-Item -Recurse (Join-Path $kt 'app\build\dist\wasmJs\productionExecutable') (Join-Path $Out 'web')
Copy-Item -Recurse (Join-Path $kt 'app\build\compose\binaries\main\app\Vinity') (Join-Path $Out 'desktop')

$config = Join-Path $Out 'vinity.properties'
if (-not (Test-Path $config)) {
    $local = Read-LocalSettings
    Write-Utf8 $config @(
        "root=$(ConvertTo-PropertyPath $root)",
        'port=8081',
        "web=$(ConvertTo-PropertyPath (Join-Path $Out 'web'))",
        "model.url=$($local['model.url'])",
        "model.name=$($local['model.name'])",
        "model.key=$($local['model.key'])",
        "rag.url=$($local['rag.url'])",
        'python='
    )
}
$port = ((Get-Content $config -Encoding UTF8) | Where-Object { $_ -match '^\s*port\s*=' } | ForEach-Object { ($_ -split '=', 2)[1].Trim() } | Select-Object -First 1)
if (-not $port) { $port = '8081' }
Write-Utf8 (Join-Path $Out 'desktop\vinity.properties') @("api.url=http://localhost:$port")
Write-Utf8 (Join-Path $Out 'server.cmd') @(
    '@echo off',
    'cd /d "%~dp0"',
    'set "VINITY_CONFIG=%~dp0vinity.properties"',
    'call "%~dp0server\bin\server.bat"'
)
Write-Utf8 (Join-Path $Out 'desktop.cmd') @(
    '@echo off',
    'start "" "%~dp0desktop\Vinity.exe"'
)

$seconds = [int]((Get-Date) - $started).TotalSeconds
Write-Host ''
Write-Host "Готово за $seconds с: $Out" -ForegroundColor Green
Write-Host "Сервер и сайт: $Out\server.cmd, адрес http://localhost:$port"
Write-Host "Desktop: $Out\desktop.cmd (адрес сервера — api.url в desktop\vinity.properties)"
Write-Host "Настройки сервера: $config"
