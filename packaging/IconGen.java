import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates the Lumora icon set deterministically (no external assets, no third-party artwork):
 *   java packaging/IconGen.java <outDir>
 * Writes icon-<size>.png and lumora.ico (PNG-compressed ICO entries, supported since Windows Vista).
 */
public final class IconGen {
    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "packaging/icons");
        Files.createDirectories(out);
        int[] sizes = {16, 32, 48, 64, 128, 256};
        List<byte[]> pngs = new ArrayList<>();
        for (int s : sizes) {
            byte[] png = png(render(s));
            pngs.add(png);
            Files.write(out.resolve("icon-" + s + ".png"), png);
        }
        Files.write(out.resolve("lumora.ico"), ico(sizes, pngs));
        if (args.length > 1) {
            // optional: picture for the launcher news about the logo
            Files.write(Path.of(args[1]), png(banner()));
        }
    }

    /**
     * Lumora logo: rounded dark tile with an aurora glow and a yin-yang in the Lumora colours. The light half carries
     * the teal-to-violet gradient (day), the dark half a deep night violet; the light "eye" in the dark half is the
     * four-pointed Lumora star, the dark eye sits in the light half. A gradient ring holds both, the symbol is turned
     * a little for movement. No text, so it stays readable down to 16 px.
     */
    static BufferedImage render(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        float s = size;
        float pad = s * 0.03f;
        java.awt.geom.RoundRectangle2D tile = new java.awt.geom.RoundRectangle2D.Float(pad, pad, s - 2 * pad, s - 2 * pad, s * 0.42f, s * 0.42f);
        g.setPaint(new GradientPaint(0, 0, new Color(0x16, 0x1B, 0x26), s, s, new Color(0x0B, 0x0D, 0x12)));
        g.fill(tile);
        // aurora glow inside the tile
        g.setClip(tile);
        java.awt.RadialGradientPaint aurora = new java.awt.RadialGradientPaint(new java.awt.geom.Point2D.Float(s * 0.32f, s * 0.30f), s * 0.75f,
                new float[]{0f, 0.55f, 1f}, new Color[]{new Color(0x18, 0xB4, 0xCC, 120), new Color(0x8B, 0x7C, 0xF6, 40), new Color(0, 0, 0, 0)});
        g.setPaint(aurora);
        g.fillRect(0, 0, size, size);
        g.setClip(null);

        float cx = s * 0.5f;
        float cy = s * 0.5f;
        float big = s * (size <= 16 ? 0.37f : 0.33f); // radius of the symbol
        float half = big / 2;
        // light half: left semicircle + lower small circle - upper small circle (classic yin-yang construction)
        java.awt.geom.Area disk = new java.awt.geom.Area(new Ellipse2D.Float(cx - big, cy - big, 2 * big, 2 * big));
        java.awt.geom.Area light = new java.awt.geom.Area(new java.awt.geom.Rectangle2D.Float(cx - big, cy - big, big, 2 * big));
        light.intersect(disk);
        light.add(new java.awt.geom.Area(new Ellipse2D.Float(cx - half, cy, big, big)));
        light.subtract(new java.awt.geom.Area(new Ellipse2D.Float(cx - half, cy - big, big, big)));
        java.awt.geom.Area dark = new java.awt.geom.Area(disk);
        dark.subtract(light);
        java.awt.geom.AffineTransform turn = java.awt.geom.AffineTransform.getRotateInstance(Math.toRadians(-28), cx, cy);
        light.transform(turn);
        dark.transform(turn);
        // eye positions after the turn
        java.awt.geom.Point2D eyeLight = turn.transform(new java.awt.geom.Point2D.Float(cx, cy + half), null); // dark eye in the light half
        java.awt.geom.Point2D eyeDark = turn.transform(new java.awt.geom.Point2D.Float(cx, cy - half), null);  // star in the dark half

        // soft glow behind the symbol
        if (size >= 32) {
            java.awt.RadialGradientPaint glow = new java.awt.RadialGradientPaint(new java.awt.geom.Point2D.Float(cx, cy), big * 1.35f,
                    new float[]{0.6f, 1f}, new Color[]{new Color(0x6E, 0xE6, 0xF7, 55), new Color(0x8B, 0x7C, 0xF6, 0)});
            g.setPaint(glow);
            g.fill(new Ellipse2D.Float(cx - big * 1.4f, cy - big * 1.4f, big * 2.8f, big * 2.8f));
        }
        GradientPaint day = new GradientPaint(cx - big, cy - big, new Color(0x6E, 0xE6, 0xF7), cx + big, cy + big, new Color(0x8B, 0x7C, 0xF6));
        g.setPaint(new GradientPaint(cx - big, cy - big, new Color(0x2A, 0x1F, 0x5C), cx + big, cy + big, new Color(0x0E, 0x10, 0x1C)));
        g.fill(dark);
        g.setPaint(day);
        g.fill(light);
        // dark eye in the light half
        float eye = big * (size <= 16 ? 0.34f : 0.26f);
        g.setColor(new Color(0x14, 0x12, 0x2A));
        g.fill(new Ellipse2D.Double(eyeLight.getX() - eye, eyeLight.getY() - eye, 2 * eye, 2 * eye));
        // ring and light edge
        g.setStroke(new BasicStroke(Math.max(1f, s * (size <= 16 ? 0.06f : 0.028f))));
        g.setPaint(day);
        g.draw(new Ellipse2D.Float(cx - big, cy - big, 2 * big, 2 * big));
        if (size >= 48) {
            g.setStroke(new BasicStroke(Math.max(1f, s * 0.010f)));
            g.setColor(new Color(255, 255, 255, 70));
            g.draw(light);
        }
        // the Lumora star as the light eye in the dark half
        float sx = (float) eyeDark.getX();
        float sy = (float) eyeDark.getY();
        float r = big * (size <= 16 ? 0.48f : 0.42f);
        float w = r * 0.28f;
        java.awt.geom.Path2D star = new java.awt.geom.Path2D.Float();
        star.moveTo(sx, sy - r);
        star.quadTo(sx + w * 0.4f, sy - w * 0.4f, sx + r, sy);
        star.quadTo(sx + w * 0.4f, sy + w * 0.4f, sx, sy + r);
        star.quadTo(sx - w * 0.4f, sy + w * 0.4f, sx - r, sy);
        star.quadTo(sx - w * 0.4f, sy - w * 0.4f, sx, sy - r);
        star.closePath();
        g.setColor(Color.WHITE);
        g.fill(star);
        // thin tile outline
        g.setStroke(new BasicStroke(Math.max(1f, s * 0.015f)));
        g.setColor(new Color(255, 255, 255, 28));
        g.draw(tile);
        g.dispose();
        return img;
    }

    /** News picture for the logo change (1280x540): aurora night sky with the logo, room for the title bottom left. */
    static BufferedImage banner() {
        int w = 1280;
        int h = 540;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setPaint(new GradientPaint(0, 0, new Color(0x10, 0x16, 0x24), w, h, new Color(0x08, 0x09, 0x0E)));
        g.fillRect(0, 0, w, h);
        java.util.Random rnd = new java.util.Random(42);
        for (int i = 0; i < 160; i++) {
            int a = 60 + rnd.nextInt(160);
            g.setColor(new Color(255, 255, 255, a));
            float d = 1 + rnd.nextFloat() * 2;
            g.fill(new Ellipse2D.Float(rnd.nextInt(w), rnd.nextInt(h), d, d));
        }
        java.awt.RadialGradientPaint aurora = new java.awt.RadialGradientPaint(new java.awt.geom.Point2D.Float(w * 0.72f, h * 0.38f), w * 0.5f,
                new float[]{0f, 0.5f, 1f}, new Color[]{new Color(0x18, 0xB4, 0xCC, 110), new Color(0x8B, 0x7C, 0xF6, 50), new Color(0, 0, 0, 0)});
        g.setPaint(aurora);
        g.fillRect(0, 0, w, h);
        BufferedImage logo = render(300);
        g.drawImage(logo, (int) (w * 0.72f) - 150, (int) (h * 0.40f) - 150, null);
        g.dispose();
        return img;
    }

    static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    static byte[] ico(int[] sizes, List<byte[]> pngs) {
        int headerSize = 6 + 16 * sizes.length;
        int total = headerSize + pngs.stream().mapToInt(p -> p.length).sum();
        ByteBuffer b = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        int offset = headerSize;
        for (int i = 0; i < sizes.length; i++) {
            int s = sizes[i];
            b.put((byte) (s >= 256 ? 0 : s)).put((byte) (s >= 256 ? 0 : s)).put((byte) 0).put((byte) 0);
            b.putShort((short) 1).putShort((short) 32).putInt(pngs.get(i).length).putInt(offset);
            offset += pngs.get(i).length;
        }
        for (byte[] p : pngs) {
            b.put(p);
        }
        return b.array();
    }
}
