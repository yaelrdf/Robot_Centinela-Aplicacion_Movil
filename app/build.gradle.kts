import java.util.Properties

// Módulo :app — adaptadores, infraestructura Android e interfaz (Jetpack Compose).
// Implementa los puertos de :core con supabase-kt y contiene la raíz de composición (shell) y la capa
// de presentación (ui).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

// Configuración local de Supabase. Se lee de local.properties, que está en .gitignore.
// Solo se admite la clave PÚBLICA (anon/publishable). Nunca service_role (expediente §7.3, RNF08).
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun localProperty(name: String): String = localProperties.getProperty(name, "")

/** Literal de Java para buildConfigField, con comillas y barras escapadas. */
fun javaString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.robotcentinela.app"
    // Compose 1.12, Lifecycle 2.11 y Navigation 2.10 se publican compilados con API 37, así que la app
    // debe compilar con 37. targetSdk se mantiene en 36 (no cambia el comportamiento en ejecución).
    compileSdk = 37

    defaultConfig {
        applicationId = "com.robotcentinela.app"
        // minSdk 26: permite java.time (UTC, zonas horarias) sin desugaring. Provisional hasta
        // que Ingeniería de Software defina los dispositivos objetivo.
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.2.0"

        buildConfigField("String", "SUPABASE_URL", "\"${localProperty("supabase.url")}\"")
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            "\"${localProperty("supabase.publishableKey")}\"",
        )
    }

    // Cuenta de PRUEBA para leer datos con RLS mientras no exista la pantalla de inicio de sesión (que
    // el equipo dejó para el final). Solo existe en debug: un build release nunca la lleva.
    buildTypes {
        debug {
            buildConfigField("String", "DEV_EMAIL", javaString(localProperty("centinela.devEmail")))
            buildConfigField("String", "DEV_PASSWORD", javaString(localProperty("centinela.devPassword")))
        }
        release {
            buildConfigField("String", "DEV_EMAIL", "\"\"")
            buildConfigField("String", "DEV_PASSWORD", "\"\"")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Interfaz
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)

    // En módulos Android hay que elegir explícitamente la variante JUnit 5 de kotlin-test.
    testImplementation(kotlin("test-junit5"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
}
