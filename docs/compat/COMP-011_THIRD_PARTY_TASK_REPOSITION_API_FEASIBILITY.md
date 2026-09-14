# COMP-011 — Existing Third-Party Task Reposition API Feasibility

Date: 2026-09-15

Branch: `release/1.0-beta`

Baseline commit: `958788c219cb449b3082a754747647fef3cac4f3` (`COMP-010 document existing task reuse feasibility`)

Scope: investigation only; no production implementation, privileged API, publish, push, or commit.

## A. Baseline

COMP-010 established `B — REUSE RELIABLE; REPOSITION PLATFORM-LIMITED` on the S22 One UI 8. A `NEW_TASK` launch can reuse the same task and retain its activity/state, while `setLaunchBounds()` is ignored for that already-existing task. A matching task on display 0 can also be reused instead of creating the requested external-display task. COMP-009 separately matched the observed AOSP desktop cascade transitions in all 12 controlled transitions.

The question for COMP-011 is narrower: whether a normal commercial DexWorkspaceTouch APK has a supported public handle that can discover and reposition an *already-existing third-party task* without losing its state.

## B. Android API <= 36

### `ActivityManager.getAppTasks()` / `AppTask`

The public contract says `getAppTasks()` returns tasks associated with the **calling application**, and describes `AppTask` as a way to manage the caller's own application tasks. `AppTask.getTaskInfo()` additionally filters other-app activity information for a non-system caller. `finishAndRemoveTask()`, `moveToFront()`, and `startActivity()` operate on that caller-owned `AppTask`; they do not manufacture a handle to Chrome's or Samsung Internet's task.

Source:

- <https://developer.android.com/reference/android/app/ActivityManager#getAppTasks()>
- <https://developer.android.com/reference/android/app/ActivityManager.AppTask>
- AOSP ownership enforcement: <https://android.googlesource.com/platform/frameworks/base/+/android16-qpr2-release/services/core/java/com/android/server/wm/AppTaskImpl.java>

### `ActivityManager.moveTaskToFront()`

`moveTaskToFront(taskId, flags)` requires the normal `REORDER_TASKS` permission. It only changes foreground/Z-order; it has no bounds or target-display parameter. Its `taskId` must come from a supported `RunningTaskInfo`/`RecentTaskInfo` path, but modern third-party task enumeration is deliberately restricted. Therefore it neither supplies geometry control nor a reliable supported route to an arbitrary Chrome task ID.

Source: <https://developer.android.com/reference/android/app/ActivityManager#moveTaskToFront(int,int)>

### Launch options

`ActivityOptions.setLaunchBounds()` and `setLaunchDisplayId()` are launch-time requests. They are useful when the platform creates/places a launch, but are not existing-task mutation APIs. COMP-010 empirically confirmed this distinction when an existing task was reused and retained its old bounds/display.

Source: <https://developer.android.com/reference/android/app/ActivityOptions>

**Android <=36 conclusion:** a normal app can launch with requested bounds/display and manage its own `AppTask`s, but no public API provides durable arbitrary third-party task discovery plus resize/reposition/cross-display movement.

## C. Android API 37 additions

API 37 adds:

- `ActivityManager.AppTask.moveTaskTo(TaskLocation, Executor, OutcomeReceiver)`;
- `TaskLocation`, containing target display ID and pixel bounds;
- `ActivityOptions.setMovableTaskRequired(boolean)` for a newly launched task.

`moveTaskTo()` can change bounds and display, subject to system validation and Background Activity Launch/display rules. The system may adjust the requested location. `setMovableTaskRequired(true)` makes a new-task launch fail rather than silently produce a task that cannot later be moved.

These are meaningful additions, but `moveTaskTo()` remains a method on `AppTask`. `getAppTasks()` still returns caller-associated tasks and AOSP `AppTaskImpl` still checks the calling UID against the owner captured by `getAppTasks()`. API 37 therefore does **not** give DexWorkspaceTouch an `AppTask` for Chrome or Samsung Internet.

Sources:

- <https://developer.android.com/reference/android/app/ActivityManager.AppTask#moveTaskTo(android.app.TaskLocation,java.util.concurrent.Executor,android.os.OutcomeReceiver)>
- <https://developer.android.com/reference/android/app/TaskLocation>
- API 36→37 diff: <https://developer.android.com/sdk/api_diff/37/changes/android.app.ActivityManager.AppTask>
- <https://developer.android.com/reference/android/app/ActivityOptions#setMovableTaskRequired(boolean)>

## D. Permission analysis

`AppTask.moveTaskTo()` and `ActivityOptions.setMovableTaskRequired()` require `android.permission.REPOSITION_SELF_WINDOWS`, introduced at API 37. The official permission contract describes moving/resizing **the application's tasks** and assigns protection level `signature|role`; it may be extended to the default browser and OEM-specific signature applications.

A directly distributed or Play-distributed ordinary APK is not platform/OEM-signed and cannot obtain a signature permission. It can only receive the role path if it is legitimately selected for an eligible role. Becoming the default browser merely to seek this capability is product-inappropriate, violates user expectations, and still would not convert Chrome/Samsung Internet tasks into DexWorkspaceTouch-owned `AppTask`s. No browser-role request was implemented or attempted.

Source: <https://developer.android.com/reference/android/Manifest.permission#REPOSITION_SELF_WINDOWS>

Result: API 37 is **not generally usable** by the current consumer product for third-party task positioning. Even hypothetical permission eligibility does not remove `AppTask` ownership.

## E. Task observability

`getRunningTasks()` and `getRecentTasks()` were deprecated in API 21. Since Lollipop, third-party callers receive only a small non-sensitive subset, at least their own tasks and possibly home. Android explicitly says not to use `getRunningTasks()` for application core logic.

The S22 probe made the restriction concrete while Chrome and DexWorkspaceTouch were simultaneously visible on DeX:

- `getAppTasks(100 equivalent)`: one DWT task only;
- `getRunningTasks(100)`: one DWT task only;
- `getRecentTasks(100, 0)`: one DWT task only;
- privileged `dumpsys` separately showed Chrome task `10840` and DWT task `10841` on the same DeX root task.

`dumpsys` is development evidence, not a production API. Package visibility can identify installed apps and launch components, but not their task ID, bounds, or display. On API 36, a normal app therefore cannot reliably answer before launch: “does Chrome already have a matching task on display 0?”

Source: <https://developer.android.com/reference/android/app/ActivityManager#getRunningTasks(int)>

## F. TaskOrganizer / system APIs

`TaskOrganizer`, `ShellTaskOrganizer`, `WindowContainerTransaction`, and `WindowContainerToken` are the system/Shell window-management boundary. `WindowContainerTransaction.setBounds()`, reorder, and reparent operations are capable of the required geometry operations inside the platform, but the relevant classes/operations are hidden or system/test APIs and transactions are protected by `MANAGE_ACTIVITY_TASKS`. Task-fragment APIs available to applications are scoped to fragments/activities the organizer owns; they are not an escape hatch for arbitrary top-level third-party tasks.

Classification:

- `TaskOrganizer` / `ShellTaskOrganizer`: **SYSTEM_PRIVILEGED / HIDDEN**;
- general `WindowContainerTransaction` top-level task control: **SYSTEM_PRIVILEGED / HIDDEN**;
- app embedding / owned `TaskFragment`: **PUBLIC OR LIMITED, OWN CONTENT ONLY**;
- ordinary commercial APK feasibility: **NO**.

Sources:

- <https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/window/TaskOrganizer.java>
- <https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/window/WindowContainerTransaction.java>
- `MANAGE_ACTIVITY_TASKS` enforcement: <https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/window/WindowOrganizer.java>

## G. Samsung DeX APIs

Current Samsung DeX developer guidance documents:

- app-owned manifest metadata such as `com.samsung.android.dex.launchwidth/launchheight`;
- Android `ActivityOptions.setLaunchBounds()` at launch time;
- making the developer's own activity resizable via Android multi-window declarations.

The documentation does not expose a supported task-ID manager or an API to mutate an already-existing third-party task, move it between display 0 and DeX, or preserve and resize a foreign task. The documented metadata configures the declaring app and the documented bounds example is a launch operation.

Sources:

- <https://developer.samsung.com/samsung-dex/modify-optional.html>
- <https://developer.samsung.com/samsung-dex/modify-optimizing.html>
- <https://developer.samsung.com/samsung-dex/faq.html>

## H. Samsung legacy MultiWindow APIs

Samsung's legacy `com.samsung.android.sdk.multiwindow.dex.launchwidth/launchheight` metadata is explicitly documented as deprecated from Android O. It only described launch/default size for the declaring app. No current, supported One UI 8 consumer SDK was found that exposes arbitrary existing third-party task bounds or display migration. It is not suitable for integration.

Source: <https://developer.samsung.com/samsung-dex/modify-optional.html>

## I. Knox APIs

The current official Knox `DexManager` surface controls DeX policy/configuration such as enabling DeX, shortcuts, screen timeout, wallpaper, and package policy. Its current public method list does not contain arbitrary task bounds, reposition, reparent, or cross-display movement. `addShortcut(x, y, component)` positions a **desktop shortcut**, not an application window.

Knox is an enterprise-management product. On Android 15+, broad Knox SDK access generally requires an Android Enterprise Device Owner/Profile Owner deployment, and DeX APIs require Knox permissions/licenses. Even if a future enterprise API existed, device-owner enrollment solely to obtain consumer window placement would be outside this product's acceptable model. On the audited API 36 `DexManager`, no arbitrary third-party freeform task-resize API exists.

Sources:

- <https://docs.samsungknox.com/devref/knox-sdk/reference/com/samsung/android/knox/dex/DexManager.html>
- <https://docs.samsungknox.com/dev/knox-sdk/features/mdm-providers/device-management/samsung-dex-and-knox/>
- <https://docs.samsungknox.com/dev/knox-sdk/>

## J. S22 device validation

Device/runtime:

- Samsung SM-S908E (S22 Ultra), Android API 36 / One UI 8;
- active external DeX display ID `14` at test time (dynamic; previous sessions used other IDs);
- physical mode 1920×1080; DeX work area observed as 1920×1024;
- Chrome task `10840`, bounds `Rect(441, 161 - 1479, 863)`;
- DWT probe task `10841`, display 14, bounds `Rect(882, 322 - 1920, 1024)`.

A temporary production-signed DWT Activity used only public `ActivityManager` APIs. Results:

```text
sdk=36 ownTaskId=10841 ownDisplayId=14
getAppTasks.count=1       -> DWT task 10841 only
getRunningTasks.count=1   -> DWT task 10841 only
getRecentTasks.count=1    -> DWT task 10841 only
ownAppTask.moveToFront=SUCCESS
API 37 moveTaskTo/TaskLocation/setMovableTaskRequired=SKIPPED_API_36
```

The API 36 framework permission list contained `MANAGE_ACTIVITY_TASKS` and `REORDER_TASKS`, but not `REPOSITION_SELF_WINDOWS`; the installed DWT manifest requested none of those. The probe successfully controlled its own AppTask and could not observe Chrome through supported task APIs. No foreign task operation, hidden reflection, binder transaction, removal, or force-stop was attempted.

The temporary Activity and manifest entry were removed after capture. A clean production-signed release was rebuilt and reinstalled during cleanup (see Verification).

## K. Wrong-display implications

COMP-010's wrong-display case has two independent blockers:

1. **Observability:** an ordinary API 36 app cannot reliably enumerate a matching Chrome task and inspect its display/bounds before launch.
2. **Control:** if Android reuses that phone task, launch options do not become a supported post-launch resize/move request.

API 37 still does not expose the foreign task as a DWT `AppTask`. Consequently DWT cannot promise either preflight detection or repair for an arbitrary app's wrong-display task.

## L. Open-new-close-old feasibility

“Create a new task at the desired geometry, then close the old task” is not a supported general solution:

- Android/task flags and the target app's launch mode may reuse the old task, so DWT cannot guarantee creation of a second independent task.
- DWT cannot safely identify the old foreign task through supported APIs.
- `AppTask.finishAndRemoveTask()` applies to caller-owned AppTasks, not Chrome's task.
- Android's modern process/task-management restrictions do not give a regular app authority to kill/remove another app's task.
- forcibly replacing a task would discard navigation/session/UI state and contradict the state-preservation purpose of reuse.

No destructive third-party test was performed.

## M. Public API matrices

### Observability and operation needs

| Need | Public API? | Caller-owned only? | Third-party visible/control? | API level | Permission | Production usable? |
|---|---|---:|---:|---:|---|---|
| Enumerate tasks | `getAppTasks()` | Yes | No | 21 | none for own list | Yes, self only |
| Enumerate running/recent | Deprecated restricted APIs | Effectively restricted | Not reliable | 1, deprecated 21 | privileged visibility otherwise | No for core logic |
| Get task ID | Own `RecentTaskInfo.taskId` | Yes | No reliable foreign ID | 21 | none for own | Yes, self only |
| Get package/component | Own task info | Yes/filtering applies | No reliable task mapping | 21 | privileged otherwise | Yes, self only |
| Get display ID | Own/current context; API 37 task location/result | Yes | No foreign task handle | varies/37 | API 37 permission for move | Self only |
| Get bounds | Own window/config; API 37 `TaskLocation` result | Yes | No | 37 for task move result | `REPOSITION_SELF_WINDOWS` | Restricted self only |
| Bring to front | `AppTask.moveToFront()` | Yes | No | 21 | own AppTask | Yes, self only |
| Bring task ID to front | `ActivityManager.moveTaskToFront()` | ID required | Foreign ID not reliably obtainable | 11 | `REORDER_TASKS` | Not a foreign-task solution |
| Resize/reposition | `AppTask.moveTaskTo()` | Yes | No | 37 | signature\|role | Not generally usable |
| Move across display | `AppTask.moveTaskTo()` | Yes | No | 37 | signature\|role | Not generally usable |
| Remove task | `AppTask.finishAndRemoveTask()` | Yes | No | 21 | own AppTask | Yes, self only |

### Candidate control APIs

| Candidate | Classification | Reason |
|---|---|---|
| `ActivityOptions.setLaunchBounds` | `LAUNCH_ONLY` | Request applies to activity launch; no existing-task mutation guarantee |
| `ActivityOptions.setLaunchDisplayId` | `LAUNCH_ONLY` | Selects launch display, not foreign task migration |
| `FLAG_ACTIVITY_NEW_TASK` | `LAUNCH_ONLY` | May reuse an existing task; no geometry control after reuse |
| `AppTask.moveToFront` | `SELF_TASK_ONLY` | `AppTask` originates from caller-associated tasks |
| `AppTask.startActivity` | `SELF_TASK_ONLY` | Starts/brings activity in caller-owned AppTask |
| `AppTask.moveTaskTo` | `SELF_TASK_ONLY`, `UNAVAILABLE_API36` | API 37 plus signature\|role permission; still owned AppTask |
| `ActivityManager.moveTaskToFront` | `FOREGROUND_ONLY` | Requires task ID/`REORDER_TASKS`; no resize/display parameter |
| `TaskOrganizer` | `PRIVILEGED` | System/Shell organizer boundary |
| `WindowContainerTransaction` | `PRIVILEGED/HIDDEN` | General top-level task transactions require system authority |
| Samsung DeX docs | `LAUNCH_ONLY` / own-app config | No documented foreign existing-task control |
| Legacy Samsung MultiWindow metadata | `DEPRECATED`, own-app launch config | Deprecated from Android O; not task control |
| Samsung Knox `DexManager` | `NOT_APPLICABLE` | Enterprise DeX policy/shortcut surface; no task bounds API |

## N. Future API 37 implications

- **DWT-owned tasks:** potentially movable/resizable across eligible displays if DWT can legitimately obtain `REPOSITION_SELF_WINDOWS` and the task is movable.
- **Chrome/Samsung Internet tasks:** no; they are not returned as DWT `AppTask`s.
- **Consumer eligibility:** signature/role protection blocks general use. Default-browser role is neither product-correct nor a path to ownership of another browser's tasks.
- **Architecture opportunity:** useful only for future DWT-owned workspace/window surfaces or an OEM-integrated distribution model, not the current arbitrary-app workspace launcher.

Thus API 37 does not justify advertising future third-party reuse-and-reposition semantics.

## O. Product semantics

`REUSE_AND_REPOSITION` cannot be offered reliably for arbitrary third-party apps with the accepted commercial security/policy constraints.

Viable semantics are:

1. **NEW_WINDOW / requested launch placement (current):** request display/bounds and disclose that Android/app launch modes may reuse an existing task.
2. **REUSE_FOREGROUND_ONLY:** preserve task/state while explicitly accepting its existing geometry/display. This should not be labeled reposition.
3. **HYBRID, user-selected:** allow the user to prefer reuse, with a clear warning that prior geometry and even display may remain unchanged.
4. **APP-OWNED WINDOW:** reserve precise post-launch movement for DWT-owned tasks on a future platform/distribution path where the API 37 permission is legitimately available.

Do not require root, Shizuku, ADB, Accessibility drag automation, signature spoofing, hidden binder APIs, or enterprise device-owner enrollment for the consumer product.

## P. Final verdict

### Primary verdict: **B — ONLY CALLER-OWNED TASKS CAN BE REPOSITIONED**

Qualifiers:

- Android <=36 has no public existing-task bounds/display mutation API for arbitrary third-party tasks.
- Android 37 introduces real bounds/display repositioning, but on caller-owned `AppTask` and behind `REPOSITION_SELF_WINDOWS` (`signature|role`). It is not a viable arbitrary Chrome/Samsung Internet path for DexWorkspaceTouch.
- Platform TaskOrganizer/WCT mechanisms are system-privileged/hidden.
- Samsung's public DeX surface is launch-time/own-app configuration.
- Current Knox DeX APIs are enterprise policy/configuration and do not expose arbitrary task bounds.
- Restricted task observability independently prevents reliable wrong-display preflight on API 36.

Product decision code: **NO_SELF_ONLY**, with secondary findings **NO_OBSERVABILITY** and **NO_PRIVILEGED** for a normal commercial APK.

## Verification and cleanup

- Temporary probe release: production script `-AcknowledgeDirtyWorktree -SkipTests` passed and verified signer `19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7`.
- Device probe: PASS; own task controllable, Chrome excluded from all supported enumeration results.
- Temporary source/manifest entry: removed.
- Full production gate: PASS — `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, and `assembleRelease` (`138 actionable tasks`, 43 executed, 95 up-to-date).
- Clean release SHA-256: `91b7eb2e874500b6125b125f8f11e656ebafbd19f41764c6ba8a2ba0c3054025`.
- Clean release signer SHA-256: `19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7` (unchanged production signer).
- Clean release reinstall on S22: PASS (`1.0.0-beta.8`, version code 9); package manager reports `No activity found` for the removed probe component.
