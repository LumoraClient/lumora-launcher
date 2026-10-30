package dev.lumora.launcher.app.ui;

import com.google.gson.JsonObject;
import dev.lumora.launcher.core.download.UserAgent;
import dev.lumora.launcher.core.util.Json;
import dev.lumora.launcher.core.util.Log;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;

/**
 * Player avatar: initials by default; after sign-in the skin face is loaded from Mojang's official
 * session server (sessionserver.mojang.com -> textures.minecraft.net). Only the public profile UUID is sent.
 */
public final class Avatar extends JComponent {
    private static final Set<String> TEXTURE_HOSTS = Set.of("textures.minecraft.net");
    private final int size;
    private String name;
    private BufferedImage face;

    public Avatar(int size) {
        this.size = size;
        setPreferredSize(new Dimension(size, size));
        setMinimumSize(new Dimension(size, size));
        setMaximumSize(new Dimension(size, size));
    }

    /** @param uuid profile UUID without dashes, or null when signed out */
    public void setPlayer(String name, String uuid) {
        this.name = name;
        this.face = null;
        repaint();
        if (uuid == null || !uuid.matches("[0-9a-fA-F]{32}")) {
            return;
        }
        Thread t = new Thread(() -> {
            BufferedImage f = loadFace(uuid);
            if (f != null) {
                SwingUtilities.invokeLater(() -> {
                    if (name != null && name.equals(this.name)) {
                        face = f;
                        repaint();
                    }
                });
            }
        }, "avatar-loader");
        t.setDaemon(true);
        t.start();
    }

    static BufferedImage loadFace(String uuid) {
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            HttpResponse<String> profile = http.send(HttpRequest.newBuilder(
                            URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid))
                    .header("User-Agent", UserAgent.VALUE).timeout(Duration.ofSeconds(10)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (profile.statusCode() != 200) {
                return null;
            }
            JsonObject p = Json.GSON.fromJson(profile.body(), JsonObject.class);
            String value = p.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString();
            JsonObject tex = Json.GSON.fromJson(new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8), JsonObject.class);
            String url = tex.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString().replace("http://", "https://");
            URI skinUri = URI.create(url);
            if (!TEXTURE_HOSTS.contains(skinUri.getHost())) {
                return null;
            }
            HttpResponse<byte[]> skin = http.send(HttpRequest.newBuilder(skinUri).header("User-Agent", UserAgent.VALUE)
                    .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofByteArray());
            BufferedImage s = ImageIO.read(new ByteArrayInputStream(skin.body()));
            if (s == null || s.getWidth() < 64) {
                return null;
            }
            BufferedImage out = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.drawImage(s.getSubimage(8, 8, 8, 8), 0, 0, null);   // face
            g.drawImage(s.getSubimage(40, 8, 8, 8), 0, 0, null);  // hat layer
            g.dispose();
            return out;
        } catch (Exception e) {
            Log.debug("Skin face not available: " + e.getMessage());
            return null;
        }
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        RoundRectangle2D clip = new RoundRectangle2D.Float(0, 0, size, size, 12, 12);
        if (face != null) {
            g.clip(clip);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(face, 0, 0, size, size, null);
        } else {
            g.setColor(name == null ? Theme.SURFACE_2 : Theme.alpha(Theme.ACCENT, 60));
            g.fill(clip);
            if (name == null) {
                Icons.of(Icons.Kind.USER, size * 2 / 3, Theme.MUTED).paintIcon(this, g, size / 6, size / 6);
            } else {
                String initial = name.substring(0, 1).toUpperCase();
                g.setColor(Theme.ACCENT_HOVER);
                g.setFont(Theme.font(Font.BOLD, size * 0.45f));
                FontMetrics fm = g.getFontMetrics();
                g.drawString(initial, (size - fm.stringWidth(initial)) / 2, (size - fm.getHeight()) / 2 + fm.getAscent());
            }
        }
        g.dispose();
    }
}
