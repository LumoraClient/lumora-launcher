package dev.lumora.core.hub;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HubIconsTest {
    private static final String DIR = "/assets/lumora/textures/gui/hub/";

    @Test
    void namesMatchTheIndexWrittenWithTheSheet() throws IOException {
        try (InputStream in = HubIconsTest.class.getResourceAsStream(DIR + "icons.txt")) {
            assertNotNull(in, "icons.txt missing: run packaging/HubIconGen.java");
            List<String> index = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().filter(l -> !l.isBlank()).toList();
            assertEquals(index, HubIcons.NAMES);
        }
    }

    @Test
    void everyNamedCellHasPixels() throws IOException {
        BufferedImage sheet;
        try (InputStream in = HubIconsTest.class.getResourceAsStream(DIR + "icons.png")) {
            assertNotNull(in, "icons.png missing: run packaging/HubIconGen.java");
            sheet = ImageIO.read(in);
        }
        assertEquals(HubIcons.SHEET_SIZE, sheet.getWidth());
        assertEquals(HubIcons.SHEET_SIZE, sheet.getHeight());
        for (int i = 0; i < HubIcons.NAMES.size(); i++) {
            int cx = (i % HubIcons.COLUMNS) * HubIcons.CELL;
            int cy = (i / HubIcons.COLUMNS) * HubIcons.CELL;
            int opaque = 0;
            for (int y = 0; y < HubIcons.CELL; y++) {
                for (int x = 0; x < HubIcons.CELL; x++) {
                    if ((sheet.getRGB(cx + x, cy + y) >>> 24) != 0) {
                        opaque++;
                    }
                }
            }
            assertTrue(opaque > 64, HubIcons.NAMES.get(i) + " is empty");
        }
    }

    @Test
    void builtInModulesAndWidgetsHaveTheirOwnIcon() {
        for (String[] module : HubView.BUILT_IN.values()) {
            assertTrue(HubIcons.has(module[1]), module[1]);
        }
        for (String widget : HubView.WIDGET_ICONS) {
            assertTrue(HubIcons.has(widget), widget);
        }
        assertTrue(HubIcons.has("zoom"));
    }

    @Test
    void knownAddonsHaveAnIconAndOthersTheLogo() {
        assertEquals("voice", HubIcons.forAddon("lumoravoice"));
        assertEquals("music", HubIcons.forAddon("spotify"));
        assertEquals("baublase", HubIcons.forAddon("baublase"));
        assertEquals(HubIcons.LOGO, HubIcons.forAddon("someone-elses-addon"));
        assertTrue(!HubIcons.has(HubIcons.LOGO));
    }
}
