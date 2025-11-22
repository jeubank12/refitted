plugins {
    id("com.android.library")
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.kapt)
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
    // Dependency Injection (Dagger)
    api(libs.dagger)
    kapt(libs.dagger.compiler)
    runtimeOnly(libs.dagger.lint.aar)

    // Coroutines runtime
    runtimeOnly(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(platform(libs.junit))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.bundles.junit.runtime)
    testImplementation(libs.kotlinx.coroutines.test)
}
