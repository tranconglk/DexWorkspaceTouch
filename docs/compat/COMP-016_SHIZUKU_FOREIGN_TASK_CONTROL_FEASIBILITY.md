# COMP-016 — Shizuku Foreign Task Control Bridge Feasibility

Date: 2026-09-15  
Branch: `release/1.0-beta`  
Baseline: `8a0bb8066a64584c58c728ca6559c05492e29026` (`COMP-014A/015 document embedding gate and shell task resize feasibility`)

## 1. Scope and isolation

This investigation continued from Section 3 after official Shizuku was installed and started by ADB/shell. It did not repeat COMP-015. PC-side ADB did not resize any task; it only installed the probe and independently inspected logs, processes, displays, and task state.

The standalone probe lives at `experiments/shizuku-task-probe/`. It is absent from the production DWT Gradle graph and does not alter the production manifest or workspace pipeline. Application ID: `com.trancong.dexworkspacetouch.shizukuprobe`.

## 2. Device and runtime

| Item | Evidence |
|---|---|
| Device | Samsung S22 Ultra `SM-S908E` (`b0q`) |
| Android/build | 16, API 36; `BP2A.250605.031.A3.S908EXXSEGZH4` |
| Shizuku package | `moe.shizuku.privileged.api` |
| Shizuku version | `13.6.0.r1086.2650830c`, versionCode `1086` |
| Start mode | ADB/shell; `shizuku_server` ran as `shell`, PID `30864` |
| Client dependencies | `dev.rikka.shizuku:api:13.1.5`, `provider:13.1.5` |

The API version and UserService usage were checked against the official `RikkaApps/Shizuku-API` repository/demo. Installed manager/server version and client API artifact version are separate.

## 3. Authorization and bridge

The normal probe called official `Shizuku.requestPermission(16)`. The user explicitly approved Shizuku authorization; it changed from `DENIED` to `GRANTED`. No auto-grant or bypass was used.

The app bound `ProbeUserService` with `Shizuku.bindUserService`. Its deliberately narrow AIDL surface is `runtimeInfo`, `findTask`, `workArea`, `resizeVerified`, and `destroy`. Only Chrome and Samsung Internet are accepted. It rejects invalid bounds/correlation IDs, display 0, non-freeform tasks, and package/task/display mismatches. It exposes no arbitrary command API.

Evidence from the successful connection:

```text
normal app: UID 10318, PID 10029, Shizuku permission GRANTED
UserService: UID 2000, PID 10081
MANAGE_ACTIVITY_TASKS=GRANTED
DUMP=GRANTED
```

The operation therefore ran in a shell-UID Shizuku UserService, not the normal app. Production DWT remains unprivileged; this experiment did not add or grant the signature permission to DWT.

## 4. Display/work-area and task discovery

No display ID or size was hard-coded. The privileged service discovered task/display from `dumpsys activity activities`, then matched that external display and its visible navigation-bar inset from `dumpsys window displays`:

```text
displayId=15
mode=1920x1080
navigationBars frame=[0,1024][1920,1080]
workArea=[0,0-1920,1024]
logical DeX density=1.0 / 160 dpi
```

Exactly one complete qualifying Chrome task was found; incomplete summary entries were ignored and multiple complete candidates would return `AMBIGUOUS`:

```text
package=com.android.chrome
taskId=10879
displayId=15
windowingMode=5 (freeform)
initial bounds=[441,161-1479,863]
ActivityRecord=233484157
PID=3794
launcher component=com.android.chrome/com.google.android.apps.chrome.Main
activity component=com.android.chrome/org.chromium.chrome.browser.ChromeTabbedActivity
```

## 5. Path A — controlled shell-command bridge

The normal app requested a validated operation through AIDL. The UserService revalidated task identity immediately before executing `/system/bin/am task resize <taskId> <bounds>`, then captured exit code/stdout/stderr and performed a post-operation lookup. The UI cannot provide arbitrary shell text.

### LEFT50

```text
correlationId=LEFT1789451121832
executor UID/PID=2000/10081
before=[441,161-1479,863]
requested=[8,8-952,1016]
actual=[8,8-952,1016]
exit=0; stdout/stderr empty
edge error=0 px
```

Independent ADB inspection confirmed the exact bounds. Task `10879`, ActivityRecord `233484157`, PID `3794`, package, freeform mode, and display `15` were preserved.

### RIGHT50 on the same Chrome task

Chrome was not relaunched:

```text
correlationId=RIGHT1789451182908
executor UID/PID=2000/10081
before=[8,8-952,1016]
requested=[968,8-1912,1016]
actual=[968,8-1912,1016]
exit=0; stdout/stderr empty
edge error=0 px
```

Independent samples retained `[968,8-1912,1016]` at T+0, T+1, T+3, and T+5. A later check after manually focusing another DeX app and returning to Chrome retained the exact bounds. Task `10879`, ActivityRecord `233484157`, PID `3794`, display `15`, and freeform mode were unchanged. Samsung recascade: **NO**.

## 6. Optional paths and lifecycle

- Chrome + Samsung Internet 50/50: **NOT RUN**. Mandatory tests answered the bridge gate; COMP-015 already established multi-task shell behavior.
- Path A controlled shell command: **PASS**.
- Path B direct `IActivityTaskManager` Binder: **NOT ATTEMPTED**. Android 16 hidden-interface/version coupling was unnecessary for this gate.
- Lifecycle: **PARTIAL**. Repeated probe replacement restarted its normal process; authorization remained granted and reconnection succeeded. Deliberate Shizuku death/restart/revocation was not tested. Old experimental UserService processes remained after repeated APK replacement, so future production work must explicitly unbind/remove services and handle Binder death rather than copy probe lifecycle behavior.

## 7. Samsung conclusion and product boundary

On this S22 Ultra Android 16 / One UI 8 build, an ADB-started Shizuku UserService receives `MANAGE_ACTIVITY_TASKS` and can perform the controlled foreign-task resize proven for direct shell in COMP-015. One UI accepted exact LEFT50 and RIGHT50 bounds, preserved task/activity/process identity, and did not recascade after time or focus changes.

Future production use must remain an optional privileged backend behind normal DWT, with explicit authorization, narrow operations, package/task/display revalidation, no stale task IDs, lifecycle/revocation handling, and public-API fallback. COMP-015 + COMP-016 support this future flow without implementing it here:

```text
Workspace plan → launch/reuse app → privileged task discovery
→ resolve exact task → post-launch resize/reposition → verify bounds
```

## 8. Result matrix

| Condition | Result |
|---|---|
| Shizuku installed/running | PASS / PASS |
| Explicit user authorization | PASS |
| Privileged service connected | PASS |
| Service UID / permission | `2000`; `MANAGE_ACTIVITY_TASKS=GRANTED` |
| Current DeX display detected | PASS (`15`) |
| Chrome exact task identified | PASS (`10879`) |
| LEFT50 / RIGHT50 via Shizuku | PASS / PASS |
| 0 px edge accuracy | PASS |
| Same task / Activity / PID | PASS / PASS / PASS |
| Stable T+5 / focus | PASS / PASS |
| Chrome + Browser 50/50 | NOT RUN |
| Path A / Path B | PASS / NOT ATTEMPTED |
| Samsung recascade | NO |
| Basic Shizuku lifecycle | PARTIAL |

## 9. Final verdict

**A — SHIZUKU CAN RELIABLY CONTROL FOREIGN DEX TASK BOUNDS**

```text
normal probe UID 10318
→ explicit Shizuku authorization
→ narrow AIDL bridge
→ UserService shell UID 2000 + MANAGE_ACTIVITY_TASKS
→ validated Chrome task 10879 on detected external display 15
→ LEFT50 exact
→ RIGHT50 exact
→ same task / ActivityRecord / PID
→ stable through T+5 and focus changes
→ no Samsung recascade
```

No PC-side ADB resize command participated in either operation.
