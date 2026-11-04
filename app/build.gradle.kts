plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp") version "2.3.12"
}

android {
    namespace = "com.vitalstream.android"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.vitalstream.android"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appAuthRedirectScheme"] = "com.vitalstream.android"
        buildConfigField("String", "API_URL", "\"${providers.gradleProperty("vitalstream.apiUrl").getOrElse("https://api.example.invalid")}\"")
        buildConfigField("String", "OIDC_ISSUER", "\"${providers.gradleProperty("vitalstream.issuer").getOrElse("https://identity.example.invalid/realms/vitalstream")}\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_URL", "\"http://10.0.2.2:8000\"")
            buildConfigField("String", "OIDC_ISSUER", "\"http://10.0.2.2:8080/realms/vitalstream\"")
        }
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("net.openid:appauth:0.11.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.json:json:20240303")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }
