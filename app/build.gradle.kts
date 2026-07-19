import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val releaseLocalProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
fun releaseSecret(name: String): String? =
    providers.environmentVariable(name).orNull?.takeIf(String::isNotBlank)
        ?: releaseLocalProperties.getProperty(name)?.takeIf(String::isNotBlank)

val releaseStorePath = releaseSecret("DWT_RELEASE_STORE_FILE")
val releaseStorePassword = releaseSecret("DWT_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSecret("DWT_RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSecret("DWT_RELEASE_KEY_PASSWORD")
val releaseStore = releaseStorePath?.let(rootProject::file)
val releaseSigningConfigured = releaseStore?.isFile == true &&
    releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null
val requireReleaseSigning = (
    providers.gradleProperty("dwt.requireReleaseSigning").orNull
        ?: providers.environmentVariable("DWT_REQUIRE_RELEASE_SIGNING").orNull
    )?.toBooleanStrictOrNull() == true

android {
    namespace = "com.trancong.dexworkspacetouch"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.trancong.dexworkspacetouch"
        minSdk = 28
        targetSdk = 37
        versionCode = 2
        versionName = "1.0.0-beta.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = releaseStore
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = false
            if (releaseSigningConfigured) signingConfig = signingConfigs.getByName("release")
        }
    }
}

tasks.matching {
    it.name.contains("release", ignoreCase = true) &&
        (it.name.startsWith("assemble", ignoreCase = true) ||
            it.name.startsWith("bundle", ignoreCase = true) ||
            it.name.startsWith("package", ignoreCase = true))
}.configureEach {
    doFirst {
        if (requireReleaseSigning && !releaseSigningConfigured) {
            throw GradleException(
                "Signed release requested but DWT_RELEASE_STORE_FILE, " +
                    "DWT_RELEASE_STORE_PASSWORD, DWT_RELEASE_KEY_ALIAS, or " +
                    "DWT_RELEASE_KEY_PASSWORD is missing, or the keystore file does not exist.",
            )
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
}
