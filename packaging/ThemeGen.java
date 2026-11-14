import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates the "Lumora Theme" resource pack (own pixel art in the launcher's colors, no Mojang textures):
 *   java packaging/ThemeGen.java client/mc26.1.2/src/main/resources/resourcepacks/lumora_theme
 * Sprite sizes and nine-slice borders match the vanilla 26.1.2 GUI so every vanilla and modded screen keeps
 * its layout - only the look changes.
 */
public final class ThemeGen {
    // launcher design tokens (dev.lumora.launcher.app.ui.Theme)
    static final int BG = 0xFF070B14;
    static final int SURFACE = 0xFF0D1424;
    static final int SURFACE_2 = 0xFF16213A;
    static final int SURFACE_3 = 0xFF1C2A47;
    static final int BORDER = 0xFF22314F;
    static final int BORDER_DIM = 0xFF182540;
    static final int ACCENT = 0xFF3B82F6;
    static final int ACCENT_HOVER = 0xFF5E9BF8;
    static final int ACCENT_DARK = 0xFF306ACA;
    static final int SHADOW = 0xFF04070E;
    static final int WHITE = 0xFFFFFFFF;
    static final int CLEAR = 0x00000000;

    static Path root;

    public static void main(String[] args) throws IOException {
        root = Path.of(args.length > 0 ? args[0] : "client/mc26.1.2/src/main/resources/resourcepacks/lumora_theme");
        Files.createDirectories(root);
        Files.writeString(root.resolve("pack.mcmeta"), """
                {
                  "pack": {
                    "description": "Lumora Theme - Minecraft-Oberfläche im Lumora-Design",
                    "min_format": 84,
                    "max_format": 84
                  }
                }
                """, StandardCharsets.UTF_8);
        copyIcon();

        String w = "assets/minecraft/textures/gui/sprites/widget/";
        // buttons 200x20, nine-slice border 3
        sprite(w + "button", card(200, 20, SURFACE_2, BORDER, SHADOW), nine(200, 20, "3"));
        sprite(w + "button_highlighted", card(200, 20, SURFACE_3, ACCENT, ACCENT_DARK), nine(200, 20, "3"));
        sprite(w + "button_disabled", card(200, 20, SURFACE, BORDER_DIM, SURFACE), nine(200, 20, "3"));
        // slider track + handle
        sprite(w + "slider", card(200, 20, SURFACE, BORDER, SURFACE), nine(200, 20, "1"));
        sprite(w + "slider_highlighted", card(200, 20, SURFACE, ACCENT, SURFACE), nine(200, 20, "1"));
        sprite(w + "slider_handle", card(8, 20, ACCENT, ACCENT_DARK, ACCENT_DARK), nine(8, 20, "{\"left\":2,\"top\":2,\"right\":2,\"bottom\":3}"));
        sprite(w + "slider_handle_highlighted", card(8, 20, ACCENT_HOVER, ACCENT, ACCENT_DARK), nine(8, 20, "{\"left\":2,\"top\":2,\"right\":2,\"bottom\":3}"));
        // text fields
        sprite(w + "text_field", card(200, 20, BG, BORDER, BG), nine(200, 20, "1"));
        sprite(w + "text_field_highlighted", card(200, 20, BG, ACCENT, BG), nine(200, 20, "1"));
        // scrollbar
        sprite(w + "scroller", card(6, 32, 0xFF333E52, 0xFF333E52, 0xFF333E52), nine(6, 32, "1"));
        sprite(w + "scroller_background", card(6, 32, SURFACE, SURFACE, SURFACE), nine(6, 32, "1"));
        // tabs 130x24 (open at the bottom)
        sprite(w + "tab", tab(SURFACE, BORDER, false), nine(130, 24, "{\"left\":2,\"top\":2,\"right\":2,\"bottom\":0}"));
        sprite(w + "tab_highlighted", tab(SURFACE_2, BORDER, false), nine(130, 24, "{\"left\":2,\"top\":2,\"right\":2,\"bottom\":0}"));
        sprite(w + "tab_selected", tab(SURFACE_2, BORDER, true), nine(130, 24, "{\"left\":2,\"top\":2,\"right\":2,\"bottom\":0}"));
        sprite(w + "tab_selected_highlighted", tab(SURFACE_3, ACCENT, true), nine(130, 24, "{\"left\":2,\"top\":2,\"right\":2,\"bottom\":0}"));
        // checkboxes 20x20 (drawn 1:1)
        sprite(w + "checkbox", checkbox(false, false), null);
        sprite(w + "checkbox_highlighted", checkbox(false, true), null);
        sprite(w + "checkbox_selected", checkbox(true, false), null);
        sprite(w + "checkbox_selected_highlighted", checkbox(true, true), null);
        // popup + system toast
        sprite("assets/minecraft/textures/gui/sprites/popup/background", card(236, 34, SURFACE, BORDER, SHADOW), nine(236, 34, "6"));
        sprite("assets/minecraft/textures/gui/sprites/toast/system", toast(), nine(160, 64,
                "{\"left\":17,\"top\":30,\"right\":4,\"bottom\":4}"));

        // menu backgrounds (tiled 16x16) and separators
        String g = "assets/minecraft/textures/gui/";
        sprite(g + "menu_background", solid(16, 16, 0x8C070B14), null);
        sprite(g + "menu_list_background", solid(16, 16, 0xA00D1424), null);
        sprite(g + "inworld_menu_background", solid(16, 16, 0x8C070B14), null);
        sprite(g + "inworld_menu_list_background", solid(16, 16, 0xA00D1424), null);
        sprite(g + "tab_header_background", solid(16, 16, 0xE60D1424), null);
        sprite(g + "header_separator", separator(true), null);
        sprite(g + "footer_separator", separator(false), null);
        sprite(g + "inworld_header_separator", separator(true), null);
        sprite(g + "inworld_footer_separator", separator(false), null);
        System.out.println("Theme written to " + root.toAbsolutePath());
    }

    /** Card with 1px border, rounded corners (corner pixels transparent) and a 1px bottom shade. */
    static BufferedImage card(int w, int h, int fill, int border, int shade) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean edge = x == 0 || y == 0 || x == w - 1 || y == h - 1;
                boolean corner = (x == 0 || x == w - 1) && (y == 0 || y == h - 1);
                int c = corner ? CLEAR : edge ? border : (y == h - 2 && h > 4 ? shade : fill);
                img.setRGB(x, y, c);
            }
        }
        return img;
    }

    static BufferedImage tab(int fill, int border, boolean selected) {
        BufferedImage img = new BufferedImage(130, 24, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 130; x++) {
                boolean corner = (x == 0 || x == 129) && y == 0;
                boolean edge = x == 0 || x == 129 || y == 0;
                int c = corner ? CLEAR : edge ? border : fill;
                if (selected && y >= 1 && y <= 2 && x > 0 && x < 129) {
                    c = ACCENT;
                }
                img.setRGB(x, y, c);
            }
        }
        return img;
    }

    static BufferedImage checkbox(boolean selected, boolean highlighted) {
        BufferedImage img = card(20, 20, selected ? (highlighted ? ACCENT_HOVER : ACCENT) : SURFACE,
                highlighted ? ACCENT_HOVER : (selected ? ACCENT_DARK : BORDER), selected ? ACCENT_DARK : SURFACE);
        if (selected) {
            int[][] check = {{5, 10}, {6, 11}, {7, 12}, {8, 13}, {9, 12}, {10, 11}, {11, 10}, {12, 9}, {13, 8}, {14, 7}};
            for (int[] p : check) {
                img.setRGB(p[0], p[1], WHITE);
                img.setRGB(p[0], p[1] - 1, WHITE);
            }
        }
        return img;
    }

    static BufferedImage toast() {
        BufferedImage img = card(160, 64, SURFACE, BORDER, SURFACE);
        for (int y = 3; y < 61; y++) {
            img.setRGB(2, y, ACCENT);
            img.setRGB(3, y, ACCENT);
        }
        return img;
    }

    static BufferedImage solid(int w, int h, int argb) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, argb);
            }
        }
        return img;
    }

    static BufferedImage separator(boolean header) {
        BufferedImage img = new BufferedImage(32, 2, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 32; x++) {
            img.setRGB(x, header ? 0 : 1, BORDER);
            img.setRGB(x, header ? 1 : 0, 0x6604070E);
        }
        return img;
    }

    static String nine(int w, int h, String border) {
        return "{\n  \"gui\": {\n    \"scaling\": {\n      \"type\": \"nine_slice\",\n      \"width\": " + w
                + ",\n      \"height\": " + h + ",\n      \"border\": " + border + "\n    }\n  }\n}\n";
    }

    static void sprite(String path, BufferedImage img, String mcmeta) throws IOException {
        Path png = root.resolve(path + ".png");
        Files.createDirectories(png.getParent());
        ImageIO.write(img, "png", png.toFile());
        if (mcmeta != null) {
            Files.writeString(root.resolve(path + ".png.mcmeta"), mcmeta, StandardCharsets.UTF_8);
        }
    }

    static void copyIcon() throws IOException {
        Path icon = Path.of("packaging/icons/icon-64.png");
        if (Files.exists(icon)) {
            Files.copy(icon, root.resolve("pack.png"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
