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
val releaseSigningError = when {
    releaseStorePath == null -> "DWT_RELEASE_STORE_FILE is missing."
    releaseStore?.isFile != true -> "DWT_RELEASE_STORE_FILE does not exist or is not a file."
    releaseStorePassword == null -> "DWT_RELEASE_STORE_PASSWORD is missing."
    releaseKeyAlias == null -> "DWT_RELEASE_KEY_ALIAS is missing."
    releaseKeyPassword == null -> "DWT_RELEASE_KEY_PASSWORD is missing."
    else -> null
}
val requireReleaseSigning = (
    providers.gradleProperty("dwt.requireReleaseSigning").orNull
        ?: providers.environmentVariable("DWT_REQUIRE_RELEASE_SIGNING").orNull
    )?.toBooleanStrictOrNull() == true
fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
val buildCommit = providers.environmentVariable("DWT_BUILD_COMMIT").orNull
    ?.trim()?.takeIf(String::isNotEmpty) ?: "unknown"
val buildDateUtc = providers.environmentVariable("DWT_BUILD_DATE_UTC").orNull
    ?.trim()?.takeIf(String::isNotEmpty) ?: "unknown"

android {
    namespace = "com.trancong.dexworkspacetouch"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.trancong.dexworkspacetouch"
        minSdk = 28
        targetSdk = 37
        versionCode = 3
        versionName = "1.0.0-beta.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "BUILD_COMMIT", buildConfigString(buildCommit))
        buildConfigField("String", "BUILD_DATE_UTC", buildConfigString(buildDateUtc))
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
        getByName("debug") {
            buildConfigField("String", "BUILD_CHANNEL", buildConfigString("debug"))
        }
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = false
            buildConfigField("String", "BUILD_CHANNEL", buildConfigString("beta"))
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
            throw GradleException("Signed release requested: $releaseSigningError")
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
