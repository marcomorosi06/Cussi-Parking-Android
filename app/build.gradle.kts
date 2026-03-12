plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp") version "1.9.22-1.0.17"
    id("kotlin-kapt")
}

android {
    namespace = "com.cuscus.cussiparking"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.cuscus.cussiparking"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
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
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation("androidx.compose.material3:material3:1.4.0-alpha02")
    implementation("androidx.compose.material:material-icons-extended:1.6.3")

    // Navigazione tra le schermate in Compose
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // DataStore (Il nuovo standard per salvare l'IP del server e il Token, sostituisce SharedPreferences)
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Retrofit e Gson (Per far parlare Kotlin con i nostri file PHP e leggere i JSON)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Mappe FOSS (osmdroid)
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")

    kapt("androidx.room:room-compiler:$roomVersion")
    // Posizione e GPS
    implementation("com.google.android.gms:play-services-location:21.2.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}