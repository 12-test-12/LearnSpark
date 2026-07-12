# 🔧 常见问题排查

> 解决 99% 的"为什么我的 Release 没出来 / 下载链接 404 / APK 装不上"问题

---

## 📌 1. 仓库页面只显示"1 个标签"，没有 Release

### 现象

打开 GitHub 仓库主页 → 右侧 **发布** 区域：

> 🏷️ 发布
> 🔖 1 个 标签
> [+ 创建新版本]

没有看到任何带 APK 附件的 release。

### 原因

GitHub 区分两个概念：

| 概念 | 位置 | 何时出现 |
|---|---|---|
| **Git Tag** | 仓库 → Tags 页面 | 手动 `git tag xxx` 推送，或 CI 创建 |
| **GitHub Release** | 仓库主页右侧"发布" | 必须在 Tag 基础上**手动或自动创建** |

只创建 Tag 不会自动生成 Release。本项目通过 CI 自动创建 Release，需要 workflow 跑成功。

### 解决

**方案 A（最简单）**：让 CI 自动跑

```bash
# 确保代码推到了 main 分支
git add .
git commit -m "test: 触发 Android 构建"
git push origin main
```

3-5 分钟后：
1. 打开 `仓库 → Actions` 看是否在跑
2. 跑成功后，`仓库 → Releases` 会出现新版本
3. `仓库主页右侧的 发布` 区域会显示 "Latest" 标签

**方案 B（手动）**：

1. 进入 `仓库 → Tags` → 找到那个 "1 个标签" 里的 tag
2. 点击右侧 **⋯** → **Create release**
3. 手动填写 Title / 描述
4. 拖入 APK 文件
5. 点 **Publish release**

---

## 📌 2. workflow 跑失败了

### 现象

`Actions` 页面显示红色 ❌，某一步失败。

### 排查

1. 点开失败的 run
2. 找到 **红色 ❌ 的步骤**
3. 点击查看日志末尾的错误信息

### 常见错误

| 错误 | 原因 | 解决 |
|---|---|---|
| `Could not find method android()` | build.gradle 注入位置错 | 已修复，确保 `android.yml` 是最新版本 |
| `SDK location not found` | CI 没装 Android SDK | 已用 `android-actions/setup-android@v3` 处理 |
| `Keystore was tampered with` | 密码错 | 检查 `KEYSTORE_PASSWORD` Secret |
| `npm ci` 失败 | lock 文件过期 | 删除 `package-lock.json` 后 `npm install` 重新生成 |
| `Could not GET 'https://repo.maven.apache.org/...'` | 网络问题 | 重跑 workflow |

---

## 📌 3. Release 创建了但下载链接 404

### 现象

`https://github.com/OWNER/REPO/releases/latest` 返回 404。

### 原因

`/releases/latest` 只跳转到 **最新的非 prerelease 版本**。如果只有 prerelease，GitHub 会返回 404。

### 解决

本项目最新版 workflow 已设置 `prerelease: false`，所以 `/releases/latest` 一定会跳转。

如果你升级了 workflow 还是 404：
1. 确认 release 不是 draft 状态（`仓库 → Releases → 你的版本 → Edit → 取消 "This is a draft"`）
2. 确认 release 不是 prerelease（同上路径，取消 "This is a pre-release"）

---

## 📌 4. 下载按钮点不进去

### 现象

README 里的"下载 Latest APK"按钮点击后报 404。

### 原因

README 里的链接是相对路径 `../../releases/latest`，**只有当 README 渲染在 GitHub 仓库页面**时才会被解析。

如果你是本地编辑器（如 VS Code）预览 Markdown，链接会失效——这是正常的，**只在 GitHub 仓库页面上才工作**。

---

## 📌 5. APK 下载到手机装不上

### 现象

点击 APK → 提示"应用未安装"或"解析包出现问题"。

### 原因

- 旧版本签名不同 → 需要先卸载
- Android 版本过低（< 5.0）
- APK 损坏（下载中断）

### 解决

```bash
# 1. 先卸载旧版本（注意：会丢失本地数据）
adb uninstall com.learnspark.app

# 2. 重新下载 APK（不要用浏览器下载器，可能破坏文件）
#    用 curl 或 wget 下载
curl -L -o learnspark.apk "https://github.com/OWNER/REPO/releases/latest/download/learnspark-x.x.x.apk"

# 3. 检查 APK 是否完整
ls -lh learnspark.apk  # 大小应该 ~8MB
file learnspark.apk    # 应该显示 "Android package (APK)"

# 4. 用 ADB 安装（推荐）
adb install learnspark.apk
```

### 签名冲突

如果是从 debug 签名版本升级到 release 签名版本（反之亦然），**必须先卸载旧版本**。生产环境请保持 keystore 不变。

---

## 📌 6. Android 提示"未知来源"

首次安装需要允许。流程：

1. 点击 APK
2. 系统弹窗 → 提示"此来源未知"
3. 点击 **设置** → 打开 **允许此来源**
4. 返回重试

或者：

`设置 → 安全 → 更多安全设置 → 安装未知应用 → 选择你的浏览器/文件管理器 → 允许`

---

## 📌 7. Release 太多导致仓库杂乱

每次 push main 都会创建一个 release。可以用 cleanup workflow 自动清理。

新建 `.github/workflows/cleanup-releases.yml`：

```yaml
name: Cleanup Old Releases
on:
  schedule:
    - cron: '0 3 * * 0'  # 每周日凌晨 3 点
  workflow_dispatch:      # 也可手动触发
jobs:
  cleanup:
    runs-on: ubuntu-latest
    steps:
      - name: 删除超过 30 天的 non-tag build releases
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          # 列出所有 release，删除 30 天前创建的、非 v* tag 的 release
          gh release list --limit 100 --json tagName,createdAt | \
            jq -r '.[] | select(.tagName | startswith("v") | not) | select(.createdAt < (now - 30*86400 | todate)) | .tagName' | \
            while read tag; do
              echo "删除 $tag"
              gh release delete "$tag" --yes --cleanup-tag
            done
```

**保留策略**：
- `v*` tag（正式版）→ 永远保留
- `build-*` tag（开发版）→ 30 天后自动清理
- 总 release 数控制在 ~10 个以内

---

## 📌 8. 想看完整的发布流程演示

第一次使用建议按这个顺序：

```bash
# 1. 提交代码
git add .
git commit -m "feat: 添加新功能"
git push origin main

# 2. 等 3-5 分钟
#    打开 仓库 → Actions → 看 Build Android APK 是否在跑

# 3. 跑成功后
#    打开 仓库 → Releases → 应该能看到一个 build-xxx 的 release
#    展开 Assets → 下载 .apk

# 4. 试用满意后打正式版 tag
.\release.ps1 v1.0.0
# 等 5 分钟
# Releases 页面会出现 v1.0.0，标记为 Latest
```

---

## 📌 9. CI 跑成功了但 Release 没创建

### 排查步骤

1. 打开 `仓库 → Actions → 点击成功的 run`
2. 找到 **"创建 GitHub Release"** 这一步
3. 查看输出日志

### 常见失败

| 错误日志 | 原因 | 解决 |
|---|---|---|
| `Error: Resource not accessible by integration` | workflow 权限不足 | 在 workflow 顶部加 `permissions: contents: write` |
| `tag_name already exists` | tag 冲突 | 删掉旧 tag：`git push origin :refs/tags/xxx` |
| `No files found` | APK 没生成 | 查 gradle 构建步骤的日志 |

### 检查权限

确认 `.github/workflows/android.yml` 顶部有：

```yaml
permissions:
  contents: write
```

---

## 📌 10. 完全卡住了怎么办

1. **删除所有 release 和 tag，重新跑**
   ```bash
   # 远程删除所有 tag
   git tag -l | xargs -I {} git push origin :refs/tags/{}
   # 远程删除所有 release（需要 GitHub CLI）
   gh release list --json tagName -q '.[] | .tagName' | xargs -I {} gh release delete {} --yes
   # 推送一次新代码
   git commit --allow-empty -m "trigger build"
   git push origin main
   ```

2. **看 CI 日志**
   `仓库 → Actions → 选中 run → 查看每步详细日志`

3. **如果还是不行，把日志贴给我**

---

## 🎯 一句话总结

> **Release 出现的关键**：CI 必须成功跑完 → 触发"创建 Release"步骤 → GitHub 自动生成 release → 仓库主页右侧"发布"区域显示 "Latest" 标记
>
> **最快验证方式**：
> ```bash
> git commit --allow-empty -m "trigger build"
> git push origin main
> ```
> 等 3-5 分钟，看 `仓库 → Releases` 页面
