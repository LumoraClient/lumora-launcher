package dev.lumora.core.hub;

import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.render.TextureRef;

import java.util.List;
import java.util.Map;

/**
 * Lumora's icon pack for the hub: one sprite sheet ({@code textures/gui/hub/icons.png}, 32x32 cells, 8 per row) made by
 * {@code packaging/HubIconGen.java}. A name without a cell (an addon or widget by another author) is drawn with the
 * Lumora logo.
 */
final class HubIcons {
    static final TextureRef SHEET = new TextureRef("lumora", "textures/gui/hub/icons.png");
    static final int CELL = 32;
    static final int COLUMNS = 8;
    static final int SHEET_SIZE = 256;
    /** Name for the Lumora logo (no cell in the sheet). */
    static final String LOGO = "lumora";

    /** Cells of the sheet in order; must match {@code icons.txt} written by the generator. */
    static final List<String> NAMES = List.of(
            "fps", "cps", "coords", "clock", "ping", "speed", "server", "keystrokes",
            "armor", "blockinfo", "potions", "items", "compass", "memory", "day", "packs",
            "combo", "togglesprint", "crosshair", "attackindicator", "blockoverlay", "connection", "fullbright", "chat",
            "tab", "elytratrails", "hotbar", "emojichat", "freelook", "lightoverlay", "offhand", "zoom",
            "buttons", "browser", "voice", "music", "performance", "hiddenarmor");

    /** Icons with their own image instead of a cell ({@link #IMAGE_SIZE} square, drawn smaller). */
    static final Map<String, TextureRef> IMAGES = Map.of(
            "baublase", new TextureRef("lumora", "textures/gui/hub/addons/baublase.png"));
    static final int IMAGE_SIZE = 128;

    /** Icons of known addons by addon id. */
    private static final Map<String, String> ADDONS = Map.of(
            "lumoravoice", "voice",
            "spotify", "music",
            "baublase", "baublase");

    private HubIcons() {
    }

    /** The icon of an addon by its id; the Lumora logo if the pack has none for it. */
    static String forAddon(String addonId) {
        return ADDONS.getOrDefault(addonId, LOGO);
    }

    static boolean has(String name) {
        return NAMES.contains(name) || IMAGES.containsKey(name);
    }

    /** Draws the icon in a square of the given size; unknown names get the Lumora logo. */
    static void draw(HudGraphics g, String name, int x, int y, int size) {
        TextureRef image = IMAGES.get(name);
        if (image != null) {
            g.texture(image, x, y, size, size, IMAGE_SIZE, IMAGE_SIZE);
            return;
        }
        int i = NAMES.indexOf(name);
        if (i < 0) {
            g.texture(HubView.LOGO, x, y, size, size, 128, 128);
            return;
        }
        g.texture(SHEET, x, y, size, size, (i % COLUMNS) * CELL, (i / COLUMNS) * CELL, CELL, CELL, SHEET_SIZE, SHEET_SIZE);
    }
}
