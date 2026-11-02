package dev.lumora.testaddon.lib;

/** Library class of the test addon "greeter" (its own jar). */
public final class Greeter {
    private Greeter() {
    }

    public static String greet() {
        return "hi from " + Greeter.class.getClassLoader().getName();
    }
}
