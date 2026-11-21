$ErrorActionPreference = 'Stop'

$parts = [ordered]@{
    'cybergarden.vinity.server.ApplicationKt' = 'сервер'
    'cybergarden.vinity.app.MainKt' = 'desktop'
}

$stopped = 0
foreach ($process in Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'") {
    foreach ($mainClass in $parts.Keys) {
        if ($process.CommandLine -like "*$mainClass*") {
            Stop-Process -Id $process.ProcessId -Force
            Write-Host "Остановлен $($parts[$mainClass]), PID $($process.ProcessId)"
            $stopped++
        }
    }
}
if ($stopped -eq 0) { Write-Host 'Запущенных сервера и desktop нет' }
