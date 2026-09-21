import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// PLACEHOLDER — Phase 2 (#41) module. Scaffolded now only so `:cache-core` and
// `:cache-test-suite` can be wired into `settings.gradle.kts` in this PR; the Okio
// CacheFileSystem<okio.Path> adapter, the `:shared` facade wiring, and the
// conformance-suite subclasses land in a follow-up PR.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKMPLibrary)
}

kotlin {
    androidLibrary {
        namespace = "cache.okio"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "KtorPersistentCacheOkio"
            isStatic = true
        }
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            api(projects.cacheCore)
        }
    }
}
