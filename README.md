# KMP Basic Template

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin_Multiplatform-2.3.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/docs/multiplatform.html)
[![Android](https://img.shields.io/badge/Android-AGP%209.0-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![iOS](https://img.shields.io/badge/iOS-Supported-8E8E93?logo=apple&logoColor=white)](https://developer.apple.com)
[![Gradle](https://img.shields.io/badge/Gradle-9.1-02303A?logo=gradle&logoColor=white)](https://gradle.org)
[![Android SDK](https://img.shields.io/badge/Android_SDK-36%20%7C%20min%2024-3DDC84?logo=android&logoColor=white)](https://developer.android.com)

Kotlin Multiplatform (KMP) project targeting **Android** and **iOS**, with a shared module and platform-specific app entry points.

---

## 1. Overview

This template provides a minimal structure for a Kotlin Multiplatform project:

- **Shared module** — Common and platform-specific Kotlin code (Android, iOS).
- **Android app** — Android application module (`androidApp`).
- **iOS app** — iOS application project (`iosApp`).

| Module    | Path              | Description |
|-----------|-------------------|-------------|
| **shared** | [`shared/`](shared/) | Shared KMP code. Main sources in `shared/src/commonMain/kotlin`. Platform code in `androidMain`, `iosMain`. |
| **androidApp** | [`androidApp/`](androidApp/) | Android application. Entry point and Android-specific UI. |
| **iosApp** | [`iosApp/`](iosApp/) | iOS application (Xcode project). Entry point and SwiftUI. |

---

## 2. Prerequisites

- **JDK 17**
- **Android Studio** (Otter 3 Feature Drop or later) or **IntelliJ IDEA** with Android and KMP support
- **Xcode** (for iOS builds)
- **Gradle 9.1+** (wrapper included)

Optional: [kdoctor](https://github.com/Kotlin/kdoctor) to verify the KMP development environment.

---

## 3. Getting Started

### 3.1 Build and run

**Android**

```bash
./gradlew :androidApp:assembleDebug
# Or run the androidApp configuration from the IDE
```

**iOS**

Open `iosApp/iosApp.xcodeproj` in Xcode and run the app on a simulator or device.

**Shared module (all targets)**

```bash
./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileAndroidMain
```

### 3.2 Run tests

```bash
./gradlew test
```

---

## 4. Configuration After Cloning

When creating a new project from this template, update the following.

### 4.1 Project name

**File:** `settings.gradle.kts`

Set the root project name:

```kotlin
rootProject.name = "YourNewProjectName"
```

### 4.2 Android application ID

**File:** `androidApp/build.gradle.kts`

Set the `applicationId` in the `android { defaultConfig { ... } }` block:

```kotlin
android {
    defaultConfig {
        applicationId = "com.yourcompany.yournewprojectname"
        // ...
    }
}
```

### 4.3 iOS bundle ID and display name

**File:** `iosApp/Configuration/Config.xcconfig`

Update at least:

| Variable  | Description     | Example |
|-----------|-----------------|---------|
| `APP_ID`  | Bundle ID       | `com.yourcompany.YourNewProjectName` |
| `APP_NAME`| Display name    | `YourNewProjectName` |

---

## 5. Publishing the Shared Module

The **shared** module is set up for Maven publication via the [gradle-maven-publish-plugin](https://github.com/vanniktech/gradle-maven-publish-plugin).

| Action                    | Command / Reference |
|---------------------------|----------------------|
| Publish to local Maven    | `./gradlew :shared:publishToMavenLocal` |
| Publish to Maven Central  | See [docs/PUBLISHING.md](docs/PUBLISHING.md) for credentials and steps. |

**Default coordinates:** `com.santimattius.kmp:shared:1.0.0-SNAPSHOT`  
To change groupId, artifactId, or version, edit the `mavenPublishing { coordinates(...) }` block in `shared/build.gradle.kts`.

---

## 6. Environment Verification

It is recommended to run [kdoctor](https://github.com/Kotlin/kdoctor) to check that your environment is correctly configured for Kotlin Multiplatform. kdoctor reports and can help fix common setup issues.

---

## 7. References

| Resource | URL |
|----------|-----|
| Kotlin Multiplatform — Get started | [JetBrains Help](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html) |
| kdoctor | [GitHub](https://github.com/Kotlin/kdoctor) |
| Maven publishing (this project) | [docs/PUBLISHING.md](docs/PUBLISHING.md) |
