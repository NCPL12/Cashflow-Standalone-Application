[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$RuntimePath, [switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$runtimeRoot = (Resolve-Path -LiteralPath $RuntimePath).Path
foreach ($name in @('java.exe','javaw.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $runtimeRoot ('bin\' + $name)))) { throw "Runtime is missing bin\$name" }
}
if (-not $SkipBuild) {
    Push-Location $projectRoot
    try { & mvn.cmd -DskipTests package; if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' } }
    finally { Pop-Location }
}
$war = Join-Path $projectRoot 'target\neptune-cashflow.war'
if (-not (Test-Path -LiteralPath $war)) { throw 'Build target\neptune-cashflow.war first.' }
$tag = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,6)
$buildRoot = Join-Path $projectRoot ('dist\' + $tag)
$packageRoot = Join-Path $buildRoot 'Neptune Cashflow Finance'
New-Item -ItemType Directory -Path $packageRoot -Force | Out-Null
Get-ChildItem -LiteralPath (Join-Path $projectRoot 'packaging\template') -Force | Copy-Item -Destination $packageRoot -Recurse -Force
Copy-Item -LiteralPath $war -Destination (Join-Path $packageRoot 'app\neptune-cashflow.war')
Copy-Item -LiteralPath $runtimeRoot -Destination (Join-Path $packageRoot 'runtime') -Recurse
$zip = Join-Path $buildRoot ('Neptune-Cashflow-Finance-' + $tag + '.zip')
Compress-Archive -LiteralPath $packageRoot -DestinationPath $zip
Write-Host "Created installer ZIP: $zip" -ForegroundColor Green
