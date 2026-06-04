import com.android.build.api.dsl.LibraryExtension

plugins {
    id("com.android.library")
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.dagger.hilt.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrains.kotlin.kapt)
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
    implementation(project(":util"))
    implementation(project(":identity"))

    // Core Android
    implementation(libs.androidx.activity)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.guava)
    implementation(libs.javax.inject)
    implementation(libs.androidx.lifecycle.runtime.compose.android)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.recyclerview)

    // Paging (exposed as api - ViewModels expose Flow<PagingData<T>>)
    api(libs.androidx.paging.compose)
    api(libs.androidx.paging.runtime)

    // Hilt
    implementation (libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.navigation.common)
    implementation(libs.androidx.navigation.runtime)

    // Firebase (types only - implementations from :identity)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth) // For AuthProvider types
    implementation(libs.firebase.config)  // For ConfigProvider.RemoteConfig type

    // Arrow
    implementation(platform(libs.arrow.stack))
    implementation(libs.arrow.core)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.bundles.compose.tooling)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)

    // Lifecycle
    testImplementation(libs.androidx.lifecycle.livedata.core)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)

    // Accompanist
    implementation(libs.accompanist.adaptive)

    // Credentials
    implementation(libs.androidx.credentials)
    runtimeOnly(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Testing
    testImplementation(platform(libs.junit))
    testImplementation(libs.bundles.junit)
    testRuntimeOnly(libs.bundles.junit.runtime)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.core.runtime)
}
