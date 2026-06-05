import com.android.build.api.dsl.LibraryExtension

plugins {
    id("com.android.library")
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.ksp)
}

extensions.configure<LibraryExtension> {
    namespace = "com.litus_animae.refitted.identity"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
        }
    }
}

dependencies {
    // Modules
    api(project(":util"))

    // Core dependencies
    implementation(libs.androidx.annotation)
    implementation(libs.javax.inject)

    // Kotlin
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.config)

    // Dependency Injection
    implementation(libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.android.compiler)
}
