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
// A Gradle property wins over the file (CI, or a one-off build: ./gradlew assembleRelease -PMAP_STYLE_URL=).
fun key(name: String, default: String): String = (findProperty(name) as String?) ?: keys.getProperty(name) ?: default

// Misplaced Firebase values do not fail at runtime, push just never registers. Catch them here.
// Messages never echo the values.
val firebaseShapes = mapOf(
    "FIREBASE_APP_ID" to Regex("""1:\d+:android:[0-9a-f]+"""),
    "FIREBASE_API_KEY" to Regex("""AIza[0-9A-Za-z_-]{35}"""),
    "FIREBASE_PROJECT_ID" to Regex("""[a-z][a-z0-9-]{4,29}"""),
    "FIREBASE_SENDER_ID" to Regex("""\d{6,}"""),
)
for ((name, shape) in firebaseShapes) {
    val v = key(name, "")
    require(v.isEmpty() || shape.matches(v)) {
        "$name in keys.properties does not look like a $name (expected ${shape.pattern}). " +
            "Firebase console → Project settings → General; App ID is under Your apps."
    }
}

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
        // Plain HTTP only for a local backend (http://10.0.2.2:8080, http://<PC-IP>). An https:// API turns
        // cleartext off: an http:// URL to Cloud Run gets a 302 to https, which OkHttp follows for GET but
        // replays a PUT/POST as GET, so writes (push token, reports) failed while reads seemed fine.
        manifestPlaceholders["usesCleartextTraffic"] =
            (!key("PIYO_API_BASE_URL", "http://10.0.2.2:8080").startsWith("https://")).toString()

        // One build for every environment. Where the data and the map come from is configuration
        // (app/keys.properties), not a flavor: the line dataset is always the bundled seed plus
        // releases downloaded from API_BASE_URL; the map is MapLibre with the style served by the same
        // API (/v1/map/style.json) unless MAP_STYLE_URL overrides it; search is Photon.
        buildConfigField("String", "API_BASE_URL", "\"${key("PIYO_API_BASE_URL", "http://10.0.2.2:8080")}\"")
        buildConfigField("String", "MAP_STYLE_URL", "\"${key("MAP_STYLE_URL", "")}\"")
        buildConfigField("String", "MAP_STYLE_DARK_URL", "\"${key("MAP_STYLE_DARK_URL", "")}\"")
        buildConfigField("String", "GEOCODER_URL", "\"${key("GEOCODER_URL", "https://photon.komoot.io/api/")}\"")
        // Push (FCM) is configured here instead of with google-services.json, so the app builds without
        // Firebase files; blank values disable push. Values: Firebase console → Project settings → Your
        // apps (App ID, Web API key, Project ID, Project number). See PRIVACY.md §Push.
        buildConfigField("String", "FIREBASE_APP_ID", "\"${key("FIREBASE_APP_ID", "")}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${key("FIREBASE_API_KEY", "")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${key("FIREBASE_PROJECT_ID", "")}\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"${key("FIREBASE_SENDER_ID", "")}\"")
        resValue("string", "app_name", "piyo")
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
    // The bundled city map (assets/map/santa-cruz.pmtiles) is already compressed vector data; storing it
    // uncompressed lets BundledMap read its size with openFd() and copy it in one pass.
    androidResources { noCompress += "pmtiles" }
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
    // Push notifications only (no Analytics/Crashlytics); inert until FIREBASE_* keys are set.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // JVM unit tests (src/test): dataset parser, planner against the bundled seed.
    testImplementation(libs.junit)
}
