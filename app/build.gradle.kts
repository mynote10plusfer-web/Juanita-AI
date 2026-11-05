plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingStoreFile = providers.environmentVariable("JUANITA_SIGNING_STORE_FILE").orNull
val signingStorePassword = providers.environmentVariable("JUANITA_SIGNING_STORE_PASSWORD").orNull
val signingKeyAlias = providers.environmentVariable("JUANITA_SIGNING_KEY_ALIAS").orNull
val signingKeyPassword = providers.environmentVariable("JUANITA_SIGNING_KEY_PASSWORD").orNull

android {
    namespace = "ai.juanita.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ai.juanita.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.5.1"
    }

    signingConfigs {
        create("release") {
            if (!signingStoreFile.isNullOrBlank()) {
                storeFile = file(signingStoreFile)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (!signingStoreFile.isNullOrBlank()) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
