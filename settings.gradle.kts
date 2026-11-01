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
    }
}

rootProject.name = "ct4nk3r-games"

// Project names are unique (not ":solitaire:app" / ":mahjong:app") so Gradle never confuses two modules called "app".
fun module(path: String, dir: String) {
    include(path)
    project(path).projectDir = file(dir)
}

module(":core-common", "core/common")
module(":core-data", "core/data")
module(":core-ui", "core/ui")
module(":solitaire-engine", "solitaire/engine")
module(":solitaire-app", "solitaire/app")
module(":mahjong-engine", "mahjong/engine")
module(":mahjong-app", "mahjong/app")
