import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

import java.security.MessageDigest

plugins {
    application
}

dependencies {
    implementation(project(":launcher-core"))
    implementation(libs.flatlaf)
    implementation(libs.flatlaf.inter)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

application {
    mainClass.set("dev.lumora.launcher.app.Main")
}

val appName = "LumoraLauncher"
val bundledMod: Configuration = configurations.create("bundledMod") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
dependencies {
    // Our own companion mod (one build per Minecraft version) is the only mod shipped inside the distribution
    // (GPL-3.0, own code).
    @Suppress("UNCHECKED_CAST")
    for (v in gradle.extra["lumoraClientVersions"] as List<String>) {
        bundledMod(project(path = ":client-mc$v", configuration = "runtimeElements")) { isTransitive = false }
    }
}

fun sha(file: File, algorithm: String): String =
    MessageDigest.getInstance(algorithm).digest(file.readBytes()).joinToString("") { "%02x".format(it) }

/** build/bundled: companion mod jar + bundled-mods.json (file name -> SHA-512). */
val stageBundled = tasks.register("stageBundled") {
    val out = layout.buildDirectory.dir("bundled")
    inputs.files(bundledMod)
    outputs.dir(out)
    doLast {
        val dir = out.get().asFile
        project.delete(dir)
        dir.mkdirs()
        val entries = mutableListOf<String>()
        bundledMod.files.filter { it.name.endsWith(".jar") }.forEach { jar ->
            val target = dir.resolve(jar.name)
            jar.copyTo(target, overwrite = true)
            entries += "  \"${jar.name}\": \"${sha(target, "SHA-512")}\""
        }
        @Suppress("UNCHECKED_CAST")
        val expected = (gradle.extra["lumoraClientVersions"] as List<String>).map { "lumora-client-mc$it.jar" }
        val missing = expected.filter { name -> bundledMod.files.none { it.name == name } }
        check(missing.isEmpty()) { "companion mod jars missing: $missing" }
        dir.resolve("bundled-mods.json").writeText("{\n" + entries.joinToString(",\n") + "\n}\n")
    }
}

tasks.named<JavaExec>("run") {
    dependsOn(stageBundled)
    systemProperty("lumora.bundledDir", layout.buildDirectory.dir("bundled").get().asFile.absolutePath)
}

// ------------------------------------------------------------------------------------------------
// Windows packaging with jpackage (JDK tool). Produces a native LumoraLauncher.exe with a bundled,
// jlink-trimmed Java runtime, so end users need no Java installation for the launcher itself.
// ------------------------------------------------------------------------------------------------

val jdkHome = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) }
    .map { it.metadata.installationPath.asFile }

val jpackageInput = tasks.register<Sync>("jpackageInput") {
    from(tasks.jar)
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("jpackage/input"))
}

val jpackageImage = tasks.register<Exec>("jpackageImage") {
    group = "distribution"
    description = "Builds build/jpackage/image/LumoraLauncher (exe + runtime) with jpackage"
    dependsOn(jpackageInput, stageBundled)
    val image = layout.buildDirectory.dir("jpackage/image")
    val input = layout.buildDirectory.dir("jpackage/input")
    val bundled = layout.buildDirectory.dir("bundled")
    val icon = rootProject.file("packaging/icons/lumora.ico")
    val cliProps = rootProject.file("packaging/windows/cli-launcher.properties")
    inputs.dir(input)
    inputs.dir(bundled)
    inputs.files(icon, cliProps)
    outputs.dir(image)
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isWindows }
    doFirst { project.delete(image) }
    val ver = project.version.toString().substringBefore('-')
    executable = jdkHome.get().resolve("bin/jpackage.exe").absolutePath
    args(
        "--type", "app-image",
        "--name", appName,
        "--app-version", ver,
        "--vendor", "Lumora Client contributors",
        "--description", "Lumora Client Launcher",
        "--input", input.get().asFile.absolutePath,
        "--main-jar", tasks.jar.get().archiveFileName.get(),
        "--main-class", application.mainClass.get(),
        "--icon", icon.absolutePath,
        "--app-content", bundled.get().asFile.absolutePath,
        "--java-options", "-Dlumora.bundledDir=\$APPDIR\\..\\bundled",
        "--java-options", "-Dfile.encoding=UTF-8",
        "--java-options", "-Xmx512m",
        "--java-options", "--enable-native-access=ALL-UNNAMED",
        "--add-modules", "java.base,java.desktop,java.logging,java.management,jdk.management,java.net.http,jdk.httpserver,jdk.unsupported,jdk.charsets,java.naming,jdk.naming.dns,jdk.accessibility",
        "--jlink-options", "--strip-debug --no-man-pages --no-header-files",
        "--add-launcher", "LumoraLauncherCli=${cliProps.absolutePath}",
        "--dest", image.get().asFile.absolutePath
    )
}

val releaseDir = rootProject.layout.buildDirectory.dir("release")
val installerDir = layout.buildDirectory.dir("installer")

/** Output of :lumora-social-server:jar (fixed file name, see lumora-social-server/build.gradle.kts). */
val socialServerJar = rootProject.layout.projectDirectory.file("lumora-social-server/build/libs/lumora-social-server.jar")

val releaseDist = tasks.register("releaseDist") {
    group = "distribution"
    description = "Assembles build/release: app folder, Lumora Social server, zip, README.txt, licenses, SHA256SUMS.txt"
    dependsOn(jpackageImage, ":lumora-social-server:jar")
    inputs.file(socialServerJar)
    val image = layout.buildDirectory.dir("jpackage/image/$appName")
    inputs.dir(image)
    inputs.files(rootProject.file("LICENSE"), rootProject.file("docs/THIRD_PARTY_NOTICES.md"),
        rootProject.file("packaging/windows/README.txt"), rootProject.fileTree("packaging/licenses"))
    inputs.files(installerDir).optional()
    inputs.files(layout.buildDirectory.dir("portable")).optional()
    outputs.dir(releaseDir)
    doLast {
        val rel = releaseDir.get().asFile
        project.delete(rel)
        rel.mkdirs()
        val appDir = rel.resolve(appName)
        image.get().asFile.copyRecursively(appDir, overwrite = true)
        val licenses = appDir.resolve("licenses")
        licenses.mkdirs()
        rootProject.file("LICENSE").copyTo(licenses.resolve("LICENSE-GPL-3.0.txt"), true)
        rootProject.file("packaging/licenses").copyRecursively(licenses, true)
        rootProject.file("docs/THIRD_PARTY_NOTICES.md").copyTo(licenses.resolve("THIRD_PARTY_NOTICES.txt"), true)
        rootProject.file("packaging/windows/README.txt").copyTo(appDir.resolve("README.txt"), true)
        rootProject.file("packaging/windows/README.txt").copyTo(rel.resolve("README.txt"), true)
        rel.resolve("licenses").mkdirs()
        licenses.copyRecursively(rel.resolve("licenses"), true)

        // Lumora Social server for whoever hosts friends/chat (uses the launcher's Java runtime when next to it)
        val social = rel.resolve("LumoraSocialServer")
        social.mkdirs()
        socialServerJar.asFile.copyTo(social.resolve("lumora-social-server.jar"), true)
        rootProject.file("packaging/social/start-server.bat").copyTo(social.resolve("start-server.bat"), true)
        rootProject.file("docs/SOCIAL.md").copyTo(social.resolve("SOCIAL.md"), true)

        val zip = rel.resolve("LumoraClient-${project.version}-windows-x64.zip")
        ant.withGroovyBuilder {
            "zip"("destfile" to zip.absolutePath, "basedir" to rel.absolutePath, "includes" to "$appName/**,LumoraSocialServer/**")
        }
        val builtInstaller = installerDir.get().asFile.resolve("LumoraClient-Setup-${project.version}.exe")
        val installer = rel.resolve(builtInstaller.name)
        if (builtInstaller.exists()) {
            builtInstaller.copyTo(installer, true)
        }
        val builtPortable = layout.buildDirectory.file("portable/LumoraClient.exe").get().asFile
        val portable = rel.resolve("LumoraClient.exe")
        if (builtPortable.exists()) {
            builtPortable.copyTo(portable, true)
        }
        val sums = listOfNotNull(portable.takeIf { it.exists() }, appDir.resolve("$appName.exe"),
            appDir.resolve("LumoraLauncherCli.exe"), zip, installer.takeIf { it.exists() },
            social.resolve("lumora-social-server.jar"))
            .joinToString("") { f -> "${sha(f, "SHA-256")}  ${f.relativeTo(rel).invariantSeparatorsPath}\n" }
        rel.resolve("SHA256SUMS.txt").writeText(sums)
        // signature for the launcher self-update (packaging/SignRelease.java); the private key is never in the repository
        val keyFile = file((findProperty("lumoraSigningKey") as String?) ?: System.getenv("LUMORA_SIGNING_KEY")
            ?: (System.getProperty("user.home") + "/.lumora-signing/release-ed25519.key"))
        if (keyFile.exists()) {
            val der = Base64.getDecoder().decode(keyFile.readText().trim())
            val key = KeyFactory.getInstance("Ed25519").generatePrivate(PKCS8EncodedKeySpec(der))
            val s = Signature.getInstance("Ed25519")
            s.initSign(key)
            s.update(rel.resolve("SHA256SUMS.txt").readBytes())
            rel.resolve("SHA256SUMS.txt.sig").writeText(Base64.getEncoder().encodeToString(s.sign()) + "\n")
            println("SHA256SUMS.txt signed")
        } else {
            println("No signing key: SHA256SUMS.txt.sig not written (self-update needs it)")
        }
        println("Release written to $rel")
    }
}

/** Optional: Windows installer via Inno Setup (ISCC.exe must be installed; path via -PisccPath or ISCC env). */
val installerExe = tasks.register<Exec>("installerExe") {
    group = "distribution"
    description = "Builds LumoraClient-Setup-<version>.exe with Inno Setup"
    dependsOn(jpackageImage)
    val iscc = (findProperty("isccPath") as String?) ?: System.getenv("ISCC")
        ?: "C:\\Program Files (x86)\\Inno Setup 6\\ISCC.exe"
    onlyIf { file(iscc).exists() }
    executable = iscc
    args(
        "/DAppVersion=${project.version}",
        "/DSourceDir=${layout.buildDirectory.dir("jpackage/image/$appName").get().asFile.absolutePath}",
        "/DRepoRoot=${rootProject.projectDir.absolutePath}",
        "/O${installerDir.get().asFile.absolutePath}",
        rootProject.file("packaging/windows/LumoraClient.iss").absolutePath
    )
}

/**
 * Single-file installer without extra tools: Windows' built-in IExpress wraps the app image (as zip) and
 * packaging/windows/sfx/install.cmd into LumoraClient-Setup-<version>.exe (per-user install, no admin).
 * Staged in %TEMP% because IExpress/makecab cannot handle paths longer than 260 characters.
 */
val setupExe = tasks.register("setupExe") {
    group = "distribution"
    description = "Builds LumoraClient-Setup-<version>.exe with the Windows-builtin IExpress"
    dependsOn(jpackageImage)
    val image = layout.buildDirectory.dir("jpackage/image/$appName")
    val sfx = rootProject.file("packaging/windows/sfx")
    inputs.dir(image)
    inputs.dir(sfx)
    val outFile = installerDir.map { it.file("LumoraClient-Setup-${project.version}.exe") }
    outputs.file(outFile)
    val iexpress = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32\\iexpress.exe")
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isWindows && iexpress.exists() }
    doLast {
        val stage = File(System.getenv("TEMP"), "lumora-sfx-${project.version}")
        project.delete(stage)
        stage.mkdirs()
        ant.withGroovyBuilder {
            "zip"("destfile" to stage.resolve("LumoraClient.zip").absolutePath,
                "basedir" to image.get().asFile.parentFile.absolutePath, "includes" to "$appName/**")
        }
        sfx.resolve("install.cmd").copyTo(stage.resolve("install.cmd"), true)
        sfx.resolve("uninstall.cmd").copyTo(stage.resolve("uninstall.cmd"), true)
        val target = stage.resolve("LumoraClient-Setup.exe")
        val sed = """
            [Version]
            Class=IEXPRESS
            SEDVersion=3
            [Options]
            PackagePurpose=InstallApp
            ShowInstallProgramWindow=0
            HideExtractAnimation=0
            UseLongFileName=1
            InsideCompressed=0
            CAB_FixedSize=0
            CAB_ResvCodeSigning=0
            RebootMode=N
            InstallPrompt=%InstallPrompt%
            DisplayLicense=%DisplayLicense%
            FinishMessage=%FinishMessage%
            TargetName=%TargetName%
            FriendlyName=%FriendlyName%
            AppLaunched=%AppLaunched%
            PostInstallCmd=%PostInstallCmd%
            AdminQuietInstCmd=%AdminQuietInstCmd%
            UserQuietInstCmd=%UserQuietInstCmd%
            SourceFiles=SourceFiles
            [Strings]
            InstallPrompt=Lumora Client ${project.version} jetzt installieren?
            DisplayLicense=
            FinishMessage=
            TargetName=${target.absolutePath}
            FriendlyName=Lumora Client Setup
            AppLaunched=cmd /c install.cmd
            PostInstallCmd=<None>
            AdminQuietInstCmd=
            UserQuietInstCmd=
            FILE0="install.cmd"
            FILE1="uninstall.cmd"
            FILE2="LumoraClient.zip"
            [SourceFiles]
            SourceFiles0=${stage.absolutePath}\
            [SourceFiles0]
            %FILE0%=
            %FILE1%=
            %FILE2%=
        """.trimIndent().replace("\n", "\r\n")
        val sedFile = stage.resolve("setup.sed")
        sedFile.writeText(sed)
        val proc = ProcessBuilder(iexpress.absolutePath, "/N", "/Q", sedFile.absolutePath)
            .directory(stage).inheritIO().start()
        check(proc.waitFor() == 0 || target.exists()) { "IExpress failed" }
        check(target.exists() && target.length() > 1_000_000) { "IExpress produced no installer: $target" }
        val out = outFile.get().asFile
        out.parentFile.mkdirs()
        target.copyTo(out, true)
        project.delete(stage)
        println("Installer: $out (${out.length() / 1048576} MB)")
    }
}

/**
 * Portable single-file LumoraClient.exe: a small C# starter (compiled with the csc.exe of the .NET Framework 4.8
 * that ships with Windows) with the whole app image embedded as a resource. Double-click -> first start
 * extracts to %LOCALAPPDATA%\LumoraClient\portable\<hash> (SHA-256 verified) -> runs LumoraLauncher.exe.
 */
val portableExe = tasks.register("portableExe") {
    group = "distribution"
    description = "Builds the portable single-file LumoraClient.exe"
    dependsOn(jpackageImage)
    val image = layout.buildDirectory.dir("jpackage/image/$appName")
    val source = rootProject.file("packaging/windows/portable/PortableLauncher.cs")
    val icon = rootProject.file("packaging/icons/lumora.ico")
    inputs.dir(image)
    inputs.files(source, icon)
    val outFile = layout.buildDirectory.file("portable/LumoraClient.exe")
    outputs.file(outFile)
    val winDir = System.getenv("SystemRoot") ?: "C:\\Windows"
    val csc = File(winDir, "Microsoft.NET\\Framework64\\v4.0.30319\\csc.exe")
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isWindows && csc.exists() }
    doLast {
        // csc from .NET Framework is not long-path aware: stage in %TEMP%
        val stage = File(System.getenv("TEMP"), "lumora-portable-${project.version}")
        project.delete(stage)
        stage.mkdirs()
        val zip = stage.resolve("app.zip")
        ant.withGroovyBuilder {
            "zip"("destfile" to zip.absolutePath, "basedir" to image.get().asFile.parentFile.absolutePath,
                "includes" to "$appName/**")
        }
        stage.resolve("version.txt").writeText(sha(zip, "SHA-256"))
        source.copyTo(stage.resolve("PortableLauncher.cs"), true)
        icon.copyTo(stage.resolve("lumora.ico"), true)
        val fw = csc.parentFile
        val proc = ProcessBuilder(csc.absolutePath, "/nologo", "/target:winexe", "/optimize+", "/platform:anycpu",
            "/out:LumoraClient.exe", "/win32icon:lumora.ico",
            "/resource:app.zip,app.zip", "/resource:version.txt,version.txt",
            "/reference:${fw.resolve("System.IO.Compression.dll")}",
            "/reference:${fw.resolve("System.IO.Compression.FileSystem.dll")}",
            "/reference:System.Windows.Forms.dll", "/reference:System.Drawing.dll",
            "PortableLauncher.cs").directory(stage).redirectErrorStream(true).start()
        val log = proc.inputStream.bufferedReader().readText()
        check(proc.waitFor() == 0) { "csc failed:\n$log" }
        val out = outFile.get().asFile
        out.parentFile.mkdirs()
        stage.resolve("LumoraClient.exe").copyTo(out, true)
        project.delete(stage)
        println("Portable EXE: $out (${out.length() / 1048576} MB)")
    }
}

releaseDist.configure { mustRunAfter(installerExe, setupExe, portableExe) }

tasks.jar {
    from(rootProject.file("LICENSE")) { rename { "LICENSE_lumora" } }
}
