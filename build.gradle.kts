import java.io.ByteArrayInputStream

plugins {
    java
}

group = "com.doolecg"
version = providers.gradleProperty("modVersion").get()

val hytaleServerVersion = providers.gradleProperty("hytaleServerVersion").get()

// Hytale install (launcher default). Override with -PhytaleHome=<dir containing game/ and jre/>.
val hytaleHome: File = providers.gradleProperty("hytaleHome").map { file(it) }
    .getOrElse(file("${System.getenv("APPDATA")}/Hytale/install/release/package"))
val hytaleGame = File(hytaleHome, "game/latest")
val hytaleJava = File(hytaleHome, "jre/latest/bin/java.exe")
val hytaleUserMods = file("${System.getenv("APPDATA")}/Hytale/UserData/Mods")

repositories {
    mavenCentral()
    maven {
        name = "hytale"
        url = uri("https://maven.hytale.com/release")
    }
}

dependencies {
    compileOnly("com.hypixel.hytale:Server:$hytaleServerVersion")
    testImplementation("com.hypixel.hytale:Server:$hytaleServerVersion")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    val props = mapOf("version" to project.version, "serverVersion" to hytaleServerVersion)
    inputs.properties(props)
    filesMatching("manifest.json") { expand(props) }
}

tasks.jar {
    archiveBaseName = "Techtale"
}

// Mods folder handed to dev servers: holds only this build's jar.
val devMods = layout.buildDirectory.dir("devmods")
val stageDevMods = tasks.register<Sync>("stageDevMods") {
    from(tasks.jar)
    into(devMods)
}

fun JavaExec.hytaleServer(runDir: File, vararg extra: String) {
    dependsOn(stageDevMods)
    setExecutable(hytaleJava.absolutePath)
    classpath = files(File(hytaleGame, "Server/HytaleServer.jar"))
    mainClass = "com.hypixel.hytale.Main"
    workingDir = runDir
    doFirst { runDir.mkdirs() }
    args(
        "--assets", File(hytaleGame, "Assets.zip").absolutePath,
        "--mods", devMods.get().asFile.absolutePath,
        "--disable-sentry",
        *extra,
    )
    standardInput = System.`in`
}

// Server runs that fail if the log reports any problem with this mod.
// (--validate-assets is not used: it skips plugins and vanilla's own instance checks always fail it.)
fun JavaExec.checkedServerRun(runDir: File, logName: String, bootCommands: String, requiredLine: String?, vararg alsoRequired: String) {
    hytaleServer(runDir, "--boot-command", bootCommands)
    standardInput = ByteArrayInputStream(ByteArray(0))
    val logFile = layout.buildDirectory.file(logName).get().asFile
    doFirst {
        delete(runDir)
        runDir.mkdirs()
        val out = logFile.outputStream()
        standardOutput = out
        errorOutput = out
    }
    doLast {
        val lines = logFile.readLines()
        val problems = lines.withIndex().filter { (_, line) ->
            val l = line.lowercase()
            (("warn]" in l || "severe]" in l) && ("techtale" in l || "doolecg" in l)) || "at com.doolecg" in l
        }
        val enabled = lines.any { "Enabled plugin Doolecg:Techtale" in it }
        val required = (requiredLine == null || lines.any { requiredLine in it }) && alsoRequired.all { r -> lines.any { r in it } }
        if (problems.isNotEmpty() || !enabled || !required) {
            val detail = problems.take(40).joinToString("\n") { (i, line) ->
                // include the lines after each hit: codec errors put the useful part there
                (listOf(line) + lines.drop(i + 1).take(4)).joinToString("\n")
            }
            throw GradleException("$name failed (plugin enabled: $enabled, '$requiredLine' found: $required). Log: $logFile\n$detail")
        }
        logger.lifecycle("$name passed.")
    }
}

val smokeTest = tasks.register<JavaExec>("smokeTest") {
    group = "verification"
    description = "Boots the Hytale server with the mod, stops it, and checks the log for mod errors."
    checkedServerRun(file("run/smoke"), "smoke.log", "stop", null)
}

// Builds a generator -> cable -> machine rig in a fresh world and checks it processes ore (see SelfTest.java).
tasks.register<JavaExec>("selfTest") {
    group = "verification"
    description = "Runs /techtale selftest on a fresh server world and fails unless it passes."
    checkedServerRun(file("run/selftest"), "selftest.log", "techtale selftest,stop", "Chemical self-test PASSED", "Self-test PASSED")
}

// Dedicated dev server in run/server with this build's jar.
val runServer = tasks.register<JavaExec>("runServer") {
    group = "hytale"
    description = "Runs a Hytale dev server with the working-tree build of the mod."
    hytaleServer(file("run/server"))
}

// Installs the jar into the game client's global Mods folder for singleplayer testing.
val deployClient = tasks.register<Copy>("deployClient") {
    group = "hytale"
    description = "Copies the mod jar into the Hytale client's Mods folder."
    from(tasks.jar)
    into(hytaleUserMods)
}

// Dev Run: working tree -> client Mods folder + dev server.
tasks.register("runDev") {
    group = "hytale"
    description = "Builds the working tree, installs it in the client and starts the dev server."
    dependsOn(deployClient, runServer)
}

// Main Run: exports the code committed on main to build/main-run and runs its dev server there.
val exportMain = tasks.register<Exec>("exportMain") {
    group = "hytale"
    val out = layout.buildDirectory.dir("main-run").get().asFile
    doFirst {
        delete(out)
        out.mkdirs()
    }
    commandLine("cmd", "/c", "git archive main | tar -x -C \"${out.absolutePath}\"")
}

tasks.register<Exec>("runMain") {
    group = "hytale"
    description = "Builds and runs the code committed on main (exported to build/main-run)."
    dependsOn(exportMain)
    val out = layout.buildDirectory.dir("main-run").get().asFile
    workingDir = out
    commandLine("cmd", "/c", "gradlew.bat", "runServer", "--console=plain")
    standardInput = System.`in`
}
