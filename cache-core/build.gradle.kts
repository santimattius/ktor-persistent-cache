import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import kotlinx.validation.ExperimentalBCVApi

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKMPLibrary)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.binaryCompatibilityValidator)
}

kotlin {
    androidLibrary {
        namespace = "cache.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        // Runs commonTest (incl. the cache-test-suite conformance suites) on the JVM against the
        // Android source set and android.jar stubs. This is not a device/emulator run.
        withHostTest { }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "KtorPersistentCacheCore"
            isStatic = true
        }
    }

    jvm()

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.startup.runtime)
        }
        commonMain.dependencies {
            // cache-core intentionally has ZERO Okio / kotlinx-io dependencies.
            // Ktor client core is the framework being extended, not a backend I/O dependency.
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.protobuf)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(projects.cacheTestSuite)
            // Test-only cross-check against Okio's SHA-256, until Phase 2 relocates this
            // test to cache-okio/commonTest (where Okio is a real backend dependency).
            implementation(libs.okio)
        }
    }
}

// klib (iOS) ABI validation, enabled on this module only. `klibApiDump`/`klibApiCheck` run
// reproducibly on a macOS host (matching CI's macOS-latest runner) for iosArm64/
// iosSimulatorArm64/iosX64, and `klibApiCheck` fails when the committed `cache-core.klib.api`
// baseline diverges from the compiled API. Kept scoped to this one module; not rolled out to
// shared/okio/kotlinx-io.
@OptIn(ExperimentalBCVApi::class)
apiValidation {
    klib {
        enabled = true
    }
}

// --- Maven publication (gradle-maven-publish-plugin) ---
// https://github.com/vanniktech/gradle-maven-publish-plugin
// Supports com.android.kotlin.multiplatform.library out of the box.
mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(
        "io.github.santimattius",
        "ktor-persistent-cache-core",
        providers.gradleProperty("artifactVersion").get(),
    )
    pom {
        name.set("Ktor Persistent Cache - Core")
        description.set("Backend-independent algorithm and SPI for Ktor Persistent Cache. Zero Okio/kotlinx-io dependencies.")
        inceptionYear.set("2026")
        url.set("https://github.com/santimattius/ktor-persistent-cache/")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("santimattius")
                name.set("Santiago Mattiauda")
                url.set("https://github.com/santimattius/")
            }
        }
        scm {
            url.set("https://github.com/santimattius/ktor-persistent-cache/")
            connection.set("scm:git:git://github.com/santimattius/ktor-persistent-cache.git")
            developerConnection.set("scm:git:ssh://git@github.com/santimattius/ktor-persistent-cache.git")
        }
    }
}
