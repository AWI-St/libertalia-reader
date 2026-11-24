// Archivo raíz: declara los plugins que usan los módulos.
// "kotlin-android" no se aplica (Android Gradle Plugin 9 ya incluye Kotlin),
// pero se declara aquí para usar Kotlin 2.4, que es la versión que necesita Readium.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
