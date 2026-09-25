plugins {
    // AGP 8.5.2 → 8.6.0：Glance 1.2.0 的 AAR metadata 硬性要求
    // "requires Android Gradle plugin 8.6.0 or higher"，8.5.2 会在 :app:checkDebugAarMetadata
    // 直接失败。8.6.0 是满足该下限的最小跃迁：它要求 Gradle 8.7（本仓 wrapper 正是 8.7）
    // 与 JDK 17（本机/CI 均为 17），compileSdk 35 仍在支持范围内。
    id("com.android.application") version "8.6.0" apply false
    // Kotlin 2.0.20 → 2.0.21：Glance 1.2.0 拉 kotlin-stdlib 2.0.21，插件不对齐会被 Gradle 抬高。
    // KSP 必须跟着 Kotlin 版本走（ksp 版本号 = <kotlin版本>-<ksp版本>）。
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.25" apply false
}
