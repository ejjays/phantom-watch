import java.util.Properties

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The TMDB key lives in local.properties and must never be committed, so it is baked into a
// generated source file at build time instead of being written into the repo.
val tmdbKey: String = run {
    val f = rootProject.file("local.properties")
    if (!f.exists()) "" else Properties().apply { f.inputStream().use { load(it) } }
        .getProperty("TMDB_API_KEY", "")
}
val genKeyDir = layout.buildDirectory.dir("generated/key")
val genKey = tasks.register("genKey") {
    val key = tmdbKey
    val out = genKeyDir.get().file("dev/phantomwatch/spike/Key.kt").asFile
    outputs.file(out)
    doLast {
        out.parentFile.mkdirs()
        out.writeText("package dev.phantomwatch.spike\n\nconst val TMDB_KEY = \"$key\"\n")
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
                implementation(project(":shared"))
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn(genKey)
}
