import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 自持签名：debug / release 共用同一密钥，装到手机上可直接覆盖升级。
// 凭证只放本机 keystore.properties（不入库）；公开仓库照 keystore.properties.example 复原。
val releaseKeystore = rootProject.file("zentimer.jks")
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasSigning = releaseKeystore.exists() &&
    keystoreProperties.getProperty("storePassword") != null &&
    keystoreProperties.getProperty("keyAlias") != null &&
    keystoreProperties.getProperty("keyPassword") != null

android {
    namespace = "com.yaohui.zentimer"
    compileSdk = 37
    // API 37 的平台包以小版本号发布（platforms;android-37.0）
    compileSdkMinor = 0

    defaultConfig {
        applicationId = "com.yaohui.zentimer"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "2.3"
    }

    signingConfigs {
        if (hasSigning) {
            create("still") {
                storeFile = releaseKeystore
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (hasSigning) signingConfig = signingConfigs.getByName("still")
        }
        release {
            isMinifyEnabled = false
            if (hasSigning) signingConfig = signingConfigs.getByName("still")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    // 显式对齐本机已验证的 lifecycle 版本，避免传递依赖落到未缓存的 2.6.2 / 2.8.7
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
