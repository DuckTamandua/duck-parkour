package dev.endlessparkour;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.spawn.GlobalSpawnProvider;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.VoidWorldGenProvider;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/** Creates the empty sky world the course is built in. */
final class ParkourWorld {

    /** Players look along +X (the direction the course runs). */
    static final float YAW_EAST = (float) (-Math.PI / 2);

    private ParkourWorld() {
    }

    static CompletableFuture<World> ensure(ParkourConfig cfg, HytaleLogger log) {
        Universe u = Universe.get();
        World existing = u.getWorld(cfg.worldName);
        if (existing != null) {
            return CompletableFuture.completedFuture(existing);
        }
        if (u.isWorldLoadable(cfg.worldName)) {
            log.at(Level.INFO).log("Loading world %s", cfg.worldName);
            return u.loadWorld(cfg.worldName);
        }
        WorldConfig wc = new WorldConfig();
        wc.setDisplayName(Msg.NAME);
        wc.setWorldGenProvider(new VoidWorldGenProvider(color(cfg.grassTint), cfg.environment));
        wc.setSpawnProvider(new GlobalSpawnProvider(
                new Transform(2.5, cfg.startY + 1.0, 0.5, 0f, YAW_EAST, 0f)));
        wc.setGameMode(GameMode.Adventure);
        wc.setPvpEnabled(false);
        wc.setFallDamageEnabled(false);
        wc.setSpawningNPC(false);
        wc.setIsSpawnMarkersEnabled(false);
        wc.setObjectiveMarkersEnabled(false);
        wc.setBlockTicking(false);
        wc.setGameTimePaused(true);
        wc.setGameTime(Instant.parse("0001-01-01T12:00:00Z"));
        if (cfg.weather != null && !cfg.weather.isBlank()) {
            wc.setForcedWeather(cfg.weather);
        }
        // The course is rebuilt for every run: nothing of this world needs to be kept.
        wc.setSavingPlayers(false);
        wc.setCanSaveChunks(false);
        wc.setSaveNewChunks(false);
        wc.setDeleteOnUniverseStart(true);
        wc.markChanged();
        log.at(Level.INFO).log("Creating world %s", cfg.worldName);
        return u.makeWorld(cfg.worldName, u.getWorldsPath().resolve(cfg.worldName), wc);
    }

    static Color color(String hex) {
        try {
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            int rgb = Integer.parseInt(h, 16);
            return new Color((byte) (rgb >> 16), (byte) (rgb >> 8), (byte) rgb);
        } catch (RuntimeException e) {
            return new Color((byte) 0x6A, (byte) 0xB8, (byte) 0x3A);
        }
    }
}
