import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

// tmdb key lives in local.properties and must never be committed, so baked into generated
// source at build time instead of written into repo
val props: Properties = run {
    val f = rootProject.file("local.properties")
    if (!f.exists()) Properties() else Properties().apply { f.inputStream().use { load(it) } }
}
val tmdbKey: String = props.getProperty("TMDB_API_KEY", "")
// PW_URL in local.properties is resolver on this device, published bundle needs public one so
// it reads own key and never picks up dev host by accident
// oracle box public name is dns name resolving to box address so caddy can hold real
// certificate, phantom.work.gd has no dns record so was never reachable
val pwUrl: String = props.getProperty("PW_URL_WEB", "https://158-178-228-161.sslip.io")
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
