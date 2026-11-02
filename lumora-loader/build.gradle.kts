// Lumora Loader: starts Minecraft without a mod loader. It loads the game and the Lumora client into its own class
// loader, applies the client's mixins and calls its entry point. Mixin (MIT) and ASM (BSD-3-Clause) are copied into
// the jar, so a game start needs no further downloads. Gson and Guava, which Mixin uses, come from Minecraft itself.
plugins {
    `java-library`
}

repositories {
    mavenCentral()
    // SpongePowered Mixin in the build maintained by FabricMC: the only one that understands Java 25 class files.
    // Only this library is taken from there (no Fabric Loader, no Fabric API).
    maven("https://maven.fabricmc.net/") {
        name = "Mixin"
        content { includeModule("net.fabricmc", "sponge-mixin") }
    }
}

val mixinVersion = "0.17.4+mixin.0.8.7"
val asmVersion = "9.10.1"

/** Jars whose classes go into the loader jar (no nested jars). */
val shade: Configuration by configurations.creating {
    isTransitive = false
}

dependencies {
    // client modules compile their mixins against the same Mixin annotations
    compileOnlyApi("net.fabricmc:sponge-mixin:$mixinVersion")
    compileOnly("org.ow2.asm:asm:$asmVersion")
    compileOnly("org.ow2.asm:asm-tree:$asmVersion")
    compileOnly(libs.gson.minecraft.min)
    shade("net.fabricmc:sponge-mixin:$mixinVersion")
    for (m in listOf("asm", "asm-tree", "asm-commons", "asm-util", "asm-analysis")) {
        shade("org.ow2.asm:$m:$asmVersion")
    }

    testImplementation("net.fabricmc:sponge-mixin:$mixinVersion")
    testImplementation("org.ow2.asm:asm:$asmVersion")
    testImplementation("org.ow2.asm:asm-tree:$asmVersion")
    testImplementation(libs.gson)
    testImplementation("com.google.guava:guava:33.4.8-jre")
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

tasks.jar {
    archiveFileName.set("lumora-loader.jar")
    from(rootProject.file("LICENSE")) { rename { "LICENSE_lumora" } }
    from(rootProject.file("packaging/licenses/BSD-3-Clause-ASM.txt")) { rename { "LICENSE_asm.txt" } }
    dependsOn(shade)
    from(shade.elements.map { jars -> jars.map { zipTree(it) } }) {
        // our own service registrations only (Mixin's would point at LaunchWrapper / ModLauncher)
        exclude("META-INF/services/**", "META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA",
            "META-INF/versions/**", "module-info.class", "LICENSE_lumora")
        // Mixin's licence text under its own name (ASM's BSD text: THIRD_PARTY_NOTICES / licenses folder)
        rename("LICENSE.txt", "LICENSE_mixin.txt")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    manifest {
        attributes("Main-Class" to "dev.lumora.loader.Main", "Implementation-Title" to "Lumora Loader",
            "Implementation-Version" to project.version.toString())
        // Mixin reads the ASM version from the package manifest (it decides which Java class versions it supports);
        // copied classes would otherwise report the loader's version
        for (pkg in listOf("org/objectweb/asm/", "org/objectweb/asm/tree/", "org/objectweb/asm/tree/analysis/",
                "org/objectweb/asm/commons/", "org/objectweb/asm/util/", "org/objectweb/asm/signature/")) {
            attributes(mapOf("Implementation-Title" to "ASM", "Implementation-Version" to asmVersion), pkg)
        }
    }
}
