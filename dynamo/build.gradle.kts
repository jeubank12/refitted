plugins {
    id("com.android.library")
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.kapt)
}

android {
    namespace = "com.litus_animae.refitted.dynamo"
    compileSdk = 36

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
    api(project(":util"))
    api(project(":identity"))

    // Dependency Injection (Dagger + Hilt qualifier)
    api(libs.dagger)
    api(libs.dagger.hilt.android)  // For @ApplicationContext qualifier
    api(libs.javax.inject)
    kapt(libs.dagger.compiler)

    // Kotlin
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)

    // AWS SDK (exposed as api - return types use these)
    api(libs.aws.android.sdk.ddb)
    api(libs.aws.android.sdk.ddb.mapper)
    implementation(libs.aws.android.sdk.core)

    // Firebase (for types returned by AuthProvider)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
}
