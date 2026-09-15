plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Keep local/CI builds usable before the project-specific Firebase file is provisioned.
// Once present, the plugin generates the Firebase Android resources automatically.
if (file("google-services.json").isFile) {
    apply(plugin = "com.google.gms.google-services")
}

dependencies {
    implementation(libs.androidx.activity)
    implementation(libs.play.services.auth)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
}

android {
    namespace = "com.netk.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.netk.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
