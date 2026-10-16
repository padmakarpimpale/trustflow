# Run from the project root in PowerShell with MySQL client tools installed.
# mysqldump prompts for your database password. Do not put it in this script.
$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force database | Out-Null
mysqldump --host=127.0.0.1 --port=3306 --user=trustflow --password --single-transaction --no-tablespaces --result-file=database/trustflow-mysql-dump.sql trustflow
if ($LASTEXITCODE -ne 0) { throw "Database export failed" }
Write-Host "Saved database/trustflow-mysql-dump.sql"
