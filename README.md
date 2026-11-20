# «Duck» Parkour (Hytale server plugin) 1.3.1

Plugin id and files keep the old name `EndlessParkour` (jar, data folder `EndlessParkour_EndlessParkour`), so the config and top list stay.

An endless jump course in the sky. Every run is generated anew, so no two runs look the same.
Fall and you start over at 0 on a brand-new course; the old one is removed. A shared top list
keeps everyone's best run.

- Parkour-only server (default): players join straight onto their own start island. There is no
  other world to go to, so `/parkour leave` is switched off.
- Every player runs in their own lane (128 blocks apart), so any number of players can play at once.
- Areas of Orbis with their vanilla names, blocks, plants, grass colours, trees, weather, music and
  ambient sounds. Every run starts in the green Drifting Plains; after that the areas come in a fully
  random order (never the same one twice in a row, easy ones can come back later), each 22 to 45
  platforms long (random). A title shows the area and its zone, with the zone's discovery sound.
- The difficulty comes from the area: level 1 (Drifting Plains) to level 10 (Charred Woodlands).
- Jumps are sized from Hytale's own movement numbers (gravity 32, jump force 11.8, run speed 5.5,
  sprint x1.273): never more than one block up, always within reach with margin. Nothing of another
  island stands in the way of a jump.
- Islands are shaped like vanilla floating islands: rounded, deeper in the middle, with plants on top
  and stalactites hanging below. Area starts and about every fifth platform get a decoration island
  beside the course with a tree of the area (or ruin pillars for temples, outposts and hives).
- New islands glide in smoothly from ahead (or rise from below or come from the side when that way is
  not free), growing out of nothing, and turn into real blocks when they land; old ones glide away to
  the back and shrink. A flying island never passes through another one. The start island and the
  first jump are there at once; the rest of the first islands fly in once the player stands on the start.
  (Flying islands are block objects; see `CLAUDE.md` for how and why.)
- Unlimited stamina, no breaking, damaging or placing blocks, no fall damage.
- A side panel shows the area, score, best (with rank) and the top 5 (`/parkour hud` turns it off).
  Hotbar, health, stamina bar and the key hints are hidden in the parkour.
- A quiet sound on every new platform and a softened zone sound for every new area
  (config: `SoundPoint`, `SoundPointVolume`, `SoundBiomeVolume`).

## Areas

| Area | Zone | Level |
| --- | --- | --- |
| Drifting Plains (start) | Emerald Wilds | 1 |
| Seedling Woods | Emerald Wilds | 2 |
| Kweebec Village | Emerald Wilds | 2 |
| The Fens | Emerald Wilds | 3 |
| Trork Outpost | Emerald Wilds | 3 |
| Forgotten Temple | Emerald Wilds | 3 |
| Golden Steppes | Howling Sands | 4 |
| Feran Village | Howling Sands | 4 |
| Badlands | Howling Sands | 5 |
| Scarak Hive | Howling Sands | 5 |
| Crystalline Depths | Oceans | 5 |
| Desolate Basin | Howling Sands | 6 |
| Frostmarch Tundra | Whisperfrost Frontiers | 6 |
| Boreal Reach | Whisperfrost Frontiers | 7 |
| Outlander Camp | Whisperfrost Frontiers | 7 |
| The Everfrost | Whisperfrost Frontiers | 8 |
| Cinder Wastes | Devastated Lands | 9 |
| Charred Woodlands | Devastated Lands | 10 |

| Level | Gap (blocks) | Up-jump gap | Gap with a drop of 2+ | Largest drop | One-block pillars | Platform size | What you notice |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 1-2 | 1 | 2 | 1 | 0 % | 3 | big islands, short hops |
| 2 | 2 | 2 | 2 | 1 | 0 % | 2-3 | every gap two blocks |
| 3 | 2-3 | 2 | 3 | 2 | 5 % | 2-3 | gaps up to three |
| 4 | 2-3 | 3 | 3 | 2 | 10 % | 2 | up-jumps over three blocks |
| 5 | 2-3 | 3 | 4 | 2 | 15 % | 1-2 | narrow platforms |
| 6 | 3 | 3 | 4 | 3 | 20 % | 1-2 | every gap at least three |
| 7 | 3-4 | 3 | 4 | 3 | 30 % | 1-2 | gaps up to four |
| 8 | 3-4 | 3 | 5 | 3 | 40 % | 1-2 | many pillars, long drops |
| 9 | 3-4 | 3 | 5 | 3 | 50 % | 1-2 | every other platform a pillar |
| 10 | 3-4 | 3 | 5 | 4 | 65 % | 1-2 | two of three platforms a pillar |

Gaps of more than four blocks are not safely jumpable in Hytale, so the top levels get harder through
smaller platforms, pillars and up-jumps.

## Install

1. Download `EndlessParkour-<version>.jar` from the [Releases](../../releases) page (or build it, see below)
   and copy it into the server's `mods` folder (remove older versions).
2. Restart the server. The plugin creates the world `parkour` (an empty sky world, rebuilt at every start).

## Commands

| Command | What it does |
| --- | --- |
| `/parkour` (or `/pk`) | Start over |
| `/parkour top` | Best players |
| `/parkour stats` / `/parkour stats --player=<name>` | Stats of you or another player |
| `/parkour hud` | Side panel on/off |
| `/parkour plants grow\|fly\|land` | How plants, leaves and hanging rocks come with your flying islands (your islands only) |
| `/parkour leave` | Leave (only when `ParkourOnly` is off) |
| `/parkour selftest --count=<n>` | Admin: build a test course, check every block, remove it |
| `/parkour selftest --count=<n> --fly=true` | Admin: the same with flying islands (about ten at once), with timings |
| `/parkour scan` | Admin: count blocks left over from old courses (should be 0) |

## Config (`mods/EndlessParkour_EndlessParkour/config.json`)

`ParkourOnly`, `UnlimitedStamina`, `HideHud`, height band, lane spacing, platforms built ahead,
area length (`SectionLengthMin`/`Max`, default 22 to 45), sounds and side panel.

- `StartArea`: the area every run begins in (default `drifting_plains`).
- `FlyIslands`, `FlyDistance`, `FlyMillis`: islands fly in and away (default on, 12 blocks, 1000 ms).
- `PlantStyle`: how plants travel with flying islands unless a player picks another with `/parkour plants`:
  `grow` (default: tiny at first, they grow during the second half of the flight), `fly` (full size all
  the way) or `land` (they appear when the island lands). The game draws moving objects plainer than
  blocks, plants most of all; in every style they become real blocks the moment the island lands.
- `AreaWeather`, `AreaMusic`, `AreaColors`: each area shows its vanilla weather, plays its music and
  ambient sounds, and colours its grass (all on by default).
- `Levels`: the jumps of difficulty levels 1 to 10 (gap range, drop, chances for up/down/sideways
  jumps and one-block pillars, platform size). Values beyond what a Hytale jump can clear are clamped.
  The old `Tiers` key of version 1.2 is no longer used.

The top list is stored in `leaderboard.json` next to the config.

## Build

Needs Windows PowerShell, a JDK 25 and `HytaleServer.jar` from your own Hytale server (not part of
this repo). Put `HytaleServer.jar` next to `build.ps1` (git ignores it) or pass the paths:

```powershell
./build.ps1 -Jdk "C:\path\to\jdk-25" -ServerJar "C:\path\to\HytaleServer.jar"
```

Without `-Jdk` it uses `JAVA_HOME`. The jar lands in `build/`; `-SkipTests` skips the tests.

`./build.ps1` compiles against `HytaleServer.jar` (JDK 25) and runs the tests: 300 random courses
x 400 platforms (every jump in reach and within its area's level, nothing in the way of a jump,
no overlaps, plants on fitting ground, area order random after the start, area lengths, harder
levels have bigger gaps and more pillars), flying islands in a pretend world (whole runs, falls,
foreign blocks, late chunks: no island damaged, nothing left behind, none passing through another),
and the top list (ranks, records, saving).

Adapting it with an AI assistant: `CLAUDE.md` explains the design, the Hytale quirks and what to change
when fitting the plugin into another server.

## License

MIT, see [LICENSE](LICENSE). An unofficial fan plugin, not made by or affiliated with Hypixel Studios.
