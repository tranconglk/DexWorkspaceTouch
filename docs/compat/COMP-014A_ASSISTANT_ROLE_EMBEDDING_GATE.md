# COMP-014A — Assistant Role Arbitrary App Embedding Gate

Date: 2026-09-15

Branch: `release/1.0-beta`

Baseline: `56108d0df9742591a5725c4a201aec553f8f1c6b` (`COMP-013 document One UI 8 workspace launch limits`)

Scope: read-only investigation. No role request, Assistant change, VoiceInteractionService, Activity Embedding prototype, dependency, production source change, build, commit, or push.

## 1. Device and build

- Samsung Galaxy S22 Ultra `SM-S908E`
- Android 16 / API 36, One UI 8
- build fingerprint: `samsung/b0qxxx/b0q:16/BP2A.250605.031.A3/S908EXXSEGZH4:user/release-keys`
- incremental build: `S908EXXSEGZH4`
- ADB target: `192.168.1.183:5555`
- wired external DeX was active during the investigation
- Android SDK extension properties `r` through `v`: 22
- framework publishes `androidx.window.extensions.jar` and `androidx.window.sidecar.jar`

## 2. Feature flag

Flag: `com.android.window.flags.untrusted_embedding_any_app_permission`.

Device observations:

- `aflags --help` is present;
- `aflags list` is root-only and returned `Error: must be root`;
- read-only `device_config get windowing_sdk untrusted_embedding_any_app_permission` returned `null`;
- no readable DeviceConfig entry exposed the generated aconfig value.

Result: **NOT EXPOSED / UNKNOWN**. The flag was not modified. `null` is not evidence of disabled state because this is a fixed read-only aconfig flag rather than necessarily a DeviceConfig override.

AOSP defines it in [`core/java/android/window/flags/windowing_sdk.aconfig`](https://android.googlesource.com/platform/frameworks/base/+/android16-qpr2-release/core/java/android/window/flags/windowing_sdk.aconfig) as exported and fixed read-only. The effective OEM build value is required by enforcement but could not be queried from the non-root shell on this Samsung build.

## 3. Current Assistant holder and direct permissions

`cmd role get-role-holders android.app.role.ASSISTANT` returned:

```text
com.google.android.googlequicksearchbox
```

`settings get secure assistant` and `settings get secure voice_interaction_service` both resolve to Google's `GsaVoiceInteractionService`.

Direct permission checks, where `0` is granted and `-1` is denied:

| Package | Result |
|---|---:|
| `com.google.android.googlequicksearchbox` | `-1` |
| `com.trancong.dexworkspacetouch` | `-1` |

The Google package dump does **not** list `android.permission.EMBED_ANY_APP_IN_UNTRUSTED_MODE` among requested permissions. It does show other role grants such as `READ_ASSISTANT_APP_SEARCH_DATA` and `EXECUTE_APP_ACTION`. Therefore Google's `-1` does not prove that Samsung's role controller refuses this permission; the installed holder did not request it.

DWT requests only its existing permissions and likewise does not request this permission.

## 4. AOSP role grant mechanism

Android 16 QPR2 [`PermissionController/res/xml/roles.xml`](https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/android16-qpr2-release/PermissionController/res/xml/roles.xml) defines `android.app.role.ASSISTANT` as:

- exclusive per user;
- `systemOnly=false` by omission;
- `requestable=false`;
- managed through Assistant-specific behavior/UI;
- granting `EMBED_ANY_APP_IN_UNTRUSTED_MODE` from API 35 onward when the qualified holder requests it.

The platform declaration in [`core/res/AndroidManifest.xml`](https://android.googlesource.com/platform/frameworks/base/+/android16-qpr2-release/core/res/AndroidManifest.xml) uses protection `internal|role` and describes it as a permission for the user-selected Assistant. `internal` alone grants nothing; role policy is the grant path.

The role is not obtained through a normal `RoleManager.createRequestRoleIntent()` flow because `requestable=false`. [`AssistantRoleUiBehavior`](https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/android16-qpr2-release/PermissionController/src/com/android/permissioncontroller/role/ui/behavior/AssistantRoleUiBehavior.java) routes management to `Settings.ACTION_VOICE_INPUT_SETTINGS`.

## 5. Embedding enforcement

Android 16 QPR2 [`TaskFragment.java`](https://android.googlesource.com/platform/frameworks/base/+/android16-qpr2-release/services/core/java/com/android/server/wm/TaskFragment.java) performs these checks:

1. the untrusted TaskFragment bounds must remain inside its parent task;
2. if `Flags.untrustedEmbeddingAnyAppPermission()` is true **and** the organizer UID has `EMBED_ANY_APP_IN_UNTRUSTED_MODE`, untrusted embedding is allowed;
3. otherwise the target must declare `FLAG_ALLOW_UNTRUSTED_ACTIVITY_EMBEDDING`, or qualify through the separate trusted/certificate path;
4. target minimum dimensions still apply.

The Android CTS Activity Embedding test [`testCrossUidActivityEmbeddingIsAllowedWithEmbedAnyAppPermission`](https://android.googlesource.com/platform/cts/+/8a222004d187f114a4e28e000dc8dd9463aa2302%5E2..8a222004d187f114a4e28e000dc8dd9463aa2302/) explicitly verifies cross-UID embedding without target opt-in when the permission and flag are enabled, and rejection when the flag is disabled.

Answer: **CONDITIONAL YES**. The permission bypasses target manifest opt-in only when the runtime flag is enabled, the TaskFragment is within its parent, the organizer UID actually has the permission, and normal Activity Embedding constraints such as minimum dimensions are satisfied. It is untrusted embedding, not fully trusted embedding.

## 6. DWT current role eligibility

Result: **NOT_CURRENTLY_ELIGIBLE**.

Read-only package and source inspection found neither:

- a service handling `android.service.voice.VoiceInteractionService` with `android.permission.BIND_VOICE_INTERACTION` and valid `android.voice_interaction` metadata; nor
- an Activity handling `android.intent.action.ASSIST`.

Device queries returned `No services found` and `No activities found` for DWT. The project also has no Assistant role implementation or `androidx.window` dependency.

## 7. Minimum future eligibility

[`AssistantRoleBehavior`](https://android.googlesource.com/platform/packages/modules/Permission/+/db906a39b759578ecabe5ed9be819b88b9b6829f/PermissionController/src/com/android/permissioncontroller/role/model/AssistantRoleBehavior.java) accepts either:

- a qualifying `VoiceInteractionService` protected by `BIND_VOICE_INTERACTION`, with valid voice-interaction metadata and required assist capability; or
- an exported/default Assist Activity resolving `android.intent.action.ASSIST`.

The smallest candidate surface appears to be a legitimate Assist Activity plus a manifest request for the role permission. A complete voice-assistant product would instead require the VoiceInteractionService/session/recognition metadata and lifecycle obligations. Actual qualification must still be tested through Samsung Settings; this gate does not claim the smaller Activity-only form will receive this particular OEM grant.

In either case the user must explicitly select DexWorkspaceTouch in **Settings → Default digital assistant app / Voice input**. Because the role is exclusive, DWT would replace the current Google/Gemini Assistant holder for that user. It is not an additive background entitlement.

## 8. Third-party selection feasibility on this Samsung build

Classification: **PUBLIC_USER_SELECTABLE**, through system Settings rather than the request-role dialog.

Evidence on this exact device:

- the role exists and has Google as holder;
- `android.intent.action.ASSIST` resolves both Google and the third-party ChatGPT package;
- `android.service.voice.VoiceInteractionService` resolves both Google and ChatGPT;
- the ChatGPT service is protected by `BIND_VOICE_INTERACTION`;
- Samsung exposes the secure Assistant/voice-interaction selection state.

This proves Samsung is not restricting the candidate mechanism to system packages. It does not prove DWT would receive the embedding permission because DWT is not presently qualified/requesting it and no role change was permitted.

## 9. Jetpack WindowManager compatibility

The repository currently has **no `androidx.window` dependency**. A future approved prototype would use the current stable `androidx.window:window:1.5.1`; this milestone does not add it. The official [WindowManager release page](https://developer.android.com/jetpack/androidx/releases/window) identifies 1.5.1 as stable.

The Samsung framework exposes Window Extensions and Sidecar jars, Android freeform window management, and SDK extension level 22. However, no public read-only shell command exposed the Window SDK Extensions Activity Embedding vendor API level itself. Device support is therefore **present at framework-library level but exact Window SDK Extensions level is UNKNOWN**. A future in-app probe using `WindowSdkExtensions` would be required; none was created here.

## 10. Samsung-specific findings

Supported evidence:

- Samsung retains the public Assistant candidate/settings mechanism and admits a third-party candidate (ChatGPT).
- The permission exists as `internal|role` in Samsung framework.
- Both current Google Assistant and DWT are denied it.
- Google does not request it, so its denial is inconclusive about Samsung role-grant policy.
- Samsung hides the fixed aconfig flag value from the non-root shell; DeviceConfig has no equivalent exposed value.

No evidence proves that Samsung disables arbitrary embedding, removes the role permission, or changes TaskFragment enforcement. Conversely, no evidence proves that the flag is enabled or that a newly qualified third-party Assistant requesting the permission receives it. **No Samsung-specific blocker is confirmed; Samsung viability remains unproven.**

## 11. Chrome and Samsung Internet ownership answer

If—and only if—DWT legitimately becomes the Assistant, requests and receives `EMBED_ANY_APP_IN_UNTRUSTED_MODE`, and the Samsung runtime flag is enabled, AOSP enforcement permits DWT's TaskFragment organizer to embed exported Activities from Chrome or Samsung Internet without their `allowUntrustedActivityEmbedding`/certificate opt-in. Parent containment and target minimum-size rules still apply.

Thus theoretical result: **YES, CONDITIONAL**. Current-device operational result: **NOT PROVEN**. This is Activity Embedding/TaskFragment behavior, not AppTask ownership, task repositioning, ActivityView, TaskView, or VirtualDisplay.

## 12. Product cost

This would be an **ADVANCED OPTIONAL MODE WITH LARGE SYSTEM-SIDE EFFECT**. The user must replace Google/Gemini (or another selected provider) as the default digital Assistant with DexWorkspaceTouch. That changes the system Assistant affordance and grants DWT broad Assistant-role capabilities. It cannot be presented as an ordinary Workspace toggle.

## 13. Decision matrix

| Condition | Result |
|---|---|
| Permission exists | YES |
| Feature flag enabled | NOT EXPOSED / UNKNOWN |
| Current Assistant has permission | NO (`-1`; permission not requested) |
| `ROLE_ASSISTANT` user-selectable | YES, through Settings; not request-role dialog |
| DWT currently eligible | NO |
| DWT could become eligible | THEORETICALLY YES, with legitimate Assist component |
| Would it replace current Assistant | YES |
| Permission bypasses target opt-in | CONDITIONAL YES |
| Chrome could theoretically be embedded | CONDITIONAL YES |
| Samsung-specific blocker found | NO CONFIRMED BLOCKER; Samsung flag/grant unresolved |

## 14. Final verdict

**F — INSUFFICIENT EVIDENCE**

AOSP contains a public-user-selection plus role-permission path, and the permission genuinely bypasses target opt-in when enabled. The current Samsung device also accepts third-party Assistant candidates. But the decisive Samsung runtime flag is not readable without root, the current holder does not request the permission, and this task forbids temporarily qualifying/selecting DWT. Therefore the gate cannot truthfully choose A or B for this firmware, nor C/D/E.

Recommendation: **STOP before prototype**. Do not add `VoiceInteractionService`, Assist Activity, `androidx.window`, role request, or WorkspaceHost yet. A separately approved, reversible gate must first use a minimal legitimate Assistant-candidate test build, user-confirmed Settings selection, direct permission verification, public `WindowSdkExtensions` inspection, and immediate restoration of the previous Assistant. Only a positive permission result should authorize an embedding prototype.
