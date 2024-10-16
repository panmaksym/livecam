import java.io.FileInputStream
import java.io.FileNotFoundException
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.cameralive"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.cameralive"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Читання IP-адреси з файлу local.properties
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(FileInputStream(localPropertiesFile))

            val ipAddress = localProperties.getProperty("ip.address") ?: "127.0.0.1"
            val port = localProperties.getProperty("port") ?: "8080"

            buildConfigField("String", "IP_ADDRESS", "\"$ipAddress\"")
            buildConfigField("String", "PORT", "\"$port\"")
        } else {
            buildConfigField("String", "IP_ADDRESS", "\"127.0.0.1\"")
            buildConfigField("String", "PORT", "\"8080\"")

        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Увімкнення BuildConfig
    buildFeatures {
        buildConfig = true
        compose = true
    }




    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))

    // implementation ("com.mesibo.api:webrtc:1.0.5")
    // https://mvnrepository.com/artifact/io.github.webrtc-sdk/android
    implementation("io.github.webrtc-sdk:android:125.6422.05")

    implementation("org.java-websocket:Java-WebSocket:1.5.2")
    implementation("androidx.core:core-ktx:1.6.0")
    implementation("androidx.appcompat:appcompat:1.3.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.3")

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
