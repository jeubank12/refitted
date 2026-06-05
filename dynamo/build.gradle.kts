import com.android.build.api.dsl.LibraryExtension

plugins {
    id("com.android.library")
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.dagger.hilt.android)
    alias(libs.plugins.jetbrains.kotlin.kapt)
    alias(libs.plugins.ksp)
}

extensions.configure<LibraryExtension> {
    namespace = "com.litus_animae.refitted.dynamo"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    buildTypes {
      release {
        val Refitted_IdentityPoolId: String by rootProject.extra
        val Refitted_OpenIdSource: String by rootProject.extra
        resValue("string", "cognito_identity_pool_id", Refitted_IdentityPoolId)
        resValue("string", "firebase_id_source", Refitted_OpenIdSource)
      }
      debug {
        val Refitted_IdentityPoolId: String by rootProject.extra
        val Refitted_OpenIdSource: String by rootProject.extra
        resValue("string", "cognito_identity_pool_id", Refitted_IdentityPoolId)
        resValue("string", "firebase_id_source", Refitted_OpenIdSource)
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
}

dependencies {
    // Module dependencies
    api(project(":data"))
    implementation(project(":identity"))

    // Core dependencies
    implementation(libs.androidx.annotation)
    implementation(libs.javax.inject)

    // Kotlin
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)

    // AWS SDK
    implementation(libs.aws.android.sdk.core)
    implementation(libs.aws.android.sdk.ddb)
    implementation(libs.aws.android.sdk.ddb.mapper)

    // Firebase (for types returned by AuthProvider)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)

    // Hilt
    implementation(libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.android.compiler)
}
