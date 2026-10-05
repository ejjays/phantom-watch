import java.util.Properties

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The TMDB key lives in local.properties and must never be committed, so it is baked into a
// generated source file at build time instead of being written into the repo.
val props: Properties = run {
    val f = rootProject.file("local.properties")
    if (!f.exists()) Properties() else Properties().apply { f.inputStream().use { load(it) } }
}
val tmdbKey: String = props.getProperty("TMDB_API_KEY", "")
val pwUrl: String = props.getProperty("PW_URL", "")
val pwKey: String = props.getProperty("PW_KEY", "")
val genKeyDir = layout.buildDirectory.dir("generated/key")
val genKey = tasks.register("genKey") {
    val key = tmdbKey
    val url = pwUrl
    val pw = pwKey
    val out = genKeyDir.get().file("dev/phantomwatch/spike/Key.kt").asFile
    outputs.file(out)
    doLast {
        out.parentFile.mkdirs()
        out.writeText(
            "package dev.phantomwatch.spike\n\n" +
                "const val TMDB_KEY = \"$key\"\n" +
                "const val PW_URL = \"$url\"\n" +
                "const val PW_KEY = \"$pw\"\n"
        )
    }
}

kotlin {
    wasmJs {
        browser()
        binaries.executable()
    }
    sourceSets {
        commonMain {
            kotlin.srcDir(genKeyDir)
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation("io.coil-kt.coil3:coil-compose:3.6.3")
                implementation(project(":shared"))
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn(genKey)
}
