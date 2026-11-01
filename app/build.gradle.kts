import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Optional local config (never committed): app/keys.properties, see app/keys.properties.example.
// Without the file the app talks to http://10.0.2.2:8080 (the emulator's route to localhost).
val keys = Properties().apply {
    val f = rootProject.file("keys.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun key(name: String, default: String): String = keys.getProperty(name) ?: default

android {
    namespace = "bo.piyo.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "bo.piyo.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables { useSupportLibrary = true }
        // Plain HTTP is allowed so the app can talk to a local backend (10.0.2.2:8080).
        // Set it to "false" once API_BASE_URL is an https:// URL.
        manifestPlaceholders["usesCleartextTraffic"] = "true"

        // One build for every environment. Where the data and the map come from is configuration
        // (app/keys.properties), not a flavor: the line dataset is always the bundled seed plus
        // releases downloaded from API_BASE_URL; the map is MapLibre with the style served by the same
        // API (/v1/map/style.json) unless MAP_STYLE_URL overrides it; search is Photon.
        buildConfigField("String", "API_BASE_URL", "\"${key("PIYO_API_BASE_URL", "http://10.0.2.2:8080")}\"")
        buildConfigField("String", "MAP_STYLE_URL", "\"${key("MAP_STYLE_URL", "")}\"")
        buildConfigField("String", "GEOCODER_URL", "\"${key("GEOCODER_URL", "https://photon.komoot.io/api/")}\"")
        resValue("string", "app_name", "Piyo")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug keystore so a release build can be installed for performance checks
            // (`gradlew :app:installRelease`). Replace with a real keystore before publishing.
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
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
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.google.fonts)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.maplibre.android.sdk)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // JVM unit tests (src/test): dataset parser, planner against the bundled seed.
    testImplementation(libs.junit)
}
