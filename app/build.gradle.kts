import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// ---------------------------------------------------------------------------
// Google Services (Firebase)
//
// El plugin se aplica únicamente si existe app/google-services.json. Así el
// proyecto se puede compilar y ejecutar las pruebas unitarias sin credenciales
// reales. Para usar Firebase hay que descargar el archivo desde la consola y
// dejarlo en app/. Ver FIREBASE_SETUP.md.
// ---------------------------------------------------------------------------
val googleServicesFile = file("google-services.json")
if (googleServicesFile.exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// ---------------------------------------------------------------------------
// Configuración local (no versionada)
//
// local.properties guarda la ruta del SDK y datos del entorno de desarrollo.
// El archivo de firma se busca fuera del repositorio para no versionar
// credenciales. Ver RELEASE_SIGNING.md.
// ---------------------------------------------------------------------------
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

val signingPropertiesFile: File? = sequenceOf(
    project.findProperty("SIGNING_PROPERTIES_FILE") as String?,
    System.getenv("RELEASE_SIGNING_PROPERTIES"),
    rootProject.file("../API1ELIASESCALANTEPM-signing/keystore.properties").absolutePath,
    rootProject.file("keystore.properties").absolutePath
)
    .filterNotNull()
    .map(::File)
    .firstOrNull { it.isFile }

val signingProperties = Properties().apply {
    signingPropertiesFile?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.example.api1_eliasescalante_pm"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.api1_eliasescalante_pm"
        minSdk = 24
        targetSdk = 37

        // Versión candidata para el grupo de testers.
        // versionCode se incrementa en cada entrega; versionName es legible.
        versionCode = 4
        versionName = "4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (signingPropertiesFile != null) {
            create("release") {
                storeFile = File(
                    requireNotNull(signingProperties.getProperty("storeFile")) {
                        "keystore.properties debe definir storeFile"
                    }
                )
                storePassword = requireNotNull(signingProperties.getProperty("storePassword"))
                keyAlias = requireNotNull(signingProperties.getProperty("keyAlias"))
                keyPassword = requireNotNull(signingProperties.getProperty("keyPassword"))
            }
        }
    }

    buildTypes {
        debug {
            // Misma applicationId que release para que google-services.json
            // (registrado con ese package) también aplique en depuración.
            buildConfigField("String", "APP_ENV", "\"development\"")
            buildConfigField("boolean", "CLOUD_LOGGING", "true")
        }

        release {
            buildConfigField("String", "APP_ENV", "\"production\"")
            buildConfigField("boolean", "CLOUD_LOGGING", "false")

            signingConfig = signingConfigs.findByName("release")

            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Persistencia local
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Firebase (Backend as a Service): autenticación y Firestore
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
