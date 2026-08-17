param(
    [ValidateSet("start", "stop", "reload", "status")]
    [string]$Action = "start"
)

$nginxRoot = Join-Path $PSScriptRoot "nginx-1.18.0"
$nginxExe = Join-Path $nginxRoot "nginx.exe"

if (-not (Test-Path -LiteralPath $nginxExe)) {
    Write-Error "nginx.exe was not found at $nginxExe"
    exit 1
}

switch ($Action) {
    "start" {
        if (Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue) {
            Write-Host "Frontend is already running at http://127.0.0.1:8080"
            exit 0
        }

        & $nginxExe -p "$nginxRoot\"
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }

        Start-Sleep -Seconds 1
        Write-Host "Frontend started at http://127.0.0.1:8080"
    }
    "stop" {
        & $nginxExe -p "$nginxRoot\" -s quit
        Write-Host "Frontend stop requested."
    }
    "reload" {
        & $nginxExe -p "$nginxRoot\" -s reload
        Write-Host "Frontend configuration reloaded."
    }
    "status" {
        $listener = Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue
        if ($listener) {
            Write-Host "Frontend is running at http://127.0.0.1:8080"
        } else {
            Write-Host "Frontend is stopped."
        }
    }
}
