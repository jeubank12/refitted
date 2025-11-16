plugins {
    id("com.android.library")
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.dagger.hilt.android)
    alias(libs.plugins.jetbrains.kotlin.kapt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.litus_animae.refitted.util"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }
}

dependencies {
    // Core dependencies
    implementation(libs.androidx.annotation)
    implementation(libs.javax.inject)

    // Kotlin
    implementation(libs.kotlinx.coroutines.core)

    // Hilt
    implementation(libs.dagger.hilt.core)
    implementation(libs.dagger.hilt.android)
    kapt(libs.dagger.hilt.android.compiler)
    ksp(libs.androidx.hilt.compiler)
}
