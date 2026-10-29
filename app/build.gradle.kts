
import java.util.Properties

val apiKeyFile = rootProject.file("api-key.properties")
val apiKeyProperties = Properties().apply {
    if (apiKeyFile.exists()) {
        apiKeyFile.inputStream().use { load(it) }
    }
}

val yandexRaspApiKey = apiKeyProperties
    .getProperty(
        "YANDEX_RASP_API_KEY",
        System.getenv("YANDEX_RASP_API_KEY") ?: ""
    )
    .trim()

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ru.elektrichki.russia"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "ru.elektrichki.russia"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField(
            "String",
            "YANDEX_RASP_API_KEY",
            "\"${yandexRaspApiKey.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // Убираем runtime-проверки параметров Kotlin: меньше байткода, поведение для пользователя не меняется.
        freeCompilerArgs += listOf(
            "-Xno-param-assertions",
            "-Xno-call-assertions",
            "-Xno-receiver-assertions"
        )
    }

    // Автоматическая подпись для личного использования.
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true      // R8: удаление неиспользуемого кода и оптимизация
            isShrinkResources = true    // удаление неиспользуемых ресурсов
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // Служебные файлы библиотек, не нужные в рантайме.
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.version",
                "/META-INF/com/android/build/gradle/app-metadata.properties",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
                "/kotlin/**",
                "/META-INF/*.kotlin_module"
            )
        }
    }

    // Блоб с метаданными зависимостей (нужен только для Google Play).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    // AppCompat и Room убраны: весь UI строится в коде на стандартных виджетах,
    // кэш — один простой SQLite-запрос без ORM.
    implementation("androidx.activity:activity:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
