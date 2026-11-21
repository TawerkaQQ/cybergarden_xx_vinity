param(
    [int]$Port = 8081,
    [switch]$Desktop,
    [switch]$Dev,
    [switch]$NoBrowser,
    [string]$ModelUrl,
    [string]$Model
)

$ErrorActionPreference = 'Stop'
$kt = Split-Path $PSScriptRoot -Parent
$root = Split-Path $kt -Parent
$logs = Join-Path $kt 'build\logs'
$started = Get-Date
$pipelinePython = Join-Path $root 'pipeline\.venv\Scripts\python.exe'
$needsPipeline = -not (Test-Path $pipelinePython)
$steps = 3 + [int][bool]$Desktop + [int]$needsPipeline
$step = 0

function Write-Step([string]$Title) {
    $script:step++
    Write-Host ''
    Write-Host "[$script:step/$steps] $Title" -ForegroundColor Cyan
}

function Write-Done([datetime]$Since) {
    $seconds = [int]((Get-Date) - $Since).TotalSeconds
    Write-Host "      готово за $seconds с" -ForegroundColor Green
}

function Test-PortFree([int]$Candidate) {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Any, $Candidate)
    try { $listener.Start(); $true } catch { $false } finally { $listener.Stop() }
}

function Test-Listening([int]$Candidate) {
    $client = [System.Net.Sockets.TcpClient]::new()
    try { $client.Connect('127.0.0.1', $Candidate); $true } catch { $false } finally { $client.Dispose() }
}

function Find-FreePort([int]$From) {
    $candidate = $From
    while (-not (Test-PortFree $candidate)) { $candidate++ }
    $candidate
}

function Get-Java {
    if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
}

function Invoke-Gradle([string]$Title, [string[]]$Tasks) {
    Write-Step $Title
    Write-Host "      gradlew $($Tasks -join ' ')" -ForegroundColor DarkGray
    $since = Get-Date
    & (Join-Path $kt 'gradlew.bat') @Tasks
    if ($LASTEXITCODE -ne 0) { throw "Сборка не прошла: $($Tasks -join ' ')" }
    Write-Done $since
}

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

function Find-Model([string]$BaseUrl) {
    try {
        (Invoke-RestMethod -Uri "$($BaseUrl.TrimEnd('/'))/models" -TimeoutSec 15).data[0].id
    } catch {
        Write-Host "      модель по адресу $BaseUrl не отвечает: $($_.Exception.Message)" -ForegroundColor Yellow
        $null
    }
}

function Initialize-Pipeline {
    Write-Step 'Окружение конвейера: pipeline\.venv'
    $since = Get-Date
    $pipeline = Join-Path $root 'pipeline'
    & python -m venv (Join-Path $pipeline '.venv')
    if ($LASTEXITCODE -eq 0) { & $pipelinePython -m pip install --quiet --timeout 120 --retries 5 -r (Join-Path $pipeline 'requirements.txt') }
    if ($LASTEXITCODE -ne 0) {
        Write-Host '      окружение не собралось: статьи будут загружаться без обработки' -ForegroundColor Yellow
        return
    }
    Write-Done $since
}

function Start-Server([int]$ServerPort, [string[]]$Settings) {
    $classpath = Join-Path $kt 'server\build\install\server\lib\*'
    $properties = ($Settings | ForEach-Object { "-D$_" }) -join ' '
    $options = @{
        FilePath = (Get-Java)
        ArgumentList = "-Dvinity.root=`"$root`" -Dvinity.port=$ServerPort $properties -cp `"$classpath`" cybergarden.vinity.server.ApplicationKt"
        WorkingDirectory = $kt
        WindowStyle = 'Hidden'
        PassThru = $true
        RedirectStandardOutput = (Join-Path $logs 'server.log')
        RedirectStandardError = (Join-Path $logs 'server-error.log')
    }
    Start-Process @options
}

function Wait-Server($Process, [int]$ServerPort) {
    $since = Get-Date
    foreach ($attempt in 1..240) {
        if ($Process.HasExited) { Write-Host ''; return $false }
        if (Test-Listening $ServerPort) {
            Write-Host ''
            Write-Done $since
            return $true
        }
        $seconds = [int]((Get-Date) - $since).TotalSeconds
        Write-Host "`r      ждём, пока сервер начнёт принимать запросы: $seconds с" -NoNewline
        Start-Sleep -Milliseconds 500
    }
    Write-Host ''
    $false
}

function Start-Desktop([string]$ApiUrl) {
    Write-Step 'Desktop-приложение'
    $options = @{
        FilePath = (Join-Path $kt 'gradlew.bat')
        ArgumentList = ":app:run -Pvinity.api=$ApiUrl"
        WorkingDirectory = $kt
        WindowStyle = 'Normal'
    }
    Start-Process @options
    Write-Host '      собирается в отдельном окне, там же виден ход сборки; приложение откроется само'
}

Write-Host 'Останавливаю прежний запуск' -ForegroundColor Cyan
& (Join-Path $PSScriptRoot 'stop.ps1')
New-Item -ItemType Directory -Force $logs | Out-Null

if ($Dev) { Invoke-Gradle 'Веб-интерфейс: Kotlin/Wasm, отладочная сборка без оптимизации' @(':app:wasmJsBrowserDevelopmentExecutableDistribution') }
else { Invoke-Gradle 'Веб-интерфейс: Kotlin/Wasm и сборка бандла' @(':app:wasmJsBrowserDistribution') }
Invoke-Gradle 'Сервер' @(':server:installDist')
if ($needsPipeline) { Initialize-Pipeline }

$local = Read-LocalSettings
$modelUrl = if ($ModelUrl) { $ModelUrl } else { $local['model.url'] }
$modelName = if ($Model) { $Model } elseif ($local['model.name']) { $local['model.name'] } elseif ($modelUrl) { Find-Model $modelUrl }
$serverSettings = @()
if ($modelUrl) { $serverSettings += "vinity.model.url=`"$modelUrl`"" }
if ($modelName) { $serverSettings += "vinity.model.name=`"$modelName`"" }
if ($local['model.key']) { $serverSettings += "vinity.model.key=`"$($local['model.key'])`"" }
if ($local['rag.url']) { $serverSettings += "vinity.rag.url=`"$($local['rag.url'])`"" }

Write-Step 'Запуск сервера'
$serverPort = Find-FreePort $Port
if ($serverPort -ne $Port) { Write-Host "      порт $Port занят, беру $serverPort" -ForegroundColor Yellow }
$server = Start-Server $serverPort $serverSettings
if (-not (Wait-Server $server $serverPort)) {
    Write-Host '      сервер не запустился, последние строки логов:' -ForegroundColor Red
    Get-Content (Join-Path $logs 'server.log'), (Join-Path $logs 'server-error.log') -Tail 30
    throw "Сервер не запустился, логи: $logs"
}

$url = "http://localhost:$serverPort"
if ($Desktop) { Start-Desktop $url }

$total = [int]((Get-Date) - $started).TotalSeconds
Write-Host ''
Write-Host "Готово за $total с. Сайт: $url" -ForegroundColor Green
Write-Host "Сервер работает в фоне, PID $($server.Id), лог $logs\server.log"
if ($modelUrl) { Write-Host "Конвейер: модель $modelName, $modelUrl" }
else { Write-Host 'Конвейер не настроен: загруженные статьи не обрабатываются. Адрес модели — model.url в kt\local.properties или -ModelUrl' -ForegroundColor Yellow }
if ($local['rag.url']) { Write-Host "Вопросы по статьям: модуль РАГ $($local['rag.url'])" }
else { Write-Host 'Вопросы по статьям: поиск по статьям и модель конвейера. Модуль РАГ подключается адресом rag.url в kt\local.properties' }
Write-Host 'Остановить: stop.cmd'
if (-not $NoBrowser) { Start-Process $url }
