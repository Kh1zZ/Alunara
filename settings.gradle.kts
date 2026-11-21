@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google {
            content {
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("androidx")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        exclusiveContent {
            forRepository {
                maven {
                    name = "JitPack"
                    setUrl("https://jitpack.io")
                }
            }
            filter {
                includeGroup("com.github.TeamNewPipe")
            }
        }
    }
}

rootProject.name = "Alunara"

include(":app")
include(":core:domain")
include(":core:music")
include(":core:lyrics")
include(":core:data")

include(":third_party:core")
project(":third_party:core").projectDir = file("third_party/core")

include(":third_party:lyrics:lrclib")
project(":third_party:lyrics:lrclib").projectDir = file("third_party/lyrics/lrclib")

include(":third_party:lyrics:simpmusic")
project(":third_party:lyrics:simpmusic").projectDir = file("third_party/lyrics/simpmusic")

include(":third_party:lyrics:kugou")
project(":third_party:lyrics:kugou").projectDir = file("third_party/lyrics/kugou")
