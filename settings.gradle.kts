pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://dl.frostwire.com/maven")
            content { includeGroup("com.frostwire") }
        }
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("com.github.dqev") }
        }
    }
}
rootProject.name = "PhantomWatch"
include(":app")
