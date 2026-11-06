import java.io.FileInputStream
import java.util.Properties

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}

// CI builds against the last published platform; the phone uses the hand-made 37
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
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.gitlab.arturbosch.detekt")
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
        buildConfigField("String", "GROQ_API_KEY", "\"${localProps.getProperty("GROQ_API_KEY", "")}\"")
        buildConfigField("String", "DEEPGRAM_API_KEY", "\"${localProps.getProperty("DEEPGRAM_API_KEY", "")}\"")
        buildConfigField("String", "SEEDBOX_URL", "\"${localProps.getProperty("SEEDBOX_URL", "")}\"")
        buildConfigField("String", "SEEDBOX_USER", "\"${localProps.getProperty("SEEDBOX_USER", "")}\"")
        buildConfigField("String", "SEEDBOX_PASS", "\"${localProps.getProperty("SEEDBOX_PASS", "")}\"")
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
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.packaging.jniLibs.excludes.add("lib/armeabi-v7a/**")
    }
}

dependencies {
    implementation(project(":shared"))
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.frostwire:jlibtorrent:2.0.12.9")
    implementation("com.frostwire:jlibtorrent-android-arm64:2.0.12.9")
    implementation("com.frostwire:jlibtorrent-android-arm:2.0.12.9")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
    implementation("com.google.android.gms:play-services-media-effect-enhancement:16.0.0-beta09")
    implementation("com.airbnb.android:lottie-compose:6.7.1")
    implementation("io.github.kyant0:backdrop:1.0.6")
    implementation("io.github.kyant0:shapes:1.2.0")
    testImplementation("junit:junit:4.13.2")
    // real org.json on the unit-test classpath; android.jar only ships stubs
    testImplementation("org.json:json:20260814")
}
