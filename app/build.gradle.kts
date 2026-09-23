// Android 构建配置、应用版本和 Compose 依赖。
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.zongce.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zongce.app"
        minSdk = 26
        targetSdk = 35
        versionCode = providers.gradleProperty("releaseVersionCode").orElse("2").get().toInt()
        versionName = providers.gradleProperty("versionName").orElse("1.3.5").get()
    }

    // 本地没有签名参数时仍可构建 release；CI 通过 GitHub Secrets 注入正式签名。
    signingConfigs {
        create("release") {
            val storeFilePath = providers.gradleProperty("signingStoreFile").orNull
            if (!storeFilePath.isNullOrBlank()) {
                // 相对路径按【仓库根】解析，不是按 app/ 模块目录。
                // 原因：CI 把解出来的 keystore 放在仓库根（.github/workflows/release.yml 写入
                // `signingStoreFile=release.keystore` 并解码到仓库根），本机也把 keystore 放仓库根。
                // 模块级的 file() 会去 app/release.keystore 找，必然 not found。
                // rootProject.file() 对相对路径按根目录解析，对绝对路径原样使用，两种写法都对。
                storeFile = rootProject.file(storeFilePath)
                storePassword = providers.gradleProperty("signingStorePassword").orNull
                keyAlias = providers.gradleProperty("signingKeyAlias").orNull
                keyPassword = providers.gradleProperty("signingKeyPassword").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) signingConfig = releaseSigning
            buildConfigField(
                "String",
                "UPDATE_MANIFEST_URL",
                "\"${providers.gradleProperty("updateManifestUrl").orNull.orEmpty()}\""
            )
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            buildConfigField(
                "String",
                "UPDATE_MANIFEST_URL",
                "\"${providers.gradleProperty("updateManifestUrl").orNull.orEmpty()}\""
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// 把 Room 导出的 schema 落到 app/schemas/，纳入版本控制以便将来写迁移。
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.1")

    // 数据库（Room = SQLite 官方封装）
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // 桌面小组件：Glance 是 Compose 风格写 AppWidget 的官方适配层（见 docs/adr/0001）。
    implementation("androidx.glance:glance-appwidget:1.1.0")

    // 规则测试：覆盖学年归属和导出文件名等不依赖 Android UI 的行为。
    testImplementation("junit:junit:4.13.2")
}
