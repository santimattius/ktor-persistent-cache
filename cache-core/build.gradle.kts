import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKMPLibrary)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.kotlinSerialization)
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

// --- Maven publication (gradle-maven-publish-plugin) ---
// https://github.com/vanniktech/gradle-maven-publish-plugin
// Supports com.android.kotlin.multiplatform.library out of the box.
mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("io.github.santimattius", "ktor-persistent-cache-core", "1.2.0")
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
