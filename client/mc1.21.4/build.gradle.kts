// Fabric companion mod for the Minecraft 1.21.4 runtime profile. Port of client/mc26.1.2 to the 1.21.4 APIs.
// 1.21.4 is obfuscated: Loom maps it with Mojang's official names and remaps the jar for production.
plugins {
    alias(libs.plugins.fabric.loom.remap)
}

base {
    archivesName.set("lumora-client-mc1.21.4")
}

/** Plain jars whose classes are copied into the mod jar (not nested; they use no Minecraft classes, so remapping keeps them as they are). */
val bundled: Configuration by configurations.creating {
    isTransitive = false
}

dependencies {
    minecraft("com.mojang:minecraft:${libs.versions.mc1214.minecraft.get()}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${libs.versions.mc26.fabric.loader.get()}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${libs.versions.mc1214.fabric.api.get()}")
    // Sodium and ViaFabricPlus are deliberately NOT compile dependencies (ViaFabricPlus via reflection only).

    // Lumora client API and its Minecraft-independent implementation, bundled into the jar (see tasks.jar)
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
    filesMatching("fabric.mod.json") {
        expand("version" to v)
    }
}

tasks.withType<JavaCompile>().configureEach {
    // Minecraft 1.21.4 runs on Java 21.
    options.release.set(21)
    options.compilerArgs.removeAll(listOf("-Werror"))
}

tasks.remapJar {
    // Stable file name referenced from the launcher's profiles.json and bundled-mods.json.
    archiveFileName.set("lumora-client-mc1.21.4.jar")
}

tasks.jar {
    archiveClassifier.set("dev")
    from(rootProject.file("LICENSE")) { rename { "LICENSE_lumora" } }
    dependsOn(bundled)
    from(bundled.elements.map { jars -> jars.map { zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF", "LICENSE_lumora")
    }
}
