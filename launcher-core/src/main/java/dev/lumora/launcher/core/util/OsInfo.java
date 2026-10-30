package dev.lumora.launcher.core.util;

import java.util.Locale;

/** Operating system description in the vocabulary used by Mojang's version rules. */
public record OsInfo(Name name, Arch arch, String version) {
    public enum Name {
        WINDOWS("windows"), LINUX("linux"), OSX("osx");

        public final String mojangName;

        Name(String mojangName) {
            this.mojangName = mojangName;
        }
    }

    public enum Arch {
        X86_64, X86, ARM64, OTHER;

        public boolean is64Bit() {
            return this == X86_64 || this == ARM64;
        }
    }

    public static OsInfo current() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Name name = os.contains("win") ? Name.WINDOWS : os.contains("mac") ? Name.OSX : Name.LINUX;
        String a = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        Arch arch = switch (a) {
            case "amd64", "x86_64" -> Arch.X86_64;
            case "x86", "i386", "i486", "i586", "i686" -> Arch.X86;
            case "aarch64", "arm64" -> Arch.ARM64;
            default -> Arch.OTHER;
        };
        return new OsInfo(name, arch, System.getProperty("os.version", ""));
    }

    /** Key used in Mojang's java-runtime manifest (all.json). */
    public String javaRuntimePlatform() {
        return switch (name) {
            case WINDOWS -> arch == Arch.ARM64 ? "windows-arm64" : arch == Arch.X86 ? "windows-x86" : "windows-x64";
            case LINUX -> arch == Arch.X86 ? "linux-i386" : "linux";
            case OSX -> arch == Arch.ARM64 ? "mac-os-arm64" : "mac-os";
        };
    }

    /** Value used for the "arch" rule in Mojang version JSON (only "x86" is used in practice). */
    public String mojangArch() {
        return switch (arch) {
            case X86 -> "x86";
            case X86_64 -> "x86_64";
            case ARM64 -> "arm64";
            case OTHER -> System.getProperty("os.arch", "unknown");
        };
    }

    public String javaExecutableName(boolean console) {
        if (name == Name.WINDOWS) {
            return console ? "java.exe" : "javaw.exe";
        }
        return "java";
    }
}
