import com.android.build.api.dsl.LibraryExtension

plugins {
  id("com.android.library")
  alias(libs.plugins.ksp)
}

extensions.configure<LibraryExtension> {
  namespace = "com.litus_animae.refitted.dynamo"
  compileSdk = 37

  defaultConfig {
    minSdk = 26
  }

  buildFeatures {
    resValues = true
  }

  buildTypes {
    release {
      val Refitted_IdentityPoolId: String = rootProject.extra["Refitted_IdentityPoolId"] as String
      val Refitted_OpenIdSource: String = rootProject.extra["Refitted_OpenIdSource"] as String
      resValue("string", "cognito_identity_pool_id", Refitted_IdentityPoolId)
      resValue("string", "firebase_id_source", Refitted_OpenIdSource)
    }
    debug {
      val Refitted_IdentityPoolId: String = rootProject.extra["Refitted_IdentityPoolId"] as String
      val Refitted_OpenIdSource: String = rootProject.extra["Refitted_OpenIdSource"] as String
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

  testOptions {
    unitTests.all {
      it.useJUnitPlatform()
    }
  }
}

dependencies {
  // Module dependencies
  api(project(":data"))
  api(project(":util"))
  api(project(":identity"))

  // Core dependencies
  api(libs.javax.inject)

  // Kotlin
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.play.services)

  // Hilt
  api(libs.dagger.hilt.android)
  ksp(libs.dagger.hilt.android.compiler)

  // AWS SDK (exposed as api - return types use these)
  implementation(libs.aws.android.sdk.ddb)
  implementation(libs.aws.android.sdk.ddb.mapper)
  implementation(libs.aws.android.sdk.core)

  // Firebase (for types returned by AuthProvider)
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.auth)

  testImplementation(platform(libs.junit))
  testImplementation(libs.junit.jupiter.api)
  testRuntimeOnly(libs.bundles.junit.runtime)
}
