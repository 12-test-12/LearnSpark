#!/usr/bin/env pwsh
# ============================================================
#  release.ps1 - 一键发布新版本到 GitHub
#  Usage:  .\release.ps1 v1.0.0
#          .\release.ps1            # 交互式输入版本号
# ============================================================

[CmdletBinding()]
param(
    [Parameter(Position=0)]
    [string]$Version = ""
)

$ErrorActionPreference = "Stop"

# 彩色输出
function Write-Step  { param($msg) Write-Host "▶ $msg" -ForegroundColor Cyan }
function Write-OK    { param($msg) Write-Host "✓ $msg" -ForegroundColor Green }
function Write-Warn  { param($msg) Write-Host "⚠ $msg" -ForegroundColor Yellow }
function Write-Err   { param($msg) Write-Host "✗ $msg" -ForegroundColor Red }

Clear-Host
Write-Host ""
Write-Host "  ╭──────────────────────────────────────╮" -ForegroundColor Cyan
Write-Host "  │   📦 LearnSpark Release Publisher     │" -ForegroundColor Cyan
Write-Host "  ╰──────────────────────────────────────╯" -ForegroundColor Cyan
Write-Host ""

# 1) 参数校验
if ([string]::IsNullOrWhiteSpace($Version)) {
    $Version = Read-Host "请输入版本号（例 v1.0.0）"
}

if ($Version -notmatch '^v\d+\.\d+\.\d+(-[a-zA-Z0-9.]+)?$') {
    Write-Err "版本号格式不正确！"
    Write-Host "  正确格式：v1.0.0 或 v1.0.0-beta.1" -ForegroundColor Gray
    Write-Host "  示例：v1.0.0, v1.2.3, v2.0.0-rc.1" -ForegroundColor Gray
    exit 1
}

# 2) 基础检查
if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Write-Err "未检测到 git，请先安装 Git for Windows"
    exit 1
}

try { git rev-parse --is-inside-work-tree | Out-Null }
catch {
    Write-Err "当前目录不是 git 仓库"
    exit 1
}

# 3) 远程仓库
$remote = git remote get-url origin 2>$null
if (-not $remote) {
    Write-Err "未配置 origin 远程仓库"
    exit 1
}
Write-Step "远程仓库：$remote"

# 4) 解析 GitHub owner/repo
$owner = $null
$repo = $null
if ($remote -match 'github\.com[:/](.+?)/(.+?)(?:\.git)?$') {
    $owner = $Matches[1]
    $repo  = $Matches[2]
    $repoUrl = "https://github.com/$owner/$repo"
} else {
    Write-Err "远程仓库不是 GitHub（无法使用本脚本）"
    exit 1
}
Write-OK "识别为 GitHub 仓库：$owner/$repo"

# 5) 拉取最新
Write-Step "拉取最新代码..."
git fetch origin | Out-Null
$branch = git rev-parse --abbrev-ref HEAD
if ($branch -ne 'main' -and $branch -ne 'master') {
    Write-Warn "当前分支是 $branch（不是 main/master），但仍可继续"
}
git pull origin $branch --ff-only 2>$null

# 6) 检查工作区
$status = git status --porcelain
if ($status) {
    Write-Warn "工作区有未提交的修改："
    Write-Host $status -ForegroundColor Gray
    $confirm = Read-Host "是否继续？(y/N)"
    if ($confirm -ne 'y' -and $confirm -ne 'Y') {
        Write-Host "已取消" -ForegroundColor Yellow
        exit 0
    }
}

# 7) 检查 tag 是否已存在
if (git tag -l $Version) {
    Write-Err "Tag $Version 已存在！"
    Write-Host "  删除本地：    git tag -d $Version" -ForegroundColor Gray
    Write-Host "  删除远程：    git push origin :refs/tags/$Version" -ForegroundColor Gray
    exit 1
}

# 8) 询问是否立即触发（避免误操作）
Write-Host ""
Write-Host "  即将执行：" -ForegroundColor White
Write-Host "    1. 创建本地 tag:    $Version" -ForegroundColor Gray
Write-Host "    2. 推送到 origin" -ForegroundColor Gray
Write-Host "    3. 打开浏览器监控 CI" -ForegroundColor Gray
Write-Host ""
$go = Read-Host "确认发布？(Y/n)"
if ($go -eq 'n' -or $go -eq 'N') {
    Write-Host "已取消" -ForegroundColor Yellow
    exit 0
}

# 9) 创建 + 推送 tag
Write-Step "创建 tag $Version..."
git tag -a $Version -m "Release $Version"

Write-Step "推送到 origin（触发 CI）..."
git push origin $Version
if ($LASTEXITCODE -ne 0) {
    Write-Err "推送失败！请检查网络和权限"
    exit 1
}

Write-OK "Tag $Version 已推送！"

# 10) 打开监控
$actionsUrl  = "$repoUrl/actions/workflows/android.yml"
$releaseUrl  = "$repoUrl/releases/tag/$Version"

Write-Host ""
Write-Step "📊 CI 监控："
Write-Host "    $actionsUrl" -ForegroundColor Gray
Write-Host ""
Write-Step "📥 发布完成下载："
Write-Host "    $releaseUrl" -ForegroundColor Gray
Write-Host ""

# 自动打开
Start-Process $actionsUrl

Write-Host ""
Write-OK "🎉 全部完成！通常 3-5 分钟后可在 Releases 页面下载 APK" -ForegroundColor Green
Write-Host ""
Write-Host "  📱 完成后访问：$repoUrl/releases/latest" -ForegroundColor Cyan
Write-Host ""
