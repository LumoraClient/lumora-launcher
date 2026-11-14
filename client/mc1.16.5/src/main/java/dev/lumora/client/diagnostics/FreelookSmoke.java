package dev.lumora.client.diagnostics;

import dev.lumora.core.camera.Freelook;
import dev.lumora.core.extras.GameExtras;

import java.util.List;

/** Smoke step "freelook" (module "Freelook"): the free camera turns half way around the player, the player does not. */
final class FreelookSmoke {
    private static float[] before;

    private FreelookSmoke() {
    }

    /** Free camera on and turned 180 degrees: the screenshot shows the player from the front. */
    static void start() {
        GameExtras x = ApiSmoke.core().platform().extras();
        before = x.playerRotation();
        if (before == null) {
            return;
        }
        Freelook.start(before[0], before[1]);
        x.setPerspective(1);
        Freelook.turn(1200, 100);
    }

    static void check(List<String> problems) {
        GameExtras x = ApiSmoke.core().platform().extras();
        float[] now = x.playerRotation();
        float camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getYRot();
        dev.lumora.client.LumoraClient.LOG.info("LUMORA_FREELOOK active={} perspective={} player={} free={} camera={}",
                Freelook.active(), x.perspective(), now == null ? null : now[0] + "/" + now[1],
                Freelook.yaw() + "/" + Freelook.pitch(), camera);
        if (before == null || now == null) {
            problems.add("freelook: no player");
            return;
        }
        if (!Freelook.active()) {
            problems.add("freelook: not active");
        }
        if (Math.abs(now[0] - before[0]) > 0.01f || Math.abs(now[1] - before[1]) > 0.01f) {
            problems.add("freelook: the player turned with the camera");
        }
        if (x.perspective() != 1) {
            problems.add("freelook: not in third person: " + x.perspective());
        }
        if (!Float.isNaN(camera) && Math.abs(wrap(camera - Freelook.yaw())) > 0.5f) {
            problems.add("freelook: the camera does not follow the free camera: " + camera);
        }
    }

    static void stop() {
        Freelook.stop();
        ApiSmoke.core().platform().extras().setPerspective(0);
    }

    private static float wrap(float degrees) {
        float d = degrees % 360f;
        return d > 180f ? d - 360f : d < -180f ? d + 360f : d;
    }
}
