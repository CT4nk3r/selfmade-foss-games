# Self-made FOSS games

Free and open-source card and tile games for Android, in English and Hungarian.
No ads, no tracking, no internet permission.

| App | Package | What it is |
|---|---|---|
| **Solitaire** (Pasziánsz) | `io.github.ct4nk3r.solitaire` | Klondike, draw one, unlimited passes through the stock |
| **Mahjong** | `io.github.ct4nk3r.mahjong` | Mahjong solitaire: remove matching pairs of free tiles |

## Download

Get the APKs from the [Releases page](https://github.com/CT4nk3r/selfmade-foss-games/releases).
Current versions are **beta**: they are signed with a development key, so once properly signed
releases arrive you will need to uninstall the beta once before installing them.

## Features

**Both games:** unlimited undo/redo, hints, daily challenge (same deal for everyone),
per-variant statistics with streaks and best times, auto-save and resume, timer,
in-app language switch (English / Magyar), light/dark theme, vibration, TalkBack descriptions.

**Solitaire:** drag-and-drop, animated moves and card flips, optional easy mode (tap-to-move and safe auto-moves
to the foundations, both off by default), one-tap finish, sound effects, optional winnable-deals-only (built-in solver),
standard scoring, left-handed layout, table colours and card backs.

**Mahjong:** 8 layouts (Turtle, Pyramid, Fortress, Bridge, Cross, Twin Peaks, Diamond, Beginner),
every deal guaranteed solvable, shuffle that stays solvable, free-tile highlighting,
corner labels, pinch-zoom and pan, 3D tiles drawn in code.

## Harder modes (already in the engine)

All rule differences live in one options class per game, so new difficulty levels only need UI:

- `KlondikeOptions`: `drawCount` (1/3), `maxRedeals` (null = unlimited), `scoring` (standard/Vegas/none),
  `kingOnlyOnEmptyColumn`, `allowFoundationToTableau`, `allowUndo`, `allowHints`.
  Presets: `DRAW_THREE`, `DRAW_THREE_THREE_PASSES`, `VEGAS_DRAW_ONE`, `VEGAS_DRAW_THREE`.
- `MahjongOptions`: `solvableDeals`, `solvableShuffles`, `maxShuffles`, `bonusTilesMatchWithinGroup`,
  `allowUndo`, `allowHints`. Preset: `HARD`.

Statistics are keyed by the rule set, so harder modes get their own stats automatically.

## Project layout

```
core/common      pure Kotlin: undo timeline, stats, timer, daily seed
core/data        on-device storage (DataStore + JSON)
core/ui          Compose theme, shared screens (settings, stats, about), shared strings
solitaire/engine pure Kotlin Klondike rules + solver (unit tested)
solitaire/app    Solitaire Android app
mahjong/engine   pure Kotlin Mahjong rules, layouts, solvable dealer (unit tested)
mahjong/app      Mahjong Android app
docs/glossary.md English ↔ Hungarian terminology
```

## Building

Requires JDK 17 and the Android SDK (platform 35). Create `local.properties` with `sdk.dir=…`, then:

```bash
./gradlew test                      # engine unit tests
./gradlew assembleRelease           # both APKs
```

APKs end up in `solitaire/app/build/outputs/apk/release/` and `mahjong/app/build/outputs/apk/release/`.
Release builds are minified and signed with the local **debug key** for beta testing. Updates only install
over an existing app if they are signed with the same key, so keep building beta APKs on the same machine.
F-Droid builds and signs its own copies.

## Adding a language

1. Add `values-xx/strings.xml` next to the existing `values-hu` in `core/ui`, `solitaire/app` and `mahjong/app`.
2. Add the language to `AppLanguage` in `core/ui` and to `res/xml/locales_config.xml` in both apps.
3. Add it to `resourceConfigurations` in both apps' `build.gradle.kts`.

`./gradlew lint` fails if a string is missing from a translation.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE). All artwork (cards, tiles, icons) is drawn in code or vector XML
in this repository; there are no third-party assets.
