pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "FocusModePlus"

include(":app")

// Pure Kotlin/JVM: domain models, business rules, shared fakes.
include(":core:model")
include(":core:domain")
include(":core:testing")

// Android infrastructure owners.
include(":core:data")
include(":core:network")
include(":core:designsystem")
include(":core:notifications")
include(":core:protection")
include(":core:session")
include(":core:reminders")
// v21: the only module that talks to Firebase (Auth, Firestore, App Check) and Credential Manager.
include(":core:account")

// Features: each owns UI state, ViewModel, actions, navigation contract and UI.
include(":feature:home")
include(":feature:quickfocus")
include(":feature:focus")
include(":feature:result")
include(":feature:history")
include(":feature:missions")
include(":feature:reminders")
include(":feature:settings")
