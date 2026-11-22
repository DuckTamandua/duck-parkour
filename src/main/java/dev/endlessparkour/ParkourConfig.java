package dev.endlessparkour;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.hypixel.hytale.logger.HytaleLogger;
import dev.endlessparkour.course.Tier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

/** Plugin settings, stored as config.json in the plugin data folder. */
public final class ParkourConfig {

    static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE)
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    /** Name of the world the plugin creates for the parkour (an empty sky world). */
    public String worldName = "parkour";
    /** Sky and ambience of that world (an Environment asset). */
    public String environment = "Env_Zone1_Plains";
    /** Fixed weather of that world. */
    public String weather = "Zone1_Sunny";
    /** Grass colour of that world. */
    public String grassTint = "#6AB83A";
    /** Height of the start island. */
    public int startY = 120;
    /** The course stays between these heights. */
    public int minY = 80;
    public int maxY = 170;
    /** Distance between the lanes of two players (along Z). */
    public int laneSpacing = 128;
    /** How far a course may wander sideways from its lane. */
    public int laneDrift = 14;
    /** Platforms built ahead of the player. */
    public int platformsAhead = 7;
    /** Platforms kept behind the player. */
    public int platformsBehind = 3;
    /** Platforms per area: a random length between these two (33.5 on average). */
    public int sectionLengthMin = 22;
    public int sectionLengthMax = 45;
    /** A run ends when the player is this many blocks below the current and next platform. */
    public double fallDistance = 6.0;
    /** How often positions are checked, in milliseconds. */
    public int tickMillis = 50;
    /** The server is only the parkour: players go straight in when they join and cannot leave. */
    public boolean parkourOnly = true;
    /** Stamina never runs out while running the parkour. */
    public boolean unlimitedStamina = true;
    /** Parts of the normal game screen hidden in the parkour (names of Hytale HUD parts). */
    public String[] hideHud = {"Hotbar", "Health", "Mana", "Stamina", "Oxygen", "InputBindings",
            "AmmoIndicator", "Abilities", "UtilitySlotSelector"};
    /** Puts players in Adventure mode while they run (their mode comes back when they leave). */
    public boolean forceAdventure = true;
    /** Shows the side panel with score and top list. */
    public boolean showHud = true;
    /** Tells everyone in the parkour when the record is beaten. */
    public boolean announceRecords = true;
    /** Lines of /parkour top. */
    public int leaderboardSize = 10;
    /** Lines of the top list in the side panel. */
    public int hudTopSize = 5;
    /** Sound events (empty = no sound). The point sound plays, quietly, on every new platform. */
    public String soundPoint = "SFX_Player_Pickup_Item";
    /** Loudness of the point sound (1 = as loud as in the game). */
    public double soundPointVolume = 0.35;
    /** Loudness of the zone discovery sound when a new biome begins (1 = as loud as in the game). */
    public double soundBiomeVolume = 0.4;
    public String soundRecord = "SFX_Memories_Unlock_Local";
    public String soundFail = "SFX_Portal_Neutral_Teleport_Local";
    /** Area every run begins in. */
    public String startArea = "drifting_plains";
    /** Each area shows its vanilla weather (sky, particles) to the players in it. */
    public boolean areaWeather = true;
    /** Each area gets its vanilla environment: its music and ambient sounds. */
    public boolean areaMusic = true;
    /** Each area gets its vanilla grass colours. */
    public boolean areaColors = true;
    /** New islands fly in from ahead and old ones fly away to the back (instead of popping up and vanishing). */
    public boolean flyIslands = true;
    /** How far islands fly, in blocks. */
    public int flyDistance = 12;
    /** How long a flight takes, in milliseconds. */
    public int flyMillis = 1000;
    /**
     * How plants, leaves and hanging rocks travel with flying islands, unless a player picks another
     * with /parkour plants: "grow" (they fly along tiny and grow during the second half of the flight),
     * "fly" (they fly along at full size) or "land" (they appear when the island lands).
     */
    public String plantStyle = "grow";
    /**
     * Special jumps besides the normal ones, each area with its own mix: ladder towers, bounce mushrooms,
     * long beams, deep drops and dash gaps. False: normal jumps only.
     */
    public boolean specialJumps = true;
    /**
     * Every player holds the dash sword: hold attack to charge it, let go to dash forward. False: no sword
     * and no dash gaps.
     */
    public boolean dashSword = true;
    /** Jumps per difficulty level 1 to 10 (the areas say which level they are). */
    public Tier[] levels = Tier.defaults();

    static ParkourConfig load(Path dir, HytaleLogger log) {
        Path file = dir.resolve("config.json");
        ParkourConfig cfg = new ParkourConfig();
        try {
            Files.createDirectories(dir);
            if (Files.exists(file)) {
                ParkourConfig read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ParkourConfig.class);
                if (read != null) {
                    cfg = read;
                }
            }
            cfg.fix();
            // Write back so new settings appear in the file.
            Files.writeString(file, GSON.toJson(cfg), StandardCharsets.UTF_8);
        } catch (IOException | JsonParseException e) {
            log.at(Level.WARNING).withCause(e).log("Could not read %s, using defaults", file);
            cfg = new ParkourConfig();
            cfg.fix();
        }
        return cfg;
    }

    private void fix() {
        if (worldName == null || worldName.isBlank()) {
            worldName = "parkour";
        }
        if (environment == null || environment.isBlank()) {
            environment = "Env_Zone1_Plains";
        }
        if (levels == null || levels.length == 0) {
            levels = Tier.defaults();
        }
        if (startArea == null || startArea.isBlank()) {
            startArea = "drifting_plains";
        }
        if (hideHud == null) {
            hideHud = new String[0];
        }
        if (soundPoint == null) {
            soundPoint = "";
        }
        // The first test version used the clinking coin sound; it was too loud and sounded like glass.
        if ("SFX_Coins_Hit".equals(soundPoint)) {
            soundPoint = "SFX_Player_Pickup_Item";
        }
        soundPointVolume = Math.max(0.0, Math.min(1.0, soundPointVolume));
        soundBiomeVolume = Math.max(0.0, Math.min(1.0, soundBiomeVolume));
        maxY = Math.max(40, Math.min(300, maxY));
        minY = Math.max(10, Math.min(maxY - 20, minY));
        startY = Math.max(minY + 5, Math.min(maxY - 5, startY));
        laneSpacing = Math.max(48, laneSpacing);
        laneDrift = Math.max(4, Math.min(laneSpacing / 2 - 12, laneDrift));
        platformsAhead = Math.max(3, Math.min(20, platformsAhead));
        platformsBehind = Math.max(1, Math.min(10, platformsBehind));
        sectionLengthMin = Math.max(5, sectionLengthMin);
        sectionLengthMax = Math.max(sectionLengthMin, sectionLengthMax);
        fallDistance = Math.max(4.0, Math.min(20.0, fallDistance));
        tickMillis = Math.max(20, Math.min(500, tickMillis));
        flyDistance = Math.max(2, Math.min(32, flyDistance));
        if (IslandMover.PlantStyle.parse(plantStyle) == null) {
            plantStyle = "grow";
        }
        flyMillis = Math.max(200, Math.min(5000, flyMillis));
        leaderboardSize = Math.max(3, Math.min(50, leaderboardSize));
        hudTopSize = Math.max(0, Math.min(10, hudTopSize));
    }
}
