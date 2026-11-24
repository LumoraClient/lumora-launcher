package dev.lumora.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.lumora.core.cosmetics.AttachPoint;
import dev.lumora.core.cosmetics.Cosmetics;
import dev.lumora.core.cosmetics.EmotePose;
import dev.lumora.core.cosmetics.PlayerFrame;
import dev.lumora.core.cosmetics.PlayerInput;
import dev.lumora.core.cosmetics.RenderMode;
import dev.lumora.core.cosmetics.model.CosmeticMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Lumora "Cosmetics & Emotes" for this Minecraft version: hands the core what a player does this frame (while the
 * render state is extracted), applies the emote pose to the model parts and the whole body, and submits the cosmetic
 * meshes attached to the model parts. Everything else is computed by the version-independent core.
 */
public final class CosmeticsRender {
    private CosmeticsRender() {
    }

    private static final int FULL_BRIGHT = 0xF000F0;
    private static final PlayerInput INPUT = new PlayerInput();
    private static final Map<String, Identifier> IDS = new HashMap<>();
    /** Set while the cosmetics menu extracts the preview player. */
    public static boolean preview;
    /** Cosmetic vertices handed to the renderer and model poses changed by an emote (smoke test). */
    public static int vertices;
    public static int posed;

    /** The player's frame, built while its render state is extracted ({@code null}: nothing to do). */
    public static PlayerFrame frame(Avatar entity, AvatarRenderState state, float partial) {
        Cosmetics c = Cosmetics.get();
        if (c == null || !(entity instanceof Player p)) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        PlayerInput in = INPUT.reset();
        in.id = p.getUUID();
        in.self = p == mc.player;
        in.preview = preview && in.self;
        in.age = p.tickCount + partial;
        double dx = p.getX() - p.xo;
        double dz = p.getZ() - p.zo;
        in.groundSpeed = (float) Math.sqrt(dx * dx + dz * dz);
        in.verticalSpeed = (float) (p.getY() - p.yo);
        in.onGround = p.onGround();
        in.sneaking = p.isCrouching();
        in.sprinting = p.isSprinting();
        in.swimming = p.isVisuallySwimming();
        in.gliding = p.isFallFlying();
        in.invisible = p.isInvisible();
        in.firstPerson = in.self && mc.options.getCameraType().isFirstPerson();
        in.headYaw = state.yRot;
        in.headPitch = state.xRot;
        in.bodyYaw = state.bodyRot;
        in.limbSwing = state.walkAnimationPos;
        in.limbAmount = state.walkAnimationSpeed;
        return c.frame(in);
    }

    /** After PlayerModel.setupAnim: the emote moves the parts it animates. */
    public static void pose(PlayerModel model, PlayerFrame frame) {
        if (frame == null || !frame.pose().active()) {
            return;
        }
        EmotePose p = frame.pose();
        part(model.head, p, AttachPoint.HEAD);
        part(model.body, p, AttachPoint.BODY);
        part(model.rightArm, p, AttachPoint.RIGHT_ARM);
        part(model.leftArm, p, AttachPoint.LEFT_ARM);
        part(model.rightLeg, p, AttachPoint.RIGHT_LEG);
        part(model.leftLeg, p, AttachPoint.LEFT_LEG);
        posed++;
    }

    private static void part(ModelPart m, EmotePose p, AttachPoint point) {
        int i = point.ordinal();
        if (!p.moves(i)) {
            return;
        }
        m.xRot = p.rotation(i, 0, m.xRot);
        m.yRot = p.rotation(i, 1, m.yRot);
        m.zRot = p.rotation(i, 2, m.zRot);
        m.x = p.position(i, 0, m.x);
        m.y = p.position(i, 1, m.y);
        m.z = p.position(i, 2, m.z);
    }

    /** After the body turn (setupRotations): sitting, lying and spinning move the whole player. */
    public static void root(PoseStack pose, PlayerFrame frame) {
        if (frame == null || !frame.pose().hasRoot()) {
            return;
        }
        EmotePose p = frame.pose();
        pose.translate(p.rootX(), p.rootY(), p.rootZ());
        if (p.rootRotZ() != 0) {
            pose.mulPose(Axis.ZP.rotation(p.rootRotZ()));
        }
        if (p.rootRotY() != 0) {
            pose.mulPose(Axis.YP.rotation(p.rootRotY()));
        }
        if (p.rootRotX() != 0) {
            pose.mulPose(Axis.XP.rotation(p.rootRotX()));
        }
    }

    private static ModelPart partOf(PlayerModel model, AttachPoint point) {
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

    /** From the cosmetics layer: every cosmetic part after its model part's transform. */
    public static void submit(PlayerModel model, PoseStack pose, SubmitNodeCollector collector, int light,
                              AvatarRenderState state, PlayerFrame frame) {
        if (frame == null || frame.count() == 0 || state.isInvisible || state.isSpectator) {
            return;
        }
        for (int i = 0; i < frame.count(); i++) {
            PlayerFrame.Item item = frame.item(i);
            CosmeticMesh mesh = item.mesh();
            if (mesh.isEmpty()) {
                continue;
            }
            Identifier texture = IDS.computeIfAbsent(item.texture(), Identifier::parse);
            RenderType type = switch (item.render()) {
                case TRANSLUCENT -> RenderTypes.entityTranslucent(texture);
                case EMISSIVE -> RenderTypes.entityTranslucentEmissive(texture);
                default -> RenderTypes.entityCutout(texture);
            };
            int l = item.render() == RenderMode.EMISSIVE ? FULL_BRIGHT : light;
            for (AttachPoint point : AttachPoint.values()) {
                int n = mesh.vertices(point);
                if (n == 0) {
                    continue;
                }
                float[] data = mesh.data(point);
                pose.pushPose();
                model.root().translateAndRotate(pose);
                ModelPart part = partOf(model, point);
                if (part != null) {
                    part.translateAndRotate(pose);
                }
                collector.submitCustomGeometry(pose, type, (p, vc) -> emit(p, vc, data, n, l));
                pose.popPose();
            }
        }
    }

    private static void emit(PoseStack.Pose p, VertexConsumer vc, float[] d, int n, int light) {
        vertices += n;
        for (int v = 0; v < n; v++) {
            int o = v * CosmeticMesh.STRIDE;
            vc.addVertex(p, d[o], d[o + 1], d[o + 2]).setColor(-1).setUv(d[o + 3], d[o + 4])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, d[o + 5], d[o + 6], d[o + 7]);
        }
    }
}
