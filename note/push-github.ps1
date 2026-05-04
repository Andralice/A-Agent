#Requires -Version 5.1
<#
.SYNOPSIS
  Git add -A, commit if needed, push (repo root = parent of note/).

.DESCRIPTION
  Run from repo root, e.g.:
    powershell -ExecutionPolicy Bypass -File .\note\push-github.ps1 -Message "chore: sync"
  PowerShell 7: pwsh -File .\note\push-github.ps1 ...

.PARAMETER Message
  git commit -m message (default: timestamped chore: sync)

.PARAMETER SkipPush
  Commit only; do not git push.
.EXAMPLE
  .\note\push-github.ps1 -Message "chore: sync"
#>
param(
    [string]$Message = "",
    [switch]$SkipPush
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path -LiteralPath (Join-Path $Root ".git"))) {
    Write-Error "Not a git repository: $Root"
}

if ([string]::IsNullOrWhiteSpace($Message)) {
    $Message = "chore: sync $(Get-Date -Format 'yyyy-MM-dd HH:mm')"
}

Push-Location -LiteralPath $Root
try {
    git add -A
    $pending = git status --porcelain
    if (-not $pending) {
        Write-Host "No changes to commit."
        if (-not $SkipPush) {
            Write-Host "Running git push..."
            git push
        }
        exit 0
    }

    git commit -m $Message
    if (-not $SkipPush) {
        git push
    }
    Write-Host "Done."
}
finally {
    Pop-Location
}
