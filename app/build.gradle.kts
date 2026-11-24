// Configuración del módulo de la app: nombre, versiones de Android y librerías.
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/** Los datos de la firma definitiva (vacío si aún no existe keystore.properties). */
val firma = Properties().apply {
    val archivo = rootProject.file("keystore.properties")
    if (archivo.exists()) archivo.inputStream().use { load(it) }
}

android {
    namespace = "org.libertalia.reader"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "org.libertalia.reader"
        // Android 8.0 o posterior.
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    // Firma definitiva de la app (la del APK que se instala en el móvil). Los datos están en
    // keystore.properties (en la raíz del proyecto, fuera de Git): dónde está la clave, su alias y
    // la contraseña, que solo conoce el usuario. Si ese archivo no existe, la versión «release»
    // sale sin firmar.
    signingConfigs {
        if (!firma.getProperty("contrasena").isNullOrBlank()) {
            create("definitiva") {
                storeFile = file(firma.getProperty("archivo"))
                storePassword = firma.getProperty("contrasena")
                keyAlias = firma.getProperty("alias")
                keyPassword = firma.getProperty("contrasena")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("definitiva")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Readium usa funciones modernas de Java; esto las hace funcionar en móviles antiguos.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.documentfile)
    // Navegación entre pantallas: «volver» regresa siempre a la pantalla de origen.
    implementation(libs.androidx.navigation.compose)

    // Room: la base de datos donde se guardan libros, posiciones, estanterías...
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Readium: abre EPUB y PDF y los muestra en pantalla.
    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.readium.navigator)
    implementation(libs.readium.navigator.media.tts)
    implementation(libs.media3.session)
    implementation(libs.readium.pdfium.document)
    implementation(libs.readium.pdfium.navigator)
    // Modo texto para PDF: saca el texto del PDF (licencia Apache 2.0).
    implementation(libs.pdfbox.android)
    implementation(libs.junrar)
    implementation(libs.androidx.glance.appwidget)
    // Copia automática en segundo plano (Apache 2.0).
    implementation(libs.androidx.work.runtime.ktx)

    // Pruebas en el ordenador (por ejemplo, la detección de notas del modo texto).
    testImplementation(libs.junit)
}

// Room guarda aquí la descripción de cada versión de la base de datos,
// para poder actualizarla en el futuro sin perder los datos del usuario.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
