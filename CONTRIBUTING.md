# Contributing

Thank you for your interest in contributing to Ktor Persistent Cache. This document explains how
to propose changes and what a pull request needs to be accepted.

By participating, you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md). To report a
security vulnerability, do not open an issue; follow [SECURITY.md](SECURITY.md). For usage
questions, see [SUPPORT.md](SUPPORT.md).

## Issue-first workflow

1. **Open an issue first.** Use the bug report, feature request, or task template. Search existing
   issues before opening a new one.
2. **Wait for approval.** A maintainer applies the `status:approved` label once the issue is
   accepted for implementation. Please do not start work, and do not open a pull request, for an
   issue that has not been approved; it may be closed without review.
3. **Open a pull request** that links the approved issue (for example `Closes #123`).

Trivial fixes (typos, broken links) may skip the issue step.

## Development setup

Requirements: JDK 21 (the CI uses Zulu 21) and, to build the iOS targets, macOS with Xcode.

Build and verify everything the CI verifies:

```bash
./gradlew check apiCheck
```

`check` runs the tests of all modules; `apiCheck` compares each module's public API against the
committed baseline (see below). Both must pass before a pull request is merged.

### Project layout

| Module | Purpose |
|--------|---------|
| `cache-core` | The `install(PersistentCache)` DSL, caching algorithm, and the `CacheFileSystem` SPI |
| `cache-okio` | Default, stable Okio-based backend |
| `cache-kotlinx-io` | Experimental kotlinx-io backend |
| `cache-test-suite` | Shared conformance tests that every backend must pass |
| `shared` | Compatibility facade over `cache-core` and `cache-okio` |
| `sample` | Unpublished deterministic backend checks consumed by the platform apps |
| `androidApp`, `iosApp` | Native sample shells (not published); see [Running the backend sample apps](README.md#running-the-backend-sample-apps) |

## Updating the public API baselines

The public API is guarded by
[binary-compatibility-validator](https://github.com/Kotlin/binary-compatibility-validator). If
you change the public API intentionally, regenerate the baselines and commit the result:

```bash
./gradlew apiDump
```

Review the resulting `*.api` / `*.klib.api` diffs carefully: every line is a change visible to
library consumers. Explain the API change in the pull request description. An unexpected
`apiCheck` failure usually means an accidental public declaration; fix the code instead of
dumping the baseline.

## Commit messages

This project uses [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<optional scope>): <short imperative summary>
```

Common types: `feat`, `fix`, `docs`, `test`, `refactor`, `perf`, `build`, `ci`, `chore`.
Mark breaking changes with `!` after the type or a `BREAKING CHANGE:` footer.

## Pull request expectations

- Link the approved issue and keep the change focused on it; unrelated changes belong in separate
  pull requests.
- Add or update tests for behavior changes. Backend behavior belongs in the shared conformance
  suite (`cache-test-suite`) so every backend is covered.
- Update the documentation (`README.md`, `docs/`) when behavior, configuration, or setup changes.
- Run `./gradlew check apiCheck` locally and make sure the CI checks pass.
- Fill in the pull request template. Prefer small pull requests that are easy to review.
- Be responsive to review feedback; maintainers may request changes before merging.

## License

By contributing, you agree that your contributions are licensed under the
[Apache License, Version 2.0](LICENSE), the same license as the project.
