import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

// tmdb key lives in local.properties, baked into generated source at build time
val props: Properties = run {
    val f = rootProject.file("local.properties")
    if (!f.exists()) Properties() else Properties().apply { f.inputStream().use { load(it) } }
}
val tmdbKey: String = props.getProperty("TMDB_API_KEY", "")
// PW_RELAY_URL is dev resolver, published bundles must not pick it up
// oracle public name is dns to box address so caddy holds real cert, phantom.work.gd had no dns
val pwUrl: String = props.getProperty("PW_RELAY_URL", "https://phantomwatch-relay.christsonalloso021.workers.dev")
val pwFallback: String = props.getProperty("PW_URL_WEB", "https://158-178-228-161.sslip.io")
val pwKey: String = props.getProperty("PW_KEY", "")
val genKeyDir = layout.buildDirectory.dir("generated/key")
val genKey = tasks.register("genKey") {
    val key = tmdbKey
    val url = pwUrl
    val fallback = pwFallback
    val pw = pwKey
    val out = genKeyDir.get().file("dev/phantomwatch/web/Key.kt").asFile
    // value inputs so a relay url change reruns the task, no file input since runners have none
    inputs.property("tmdbKey", key)
    inputs.property("pwUrl", url)
    inputs.property("pwFallback", fallback)
    inputs.property("pwKey", pw)
    outputs.file(out)
    doLast {
        out.parentFile.mkdirs()
        out.writeText(
            "package dev.phantomwatch.web\n\n" +
                "const val TMDB_KEY = \"$key\"\n" +
                "const val PW_URL = \"$url\"\n" +
                "const val PW_FALLBACK = \"$fallback\"\n" +
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
                implementation(libs.coil.compose)
                implementation(libs.kyant.backdrop)
                implementation(project(":shared"))
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn(genKey)
}
