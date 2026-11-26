package dev.lumora.voice.audio;

public enum AudioBackend {
    JAVAX("JavaX / JavaSound"), OPENAL("OpenAL");
    private final String label;
    AudioBackend(String label) { this.label = label; }
    public String label() { return label; }

    private static volatile Boolean lwjgl3;

    /**
     * Whether this engine works in the running game. OpenAL needs LWJGL 3 (Minecraft 1.13+); Minecraft 1.8.9 and
     * 1.12.2 ship LWJGL 2, whose OpenAL classes have a different API.
     */
    public boolean available() {
        if (this != OPENAL) return true;
        Boolean known = lwjgl3;
        if (known == null) {
            try { Class.forName("org.lwjgl.openal.EXTThreadLocalContext", false, AudioBackend.class.getClassLoader()); known = true; }
            catch (ClassNotFoundException | LinkageError missing) { known = false; }
            lwjgl3 = known;
        }
        return known;
    }
}
