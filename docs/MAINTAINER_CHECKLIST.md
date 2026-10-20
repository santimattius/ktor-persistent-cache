# Maintainer Checklist: Repository Settings

These settings live in GitHub, not in the repository, so a pull request cannot change them. A
repository admin must apply them once. Each item gives the UI path and, where available, a `gh api`
command. Replace `OWNER/REPO` with `santimattius/ktor-persistent-cache`.

Prerequisites: `gh auth login` with a token that has the `repo` scope. Commands that edit settings require admin permission on the repository.

Intentionally out of scope: `CODEOWNERS` (deferred until ownership expands beyond the current
maintainer), `FUNDING.yml`, `CITATION.cff`, and GitHub Discussions.

---

## 1. Protect `main`

**UI:** Settings > Rules > Rulesets > New ruleset > New branch ruleset.

- Ruleset name: `protect-main`; Enforcement status: **Active**
- Target branches: **Include default branch**
- Enable: **Restrict deletions**, **Block force pushes**, **Require a pull request before merging**
  (at least 1 approval, dismiss stale approvals on new commits), **Require status checks to pass**
  (add `Verify Code`, the job name from `.github/workflows/check.yml`; enable **Require branches
  to be up to date**)
- Optional: **Require linear history**, **Require conversation resolution before merging**

**CLI** (the status check context must have run at least once so GitHub lists it):

```bash
gh api -X POST repos/OWNER/REPO/rulesets --input - <<'JSON'
{
  "name": "protect-main",
  "target": "branch",
  "enforcement": "active",
  "conditions": { "ref_name": { "include": ["~DEFAULT_BRANCH"], "exclude": [] } },
  "rules": [
    { "type": "deletion" },
    { "type": "non_fast_forward" },
    { "type": "pull_request", "parameters": {
        "required_approving_review_count": 1,
        "dismiss_stale_reviews_on_push": true,
        "require_code_owner_review": false,
        "require_last_push_approval": false,
        "required_review_thread_resolution": true } },
    { "type": "required_status_checks", "parameters": {
        "strict_required_status_checks_policy": true,
        "required_status_checks": [ { "context": "Verify Code" } ] } }
  ]
}
JSON
```

With a single maintainer, a required approval blocks self-merges; set
`required_approving_review_count` to `0` (still requiring a pull request) or add the
ruleset bypass list entry for the maintainer if that is intended.

Verify: `gh api repos/OWNER/REPO/rulesets --jq '.[] | {name, enforcement}'`

## 2. Private vulnerability reporting

`SECURITY.md` relies on this being enabled.

**UI:** Settings > Code security > Private vulnerability reporting > **Enable**.

```bash
gh api -X PUT repos/OWNER/REPO/private-vulnerability-reporting
# verify
gh api repos/OWNER/REPO/private-vulnerability-reporting --jq .enabled
```

## 3. Dependabot alerts and security updates

Version updates are already configured in `.github/dependabot.yml`; alerts and security updates
are separate settings.

**UI:** Settings > Code security > **Dependabot alerts** > Enable, and **Dependabot security
updates** > Enable. Optionally enable the **Dependency graph**.

```bash
gh api -X PUT repos/OWNER/REPO/vulnerability-alerts
gh api -X PUT repos/OWNER/REPO/automated-security-fixes
# verify
gh api repos/OWNER/REPO --jq '.security_and_analysis'
```

## 4. Secret scanning and push protection

**UI:** Settings > Code security > **Secret scanning** > Enable, then **Push protection** > Enable.

```bash
gh api -X PATCH repos/OWNER/REPO --input - <<'JSON'
{
  "security_and_analysis": {
    "secret_scanning": { "status": "enabled" },
    "secret_scanning_push_protection": { "status": "enabled" }
  }
}
JSON
# verify
gh api repos/OWNER/REPO --jq '.security_and_analysis'
```

## 5. GitHub Actions restrictions

The workflows already declare `permissions: contents: read` and pin actions to commit SHAs; these
repository settings enforce the same posture.

**UI:** Settings > Actions > General.

- **Actions permissions:** *Allow OWNER, and select non-OWNER, actions and reusable workflows*;
  allow `actions/*` (GitHub-authored actions used here: `actions/checkout`, `actions/setup-java`).
  Enable **Require actions to be pinned to a full-length commit SHA** if available.
- **Workflow permissions:** **Read repository contents and packages permissions**; leave
  **Allow GitHub Actions to create and approve pull requests** disabled.
- **Fork pull request workflows from outside collaborators:** **Require approval for all outside
  collaborators** (or at minimum for first-time contributors).

```bash
# Default workflow token: read-only, cannot approve pull requests
gh api -X PUT repos/OWNER/REPO/actions/permissions/workflow --input - <<'JSON'
{ "default_workflow_permissions": "read", "can_approve_pull_request_reviews": false }
JSON

# Allowed actions: GitHub-owned only, SHA pinning enforced
gh api -X PUT repos/OWNER/REPO/actions/permissions --input - <<'JSON'
{ "enabled": true, "allowed_actions": "selected", "sha_pinning_required": true }
JSON
gh api -X PUT repos/OWNER/REPO/actions/permissions/selected-actions --input - <<'JSON'
{ "github_owned_allowed": true, "verified_allowed": false, "patterns_allowed": [] }
JSON

# Approval for fork pull requests from outside collaborators
gh api -X PUT repos/OWNER/REPO/actions/permissions/fork-pr-contributor-approval \
  -f approval_policy=all_external_contributors
# verify
gh api repos/OWNER/REPO/actions/permissions/workflow
```

If a command returns `422` or `404` for a field your plan or API version does not support, use the
UI path above.

## 6. Delete branches on merge

**UI:** Settings > General > Pull Requests > **Automatically delete head branches**.

```bash
gh api -X PATCH repos/OWNER/REPO -F delete_branch_on_merge=true
# verify
gh api repos/OWNER/REPO --jq .delete_branch_on_merge
```

## 7. Release secrets

The `Publish` workflow needs these repository secrets (Settings > Secrets and variables >
Actions): `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY_ID`,
`SIGNING_PASSWORD`, `GPG_KEY_CONTENTS`. See [PUBLISHING.md](PUBLISHING.md). Consider moving them
into a protected **environment** with required reviewers and referencing it from the publish job.

## 8. Labels used by the contribution workflow

The templates and [CONTRIBUTING.md](../CONTRIBUTING.md) assume these labels exist: `bug`,
`enhancement`, `question`, and `status:approved` (applied by a maintainer when an issue is accepted
for implementation).

```bash
gh label list --repo OWNER/REPO
```
