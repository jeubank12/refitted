import com.android.build.api.dsl.LibraryExtension

plugins {
    id("com.android.library")
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.dagger.hilt.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrains.kotlin.parcelize)
    alias(libs.plugins.ksp)
}

extensions.configure<LibraryExtension> {
    namespace = "com.litus_animae.refitted.ui"
    compileSdk = 37

    defaultConfig {
        minSdk = 26

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += emptyMap()
            }
        }
    }

    buildFeatures {
        dataBinding = true
        compose = true
        buildConfig = true
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

    packaging {
        resources {
            excludes += listOf("/META-INF/LICENSE.md", "/META-INF/LICENSE-notice.md")
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

    // Dependency Injection
    implementation(libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.android.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    // Core Android
    implementation(libs.androidx.lifecycle.runtime.compose.android)

    // Paging (exposed as api - ViewModels expose Flow<PagingData<T>>)
    api(libs.androidx.paging.compose)
    implementation(libs.androidx.paging.common)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.navigation.common)
    implementation(libs.androidx.navigation.runtime)

    // Firebase (types only - implementations from :identity)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth) // For AuthProvider types
    implementation(libs.firebase.config)  // For ConfigProvider.RemoteConfig type

    // Arrow
    api(platform(libs.arrow.stack))
    api(libs.arrow.core)

    // Coroutines
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    api(libs.bundles.compose)
    api(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.runtime.annotation)
    implementation(libs.androidx.compose.ui.util)

    // Lifecycle
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    api(libs.androidx.lifecycle.viewmodel.savedstate)

    // Credentials (exposed as api - used in public APIs)
    implementation(libs.androidx.credentials)
    runtimeOnly(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Accompanist
    implementation(libs.accompanist.adaptive)

    // Testing
    testImplementation(platform(libs.junit))
    testRuntimeOnly(libs.bundles.junit.runtime)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
