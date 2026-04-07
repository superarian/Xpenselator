plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // KSP Plugin for Room
    id("com.google.devtools.ksp") version "1.9.24-1.0.20"
    id("com.google.gms.google-services")
}

android {
    namespace = "com.bytemantis.xpenselator"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.byteskull.xpenselator"
        minSdk = 24
        targetSdk = 35
        versionCode = 4
        versionName = "2.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // --- ROOM DATABASE TOOLS ---
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")

    // KSP Compiler (Replaces KAPT)
    ksp("androidx.room:room-compiler:$room_version")

    // --- FIREBASE ---
    // Import the Firebase BoM (Bill of Materials)
    implementation(platform("com.google.firebase:firebase-bom:32.8.0"))
    // Add Firestore Database
    implementation("com.google.firebase:firebase-firestore")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
}