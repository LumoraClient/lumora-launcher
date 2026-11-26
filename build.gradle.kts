plugins {
    alias(libs.plugins.fabric.loom) apply false
    alias(libs.plugins.fabric.loom.remap) apply false
}

val lumoraVersion = providers.gradleProperty("lumora.version").get()

allprojects {
    group = providers.gradleProperty("lumora.group").get()
    version = lumoraVersion
}

subprojects {
    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        }
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.release.set(25)
            options.compilerArgs.addAll(listOf("-Xlint:all,-serial,-processing,-this-escape", "-Werror"))
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            jvmArgs("--enable-native-access=ALL-UNNAMED")
            testLogging {
                events("failed", "skipped")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }
    }
}


// ------------------------------------------------------------------------------------------------
// Loader variants of the client jars: the same Lumora Client, started by a mod loader through lumora-bridge, so it runs
// next to other mods.
// - lumora-client-mc<version>-fabric.jar (Fabric, Quilt; 1.16.5+): 26.1+ is not obfuscated and Fabric uses Mojang's
//   names there, so the jar is taken as it is; older versions are remapped to Fabric's intermediary names (Loom remaps
//   the mixin targets as well). The launcher adds it with -Dfabric.addMods.
// - lumora-client-mc<version>-forge.jar (Forge, NeoForge; 1.20.6+): both run Minecraft with Mojang's names from 1.20.6,
//   so the jar keeps them; mods.toml for Forge, neoforge.mods.toml for NeoForge. The launcher puts a managed copy in mods/.
@Suppress("UNCHECKED_CAST")
val lumoraFabricVersions = gradle.extra["lumoraFabricVersions"] as List<String>
@Suppress("UNCHECKED_CAST")
val lumoraForgeVersions = gradle.extra["lumoraForgeVersions"] as List<String>

fun fabricModJson(version: String, mc: String) = """
    |{
    |  "schemaVersion": 1,
    |  "id": "lumora",
    |  "version": "$version",
    |  "name": "Lumora Client",
    |  "description": "The Lumora Client next to other Fabric mods.",
    |  "license": "GPL-3.0-only",
    |  "environment": "client",
    |  "entrypoints": {
    |    "preLaunch": ["dev.lumora.fabric.LumoraFabric"],
    |    "client": ["dev.lumora.fabric.LumoraFabric"]
    |  },
    |  "mixins": ["lumora.mixins.json"],
    |  "depends": { "fabricloader": ">=0.16.0", "minecraft": "$mc" }
    |}
    |""".trimMargin()

/** Next to other mods, a mixin that does not apply switches off its one feature instead of stopping the game. */
fun lenientMixins(line: String) = line.replace("\"required\": true", "\"required\": false").replace("\"defaultRequire\": 1", "\"defaultRequire\": 0")

/**
 * Forge and NeoForge bring MixinExtras, which reads @Redirect(at = ...) as one annotation; the Mixin build Lumora compiles
 * against declares it as an array (At[]), so its class files hold a one-element list. Mixin reads both forms; this
 * rewrites the list to the single value in the jar's mixin classes.
 */
/** Forge's Mixin builds do not know the newer compatibility levels (JAVA_21 in 1.20.6, JAVA_25 in 26.x); JAVA_17 is enough for Lumora's mixins. */
fun forgeMixins(line: String) = lenientMixins(line).replace(Regex("\"compatibilityLevel\": \"JAVA_[0-9]+\""), "\"compatibilityLevel\": \"JAVA_17\"")

fun singleRedirectAt(jar: File) {
    val uri = java.net.URI.create("jar:" + jar.toURI())
    java.nio.file.FileSystems.newFileSystem(uri, emptyMap<String, Any>()).use { fs ->
        val root = fs.getPath("/dev/lumora/client/mixin")
        if (!java.nio.file.Files.isDirectory(root)) return
        java.nio.file.Files.walk(root).use { paths ->
            for (p in paths.filter { it.toString().endsWith(".class") }.toList()) {
                val node = org.objectweb.asm.tree.ClassNode()
                org.objectweb.asm.ClassReader(java.nio.file.Files.readAllBytes(p)).accept(node, 0)
                var changed = false
                for (m in node.methods.orEmpty()) {
                    for (a in m.visibleAnnotations.orEmpty()) {
                        if (a.desc != "Lorg/spongepowered/asm/mixin/injection/Redirect;" || a.values == null) continue
                        var i = 0
                        while (i + 1 < a.values.size) {
                            val v = a.values[i + 1]
                            if (a.values[i] == "at" && v is List<*> && v.size == 1) {
                                a.values[i + 1] = v[0]
                                changed = true
                            }
                            i += 2
                        }
                    }
                }
                if (changed) {
                    val writer = org.objectweb.asm.ClassWriter(0)
                    node.accept(writer)
                    java.nio.file.Files.write(p, writer.toByteArray())
                }
            }
        }
    }
}

fun modsToml(version: String, mc: String, neo: Boolean) = """
    |modLoader = "javafml"
    |loaderVersion = "[1,)"
    |license = "GPL-3.0-only"
    |
    |[[mods]]
    |modId = "lumora"
    |version = "$version"
    |displayName = "Lumora Client"
    |description = '''The Lumora Client next to other ${if (neo) "NeoForge" else "Forge"} mods.'''
    |${if (neo) "\n[[mixins]]\nconfig = \"lumora.mixins.json\"\n" else ""}
    |[[dependencies.lumora]]
    |modId = "minecraft"
    |${if (neo) "type = \"required\"" else "mandatory = true"}
    |versionRange = "[$mc]"
    |ordering = "NONE"
    |side = "CLIENT"
    |""".trimMargin()

for (mc in lumoraFabricVersions.union(lumoraForgeVersions)) {
    project(":client-mc$mc") {
        val bridge = configurations.create("lumoraBridge") {
            isTransitive = false
            isCanBeConsumed = false
        }
        val loaderJar = configurations.create("lumoraLoaderApi") {
            isTransitive = false
            isCanBeConsumed = false
        }
        val fabricElements = configurations.create("fabricElements") {
            isCanBeConsumed = true
            isCanBeResolved = false
        }
        val forgeElements = configurations.create("forgeElements") {
            isCanBeConsumed = true
            isCanBeResolved = false
        }
        dependencies {
            add(bridge.name, project(":lumora-bridge"))
            add(loaderJar.name, project(":lumora-loader"))
        }
        afterEvaluate {
            val jarTask = tasks.named<Jar>("jar")
            val version = project.version.toString()

            fun org.gradle.api.tasks.bundling.AbstractArchiveTask.addBridge(pkg: String) {
                from(bridge.elements.map { files -> files.map { zipTree(it) } }) {
                    include("dev/lumora/bridge/**", "dev/lumora/$pkg/**")
                }
                from(loaderJar.elements.map { files -> files.map { zipTree(it) } }) {
                    include("dev/lumora/loader/api/**")
                }
            }

            if (mc in lumoraFabricVersions) {
                val source: TaskProvider<out org.gradle.api.tasks.bundling.AbstractArchiveTask> =
                    if (plugins.hasPlugin("net.fabricmc.fabric-loom-remap")) {
                        tasks.register<net.fabricmc.loom.task.RemapJarTask>("remapFabricJar") {
                            inputFile.set(jarTask.flatMap { it.archiveFile })
                            sourceNamespace.set("named")
                            targetNamespace.set("intermediary")
                            archiveFileName.set("lumora-client-mc$mc-intermediary.jar")
                            destinationDirectory.set(layout.buildDirectory.dir("fabric"))
                        }
                    } else {
                        jarTask
                    }
                val meta = layout.buildDirectory.file("fabric/fabric.mod.json")
                val writeMeta = tasks.register("fabricModJson") {
                    inputs.property("version", version)
                    outputs.file(meta)
                    doLast { meta.get().asFile.writeText(fabricModJson(version, mc)) }
                }
                val fabricJar = tasks.register<Jar>("fabricJar") {
                    archiveFileName.set("lumora-client-mc$mc-fabric.jar")
                    inputs.property("mixinConfig", "lenient-v1") // lumora.mixins.json is rewritten (lenientMixins)
                    destinationDirectory.set(layout.buildDirectory.dir("libs"))
                    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                    dependsOn(source, writeMeta, bridge, loaderJar)
                    from(source.flatMap { it.archiveFile }.map { zipTree(it) }) {
                        exclude("META-INF/MANIFEST.MF")
                        filesMatching("lumora.mixins.json") { filter { lenientMixins(it) } }
                    }
                    addBridge("fabric")
                    from(meta)
                }
                fabricElements.outgoing.artifact(fabricJar)
                tasks.named("assemble") { dependsOn(fabricJar) }
            }

            if (mc in lumoraForgeVersions) {
                val metaDir = layout.buildDirectory.dir("forge/META-INF")
                val writeMeta = tasks.register("forgeModsToml") {
                    inputs.property("version", version)
                    outputs.dir(metaDir)
                    doLast {
                        val dir = metaDir.get().asFile
                        dir.mkdirs()
                        dir.resolve("mods.toml").writeText(modsToml(version, mc, false))
                        dir.resolve("neoforge.mods.toml").writeText(modsToml(version, mc, true))
                    }
                }
                val forgeJar = tasks.register<Jar>("forgeJar") {
                    archiveFileName.set("lumora-client-mc$mc-forge.jar")
                    inputs.property("mixinConfig", "forge-v2") // lumora.mixins.json is rewritten (forgeMixins)
                    destinationDirectory.set(layout.buildDirectory.dir("libs"))
                    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                    dependsOn(jarTask, writeMeta, bridge, loaderJar)
                    from(jarTask.flatMap { it.archiveFile }.map { zipTree(it) }) {
                        exclude("META-INF/MANIFEST.MF")
                        filesMatching("lumora.mixins.json") { filter { forgeMixins(it) } }
                    }
                    addBridge("forge")
                    from(metaDir) { into("META-INF") }
                    manifest {
                        // Forge reads the mixin configuration from the manifest, NeoForge from neoforge.mods.toml
                        attributes("MixinConfigs" to "lumora.mixins.json", "Automatic-Module-Name" to "lumora")
                    }
                    inputs.property("redirectAt", "single-v1")
                    doLast { singleRedirectAt(archiveFile.get().asFile) }
                }
                forgeElements.outgoing.artifact(forgeJar)
                tasks.named("assemble") { dependsOn(forgeJar) }
            }
        }
    }
}
