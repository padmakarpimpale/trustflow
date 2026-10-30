param(
    [string]$DatabaseHost = "127.0.0.1",
    [int]$Port = 3307,
    [string]$Database = "trustflow",
    [string]$Username = "trustflow",
    [string]$OutputFile = "database/trustflow-mysql-dump.sql"
)
$ErrorActionPreference = "Stop"
$folder = Split-Path -Parent $OutputFile
if ($folder) { New-Item -ItemType Directory -Force $folder | Out-Null }
& mysqldump --host=$DatabaseHost --port=$Port --user=$Username --password `
    --single-transaction --no-tablespaces --set-gtid-purged=OFF `
    --result-file=$OutputFile $Database
if ($LASTEXITCODE -ne 0) { throw "Database export failed" }
Write-Host "Saved $OutputFile"
