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
fun configured(name: String): String = releaseSecret(name).orEmpty()
val debugLicenseUrl = providers.gradleProperty("dwt.debugLicenseApiBaseUrl").orNull
    ?: providers.environmentVariable("DWT_DEBUG_LICENSE_API_BASE_URL").orNull
    ?: "http://10.0.2.2:8787/"
val debugLicensePublicKey = providers.gradleProperty("dwt.debugLicenseSigningPublicKeyV1Base64").orNull
    ?: providers.environmentVariable("DWT_DEBUG_LICENSE_SIGNING_PUBLIC_KEY_V1_BASE64").orNull
    ?: ""
val debugLicenseSigningKeyId = providers.gradleProperty("dwt.debugLicenseSigningKeyId").orNull
    ?: providers.environmentVariable("DWT_DEBUG_LICENSE_SIGNING_KEY_ID").orNull
    ?: "license-signing-v1"
val releaseLicenseUrl = configured("DWT_LICENSE_API_BASE_URL")
val releaseLicensePublicKey = configured("DWT_LICENSE_SIGNING_PUBLIC_KEY_V1_BASE64")
val releaseLicenseSigningKeyId = configured("DWT_LICENSE_SIGNING_KEY_ID")
    .ifBlank { "license-signing-v1" }
fun singleKeyRegistry(kid: String, spki: String): String = if (spki.isBlank()) "[]" else
    "[{\"kid\":\"$kid\",\"algorithm\":\"RS256\",\"spkiBase64\":\"$spki\"}]"
val debugTrustedKeysJson = providers.gradleProperty("dwt.debugLicenseTrustedPublicKeysJson").orNull
    ?: providers.environmentVariable("DWT_DEBUG_LICENSE_TRUSTED_PUBLIC_KEYS_JSON").orNull
    ?: singleKeyRegistry(debugLicenseSigningKeyId, debugLicensePublicKey)
val releaseTrustedKeysJson = configured("DWT_LICENSE_TRUSTED_PUBLIC_KEYS_JSON")
    .ifBlank { singleKeyRegistry(releaseLicenseSigningKeyId, releaseLicensePublicKey) }
val debugUpdateManifestUrl = providers.gradleProperty("dwt.debugUpdateManifestUrl").orNull
    ?: providers.environmentVariable("DWT_DEBUG_UPDATE_MANIFEST_URL").orNull.orEmpty()
val releaseUpdateManifestUrl = configured("DWT_UPDATE_MANIFEST_URL")
val productionApkSignerSha256 = "19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7"

android {
    namespace = "com.trancong.dexworkspacetouch"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.trancong.dexworkspacetouch"
        minSdk = 28
        targetSdk = 37
        versionCode = 9
        versionName = "1.0.0-beta.8"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "BUILD_COMMIT", buildConfigString(buildCommit))
        buildConfigField("String", "BUILD_DATE_UTC", buildConfigString(buildDateUtc))
        buildConfigField("String", "APK_SIGNING_CERTIFICATE_SHA256", buildConfigString(productionApkSignerSha256))
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
            buildConfigField("String", "LICENSE_API_BASE_URL", buildConfigString(debugLicenseUrl))
            buildConfigField("String", "LICENSE_SIGNING_PUBLIC_KEY_V1_BASE64", buildConfigString(debugLicensePublicKey))
            buildConfigField("String", "LICENSE_SIGNING_KEY_ID", buildConfigString(debugLicenseSigningKeyId))
            buildConfigField("String", "LICENSE_TRUSTED_PUBLIC_KEYS_JSON", buildConfigString(debugTrustedKeysJson))
            buildConfigField("String", "UPDATE_MANIFEST_URL", buildConfigString(debugUpdateManifestUrl))
        }
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = false
            buildConfigField("String", "BUILD_CHANNEL", buildConfigString("beta"))
            buildConfigField("String", "LICENSE_API_BASE_URL", buildConfigString(releaseLicenseUrl))
            buildConfigField("String", "LICENSE_SIGNING_PUBLIC_KEY_V1_BASE64", buildConfigString(releaseLicensePublicKey))
            buildConfigField("String", "LICENSE_SIGNING_KEY_ID", buildConfigString(releaseLicenseSigningKeyId))
            buildConfigField("String", "LICENSE_TRUSTED_PUBLIC_KEYS_JSON", buildConfigString(releaseTrustedKeysJson))
            buildConfigField("String", "UPDATE_MANIFEST_URL", buildConfigString(releaseUpdateManifestUrl))
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
        if (releaseLicenseUrl.isBlank() || !releaseLicenseUrl.startsWith("https://") ||
            releaseLicenseUrl.contains("localhost") || releaseLicenseUrl.contains("10.0.2.2")) {
            throw GradleException("Release requires DWT_LICENSE_API_BASE_URL with a non-local HTTPS URL.")
        }
        if (releaseTrustedKeysJson == "[]") {
            throw GradleException("Release requires a non-empty trusted license signing-key registry.")
        }
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
    implementation(libs.okhttp)
    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.json.jvm)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
}
