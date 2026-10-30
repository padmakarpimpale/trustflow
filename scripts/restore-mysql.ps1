param(
    [string]$DatabaseHost = "127.0.0.1",
    [int]$Port = 3307,
    [string]$Database = "trustflow_restore",
    [string]$Username = "root",
    [string]$InputFile = "database/trustflow-mysql-dump.sql"
)
$ErrorActionPreference = "Stop"
if ($Database -notmatch '^[A-Za-z0-9_]+$' -or $Database -eq "trustflow") {
    throw "Restore into a new database, not the running trustflow database."
}
$path = (Resolve-Path $InputFile).Path.Replace('\', '/')
if ($path -match '[;
]') { throw "Unsupported dump path" }
# CREATE DATABASE intentionally fails if the target already exists.
& mysql --host=$DatabaseHost --port=$Port --user=$Username --password `
    --execute="CREATE DATABASE $Database;"
if ($LASTEXITCODE -ne 0) { throw "Cannot create restore database; no data was replaced." }
& mysql --host=$DatabaseHost --port=$Port --user=$Username --password `
    --database=$Database --execute="source $path"
if ($LASTEXITCODE -ne 0) { throw "Restore failed. Inspect the separate restore database." }
Write-Host "Restored into $Database. Verify records before using it."
