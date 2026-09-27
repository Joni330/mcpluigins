$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
try {
    $rebasePath = git rev-parse --git-path rebase-merge
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $rebasePath)) {
        throw 'Kein laufender Rebase. Bitte die Git-Fehlermeldung pruefen.'
    }
    do {
        $conflicts = @(git diff --name-only --diff-filter=U)
        if ($LASTEXITCODE -ne 0) { throw 'Konflikte konnten nicht gelesen werden.' }
        if ($conflicts.Count -ne 1 -or $conflicts[0] -cne 'Casino.jar') {
            throw 'Kein reiner Casino.jar-Konflikt. Quellcode- und andere Konflikte muessen manuell geprueft werden.'
        }
        Write-Host 'Casino.jar wurde lokal und auf GitHub gebaut. Baue sie aus den zusammengefuehrten Quellen neu ...'
        & (Join-Path $PSScriptRoot 'build.ps1')
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'target/Casino-0.2.0.jar') -Destination 'Casino.jar' -Force
        git add -- Casino.jar
        if ($LASTEXITCODE -ne 0) { throw 'Casino.jar konnte nicht zum Commit hinzugefuegt werden.' }
        git -c core.editor=true rebase --continue
        if ($LASTEXITCODE -eq 0) { exit 0 }
        # Another commit can conflict with the generated binary; inspect it again before touching anything.
    } while (Test-Path -LiteralPath $rebasePath)
    throw 'Rebase wurde nicht erfolgreich abgeschlossen.'
} catch {
    Write-Host ('Automatische JAR-Konfliktloesung gestoppt: ' + $_.Exception.Message)
    exit 1
}
