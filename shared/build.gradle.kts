val compileSdkVersion = project.findProperty("compileSdk")?.toString()?.toIntOrNull() ?: 37

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidLibrary {
        namespace = "dev.phantomwatch.shared"
        compileSdk = compileSdkVersion
        minSdk = 26
    }
    wasmJs {
        browser()
    }
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation("io.coil-kt.coil3:coil-compose:3.6.3")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
        }
        wasmJsMain.dependencies {
            implementation("io.coil-kt.coil3:coil-network-ktor3:3.6.3")
            implementation("io.ktor:ktor-client-js:3.1.0")
        }
        androidMain.dependencies {
            implementation("com.squareup.okhttp3:okhttp:4.12.0")
            implementation("androidx.activity:activity-compose:1.13.0")
            implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "dev.phantomwatch.res"
}
