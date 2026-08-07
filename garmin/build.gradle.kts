import com.android.build.api.dsl.LibraryExtension

plugins {
  id("com.android.library")
  alias(libs.plugins.ksp)
}

extensions.configure<LibraryExtension> {
  namespace = "com.litus_animae.refitted.garmin"
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
  // Module dependencies
  api(project(":data"))

  // Core dependencies
  api(libs.javax.inject)

  // Kotlin (StateFlow return types in WatchService implementations are part of the public API)
  api(libs.kotlinx.coroutines.core)

  // Hilt
  api(libs.dagger.hilt.android)
  ksp(libs.dagger.hilt.android.compiler)

  // Connect IQ Mobile SDK (ConnectIQ instance exposed on GarminConnection)
  api(libs.garmin.connectiq)

  // Lifecycle (GarminConnection implements DefaultLifecycleObserver)
  api(libs.androidx.lifecycle.common)

  testImplementation(platform(libs.junit))
  testRuntimeOnly(libs.bundles.junit.runtime)
  testImplementation(libs.mockk)
  testImplementation(libs.kotlinx.coroutines.test)
}
