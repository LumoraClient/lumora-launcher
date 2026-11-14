import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lumora hub module icons: 16x16 pixel art, written as 32x32 PNGs (each pixel 2x2) to
 * {@code <dir>/assets/lumora/textures/gui/hub/} of every given resources directory.
 * <p>
 * Run: {@code java packaging/HubIconGen.java client/mc26.3/src/main/resources [...]}. With {@code --sheet out.png}
 * it also writes an overview of all icons.
 */
public final class HubIconGen {
    private static final Map<Character, Integer> PALETTE = Map.ofEntries(
            Map.entry('K', 0xFF161D26), Map.entry('W', 0xFFF4F2FA), Map.entry('L', 0xFFC9C4D6),
            Map.entry('G', 0xFF848C99), Map.entry('D', 0xFF444C58),
            Map.entry('P', 0xFF3B82F6), Map.entry('p', 0xFFA0C8FF), Map.entry('V', 0xFF3B6BB0),
            Map.entry('B', 0xFF3B82F6), Map.entry('b', 0xFF93C5FD), Map.entry('N', 0xFF1E2A5A),
            Map.entry('R', 0xFFEF4444), Map.entry('r', 0xFFFCA5A5), Map.entry('M', 0xFF991B1B),
            Map.entry('Y', 0xFFFACC15), Map.entry('y', 0xFFFEF08A), Map.entry('O', 0xFFF59E0B),
            Map.entry('E', 0xFF22C55E), Map.entry('e', 0xFF86EFAC), Map.entry('F', 0xFF15803D),
            Map.entry('C', 0xFF22D3EE), Map.entry('c', 0xFF0E7490),
            Map.entry('T', 0xFF8B5A2B), Map.entry('t', 0xFFC68A4E));

    private static final Map<String, String[]> ICONS = new LinkedHashMap<>();

    static {
        ICONS.put("fps", new String[]{
                "................", ".KKKKKKKKKKKKKK.", ".KNNNNNNNNNNNNK.", ".KNNNNNNNNNNeNK.",
                ".KNNNNNNNNNeENK.", ".KNNNNNNNNeENNK.", ".KNeNNNNNeENNNK.", ".KNEeNNNeENNNNK.",
                ".KNNEeNeENNNNNK.", ".KNNNEeENNNNNNK.", ".KNNNNENNNNNNNK.", ".KKKKKKKKKKKKKK.",
                ".......KK.......", "......KGGK......", "....KKKKKKKK....", "................"});
        ICONS.put("cps", new String[]{
                "................", ".....KKKKKK.....", "....KppKKLLK....", "...KpPPKKLLLK...",
                "...KpPPKKLLLK...", "...KPPPKKLLLK...", "...KKKKKKKKKK...", "...KLLLLLLLLK...",
                "...KWLLLLLLLK...", "...KWLLLLLLLK...", "...KLLLLLLLLK...", "...KLLLLLLLGK...",
                "....KLLLLLLK....", ".....KKKKKK.....", "................", "................"});
        ICONS.put("coords", new String[]{
                "................", "......KKKK......", ".....KRRRRK.....", "....KRRrrRRK....",
                "....KRrWWrRK....", "....KRrWWrRK....", "....KRRrrRRK....", "....KRRRRRRK....",
                ".....KRRRRK.....", ".....KRRRRK.....", "......KRRK......", "......KRRK......",
                ".......KK.......", "....DDDDDDDD....", ".....DDDDDD.....", "................"});
        ICONS.put("clock", new String[]{
                "................", ".....KKKKKK.....", "...KKPPPPPPKK...", "..KPPWWWWWWPPK..",
                "..KPWWWWKWWWPK..", ".KPWWWWWKWWWWPK.", ".KPWWWWWKWWWWPK.", ".KPWWWWWKKKWWPK.",
                ".KPWWWWWWWWWWPK.", ".KPWWWWWWWWWWPK.", "..KPWWWWWWWWPK..", "..KPPWWWWWWPPK..",
                "...KKPPPPPPKK...", ".....KKKKKK.....", "................", "................"});
        ICONS.put("ping", new String[]{
                "................", "................", "............KKK.", "............KEK.",
                "............KEK.", "........KKK.KEK.", "........KEK.KEK.", "........KEK.KEK.",
                "....KKK.KEK.KEK.", "....KEK.KEK.KEK.", "....KEK.KEK.KEK.", "KKK.KEK.KEK.KEK.",
                "KEK.KEK.KEK.KEK.", "KEK.KEK.KEK.KEK.", "KKK.KKK.KKK.KKK.", "................"});
        ICONS.put("speed", new String[]{
                "................", "................", ".....KKKKKK.....", "...KKEEEYYOKK...",
                "..KEEDDDDDDOOK..", ".KEDDDDDDDDDDRK.", ".KEDDDDDDDDDWRK.", "KEDDDDDDDDDWDDRK",
                "KEDDDDDDDDWDDDRK", "KDDDDDDDKWDDDDDK", "KDDDDDDKWKDDDDDK", "KDDDDDDDKDDDDDDK",
                ".KKKKKKKKKKKKKK.", "................", "................", "................"});
        ICONS.put("server", new String[]{
                "................", "..KKKKKKKKKKKK..", "..KLLLLLLLLLLK..", "..KLDDDDDDELLK..",
                "..KLLLLLLLLLLK..", "..KKKKKKKKKKKK..", "..KLLLLLLLLLLK..", "..KLDDDDDDBLLK..",
                "..KLLLLLLLLLLK..", "..KKKKKKKKKKKK..", "..KLLLLLLLLLLK..", "..KLDDDDDDELLK..",
                "..KLLLLLLLLLLK..", "..KKKKKKKKKKKK..", "................", "................"});
        ICONS.put("keystrokes", new String[]{
                "................", ".....KKKKKK.....", ".....KWWWWK.....", ".....KWPPWK.....",
                ".....KWPPWK.....", ".....KLLLLK.....", ".....KKKKKK.....", "................",
                "KKKKKKKKKKKKKKKK", "KWWWWKWWWWKWWWWK", "KWPPWKWPPWKWPPWK", "KWPPWKWPPWKWPPWK",
                "KLLLLKLLLLKLLLLK", "KKKKKKKKKKKKKKKK", "................", "................"});
        ICONS.put("armor", new String[]{
                "................", "..KKKK....KKKK..", ".KCCCCKKKKCCCCK.", ".KCbCCCCCCCCbCK.",
                ".KCCCCCCCCCCCCK.", ".KKKCCCCCCCCKKK.", "...KCCCbCCCCK...", "...KCCCCCCCCK...",
                "...KCbCCCCCCK...", "...KCCCCCCbCK...", "...KCCCCCCCCK...", "...KcCCCCCCcK...",
                "...KKKKKKKKKK...", "................", "................", "................"});
        ICONS.put("blockinfo", new String[]{
                "................", "..KKKKKKKKKKKK..", "..KEEeEEEEeEEK..", "..KEeEEEEEEeEK..",
                "..KFEFFEFEFFEK..", "..KtFTTFTTFTtK..", "..KTTtTTTTtTTK..", "..KTTTTTtTTTTK..",
                "..KtTTTTTTTtTK..", "..KTTTtTTTTTTK..", "..KTTTTTTtTTTK..", "..KTtTTTTTTTtK..",
                "..KTTTTtTTTTTK..", "..KKKKKKKKKKKK..", "................", "................"});
        ICONS.put("potions", new String[]{
                "................", "......KKKK......", "......KTTK......", "......KKKK......",
                ".......KK.......", "......KWWK......", ".....KWWWWK.....", "....KWRRRRWK....",
                "...KRRrRRRRRK...", "...KRrRRRRRRK...", "...KRRRRRRRRK...", "...KRRRRRRMRK...",
                "....KRRRRMMK....", ".....KKKKKK.....", "................", "................"});
        ICONS.put("items", new String[]{
                "................", "...........KKKK.", "...........KWLK.", "..........KWLLK.",
                ".........KTKKK..", "........KTK.....", ".......KTK......", "......KTK.......",
                ".....KTK........", "....KTK.........", ".KKKTK..........", ".KrKK...........",
                ".KrrK...........", ".KKKrK..........", "....KK..........", "................"});
        ICONS.put("compass", new String[]{
                "................", ".....KKKKKK.....", "...KKGGGGGGKK...", "..KGGLLLLLLGGK..",
                "..KGLLLLRLLLGK..", ".KGLLLLRRLLLLGK.", ".KGLLLLRRLLLLGK.", ".KGLLLLKKLLLLGK.",
                ".KGLLLLWWLLLLGK.", ".KGLLLLWWLLLLGK.", "..KGLLLWLLLLGK..", "..KGGLLLLLLGGK..",
                "...KKGGGGGGKK...", ".....KKKKKK.....", "................", "................"});
        ICONS.put("memory", new String[]{
                "................", "................", "................", "................",
                ".KKKKKKKKKKKKKK.", ".KEEEEEEEEEEEEK.", ".KEDDEDDEDDEDDK.", ".KEDDEDDEDDEDDK.",
                ".KEDDEDDEDDEDDK.", ".KEEEEEEEEEEEEK.", ".KFFFFFFFFFFFFK.", ".KYKYKYKYKYKYKK.",
                ".KKKKKKKKKKKKKK.", "................", "................", "................"});
        ICONS.put("day", new String[]{
                "................", ".......YY.......", "..Y....YY....Y..", "...Y........Y...",
                ".....KKKKKK.....", "....KYYYYYYK....", "....KYyyYYYK....", "YY..KYyYYYYK..YY",
                "YY..KYYYYYYK..YY", "....KYYYYYOK....", "....KYYYYOOK....", ".....KKKKKK.....",
                "...Y........Y...", "..Y....YY....Y..", ".......YY.......", "................"});
        ICONS.put("packs", new String[]{
                "................", ".......KKKKKKKK.", ".......KBBBBBBK.", ".......KBbBBBBK.",
                "....KKKKKKKKBBK.", "....KPPPPPPKBBK.", "....KPpPPPPKBBK.", ".KKKKKKKKPPKKKK.",
                ".KEEEEEEKPPK....", ".KEeEEEEKPPK....", ".KEEEEEEKKKK....", ".KEEEEEEK.......",
                ".KEEEEEEK.......", ".KKKKKKKK.......", "................", "................"});
        ICONS.put("combo", new String[]{
                "................", "KKK..........KKK", "KWLK........KLWK", ".KWLK......KLWK.",
                "..KWLK....KLWK..", "...KWLK..KLWK...", "....KWLKKLWK....", ".....KWLLWK.....",
                "......KLLK......", ".....KWKKWK.....", "..KKKWK..KWKKK..", "..KOOK....KOOK..",
                "...KTK....KTK...", "..KTK......KTK..", "..KK........KK..", "................"});
        ICONS.put("togglesprint", new String[]{
                "................", "................", "........KKKKK...", "........KBBBK...",
                "........KBbBK...", ".GGGG...KBBBK...", "........KBBBKK..", "..GGGG..KBBBBBK.",
                "........KBBBBBBK", ".GGGG...KBBBBBBK", ".......KKBBBBBBK", ".......KWWWWWWWK",
                ".......KKKKKKKKK", "................", "................", "................"});
        ICONS.put("crosshair", new String[]{
                "................", ".......KK.......", "......KWWK......", "......KWWK......",
                "......KWWK......", ".......KK.......", ".KKK...RR...KKK.", "KWWWK.RRRR.KWWWK",
                "KWWWK.RRRR.KWWWK", ".KKK...RR...KKK.", ".......KK.......", "......KWWK......",
                "......KWWK......", "......KWWK......", ".......KK.......", "................"});
        ICONS.put("blockoverlay", new String[]{
                "pPPPPPPPPPPPPPPp", "PKKKKKKKKKKKKKKP", "PKLLGLLLLLGLLLKP", "PKLGGLLGLLLLLGKP",
                "PKLLLLLLLGGLLLKP", "PKGLLLGLLLLLLLKP", "PKLLLLGGLLLLGLKP", "PKLLLLLLLLLGGLKP",
                "PKLGLLLLLLLLLLKP", "PKLGGLLLGLLLLLKP", "PKLLLLLLGGLLLLKP", "PKLLLGLLLLLLGLKP",
                "PKLLLLLLLLLLLLKP", "PKKKKKKKKKKKKKKP", "pPPPPPPPPPPPPPPp", "................"});
        ICONS.put("buttons", new String[]{
                "................", "................", "..KKKKKKKKKKKK..", ".KppppppppppppK.",
                ".KPPPPPPPPPPPPK.", ".KPPWWWWWWWWPPK.", ".KPPPPPPPPPPPPK.", ".KVVVVVVVVVVVVK.",
                "..KKKKKKKKKKKK..", "..........KK....", "..........KWK...", "..........KWWK..",
                "..........KWWWK.", "..........KWWKK.", "..........KKWK..", "................"});
        ICONS.put("browser", new String[]{
                "................", ".....KKKKKK.....", "...KKBBEEBBKK...", "..KBBEEEEBBBBK..",
                "..KBEEEEBBBBBK..", ".KBBBEEEBBBEEBK.", ".KBBBBEBBBEEEBK.", ".KBBBBBBBEEEEBK.",
                ".KBBBBBBBBEEBBK.", ".KBEEBBBBBBBBBK.", "..KEEEBBBBBBBK..", "..KBEEBBBBBEBK..",
                "...KKBBBBBBKK...", ".....KKKKKK.....", "................", "................"});
        ICONS.put("connection", new String[]{
                "................", "....KK...KK.....", "....KLK..KLK....", "....KLK..KLK....",
                "..KKKKKKKKKKKK..", "..KGGGGGGGGGGK..", "..KGLLLLLLLLGK..", "..KGLLLLLLLLGK..",
                "...KGLLLLLLGK...", "....KGGGGGGK....", ".....KKGGKK.....", ".......KK.......",
                ".......KP.......", ".......KP.......", "......KPK.......", ".....KPK........"});
        ICONS.put("fullbright", new String[]{
                "................", ".....KKKKKK.....", "....KYYYYYYK....", "...KYyyYYYYYK...",
                "...KYyYYYYYYK...", "...KYYYYYYYYK...", "...KYYYYYYYOK...", "....KYYYYYOK....",
                ".....KYYYOK.....", "......KYYK......", ".....KLLLLK.....", ".....KGGGGK.....",
                ".....KLLLLK.....", "......KGGK......", ".......KK.......", "................"});
        ICONS.put("chat", new String[]{
                "................", "................", ".KKKKKKKKKKKKKK.", "KWWWWWWWWWWWWWWK",
                "KWWWWWWWWWWWWWWK", "KWWPPWWPPWWPPWWK", "KWWPPWWPPWWPPWWK", "KWWWWWWWWWWWWWWK",
                "KLWWWWWWWWWWWWLK", ".KKKKWWKKKKKKKK.", "....KWK.........", "....KK..........",
                "................", "................", "................", "................"});
        ICONS.put("tab", new String[]{
                "................", ".KKKKKKKKKKKKKK.", ".KPPPPPPPPPPPPK.", ".KKKKKKKKKKKKKK.",
                ".KLDDDDDDLLEEEK.", ".KLLLLLLLLLLLLK.", ".KLDDDDDLLLLEEK.", ".KLLLLLLLLLLLLK.",
                ".KLDDDDDDDLLLEK.", ".KLLLLLLLLLLLLK.", ".KLDDDDLLLLEEEK.", ".KLLLLLLLLLLLLK.",
                ".KKKKKKKKKKKKKK.", "................", "................", "................"});
        ICONS.put("zoom", new String[]{
                "................", "...KKKKK........", "..KbbbbbK.......", ".KbWbbbbbK......",
                ".KbWbbbbbK......", ".KbbbbbbbK......", ".KbbbbbbbK......", ".KbbbbbbbK......",
                "..KbbbbbK.......", "...KKKKKKK......", ".........KTK....", "..........KTK...",
                "...........KTK..", "............KTK.", ".............KK.", "................"});
        ICONS.put("addon", new String[]{
                "................", ".....KKKK.......", "....KPPPPK......", "....KPppPK......",
                ".KKKKPPPPKKKK...", ".KPPPPPPPPPPK...", ".KPpPPPPPPPPKKK.", ".KPPPPPPPPPPPPPK",
                ".KPPPPPPPPPPPPPK", ".KPPPPPPPPPPKKK.", ".KPPPPPPPPPPK...", ".KVPPPPPPPPVK...",
                ".KKKKKKKKKKKK...", "................", "................", "................"});
        ICONS.put("widget", new String[]{
                "................", "................", ".KKKKKKKKKKKKKK.", ".KPPPPPPPPPPPPK.",
                ".KKKKKKKKKKKKKK.", ".KDDDDDDDDDDDDK.", ".KDLLLLLLDDDDDK.", ".KDDDDDDDDDDDDK.",
                ".KDLLLLDDDDDDDK.", ".KDDDDDDDDDDDDK.", ".KDLLLLLLLLDDDK.", ".KDDDDDDDDDDDDK.",
                ".KKKKKKKKKKKKKK.", "................", "................", "................"});
    }

    private HubIconGen() {
    }

    static BufferedImage render(String name, String[] rows) {
        if (rows.length != 16) {
            throw new IllegalStateException(name + ": " + rows.length + " rows");
        }
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            if (rows[y].length() != 16) {
                throw new IllegalStateException(name + " row " + y + ": \"" + rows[y] + "\" has " + rows[y].length());
            }
            for (int x = 0; x < 16; x++) {
                char c = rows[y].charAt(x);
                if (c == '.') {
                    continue;
                }
                Integer argb = PALETTE.get(c);
                if (argb == null) {
                    throw new IllegalStateException(name + ": unknown colour '" + c + "'");
                }
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        img.setRGB(x * 2 + dx, y * 2 + dy, argb);
                    }
                }
            }
        }
        return img;
    }

    public static void main(String[] args) throws IOException {
        Path sheet = null;
        java.util.List<Path> dirs = new java.util.ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--sheet")) {
                sheet = Path.of(args[++i]);
            } else {
                dirs.add(Path.of(args[i]));
            }
        }
        Map<String, BufferedImage> images = new LinkedHashMap<>();
        ICONS.forEach((name, rows) -> images.put(name, render(name, rows)));
        for (Path dir : dirs) {
            Path out = dir.resolve("assets/lumora/textures/gui/hub");
            Files.createDirectories(out);
            for (Map.Entry<String, BufferedImage> e : images.entrySet()) {
                ImageIO.write(e.getValue(), "png", out.resolve(e.getKey() + ".png").toFile());
            }
        }
        if (sheet != null) {
            int cols = 8;
            int rows = (images.size() + cols - 1) / cols;
            int cell = 80;
            BufferedImage s = new BufferedImage(cols * cell, rows * cell, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = s.createGraphics();
            g.setColor(new java.awt.Color(0x192028));
            g.fillRect(0, 0, s.getWidth(), s.getHeight());
            int i = 0;
            for (Map.Entry<String, BufferedImage> e : images.entrySet()) {
                int x = (i % cols) * cell;
                int y = (i / cols) * cell;
                g.drawImage(e.getValue(), x + 8, y + 4, 64, 64, null);
                g.setColor(java.awt.Color.LIGHT_GRAY);
                g.drawString(e.getKey(), x + 6, y + 78);
                i++;
            }
            g.dispose();
            ImageIO.write(s, "png", sheet.toFile());
        }
        System.out.println(images.size() + " icons, " + dirs.size() + " directories");
    }
}
