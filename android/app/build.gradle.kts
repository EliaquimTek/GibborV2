import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

val crossmintApiKey = localProperties.getProperty("CROSSMINT_API_KEY") ?: ""
val backendUrl = localProperties.getProperty("BACKEND_URL") ?: "http://10.0.2.2:3001"

android {
    namespace = "mx.edu.utez.gibbor"
    compileSdk = 36

    buildFeatures {
        compose = true
        buildConfig = true
    }

    defaultConfig {
        applicationId = "mx.edu.utez.gibbor"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "CROSSMINT_API_KEY", "\"$crossmintApiKey\"")
        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.animation:animation")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation("com.crossmint:crossmint-sdk:0.0.2")
    implementation("com.crossmint:crossmint-compose:0.0.2")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // CameraX — video recording
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-video:$cameraxVersion")
    implementation("androidx.lifecycle:lifecycle-process:2.9.4")
}
