// 本机 maven.google.com 走代理会 502，改用官方 dl.google.com Maven 路径 + 阿里云镜像兜底。
pluginManagement {
    repositories {
        maven { url = uri("https://dl.google.com/dl/android/maven2") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://dl.google.com/dl/android/maven2") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        mavenCentral()
    }
}

rootProject.name = "ZenTimer"
include(":app")
