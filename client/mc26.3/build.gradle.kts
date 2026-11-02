// Fabric companion mod for the Minecraft 26.3 runtime profile.
// Minecraft 26.1+ ships unobfuscated, so Loom uses Mojang's names directly (no mappings, no remapping).
plugins {
    alias(libs.plugins.fabric.loom)
}

base {
    archivesName.set("lumora-client-mc26.3")
}

/** Plain jars whose classes are copied into the mod jar (not nested, not remapped: they use no Minecraft classes). */
val bundled: Configuration by configurations.creating {
    isTransitive = false
}

dependencies {
    minecraft("com.mojang:minecraft:${libs.versions.mc263.minecraft.get()}")
    // no mod loader: started by the Lumora Loader (its API and the Mixin annotations at compile time only)
    compileOnly(project(":lumora-loader"))
    // Sodium and ViaFabricPlus are deliberately NOT compile dependencies: the mod talks to ViaFabricPlus through
    // reflection on its public API classes, so it loads without it and does not link GPL code.

    // Lumora client API and its Minecraft-independent implementation; their classes are bundled into the jar
    // below, so addons find dev.lumora.api.* at runtime through the Lumora mod.
    implementation(project(":lumora-core"))
    bundled(project(":lumora-api"))
    bundled(project(":lumora-core"))
    bundled(project(":lumora-social"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.processResources {
    val v = project.version.toString()
    inputs.property("version", v)
    filesMatching("lumora.loader.json") {
        expand("version" to v)
    }
}

tasks.withType<JavaCompile>().configureEach {
    // Minecraft APIs emit deprecation/removal lint we do not control; keep -Werror for our own launcher code only.
    options.compilerArgs.removeAll(listOf("-Werror"))
}

tasks.jar {
    // Stable file name referenced from the launcher's profiles.json and bundled-mods.json.
    archiveFileName.set("lumora-client-mc26.3.jar")
    from(rootProject.file("LICENSE")) { rename { "LICENSE_lumora" } }
    dependsOn(bundled)
    from(bundled.elements.map { jars -> jars.map { zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF", "LICENSE_lumora")
    }
}
