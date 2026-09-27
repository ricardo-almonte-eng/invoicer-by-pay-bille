rootProject.name = "InvoicerByPayBille"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

// composeApp: TODO el código compartido (lógica + UI).
// androidApp: solo la Activity y la Application. Con AGP 9 la app Android y la
// librería KMP tienen que ser módulos distintos.
include(":composeApp")
include(":androidApp")
