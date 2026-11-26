pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "lumora-client"

// Version-independent launcher (no Minecraft classes on its classpath).
include(":launcher-core")
include(":launcher-app")

// Client extension framework: public addon/widget API and its Minecraft-independent implementation.
// Both are plain Java 21 (the oldest supported runtime) and are bundled into every companion mod jar.
include(":lumora-api")
include(":lumora-core")
include(":lumora-social")
include(":lumora-social-server")
include(":lumora-loader")
// Runs the Lumora Client inside Fabric, Quilt, Forge and NeoForge (copied into the loader variants of the client jars).
include(":lumora-bridge")
// LumoraVoice (voice chat), built into the client; the launcher ships its jar once (bundled/lumora-voice.jar).
include(":lumora-voice")
// Example addon that only uses the public API (no Minecraft classes) and therefore runs on every version.
include(":examples:session-timer-addon")

// One Fabric companion mod per native Minecraft version. Each one is a separate,
// explicitly configured runtime profile with its own Loom/Minecraft/Fabric versions.
val lumoraClientVersions = listOf("26.3", "26.2", "26.1.2", "1.21.11", "1.21.8", "1.21.4", "1.21", "1.20.6", "1.20.4", "1.20.1", "1.19.4", "1.18.2", "1.17.1", "1.16.5", "1.12.2", "1.8.9")
for (v in lumoraClientVersions) {
    include(":client-mc$v")
    project(":client-mc$v").projectDir = file("client/mc$v")
}
gradle.extra["lumoraClientVersions"] = lumoraClientVersions
// Versions with a Fabric variant of the client jar (Fabric Loader exists from 1.14; 1.12.2 and 1.8.9 only have Legacy Fabric).
gradle.extra["lumoraFabricVersions"] = lumoraClientVersions.filter { it != "1.12.2" && it != "1.8.9" }
// Versions with a Forge/NeoForge variant: both run Minecraft with Mojang's names from 1.20.6, like the Lumora Client.
gradle.extra["lumoraForgeVersions"] = listOf("26.3", "26.2", "26.1.2", "1.21.11", "1.21.8", "1.21.4", "1.21", "1.20.6")

// Legacy Fabric versions (1.8.9, 1.12.2): Legacy intermediary + Yarn names, ported separately; built but not bundled yet.
val lumoraLegacyVersions = listOf<String>()
for (v in lumoraLegacyVersions) {
    include(":client-mc$v")
    project(":client-mc$v").projectDir = file("client/mc$v")
}
