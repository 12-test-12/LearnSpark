# ============================================================
#  LearnSpark 本地打包 & 收集脚本
#  用法: .\scripts\build-release.ps1
#  可选: .\scripts\build-release.ps1 -SkipClean (不清除上次构建)
#        .\scripts\build-release.ps1 -Targets "desktop,server"
# ============================================================

param(
    [switch]$SkipClean,
    [string]$Targets = "desktop,server"
)

$ErrorActionPreference = "Stop"
$RootDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$KmpDir = Join-Path $RootDir "learnspark-kmp"
$ReleasesDir = Join-Path $RootDir "releases"
$Version = "1.0.0"   # 与 composeApp/build.gradle.kts 中的 packageVersion 保持一致

# ---------- 准备 ----------
Write-Host "===== LearnSpark Release Builder =====" -ForegroundColor Cyan
Write-Host "Version : $Version"
Write-Host "Targets : $Targets"
Write-Host "Releases: $ReleasesDir"
Write-Host ""

# 创建 releases 目录
if (-not (Test-Path $ReleasesDir)) {
    New-Item -ItemType Directory -Path $ReleasesDir | Out-Null
    Write-Host "[OK] Created $ReleasesDir" -ForegroundColor Green
}

# 清理旧产物
if (-not $SkipClean) {
    Write-Host "[...] Cleaning previous release artifacts..." -ForegroundColor Yellow
    Remove-Item -Path "$ReleasesDir\*" -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host "[OK] Cleaned" -ForegroundColor Green
}

# ---------- Desktop EXE ----------
if ($Targets -match "desktop") {
    Write-Host ""
    Write-Host "===== [1/2] Building Desktop EXE =====" -ForegroundColor Cyan

    Push-Location $KmpDir
    try {
        & .\gradlew.bat :composeApp:packageExe --no-daemon --console=plain 2>&1 | Out-Host
        if ($LASTEXITCODE -ne 0) { throw "Desktop build failed" }
    }
    finally {
        Pop-Location
    }

    # 定位 EXE 产物
    $ExeSearchDir = Join-Path $KmpDir "composeApp\build\compose\binaries\main"
    $ExeFile = Get-ChildItem -Path $ExeSearchDir -Recurse -Filter "*.exe" -ErrorAction SilentlyContinue |
               Sort-Object LastWriteTime -Descending |
               Select-Object -First 1

    if ($ExeFile) {
        $DestExe = Join-Path $ReleasesDir "LearnSpark-$Version.exe"
        Copy-Item -Path $ExeFile.FullName -Destination $DestExe -Force
        Write-Host "[OK] Desktop EXE -> $DestExe" -ForegroundColor Green
    }
    else {
        Write-Host "[WARN] No EXE found! Searched: $ExeSearchDir" -ForegroundColor Yellow
    }

    # 也检查 MSI
    $MsiFile = Get-ChildItem -Path $ExeSearchDir -Recurse -Filter "*.msi" -ErrorAction SilentlyContinue |
               Sort-Object LastWriteTime -Descending |
               Select-Object -First 1
    if ($MsiFile) {
        $DestMsi = Join-Path $ReleasesDir "LearnSpark-$Version.msi"
        Copy-Item -Path $MsiFile.FullName -Destination $DestMsi -Force
        Write-Host "[OK] Desktop MSI -> $DestMsi" -ForegroundColor Green
    }
}

# ---------- Server BootJar ----------
if ($Targets -match "server") {
    Write-Host ""
    Write-Host "===== [2/2] Building Server BootJar =====" -ForegroundColor Cyan

    Push-Location $KmpDir
    try {
        & .\gradlew.bat :server:bootJar --no-daemon --console=plain 2>&1 | Out-Host
        if ($LASTEXITCODE -ne 0) { throw "Server build failed" }
    }
    finally {
        Pop-Location
    }

    $JarSearchDir = Join-Path $KmpDir "server\build\libs"
    $JarFile = Get-ChildItem -Path $JarSearchDir -Filter "*.jar" -ErrorAction SilentlyContinue |
               Where-Object { $_.Name -notmatch "plain" } |
               Sort-Object LastWriteTime -Descending |
               Select-Object -First 1

    if ($JarFile) {
        $DestJar = Join-Path $ReleasesDir "server-$Version.jar"
        Copy-Item -Path $JarFile.FullName -Destination $DestJar -Force
        Write-Host "[OK] Server JAR -> $DestJar" -ForegroundColor Green
    }
    else {
        Write-Host "[WARN] No server JAR found! Searched: $JarSearchDir" -ForegroundColor Yellow
    }
}

# ---------- 汇总 ----------
Write-Host ""
Write-Host "===== Done =====" -ForegroundColor Cyan
Write-Host "Release artifacts in: $ReleasesDir" -ForegroundColor White
Get-ChildItem -Path $ReleasesDir | ForEach-Object {
    $size = "{0:N1} MB" -f ($_.Length / 1MB)
    Write-Host "  $($_.Name)  ($size)" -ForegroundColor White
}
