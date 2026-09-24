import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKMPLibrary)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.binaryCompatibilityValidator)
}

kotlin {
    androidLibrary {
        namespace = "cache.kotlinxio"
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
            baseName = "KtorPersistentCacheKotlinxIo"
            isStatic = true
        }
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            api(projects.cacheCore)
            // KotlinxIoCacheFileSystem exposes kotlinx.io.files.Path in its public API, so this
            // must be `api`, not `implementation`, for consumers of :cache-kotlinx-io to resolve
            // it — same rationale as :cache-okio's `api(libs.okio)`.
            api(libs.kotlinx.io.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.core)
            implementation(projects.cacheTestSuite)
        }
    }
}

// --- Maven publication (gradle-maven-publish-plugin) ---
// https://github.com/vanniktech/gradle-maven-publish-plugin
// Supports com.android.kotlin.multiplatform.library out of the box.
mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("io.github.santimattius", "ktor-persistent-cache-kotlinx-io", "1.2.0")
    pom {
        name.set("Ktor Persistent Cache - kotlinx-io")
        description.set("kotlinx-io-backed CacheFileSystem adapter for Ktor Persistent Cache (experimental).")
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
