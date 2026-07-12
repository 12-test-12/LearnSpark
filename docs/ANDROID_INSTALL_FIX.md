# 🔧 修复"packageinfo is null"安装错误

> 完整的根因分析 + 修复方案 + 本地调试指南

---

## 📌 错误现象

安装 APK 时出现以下任何一种错误：

1. **"应用未安装"** + 弹窗显示 `packageinfo is null`
2. **"解析包出现问题"** + 日志包含 `PackageInfo`
3. **APK 装上后立即闪退** + logcat 报 `NullPointerException at ... PackageInfo`
4. **"应用未安装"** 静默失败（部分设备如华为/小米）

---

## 📌 根因分析（4 个叠加 bug）

### Bug ①：AndroidManifest.xml 缺少 `package` 属性

```xml
<!-- ❌ Capacitor 6 默认生成（AGP 8.x 风格，不带 package）-->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <activity android:name="com.learnspark.app.MainActivity" ... />
    </application>
</manifest>

<!-- ✅ 修复后（显式声明 package）-->
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.learnspark.app">
    <application>
        <activity android:name="com.learnspark.app.MainActivity" ... />
    </application>
</manifest>
```

**为什么**：
- AGP 8.x 改用 `namespace` 替代 manifest 的 `package`
- 但 Capacitor 6 的 Bridge 启动时仍会读 manifest 的 `package` 属性
- 找不到时 `PackageManager.getPackageInfo()` 返回 null

### Bug ②：MainActivity 缺少 `android:exported="true"`

```xml
<!-- ❌ Android 12+ 设备会拒绝安装 -->
<activity android:name=".MainActivity" ...>
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>

<!-- ✅ 修复后 -->
<activity
    android:name="com.learnspark.app.MainActivity"
    android:exported="true"           ← 关键
    ...>
    <intent-filter>...</intent-filter>
</activity>
```

**为什么**：
- Android 12 (API 31) 起，**所有带 intent-filter 的组件必须显式声明 exported**
- 缺失时 PackageInstaller 解析阶段就报错

### Bug ③：缺少 `<queries>` 块（Android 11+ Package Visibility）

```xml
<!-- ❌ Android 11+ 默认看不到其他应用信息 -->
<manifest>
    <application>...</application>
</manifest>

<!-- ✅ 修复后 -->
<manifest>
    <queries>
        <intent>
            <action android:name="android.intent.action.VIEW" />
            <data android:scheme="https" />
        </intent>
    </queries>
    <application>...</application>
</manifest>
```

**为什么**：
- Android 11 (API 30) 引入 Package Visibility
- WebView 在某些场景下需要查询其他应用信息
- 没有 `<queries>` 时 PackageManager 返回空

### Bug ④：PWA Service Worker 与 Capacitor 冲突

```typescript
// ❌ 之前 vite.config.ts 没禁用 SW
VitePWA({
  registerType: 'autoUpdate',
  workbox: {
    navigateFallback: '/index.html'  // ← WebView 中会循环重定向
  }
})

// ✅ 修复后（Capacitor 模式禁用）
VitePWA({
  disable: process.env.CAPACITOR_BUILD === 'true',  // ← 关键
  // ...
})
```

**为什么**：
- WebView 加载 `https://localhost/...` 时 Service Worker 会拦截
- SW 的 `navigateFallback: '/index.html'` 触发循环重定向
- SW 注册失败时，Capacitor Bridge 启动超时返回 null

---

## 📌 本项目已实施的修复

| 修复点 | 文件 | 状态 |
|---|---|---|
| vite 默认 `base: './'` | [vite.config.ts](file:///d:/LearnSpark/frontend/vite.config.ts) | ✅ |
| Capacitor 模式禁用 PWA SW | [vite.config.ts](file:///d:/LearnSpark/frontend/vite.config.ts) | ✅ |
| AndroidManifest 显式 package | [.github/android-patches/AndroidManifest.xml](file:///d:/LearnSpark/.github/android-patches/AndroidManifest.xml) | ✅ |
| MainActivity exported=true | 同上 | ✅ |
| `<queries>` 块 | 同上 | ✅ |
| `network_security_config.xml` | [.github/android-patches/res/xml/network_security_config.xml](file:///d:/LearnSpark/.github/android-patches/res/xml/network_security_config.xml) | ✅ |
| ProGuard 保留 Capacitor 类 | [.github/android-patches/proguard-rules.pro](file:///d:/LearnSpark/.github/android-patches/proguard-rules.pro) | ✅ |
| CI 验证 APK 签名/manifest | [.github/workflows/android.yml](file:///d:/LearnSpark/.github/workflows/android.yml) | ✅ |
| CI 注入补丁 | 同上 | ✅ |
| CI 启用 minify + proguardFiles | 同上 | ✅ |
| 强制 applicationId = namespace | 同上 | ✅ |

---

## 📌 本地调试步骤

### 方法 1：用 debug APK（最稳）

```bash
cd frontend
npm install

# 1. 构建 Web 资源
CAPACITOR_BUILD=true npx vite build

# 2. 生成 Android 工程
npx cap add android

# 3. 应用补丁（可选，CI 自动做）
cp ../../.github/android-patches/AndroidManifest.xml android/app/src/main/
mkdir -p android/app/src/main/res/xml
cp ../../.github/android-patches/res/xml/*.xml android/app/src/main/res/xml/
cp ../../.github/android-patches/proguard-rules.pro android/app/

# 4. 构建 debug APK（不用签名）
cd android
./gradlew assembleDebug

# 5. 产物路径
ls app/build/outputs/apk/debug/
# → app-debug.apk
```

Debug APK 默认用 debug 签名，**和 release 签名不同**，所以可以独立测试，不影响 release 版本。

### 方法 2：用 ADB 看真实错误

```bash
# 1. 手机连上电脑，打开 USB 调试
adb devices  # 确认设备已连接

# 2. 启动 logcat 抓包
adb logcat -c                    # 清空旧日志
adb logcat | grep -E "AndroidRuntime|Capacitor|chromium" > /tmp/logcat.log

# 3. 在手机上点击 APK 触发安装
#    观察 /tmp/logcat.log

# 4. 关键错误信息
#    "PackageInfo is null"          → Bug ①（manifest package）
#    "ClassNotFoundException"       → Bug ④（Service Worker / R8 误删类）
#    "INSTALL_PARSE_FAILED"         → manifest 语法错
#    "INSTALL_FAILED_VERSION_DOWNGRADE" → 已装过更高版本
```

### 方法 3：手动验证 APK 完整性

```bash
# 1. 查看 APK 内的 manifest（需要 aapt）
$ANDROID_HOME/build-tools/34.0.0/aapt2 dump badging app-release.apk

# 应该看到：
# package: name='com.learnspark.app' versionCode='1' ...
# launchable-activity: name='com.learnspark.app.MainActivity'
# uses-permission: name='android.permission.INTERNET'

# 2. 查看 manifest 关键字段
$ANDROID_HOME/build-tools/34.0.0/aapt2 dump xmltree app-release.apk \
  --file AndroidManifest.xml | grep -E "package|exported"

# 应该看到：
#   A: package="com.learnspark.app" (Raw: "com.learnspark.app")
#   E: android:exported (Raw: "true")

# 3. 验证签名
$ANDROID_HOME/build-tools/34.0.0/apksigner verify --verbose app-release.apk

# 应该看到：
#   Verified using v1 scheme (JAR signing): true
#   Verified using v2 scheme (APK Signature Scheme v2): true
#   Verified using v3 scheme (APK Signature Scheme v3): true
```

---

## 📌 用户侧排查清单

如果装 APK 时报 "packageinfo is null"：

- [ ] 1. 卸载旧版本（设置 → 应用 → LearnSpark → 卸载）
- [ ] 2. 清除下载缓存，重新下载 APK（避免下载不完整）
- [ ] 3. 检查 APK 大小（应该 5-15 MB，< 1MB 肯定有问题）
- [ ] 4. 用 `apksigner verify` 验证签名
- [ ] 5. 用 `aapt2 dump badging` 看 package name
- [ ] 6. 启用"未知来源安装"
- [ ] 7. 检查 Android 版本（必须 ≥ 5.0 / API 21）
- [ ] 8. 换台手机 / 模拟器试一下

---

## 📌 还有问题？

1. **看 CI 日志**：`仓库 → Actions → 选中 run → 找 "验证 APK" 步骤`
2. **看 logcat**：参考上面的"方法 2"
3. **把 APK 给我**：我可以本地用 `aapt2` 帮你解析

**最直接的验证命令**：

```bash
# 假设你的 GitHub 用户名是 yourname
curl -L -o learnspark.apk \
  "https://github.com/yourname/LearnSpark/releases/latest/download/learnspark-v1.0.0.apk"

# 检查完整性
ls -lh learnspark.apk
file learnspark.apk

# 解析 manifest
$ANDROID_HOME/build-tools/34.0.0/aapt2 dump xmltree learnspark.apk --file AndroidManifest.xml

# 验证签名
$ANDROID_HOME/build-tools/34.0.0/apksigner verify --verbose learnspark.apk
```

如果 aapt 输出的 `package` 是 `com.learnspark.app` 且 `exported=true`，那肯定能装。
