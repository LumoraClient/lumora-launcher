package dev.lumora.client.render;

import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.platform.GlStateManager;
import dev.lumora.core.cosmetics.AttachPoint;
import dev.lumora.core.cosmetics.Cosmetics;
import dev.lumora.core.cosmetics.EmotePose;
import dev.lumora.core.cosmetics.PlayerFrame;
import dev.lumora.core.cosmetics.PlayerInput;
import dev.lumora.core.cosmetics.RenderMode;
import dev.lumora.core.cosmetics.model.CosmeticMesh;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;

/**
 * Lumora "Cosmetics & Emotes" for this legacy Minecraft version: while a player is drawn (PlayerEntityRenderer.render)
 * its frame from the core is current; the model mixin applies the emote pose, the renderer the whole-body transform
 * and the cosmetics feature draws the cosmetic meshes with the tessellator after each model part's GL transform.
 */
public final class CosmeticsRender {
    private CosmeticsRender() {
    }

    private static final float SCALE = 0.0625f;
    private static final PlayerInput INPUT = new PlayerInput();
    private static final Map<String, Identifier> IDS = new HashMap<>();
    /** Set while the cosmetics menu draws the preview player. */
    public static boolean preview;
    /** Cosmetic vertices drawn and model poses changed by an emote (smoke test). */
    public static int vertices;
    public static int posed;
    private static PlayerFrame current;
    private static AbstractClientPlayerEntity currentPlayer;
    private static int depth;

    /** PlayerEntityRenderer.render starts: the player's frame becomes current ({@code null}: nothing to do). */
    public static void begin(AbstractClientPlayerEntity p, float partial) {
        depth++;
        current = null;
        currentPlayer = p;
        Cosmetics c = Cosmetics.get();
        if (c == null || p == null) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerInput in = INPUT.reset();
        in.id = p.getUuid();
        in.self = p == mc.player;
        in.preview = preview && in.self;
        in.age = p.ticksAlive + partial;
        double dx = p.x - p.prevX;
        double dz = p.z - p.prevZ;
        in.groundSpeed = (float) Math.sqrt(dx * dx + dz * dz);
        in.verticalSpeed = (float) (p.y - p.prevY);
        in.onGround = p.onGround;
        in.sneaking = p.isSneaking();
        in.sprinting = p.isSprinting();
        in.gliding = false; // no elytra before 1.9
        in.invisible = p.isInvisible();
        in.firstPerson = in.self && mc.options.perspective == 0;
        float body = p.prevBodyYaw + (p.bodyYaw - p.prevBodyYaw) * partial;
        float head = p.prevHeadYaw + (p.headYaw - p.prevHeadYaw) * partial;
        in.headYaw = wrap(head - body);
        in.headPitch = p.prevPitch + (p.pitch - p.prevPitch) * partial;
        in.bodyYaw = body;
        current = c.frame(in);
    }

    private static float wrap(float degrees) {
        float d = degrees % 360f;
        if (d >= 180f) {
            d -= 360f;
        }
        if (d < -180f) {
            d += 360f;
        }
        return d;
    }

    /** PlayerEntityRenderer.render ends. */
    public static void end() {
        depth = Math.max(0, depth - 1);
        if (depth == 0) {
            current = null;
            currentPlayer = null;
        }
    }

    /** After PlayerEntityModel.setAngles: the emote moves the parts it animates (only while the player is drawn). */
    public static void pose(PlayerEntityModel model, Object entity) {
        PlayerFrame frame = current;
        if (frame == null || entity != currentPlayer || !frame.pose().active()) {
            return;
        }
        EmotePose p = frame.pose();
        part(model.head, p, AttachPoint.HEAD);
        part(model.body, p, AttachPoint.BODY);
        part(model.rightArm, p, AttachPoint.RIGHT_ARM);
        part(model.leftArm, p, AttachPoint.LEFT_ARM);
        part(model.rightLeg, p, AttachPoint.RIGHT_LEG);
        part(model.leftLeg, p, AttachPoint.LEFT_LEG);
        // Minecraft copied the parts into the outer layer before; copy again
        copy(model.head, model.hat);
        copy(model.body, model.jacket);
        copy(model.leftArm, model.leftSleeve);
        copy(model.rightArm, model.rightSleeve);
        copy(model.leftLeg, model.leftPants);
        copy(model.rightLeg, model.rightPants);
        posed++;
    }

    /** Angles in posX/posY/posZ, rotation point in pivotX/Y/Z (Legacy Yarn names). */
    private static void part(ModelPart m, EmotePose p, AttachPoint point) {
        int i = point.ordinal();
        if (!p.moves(i)) {
            return;
        }
        m.posX = p.rotation(i, 0, m.posX);
        m.posY = p.rotation(i, 1, m.posY);
        m.posZ = p.rotation(i, 2, m.posZ);
        m.pivotX = p.position(i, 0, m.pivotX);
        m.pivotY = p.position(i, 1, m.pivotY);
        m.pivotZ = p.position(i, 2, m.pivotZ);
    }

    private static void copy(ModelPart from, ModelPart to) {
        to.posX = from.posX;
        to.posY = from.posY;
        to.posZ = from.posZ;
        to.pivotX = from.pivotX;
        to.pivotY = from.pivotY;
        to.pivotZ = from.pivotZ;
    }

    /** After the body turn: sitting, lying and spinning move the whole player (GL matrix at the feet, y up). */
    public static void root() {
        PlayerFrame frame = current;
        if (frame == null || !frame.pose().hasRoot()) {
            return;
        }
        EmotePose p = frame.pose();
        GlStateManager.translate(p.rootX(), p.rootY(), p.rootZ());
        if (p.rootRotZ() != 0) {
            GlStateManager.rotate((float) Math.toDegrees(p.rootRotZ()), 0, 0, 1);
        }
        if (p.rootRotY() != 0) {
            GlStateManager.rotate((float) Math.toDegrees(p.rootRotY()), 0, 1, 0);
        }
        if (p.rootRotX() != 0) {
            GlStateManager.rotate((float) Math.toDegrees(p.rootRotX()), 1, 0, 0);
        }
    }

    private static ModelPart partOf(PlayerEntityModel model, AttachPoint point) {
        return switch (point) {
            case HEAD -> model.head;
            case BODY -> model.body;
            case RIGHT_ARM -> model.rightArm;
            case LEFT_ARM -> model.leftArm;
            case RIGHT_LEG -> model.rightLeg;
            case LEFT_LEG -> model.leftLeg;
            case ROOT -> null;
        };
    }

    /** From the cosmetics feature (GL matrix = the model's space): every cosmetic part after its model part. */
    public static void draw(PlayerEntityModel model, AbstractClientPlayerEntity player, float partial) {
        PlayerFrame frame = current;
        if (frame == null || player != currentPlayer || frame.count() == 0 || player.isInvisible()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        GlStateManager.disableCull();
        GlStateManager.color(1f, 1f, 1f, 1f);
        for (int i = 0; i < frame.count(); i++) {
            PlayerFrame.Item item = frame.item(i);
            CosmeticMesh mesh = item.mesh();
            if (mesh.isEmpty()) {
                continue;
            }
            mc.getTextureManager().bindTexture(IDS.computeIfAbsent(item.texture(), Identifier::new));
            boolean glow = item.render() == RenderMode.EMISSIVE;
            if (item.render() != RenderMode.CUTOUT) {
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(770, 771);
            }
            if (glow) {
                GlStateManager.disableLighting();
                GLX.gl13MultiTexCoord2f(GLX.lightmapTextureUnit, 240f, 240f);
            }
            for (AttachPoint point : AttachPoint.values()) {
                int n = mesh.vertices(point);
                if (n == 0) {
                    continue;
                }
                GlStateManager.pushMatrix();
                ModelPart part = partOf(model, point);
                if (part != null) {
                    part.preRender(SCALE);
                }
                float[] d = mesh.data(point);
                b.begin(GL11.GL_QUADS, VertexFormats.POSITION_TEXTURE_NORMAL);
                for (int v = 0; v < n; v++) {
                    int o = v * CosmeticMesh.STRIDE;
                    b.vertex(d[o], d[o + 1], d[o + 2]).texture(d[o + 3], d[o + 4]).normal(d[o + 5], d[o + 6], d[o + 7]).next();
                }
                t.draw();
                vertices += n;
                GlStateManager.popMatrix();
            }
            if (glow) {
                int light = player.getLightmapCoordinates(partial);
                GLX.gl13MultiTexCoord2f(GLX.lightmapTextureUnit, light % 65536, light / 65536);
                GlStateManager.enableLighting();
            }
            if (item.render() != RenderMode.CUTOUT) {
                GlStateManager.disableBlend();
            }
        }
        GlStateManager.enableCull();
    }
}
