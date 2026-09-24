# CLAUDE.md — NetLab Studio

You are building **NetLab Studio**, a desktop app (Java 21 + JavaFX 21 + Maven) that teaches
computer-networking fundamentals to university students who struggle with the subject.
It combines interactive lessons, a drag-and-drop network simulator, a subnetting trainer,
and a simplified Cisco IOS CLI. It is also the author's public portfolio project (DevSecOps focus).

Read these before any non-trivial task:
- `docs/SPEC.md` — product spec, architecture, feature list per phase.
- `docs/CURRICULUM.md` — topic map, known errors in the source course material, golden test vectors.

## Hard rules

1. **Scientific accuracy beats everything.** Every explanation, number, header field, port and
   behaviour must match the standards (IEEE 802.3/802.1Q, RFC 791/792/826/793/768/2131, Cisco IOS behaviour).
   `docs/CURRICULUM.md` lists errors found in the course slides — never reproduce them.
   If unsure about a fact, say so in your summary instead of guessing.
2. **Original content only.** Do not copy text, figures or images from the course PDFs/slides
   (Cisco, McGraw-Hill/Forouzan). Write all lesson text and draw all diagrams from scratch.
   Never add the course files to the repo.
3. **Layered architecture.** `core` packages (model, net, sim, cli, lesson, quiz, persistence) must
   NOT import `javafx.*`. All logic lives in `core` and is unit-tested. `ui` only renders and forwards input.
4. **Tests first for logic.** Every class in `core.net`, `core.sim`, `core.cli` gets JUnit 5 tests.
   Use the golden vectors in `docs/CURRICULUM.md`. `mvn verify` must pass before you say "done".
5. **Bilingual (English/Arabic).** All user-visible strings go through `ResourceBundle`
   (`messages_en.properties`, `messages_ar.properties`, UTF-8). No hard-coded UI text.
   Arabic mode sets `NodeOrientation.RIGHT_TO_LEFT`. Technical terms (MAC, ARP, TTL, Frame...)
   stay in English inside Arabic text. IP/MAC addresses and CLI output are always LTR.
6. **Security (this is a DevSecOps portfolio).**
   - Never use Java native serialization (`ObjectInputStream`). Save files are JSON via Jackson
     with explicit DTOs; no polymorphic default typing; validate every field; cap file size (e.g. 5 MB)
     and object counts; reject unknown versions gracefully.
   - The app makes **no network connections** and collects no telemetry.
   - No secrets in the repo. Pin dependency versions. Keep dependencies minimal.
7. **Consistent OSI colours** everywhere (lessons, PDU inspector, animations):
   L7 Application `#8E44AD`, L6 Presentation `#9B59B6`, L5 Session `#3498DB`, L4 Transport `#2E86C1`,
   L3 Network `#27AE60`, L2 Data Link `#F39C12`, L1 Physical `#E74C3C`. Define them once as CSS variables.
8. **Scope discipline.** Do only the phase you were asked for. At the end, stop and write a summary:
   what you built, files changed, test results, anything unsure, and what's left. Do not start the next phase.

## Commands

- Build + all checks: `./mvnw verify` (Windows: `mvnw.cmd verify`)
- Run the app: `./mvnw javafx:run`
- Format: `./mvnw spotless:apply`

## Conventions

- Package root: `io.github.mrramez.netlabstudio` (GitHub user: mrramez, repo: https://github.com/mrramez/netlab-studio).
- Java records for value objects (`Ipv4Address`, `SubnetMask`, `MacAddress`); immutable where possible.
- Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`, `ci:`, `refactor:`), one logical change per commit.
- Work on a branch per phase: `phase-N-short-name`. Never force-push. Never commit to `main` directly.
- Javadoc on public classes in `core`. Comments explain *why*, not *what*.
