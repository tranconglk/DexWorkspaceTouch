plugins { id("com.android.application") }

android {
    namespace = "com.trancong.dexworkspacetouch.shizukuprobe"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.trancong.dexworkspacetouch.shizukuprobe"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures { aidl = true }
}

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
