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
