# NetLab Studio

**See networking happen.** A desktop app that makes computer-networking fundamentals visible:
interactive lessons, a network simulator, a subnetting trainer and a simplified Cisco IOS CLI, in
English and Arabic.

> **🚧 Work in progress.** The project skeleton and quality gates are in place; the learning
> features are being built phase by phase. Nothing is ready to use yet.

## Build and run

You need **JDK 21 or newer**, for example [Eclipse Temurin 21](https://adoptium.net/). You do not
need to install Maven: on first use, the Maven Wrapper downloads a pinned Maven version and
checks its SHA-256 checksum.

| Task                     | macOS / Linux           | Windows                   |
|--------------------------|-------------------------|---------------------------|
| Build and run all checks | `./mvnw verify`         | `mvnw.cmd verify`         |
| Run the app              | `./mvnw javafx:run`     | `mvnw.cmd javafx:run`     |
| Format the code          | `./mvnw spotless:apply` | `mvnw.cmd spotless:apply` |

`verify` compiles the code, runs the tests (JUnit 5, AssertJ, ArchUnit), checks formatting
(google-java-format), enforces the coverage gate (JaCoCo) and writes a CycloneDX SBOM to
`target/bom.json`.

## Licence

Code: [MIT](LICENSE) © Ramez Al-Maqtari.
Bundled fonts (Noto Sans, Noto Sans Arabic UI): SIL Open Font License 1.1. See
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
