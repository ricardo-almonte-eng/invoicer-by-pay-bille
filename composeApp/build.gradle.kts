import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.buildkonfig)
}

kotlin {
    android {
        namespace = "com.paybille.invoicer.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        androidResources {
            enable = true
        }
        // Pruebas de commonTest en la JVM del ordenador: `./gradlew :composeApp:testAndroidHostTest`.
        withHostTest {}
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    compilerOptions {
        // Room genera un `expect object` por base de datos.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.navigationevent.compose)
            implementation(libs.compose.components.resources)

            api(libs.koin.core)
            implementation(libs.koin.compose)

            implementation(libs.voyager.navigator)
            implementation(libs.voyager.screenmodel)
            implementation(libs.voyager.transitions)
            implementation(libs.voyager.koin)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinxJson)

            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            // Archivos (PDF de la factura) desde commonMain.
            implementation(libs.kotlinx.io.core)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.koin.android)
            // Compartir PDF (FileProvider), recordatorios (WorkManager) y permiso de avisos.
            implementation(libs.androidx.core)
            implementation(libs.androidx.work.runtime)
            implementation(libs.androidx.activity.compose)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.paybille.invoicer.resources"
    generateResClass = always
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun config(name: String, default: String): String =
    localProperties.getProperty(name) ?: project.findProperty(name)?.toString() ?: default

// La API_KEY va en el BODY del login como `key` (no en una cabecera) y el backend firma
// el JWT con ella. Vive en local.properties, que no se versiona. Queda dentro del
// binario igual que hoy queda dentro del bundle del POS: no metas aquí ningún secreto nuevo.
buildkonfig {
    packageName = "com.paybille.invoicer"

    defaultConfigs {
        buildConfigField(STRING, "API_BASE_URL", config("paybille.apiBaseUrl", "https://api.paybille.com/ventex/api"))
        buildConfigField(STRING, "API_KEY", config("paybille.apiKey", ""))
        // La web del POS: ahí vive el catálogo público (`/catalogo/<tienda>`) que se comparte.
        buildConfigField(STRING, "WEB_BASE_URL", config("paybille.webBaseUrl", "https://paybille.com"))
    }
}
