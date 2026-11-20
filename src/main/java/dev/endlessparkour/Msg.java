package dev.endlessparkour;

import com.hypixel.hytale.server.core.Message;

import java.util.ArrayList;
import java.util.List;

/** Chat lines in one style: "«Duck» Parkour » text", values in yellow, errors in red. */
final class Msg {

    static final String GOLD = "#FFAA00";
    static final String DARK = "#555555";
    static final String GRAY = "#AAAAAA";
    static final String WHITE = "#FFFFFF";
    static final String YELLOW = "#FFFF55";
    static final String RED = "#FF5555";
    static final String GREEN = "#55FF55";

    private final List<Message> parts = new ArrayList<>();

    private Msg() {
    }

    /** Name of the parkour as players see it (chat label, side panel, world). */
    static final String NAME = "«Duck» Parkour";

    /** A line with the name label in front. */
    static Msg line() {
        Msg m = new Msg();
        m.parts.add(Message.raw(NAME).color(GOLD).bold(true));
        m.parts.add(Message.raw(" » ").color(DARK));
        return m;
    }

    /** A line without a label (for lists). */
    static Msg plain() {
        return new Msg();
    }

    Msg text(String s) {
        parts.add(Message.raw(s).color(WHITE));
        return this;
    }

    Msg value(Object v) {
        parts.add(Message.raw(String.valueOf(v)).color(YELLOW));
        return this;
    }

    Msg gray(String s) {
        parts.add(Message.raw(s).color(GRAY));
        return this;
    }

    Msg good(String s) {
        parts.add(Message.raw(s).color(GREEN));
        return this;
    }

    Msg bad(String s) {
        parts.add(Message.raw(s).color(RED));
        return this;
    }

    Msg colored(String s, String color) {
        parts.add(Message.raw(s).color(color));
        return this;
    }

    Message build() {
        return Message.join(parts.toArray(new Message[0]));
    }

    static Message error(String text) {
        return line().bad(text).build();
    }

    static Message info(String text) {
        return line().text(text).build();
    }
}
