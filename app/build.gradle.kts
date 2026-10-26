
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
    id("com.google.devtools.ksp")
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
    }

    // Автоматическая подпись для личного использования.
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

