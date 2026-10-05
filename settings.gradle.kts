pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    // the Kotlin/JS node + yarn downloaders add their own distribution repo at project level,
    // and PREFER_SETTINGS would make Gradle ignore it
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://dl.frostwire.com/maven")
            content { includeGroup("com.frostwire") }
        }
    }
}
rootProject.name = "PhantomWatch"
include(":app")
include(":spike")
include(":shared")
