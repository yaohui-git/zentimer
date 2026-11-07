plugins {
    // AGP 9.x 内置 Kotlin 支持，不要再应用 org.jetbrains.kotlin.android
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
