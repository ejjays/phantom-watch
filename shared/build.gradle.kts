val compileSdkVersion = project.findProperty("compileSdk")?.toString()?.toIntOrNull() ?: 37

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
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
}
