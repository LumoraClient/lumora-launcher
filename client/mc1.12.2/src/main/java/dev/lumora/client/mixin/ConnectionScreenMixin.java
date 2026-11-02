package dev.lumora.client.mixin;

import dev.lumora.api.screen.AddonView;
import dev.lumora.client.api.McConnection;
import dev.lumora.client.gui.AddonViewScreen;
import dev.lumora.core.connection.ConnectionModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora's connection system where Minecraft opens its connect or disconnect screen. Every connection is tracked
 * (phases, events for addons); with the module "Verbindung" on, Lumora's screens are shown instead. The connect screen
 * itself keeps running underneath (Lumora's screen ticks it), so Minecraft still does the whole connection, login and
 * session handling. Exact class checks: screens of other mods that extend these stay as they are. Any error keeps
 * Minecraft's own screen.
 */
@Mixin(MinecraftClient.class)
public abstract class ConnectionScreenMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void lumora$connection(Screen screen, CallbackInfo ci) {
        if (screen == null || (screen.getClass() != ConnectScreen.class && screen.getClass() != DisconnectedScreen.class)) {
            return;
        }
        AddonView view;
        Screen parent;
        boolean connecting = screen.getClass() == ConnectScreen.class;
        try {
            if (connecting) {
                McConnection attempt = new McConnection((ConnectScreen) screen);
                parent = attempt.parent();
                view = ConnectionModule.connecting(attempt);
            } else {
                DisconnectedScreenAccessor d = (DisconnectedScreenAccessor) screen;
                Text reason = d.lumora$reason();
                parent = d.lumora$parent();
                view = ConnectionModule.disconnected(McConnection.keys(reason), reason == null ? "" : reason.asUnformattedString());
            }
        } catch (RuntimeException | LinkageError e) {
            return;
        }
        if (view == null) {
            return;
        }
        Screen back = parent != null ? parent : new TitleScreen();
        ci.cancel();
        ((MinecraftClient) (Object) this).setScreen(connecting ? AddonViewScreen.connecting(back, view, screen::tick)
                : AddonViewScreen.create(back, view));
    }
}
