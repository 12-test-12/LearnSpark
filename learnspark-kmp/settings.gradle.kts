rootProject.name = "LearnSpark"

include(":composeApp")
include(":server")

pluginManagement {
    repositories {
        // Google 官方源放最前（被 502 时回退到下面的镜像）
        google()
        gradlePluginPortal()
        mavenCentral()

        // 国内镜像兜底
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
    }
}

dependencyResolutionManagement {
    repositories {
        // Google 官方源放最前
        google()
        mavenCentral()

        // 国内镜像兜底
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
    }
}
