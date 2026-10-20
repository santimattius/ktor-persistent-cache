# Maven Publishing

**Scope:** the four publishable modules: `cache-core`, `cache-okio`, `cache-kotlinx-io`, and `shared`  
**Plugin:** [gradle-maven-publish-plugin](https://github.com/vanniktech/gradle-maven-publish-plugin)

---

## 1. Overview

This document describes how the library's Kotlin Multiplatform modules are published to Maven
Central (or to a local or private Maven repository) with the **com.vanniktech.maven.publish**
plugin. The modules use the **com.android.kotlin.multiplatform.library** plugin, which the
publishing plugin supports out of the box.

The `androidApp` (Android sample application), `iosApp`, and `cache-test-suite` modules are not
published.

---

## 2. Configuration Reference

### 2.1 Current setup

| Item | Value |
|------|--------|
| **Plugin ID** | `com.vanniktech.maven.publish` |
| **Plugin version** | Defined in `gradle/libs.versions.toml` (`mavenPublish`) |
| **Version** | `artifactVersion` in the root `gradle.properties` (shared by all modules) |
| **Group ID** | `io.github.santimattius` |
| **POM** | Name, description, Apache 2.0 license, developer, SCM; configured in each module's `build.gradle.kts` |
| **Maven Central / signing** | `publishToMavenCentral()` and `signAllPublications()` are enabled in every publishable module; credentials and signing keys are supplied externally |

### 2.2 Published artifacts

| Gradle module | Artifact ID |
|---------------|-------------|
| `:cache-core` | `ktor-persistent-cache-core` |
| `:cache-okio` | `ktor-persistent-cache-okio` |
| `:cache-kotlinx-io` | `ktor-persistent-cache-kotlinx-io` |
| `:shared` | `ktor-persistent-cache` (compatibility facade over `cache-core` and `cache-okio`) |

All artifacts are published as `io.github.santimattius:<artifact-id>:<artifactVersion>`.

### 2.3 Customizing coordinates

To change the version of every published artifact, update `artifactVersion` in the root
`gradle.properties` file. All publishable modules read their version from that property.

To change a module's **groupId** or **artifactId**, edit its
`mavenPublishing { coordinates(...) }` block.

---

## 3. Publishing to Maven Local

Use this for local testing or for consuming the library from another project on the same
machine. Nothing is uploaded. Signing is enabled in the build, so if publishing locally fails
because no signing key is configured, supply a test key (see section 4.2) or temporarily comment
out `signAllPublications()` in the module you are testing, without committing that change.

### 3.1 Procedure

1. From the project root, run:

   ```bash
   ./gradlew publishToMavenLocal
   ```

   To publish a single module, prefix the task with the module path, for example
   `./gradlew :cache-core:publishToMavenLocal`.

2. Artifacts are published to the local Maven repository (typically `~/.m2/repository`).

### 3.2 Consuming from another project

Add the local repository and the dependency:

```kotlin
repositories {
    mavenLocal()
}

dependencies {
    implementation("io.github.santimattius:ktor-persistent-cache:<artifactVersion>")
}
```

Replace `<artifactVersion>` with the value in the root `gradle.properties`.

---

## 4. Publishing to Maven Central

Releases are published by the `Publish` GitHub Actions workflow
(`.github/workflows/publish.yml`) when a GitHub release is released or prereleased. It runs
`./gradlew publishToMavenCentral --no-configuration-cache` with the secrets listed below.

### 4.1 Prerequisites

| Requirement | Reference |
|-------------|-----------|
| Central Portal account and registered namespace | [Central Portal](https://central.sonatype.com/) |
| GPG key pair for signing artifacts | [GPG - Sonatype](https://central.sonatype.com/publish/requirements/gpg/) |
| Central Portal **user token** (not account password) | [Generate Portal Token](https://central.sonatype.com/publish/generate-portal-token/) |

### 4.2 Configure credentials

Store credentials **outside** version control. Use either:

- **Option A:** User-level `~/.gradle/gradle.properties`
- **Option B:** Environment variables (recommended for CI)

#### Option A: `~/.gradle/gradle.properties`

```properties
# Maven Central (user token, not account password)
mavenCentralUsername=<your-username>
mavenCentralPassword=<your-token>

# GPG signing (file-based)
signing.keyId=<key-id>
signing.password=<key-password>
signing.secretKeyRingFile=/path/to/.gnupg/secring.gpg
```

#### Option B: Environment variables (for example CI)

| Variable | Description | GitHub Actions secret used by the workflow |
|----------|-------------|--------------------------------------------|
| `ORG_GRADLE_PROJECT_mavenCentralUsername` | Central username | `MAVEN_CENTRAL_USERNAME` |
| `ORG_GRADLE_PROJECT_mavenCentralPassword` | Central token | `MAVEN_CENTRAL_PASSWORD` |
| `ORG_GRADLE_PROJECT_signingInMemoryKey` | ASCII-armored private key (see below) | `GPG_KEY_CONTENTS` |
| `ORG_GRADLE_PROJECT_signingInMemoryKeyId` | GPG key ID (optional) | `SIGNING_KEY_ID` |
| `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword` | Key password (if set) | `SIGNING_PASSWORD` |

**Exporting the secret key for in-memory signing** (run only in a secure environment):

```bash
gpg --export-secret-keys --armor <key-id>
```

Use the full output as the value for `signingInMemoryKey`.

### 4.3 Gradle tasks

| Task | Description |
|------|-------------|
| `./gradlew publishToMavenCentral` | Uploads all publishable modules to Maven Central. If automatic release is not enabled, complete the release manually in the Central Portal. |
| `./gradlew :cache-core:publishToMavenCentral` | Same, for a single module. |

### 4.4 Snapshots vs releases

| Version type | Example | Behavior |
|--------------|---------|----------|
| **Snapshot** | `1.3.0-SNAPSHOT` | Published to [Central Snapshots](https://central.sonatype.com/repository/maven-snapshots/). |
| **Release** | `1.3.0` | Requires signing. After upload, publication to Central may take several minutes. |

---

## 5. Publishing to Other Maven Repositories

To publish to a repository other than Maven Central (for example GitHub Packages, Artifactory, or
a custom Nexus):

### 5.1 Disable Maven Central and signing (if not needed)

In each module's `build.gradle.kts`, comment out or remove the following unless the target
repository requires signed artifacts:

- `publishToMavenCentral()`
- `signAllPublications()` (only if signing is not required)

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
./gradlew publishAllPublicationsToGithubPackagesRepository
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

The plugin automatically detects `com.android.kotlin.multiplatform.library` and configures
publications for Android, iOS, JVM, and Kotlin Multiplatform metadata; no extra "what to publish"
configuration is required for this setup.
