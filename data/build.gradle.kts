plugins {
    id("com.android.library")
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.litus_animae.refitted.data"
    compileSdk = 36

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
    // Kotlin & Coroutines
    implementation(libs.kotlinx.coroutines.core)
    runtimeOnly(libs.kotlinx.coroutines.android)

    // Paging (used directly in repository interfaces)
    implementation(libs.androidx.paging.common)

    // Dependency Injection (lint support)
    runtimeOnly(libs.dagger.lint.aar)

    // Testing
    testImplementation(platform(libs.junit))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.bundles.junit.runtime)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
