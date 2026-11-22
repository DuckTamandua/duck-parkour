package dev.endlessparkour;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * Checks the plugin's asset pack (the dash sword, the bounce mushroom, the dash crystal) against the game's
 * Assets.zip: every game file, parent item, particle system and sound it names must exist. A pack asset
 * that fails the server's checks stops the whole server at start ("Mod ... failed to load", exit code 6),
 * which happened once with a wrong icon path. Needs Assets.zip: $env:ASSETS_ZIP, else
 * ..\hytale-testserver\Assets.zip next to this repo; without it the test says so and passes.
 */
public final class AssetPackTest {

    private static final Pattern FILE = Pattern.compile("\"([A-Za-z0-9_/]+\\.(?:png|blockymodel|blockyanim))\"");
    private static final Pattern PARENT = Pattern.compile("\"Parent\"\\s*:\\s*\"([A-Za-z0-9_]+)\"");
    private static final Pattern SYSTEM = Pattern.compile("\"SystemId\"\\s*:\\s*\"([A-Za-z0-9_]+)\"");
    private static final Pattern SOUND = Pattern.compile("\"(?:World|Local)SoundEventId\"\\s*:\\s*\"([A-Za-z0-9_]+)\"");

    private static int failures;

    public static void main(String[] args) throws IOException, URISyntaxException {
        Path classes = Path.of(AssetPackTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path repo = classes.getParent().getParent();
        Path pack = repo.resolve("src/main/resources/Server");
        String env = System.getenv("ASSETS_ZIP");
        Path zip = env != null && !env.isBlank() ? Path.of(env) : repo.resolveSibling("hytale-testserver").resolve("Assets.zip");
        if (!Files.isRegularFile(zip)) {
            System.out.println("Asset pack check skipped: no Assets.zip at " + zip);
            return;
        }
        Set<String> names = new HashSet<>();
        Set<String> items = new HashSet<>();
        Set<String> systems = new HashSet<>();
        Set<String> sounds = new HashSet<>();
        try (ZipFile z = new ZipFile(zip.toFile())) {
            z.stream().forEach(e -> {
                String n = e.getName();
                names.add(n);
                String base = n.substring(n.lastIndexOf('/') + 1);
                if (n.startsWith("Server/Item/Items/") && n.endsWith(".json")) {
                    items.add(base.substring(0, base.length() - 5));
                } else if (n.startsWith("Server/Entity/Effects/") && n.endsWith(".json")) {
                    items.add(base.substring(0, base.length() - 5));
                } else if (n.startsWith("Server/Particles/") && n.endsWith(".particlesystem")) {
                    systems.add(base.substring(0, base.length() - ".particlesystem".length()));
                } else if (n.startsWith("Server/Audio/SoundEvents/") && n.endsWith(".json")) {
                    sounds.add(base.substring(0, base.length() - 5));
                }
            });
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> s = Files.walk(pack)) {
            s.filter(p -> p.toString().endsWith(".json")).forEach(files::add);
        }
        int refs = 0;
        for (Path f : files) {
            String text = Files.readString(f, StandardCharsets.UTF_8);
            String name = pack.relativize(f).toString();
            check(text.trim().startsWith("{") && text.trim().endsWith("}"), name + " is a JSON object");
            refs += checkAll(FILE.matcher(text), name, "game file", r -> names.contains("Common/" + r));
            refs += checkAll(PARENT.matcher(text), name, "parent item or effect", items::contains);
            refs += checkAll(SYSTEM.matcher(text), name, "particle system", systems::contains);
            refs += checkAll(SOUND.matcher(text), name, "sound", sounds::contains);
        }
        check(files.size() >= 6, "the pack has its files (" + files.size() + ")");
        if (failures > 0) {
            System.out.println("FAILED: " + failures);
            System.exit(1);
        }
        System.out.println("Asset pack OK: " + files.size() + " files, " + refs + " game references found in " + zip.getFileName());
    }

    private static int checkAll(Matcher m, String file, String what, java.util.function.Predicate<String> exists) {
        int n = 0;
        while (m.find()) {
            n++;
            check(exists.test(m.group(1)), file + ": " + what + " " + m.group(1) + " exists in the game");
        }
        return n;
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }
}
