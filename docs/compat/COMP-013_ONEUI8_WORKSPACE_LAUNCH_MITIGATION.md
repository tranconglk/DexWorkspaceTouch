# COMP-013 — One UI 8 Workspace Launch Mitigation

Date: 2026-09-15

Branch: `release/1.0-beta`

Baseline: `94991fbf572c9b0bf2423236197d3113a59ab6f7` (`COMP-012 document workspace window policy design`)

Scope: investigation and feasibility only. No production workaround, saved geometry change, task reuse, foreign-task control, hidden API, version change, publish, or push.

## A. Baseline

The device under test was a Samsung Galaxy S22 Ultra (`SM-S908E`), Android API 36 / One UI 8, connected at `192.168.1.183:5555`. The active external DeX display was dynamically assigned display ID `14`, mode `1920 x 1080`; the measured desktop work area was `[0,0-1920,1024]` at DeX logical density `1.0`.

Production creates explicit-component activities on the validated display with requested bounds, `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK`, and the existing 400 ms sequencing delay. COMP-009's established Samsung cascade is:

`CENTER → BOTTOM_RIGHT → TOP_LEFT → BOTTOM_LEFT → TOP_RIGHT → CENTER`

with a 48 px separation threshold. COMP-010/011 already established that reuse does not reposition and a consumer APK cannot reposition arbitrary third-party tasks.

Raw captures, simulator output, and the temporary harness build remain ignored under `build/comp-013/`. No raw device dump is committed.

## B. Production failure

Requested rectangles are correct, but One UI may replace their position with a cascade anchor of the same requested size. Therefore the failure is not normalized geometry or bounds conversion. It is placement after the request reaches Samsung's desktop task policy.

The critical distinction is preserved: a result can be predictable yet wrong. A target consistently placed at `CENTER` is still a failure when its saved slot is `LEFT`.

The existing saved order is unsuitable for several common layouts. For example, 50/50 left-then-right is predicted as right-anchor then left-anchor, swapping both logical slots. For 25/25/50 and 2x2, the finite anchors cannot represent all saved positions without overlap or slot error.

## C. Cascade simulator

An investigation-only JavaScript simulator in `build/comp-013/cascade-simulator.mjs` implements the COMP-009 state cycle, center calculation, directional separation check, and 48 px threshold. It accepts the work area, prior visible freeform rectangle, and an ordered list of requested target sizes, then emits position class and predicted rectangle.

Historical validation reproduced the unique COMP-009 transition signatures exactly, including:

| Previous / requested shape | Predicted class | Predicted rectangle | Prior capture | Error |
|---|---|---:|---:|---:|
| DWT coordinator → quarter | CENTER | `[728,6-1192,1014]` | same | 0 px |
| DWT coordinator → half | BOTTOM_RIGHT | `[976,16-1920,1024]` | same | 0 px |
| centered quarter → quarter | BOTTOM_RIGHT | `[1456,16-1920,1024]` | same | 0 px |
| bottom-right predecessor → top-left-sized target | TOP_LEFT | `[0,0-944,1008]` | same | 0 px |

These unique signatures cover the repeated 12/12 controlled transitions documented in COMP-009. The generated permutation matrix is `build/comp-013/simulation-results.json`.

## D. Layout corpus

The corpus follows actual designer shapes rather than invented arbitrary test layouts:

- two columns (50/50);
- three columns represented as 25/25/50, 50/25/25, and equal thirds;
- four-grid / four quarters (2x2);
- asymmetric left/right templates (30/70 and 70/30);
- full-width and row-oriented shapes were reviewed against the same anchor limitation.

All desired rectangles use the production 8 px outer and 16 px internal gaps on the measured 1920 x 1024 work area.

Classification: `EXACT` ≤2 px; `ACCEPTABLE_DECORATION` ≤16 px with correct slot and no unintended overlap; otherwise `WRONG_SLOT`, `OVERLAP`, or `SIZE_WRONG`. A workspace passes only if every target is exact/acceptable and there is no unintended overlap.

## E. Current order results

| Layout | Current saved-order simulation | Classification |
|---|---|---|
| 50/50 | both apps exchanged left/right | WRONG_SLOT |
| 25/25/50 | center/right/left anchors; material overlap | OVERLAP / WRONG_SLOT |
| 50/25/25 | right/left/bottom-left distribution | WRONG_SLOT |
| thirds | center/right/left instead of left/middle/right | WRONG_SLOT |
| 2x2 | center, bottom-right, top-left, bottom-left | OVERLAP / WRONG_SLOT |
| 30/70 | narrow target centered over wide target | OVERLAP |
| 70/30 | both logical sides wrong | WRONG_SLOT |

This agrees with the decisive COMP-003/009 observation: request fidelity is not final placement fidelity.

## F. Permutation upper bound

All feasible launch permutations were enumerated while preserving app-to-cell identity and target size.

| Layout | Best execution order | Best max edge error | Passing permutations | Upper bound |
|---|---|---:|---:|---|
| 50/50 | right, left | 8 px | 1 | full pass from assumed DWT seed |
| 25/25/50 | middle quarter, right half, left quarter | 240 px + overlap | 0 | partial only |
| 50/25/25 | middle quarter, right quarter, left half | 240 px + overlap | 0 | partial only |
| thirds | middle, right, left | 8 px | 1 | full pass from assumed DWT seed |
| 2x2 | top-right, bottom-right, top-left, bottom-left | 480 px + overlap | 0 | partial only |
| 30/70 | right 70%, left 30% | 8 px | 1 | full pass from assumed DWT seed |
| 70/30 | right 30%, left 70% | 672 px + overlap | 0 | no faithful result |

Thus ordering has useful mathematical value only for a subset, and even those predictions depend on a known initial repository predecessor/state.

## G. Device order validation

The temporary visible DWT Activity used the same component identities, display, requested bounds, flags, and 400 ms delay as production; only execution order changed. It was removed after measurement.

### 50/50 candidate

Order `right, left` produced `[976,16-1920,1024]` and `[0,0-944,1008]`, both 8 px from desired, in the first run. Three observed runs were visually correct, but runs 2 and 3 reused Samsung Browser's `singleTask` task rather than creating two wholly fresh targets. They therefore do not satisfy an uncontaminated deterministic 3/3 proof.

### Equal-thirds candidate

Order `middle, right, left` was repeated three times from newly launched DWT hosts:

| Repetition | Middle | Right | Left | Result |
|---:|---|---|---|---|
| 1 | top-right | center | bottom-right | FAIL |
| 2 | bottom-left | top-right | center | FAIL |
| 3 | center, 2 px | bottom-right, 8 px | top-left, 8 px | PASS |

Result: 1/3. The simulator is correct for a supplied seed, but the normal public launch path does not establish the assumed repository predecessor deterministically.

No device claim is made for order-only layouts that the simulator proves cannot pass (25/25/50, 50/25/25, 2x2, 70/30). Testing more permutations cannot exceed that geometric upper bound.

## H. DWT host seed

The DWT coordinator Activity itself was consistently visible on display 14 with bounds `[441,161-1479,863]`, classified `CENTER`. However, its visible bounds are not sufficient to prove it is Samsung's effective placement predecessor. Equal-thirds produced three different cascade phases despite a fresh DWT host with the same bounds.

Conclusion: the normal Home flow exposes a known DWT-owned visible rectangle but **does not provide a stable effective cascade seed 3/3**. Consumer APIs do not expose Samsung's desktop placement repository, so production cannot verify or repair this state before launching.

First-launch retention without a visible freeform predecessor is **UNAVAILABLE** for safe testing: establishing it would require clearing/manipulating arbitrary desktop task state, which this milestone forbids.

Public, normal DWT Activity behavior reliably offered the current centered host only. No evidence established safe control of all five Samsung position classes without changing the user's visible DWT window or relying on repository state.

## I. Interleaved owned-seed experiment

For 25/25/50, the harness used only its own `AppTask.moveToFront()`:

`DWT host → Chrome → DWT host → Samsung Browser → DWT host → Tips`

The timestamps show three explicit foreground resets at 72,227,574 ms, 72,228,173 ms, and 72,228,760 ms, followed by target launches at 72,227,726 ms, 72,228,327 ms, and 72,228,912 ms. The sequence added about 450 ms of deliberate host-reset time on top of normal launch sequencing.

This does not create independent arbitrary placement. Every requested size still resolves through the finite Samsung anchor set, and the middle-quarter saved position is not representable faithfully from a centered seed. It also repeatedly changes foreground ownership.

## J. UX/focus cost

Interleaving visibly returns DWT to the foreground before every app. That creates focus thrashing/window flashing, interrupts each newly launched app, increases latency, and can leave final Z-order dependent on the final transition. It fails the production UX bar even if an individual anchor prediction is correct.

No invisible/transparent seed task is proposed. Such a task would be a fake-task hack rather than normal explainable UI behavior.

## K. Layout compatibility classes

| Layout | Simulation class | Device confidence | Investigation class |
|---|---|---|---|
| 50/50 | compatible from assumed center seed | qualified, reuse-contaminated 3-run sample | CASCADE_APPROXIMATE |
| thirds | compatible from assumed center seed | only 1/3 | CASCADE_UNSUPPORTED |
| 30/70 | compatible from assumed center seed | not promoted without stable seed proof | CASCADE_APPROXIMATE |
| 25/25/50 | no passing permutation | mathematical rejection | CASCADE_UNSUPPORTED |
| 50/25/25 | no passing permutation | mathematical rejection | CASCADE_UNSUPPORTED |
| 2x2/four-grid | no passing permutation | mathematical rejection | CASCADE_UNSUPPORTED |
| 70/30 | no faithful permutation | mathematical rejection | CASCADE_UNSUPPORTED |
| arbitrary/custom | finite-anchor mismatch | general proof below | CASCADE_UNSUPPORTED |

No class meets the COMP-013 production success bar because no candidate combines uncontaminated deterministic 3/3 behavior, known seed, saved-layout fidelity, and acceptable focus behavior.

## L. Arbitrary-layout feasibility

Arbitrary normalized geometry is not achievable with launch order and a DWT-owned seed alone. For a fixed requested width/height, Samsung chooses among five position classes plus the conditional centered retention rule. Those anchors form a finite set. Saved workspace coordinates form a continuous two-dimensional space; most left/top coordinates do not coincide with an anchor.

Order only selects the predecessor sequence. It cannot manufacture a missing anchor. The 48 px threshold can preserve `CENTER` directionally, but cannot independently place a target at an arbitrary middle-quarter coordinate. Multiple differently sized windows may also overlap because anchors are computed per target size rather than as a tiling constraint.

Therefore one successful 50/50 arrangement cannot generalize to the Workspace Designer's arbitrary geometry contract.

Preview implication: the current exact saved-design preview remains truthful to user intent, but on affected One UI behavior a future UI should add a compatibility warning. A predicted-actual preview is only supportable if the initial seed/cascade state becomes observable; current production cannot observe it reliably.

## M. Car/Dock implications

Home and Car Mode execute through the Activity-scoped workspace composition and can have a visible DWT Activity, but the experiment shows that visibility does not guarantee effective seed state. Floating Dock can execute while DWT is backgrounded through its external-display host/runtime; it has no predictably foreground DWT-owned freeform task at launch time. Therefore an order strategy inferred from Home must not be assumed safe for Car or Floating Dock.

No Car-specific behavior, separate launcher, or seed mechanism is justified. All entry points must continue sharing the repository → request factory → runtime path.

## N. Cross-device risk

This evidence is specific to S22 One UI 8 and the current DeX session. No S23 was available for this milestone, so no cross-device claim is made. A mitigation must never be enabled globally or keyed permanently to `SM-S908E`.

Android/Samsung version checks are only coarse hints. Production cannot inspect foreign task bounds or Samsung's placement repository. A DWT-owned self-probe could observe only its own launch/lifecycle, not prove how every third-party component will be positioned, and would itself alter the cascade state. No reliable, side-effect-free public capability detector was established.

## O. Product mitigation options

Ranked by evidence:

1. **Layout compatibility restriction/warning.** Most honest and safe: warn that One UI 8 may reposition workspace windows and document known unsupported shapes.
2. **Best-effort current behavior with explicit limitation.** Preserve saved requests and existing cross-device behavior; do not promise exact placement on affected Samsung builds.
3. **Cascade-aware launch order.** Useful as an investigation tool and may improve individual templates, but not production-ready without a stable seed and uncontaminated 3/3 validation.
4. **DWT-owned seed plus order.** Rejected for production: interleaving causes focus flashing/latency and still cannot represent arbitrary geometry.

Do not resume launch-flag, delay, race, foreign-task, root, Shizuku, Accessibility, or hidden-API searches; earlier milestones and this upper-bound analysis rule them out.

## P. Final verdict

**D — NO PRODUCTION-SAFE MITIGATION WITH PUBLIC APIs**

Reason: order-only can mathematically improve a small subset, but its correct result depends on an effective cascade seed that the consumer app cannot reliably observe or establish. The strongest fresh multi-window candidate passed only 1/3 on device. Owned-task interleaving fails UX and still cannot represent arbitrary saved coordinates. Several real template classes have zero passing permutation even under the ideal assumed seed.

No COMP-014 launch planner is recommended from this evidence. The next milestone should be product/UX handling: an affected-environment compatibility warning, best-effort wording, and supported-layout guidance. It must not claim a predicted actual layout unless a safe observable capability later exists.

### Cleanup and verification checklist

- temporary `Comp013MitigationProbeActivity`: removed;
- temporary manifest registration: removed;
- production launch flags/delay/runtime: unchanged;
- saved Workspace geometry: unchanged;
- foreign tasks: not programmatically moved, resized, removed, or enumerated by production code;
- simulator/raw evidence: ignored under `build/comp-013/`;
- no version bump, publish, push, or production mitigation.

Final verification on the cleaned production source tree:

- `testDebugUnitTest`: PASS;
- `lintDebug`: PASS;
- `assembleDebug`: PASS;
- `assembleDebugAndroidTest`: PASS;
- `assembleRelease`: PASS;
- combined production gate: `BUILD SUCCESSFUL` (138 actionable tasks; 44 executed, 94 up-to-date);
- clean release APK: `DexWorkspaceTouch-1.0.0-beta.8-9.apk`;
- APK SHA-256: `2ad4e8fa567440433782495b34075773957c0af582526d29262cdcaac1edc601`;
- signer SHA-256: `19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7` (unchanged);
- cleaned release reinstalled successfully on the S22; the temporary probe Activity is absent from installed package metadata.
