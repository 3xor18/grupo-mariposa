$bash = Get-Command bash -ErrorAction SilentlyContinue
if ($null -eq $bash) { Write-Error "Se requiere bash (Git Bash o WSL) para ejecutar mariposa.sh"; exit 1 }
& $bash.Source "$PSScriptRoot/mariposa.sh" @args
exit $LASTEXITCODE
