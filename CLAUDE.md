# «Duck» Parkour — Hytale server plugin (guide for Claude)

This file is for an AI assistant (Claude Code) working on this project. It explains what the plugin
does, how it is built, why it works the way it does, the Hytale quirks that were found the hard way,
and what to look at when fitting it into another server. `README.md` is the shorter guide for humans.

**Usual task:** adapt the plugin so it fits the rest of a Hytale server (branding, lobby/worlds,
permissions, other plugins). Ask the server owner what the rest of the server looks like before
changing behaviour; most things are already config switches (see "Adapting").

## What it is

An endless, procedurally generated jump course in the sky (version 1.4.4, built for the Hytale server
0.7.7, Java 25). Every run starts on a start island in the green "Drifting Plains" and goes on forever
through 18 areas modelled on Orbis (vanilla names, blocks, plants, grass colours, trees, weather,
music). Falling ends the run; the next run is a brand-new course. A shared top list keeps everyone's best.

- Each player runs alone in their own **lane** (128 blocks apart along Z; the course runs towards +X).
  Any number of players can play at once; nobody sees another player's course up close.
- **Difficulty is fixed per area** (level 1 to 10), not by score. Area order is random after the
  start (never the same twice in a row; easy areas come back). Areas are 22 to 45 platforms long.
- Every jump is checked against Hytale's real movement numbers (see `course/JumpModel.java`); nothing
  of another island ever stands in the way of a jump.
- **Special jumps** (1.4.0, see "Special jumps and the dash sword"): ladder towers, bounce mushrooms,
  beams, deep drops and dash gaps, each area with its own mix, ladder style and beam block.
- **Dash sword**: every player holds it; hold attack to charge, let go to dash forward.
- **Islands glide in and away** (smooth block objects, see "Flights"). Plants/leaves follow a per-player
  style (`/parkour plants grow|fly|land`, default `grow`).
- Side panel (HUD) with area, score, best + rank, top 5. Unlimited stamina, no block breaking or placing,
  no fall damage, vanilla HUD parts hidden.
- Display name: **«Duck» Parkour** (`Msg.NAME`; chat label, panel title, world name). The plugin id and
  data folder are still `EndlessParkour` (see "Adapting" before renaming).

## Build, test, run

- Needs **JDK 25** (`javac --release 25`). Hytale's bundled JRE has no `javac`. Compile against the
  server's `HytaleServer.jar`.
- `build.ps1 -Jdk <JDK 25 folder> -ServerJar <path to HytaleServer.jar>` (Windows PowerShell; without
  them it tries `$env:JDK`/`$env:SERVER_JAR`, then `JAVA_HOME` and a `HytaleServer.jar` next to the
  script) compiles, builds `build/EndlessParkour-<version>.jar` and runs the three tests.
  `HytaleServer.jar` and `Assets.zip` belong to Hypixel Studios: never commit them (`.gitignore`).
  `-SkipTests` skips them. Tests are plain `main` classes (no JUnit):
  - `course/GeneratorTest`: 300 seeds x 400 platforms. Every jump in reach and within its area's level,
    no overlaps, nothing in a jump's way, head room, plants on fitting ground, no lone floating blocks,
    area order random after the start, area lengths, harder levels clearly harder, decoration islands
    equally often in every level, safety platform never overlaps.
  - `IslandMoverTest`: island flights in a pretend world (whole runs, falls mid-flight, foreign blocks,
    chunks loading late, very fast players, objects never shown untimely, the three plant styles, flights
    planned ahead, a stalled server, and a player's game modelled on Hytale's tracker and its LOD rule).
  - `TintMarginsTest`: the grass colour rim around islands on real courses (every column next to an
    island has its colour unless another area is as near, no column gets two colours).
  - `LeaderboardTest`: ranks, records, saving.
- Install: copy the jar into the server's `mods` folder, restart. Config and top list are created in
  `mods/EndlessParkour_EndlessParkour/` (`config.json`, `leaderboard.json`).
- Admin checks in the server console: `parkour selftest --count=600 [--seed=n]` (builds a course far away,
  reads every block back, checks area music/colour, clears it), `parkour selftest --count=30 --fly=true`
  (flight test; needs a player online, runs ~50 blocks below them), `parkour scan` (blocks left over
  from old courses; must be 0), `entity count --world=parkour` (vanilla command; flying objects).
- **Always test thoroughly**: the original owner wanted every change proven (tests that fail on the old
  code, the self test in the server, then a look in the game).

## Code map (`src/main/java/dev/endlessparkour`)

| File | Job |
| --- | --- |
| `EndlessParkourPlugin` | Plugin entry: config, top list, command, events (join/ready/disconnect), block protection, flight ticker, scheduler. |
| `RunManager` | The heart: runs per player (start, progress, fall, finish, leave), lanes, join flow, HUD, titles, sounds, area weather, flights. |
| `Run` | One player's run: lane, generator, live platforms (deque), score, HUD, pending flights, dash watch. |
| `DashSword` | Gives the dash sword into the hand (and takes it back on a lobby server). |
| `course/CourseGenerator` | Pure logic, no game calls, deterministic per seed: next platform, shapes, decoration islands, jump-path rules. |
| `course/Themes`, `Theme` | The 18 areas: blocks, plants, tints, trees, weather, environment, sound, level. |
| `course/Tier` | Jump settings per level 1–10 (gaps, drops, chances, sizes); clamped by `JumpModel`. |
| `course/JumpModel` | Hytale physics: gravity 32, jump force 11.8 (apex 2.18), run 5.5, sprint x1.273, half width 0.325, safety 0.8. |
| `course/Platform`, `Placement`, `Pick` | A generated island (with its `Move`); one block (ladders: `attached`, `facing`); weighted pick lists. |
| `course/Move` | How the player gets onto a platform: jump, climb, bounce, dash, drop. |
| `BlockPlacer` | Places/clears blocks (quiet, no drops), async section loading, grass tint per column, environment per section. |
| `IslandMover` | Flights: plans the way in, moves block objects every tick, lands/lifts islands, plant styles. |
| `EntityBlocks` / `FlyingBlocks` / `BlockAccess` | Block objects (Hytale block entities) / their interface / block interface (fakes in tests). |
| `FlightTicker` | `TickingSystem` that calls `IslandMover.tick` every world tick (parkour world only). |
| `ParkourWorld` | Creates the void sky world (`VoidWorldGenProvider`), adventure mode, no saving. |
| `ParkourHud` | Side panel (`CustomUIHud`, inline UI markup). |
| `Protection` | Cancels break/damage/place events in the parkour world (one class per system). |
| `Leaderboard` | Top list + per-player stats in JSON, saved off the world thread. |
| `ParkourCommand` | `/parkour` (alias `pk`) and sub commands. |
| `ParkourConfig` | `config.json` (Gson, UpperCamelCase keys), clamps bad values, writes new keys back. |
| `Msg` | Chat style: `«Duck» Parkour » text`, values in yellow, errors red. |
| `SelfTest`, `WorldScan` | Admin checks (see above). |

## How a run works

1. **Join.** With `ParkourOnly` (default) the whole server is the parkour: in the async
   `PlayerConnectEvent` the plugin sets the target world and builds the start island *before* the player
   enters, then moves the player's holder onto it (`SpawnUtil.applyTransform`). Without this the client
   waited ~30 s for an unloaded chunk in the void. Without `ParkourOnly`, `/parkour` saves a return point
   (world, position, game mode), switches to adventure and teleports into the parkour world;
   `/parkour leave` brings the player back. (That lobby mode works but was not re-tested in game since
   version 1.2.)
2. **Start.** A lane is taken, a `CourseGenerator` with a random seed builds the start island and
   platforms 1–7 (`PlatformsAhead`). Start + platform 1 are placed at once; 2–7 fly in once the player
   stands on the start island (+600 ms: the game wakes the world around a player only then).
3. **Ticks.** Every 50 ms (`TickMillis`) a world task checks positions: standing on a newer platform →
   score, new platform(s) fly in ahead, old ones (`PlatformsBehind` = 3) fly away; a new area → title
   (area name + zone), discovery sound, weather. Falling `FallDistance` below the platforms → fail.
4. **Fail.** Score goes to the top list (record announcement), flights are cancelled, the old course is
   cleared, a new run starts in the same lane right after the old course (`RunManager.startX`, 24 blocks
   past its last platform) and the player is teleported to its start. (Until 1.4.2 every run started at
   x = 0: after a long run that part of the world had gone to sleep, and for over a minute islands could
   not fly in there and flying objects vanished: "no more islands".)
5. **Disconnect / leave.** The run ends (score kept), flights cancelled, course cleared, lane freed.
   Verified: nothing is left behind (`parkour scan` 0 blocks, no objects).

## Special jumps and the dash sword (1.4.0)

- **Where they come from**: `Theme.features(chance, Feature, weight, ...)` per area in `course/Themes.java`
  (plus `.ladder(...)` and `.beam(...)`). `CourseGenerator.next` tries one with that chance from platform 3
  on, never on an area start and never two in a row (`sinceFeature`). How the player gets onto each
  platform is `Platform.move()` (`course/Move`: JUMP, CLIMB, BOUNCE, DASH, DROP); beams are normal jumps.
- **Two-island jumps**: a ladder tower first builds an island with a bare far row (`launch`, `Extra.bareX`),
  then the tower (`secondStep`), which owns the ladder it puts on that island (it comes and goes with the
  tower; `clashesBeside` lets it touch the island). Dashes and bounces are built as pairs in one go
  (`dashPair`, `bouncePair`: the take-off island, then the target, handed out by the next `next()` call via
  `nextUp`; if the target does not fit, `forget` takes the take-off back), so a mark always means its jump.
- **Ladder jumps** (1.4.4, owner: "Jumps wo man an Leitern springen muss bzw. dashen"): `climb` picks by
  level: walk-up towers (always on levels 1-3), `LADDER_JUMP` from level 4 (a tower 3-5 cells past the
  island's edge, the ladder hanging in the gap from 2 below the surface, so a short jump still catches it)
  and `LADDER_DASH` from level 7 in areas with dash jumps (8 cells past a glowing take-off, the ladder from
  2 above the surface: `JumpModel.travelAbove` shows a jump cannot touch it without a dash). Grabbing a
  ladder in the air was not tried in play yet.
- **Hytale's pull-up at edges** ("mantling", the builtin plugin `Hytale:Mantling` turns on
  `ClientFeature.Mantling`; the server sees `MovementStates.mantling`): players grab ledges and pull up.
  The owner wants it kept, but it must not skip special jumps: towers (6-8) and bounce targets (5-6) stand
  higher than `JumpModel.MANTLE_HEIGHT` (4.5, a cautious guess: apex 2.18 + about 2 of reach), and dash
  gaps (7) are longer than `maxGapNoDash` (a perfect sprint jump plus `MANTLE_REACH` 1 for the grab). Every
  pull-up is logged ("Pull-up of <name>: +N blocks, onto platform ..."): check the guess there. Normal
  jumps can still be saved by grabbing the edge. (The server config could turn it off with
  `"Mods": {"Hytale:Mantling": {"Enabled": false}}`, read by `PluginManager.canLoadOnBoot`; untested.)
- **Marks**: both ends of a dash glow purple and sparkle (`Themes.DASH_MARK` = the plugin's own block
  `EndlessParkour_Dash_Mark`: the purple crystal block plus `Block_Gem_Sparks` particles; top two layers of the
  take-off's far row and the target's near row). The bounce mushroom is the plugin's own block too
  (`EndlessParkour_Bounce_Mushroom`: the bouncy shelf mushroom with the yellow texture, light and Hytale's
  `Dungeon_Goblin_Indicator_Mushroom_Bouncy` particles, a beam with rings made for bouncy mushrooms) and
  stands on a glowing yellow crystal (`Themes.BOUNCE_MARK`). None of these is used anywhere else (GeneratorTest
  checks). Without these blocks (asset pack missing) `Theme.filter` drops dashes and bounces.
- **Ladders**: `Placement.attached(... facing)` leans on the wall on that side of its cell.
  `BlockPlacer.wallTurns` picks the turn from the game's own turned hitboxes (no guessing which way a turn
  goes); the self test reads every ladder back ("Ladders: N lean on their tower, 0 turned wrong"). They
  run from the island's surface up to the tower's top block: the player gets off at the top on their own
  (1.4.0 put one piece above the edge; the owner: not needed). Ladders
  never fly (`IslandMover` makes no objects for attached blocks): they appear when the tower lands.
- **Bounce**: the mushroom (`EndlessParkour_Bounce_Mushroom`, made from `Plant_Crop_Mushroom_Shelve_Brown`:
  MovementSettings IsBouncy, BounceVelocity 20) stands on the far row of the pad island (its own block). The course counts on the
  game's 20 blocks/s (lifts 6.25) with `BOUNCE_SPARE` 0.75 and keeps the space up to the top free. Every
  bounce is logged ("Bounce of <name>: rose N blocks"): not measured in play yet.
- **Dash sword** (asset pack in `src/main/resources/Server`, `IncludesAssetPack` true, `build.ps1` copies
  all resources): item `EndlessParkour_Dash_Sword` (Parent `Weapon_Sword_Mithril`, Primary
  `Root_EndlessParkour_Dash`), a `Charging` interaction (0.25 s, AllowIndefiniteHold) and the dash: a
  `CooldownCondition`/`TriggerCooldown` (1 s) and `ApplyForce` (Force 20, Duration 0.25, ChangeVelocityType
  Set, level along the view). `DashSword` gives it into the hand at every run start, `Protection.NoDrop`
  stops drops. The client computes forced movement itself (`SplitVelocity` is a client feature), so the
  dash reach is not known from the server; measured in play (log "Dash of <name>: N blocks in T s, ...
  more than sprinting"): in the air 6.5-8 blocks in about 0.4 s, 3.8 to 5.1 more than sprinting; the course
  counts on 3.8 (`JumpModel.DASH_GAIN`). Every special jump reached is logged too ("CLIMB of <name> onto
  platform ... in T s"; climbing is about 2-3 blocks/s).
- **Harder with the level** (1.4.2, owner: special jumps must not soften the top levels): special islands take
  the level's platform sizes (`size(tier, ...)`), tower tops 2 x 2 / 1 x 2 from level 7 / 1 x 1 from 9, dash
  targets two deep along the dash (a dash moves a fixed distance) and 2 x 1 from level 9, bounces 2-3 away
  from level 8, beams 4-5 / 3-4 / 3 long. 1.4.1's specials (always 2-3 big) made levels 8-10 easier and 9 = 10;
  `GeneratorTest` now checks over ALL platforms that every level from 3 on asks more than the one before
  and the top level stays at 66 % or more (measured 0, 0, 4, 14, 22, 30, 49, 60, 66, 71 %; 1.3.6 without
  specials: 0, 0, 5, 10, 18, 25, 49, 59, 64, 72 %).
- **Config**: `SpecialJumps` (all off), `DashSword` (no sword, no dash gaps; also when the item is missing).
- **The asset pack can stop the server**: a pack asset that fails the server's checks (once: an icon path
  that does not exist) makes the server refuse to start ("Mod EndlessParkour:EndlessParkour failed to load",
  exit code 6). `AssetPackTest` (in `build.ps1`) checks every game file, parent, particle system and sound the
  pack names against Assets.zip (`$env:ASSETS_ZIP` or `..\hytale-testserver\Assets.zip`).

## Flights (smooth islands) — read before touching `IslandMover`

Blocks can only stand on whole positions and appear instantly, so an island cannot glide as blocks. A
flying island is therefore a set of **block entities** (`BlockEntity` component + `TransformComponent` +
`EntityScaleComponent` + our own `BoundingBox` + `Intangible` + `DespawnComponent`, deliberately **no
`Velocity`**, so no gravity or physics) moved every world tick, then swapped for real blocks.

- **Every world tick** (worlds run at 30 TPS): `FlightTicker` is an ECS `TickingSystem`. A 20 Hz
  scheduler was tried first and stuttered (updates landed 33/66 ms apart).
- **Structural changes never inside a system tick**: adding/removing entities or blocks happens in world
  tasks (`world.execute`); the store throws "Store is currently processing" otherwise. Moving and
  resizing (component mutation) is done in the tick.
- **Arriving**: objects are made a batch per tick (128; 64 take about 1 ms), far below at y=1.5, tiny,
  300 ms before the flight (`HIDE_MS`; `chooseWay` never plans a flight sooner), so the players' games get
  them out of sight; at the start they move up to the start of the way. Objects not made at least 150 ms
  before the start (`SETTLE_MS`, a stalled server) do not fly: their blocks just appear on landing, as
  popping up mid-flight would show. Islands glide in (ease-out) and grow. On landing the real blocks are
  placed while the objects still cover them at full size for 120 ms (the client needs a few frames to
  draw new blocks, else it flickers), then the objects slip inside (scale 0.98) and are removed at 250 ms.
- **What a player's game gets (Hytale's "LOD cull")**: the tracker sends an entity only within the view
  radius and only while its box is at least `EntityTrackerSystems.LODCull.ENTITY_LOD_RATIO` (3.5e-5) x
  distance² thick. A block entity's box is its block hitbox, scaled with the entity every tick by
  `EntityScaleBoundingBoxSystem`, but not yet in the entity's first tick. With the block's own box a 5 %
  object more than ~38 blocks away reached the game in its first tick, was taken back in the next and was
  sent again mid-flight: the game built every island twice (players saw rare flashes in the distance,
  fixed in 1.3.1). So flying objects get their own big box (`EntityBlocks.sendBoxSize`, ~69 blocks,
  computed from the ratio for 256 blocks at scale 0.05). It only decides what is sent: collisions, aiming
  and NPC searches ignore intangible entities (`EntitySpatialSystem` and `TangiableEntitySpatialSystem`
  exclude `Intangible`).
- **Flight watch (log)**: every tick `IslandMover` checks which objects the owner's game has (the
  tracker's `EntityViewer.sent`) and logs `Flight watch: 100 arrivals, ...` every 100 arrivals, plus a
  line for an odd flight (at most every 10 s). Healthy: every object "in the game out of sight before
  its flight", 0 reached it during the flight, 0 lost, 0 shown again, 0 not made in time.
- **Leaving**: objects appear inside the blocks first, then the blocks go, the objects glide away (-X)
  and shrink.
- **Ways in**: from ahead (+X) when free, else from below, above or the sides; `chooseWay` checks the
  whole way in space and time against standing islands and other flights, and may start a flight a bit
  later. Tests show 0 cases of islands flying through each other.
- **Grass colour**: the block entity packet only carries block id + scale; the client tints grass by the
  column the object is in. So the whole flight way is tinted in the area colour first (`prepareFlight`).
  A player's game takes an object's grass colour when it gets the object, so the objects of an arriving
  island are made only 100 ms after its colours were set and sent (`flyIn(..., waitForColours)` +
  `coloursReady`, called when `prepareFlight` completes; it often has to load sections first). 1.3.1 made
  them at once and islands flew in the old colour, changing it on landing; before 1.3.1 this was hidden
  because the game rebuilt every object mid-flight (the LOD problem). Colours that come too late: the
  island lands without flying. Flights are planned 500 ms ahead to leave room for this.
  Solid grass blends its colour with the columns around it, a flying object has one plain colour: so the
  columns up to 4 around each island get its colour too (`TintMargins`, `BlockPlacer.marginColumns`),
  except where an island of another colour is as near (each column goes to its nearest island). Without
  that rim, islands changed their look at the edges the moment they became solid (owner's report).
  Keep sending the chunk colours (`updateChunkTints`) for every flight, even when they look unchanged: an
  experiment sent them only on real changes, and the players' games then kept stale colours (flying
  islands showed the wrong colour until a later update). Most of these updates are real changes anyway
  (new columns ahead of the player).
- **Plants**: the client draws moving objects plainer than blocks, plants and leaves most of all. Styles:
  `grow` (tiny, they grow in the second half of the flight), `fly` (full size), `land` (no plant objects,
  they appear on landing). In every style plants become blocks in the same moment as the island
  (sprouting them afterwards looked odd to the owner).
- **Entities only exist near players.** Far away (inactive chunks) `store.addEntity` returns null and the
  game puts the entity into the chunk, where it pops up later. The mover then flies that island without
  objects (it just stands at its place on time) and logs "Flying objects could not be made". Every object
  also gets a despawn timer (flight time + 5 s) as a safety net.
- Load: flight bookkeeping ~0.05 ms per tick; up to ~1000 objects at a run start with `grow`/`fly` (6
  islands at once), ~120 per platform step. **Only tested with one player at a time.**

## Hytale 0.7.7 facts found the hard way

- Blocks: `BlockOperations.setBlock(chunkStore, sectionRef, x, y, z, id, BlockType, rotation, 0, settings)`
  with `chunkStore.getChunkSectionReferenceAtBlock(Async)`; ids via `BlockType.getAssetMap().getIndex(name)`;
  `SetBlockSettings.NO_SEND_AUDIO | NO_SEND_PARTICLES | NO_DROP_ITEMS` for quiet placement.
- Upside-down blocks: `RotationTuple.index(Rotation.None, Rotation.OneEighty, Rotation.None)`.
- Grass colour is per column: `BlockChunk.setTint(localX, localZ, argb)` + `world.getNotificationHandler()
  .updateChunkTints(chunkIndex)`.
- Music and ambience follow the **environment** of the 32³ section: `EnvironmentSection.setUniform(envId)`
  + `updateSectionEnvironments(sx, sy, sz)`. (`BlockChunk.readEnvironmentRuns` is deprecated for removal.)
- Weather per player: `WeatherTracker.setOverrideWeatherIndex(Weather.getAssetMap().getIndex(id))`.
- Teleport: `store.putComponent(ref, Teleport.getComponentType(), Teleport.createForPlayer(world, transform))`;
  yaw -PI/2 faces +X; `Rotation3f` is (pitch, yaw, roll).
- HUD: `CustomUIHud` + `UICommandBuilder.appendInline(null, markup)`, `set("#Id.Text", ...)`. Give panels
  an explicit height (`setObject("#Id.Anchor", anchor)`), else they fill the screen.
- Stamina: `EntityStatMap.setStatChangeSuppressed(stamina, StatChangeDirection.LOWER, true)`.
- Commands: `AbstractPlayerCommand` runs on the player's world thread; `requireNoPermission()` opens a
  command to everyone; optional args are written `--name=value`.
- A plugin whose `setup()` throws **stops the whole server** (exit code 9). ECS systems are keyed by
  class: register one class per system (that is why `Protection` has three subclasses).
- World ticks: 30 TPS; `world.execute` tasks run between ticks.
- Entity visibility: within the player's view radius (`player viewradius get --player=<name>` in the
  console; 16 chunks = 512 blocks on the test server) and passing the LOD rule above; what a player's game
  has right now is `EntityTrackerSystems.EntityViewer.sent` on the player.
- **Fonts**: the UI font (NunitoSans atlas) shows "?" for many symbols (e.g. ʚ ɞ ★ ♥) and has no
  fallback. Safe decorations: « » · ~ ° ¤ § and Cyrillic letters (є з Є З Э …); the bold title font
  (Lexend) has no Cyrillic.
- Tall plants (two-block hitboxes such as Plant_Bush_Dead, burnt wheat) vanish when any block sits right
  above them; the generator keeps that space free. Damaging plants (brambles, cactus) and slippery or
  soft blocks (ice, snow, mud) are kept off landing surfaces.

## Adapting to another server (checklist)

1. **Parkour-only or part of a bigger server?** `ParkourOnly` (true = joining players land straight in
   the parkour and cannot leave; false = `/parkour` from a lobby, `/parkour leave` back). With false,
   check the return flow and game mode handling (`RunManager.join/leave`), and that nothing else teleports
   players out of the parkour world mid-run.
2. **Branding**: `Msg.NAME` (one constant), colours in `Msg`, panel layout in `ParkourHud`, area titles in
   `RunManager.title`. Stick to font-safe characters.
3. **Plugin id**: `manifest.json` Group/Name `EndlessParkour` decides the data folder
   (`mods/EndlessParkour_EndlessParkour`). Renaming it means moving `config.json` and `leaderboard.json`.
4. **Permissions**: everything is open except `selftest` and `scan` (admin). Check how the server grants
   command permissions and adapt `requireNoPermission()` calls.
5. **World**: `WorldName`, default `Environment`/`Weather`/`GrassTint`, height band (`StartY`, `MinY`,
   `MaxY`), `LaneSpacing`/`LaneDrift`. The parkour world is deleted and rebuilt at every server start.
6. **Gameplay**: `Levels` (per-level jump settings; values beyond what a jump can clear are clamped),
   `SectionLengthMin/Max`, `StartArea`, areas in `course/Themes.java`, `FallDistance`, `PlatformsAhead/Behind`.
7. **Flights**: `FlyIslands`, `FlyDistance`, `FlyMillis`, `PlantStyle`. Turning flights off makes islands
   pop up and vanish instantly (lighter for many players).
8. **Sounds**: `SoundPoint` (+ volume), `SoundBiomeVolume`, `SoundRecord`, `SoundFail`.
9. **HUD**: `ShowHud`, `HudTopSize`, `HideHud` (vanilla HUD parts hidden in the parkour), `UnlimitedStamina`,
   `ForceAdventure`, `AnnounceRecords`, `LeaderboardSize`.
10. **Other plugins**: there are no hooks yet; natural entry points are `RunManager.join/leave` and
    `Leaderboard` (scores, ranks). Protection only covers the parkour world.
11. **Hytale updates**: everything above was verified on 0.7.7; re-check the APIs listed in "facts" after
    an update (the build fails loudly on API changes; behaviour changes need the self test and a look).

## Known limits and open points

- Not yet seen in play: bounces and drops (the bounce height is the asset's value) and the real reach of
  Hytale's pull-up at edges (`MANTLE_HEIGHT` is a guess; the log shows every pull-up).
- Several players at once: lanes and flights are per player, but the plugin was only played by one person.
  Watch entity counts and tick times with more players (`parkour selftest --fly=true` prints timings).
- Block objects look plainer than blocks while flying (client side); plants are the most visible case.
- **A rare flicker of islands in the distance is unsolved.** The owner still sees it now and then, more
  often when the game lags. Ruled out so far:
  - Objects reaching the game twice: that was real and is fixed in 1.3.1; the flight watch in the log
    shows every object in the game before its flight and none lost or sent again.
  - The jump of the objects from their hiding place at the bottom of the world to the start of their
    way: an experiment that made them wait tiny right at the start (no jump at all, and 500 ms instead of
    250 ms overlap at landing and take-off) did not help.
  - The repeated colour updates: sending only real changes did not help and left stale colours.
  The server showed no lag at those moments, so the cause is probably on the client side. A short screen
  recording of the moment would be the best next clue.
- The lobby mode (`ParkourOnly=false`) was not re-tested in game after version 1.2.
- Config keys are written into `config.json` at start; changed defaults only reach an existing server
  after editing or deleting those keys there.

## Conventions

- All player-facing texts, config and code comments in **English**, short labels.
- Course logic stays free of game calls (testable); world access only on the world thread.
- Keep `IslandMover` changes covered by `IslandMoverTest`, generator changes by `GeneratorTest`.
