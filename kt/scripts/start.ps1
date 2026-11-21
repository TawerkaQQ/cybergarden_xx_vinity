param(
    [int]$Port = 8081,
    [switch]$Desktop,
    [switch]$NoBrowser
)

$ErrorActionPreference = 'Stop'
$kt = Split-Path $PSScriptRoot -Parent
$root = Split-Path $kt -Parent
$logs = Join-Path $kt 'build\logs'
$started = Get-Date
$steps = if ($Desktop) { 4 } else { 3 }
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

function Start-Server([int]$ServerPort) {
    $classpath = Join-Path $kt 'server\build\install\server\lib\*'
    $options = @{
        FilePath = (Get-Java)
        ArgumentList = "-Dvinity.root=`"$root`" -Dvinity.port=$ServerPort -cp `"$classpath`" cybergarden.vinity.server.ApplicationKt"
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

Invoke-Gradle 'Веб-интерфейс: Kotlin/Wasm и сборка бандла' @(':app:wasmJsBrowserDistribution')
Invoke-Gradle 'Сервер' @(':server:installDist')

Write-Step 'Запуск сервера'
$serverPort = Find-FreePort $Port
if ($serverPort -ne $Port) { Write-Host "      порт $Port занят, беру $serverPort" -ForegroundColor Yellow }
$server = Start-Server $serverPort
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
Write-Host 'Остановить: stop.cmd'
if (-not $NoBrowser) { Start-Process $url }
