# Maven Publishing — Shared Module (KMP)

**Scope:** Módulo `shared` (Kotlin Multiplatform, Android + iOS)  
**Plugin:** [gradle-maven-publish-plugin](https://github.com/vanniktech/gradle-maven-publish-plugin)

---

## 1. Overview

This document describes how to publish the **shared** module to Maven repositories (Maven Central, private repositories, or local Maven) using the **com.vanniktech.maven.publish** plugin. The module uses the **com.android.kotlin.multiplatform.library** plugin, which is supported out of the box by the publishing plugin.

**Note:** Only the `shared` module is configured for publication. The `androidApp` (Android application) module is not published.

---

## 2. Configuration Reference

### 2.1 Current setup

| Item | Value |
|------|--------|
| **Plugin ID** | `com.vanniktech.maven.publish` |
| **Plugin version** | Defined in `gradle/libs.versions.toml` (`mavenPublish`) |
| **Published module** | `shared` |
| **Default coordinates** | `com.santimattius.kmp:shared:1.0.0-SNAPSHOT` |
| **POM** | Name, description, Apache 2.0 license, developer, SCM — configured in `shared/build.gradle.kts` |
| **Maven Central / signing** | Disabled by default so `publishToMavenLocal` works without credentials |

### 2.2 Customizing coordinates

To change **groupId**, **artifactId**, or **version**:

1. Open `shared/build.gradle.kts`.
2. Edit the `mavenPublishing { coordinates(...) }` block.

Alternatively, use the properties supported by the plugin in `gradle.properties` (see [plugin documentation — Maven Central](https://vanniktech.github.io/gradle-maven-publish-plugin/central/)).

---

## 3. Publishing to Maven Local

Use this for local testing or for consuming the library from another project on the same machine. No credentials or signing are required.

### 3.1 Procedure

1. From the project root, run:

   ```bash
   ./gradlew :shared:publishToMavenLocal
   ```

2. Artifacts are published to the local Maven repository (typically `~/.m2/repository`).

### 3.2 Consuming from another project

Add the local repository and the dependency:

```kotlin
repositories {
    mavenLocal()
}

dependencies {
    implementation("com.santimattius.kmp:shared:1.0.0-SNAPSHOT")
}
```

---

## 4. Publishing to Maven Central

### 4.1 Prerequisites

| Requirement | Reference |
|-------------|-----------|
| Central Portal account and registered namespace | [Central Portal](https://central.sonatype.com/) |
| GPG key pair for signing artifacts | [GPG — Sonatype](https://central.sonatype.com/publish/requirements/gpg/) |
| Central Portal **user token** (not account password) | [Generate Portal Token](https://central.sonatype.com/publish/generate-portal-token/) |

### 4.2 Enable Maven Central in the project

1. Open `shared/build.gradle.kts`.
2. Uncomment the following lines in the `mavenPublishing` block:

   ```kotlin
   publishToMavenCentral()
   signAllPublications()
   ```

### 4.3 Configure credentials

Store credentials **outside** version control. Use either:

- **Option A:** User-level `~/.gradle/gradle.properties`
- **Option B:** Environment variables (recommended for CI)

#### Option A — `~/.gradle/gradle.properties`

```properties
# Maven Central (user token, not account password)
mavenCentralUsername=<your-username>
mavenCentralPassword=<your-token>

# GPG signing — file-based
signing.keyId=<key-id>
signing.password=<key-password>
signing.secretKeyRingFile=/path/to/.gnupg/secring.gpg
```

#### Option B — Environment variables (e.g. CI)

| Variable | Description |
|----------|-------------|
| `ORG_GRADLE_PROJECT_mavenCentralUsername` | Central username |
| `ORG_GRADLE_PROJECT_mavenCentralPassword` | Central token |
| `ORG_GRADLE_PROJECT_signingInMemoryKey` | ASCII-armored private key (see below) |
| `ORG_GRADLE_PROJECT_signingInMemoryKeyId` | GPG key ID (optional) |
| `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword` | Key password (if set) |

**Exporting the secret key for in-memory signing** (run only in a secure environment):

```bash
gpg --export-secret-keys --armor <key-id>
```

Use the full output as the value for `signingInMemoryKey`.

### 4.4 Gradle tasks

| Task | Description |
|------|-------------|
| `./gradlew :shared:publishAllPublicationsToMavenCentral` | Uploads all publications to Maven Central. If automatic release is disabled, you must complete the release manually in the Central Portal. |
| `./gradlew :shared:publishToMavenCentral` | Same target; when `automaticRelease = true` is configured, also triggers automatic release. |

### 4.5 Snapshots vs releases

| Version type | Example | Behavior |
|--------------|---------|----------|
| **Snapshot** | `1.0.0-SNAPSHOT` | Published to [Central Snapshots](https://central.sonatype.com/repository/maven-snapshots/). Signing optional. |
| **Release** | `1.0.0` | Requires signing. After upload, publication to Central may take several minutes. |

---

## 5. Publishing to Other Maven Repositories

To publish to a repository other than Maven Central (e.g. GitHub Packages, Artifactory, or a custom Nexus):

### 5.1 Disable Maven Central and signing (if used)

In `shared/build.gradle.kts`, ensure the following are commented out or removed:

- `publishToMavenCentral()`
- `signAllPublications()` (unless the target repository requires signed artifacts)

### 5.2 Add the target repository

Add a `publishing { repositories { ... } }` block. Example for **GitHub Packages**:

```kotlin
publishing {
    repositories {
        maven {
            name = "githubPackages"
            url = uri("https://maven.pkg.github.com/<org>/<repo>")
            credentials(PasswordCredentials::class)
        }
    }
}
```

Provide credentials via:

- **Gradle properties:** `githubPackagesUsername`, `githubPackagesPassword`
- **Environment variables:** `ORG_GRADLE_PROJECT_githubPackagesUsername`, `ORG_GRADLE_PROJECT_githubPackagesPassword`

Publish with:

```bash
./gradlew :shared:publishAllPublicationsToGithubPackagesRepository
```

(The repository name in the task matches the `name` set in the `maven { }` block.)

---

## 6. References

| Resource | URL |
|----------|-----|
| gradle-maven-publish-plugin (GitHub) | https://github.com/vanniktech/gradle-maven-publish-plugin |
| Maven Central setup | https://vanniktech.github.io/gradle-maven-publish-plugin/central/ |
| Other Maven repositories | https://vanniktech.github.io/gradle-maven-publish-plugin/other/ |
| Configuring what to publish | https://vanniktech.github.io/gradle-maven-publish-plugin/what/ |

The plugin automatically detects `com.android.kotlin.multiplatform.library` and configures publications for Android, iOS, and Kotlin Multiplatform metadata; no extra “what to publish” configuration is required for this setup.
