# Release Signing

Release secrets must not be committed. Provide all four values through environment variables or the ignored `local.properties` file:

```properties
DWT_RELEASE_STORE_FILE=D:\\secure\\dexworkspacetouch-release.jks
DWT_RELEASE_STORE_PASSWORD=...
DWT_RELEASE_KEY_ALIAS=dexworkspacetouch
DWT_RELEASE_KEY_PASSWORD=...
```

To require a signed artifact, use one of:

```powershell
$env:DWT_REQUIRE_RELEASE_SIGNING = "true"
.\gradlew assembleRelease
```

```powershell
.\gradlew assembleRelease -Pdwt.requireReleaseSigning=true
```

Without the required flag and signing values, `assembleRelease` intentionally produces an unsigned QA artifact. Never publish that APK.

If a production keystore does not exist, create it outside the repository and store its passwords separately:

```powershell
keytool -genkeypair -v -keystore D:\secure\dexworkspacetouch-release.jks -alias dexworkspacetouch -keyalg RSA -keysize 4096 -validity 10000
```

Back up the keystore securely. Losing it prevents signing future upgrades with the same identity.
Keep at least two secure backups and store the passwords in a password manager. Do not place the
keystore inside this repository. A lost production key prevents publishing upgrades under the same
application ID unless an external distribution platform has an applicable key-management lineage.
