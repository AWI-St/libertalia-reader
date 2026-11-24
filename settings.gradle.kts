// Dónde buscar las herramientas y librerías, y qué módulos tiene el proyecto.
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
        // JitPack solo para el motor de PDF que usa Readium (PdfiumAndroid y AndroidPdfViewer).
        maven("https://jitpack.io") {
            content { includeGroup("com.github.marain87") }
        }
    }
}

rootProject.name = "LibertaliaReader"
include(":app")
