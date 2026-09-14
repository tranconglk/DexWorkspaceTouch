# COMP-005 — DeX Placement Anchor Model Validation

Date: 2026-09-14  
Branch: `release/1.0-beta`  
Verdict: **B. ANCHOR MODEL PARTIALLY SUPPORTED**

## A. Hypothesis

The investigated model predicted that One UI 8 would preserve requested task size while quantizing horizontal position to size-dependent LEFT, CENTER, or RIGHT anchors. Tests used a 16 px investigation tolerance and independently evaluated size and position. The evidence shows strong size-dependent anchor-like placement, but falsifies a simple nearest-anchor or universally quantized model.

## B. Device and environment

- Samsung Galaxy S22 Ultra SM-S908E
- Android 16 / SDK 36 / One UI 8.0
- Wired Samsung DeX, 1920x1080
- Usable work area from every COMP-002 record: `0,0-1920,1024`
- Runtime display ID discovered as `10`; never placed in product code
- Density 1.0 and production margin 8 px

## C. Harness and evidence

A temporary explicit-only Activity built an in-memory `WorkspaceLaunchRequest` and reused the production `AndroidWorkspaceLauncher`, `AndroidSingleAppLauncher`, `ActivitySingleAppLaunchPlatform`, work-area provider, bounds calculator, intent flags, display routing, and `ActivityOptions.setLaunchBounds()` path. Only test geometry changed. It wrote the production COMP-002 latest export to app-external test files; JSON and dumpsys evidence is ignored under `build/comp-005/`.

The harness was removed afterward and was never exposed through customer UI or persisted Workspace data.

## D. Quarter-width probes

Quarter task width was preserved at 464 px. All five cold requests landed at the quarter CENTER anchor `x=728`.

| Probe | Normalized X | Requested left | Actual rect | Nearest anchor | Distance | Position |
|---|---|---:|---|---|---:|---|
| q0 | 0-.25 | 8 | `728,6-1192,1014` | CENTER | 0 | changed |
| q25 | .25-.50 | 488 | `728,6-1192,1014` | CENTER | 0 | changed |
| q375 | .375-.625 | 728 | `728,6-1192,1014` | CENTER | 0 | preserved |
| q50 | .50-.75 | 968 | `728,6-1192,1014` | CENTER | 0 | changed |
| q75 | .75-1 | 1448 | `728,6-1192,1014` | CENTER | 0 | changed |

This strongly supports a center preference for a single quarter-width Chrome task, not selection of the nearest requested anchor.

## E. Third-width probes

Third width was preserved at 624 px. Requests at left, center, and right all landed at `x=648`, the third-width CENTER anchor. Only the centered request retained its requested position. The duplicate center probe reproduced the same result.

## F. Half-width probes

Half width was preserved at 944 px.

| Probe | Requested left | Actual rect | Nearest anchor | Distance | Position |
|---|---:|---|---|---:|---|
| left | 8 | `976,16-1920,1024` | RIGHT | 0 | changed |
| center | 488 | `976,16-1920,1024` | RIGHT | 0 | changed |
| right | 968 | `976,16-1920,1024` | RIGHT | 0 | OEM offset only |
| deliberate non-anchor .125 | 248 | `248,8-1192,1016` | CENTER | 240 | **preserved** |

The deliberately non-anchor request is decisive counter-evidence to universal anchor quantization: One UI accepted an x coordinate 240 px away from the nearest half-width anchor.

## G. Multi-window layouts

| Layout | Observed behavior | Classification |
|---|---|---|
| one app | Size retained; position depends on width/request and OEM heuristic | UNSUPPORTED as arbitrary absolute placement |
| 50/50 | Chrome placed right and Browser left; widths retained | UNSUPPORTED |
| 33/33/33 | Chrome center, Browser right, Calculator left | UNSUPPORTED |
| 25/25/50 | Chrome center, Browser right, Tips left-half | UNSUPPORTED / anchor conflict |
| 50/25/25 | Chrome right-half; Browser and Tips overlap left | UNSUPPORTED |
| 25/50/25 | Chrome center, Tips right-half, Browser left | UNSUPPORTED |
| four quarters | Chrome center, Browser right, Tips and Calculator overlap left | UNSUPPORTED |
| 2x2 | Chrome centered; Tips top-left, Browser bottom-right, Calculator bottom-left | UNSUPPORTED |

These cold results used distinct new tasks after force-stopping only the target packages. Width was usually preserved even when semantic position was not.

## H. Decisive 25/50/25 result

The simple anchor model predicted a pass because quarter-left, half-center, and quarter-right align with candidate anchors. It failed on the first cold run:

- Chrome quarter: `728,6-1192,1014` (center instead of left)
- Tips half: `976,16-1920,1024` (right instead of center)
- Browser quarter: `0,0-464,1008` (left instead of right)

Because the first run failed, the conditional three-run confirmation was not performed.

## I. Four-quarter result

One UI exposed only three effective horizontal placements for four equal-width tasks and overlapped the third and fourth tasks at the left edge. This is consistent with slot/collision behavior, but does not prove a fixed three-anchor capability.

## J. 2x2 and vertical behavior

The requested 2x2 grid was not retained. Three tasks occupied top-left, bottom-left, and bottom-right while Chrome was centered at `488,198-1432,694`, overlapping the grid.

Limited single-task half-height probes requested TOP, CENTER, and BOTTOM. All landed at the same `y=198` with height 496. This is not within 16 px of the nominal top, center, or bottom anchors for a 1024 px work area. Vertical behavior therefore also looks like desktop placement/cascade policy rather than a simple TOP/CENTER/BOTTOM quantizer.

## K. Anchor-distance verdict

Many results exactly match size-derived left/center/right positions: quarter and third singles center, most half singles right, and multi-window tasks frequently at 0/728/1456 or 0/488/976. However:

- the .125 half request remained at non-anchor x=248;
- the vertical tasks remained at non-anchor y=198;
- multi-window assignment depends on launch sequence and collision state;
- some layouts overlap instead of consuming distinct anchors.

Therefore the three anchors are observable placement attractors, not a complete placement model.

## L. App specificity

COMP-004's alternate Google Search / Calculator / Samsung News probe showed the same slot-like substitution. In COMP-005, Calculator participated in equal-thirds, four-quarter, and 2x2 tests and followed the same placement behavior. Samsung News was non-resizable and is excluded from strong conclusions. The evidence favors a general One UI desktop placement policy rather than a Chrome/Browser/Tips-only defect.

## M. Hypothesis verdict

**ANCHOR MODEL PARTIALLY SUPPORTED.** Size-specific LEFT/CENTER/RIGHT attractors are real and predict many actual x values, but a simple quantization rule is rejected. A better descriptive model is One UI freeform placement with size-based preferred slots, launch-order assignment, cascade/collision avoidance, and occasional acceptance of absolute non-anchor bounds.

## N. Safe-layout matrix

No tested layout can be declared generally safe from this harness ordering on the current S22. Previous COMP-003 production runs showed some 50/50 and equal-third ratios with only OEM offsets, demonstrating that host/task state and target set also matter. The conservative current matrix is:

| Layout | Current S22 classification |
|---|---|
| one app | UNSUPPORTED for arbitrary absolute X |
| 50/50 | UNSTABLE across investigation contexts |
| 33/33/33 | UNSTABLE across investigation contexts |
| 25/25/50 | UNSUPPORTED |
| 50/25/25 | UNSUPPORTED |
| 25/50/25 | UNSUPPORTED |
| 25/25/25/25 | UNSUPPORTED |
| 2x2 | UNSUPPORTED |

## O. Capability profile implications

A future capability vocabulary such as `ABSOLUTE_BOUNDS` versus `ANCHORED_BOUNDS` is directionally useful, but `ANCHORED_BOUNDS` alone is too coarse for the observed collision/cascade behavior. A more honest capability result would include `ABSOLUTE`, `HEURISTIC_FREEFORM`, and `UNVERIFIED`, scoped to a display configuration. Do not infer it only from Samsung manufacturer, model, or One UI version.

## P. Self-probe feasibility

A runtime self-probe is feasible with public APIs if DexWorkspaceTouch launches its own temporary Activities on the active external display, requests deliberately non-anchor rectangles, and has each Activity report its actual `WindowMetrics` back to a coordinator. It could distinguish absolute acceptance from heuristic placement without privileged task inspection.

Risks include visible window flashes, perturbing the desktop collision state, Samsung's visible-window limit, lifecycle cleanup, and a probe result that changes after display resolution/session changes. It should require explicit user consent, run only on an attached DeX display, clean up all owned tasks, and cache results per display mode/work-area signature—not silently run at every startup. This remains analysis only.

## Q. Production recommendation

Keep the production launcher, ordering, delay, Editor, flags, work-area logic, and device gating unchanged. Do not restrict layouts based on the simple anchor hypothesis. If commercial support requires predictable S22 layouts, investigate a user-initiated owned-Activity self-probe and post-launch verification as separate milestones before defining a compatibility profile.
