# Third-party notices

NetLab Studio's own code is under the MIT licence (see [LICENSE](LICENSE)). The files below are
bundled with the app and keep their own licences.

## Fonts: SIL Open Font License 1.1

| File in `src/main/resources/fonts/` | Font and version                     | SHA-256                                                            |
|-------------------------------------|--------------------------------------|--------------------------------------------------------------------|
| `NotoSans-Regular.ttf`              | Noto Sans Regular 2.015              | `478c558ea716033cd60c03438f628dfa75694dcf6b5f6d505a2f05fd2b4f3823` |
| `NotoSans-Bold.ttf`                 | Noto Sans Bold 2.015                 | `1df075a380fc7cb898acf64c1f7b3b4dd780de3caa860178bf929de35817a913` |
| `NotoSansArabicUI-Regular.ttf`      | Noto Sans Arabic UI Regular 2.011    | `c56275c744ded6ff6df13de04963e6174632f0405a54a83f44d0fe5395f45ae6` |
| `NotoSansArabicUI-Bold.ttf`         | Noto Sans Arabic UI Bold 2.011       | `ba511a9cf3712cc801203f5fcaf5b35221830f975ec0fc91678e9a4ed07a1f6a` |

- **Source:** the Noto project's distribution repository,
  [notofonts/notofonts.github.io](https://github.com/notofonts/notofonts.github.io) at commit
  `e0ad9f160a2942bcdd249bb1958e8336742edef3`, hinted TrueType files from
  `fonts/NotoSans/hinted/ttf/` and `fonts/NotoSansArabicUI/hinted/ttf/`. Their versions match the
  upstream releases `NotoSans-v2.015`
  ([notofonts/latin-greek-cyrillic](https://github.com/notofonts/latin-greek-cyrillic)) and
  `NotoSansArabicUI-v2.011` ([notofonts/arabic](https://github.com/notofonts/arabic)).
- **Why the "UI" Arabic font:** Noto Sans Arabic UI is the member of the Noto Sans Arabic family
  made for user interfaces. Its line height matches Noto Sans (1.36 em instead of 2.11 em), so
  Arabic labels are not taller than English ones.
- **Licences:** `OFL-NotoSans.txt` and `OFL-NotoSansArabicUI.txt`, copied unchanged from the release
  tags above and shipped next to the fonts.

## Maven dependencies

Every build lists the Maven dependencies, with versions and licences, in a CycloneDX SBOM at
`target/bom.json`.
