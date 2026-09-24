# Contributing to NetLab Studio

Thanks for helping! NetLab Studio teaches networking to students, so **being correct matters more
than being fast**. Before a non-trivial change, please read [CLAUDE.md](CLAUDE.md) (the project
rules), [docs/SPEC.md](docs/SPEC.md) and [docs/CURRICULUM.md](docs/CURRICULUM.md).

## Setup

- JDK 21 or newer. You do not need Maven: use the wrapper (`./mvnw`, or `mvnw.cmd` on Windows).
- Import the project into your IDE as a Maven project. Files are UTF-8, including the `.properties`
  files; `.editorconfig` sets this up in most editors.

## Branches

- Treat `main` as protected: never commit to it directly, and never force-push.
- Work on a branch: `phase-N-short-name` for roadmap phases; otherwise `feat/…`, `fix/…` or
  `docs/…`.
- Open a pull request to `main`. CI must be green before it is merged.

## Commits

Use [Conventional Commits](https://www.conventionalcommits.org/): `type(optional-scope): summary`.

| Type       | Use it for                                          |
|------------|-----------------------------------------------------|
| `feat`     | a new user-visible feature                          |
| `fix`      | a bug fix                                           |
| `test`     | adding or correcting tests                          |
| `docs`     | documentation only                                  |
| `refactor` | a code change that is neither a fix nor a feature   |
| `build`    | the Maven build, dependencies, the Maven Wrapper    |
| `ci`       | GitHub Actions workflows and Dependabot             |
| `chore`    | repository housekeeping                             |

Make one logical change per commit. Examples: `feat(net): add SubnetMask with prefix validation`,
`fix(sim): drop packets whose TTL reaches 0`.

## Before you open a pull request

```bash
./mvnw spotless:apply   # format the code (google-java-format)
./mvnw verify           # must pass
```

`verify` runs, in order:

1. Maven Enforcer: checks the JDK and Maven versions and that every plugin version is pinned.
2. Compilation with `-Xlint:all -Werror`.
3. All tests, including the ArchUnit architecture rules.
4. The formatting check.
5. The JaCoCo coverage gate: at least 80% line coverage in each of `core.net`, `core.sim` and
   `core.cli`.
6. The CycloneDX SBOM.

## Code rules (a summary of CLAUDE.md)

- **Layering:** `core.*` never uses `javafx.*` and never depends on `ui` or `app`.
  `ArchitectureTest` enforces this. All logic lives in `core`; `ui` only renders and forwards
  input.
- **Tests first for logic:** every class in `core.net`, `core.sim` and `core.cli` has JUnit 5
  tests. Use the golden vectors in `docs/CURRICULUM.md` §4.
- **Accuracy:** facts must match the standards (IEEE 802.3/802.1Q, RFC 791/792/826/793/768/2131,
  Cisco IOS behaviour). Never repeat an error listed in `docs/CURRICULUM.md` §3. If you are not
  sure about a fact, say so in the pull request.
- **Original content only:** do not copy text, figures or images from course slides or textbooks,
  and never commit course files.
- **Bilingual UI:** every user-visible string goes into both
  `src/main/resources/i18n/messages_en.properties` and `messages_ar.properties` (UTF-8). A test
  fails if the two files have different keys.
  - Technical terms such as MAC, ARP, TTL and Frame stay in English inside Arabic text.
  - IP/MAC addresses and CLI output are always shown left to right.
  - Check every screen in Arabic (right to left) as well as in English.
- **Colours:** the OSI layer colours are defined once, in `src/main/resources/css/osi-layers.css`.
  Use those variables; never repeat the hex values.
- **Security:** no Java native serialization, no network connections, no telemetry, no secrets in
  the repository. Report vulnerabilities privately, as described in [SECURITY.md](SECURITY.md).
- **Style:** google-java-format; Javadoc on public classes in `core`; comments explain *why*, not
  *what*.
