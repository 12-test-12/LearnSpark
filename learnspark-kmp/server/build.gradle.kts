import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.springBoot)
    alias(libs.plugins.springDependencyManagement)
}

group = "com.learnspark"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Spring Boot 核心（web starter 已经传递依赖 starter + autoconfigure + logging）
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)

    // 数据库
    runtimeOnly(libs.mysql.connector.j)
    runtimeOnly(libs.h2.database)  // R8：本地开发用 H2 文件数据库，不需要 MySQL
    implementation(libs.flyway.core)
    implementation(libs.flyway.mysql)

    // 文件解析（按模块引入，规避 tika-parsers-standard-package 触发 Gradle 8.10 + Kotlin 2.0 兼容问题）
    implementation(libs.tika.core)
    implementation(libs.tika.parsers.microsoft.module)
    implementation(libs.tika.parser.pdf.module)
    implementation(libs.tika.parser.html.module)
    implementation(libs.tika.parser.text.module)
    implementation(libs.pdfbox)

    // JWT
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    // Kotlin
    implementation(libs.kotlin.coroutines.core)

    // 测试
    testImplementation(libs.spring.boot.starter.web)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += listOf("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// 阶段 2.1：同一镜像不同 profile
// - 默认 (api)：完整 Web 服务
// - --spring.profiles.active=worker：禁用 Web，只跑定时轮询
springBoot {
    mainClass.set("com.learnspark.server.LearnSparkApplicationKt")
}
