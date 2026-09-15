# COMP-015 — Shell Foreign Task Resize Feasibility on One UI 8 DeX

Date: 2026-09-15

Branch: `release/1.0-beta`

Baseline: `56108d0df9742591a5725c4a201aec553f8f1c6b` (`COMP-013 document One UI 8 workspace launch limits`)

Scope: device investigation only. No production source, Shizuku dependency, DWT prototype, root, Assistant, commit, or push change. The existing untracked COMP-014A report was preserved untouched.

## 1. Device and session

| Property | Observed value |
|---|---|
| Device | Samsung Galaxy S22 Ultra `SM-S908E` |
| Android | 16 / API 36 |
| One UI build | `S908EXXSEGZH4` |
| ADB | `192.168.1.183:5555` |
| External DeX display | dynamically detected ID `15` |
| Display mode | `1920 x 1080` |
| Logical density | `1.0` / 160 dpi override |
| Usable work area | `[0,0-1920,1024]` |
| Bottom reserved area | 56 px |

Display 0 remained the phone (`1080 x 2316`, 450 dpi). Display 15 was confirmed from `dumpsys activity displays` and `dumpsys window displays`; no historical display ID was assumed.

## 2. Supported shell commands

`adb shell am help` on this firmware advertises:

```text
task resize <TASK_ID> <LEFT> <TOP> <RIGHT> <BOTTOM>
    The task is resized only if it is in multi-window windowing
    mode or freeform windowing mode.
```

It also advertises `task resizeable`, root-stack movement, and a display-level `move-stack`; it does not advertise a direct leaf-task-to-display command. `am task help` itself is not a valid nested command on this build, but the complete `am help` output includes task syntax.

The command actually used for all bounds changes was:

```powershell
adb -s 192.168.1.183:5555 shell am task resize <taskId> <left> <top> <right> <bottom>
```

Every tested invocation returned exit code 0 and no stderr.

## 3. Reliable foreign-task identification

Tasks were selected from `dumpsys activity activities`, not by package alone. Each selected record had to show the expected component, display section 15, `mode=freeform`, visible state, task bounds, ActivityRecord, and process.

| App | Task ID | Component | Initial bounds | ActivityRecord | PID |
|---|---:|---|---|---|---:|
| Chrome | 10869 | `com.android.chrome/com.google.android.apps.chrome.Main` | `[441,161-1479,863]` | `78952393` | 17652 |
| Samsung Internet | 10870 | `com.sec.android.app.sbrowser/.SBrowserMainActivity` | `[441,120-1479,822]` | `49390436` | 24087 |
| Samsung Calculator | 10871 | `com.sec.android.app.popupcalculator/.Calculator` | `[441,120-1479,822]` | `84398845` | 24686 |

All were standard, visible freeform leaf tasks under DeX root task 10413 on display 15. Initial centered positions demonstrate the Samsung placement behavior that the test intended to correct.

## 4. Privilege and API path

AOSP [`ActivityManagerShellCommand.runTaskResize()`](https://android.googlesource.com/platform/frameworks/base/+/android16-qpr2-release/services/core/java/com/android/server/am/ActivityManagerShellCommand.java) parses the task ID/bounds and calls `IActivityTaskManager.resizeTask(..., RESIZE_MODE_SYSTEM)`.

Android's hidden [`ActivityTaskManager.resizeTask()`](https://android.googlesource.com/platform/frameworks/base/+/android16-qpr2-release/core/java/android/app/ActivityTaskManager.java) is annotated with `@RequiresPermission(MANAGE_ACTIVITY_TASKS)`. [`ActivityTaskManagerService.resizeTask()`](https://android.googlesource.com/platform/frameworks/base/+/android16-qpr2-release/services/core/java/com/android/server/wm/ActivityTaskManagerService.java) calls `enforceTaskPermission("resizeTask()")`, resolves the attached task, verifies that its window configuration is resizeable, and applies the bounds.

Direct device permission checks:

| Caller package | `MANAGE_ACTIVITY_TASKS` |
|---|---:|
| `com.android.shell` | `0` — granted |
| `com.trancong.dexworkspacetouch` | `-1` — denied |

The shell package dump explicitly lists `MANAGE_ACTIVITY_TASKS: granted=true`. A normal DWT process therefore cannot call this API directly; it is both hidden and permission-protected. The successful ADB command executes as shell UID through the platform command service.

## 5. Test A — single Chrome exact resize

Before:

```text
taskId=10869
displayId=15
windowingMode=freeform
bounds=[441,161-1479,863]
ActivityRecord=78952393
PID=17652
state=RESUMED, visible=true
```

Command:

```powershell
adb -s 192.168.1.183:5555 shell am task resize 10869 8 8 952 1016
```

Requested LEFT50: `[8,8-952,1016]`

Actual: `[8,8-952,1016]`

Edge deltas L/T/R/B: `0/0/0/0 px`

The same task 10869, ActivityRecord `78952393`, PID 17652, package/component, freeform mode, and display remained. Samsung did not substitute a cascade anchor.

Result: **PASS-A**.

## 6. Stability

Bounds samples after Test A:

| Sample | Actual bounds |
|---|---|
| T+0 | `[8,8-952,1016]` |
| T+1s | `[8,8-952,1016]` |
| T+3s | `[8,8-952,1016]` |
| T+5s | `[8,8-952,1016]` |

Result: **PASS-STABLE**. No automatic recascade or bounds correction occurred.

## 7. Test B — move the same Chrome task again

Command:

```powershell
adb -s 192.168.1.183:5555 shell am task resize 10869 968 8 1912 1016
```

Transition:

```text
[8,8-952,1016] → requested [968,8-1912,1016] → actual [968,8-1912,1016]
```

All edge deltas were 0 px. Task 10869, ActivityRecord `78952393`, and PID 17652 remained unchanged. No relaunch was observed.

Result: **PASS-B**.

## 8. Test C — Chrome and Samsung Internet 50/50

Commands:

```powershell
adb -s 192.168.1.183:5555 shell am task resize 10869 8 8 952 1016
adb -s 192.168.1.183:5555 shell am task resize 10870 968 8 1912 1016
```

| App | Requested | Actual | Max edge delta |
|---|---|---|---:|
| Chrome | `[8,8-952,1016]` | `[8,8-952,1016]` | 0 px |
| Samsung Internet | `[968,8-1912,1016]` | `[968,8-1912,1016]` | 0 px |

There was a 16 px center gap and no overlap. Both task IDs, ActivityRecords, PIDs, freeform mode, and display 15 were preserved.

Focus was switched to Chrome and then Samsung Internet by starting the same explicit components. Android reported that each current task was brought to front rather than creating/restarting an Activity. Both bounds remained byte-for-byte identical after each focus switch.

Result: **PASS-C**, including focus stability.

## 9. Test D — arbitrary 25/25/50

Commands:

```powershell
adb -s 192.168.1.183:5555 shell am task resize 10869 8 8 472 1016
adb -s 192.168.1.183:5555 shell am task resize 10870 488 8 952 1016
adb -s 192.168.1.183:5555 shell am task resize 10871 968 8 1912 1016
```

| App | Requested | Actual | Max edge delta |
|---|---|---|---:|
| Chrome | `[8,8-472,1016]` | `[8,8-472,1016]` | 0 px |
| Samsung Internet | `[488,8-952,1016]` | `[488,8-952,1016]` | 0 px |
| Calculator | `[968,8-1912,1016]` | `[968,8-1912,1016]` | 0 px |

This is the representative layout that launch-time cascade could not reproduce in COMP-013. All three foreign tasks accepted exact arbitrary post-launch geometry. Sequential focus of Chrome, Browser, and Calculator did not alter any rectangle. A further sample five seconds later remained exact.

Result: **PASS-D**.

## 10. State preservation

Across resize and focus tests:

- Chrome: task 10869, ActivityRecord `78952393`, PID 17652 unchanged;
- Browser: task 10870, ActivityRecord `49390436`, PID 24087 unchanged;
- Calculator: task 10871, ActivityRecord `84398845`, PID 24686 unchanged;
- no new task was created by resize;
- no process restart or Activity recreation was observed;
- all targets stayed on external display 15 and in freeform mode.

This is native task-container manipulation rather than app relaunch. Tab/page content was not semantically inspected, but process/task/Activity identity strongly establishes runtime state preservation for the tested interval.

## 11. Cross-display result

Classification: **NOT TESTED**.

The firmware advertises `display move-stack`, not a clear supported command for moving the selected DeX leaf foreign task directly between phone and DeX displays. Moving the DeX root stack would disrupt the active session and was outside the primary gate. No display mutation was attempted.

## 12. Samsung-specific behavior

Samsung One UI 8 accepted shell `RESIZE_MODE_SYSTEM` bounds exactly for Chrome, Samsung Internet, and Calculator. It did not reapply cascade at T+5s, on subsequent resize, when another foreign task was resized, or during focus switches.

Therefore the One UI 8 cascade is a launch-placement policy, not an immutable constraint on the final task bounds. A sufficiently privileged post-launch resize can neutralize the measured placement error for already identified freeform tasks.

## 13. Shizuku relevance

Classification: **PROMISING**.

The demonstrated conceptual path is:

```text
launch app
→ identify the exact DeX task
→ shell-privileged IActivityTaskManager.resizeTask
→ verify final bounds
```

This is relevant to a future Shizuku user-service/Binder bridge investigation because the required operation works under shell UID. However:

**ADB shell PASS does not prove Shizuku PASS.**

A separate COMP-016 must verify Shizuku's actual UID/permission context, access to the hidden Binder interface, Samsung/API-version compatibility, robust foreign-task discovery without overbroad exposure, user authorization/reconnect lifecycle, and security boundaries. No dependency or bridge was added here.

## 14. Results matrix

| Test | Result |
|---|---|
| Find Chrome foreign task | PASS |
| Chrome exact resize | PASS |
| Bounds stable after 5s | PASS |
| Same task second resize | PASS |
| Chrome + Browser 50/50 | PASS |
| Focus switching preserves layout | PASS |
| 25/25/50 arbitrary layout | PASS |
| Foreign tasks stay on DeX display | PASS |
| Activity/task state preserved | PASS by task/Activity/PID identity |
| Cross-display move | NOT TESTED |
| Normal DWT can perform directly | NO (`MANAGE_ACTIVITY_TASKS` denied) |
| Shell privilege sufficient | YES |
| Shizuku direction | PROMISING, not proven |

## 15. Final verdict

**A — SHELL CAN RELIABLY CONTROL FOREIGN DEX TASK BOUNDS**

For all tested resize operations, requested and actual bounds matched exactly with 0 px edge error. The changes survived five-second stability sampling and focus switching, retained task/process/Activity identity, remained on the current external display, and were not recascaded by Samsung.

Narrow conclusion: **SHELL-LEVEL CONTROL IS FEASIBLE**.

This does not establish a production DWT or Shizuku implementation.

## 16. Product implication and recommendation

If COMP-016 proves a safe, user-authorized bridge, DWT could conceptually change its One UI 8 compatibility path from launch-time best-effort bounds to:

```text
launch
→ discover the exact matching task on the intended external display
→ post-launch resize/reposition
→ verify actual bounds
```

That architecture would directly correct the cascade result rather than attempting to predict it. It could also make reuse more practical: request task reuse, identify the actual reused task, then reposition it. Neither concept is implemented or considered production-proven here.

Recommended next milestone: **COMP-016 — Shizuku Foreign Task Control Bridge Feasibility**. It must remain a separate security/compatibility gate and must not assume that ADB shell behavior transfers automatically to Shizuku.
