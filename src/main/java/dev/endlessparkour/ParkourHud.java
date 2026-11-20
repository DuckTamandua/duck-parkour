package dev.endlessparkour;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.List;

/** Side panel on the right: biome, score, best and the top list. Its height follows its content. */
final class ParkourHud extends CustomUIHud {

    static final String KEY = "EndlessParkour";

    // Layout in pixels; the panel height is the sum of the rows it shows.
    private static final int RIGHT = 20;
    private static final int TOP = 180;
    private static final int WIDTH = 240;
    private static final int PADDING = 12;
    private static final int TITLE_ROW = 26;
    private static final int BIOME_ROW = 22;
    private static final int LINE_ROW = 14;
    private static final int SCORE_ROW = 24;
    private static final int BEST_ROW = 22;
    private static final int TOP_TITLE_ROW = 30;
    private static final int TOP_ROW = 20;

    private final int topLines;
    private String biome = "";
    private int score;
    private int best;
    private int rank;
    private List<String> top = List.of();

    ParkourHud(PlayerRef playerRef, int topLines) {
        super(playerRef, KEY);
        this.topLines = Math.max(0, topLines);
    }

    @Override
    protected void build(UICommandBuilder b) {
        b.appendInline(null, document(topLines));
        fill(b);
    }

    void setBiome(String name, String color) {
        this.biome = name;
    }

    void setScore(int score, int best, int rank) {
        this.score = score;
        this.best = best;
        this.rank = rank;
    }

    void setTop(List<String> lines) {
        this.top = List.copyOf(lines);
    }

    /** Sends the current values. */
    void push() {
        UICommandBuilder b = new UICommandBuilder();
        fill(b);
        update(false, b);
    }

    /** Height of the panel for a number of top lines (0 hides the top list). */
    static int height(int shownTopLines) {
        int h = 2 * PADDING + TITLE_ROW + BIOME_ROW + LINE_ROW + SCORE_ROW + BEST_ROW;
        if (shownTopLines > 0) {
            h += TOP_TITLE_ROW + shownTopLines * TOP_ROW;
        }
        return h;
    }

    private void fill(UICommandBuilder b) {
        int shown = Math.min(topLines, top.size());
        b.set("#EpBiome.Text", biome);
        b.set("#EpScore.Text", "Score: " + score);
        b.set("#EpBest.Text", rank > 0 ? "Best: " + best + "  (#" + rank + ")" : "Best: " + best);
        if (topLines > 0) {
            b.set("#EpTopTitle.Visible", shown > 0);
            for (int i = 0; i < topLines; i++) {
                boolean on = i < shown;
                b.set("#EpTop" + (i + 1) + ".Visible", on);
                b.set("#EpTop" + (i + 1) + ".Text", on ? top.get(i) : "");
            }
        }
        b.setObject("#EpPanel.Anchor", anchor(height(shown)));
    }

    private static Anchor anchor(int height) {
        Anchor a = new Anchor();
        a.setRight(Value.of(RIGHT));
        a.setTop(Value.of(TOP));
        a.setWidth(Value.of(WIDTH));
        a.setHeight(Value.of(height));
        return a;
    }

    /** The panel markup with the given number of top lines. */
    static String document(int topLines) {
        StringBuilder s = new StringBuilder();
        s.append("Group #EpPanel {\n");
        s.append("  Anchor: (Right: ").append(RIGHT).append(", Top: ").append(TOP)
                .append(", Width: ").append(WIDTH).append(", Height: ").append(height(0)).append(");\n");
        s.append("  LayoutMode: Top;\n");
        s.append("  Background: #000000(0.45);\n");
        s.append("  Padding: (Full: ").append(PADDING).append(");\n\n");
        label(s, "EpTitle", TITLE_ROW, 0, "FontSize: 18, TextColor: #ffcc55, RenderBold: true, Alignment: Center", Msg.NAME);
        label(s, "EpBiome", BIOME_ROW, 0, "FontSize: 15, TextColor: #b4c8dc, Alignment: Center", "");
        s.append("  Group {\n");
        s.append("    Anchor: (Height: 2, Vertical: 6);\n");
        s.append("    Background: #ffffff(0.18);\n");
        s.append("  }\n\n");
        label(s, "EpScore", SCORE_ROW, 0, "FontSize: 17, TextColor: #ffffff, RenderBold: true", "Score: 0");
        label(s, "EpBest", BEST_ROW, 0, "FontSize: 15, TextColor: #c8d2dc", "Best: 0");
        if (topLines > 0) {
            label(s, "EpTopTitle", TOP_TITLE_ROW - 6, 6, "FontSize: 14, TextColor: #ffcc55, RenderBold: true, RenderUppercase: true", "Top");
        }
        for (int i = 1; i <= topLines; i++) {
            label(s, "EpTop" + i, TOP_ROW, 0, "FontSize: 14, TextColor: #ffffff", "");
        }
        s.append("}\n");
        return s.toString();
    }

    private static void label(StringBuilder s, String id, int height, int top, String style, String text) {
        s.append("  Label #").append(id).append(" {\n");
        s.append("    Anchor: (Height: ").append(height);
        if (top > 0) {
            s.append(", Top: ").append(top);
        }
        s.append(");\n");
        s.append("    Style: (").append(style).append(");\n");
        s.append("    Text: \"").append(text.replace("\"", "")).append("\";\n");
        s.append("  }\n\n");
    }
}
