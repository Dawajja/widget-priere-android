plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// numéro de construction calculé par GitHub Actions (BUILD) : chaque mise à jour a un numéro plus grand.
// Il commence à 101 : plus grand que les essais d'avant la version 1.0, pour que la mise à jour s'installe.
val build = (System.getenv("BUILD") ?: "100").toInt()

android {
    namespace = "io.github.dawajja.priere"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.dawajja.priere"
        minSdk = 26
        targetSdk = 35
        versionCode = build
        versionName = "1.0.$build"
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.jks")
            // clé de signature de l'app (toujours la même : Android accepte ainsi les mises à jour)
            storePassword = "widget-priere"
            keyAlias = "priere"
            keyPassword = "widget-priere"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
}
