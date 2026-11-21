import java.io.FileInputStream
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}

// CI builds against last published platform, phone uses hand made 37
val compileSdkVersion = (project.findProperty("compileSdk")?.toString()?.toIntOrNull() ?: 37)
val targetSdkVersion = (project.findProperty("targetSdk")?.toString()?.toIntOrNull() ?: 37)
val releaseStorePath = System.getenv("PW_RELEASE_KEYSTORE").orEmpty()
val releaseStorePassword = System.getenv("PW_RELEASE_STORE_PASSWORD").orEmpty()
val releaseKeyAlias = System.getenv("PW_RELEASE_KEY_ALIAS").orEmpty()
val releaseKeyPassword = System.getenv("PW_RELEASE_KEY_PASSWORD").orEmpty()
val releaseCertSha256 = System.getenv("PW_RELEASE_CERT_SHA256").orEmpty().replace(":", "").lowercase()
val testReleaseSigning = providers.gradleProperty("testReleaseSigning").orNull == "true"
val releaseStore = when {
    releaseStorePath.isNotBlank() -> file(releaseStorePath)
    testReleaseSigning -> file("${layout.buildDirectory.get()}/test-release.jks")
    else -> null
}
val releaseStorePass = releaseStorePassword.ifBlank { if (testReleaseSigning) "android" else "" }
val releaseAlias = releaseKeyAlias.ifBlank { if (testReleaseSigning) "test" else "" }
val releasePass = releaseKeyPassword.ifBlank { if (testReleaseSigning) "android" else "" }
val releaseCert = releaseCertSha256.ifBlank { if (testReleaseSigning) "00".repeat(32) else "" }
val releaseSigningConfigured = releaseStore != null && releaseStorePass.isNotBlank() &&
    releaseAlias.isNotBlank() && releasePass.isNotBlank() && releaseCert.isNotBlank()
val releaseSigningProblem = when {
    !releaseSigningConfigured ->
        "Release signing requires PW_RELEASE_KEYSTORE, PW_RELEASE_STORE_PASSWORD, " +
            "PW_RELEASE_KEY_ALIAS, PW_RELEASE_KEY_PASSWORD, and PW_RELEASE_CERT_SHA256"
    !releaseCert.matches(Regex("[a-f0-9]{64}")) ->
        "PW_RELEASE_CERT_SHA256 must be a SHA-256 certificate fingerprint"
    !testReleaseSigning && releaseStore?.isFile != true ->
        "PW_RELEASE_KEYSTORE must point to an existing private release keystore"
    else -> null
}

fun gitCommitCount(): Int = try {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        workingDir(rootProject.projectDir)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().toIntOrNull() ?: 1
} catch (e: Exception) {
    1
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
    alias(libs.plugins.google.services)
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
    baseline = file("$rootDir/config/detekt/baseline.xml")
    source.setFrom(files("src/main/java", "src/test/java"))
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "21"
    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(false)
        sarif.required.set(false)
    }
}

android {
    namespace = "dev.phantomwatch"
    compileSdk = compileSdkVersion

    defaultConfig {
        applicationId = "dev.phantomwatch"
        minSdk = 26
        targetSdk = targetSdkVersion
        versionCode = gitCommitCount()
        versionName = "1.0"
        buildConfigField("String", "TMDB_API_KEY", "\"${localProps.getProperty("TMDB_API_KEY", "")}\"")
        buildConfigField("String", "OS_API_KEY", "\"${localProps.getProperty("OS_API_KEY", "")}\"")
        buildConfigField("String", "SUPABASE_URL", "\"${localProps.getProperty("SUPABASE_URL", "")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProps.getProperty("SUPABASE_ANON_KEY", "")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${localProps.getProperty("GOOGLE_WEB_CLIENT_ID", "")}\"")
        buildConfigField("String", "GROQ_API_KEY", "\"${localProps.getProperty("GROQ_API_KEY", "")}\"")
        buildConfigField("String", "DEEPGRAM_API_KEY", "\"${localProps.getProperty("DEEPGRAM_API_KEY", "")}\"")
        buildConfigField("String", "CFG_URL", "\"${localProps.getProperty("CFG_URL", "")}\"")
        buildConfigField("String", "TELEMETRY_URL", "\"${localProps.getProperty("TELEMETRY_URL", "")}\"")
        buildConfigField("String", "TELEMETRY_KEY", "\"${localProps.getProperty("TELEMETRY_KEY", "")}\"")
        buildConfigField("String", "UPDATE_CERT_SHA256", "\"\"")
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("production") {
                storeFile = releaseStore
                storePassword = releaseStorePass
                keyAlias = releaseAlias
                keyPassword = releasePass
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("boolean", "LOGUI", "false")
        }
        release {
            if (releaseSigningConfigured) signingConfig = signingConfigs.getByName("production")
            buildConfigField("boolean", "LOGUI", "false")
            buildConfigField("String", "UPDATE_CERT_SHA256", "\"$releaseCert\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// shared owns every string both shells render, android gets generated copy instead of
// second hand maintained definition, app res keeps platform only text
abstract class SyncSharedStrings : DefaultTask() {
    @get:InputFile abstract val source: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun sync() {
        val blocks = Regex("""<(string|plurals)\b[^>]*>.*?</\1>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(source.get().asFile.readText())
            .map { it.value }
            .filter { Regex("""\bname="[^"]+""").containsMatchIn(it) }
            .joinToString("\n")
        val out = outputDir.get().file("values/strings.xml").asFile
        out.parentFile.mkdirs()
        // compose accepts bare apostrophe, aapt2 reads it as quote
        out.writeText("<resources>\n${blocks.replace("'", "\\'")}\n</resources>\n")
    }
}
val syncSharedStrings = tasks.register<SyncSharedStrings>("syncSharedStrings") {
    source.set(rootProject.file("shared/src/commonMain/composeResources/values/strings.xml"))
    outputDir.set(layout.buildDirectory.dir("generated/sharedStrings/res"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

tasks.configureEach {
    if (name == "assembleRelease" || name.startsWith("packageRelease") || name.startsWith("bundleRelease")) {
        val problem = releaseSigningProblem
        if (problem != null) doFirst { error(problem) }
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(syncSharedStrings) { it.outputDir }
    }
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.packaging.jniLibs.excludes.add("lib/armeabi-v7a/**")
    }
}

ksp {
    // api 36 reads v1 metadata, api 37 reads v2
    arg("appfunctions:generateV1Xml", "true")
}

dependencies {
    implementation(project(":shared"))
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.media.effect.enhancement)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.lottie.compose)
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.shapes)
    implementation(libs.appfunctions)
    ksp(libs.appfunctions.compiler)
    testImplementation(libs.junit)
    // real server on loopback so subtitle cache is tested through its own network path
    testImplementation(libs.mockwebserver)
    // real org.json on unit test classpath, android.jar only ships stubs
    testImplementation(libs.json)
}
