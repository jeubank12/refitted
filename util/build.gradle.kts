import com.android.build.api.dsl.LibraryExtension

plugins {
    id("com.android.library")
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.dagger.hilt.android)
    alias(libs.plugins.jetbrains.kotlin.kapt)
    alias(libs.plugins.ksp)
}

extensions.configure<LibraryExtension> {
    namespace = "com.litus_animae.refitted.util"
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

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    // Core dependencies
    implementation(libs.androidx.annotation)
    implementation(libs.javax.inject)

    // Kotlin
    implementation(libs.kotlinx.coroutines.core)

    // Hilt
    implementation(libs.bundles.hilt)
    kapt(libs.dagger.hilt.android.compiler)
    ksp(libs.androidx.hilt.compiler)

    // Testing
    testImplementation(platform(libs.junit))
    testImplementation(libs.bundles.junit)
    testRuntimeOnly(libs.bundles.junit.runtime)
}
