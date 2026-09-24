import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Unpublished, test-support-only module.
// Holds abstract conformance suites, generic over CacheFileSystem<P>, that every
// backend (cache-core's reference double, cache-okio, cache-kotlinx-io) subclasses
// with a one-line factory, so behavior is verified identically across backends
// without duplicating test bodies. Never applies the maven-publish plugin.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKMPLibrary)
}

kotlin {
    androidLibrary {
        namespace = "cache.testsuite"
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
            baseName = "KtorPersistentCacheTestSuite"
            isStatic = true
        }
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            // Abstract suites are consumed as *test* dependencies by backend modules,
            // but they live in this module's main source set so they can be subclassed
            // (KMP has no working java-test-fixtures equivalent).
            api(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.core)
            api(projects.cacheCore)
        }
        // `kotlin-test`'s automatic per-target framework substitution (e.g. to
        // kotlin-test-junit on JVM) only applies to *test* source sets. These abstract
        // suites deliberately live in `commonMain` so they can be subclassed, so the JVM
        // and Android (JVM bytecode) actuals for `@Test`/`@BeforeTest` must be added
        // explicitly here.
        jvmMain.dependencies {
            implementation(libs.kotlin.testJunit)
        }
        androidMain.dependencies {
            implementation(libs.kotlin.testJunit)
        }
    }
}
