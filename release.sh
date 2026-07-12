#!/usr/bin/env bash
# ============================================================
#  release.sh - 一键发布新版本到 GitHub（Linux / macOS / WSL）
#  Usage:  ./release.sh v1.0.0
#          ./release.sh           # 交互式输入版本号
# ============================================================

set -e

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
GRAY='\033[0;90m'
NC='\033[0m' # No Color

step()  { echo -e "${CYAN}▶ $1${NC}"; }
ok()    { echo -e "${GREEN}✓ $1${NC}"; }
warn()  { echo -e "${YELLOW}⚠ $1${NC}"; }
err()   { echo -e "${RED}✗ $1${NC}"; }

# ---------- 1) 参数 ----------
VERSION="${1:-}"
if [ -z "$VERSION" ]; then
  read -p "请输入版本号（例 v1.0.0）: " VERSION
fi

if ! [[ "$VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?$ ]]; then
  err "版本号格式不正确！应为 v1.0.0 或 v1.0.0-beta.1"
  echo -e "  ${GRAY}示例：v1.0.0, v1.2.3, v2.0.0-rc.1${NC}"
  exit 1
fi

# ---------- 2) 依赖检查 ----------
command -v git >/dev/null 2>&1 || { err "未检测到 git，请先安装"; exit 1; }

# ---------- 3) 在 git 仓库中 ----------
if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  err "当前目录不是 git 仓库"
  exit 1
fi

# ---------- 4) 工作区干净 ----------
if [ -n "$(git status --porcelain)" ]; then
  warn "工作区有未提交的修改："
  git status --short
  read -p "是否继续？(y/N): " confirm
  [ "$confirm" = "y" ] || [ "$confirm" = "Y" ] || { echo "已取消"; exit 0; }
fi

# ---------- 5) 远程 ----------
REMOTE=$(git remote get-url origin 2>/dev/null || echo "")
[ -z "$REMOTE" ] && { err "未配置 origin 远程仓库"; exit 1; }
step "远程仓库：$REMOTE"

# ---------- 6) 拉取最新 ----------
step "拉取最新代码..."
git fetch origin
BRANCH=$(git rev-parse --abbrev-ref HEAD)
git pull origin "$BRANCH" --ff-only 2>/dev/null || warn "无法快进合并（可能存在本地提交）"

# ---------- 7) tag 重复检查 ----------
if git tag -l "$VERSION" | grep -q "$VERSION"; then
  err "Tag $VERSION 已存在！"
  echo -e "  ${GRAY}删除：git tag -d $VERSION && git push origin :refs/tags/$VERSION${NC}"
  exit 1
fi

# ---------- 8) 创建 + 推送 ----------
step "创建 tag $VERSION..."
git tag -a "$VERSION" -m "Release $VERSION"

step "推送 tag 到 origin（触发 CI）..."
git push origin "$VERSION"

ok "Tag $VERSION 已推送！"

# ---------- 9) 解析仓库地址 ----------
REPO_URL=""
if [[ "$REMOTE" =~ github\.com[:/](.+?)/(.+?)(\.git)?$ ]]; then
  OWNER="${BASH_REMATCH[1]}"
  REPO="${BASH_REMATCH[2]}"
  REPO_URL="https://github.com/$OWNER/$REPO"
else
  warn "无法解析 GitHub 仓库地址（不影响 tag 推送）"
fi

if [ -n "$REPO_URL" ]; then
  ACTIONS_URL="$REPO_URL/actions?query=workflow%3A%22Build+Android+APK%22"
  RELEASE_URL="$REPO_URL/releases/tag/$VERSION"

  echo ""
  step "GitHub Actions 构建监控："
  echo "    $ACTIONS_URL"
  echo ""
  step "构建完成后下载："
  echo "    $RELEASE_URL"
  echo ""

  read -p "是否打开 Actions 监控页？(Y/n): " open
  if [ -z "$open" ] || [ "$open" = "y" ] || [ "$open" = "Y" ]; then
    command -v xdg-open >/dev/null && xdg-open "$ACTIONS_URL" 2>/dev/null &
    command -v open     >/dev/null && open "$ACTIONS_URL" 2>/dev/null &
  fi
fi

echo ""
ok "全部完成！通常 3-5 分钟后可在 Releases 页面下载 APK"
[ -n "$REPO_URL" ] && echo -e "    ${GRAY}$REPO_URL/releases${NC}"
