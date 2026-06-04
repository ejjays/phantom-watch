import java.io.FileInputStream
import java.util.Properties

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.phantomwatch"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.phantomwatch"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "TMDB_API_KEY", "\"${localProps.getProperty("TMDB_API_KEY", "")}\"")
        buildConfigField("String", "OS_API_KEY", "\"${localProps.getProperty("OS_API_KEY", "")}\"")
        buildConfigField("String", "SUPABASE_URL", "\"${localProps.getProperty("SUPABASE_URL", "")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProps.getProperty("SUPABASE_ANON_KEY", "")}\"")
        buildConfigField("String", "GROQ_API_KEY", "\"${localProps.getProperty("GROQ_API_KEY", "")}\"")
        buildConfigField("String", "SEEDBOX_URL", "\"${localProps.getProperty("SEEDBOX_URL", "")}\"")
        buildConfigField("String", "SEEDBOX_USER", "\"${localProps.getProperty("SEEDBOX_USER", "")}\"")
        buildConfigField("String", "SEEDBOX_PASS", "\"${localProps.getProperty("SEEDBOX_PASS", "")}\"")
        buildConfigField("String", "CFG_URL", "\"${localProps.getProperty("CFG_URL", "")}\"")
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // debug keystore on purpose: sideload builds must install over the existing app
    signingConfigs {
        create("sideload") {
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.getByName("sideload")
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

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.packaging.jniLibs.excludes.add("lib/armeabi-v7a/**")
    }
}

dependencies {
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
    // JitPack: release tags fail to build; snapshot publishes repo-level artifact, not the reicon-compose module
    implementation("com.github.dqev:reicon:main-SNAPSHOT")
    testImplementation("junit:junit:4.13.2")
}
