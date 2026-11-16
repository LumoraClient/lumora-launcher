import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lumora icon pack for the in-game hub: 16x16 pixel icons (dark outline, two or three shades per surface), packed as
 * 32x32 cells (each pixel 2x2) into one sprite sheet {@code assets/lumora/textures/gui/hub/icons.png} with its index
 * {@code icons.txt} (one name per line, cell = line number, 8 cells per row). The game draws a cell with
 * {@code dev.lumora.core.hub.HubIcons}; its name list must match {@code icons.txt} (checked by a test).
 * <p>
 * Icons added with {@code outline} only hold the colours: every empty pixel next to a coloured one gets the outline.
 * <p>
 * Run: {@code java packaging/HubIconGen.java lumora-core/src/main/resources [--sheet overview.png]}.
 */
public final class HubIconGen {
    static final int CELL = 32;
    static final int COLUMNS = 8;
    static final int SHEET = 256;

    private static final Map<Character, Integer> PALETTE = Map.ofEntries(
            Map.entry('K', 0xFF14141E),
            Map.entry('W', 0xFFF6F6FA), Map.entry('L', 0xFFD0D0DA), Map.entry('G', 0xFF9A9AAA),
            Map.entry('D', 0xFF5E5E6E), Map.entry('d', 0xFF34343F), Map.entry('n', 0xFF1A2350),
            Map.entry('b', 0xFF9CC8FF), Map.entry('B', 0xFF3B82F6), Map.entry('N', 0xFF1E40AF),
            Map.entry('r', 0xFFFCA5A5), Map.entry('R', 0xFFEF4444), Map.entry('M', 0xFF991B1B),
            Map.entry('y', 0xFFFEF08A), Map.entry('Y', 0xFFFACC15), Map.entry('O', 0xFFF59E0B), Map.entry('o', 0xFFB45309),
            Map.entry('e', 0xFF86EFAC), Map.entry('E', 0xFF22C55E), Map.entry('F', 0xFF15803D),
            Map.entry('c', 0xFFA5F3FC), Map.entry('C', 0xFF22D3EE), Map.entry('T', 0xFF0E7490),
            Map.entry('t', 0xFFD9A066), Map.entry('U', 0xFF9C6B3A), Map.entry('u', 0xFF5E3A1E),
            Map.entry('v', 0xFFC4B5FD), Map.entry('V', 0xFF8B5CF6), Map.entry('X', 0xFF5B21B6),
            Map.entry('s', 0xFFF2C29B), Map.entry('P', 0xFFEC4899));

    private record Icon(boolean outline, String[] rows) {
    }

    private static final Map<String, Icon> ICONS = new LinkedHashMap<>();

    private static void raw(String name, String... rows) {
        ICONS.put(name, new Icon(false, pad(rows)));
    }

    private static void outline(String name, String... rows) {
        ICONS.put(name, new Icon(true, pad(rows)));
    }

    /** Fills missing rows at the bottom. */
    private static String[] pad(String[] rows) {
        String[] out = new String[16];
        for (int i = 0; i < 16; i++) {
            out[i] = i < rows.length ? rows[i] : "................";
        }
        return out;
    }

    static {
        // ---- HUD widgets
        raw("fps",
                "................", ".KKKKKKKKKKKKKK.", ".KLLLLLLLLLLLLK.", ".KGnnnnnnnnnnGK.",
                ".KGnnnnnnnnnEGK.", ".KGnnnnnnnnEnGK.", ".KGnnnEnnnEnnGK.", ".KGnnEnEnEnnnGK.",
                ".KGnEnnnEnnnnGK.", ".KGnnnnnnnnnnGK.", ".KGGGGGGGGGGGGK.", ".KKKKKKKKKKKKKK.",
                "......KDDK......", "......KDDK......", "....KKGGGGKK....", "....KKKKKKKK....");
        raw("cps",
                "................", ".Y...KKKKKK.....", "Y.Y.KbBBKLLK....", ".Y.KbBBBKLLLK...",
                "...KBBBNKLLGK...", "...KKKKKKKKKK...", "...KWLLLLLLGK...", "...KLLLLLLLGK...",
                "...KLLLLLLLGK...", "...KLLLLLLGGK...", "...KGLLLLLGGK...", "....KGGGGGGK....",
                ".....KKKKKK.....");
        raw("coords",
                "................", ".....KKKKKK.....", "....KRRRRrrK....", "...KRRRRRRrrK...",
                "...KRRRKKRRrK...", "...KRRKWWKRRK...", "...KRRKWWKRRK...", "...KRRRKKRRRK...",
                "...KMRRRRRRMK...", "....KMRRRRMK....", ".....KMRRMK.....", "......KMMK......",
                "....DDDKKDDD....", "...DDDDDDDDDD...", "....DDDDDDDD....");
        raw("clock",
                "................", ".....KKKKKK.....", "...KKOYYYYOKK...", "..KOYWWWWWWYOK..",
                "..KYWWWWKWWWYK..", ".KOWWWWWKWWWWOK.", ".KYWWWWWKWWWWYK.", ".KYWWWWWKKKKWYK.",
                ".KYWWWWWWWWWWYK.", ".KOWWWWWWWWWLOK.", "..KYWWWWWWWLYK..", "..KOYLWWWWLYOK..",
                "...KKOYYYYOKK...", ".....KKKKKK.....");
        raw("ping",
                "................", "................", "............KKKK", "............KeEK",
                "............KeEK", "........KKKKKeEK", "........KeEKKeEK", "........KeEKKeEK",
                "....KKKKKeEKKeEK", "....KeEKKeEKKeEK", "....KeEKKeEKKeEK", "KKKKKeEKKeEKKeEK",
                "KeEKKeEKKeEKKFEK", "KFEKKFEKKFEKKFEK", "KKKKKKKKKKKKKKKK");
        raw("speed",
                "................", "................", "................", ".....KKKKKK.....",
                "...KKEEYYRRKK...", "..KEEddddddRRK..", ".KEddddddddWdRK.", ".KEdddddddWddRK.",
                "KEdddddddWddddRK", "KEddddddWdddddRK", "KddddddKKddddddK", "KddddddKKddddddK",
                ".KKKKKKKKKKKKKK.");
        raw("server",
                "................", "..KKKKKKKKKKKK..", "..KLLLLLLLLLGK..", "..KLDDDDDLELGK..",
                "..KGGGGGGGGGGK..", "..KKKKKKKKKKKK..", "..KLLLLLLLLLGK..", "..KLDDDDDLELGK..",
                "..KGGGGGGGGGGK..", "..KKKKKKKKKKKK..", "..KLLLLLLLLLGK..", "..KLDDDDDLYLGK..",
                "..KGGGGGGGGGGK..", "..KKKKKKKKKKKK..", ".....KK..KK.....");
        raw("keystrokes",
                "................", "................", ".....KKKKK......", ".....KbBBK......",
                ".....KBBBK......", ".....KBBNK......", ".....KNNNK......", ".....KKKKK......",
                "KKKKKKKKKKKKKKK.", "KWLLKKWLLKKWLLK.", "KLLLKKLLLKKLLLK.", "KLLGKKLLGKKLLGK.",
                "KGGGKKGGGKKGGGK.", "KKKKKKKKKKKKKKK.");
        raw("armor",
                "................", "..KKKK....KKKK..", ".KcCCK....KCCTK.", ".KcCCKKKKKKCCTK.",
                ".KcCCCCCCCCCCTK.", ".KKKcCCCCCCTKKK.", "...KcCCCCCCTK...", "...KcWCCCCCTK...",
                "...KcCCCCCCTK...", "...KcCCCCCCTK...", "...KcCCCCCCTK...", "...KcCCCCCTTK...",
                "...KTTTTTTTTK...", "...KKKKKKKKKK...");
        raw("blockinfo",
                "................", ".KKKKKKKKKKKKKK.", ".KeEeEEeEEEeEEK.", ".KEEEEFEEEEFEEK.",
                ".KFEFtFFEFFtFFK.", ".KtFtttFttUtFtK.", ".KtttUttttttUtK.", ".KttttttUtttttK.",
                ".KtUtttttttUttK.", ".KtttttUttttttK.", ".KttUtttttttUtK.", ".KUtttttUtttttK.",
                ".KuUUUUUUUUUUuK.", ".KKKKKKKKKKKKKK.");
        raw("potions",
                "......KKKK......", "......KttK..r...", "......KUUK......", "......KLLK...r..",
                ".....KLWLLK.....", "....KLWLLLLK....", "...KLWRRRRRLK...", "..KLWrRRRRRRLK..",
                "..KLrRRRRRRRLK..", "..KLRRRRRRRMLK..", "..KLRRRRRRMMLK..", "..KLMRRRRMMMLK..",
                "...KLMMMMMMLK...", "....KKKKKKKK....");
        raw("items",
                "................", "...........KKKK.", "..........KWLGK.", ".........KKLGGK.",
                "........KtKKGK..", ".......KtUK.KK..", "......KtUK......", ".....KtUK.......",
                "....KtUK........", "...KtUK.........", ".KKtUK..........", "KWrKK...........",
                "KrWrK...........", "KKrWK...........", ".KKKK...........");
        raw("compass",
                "................", ".....KKKKKK.....", "...KKOYYYYOKK...", "..KOYWWWWWWYOK..",
                "..KYWWWRRWWWYK..", ".KOWWWWRRWWWWOK.", ".KYWWWRRRRWWWYK.", ".KYWWWWKKWWWWYK.",
                ".KYWWWDDDDWWWYK.", ".KOWWWWDDWWWWOK.", "..KYWWWDDWWWYK..", "..KOYWWWWWWYOK..",
                "...KKOYYYYOKK...", ".....KKKKKK.....");
        raw("memory",
                "................", "................", "................", "KKKKKKKKKKKKKKKK",
                "KeEEEEEEEEEEEEFK", "KEdddEdddEdddEFK", "KEdGdEdGdEdGdEFK", "KEdddEdddEdddEFK",
                "KEEEEEEEEEEEEEFK", "KFFFFFFFFFFFFFFK", "KYYKYYKYYKYYKYYK", "KKKKKKKKKKKKKKKK");
        raw("day",
                "................", ".......YY.......", "..Y....YY....Y..", "...Y........Y...",
                ".....KKKKKK.....", "....KyyYYYYK....", "...KyYYYYYYYK...", "YY.KYYYYYYYOK.YY",
                "YY.KYYYYYYOOK.YY", "...KYYYYYOOOK...", "....KYOOOOOK....", ".....KKKKKK.....",
                "...Y........Y...", "..Y....YY....Y..", ".......YY.......");
        raw("packs",
                "................", "......KKKKKKKKKK", "......KvVVVVVVXK", "......KVVVVVVVXK",
                "......KVVVVVVVXK", "KKKKKKKKKKKVVVXK", "KbbbbbbbbbKVVVXK", "KbbbbbbYYbKVVVXK",
                "KbbbbbbYYbKXXXXK", "KbbbEbbbbbKKKKKK", "KbbEEEbbEbK.....", "KEEEEEEEEEK.....",
                "KFEEFEEEFEK.....", "KttttttttUK.....", "KKKKKKKKKKK.....");
        outline("combo",
                "................", "................", "..W..........W..", "...W........W...",
                "....L......L....", ".....L....L.....", "......L..L......", ".......LL.......",
                ".......LL.......", "......L..L......", "...Y.L....L.Y...", "....Y......Y....",
                "...t.Y....Y.t...", "..O..........O..");
        // ---- modules
        outline("togglesprint",
                "................", "................", ".......BBB......", ".......bBB......",
                ".......bBB......", "..LLL..bBB......", ".......bBBB.....", ".......bBBBBB...",
                "LLLL..bBBBBBBB..", "......BBBBBBBN..", "......WWWWWWWL..");
        outline("crosshair",
                "................", ".......WW.......", ".......WL.......", ".......WL.......",
                "................", "................", "................", ".WWW...RR...WWW.",
                ".LLL...RM...LLL.", "................", "................", "................",
                ".......WL.......", ".......WL.......", ".......LL.......");
        outline("attackindicator",
                "................", "................", ".............W..", "............W...",
                "...........L....", "..........L.....", ".........L......", "......Y.L.......",
                ".......Y........", "......t.Y.......", ".....O..........", "................",
                "................", "..EEEEEEEDDDDD..");
        raw("blockoverlay",
                "................", ".BBBB......BBBB.", ".B............B.", ".B.KKKKKKKKKK.B.",
                ".B.KLGGDGGLGK.B.", "...KGGLGGGDGK...", "...KGDGGLGGGK...", "...KGGGGGDGLK...",
                "...KLGDGGGGGK...", "...KGGGLGDGGK...", "...KGDGGGGLGK...", ".B.KGGGDGGGDK.B.",
                ".B.KKKKKKKKKK.B.", ".B............B.", ".BBBB......BBBB.");
        outline("connection",
                "................", ".....L....L.....", ".....L....L.....", "...LLLLLLLLLL...",
                "...LWLLLLLLLG...", "...LLLLLLLLLG...", "...GLLLLLLLGG...", "....GGGGGGGG....",
                "......DDDD......", ".......BB.......", ".......BB.......", "........BB......",
                ".........BB.....", ".........BB.....");
        raw("fullbright",
                "................", "..y...KKKK...y..", "....KKyyyYKK....", "...KyyWyYYYOK...",
                "..KyyWyYYYYYOK..", "y.KyWyYYYYYYOK.y", "..KyyYYYYYYYOK..", "..KYYYYYYYYOOK..",
                "...KYYYYYYOOK...", "....KOYYYOOK....", ".....KLLLLK.....", ".....KGGGGK.....",
                ".....KLLLLK.....", "......KGGK......", ".......KK.......");
        raw("chat",
                "................", "................", ".KKKKKKKKKKKKKK.", ".KWWWWWWWWWWWLK.",
                ".KWLLLLLLLLLLGK.", ".KWLLLLLLLLLLGK.", ".KWLBBLBBLBBLGK.", ".KWLNNLNNLNNLGK.",
                ".KWLLLLLLLLLLGK.", ".KLGGGGGGGGGGGK.", ".KKKKGGKKKKKKKK.", "....KGK.........",
                "....KK..........");
        raw("tab",
                "................", "KKKKKKKKKKKKKKKK", "KddddddddddddddK", "KduudLLLLLLLdEdK",
                "KdssdddddddddEdK", "KddddddddddddddK", "KdYYdLLLLLdddEdK", "KdssdddddddddEdK",
                "KddddddddddddddK", "KdttdLLLLLLddYdK", "KdssdddddddddYdK", "KddddddddddddddK",
                "KKKKKKKKKKKKKKKK");
        outline("elytratrails",
                "................", "................", "......vvvv......", ".....vVvvVv.....",
                "....vVVvvVVv....", "...vVVXvvXVVv...", "..vVVXXvvXXVVv..", "..vVXXX..XXXVv..",
                ".vVXXX....XXXVv.", ".vVXX......XXVv.", ".VXX........XXV.", ".XX..........XX.",
                "................", "..P..........C..", ".P............C.");
        outline("hotbar",
                "................", "................", "..rR.RR.rR.RR...", "..RRRRR.RRRRR...",
                "...RRR...RRR....", "....R.....R.....", "................", "................",
                "................", ".GGGGWWWWWGGGG..", ".GdddWdddWdddG..", ".GdddWdEdWdddG..",
                ".GdddWdddWdddG..", ".GGGGWWWWWGGGG..");
        raw("emojichat",
                "................", ".....KKKKKK.....", "...KKyyyyYYKK...", "..KyyyYYYYYYOK..",
                ".KyyYYYYYYYYYOK.", ".KyYYKKYYKKYYOK.", ".KYYYKKYYKKYYOK.", ".KYYYYYYYYYYYOK.",
                ".KYYKYYYYYYKYOK.", ".KYYYKKKKKKYOOK.", ".KOYYYYYYYYYOOK.", "..KOYYYYYYYOOK..",
                "...KKOOOOOOKK...", ".....KKKKKK.....");
        raw("freelook",
                "................", "................", "................", ".....KKKKKK.....",
                "...KKWWWWWWKK...", "..KWWWKKKKWWLK..", ".KWWWKbBBBKWWLK.", "KWWWKbBKKBBKWWLK",
                "KWWWKBBKKBNKWLLK", ".KWWKBBBBNNKLLK.", "..KWLKKKKKKLLK..", "...KKLLLLLLKK...",
                ".....KKKKKK.....");
        raw("lightoverlay",
                "................", "......KK........", "..y..KyyK..y....", "....KyYYOK......",
                ".y..KYYOOK.y....", "....KYOROK......", ".....KttK.......", ".....KtUK.......",
                ".....KtUK.......", ".....KtUK.KKKKKK", ".....KtUK.KRGGRK", ".....KtUK.KGRRGK",
                ".....KtUK.KGRRGK", ".....KKKK.KRGGRK", "..........KKKKKK");
        raw("offhand",
                "................", "..KKKKKKKKKKKK..", "..KLLLLLLLLLGK..", "..KLbBBBBBBNGK..",
                "..KLbBBWWBBNGK..", "..KLbBWWWWBNGK..", "..KLbBBWWBBNGK..", "..KLbBBWWBBNGK..",
                "..KLbBBBBBBNGK..", "...KLbBBBBNGK...", "...KLbBBBBNGK...", "....KLbBBNGK....",
                ".....KLNNGK.....", "......KGGK......", ".......KK.......");
        outline("zoom",
                "................", "................", ".....GGGG.......", "....GcCCCG......",
                "...GcWCCCCG.....", "...GcCCCCCG.....", "...GCCCCCTG.....", "...GCCCCTTG.....",
                "....GCCTTG......", ".....GGGG.tU....", "...........tU...", "............tU..",
                ".............tU.");
        raw("buttons",
                "................", "................", "................", "KKKKKKKKKKKKKKK.",
                "KbBBBBBBBBBBBNK.", "KBBWWWWWWWWBBNK.", "KBBBBBBBBKBBBNK.", "KNNNNNNNNKKNNNK.",
                "KKKKKKKKKKWKKKK.", ".........KWWK...", ".........KWWWK..", ".........KWWKKK.",
                ".........KWK....", ".........KK.....");
        raw("browser",
                "................", ".....KKKKKK.....", "...KKbbBBBBKK...", "..KbbEEBBBBBNK..",
                ".KbEEEEEBBEBNNK.", ".KbEEEEBBBEEBNK.", ".KBBEEEBBBEEENK.", ".KBBBEBBBBEEENK.",
                ".KBBBBBBBEEEBNK.", ".KBBBBBBBBEBBNK.", ".KNBBBEEBBBBNNK.", "..KNBBEEEBBNNK..",
                "...KKNNNNNNKK...", ".....KKKKKK.....");
        // ---- addons
        raw("voice",
                "................", "......KKKK......", ".....KLLLGK.....", ".....KLDDGK.....",
                ".....KLLLGK.....", ".....KLDDGK.....", ".....KLLLGK.....", ".....KLDDGK.....",
                "..KK.KGGGGK.KK..", "..KGK.KKKK.KGK..", "...KGKKKKKKGK...", "....KKKGGKKK....",
                "......KGGK......", "....KKKGGKKK....", "....KGGGGGGK....", "....KKKKKKKK....");
        outline("music",
                "................", "......eEEEEEE...", "......EEEEEEF...", "......E.....F...",
                "......E.....F...", "......E.....F...", "......E.....F...", "......E.....F...",
                "...eEEE..eEEF...", "..eEEEF.eEEEF...", "..EEEFF.EEEFF...", "...FFF...FFF....");
        // ---- engine
        outline("performance",
                "................", ".........YYY....", "........yYYO....", ".......yYYO.....",
                "......yYYO......", ".....yYYYYYYO...", "....yYYYYYYO....", "........YYO.....",
                ".......YYO......", "......YYO.......", ".....YO.........", "....O...........");
    }

    static BufferedImage render(String name, Icon icon) {
        int[][] px = new int[16][16];
        for (int y = 0; y < 16; y++) {
            String row = icon.rows()[y];
            if (row.length() != 16) {
                throw new IllegalStateException(name + ": row " + y + " has " + row.length() + " pixels");
            }
            for (int x = 0; x < 16; x++) {
                char c = row.charAt(x);
                if (c != '.') {
                    Integer argb = PALETTE.get(c);
                    if (argb == null) {
                        throw new IllegalStateException(name + ": unknown colour '" + c + "'");
                    }
                    px[y][x] = argb;
                }
            }
        }
        if (icon.outline()) {
            int k = PALETTE.get('K');
            int[][] out = new int[16][];
            for (int y = 0; y < 16; y++) {
                out[y] = px[y].clone();
            }
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    if (px[y][x] == 0 && (filled(px, x - 1, y, k) || filled(px, x + 1, y, k)
                            || filled(px, x, y - 1, k) || filled(px, x, y + 1, k))) {
                        out[y][x] = k;
                    }
                }
            }
            px = out;
        }
        BufferedImage img = new BufferedImage(CELL, CELL, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                for (int d = 0; d < 4; d++) {
                    img.setRGB(x * 2 + d % 2, y * 2 + d / 2, px[y][x]);
                }
            }
        }
        return img;
    }

    private static boolean filled(int[][] px, int x, int y, int outline) {
        return x >= 0 && y >= 0 && x < 16 && y < 16 && px[y][x] != 0 && px[y][x] != outline;
    }

    public static void main(String[] args) throws IOException {
        Path overview = null;
        List<Path> dirs = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--sheet")) {
                overview = Path.of(args[++i]);
            } else {
                dirs.add(Path.of(args[i]));
            }
        }
        if (ICONS.size() > (SHEET / CELL) * COLUMNS) {
            throw new IllegalStateException("too many icons for one sheet");
        }
        BufferedImage sheet = new BufferedImage(SHEET, SHEET, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D sg = sheet.createGraphics();
        List<String> names = new ArrayList<>(ICONS.keySet());
        for (int i = 0; i < names.size(); i++) {
            sg.drawImage(render(names.get(i), ICONS.get(names.get(i))), (i % COLUMNS) * CELL, (i / COLUMNS) * CELL, null);
        }
        sg.dispose();
        for (Path dir : dirs) {
            Path out = dir.resolve("assets/lumora/textures/gui/hub");
            Files.createDirectories(out);
            ImageIO.write(sheet, "png", out.resolve("icons.png").toFile());
            Files.writeString(out.resolve("icons.txt"), String.join("\n", names) + "\n", StandardCharsets.UTF_8);
        }
        if (overview != null) {
            int cols = 8;
            int cell = 88;
            BufferedImage s = new BufferedImage(cols * cell, ((names.size() + cols - 1) / cols) * cell, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = s.createGraphics();
            g.setColor(new java.awt.Color(0x1B2230));
            g.fillRect(0, 0, s.getWidth(), s.getHeight());
            for (int i = 0; i < names.size(); i++) {
                int x = (i % cols) * cell;
                int y = (i / cols) * cell;
                g.setColor(new java.awt.Color(0x262F40));
                g.fillRoundRect(x + 4, y + 4, cell - 8, cell - 8, 10, 10);
                g.drawImage(sheet, x + 12, y + 8, x + 76, y + 72, (i % COLUMNS) * CELL, (i / COLUMNS) * CELL,
                        (i % COLUMNS + 1) * CELL, (i / COLUMNS + 1) * CELL, null);
                g.setColor(java.awt.Color.LIGHT_GRAY);
                g.drawString(names.get(i), x + 8, y + 84);
            }
            g.dispose();
            ImageIO.write(s, "png", overview.toFile());
        }
        System.out.println(names.size() + " icons, " + dirs.size() + " directories");
    }
}
