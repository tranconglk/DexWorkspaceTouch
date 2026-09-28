# DWT-VDM-002: Generalize Embedded App Runtime

## Purpose

Refactor the proven DWT-VDM-001 Waze embedding path into one reusable embedded foreign-app runtime while preserving its verified S23 behavior. This milestone exposes no generic product UI and adds no application discovery. The existing **Embedded Waze (Experimental)** route remains the only consumer and the regression golden path.

## Baseline and scope

The implementation starts from commit `04f07348f2c55a99809c39065efbfa00ceccdd72` on `release/1.0-beta`.

The refactor may change the committed `feature/embeddedwaze` implementation, its AIDL bridge, focused tests, and the minimum navigation imports needed after moving classes. It must preserve unrelated working-tree changes in `experiments/shizuku-task-probe`, `docs/compat/COMP-012R_HISTORY_ASSISTED_REUSE_DESIGN.md`, and `AGENTS.md`.

This work does not add an app picker, saved profiles, workspace integration, multiple containers, display resizing, orientation work, audio, clipboard, keyboard, mouse, multi-touch, alternate embedding architecture, licensing changes, backend changes, or release artifacts.

## Package structure

### Generic runtime: `feature/embeddedapp`

The generic package owns all behavior that does not depend on Waze:

- `EmbeddedAppTarget` describes the package name, explicit component name, and virtual display geometry.
- `EmbeddedAppGeometry` represents the fixed virtual width, height, and density used to create and validate the display.
- Generic touch helpers map SurfaceView coordinates into virtual-display coordinates, clamp supported edges, map Android actions, force DOWN pressure to `255f`, and use PALM tool type for CANCEL.
- `EmbeddedAppState` and `EmbeddedAppSession` own app-side Shizuku binding, state delivery, session start, touch forwarding, stop, and disposal.
- A generic AIDL interface transports the target, geometry, Surface, and touch events to the shell UserService.
- The generic UserService owns the association and invokes the one generic runtime.
- The remote runtime owns association creation/removal, trusted VirtualDevice/display creation, touchscreen creation, explicit component launch, task matching, dispatcher readiness checks, and cleanup.

There is exactly one implementation for VirtualDevice creation, virtual display creation, virtual touchscreen events, foreign-task removal, and resource cleanup.

### Waze adapter: `feature/embeddedwaze`

The Waze package remains intentionally small:

- `WazeEmbeddedTarget` supplies the existing proven Waze package/component and `900 x 675 @ 320 dpi` geometry.
- `EmbeddedWazeScreen` preserves the current route title, controls, SurfaceView, and visible behavior.
- The screen constructs the generic session with the Waze target and forwards Surface and touch lifecycle events to it.

No Waze package or component constant may remain in the generic package.

## Runtime data flow

1. `EmbeddedWazeScreen` creates one generic `EmbeddedAppSession` configured with `WazeEmbeddedTarget`.
2. The SurfaceView uses the target geometry for its fixed buffer size.
3. Start passes the Surface and serialized target data through the generic AIDL interface.
4. The shell UserService creates one temporary APP_STREAMING association.
5. The remote runtime creates one trusted VirtualDevice and one Surface-backed trusted virtual display.
6. It creates one `IVirtualInputDevice` virtual touchscreen before launching the target.
7. It waits for the display input configuration to settle, then launches the explicit target component on that display.
8. It identifies only the new task for the configured target package/component and validates that it remains on the VDM display.
9. SurfaceView touch events are mapped into target geometry and sent through the same input device.
10. Stop, Surface destruction, Compose disposal, startup failure, and remote destruction all invoke the same cleanup path.

The generic runtime remains single-session. A second start while active fails rather than creating another container.

## AIDL contract

The existing Waze-named AIDL is replaced by a generic embedded-app service interface. `startSession` receives only the fields needed by the remote runtime:

- output Surface
- package name
- explicit component name
- virtual width
- virtual height
- density DPI

Touch transport remains action, coordinates, pressure, and event timestamp. Stop and destroy retain their current responsibilities.

The remote side validates positive bounded geometry, an explicit component belonging to the supplied package, shell UID `2000`, a valid Surface, and single-session state. Generalization is an internal architecture boundary; it does not create a user-controlled arbitrary-launch endpoint.

## Touch contract

Only one pointer is supported.

- DOWN maps to virtual action `0`, uses finger tool type, and always sends pressure `255f`.
- UP maps to virtual action `1` and preserves the proven pressure behavior.
- MOVE maps to virtual action `2` and preserves the proven pressure behavior.
- CANCEL maps to virtual action `3` and uses virtual PALM tool type `5`, which produces the verified Samsung palm flag behavior and avoids the previous `IllegalArgumentException`.
- Coordinates map proportionally from the current SurfaceView size to the configured virtual geometry and remain clamped to its supported bounds.

No focus workaround is introduced.

## Cleanup contract

Cleanup is one idempotent state machine shared by every exit path. It advances resources in this order:

1. Close `IVirtualInputDevice` at most once.
2. Verify the input descriptor is gone while the VirtualDevice is still alive.
3. Remove only the new foreign task associated with the configured target and display.
4. Close `IVirtualDevice` at most once, which removes its display and policy controller.
5. Remove only the association created for this session.
6. Release local Surface/reference state.

Repeated stop/close/dispose calls become no-ops for already completed steps. If a step fails, cleanup records the first error and continues attempting later resource releases without reordering them. Startup failures enter the same state machine with only the resources already created.

## Error handling and state

The app-side session exposes the existing shell-ready, busy, active, status, and display state needed by the Waze screen. Remote operations return structured success/error data as they do today. The first startup error is reported after cleanup has been attempted.

Task selection uses the pre-start task snapshot plus the configured package/component and expected display. Ambiguous matches fail closed. The runtime never removes a pre-existing task or a task belonging to another package.

## Tests

Generic unit tests cover observable contracts:

1. coordinate mapping
2. edge clamping
3. DOWN mapping
4. MOVE mapping
5. UP mapping
6. CANCEL mapping
7. DOWN pressure `255f`
8. CANCEL PALM tool type
9. cleanup order
10. repeated cleanup/idempotency

Waze adapter tests verify its explicit component and `900 x 675 @ 320 dpi` geometry. Existing useful regression assertions move to the generic layer rather than being duplicated.

## Verification

The implementation must pass:

- `testDebugUnitTest`
- `lintDebug`
- `assembleDebug`
- `git diff --check`
- `scripts/build-device-smoke.ps1`

After those pass, install the production-signed smoke APK with `adb install -r` and run exactly one S23 regression smoke. Verify the route, Waze rendering, `900 x 675` display, touchscreen, DOWN/MOVE/UP, CANCEL without exception, visible map interaction, ordered cleanup, and absence of orphan association, VirtualDevice, display, input device, or Waze probe task.

## Acceptance

DWT-VDM-002 passes only when Waze uses the generic runtime, the generic package contains no Waze constants, only one VDM/touch/cleanup implementation exists, all proven behavior and cleanup ordering remain intact, all build gates pass, and the single S23 regression smoke passes.
