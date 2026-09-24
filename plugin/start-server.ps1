$ErrorActionPreference = 'Stop'
$server = Join-Path (Split-Path $PSScriptRoot -Parent) 'server'
$java = 'C:\Program Files\Java\jdk-25.0.4.1\bin\java.exe'
if (-not (Test-Path -LiteralPath $java)) { $java = 'java' }
$eula = Join-Path $server 'eula.txt'
if (-not (Test-Path -LiteralPath $eula) -or -not (Select-String -LiteralPath $eula -Pattern '^eula=true$' -Quiet)) {
    throw 'Bitte zuerst die Minecraft-EULA lesen und bei Zustimmung in server/eula.txt eula=true setzen.'
}
Push-Location $server
try { & $java '-Xms1G' '-Xmx3G' '-jar' 'paper.jar' '--nogui' }
finally { Pop-Location }
