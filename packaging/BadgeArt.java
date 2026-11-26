import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Generates the Lumora badge glyph (16x16): a four-pointed star like in the logo, cyan to violet, dark outline.
 * <pre>java packaging/BadgeArt.java lumora-core/src/main/resources/assets/lumora/textures/font/badge.png</pre>
 */
public class BadgeArt {
    public static void main(String[] args) throws Exception {
        int n = 16;
        int ss = 8; // supersampling
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        double c = n / 2.0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int fill = 0, edge = 0;
                for (int sy = 0; sy < ss; sy++) {
                    for (int sx = 0; sx < ss; sx++) {
                        double px = x + (sx + 0.5) / ss - c, py = y + (sy + 0.5) / ss - c;
                        double d = star(px, py, 7.9);
                        if (d <= 0) {
                            if (star(px, py, 6.6) <= 0) fill++; else edge++;
                        }
                    }
                }
                int total = ss * ss;
                if (fill + edge == 0) continue;
                double a = (fill + edge) / (double) total;
                double f = fill / (double) Math.max(1, fill + edge);
                // gradient along the diagonal: cyan (top left) to violet (bottom right)
                double t = Math.max(0, Math.min(1, (x + y) / (2.0 * (n - 1))));
                int r = (int) lerp(0x5C, 0x9A, t), g = (int) lerp(0xDC, 0x64, t), b = (int) lerp(0xFF, 0xFF, t);
                // highlight in the middle like the white star of the logo
                double mid = Math.max(0, 1 - Math.hypot(x + 0.5 - c, y + 0.5 - c) / 2.2);
                r = (int) lerp(r, 255, mid * 0.8); g = (int) lerp(g, 255, mid * 0.8); b = (int) lerp(b, 255, mid * 0.8);
                // outline dark navy
                r = (int) lerp(0x14, r, f); g = (int) lerp(0x12, g, f); b = (int) lerp(0x3A, b, f);
                int alpha = (int) Math.round(a * 255);
                img.setRGB(x, y, alpha << 24 | r << 16 | g << 8 | b);
            }
        }
        ImageIO.write(img, "png", new File(args[0]));
    }

    /** Negative inside a four-pointed star (astroid-like, points on the axes) of the given radius. */
    static double star(double x, double y, double r) {
        double ax = Math.abs(x) / r, ay = Math.abs(y) / r;
        // |x|^p + |y|^p <= 1 with p < 1 gives concave sides
        double p = 0.62;
        return Math.pow(ax, p) + Math.pow(ay, p) - 1;
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
