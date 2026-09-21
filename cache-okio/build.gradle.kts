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
            // OkioCacheFileSystem exposes okio.Path in its public API, so this must be `api`,
            // not `implementation`, for consumers of :cache-okio to resolve it.
            api(libs.okio)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.auth)
            implementation(projects.cacheTestSuite)
            implementation(libs.okio.fakefilesystem)
        }
    }
}

// --- Maven publication (gradle-maven-publish-plugin) ---
// https://github.com/vanniktech/gradle-maven-publish-plugin
// Supports com.android.kotlin.multiplatform.library out of the box.
mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("io.github.santimattius", "ktor-persistent-cache-okio", "1.2.0")
    pom {
        name.set("Ktor Persistent Cache - Okio")
        description.set("Okio-backed CacheFileSystem adapter for Ktor Persistent Cache.")
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
