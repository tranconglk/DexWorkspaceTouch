# UI-007 — Final Visual Consistency & Release Polish Audit

Date: 2026-09-12

Branch: `release/1.0-beta`
Accepted baseline: UI-001 through UI-006A (`5ef9188`)

## Outcome

The production visual redesign is complete. UI-007 found one objective remaining inconsistency: descriptive and control copy in Car Mode mixed English with the otherwise Vietnamese product UI, including accessibility descriptions. That copy was aligned without changing layout, navigation, state ownership, workspace execution, overlay lifecycle, or security behavior.

## Final design system

- Deterministic dark theme; no wallpaper-derived or dynamic color.
- Graphite-navy layers use the UI-001A semantic palette: background, surface, alternate surface, outline, primary, accent, muted, success, warning, and error.
- Compose and Android View Floating Dock use equivalent authoritative tokens.
- Screen, section, body, label, and metadata hierarchy uses the shared Material typography configured by UI-001.
- Shared screen padding, section/card gaps, radii, borders, and 48/56/64 dp touch policy remain authoritative.
- Primary blue is reserved for primary action/selection; secondary actions remain outlined or text actions.
- Pressed/focus/selected states are visible and do not depend on hover.

## Language policy

- Product and feature names remain English when they are stable user concepts: DexWorkspaceTouch, Workspace, Car Mode, Floating Dock, and Car Dock.
- Descriptive text, statuses, state descriptions, and control labels use Vietnamese.
- Technical diagnostic labels in About may remain English where translation would reduce support clarity, including build, database, format, and display identifiers.
- Internal identifiers, enum names, persistence keys, and protocol values are unchanged.

UI-007 aligned Car Mode copy including Truy cập nhanh, Cấu hình bảng điều khiển, Số lối tắt hiển thị, Lối tắt Workspace, configuration/unavailable states, Workspace selector controls, Floating Dock status/actions, Desktop shortcut feedback, workflow progress/errors, and related accessibility descriptions.

The exact UI-006A accessibility action contracts remain unchanged: `Open Car Dock`, `Collapse Floating Dock`, and `Hide Floating Dock`.

## Screen-by-screen status

| Surface | Status | Final audit result |
|---|---|---|
| Workspace Library / cards | Resolved | Workspace-first hierarchy, constrained desktop toolbar, dark layered cards, previews, primary/secondary hierarchy, pressed/focus and selection presentation are consistent. |
| Create/manage/dialog flows | Resolved | Responsive capped surfaces and semantic button hierarchy are consistent; behavior remains unchanged. |
| Workspace Editor | Resolved | Canvas remains dominant; toolbar, controls, app identity and selected state use the shared visual language. Coordinate/gesture geometry is untouched. |
| App Picker | Resolved | Responsive grid, filters, card density, icons, pressed/focus and selected state are consistent. |
| Update | Resolved | Centered trust surface, status tone, version hierarchy and constrained actions replace the stretched phone-form appearance. |
| License / Activation | Resolved by source audit | Shared trust/status presentation is applied while `FLAG_SECURE`, masking, obscured-touch protection, redaction and protocol behavior remain intact. Runtime capture intentionally omitted. |
| Launch/transfer/status/dialogs | Resolved | Shared status language and semantic tone are used where behavior-safe. Existing blocking/recovery semantics are unchanged. |
| Car Mode / dashboard | Resolved | Workspace-first hierarchy and dashboard remain accepted; UI-007 completes Vietnamese descriptive/control and accessibility copy. |
| Floating Dock collapsed/expanded | Resolved | Compose/View brand parity, visual workspace cards, pressed feedback, drag affordance, Collapse and Hide actions are consistent. Lifecycle and geometry are unchanged. |
| Desktop shortcut | Resolved | Native entry remains intentionally minimal; Car Mode feedback is now language-consistent. |
| About | Resolved | Capped responsive information surface and action hierarchy are consistent; support-oriented technical labels remain intentionally technical. |
| Empty/error/loading states | Resolved | Semantic status surfaces and established warning/error colors are used where safe; no state machine was changed for visual uniformity. |

## Consistency issues fixed in UI-007

1. Replaced mixed English descriptive headings in Car Mode with Vietnamese while retaining product terms.
2. Aligned Workspace shortcut configured, unconfigured, unavailable, selection, count and dialog copy.
3. Aligned Floating Dock control statuses and actions shown inside Car Mode.
4. Aligned Desktop shortcut request feedback.
5. Aligned workflow progress/error presentation and accessibility/state descriptions.
6. Updated presentation-contract tests for the new user-facing strings.

No objective typography, spacing, palette, surface, radius, or button-hierarchy defect remained after the accepted UI-001 through UI-006A baselines. Those values were deliberately not changed merely to create a visual delta.

## Accessibility status

- Production touch targets retain the 48 dp minimum and use 56/64 dp for comfortable/primary Car controls.
- Workspace cards, App Picker tiles, Car dashboard cards and Floating Dock tiles retain pressed feedback and keyboard-focus visibility.
- Selected shortcut-count semantics now expose Vietnamese content and state descriptions rather than relying on color.
- Unconfigured/unavailable Workspace cards expose explicit accessibility descriptions.
- Disabled, progress, warning and error states remain represented by text/semantics in addition to color.
- Floating Dock open/collapse/hide actions remain independently focusable with explicit descriptions and at least 48 dp targets.

## Responsive validation

Samsung S23 Ultra external DeX display was detected dynamically as logical display `139`, physical display `4632669056402301701`, at 1920×1200.

- 1920×1200: Home, Editor, App Picker, Update, Car Mode and both Dock states render without clipping.
- Approximately 800×700 freeform: Car content remains readable and scrollable without horizontal overflow.
- Approximately 560×700 freeform: two-column dashboard remains within the window, cards/previews remain identifiable, and lower sections remain available by vertical scrolling.
- Existing responsive source constraints for Home, Editor, App Picker, Update, dialogs and activation were reviewed; no new layout regression was found.

## UI-000 comparison

| UI-000 finding | Final status |
|---|---|
| Wallpaper/light-theme dependence | RESOLVED — deterministic dark theme. |
| Compose and Dock looked like separate products | RESOLVED — shared semantic palette and visual language. |
| Home resembled phone UI stretched on DeX | RESOLVED — constrained desktop Workspace Library composition. |
| Update resembled phone UI stretched on DeX | RESOLVED — focused, constrained trust/update surface. |
| Default typography lacked hierarchy | RESOLVED — shared product typography hierarchy. |
| Weak card/surface depth | RESOLVED — graphite-navy layered surfaces and semantic outlines. |
| Editor chrome and App Picker density | RESOLVED — visual polish while preserving editor geometry and selection behavior. |
| Car launch/configuration hierarchy | RESOLVED — workspace-first dashboard and separated configuration sections. |
| Floating Dock looked technical/debug-oriented | RESOLVED — branded handle/cards/header/actions with Compose parity. |
| Dedicated Settings route | INTENTIONALLY UNCHANGED — it does not exist and UI milestones do not invent it. |
| General onboarding | INTENTIONALLY UNCHANGED — activation remains the existing conditional gate. |

## Deliberately unchanged

- Workspace repository, import/export, launch validation, bounds, sequencing and partial-failure behavior.
- Editor gestures, divider logic, merge/split, canvas geometry, app assignment and save semantics.
- Car dashboard structure, shortcuts, workflow mapping and runner behavior.
- Floating Dock overlay type, permission, display targeting, lifecycle, drag/snap, slot count, workspace launch, auto-collapse and self-hide behavior.
- License and update security boundaries and release signing identity.
- Product/feature names and support-oriented diagnostic terminology.
- UI-006A Dock action content descriptions, which are accepted explicit accessibility contracts.

## Release-reference screenshots

Ignored runtime evidence is stored under `app/build/ui-007/`:

- `home.png`
- `editor.png`
- `app-picker.png`
- `update.png`
- `car.png`
- `dock-collapsed.png`
- `dock-expanded.png`

Activation is not captured because `FLAG_SECURE` is a required security boundary and was not bypassed.

## Remaining visual debt

No verified production visual issue remains that justifies another redesign milestone. Native DeX window chrome and launcher presentation are platform-owned. Support diagnostics intentionally retain a small amount of English technical terminology. Neither is a product visual defect.

## Verification

- `testDebugUnitTest --rerun-tasks`
- `lintDebug`
- `assembleDebug`
- `assembleDebugAndroidTest`
- production `assembleRelease` and signing verification
- S23 Ultra DeX runtime and responsive smoke tests
- `git diff --check`, generated-output and secret audit before commit

## Conclusion

**VISUAL REDESIGN COMPLETE**
