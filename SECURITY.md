# Security Policy

## Supported versions

Security fixes are provided for the **latest released version** only. Please upgrade to the
latest release before reporting an issue, and confirm that it still reproduces.

## Reporting a vulnerability

**Do not open a public issue, pull request, or discussion for a security vulnerability.**

Report it privately through GitHub private vulnerability reporting:

1. Go to the [Security tab](https://github.com/santimattius/ktor-persistent-cache/security) of
   this repository.
2. Choose **Report a vulnerability**.
3. Describe the problem, the affected version(s), and steps to reproduce it. Include a minimal
   proof of concept if possible.

Direct link: <https://github.com/santimattius/ktor-persistent-cache/security/advisories/new>

You can expect an acknowledgement of your report, and updates as it is triaged. Please allow
reasonable time for a fix to be prepared and released before any public disclosure. Reporters are
credited in the published advisory unless they prefer to remain anonymous.

## Scope

In scope: vulnerabilities in the library code published from this repository (for example cache
poisoning, path traversal in the cache directory handling, or disclosure of cached private
responses). Vulnerabilities in Ktor, Okio, kotlinx-io, or other dependencies should be reported to
those projects.
