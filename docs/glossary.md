# English ↔ Hungarian glossary

Terms are translated the same way everywhere in the apps. If you change one, update every
string that uses it. **Please review**, since the Hungarian was written by Claude, not a native speaker.

## Shared

| English | Magyar | Notes |
|---|---|---|
| New game | Új játék | |
| Restart this game | Játék újrakezdése | same deal again |
| Undo / Redo | Visszavonás / Újra | bottom bar says just "Vissza" (space) |
| Hint | Tipp | |
| Daily challenge | Napi kihívás | |
| Statistics | Statisztika | |
| How to play | Játékszabályok | |
| About | Névjegy | |
| Winning streak | Győzelmi sorozat | |
| Vibration | Rezgés | |
| Sound effects | Hangeffektek | |
| Saved games / Slot 1 | Mentett játékok / 1. hely | |
| Load / Save here / Delete | Betöltés / Mentés ide / Törlés | |

## Solitaire (Pasziánsz)

| English | Magyar | Notes |
|---|---|---|
| Solitaire | Pasziánsz | app name |
| Stock | Pakli | face-down pile you draw from |
| Waste | Dobópakli | turned-over cards next to the stock |
| Foundation | Alap | the four Ace→King piles |
| Column (tableau) | Oszlop | the seven piles |
| Pass through the stock | A pakli átlapozása | |
| Finish (auto-complete) | Befejezés | |
| Tap to move / easy mode | Mozgatás koppintással / könnyített mód | |
| Tap a column, then where it goes | Koppints az oszlopra, majd a célhelyre | |
| Large card labels / Four-colour deck | Nagy feliratok a lapokon / Négyszínű pakli | |
| Ace, Jack, Queen, King | ász, bubi, dáma, király | corner letters stay A, J, Q, K (spoken names are Hungarian) |
| clubs, diamonds, hearts, spades | treff, káró, kőr, pikk | "kőr dáma" = Queen of hearts |

## Mahjong

| English | Magyar | Notes |
|---|---|---|
| Tile | Kő | plural kövek |
| Free tile | Szabad kő | |
| Pair | Pár | |
| Shuffle | Keverés | |
| Layout | Elrendezés | |
| Campaign / Level 7 / Next level | Kampány / 7. szint / Következő szint | |
| Restart level / Main menu | Szint újrakezdése / Főmenü | "Főmenü" opens the level grid |
| Stars / Easy / Medium / Hard | Csillagok / Könnyű / Közepes / Nehéz | |
| Dots / Bamboo / Characters | Pötty / Bambusz / Karakter | |
| East, South, West, North wind | Keleti, Déli, Nyugati, Északi szél | corner letters K, D, Ny, É |
| Red, Green, White dragon | Piros, Zöld, Fehér sárkány | corner letters P, Z, F |
| Flower / Season | Virág / Évszak | |
| Turtle, Pyramid, Fortress, Bridge, Cross, Twin Peaks, Diamond, Beginner | Teknős, Piramis, Erőd, Híd, Kereszt, Ikercsúcsok, Gyémánt, Kezdő | layout names |

## Where the strings live

- Shared: `core/ui/src/main/res/values{,-hu}/strings.xml`
- Solitaire: `solitaire/app/src/main/res/values{,-hu}/strings.xml`
- Mahjong: `mahjong/app/src/main/res/values{,-hu}/strings.xml`
