## What and why

<!-- What does this pull request change, and why? Link the phase or issue. -->

## How it was tested

<!-- Tests added or updated, and any manual steps. -->

## Checklist

- [ ] `./mvnw verify` passes locally (tests, formatting, coverage gate, architecture rules)
- [ ] New or changed logic in `core` has JUnit 5 tests, using the golden vectors in
      `docs/CURRICULUM.md` §4 where they apply
- [ ] Facts checked against the standards and `docs/CURRICULUM.md`; no error from the §3 errata
      table is repeated
- [ ] All lesson text and diagrams are original (nothing copied from course slides or textbooks)
- [ ] Every new user-visible string is in **both** `messages_en.properties` and
      `messages_ar.properties`
- [ ] UI checked in English and in Arabic (right to left), in light and dark themes
- [ ] Screenshots attached for UI changes (EN and AR)
- [ ] No `javafx.*` in `core`; no network access, telemetry, native serialization or secrets
- [ ] Commits follow Conventional Commits, one logical change per commit

## Screenshots

<!-- UI changes only: before and after, in English and Arabic. -->
