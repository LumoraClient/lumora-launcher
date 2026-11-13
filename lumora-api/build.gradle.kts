// Public Lumora client API: interfaces, events, settings models and the HudWidget base class.
// No dependencies and no Minecraft classes, so addons compiled against it work on every supported version.
plugins {
    `java-library`
    `maven-publish`
}

// The artifact carries the API version (LumoraAPI.API_VERSION), not the client version.
version = "1.7.0"

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    // Minecraft 1.21.x runs on Java 21; the API must load there too.
    options.release.set(21)
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addStringOption("Xdoclint:none", "-quiet")
    }
}

tasks.jar {
    from(rootProject.file("LICENSE")) { rename { "LICENSE_lumora" } }
}

publishing {
    publications {
        create<MavenPublication>("api") {
            artifactId = "lumora-api"
            from(components["java"])
        }
    }
    repositories {
        // Repository inside the project; external addons (e.g. LabyVoice) reference it with a relative path.
        maven {
            name = "lumoraLocal"
            url = rootProject.layout.buildDirectory.dir("repo").get().asFile.toURI()
        }
    }
}
