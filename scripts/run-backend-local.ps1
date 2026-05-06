# Run from repository root: .\scripts\run-backend-local.ps1
# Prerequisites: MySQL running (e.g. docker compose up -d mysql) and `.env` at repo root.

$ErrorActionPreference = "Stop"
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$envFile = Join-Path $repoRoot ".env"

if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq "" -or $line.StartsWith("#")) { return }
        $eq = $line.IndexOf("=")
        if ($eq -lt 1) { return }
        $key = $line.Substring(0, $eq).Trim()
        $val = $line.Substring($eq + 1).Trim()
        if (($val.StartsWith('"') -and $val.EndsWith('"')) -or ($val.StartsWith("'") -and $val.EndsWith("'"))) {
            $val = $val.Substring(1, $val.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($key, $val, "Process")
    }
}

$port = if ($env:MYSQL_PORT) { $env:MYSQL_PORT } else { "3307" }
$db = if ($env:MYSQL_DATABASE) { $env:MYSQL_DATABASE } else { "sistema-esp8266" }
$user = if ($env:MYSQL_USER) { $env:MYSQL_USER } else { "sistema_iot" }
$pass = $env:MYSQL_PASSWORD
if (-not $pass) { throw "Defina MYSQL_PASSWORD no .env da raiz." }

$env:SPRING_DATASOURCE_URL = "jdbc:mysql://127.0.0.1:${port}/${db}?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Sao_Paulo"
$env:SPRING_DATASOURCE_USERNAME = $user
$env:SPRING_DATASOURCE_PASSWORD = $pass

if (-not $env:JWT_SECRET) { throw "Defina JWT_SECRET no .env da raiz." }

if (-not $env:SPRING_PROFILES_ACTIVE) {
    $env:SPRING_PROFILES_ACTIVE = "dev"
}

Write-Host "SPRING_DATASOURCE_URL=$($env:SPRING_DATASOURCE_URL)" -ForegroundColor DarkGray
Set-Location (Join-Path $repoRoot "sistema-esp8266\app")
& .\mvnw.cmd spring-boot:run
