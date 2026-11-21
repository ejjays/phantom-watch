val compileSdkVersion = project.findProperty("compileSdk")?.toString()?.toIntOrNull() ?: 37

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    androidLibrary {
        namespace = "dev.phantomwatch.shared"
        compileSdk = compileSdkVersion
        minSdk = 26
        // compose resources ship as assets, AGP 9 KMP library drops them without this
        androidResources.enable = true
        withHostTest {}
    }
    wasmJs {
        browser()
        binaries.executable()
    }
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.coil.compose)
            implementation(libs.kyant.backdrop)
            implementation(libs.kyant.shapes)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        wasmJsMain.dependencies {
            implementation(libs.coil.network.ktor3)
            implementation(libs.ktor.client.js)
        }
        androidMain.dependencies {
            implementation(libs.okhttp)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.lottie.compose)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "dev.phantomwatch.res"
}
