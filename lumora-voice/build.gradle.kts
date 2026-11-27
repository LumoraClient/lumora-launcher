// LumoraVoice, built into the Lumora Client: voice chat for Laby Voice, Simple Voice Chat and Plasmo Voice with spatial
// sound and speaker display, for every Minecraft version (1.8.9 to 26.3). The code only uses the Lumora API.
// The audio libraries (Opus, RNNoise with its natives, Kotlin) make the jar about 29 MB, so it is not copied into every
// client jar: the launcher ships it once (bundled/lumora-voice.jar) and hands it to the game as a built-in addon
// (-Dlumora.builtinAddons, see dev.lumora.core.addon.BuiltInAddons). Formerly the separate addon LumoraVoice 0.9.8.
plugins {
    `java-library`
}

/** Version of the voice module itself (the addon descriptor); 1.0.0 = first version built into the client. */
val voiceVersion = "1.0.0"

base {
    archivesName.set("lumora-voice")
}

/** Libraries unpacked into the jar (classes, natives, licenses stay as resources). */
val bundled: Configuration = configurations.create("bundled") {
    isTransitive = false
}

val lwjgl = "3.3.3"
// the launcher and the game run on Windows only: one RNNoise native (5 MB) instead of five (26 MB), which keeps the
// portable EXE below the update limit of older launchers (200 MiB)
val rnnoisePlatforms = listOf("windows-x86_64")
val libraries = listOf(
    "io.github.jaredmdobson:concentus:1.0.2",
    "cn.enaium.rnnoise:rnnoise-kmp-jvm:1.0.2",
    "org.jetbrains.kotlin:kotlin-stdlib:2.4.10",
)

dependencies {
    // provided at runtime by the Lumora client mod
    compileOnly(project(":lumora-api"))
    testImplementation(project(":lumora-api"))
    // Minecraft provides Gson; 1.8.9 and 1.12.2 ship 2.2.4, so the code compiles against that version
    compileOnly(libs.gson.minecraft.min)
    testImplementation(libs.gson.minecraft.min)
    // OpenAL engine: Minecraft 1.13+ ships LWJGL 3; on LWJGL 2 (1.8.9, 1.12.2) the engine is not offered
    compileOnly("org.lwjgl:lwjgl:$lwjgl")
    compileOnly("org.lwjgl:lwjgl-openal:$lwjgl")
    testRuntimeOnly("org.lwjgl:lwjgl:$lwjgl")
    testRuntimeOnly("org.lwjgl:lwjgl-openal:$lwjgl")

    for (library in libraries) {
        implementation(library)
        bundled(library)
    }
    for (platform in rnnoisePlatforms) {
        runtimeOnly("cn.enaium.rnnoise:rnnoise-kmp-jni-jvm-$platform:1.0.2")
        bundled("cn.enaium.rnnoise:rnnoise-kmp-jni-jvm-$platform:1.0.2")
    }

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    // Java 21 is the oldest runtime of the supported Minecraft versions
    options.release.set(21)
}

tasks.processResources {
    inputs.property("version", voiceVersion)
    filesMatching("lumora-addon.json") {
        expand("version" to voiceVersion)
    }
}

tasks.jar {
    archiveFileName.set("lumora-voice.jar")
    // the bundled libraries flat inside the jar; a path shipped by two libraries fails the build instead of
    // silently dropping one copy
    inputs.files(bundled)
    from(bundled.elements.map { files -> files.map { zipTree(it.asFile) } }) {
        exclude("META-INF/MANIFEST.MF", "META-INF/maven/**", "META-INF/versions/**", "module-info.class",
            "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "fabric.mod.json")
    }
    duplicatesStrategy = DuplicatesStrategy.FAIL
    from(rootProject.file("LICENSE")) { rename { "LICENSE-lumoravoice.txt" } }
}
