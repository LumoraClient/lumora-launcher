// Fabric companion mod for the Minecraft 1.8.9 runtime profile (Legacy Fabric).
// 1.8.9 predates Mojang's official mappings (1.14.4+): Loom maps it with Legacy Fabric's intermediary and Yarn names
// and remaps the jar to that intermediary for production.
plugins {
    alias(libs.plugins.fabric.loom.remap)
}

base {
    archivesName.set("lumora-client-mc1.8.9")
}

repositories {
    maven("https://maven.legacyfabric.net/") { name = "LegacyFabric" }
}

loom {
    intermediaryUrl.set("https://maven.legacyfabric.net/net/legacyfabric/intermediary/%1\$s/intermediary-%1\$s-v2.jar")
}

/** Plain jars whose classes are copied into the mod jar (not nested; they use no Minecraft classes, so remapping keeps them as they are). */
val bundled: Configuration by configurations.creating {
    isTransitive = false
}

dependencies {
    minecraft("com.mojang:minecraft:${libs.versions.mc189.minecraft.get()}")
    mappings("net.legacyfabric:yarn:${libs.versions.mc189.yarn.get()}:v2")
    // no mod loader: started by the Lumora Loader (its API and the Mixin annotations at compile time only)
    compileOnly(project(":lumora-loader"))

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
    filesMatching("lumora.loader.json") {
        expand("version" to v)
    }
}

tasks.withType<JavaCompile>().configureEach {
    // Minecraft 1.8.9 ships Java 8; the Lumora profile starts it with Java 21 (the Lumora API is built for Java 21).
    options.release.set(21)
    options.compilerArgs.removeAll(listOf("-Werror"))
}

tasks.remapJar {
    // Stable file name referenced from the launcher's profiles.json and bundled-mods.json.
    archiveFileName.set("lumora-client-mc1.8.9.jar")
}

tasks.jar {
    archiveClassifier.set("dev")
    from(rootProject.file("LICENSE")) { rename { "LICENSE_lumora" } }
    dependsOn(bundled)
    from(bundled.elements.map { jars -> jars.map { zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF", "LICENSE_lumora")
    }
}

// Lumora Loader: the game runs with Mojang's names (the loader maps the obfuscated jar), so the jar keeps them too
// and needs no refmap (no intermediary names at run time).
loom {
    mixin {
        useLegacyMixinAp.set(false)
    }
}
tasks.remapJar {
    targetNamespace.set("named")
}

// No Mojang mappings for this version: the jar ships Loom's merged mappings (official -> intermediary -> named, from
// Legacy Fabric's intermediary and Yarn, CC0). The Lumora Loader maps the obfuscated game jar to the named names.
tasks.processResources {
    from(provider { net.fabricmc.loom.LoomGradleExtension.get(project).mappingConfiguration.tinyMappings.toFile() }) {
        into("lumora")
        rename { "mappings.tiny" }
    }
}
