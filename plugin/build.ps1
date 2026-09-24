$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$maven = Join-Path $root '.tools/apache-maven-3.9.11/bin/mvn.cmd'
if (-not (Test-Path -LiteralPath $maven)) {
    throw 'Maven fehlt. Installiere Maven und führe im plugin-Ordner mvn package aus.'
}
$jdk = 'C:\Program Files\Java\jdk-25.0.4.1'
if (Test-Path -LiteralPath $jdk) { $env:JAVA_HOME = $jdk }
& $maven -f (Join-Path $PSScriptRoot 'pom.xml') "-Dmaven.repo.local=$(Join-Path $root '.tools/m2')" --batch-mode package
if ($LASTEXITCODE -ne 0) { throw 'Plugin-Build fehlgeschlagen.' }
