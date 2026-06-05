import com.android.build.api.dsl.LibraryExtension

plugins {
    id("com.android.library")
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.ksp)
}

extensions.configure<LibraryExtension> {
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

    // Dependency Injection (Dagger + Hilt qualifier)
    api(libs.dagger.core)
    api(libs.dagger.hilt.android)  // For @ApplicationContext qualifier
    api(libs.javax.inject)
    ksp(libs.dagger.hilt.android.compiler)

    // Kotlin
    implementation(libs.kotlinx.coroutines.core)

    // Room (exposed as api - :app repository implementations use these)
    api(libs.androidx.room.common)
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.paging)
    api(libs.androidx.sqlite)
    ksp(libs.androidx.room.compiler)

    // Paging (exposed as api - :app repository implementations use these)
    api(libs.androidx.paging.common)

    // Testing
    testImplementation(platform(libs.junit))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.bundles.junit.runtime)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
