$ErrorActionPreference = 'Stop'
$GitBashRelativePath = 'bin\bash.exe'
$Script = Join-Path $PSScriptRoot 'mariposa.sh'

function Find-GitBash {
    $candidates = @()
    $git = Get-Command git -ErrorAction SilentlyContinue
    if ($null -ne $git) {
        $gitRoot = Split-Path (Split-Path $git.Source -Parent) -Parent
        $candidates += Join-Path $gitRoot $GitBashRelativePath
    }
    foreach ($root in @($env:ProgramFiles, ${env:ProgramFiles(x86)}, $env:LOCALAPPDATA)) {
        if ($root) { $candidates += Join-Path $root (Join-Path 'Git' $GitBashRelativePath) }
    }
    return $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
}

$bash = Find-GitBash
if ($null -eq $bash) {
    $fallback = Get-Command bash -ErrorAction SilentlyContinue
    if ($null -eq $fallback) {
        Write-Error 'Se requiere Git Bash (recomendado) o WSL para ejecutar mariposa.sh'
        exit 1
    }
    $bash = $fallback.Source
}

& $bash $Script @args
exit $LASTEXITCODE
