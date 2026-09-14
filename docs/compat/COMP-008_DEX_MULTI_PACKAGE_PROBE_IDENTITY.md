# COMP-008 — DeX Multi-Package Probe Identity Isolation

## Baseline

COMP-007 commit `46fc421e75fff7a28e74181ea56a2f9bf9d2e486` found that post-reconnect single-window probes matched third-party placement, while the same-package 25/25/50 probe did not. COMP-008 tests whether package, UID, task affinity, or task-family identity explains that mismatch.

The existing physical DeX attachment was retained. Device: Samsung S22 Ultra `SM-S908E`, Android 16 / One UI 8. Runtime external display ID was `13`, name `Màn hình HDMI`, `uniqueId=local:4`, mode `1920x1080 @ 60 Hz`, usable work area `[0,0][1920,1024]`, orientation 0. The production diagnostics used density `1.0` and margin `8 px`.

## Harness and controls

All launches used the production `AndroidWorkspaceLauncher` → `AndroidSingleAppLauncher` → `ActivitySingleAppLaunchPlatform` path. The requested 25/25/50 rectangles were:

- A `[8,8][472,1016]`
- B `[488,8][952,1016]`
- C `[968,8][1912,1016]`

Four temporary topologies were tested:

1. Three Activities in `com.trancong.dexworkspacetouch` with its default affinity and UID 10052.
2. Three Activities in the same package/UID with distinct affinities `probe.a`, `probe.b`, and `probe.c`.
3. Three Activities in the same package/UID with empty affinity.
4. Three permission-free helper APKs: `com.trancong.dexprobe.a`, `.b`, and `.c`, with distinct UIDs 10167, 10206, and 10308.

The real-app control used Chrome UID 10251, Samsung Browser UID 10291, and Samsung Tips UID 10305. All temporary Activities were resizable explicit launcher components. Helper APKs had no network, storage, or other permissions.

## 25/25/50 identity matrix

| Topology | Actual A | Actual B | Actual C | Observed class |
|---|---|---|---|---|
| Third-party baseline | Chrome `[728,6][1192,1014]` | Browser `[1456,16][1920,1024]` | Tips `[0,0][944,1008]` | `OVERLAP_OR_CASCADE` |
| Same package/current affinity | `[728,6][1192,1014]` | `[1456,16][1920,1024]` | `[0,0][944,1008]` | `OVERLAP_OR_CASCADE` |
| Same package/distinct affinities | `[728,6][1192,1014]` | `[1456,16][1920,1024]` | `[0,0][944,1008]` | `OVERLAP_OR_CASCADE` |
| Same package/empty affinity | `[728,6][1192,1014]` | `[1456,16][1920,1024]` | `[0,0][944,1008]` | `OVERLAP_OR_CASCADE` |
| Three helper packages/UIDs | `[728,6][1192,1014]` | `[1456,16][1920,1024]` | `[0,0][944,1008]` | `OVERLAP_OR_CASCADE` |
| Third-party after cleanup | Chrome `[728,6][1192,1014]` | Browser `[1456,16][1920,1024]` | Tips `[0,0][944,1008]` | `OVERLAP_OR_CASCADE` |

All tasks were separate freeform tasks on display 13 with fresh task IDs. Distinct affinity changed the recorded task roots to `10052:probe.a`, `.b`, and `.c`, but did not change placement. Empty affinity produced component-rooted tasks and also did not change placement. Separate package/UID identity likewise did not change the first observed matrix.

Critically, the same-package baseline no longer reproduced the COMP-007 post-reconnect result, even though no physical reconnect occurred. COMP-007 had produced left-quarter/right-quarter/center-half with no owned overlap in this display-13 attachment. COMP-008 later produced center-quarter/right-quarter/left-half with overlap for the same topology. Identity therefore cannot explain the earlier mismatch in isolation.

## Three-package repeatability

Because the first helper result matched third-party behavior, the helper topology was run three cold times with all helper and target tasks force-stopped between runs.

| Run | Helper A | Helper B | Helper C | Class |
|---|---|---|---|---|
| 1 | `[728,6][1192,1014]` | `[1456,16][1920,1024]` | `[0,0][944,1008]` | `OVERLAP_OR_CASCADE` |
| 2 | `[8,8][472,1016]` | `[728,6][1192,1014]` | `[976,16][1920,1024]` | `POSITION_SUBSTITUTED`, no overlap |
| 3 | `[728,6][1192,1014]` | `[1456,16][1920,1024]` | `[0,0][944,1008]` | `OVERLAP_OR_CASCADE` |

Repeatability was only `2/3`; the behavioral class changed in run 2 inside the same clean physical session. The topology therefore fails the required 3/3 predictive threshold.

## 50/50 control

Requested A/B rectangles were `[8,8][952,1016]` and `[968,8][1912,1016]`.

| Topology | Actual A | Actual B | Result |
|---|---|---|---|
| Same package | `[976,16][1920,1024]` | `[0,0][944,1008]` | Size retained; positions swapped |
| Three helper packages | `[976,16][1920,1024]` | `[0,0][944,1008]` | Identical |
| Third-party | Chrome `[976,16][1920,1024]` | Browser `[0,0][944,1008]` | Identical |

The 50/50 control did not distinguish package or affinity identity.

## Observer effect and cleanup

Third-party 25/25/50 bounds before any COMP-008 probe and after all affinity/helper variants were pixel-identical. No observer effect was detected by that comparison. Every probe/helper task was removed before the next topology; no app data was cleared.

The later three-package cold-repeat variation nevertheless shows that force-stopping all visible test tasks does not reset or uniquely determine One UI's placement regime.

## Identity-factor conclusion

- Task affinity is not explanatory: default, distinct, and empty affinities initially behaved identically.
- Package/UID separation is not explanatory: three independent helper packages initially behaved identically to same-package probes and real third-party apps.
- Package-specific Chrome/Browser/Tips constraints are not required for the observed pattern: blank helper Activities reproduced it.
- The decisive factor is unobserved or evolving One UI placement/task state within the same attachment. The tested metadata is insufficient to classify that state.

## Final verdict

**E. PLACEMENT TOO UNSTABLE TO CLASSIFY**

The known mismatch disappeared before identity isolation, and a three-package topology changed behavioral class across three cold repeats in one physical session. Therefore none of the package/affinity causal verdicts can be supported.

## Production feasibility and recommendation

A one-APK self-probe remains unproven. Shipping three companion APKs would be commercially undesirable and would not solve the problem: the multi-package helper itself failed 3/3 repeatability. Do not implement or ship either form as a production compatibility detector.

Keep DeX placement capability `UNVERIFIED`, preserve the production launcher unchanged, and treat requested bounds as best effort. Any further investigation should first identify a public, repeatable way to reset or observe Samsung's placement state; adding more identity variants without that control is unlikely to produce causal evidence.
