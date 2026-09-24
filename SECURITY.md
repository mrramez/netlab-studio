# Security Policy

## Supported versions

NetLab Studio has no tagged release yet. Security fixes are made on `main` and ship with the next
release. After the first release, only the latest release gets security fixes.

| Version                  | Supported |
|--------------------------|-----------|
| `main` (unreleased)      | ✅        |
| Latest release           | ✅ (once there is one) |
| Older releases or builds | ❌        |

## Reporting a vulnerability

**Please do not report security problems in public issues, discussions or pull requests.**

Report them privately through GitHub:
[**Report a vulnerability**](https://github.com/mrramez/netlab-studio/security/advisories/new)
(repository *Security* tab → *Advisories* → *Report a vulnerability*).

Please include:

- the affected version or commit;
- steps to reproduce, for example a crafted `.netlab` topology file or progress file;
- what an attacker could achieve (impact);
- a suggested fix, if you have one.

This is a small open-source project. The aim is to:

- acknowledge a report within **7 days**;
- share an assessment and a fix plan within **30 days**;
- publish a GitHub Security Advisory once a fix is released, crediting you unless you prefer
  otherwise.

## Scope

In scope:

- the desktop application, in particular how it reads saved topology and progress files;
- the build and release pipeline (GitHub Actions workflows, release artefacts, SBOM, checksums);
- bundled third-party components shipped in a release.

Out of scope: vulnerabilities in the JDK or JavaFX themselves (please report those upstream), and
attacks that require an already-compromised computer.

## Security practices

These rules apply to all code in the project (see [CLAUDE.md](CLAUDE.md)), including features that
are not built yet:

- **Offline by design:** the app opens no network connections and collects no telemetry.
- **Safe file handling:** no Java native serialization. Save files are JSON read into explicit
  DTOs, with no polymorphic typing, validation of every field, and limits on file size, object
  counts and schema version.
- **Supply chain:** every dependency and plugin version is pinned, Maven itself is pinned by
  checksum, GitHub Actions are pinned to commit SHAs, and Dependabot proposes updates.
- **Automated checks:** CodeQL static analysis, gitleaks secret scanning, dependency review on pull
  requests, and a CycloneDX SBOM generated on every build.
