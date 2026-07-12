# ============================================================
#  ProGuard / R8 规则 - LearnSpark
#  修复 "packageinfo is null" 错误的关键规则
#
#  症状：APK 装上后启动崩溃，日志包含
#    "PackageInfo is null" / "java.lang.NullPointerException"
#
#  根因：R8 默认会移除 Capacitor / WebView 内部依赖的类，
#  导致 Bridge 启动时找不到必要的反射目标。
# ============================================================

# ----- 保留行号（崩溃栈可读）-----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ===== Capacitor 核心 =====
-keep class com.getcapacitor.** { *; }
-keep interface com.getcapacitor.** { *; }
-keep enum com.getcapacitor.** { *; }
-dontwarn com.getcapacitor.**

# ===== Capacitor Plugin 注解 =====
-keepclassmembers class * {
    @com.getcapacitor.PluginMethod *;
    @com.getcapacitor.ActivityCallback *;
    @com.getcapacitor.PermissionCallback *;
}

# ===== Capacitor Cordova 兼容层 =====
-keep class org.apache.cordova.** { *; }
-dontwarn org.apache.cordova.**

# ===== WebView JavaScript Interface =====
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ===== WebView 系统类 =====
-keep class android.webkit.** { *; }
-dontwarn android.webkit.**

# ===== AndroidX Core =====
-keep class androidx.core.** { *; }
-dontwarn androidx.core.**

# ===== 我们的 App 类 =====
-keep class com.learnspark.app.** { *; }
-keep class com.learnspark.app.MainActivity { *; }

# ===== 反射保留（Capacitor Bridge 大量使用反射）=====
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ===== 协程 / Kotlin 元数据（Capacitor 6 用 Kotlin 实现）=====
-keep class kotlin.Metadata { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlinx.**

# ===== OkHttp / Retrofit（如果 Capacitor 插件用到）=====
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# ===== Gson / Moshi（JSON 序列化）=====
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepattributes Signature
