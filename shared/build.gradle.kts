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
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "dev.phantomwatch.res"
}
