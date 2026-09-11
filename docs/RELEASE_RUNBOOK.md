# Direct APK Release Runbook

DexWorkspaceTouch direct releases keep package `com.trancong.dexworkspacetouch` and the approved
production certificate SHA-256 `19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7`.
Use monotonically increasing `versionCode`; use human-readable semantic `versionName`. Downgrade
and `adb install -d` are not supported customer workflows.

1. Review and intentionally increment version metadata only for a real release. Review Git state.
2. Ensure signing credentials remain in secure local configuration; never commit or print them.
3. Run `scripts/build-production-release.ps1 -AcknowledgeDirtyWorktree` only after reviewing a
   dirty tree. The script runs regression/build, verifies actual APK metadata and signature,
   checks the approved signer and license trust configuration, and generates `release-output/`.
4. Review `release-manifest.json`, `SHA256SUMS.txt`, release notes, and the APK. Publish/upload is a
   separate explicit operator action; the script never deploys the backend or uploads artifacts.
   To generate the public update contract from the verified APK, provide operator-controlled HTTPS
   locations: `-CreateUpdateManifest -UpdateManifestUrl https://.../update-manifest.json
   -ApkUrl https://.../DexWorkspaceTouch-x-y.apk -ReleaseNotes "..."`. The script embeds the
   manifest URL in the release build and creates `update-manifest.json`, but never uploads files.
   Upload and verify the APK first, then publish the manifest last.
5. Test a true update on a licensed device with representative workspace state: record version,
   workspace IDs/count, a non-secret device public-key fingerprint, and stored-token digest/status;
   run `adb install -r` without uninstall; confirm all values and the License Gate remain valid.
6. Separately test a fresh installation on an expendable test device/profile: it must show the
   Unactivated gate and contain no bundled token/license. Revoke any disposable production smoke
   license and remove its plaintext key after activation/refresh/offline tests.
7. A corrupted APK, unexpected package, checksum mismatch, or signer mismatch is a release stop.
   Never “fix” an update by uninstalling the customer app because that deletes data and identity.

## Rollback and platform readiness

A rollback is a separate release-engineering decision and normally requires a new APK with a
higher versionCode. Preserve signing continuity. Direct APK distribution may require Android
Developer Verification according to the platform rollout: register the exact package and current
certificate through the eligible operator account; never bypass verification or change package.
If Google Play is used later and its app-signing certificate differs, obtain the actual Play
certificate, update/test the backend allowlist first, then roll out. Do not assume the upload-key
certificate is the installed APK signer.

APK signing certificate, device P-256 identity, backend RS256 token key, and License Key HMAC
pepper are independent. LIC-014 rotates none of them and adds no auto-updater or installer-source
restriction.

## Direct update behavior

The Updates screen performs an explicit, non-blocking check. A valid manifest must identify this
application, advertise a positive newer `versionCode`, use an HTTPS APK URL, match the exact
production APK signer fingerprint, and contain the verified SHA-256 and byte size. Invalid,
unreachable, or server-error responses become update unavailable and never affect startup or the
license gate. Download opens the HTTPS artifact in the browser; the app does not silently download
or install APKs and does not request package-install permission.

## Production update delivery

Production artifacts are stored in the private Standard-class R2 bucket
`dexworkspacetouch-releases`. Public reads go only through the dedicated GET/HEAD Worker at
`https://dexworkspacetouch-updates.dex-backend.workers.dev`; the bucket's `r2.dev` URL remains
disabled and no custom domain is attached. Versioned APK keys are immutable. The stable manifest
is uploaded only after the versioned APK passes remote HEAD, byte-size, SHA-256, and Range checks.

Publish a previously built and verified artifact with `scripts/publish-production-release.ps1`,
supplying the bucket, Worker origin, APK, and generated manifest paths. The script never rebuilds,
lists publicly, deletes old releases, or exposes an upload route. It retains a local copy of the
previous production manifest when one exists.

Version `1.0.0-beta.3` (code 4) was built before the production manifest URL existed and cannot
discover updates remotely. Version `1.0.0-beta.4` (code 5) is the one-time manually distributed
bootstrap release. From code 5 onward, manual update checks use the stable production manifest.
