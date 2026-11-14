// AGP 9 trae Kotlin integrado ("built-in Kotlin") y depende en tiempo de ejecución de una
// versión base del Kotlin Gradle Plugin. Para usar la versión estable actual de Kotlin
// (y que coincida con el plugin del compilador de Compose) se declara en el classpath,
// tal como indica la documentación oficial de AGP 9.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
