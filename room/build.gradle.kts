plugins {
    id("com.android.library")
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.dagger.hilt.android)
    alias(libs.plugins.jetbrains.kotlin.kapt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.litus_animae.refitted.room"
    compileSdk = 37

    defaultConfig {
        minSdk = 26

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.expandProjection", "true")
        }
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
    // Module dependencies
    api(project(":data"))

    // Core dependencies
    implementation(libs.androidx.annotation)
    implementation(libs.javax.inject)

    // Kotlin
    implementation(libs.kotlinx.coroutines.core)

    // Room (exposed as api - :app repository implementations use these)
    api(libs.bundles.room)
    api(libs.androidx.sqlite)
    ksp(libs.androidx.room.compiler)

    // Paging (exposed as api - :app repository implementations use these)
    api(libs.androidx.paging.runtime)

    // Hilt
    implementation(libs.bundles.hilt)
    kapt(libs.dagger.hilt.android.compiler)
    ksp(libs.androidx.hilt.compiler)

    // Testing
    testImplementation(platform(libs.junit))
    testImplementation(libs.bundles.junit)
    testRuntimeOnly(libs.bundles.junit.runtime)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.core.runtime)
}
