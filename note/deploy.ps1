# 在「项目根目录」执行（与 pom.xml 同级）时使用 Git Bash 调用 deploy.sh，避免手写 ssh 引号逃逸。
#
# PowerShell（项目根目录）:
#   Set-ExecutionPolicy -Scope Process Bypass
#   .\note\deploy.ps1
#
# 修改部署目标：编辑 note/deploy.sh 顶部「配置区」。
# systemd 发布：先在服务器安装 note/novel-agent.service.example + novel-agent.env，再设环境变量：
#   $env:DEPLOY_MODE = "systemd"
#   .\note\deploy.ps1

param()

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

function QuoteForBashSingle {
    param([string]$Path)
    $Path -replace "'", "'\''"
}

$BashExe = $null
$bashCmd = Get-Command bash -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
if ($bashCmd) { $BashExe = $bashCmd.Source }
if (-not $BashExe) {
    $BashExe = @(
        "${env:ProgramFiles}\Git\bin\bash.exe",
        "${env:ProgramFiles(x86)}\Git\bin\bash.exe",
        "${env:LocalAppData}\Programs\Git\bin\bash.exe"
    ) | Where-Object { Test-Path $_ } | Select-Object -First 1
}

if (-not $BashExe) {
    throw "未找到 Git Bash（bash.exe）。请安装 Git for Windows，或直接使用 WSL/Git Bash 运行: chmod +x note/deploy.sh && ./note/deploy.sh"
}

$Cwd = QuoteForBashSingle $RepoRoot
& $BashExe -lc "cd '$Cwd' && chmod +x note/deploy.sh && ./note/deploy.sh"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
