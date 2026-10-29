import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.kissa.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kissa.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "3.0.0"
        vectorDrawables { useSupportLibrary = true }
        // OPTIONAL Kissa backend (Cloudflare Worker). Default (empty) = direct-key mode: the user pastes their own Gemini key.
        // Set -PkissaBackendUrl=https://... to build a backend-mode APK where the server holds the key instead.
        val backendUrl = (project.findProperty("kissaBackendUrl") as String?) ?: ""
        buildConfigField("String", "KISSA_BACKEND_URL", "\"" + backendUrl + "\"")
        // Phase 2 CDN catalog (catalog.json + game packs). Empty = bundled launch games only (V1, fully offline).
        val catalogUrl = (project.findProperty("kissaCatalogUrl") as String?) ?: ""
        buildConfigField("String", "CATALOG_URL", "\"" + catalogUrl + "\"")
    }

    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.10" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    lint { checkReleaseBuilds = false; abortOnError = false }
    testOptions { unitTests.isReturnDefaultValues = true; unitTests.all {
        it.systemProperty("shots", System.getProperty("shots") ?: "false")
        it.systemProperty("live", System.getProperty("live") ?: "false")
        System.getProperty("previewOut")?.let { p -> it.systemProperty("previewOut", p) }
        System.getProperty("testProxyPort")?.let { p -> it.systemProperty("testProxyPort", p) }
        System.getProperty("voiceOut")?.let { p -> it.systemProperty("voiceOut", p) }
    } }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.02")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation(files("../testshim/graphics-shim.jar"))
}
