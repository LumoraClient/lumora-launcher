package dev.lumora.api.hud;

import java.util.List;

/** The HUD editor and its layout profiles (Default, PvP, Survival, Minimal and own ones). */
public interface HudEditor {
    /** Opens the editor (client thread). */
    void open();

    boolean isOpen();

    String activeProfile();

    /** Profile ids in display order. */
    List<String> profiles();

    /** Switches the active profile; creates it as a copy of the current one if it does not exist yet. */
    boolean activateProfile(String profile);
}
