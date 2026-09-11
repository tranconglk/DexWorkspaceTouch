# UI-000 — Current UI Visual Audit

Date: 2026-09-11

Scope: production UI only; read-only audit

Target: **Modern Dark + AMOLED Premium + Car Focus touch sizing**

## Executive summary

DexWorkspaceTouch has a sound functional UI foundation: workspace previews communicate layouts well, the editor correctly prioritizes its canvas, CAR-022 gives Car Mode a useful workspace-first dashboard, and most important controls use deliberate 48–64 dp touch targets. It does not yet feel like one premium DeX product. The application defaults to a light, wallpaper-derived Material palette with default typography/shapes, while Floating Dock hard-codes a separate dark-blue language. Home and Update also resemble phone forms stretched across 1920×1200.

The redesign should preserve navigation/state ownership, workspace preview semantics, editor gestures, launch behavior, display targeting, overlay lifecycle, and licensing security. UI-001 should first establish one deterministic dark design system and Compose/Android View token parity.

## Method and evidence

- Read production callers from `MainActivity`, `TouchNavigation`, `LicenseGate`, Home/Library, editor, picker, Car Mode, Floating Dock, updater, dialogs, and launch presentation. Previews/tests/dead UI were excluded.
- Audited connected Samsung S23 Ultra (`SM-S918B`) on live external DeX.
- Runtime-detected logical display **133**, HDMI **1920×1200**; physical screenshot display **4632669056402301701**. No historical display ID was assumed.
- Reused existing data without saving changes or altering license/workspace/shortcut state.
- `docs/ui/reference/dexworkspacetouch-final-direction.png` is **missing**. Therefore no image-to-image comparison is possible; findings use the written direction only.

## Production UI inventory

| Area | Production path | Audited surfaces | Capture |
|---|---|---|---|
| Shell/license | `MainActivity` → `LicenseGate` → `TouchNavigation` | licensed pass-through; secure activation form | Source only; device already licensed |
| Home/Library | Home route → `HomeScreen` | header/actions, search/sort/file, pinned/regular grids, empty/search, manage | Yes |
| Creation | Home → `WorkspaceTemplatePickerDialog` → designer | responsive template dialog and creation entry | Source; no creation committed |
| Editor | designer route → `LayoutDesignerScreen` | canvas/cells/dividers, selection, context tools, undo/redo, save/open, merge | Yes; existing workspace, no save |
| App Picker | editor → `AppPickerScreen` | search, filters, app grid, clear/remove | Yes |
| Launch/transfer | Home/card → status; navigation-level transfer states | launch states, import/export, backup/restore, confirmations/errors | Source |
| Car Mode | Car route → `CarScreen` | six-slot dashboard, overlay, shortcut, visible count, assignment | Yes |
| Floating Dock | coordinator → `AndroidCarOverlayPlatform` | collapsed edge tab, expanded visual grid, empty cards | Yes |
| Desktop shortcut | `CarDockShortcutActivity`/bootstrap | launcher entry and dock outcome; no dedicated screen | Icon/dock |
| Update | Updates route | version/status/check/update/back | Yes |
| About | Home file flow → `AboutDialog` | build/device/support/privacy/copy actions | Source |
| Settings | — | **CHƯA CÓ** dedicated route; Car config is embedded in Car Mode | N/A |

## Screen reviews and scores

Scores are Visual / Consistency / Touch / DeX / Commercial readiness.

### Home / Workspace Library — **5 / 6 / 8 / 5 / 5**

- **Hierarchy:** title and Create are legible, but stacked Car/Update/About/Create/search utilities compete with workspace content. The product’s main asset begins too low.
- **Layout/density:** wide pill controls span nearly the whole display; large empty bands coexist with a grid partly below the viewport. Cards use desktop width better than the header.
- **Type/surfaces/color:** default small Material hierarchy; beige surfaces and faint outlines merge. Live dynamic color produced mustard-brown accents, not deterministic TouchBlue/AMOLED.
- **Icons/touch:** app icons in previews are excellent recognition anchors; top actions are text-heavy. Touch targets are generous.
- **Verdict:** functional landing screen, but it looks like a stretched internal tool rather than paid desktop software. Preserve previews/cards; rebalance the shell.

### Workspace creation/template picker — **6 / 7 / 8 / 7 / 6**

Responsive width/height caps, grid, section headers, and 56 dp actions are desktop-aware. The structure is sound, but default dialog chrome/type is generic and the transition from sparse Home to a dense dialog feels visually abrupt. Empty/loading/error states remain text-led.

### Workspace Editor — **6 / 7 / 9 / 8 / 6**

- **Hierarchy/layout:** the large 16:10 canvas is correctly dominant and makes best use of DeX. Back/Undo/Redo/status are too small and visually uncontained; bottom Open/Save actions are excessively wide.
- **Canvas:** intentional border, selection, divider and 64 dp hit-area modeling is a strong core. App identity in the inspected canvas was weaker than Library because labels, not prominent icons, dominated.
- **Visual issues:** toolbar framing, surface separation, type/icon hierarchy, selected-state contrast.
- **Interaction observation:** tapping a cell routed directly to App Picker. This is existing behavior, not a visual defect. Do not alter gestures, divider/merge/undo/persistence/save behavior during visual work.

### App Picker — **6 / 7 / 9 / 8 / 6**

Search plus three filters lead clearly into a six-column grid. Icons/names are highly recognizable and items are touch-friendly. Filters stretch too far, 280×168 dp minimum cards contain excess blank space, and the default beige styling is flat. Structurally one of the stronger desktop screens.

### Launch and transfer states — **5 / 7 / 8 / 6 / 6**

Checking, launching, completed, readiness/launch errors, cancellation, import preview, backup/restore and confirmations all have explicit production states. Standard `AlertDialog`/bottom-sheet presentation is safe but makes operationally different states look alike. Add semantic icon/color hierarchy later without changing state or recovery behavior.

### Car Mode / dashboard — **7 / 7 / 9 / 8 / 7**

- The workspace-first two-column six-slot dashboard is correct and should be polished, not redesigned structurally.
- Visual workspace previews and empty `+` slots scan well. Cards are wide/short; the long full-width configuration area blends with launch content.
- 56/64 dp targets suit touch-first DeX. The settings half still resembles a tablet form.
- Keep IA, grid, slots, previews, overlay controls and persistence. Apply tokens, separate “launch” from “configure,” constrain settings width, and strengthen active/selected states. Do not rewrite workspace loading, runner, navigation, or overlay coordination.

### Floating Dock — **6 / 4 / 8 / 9 / 6**

- **Collapsed:** compact left-edge navy `CAR` tab is unobtrusive, but under-branded; tap versus drag affordance is ambiguous.
- **Expanded:** useful 4×2 grid of eight visual workspace tiles. Layout recognition is strong, though content is cramped and `Collapse` is bare text.
- **Consistency:** Android View colors are hard-coded (`#142C48` base plus local blue surfaces/borders and white), conflicting with light Compose screens. Align tokens without replacing the View renderer or changing gesture/window behavior.
- Add a subtle drag handle and pressed/dragging treatment while preserving hit regions and arbitration.

### Desktop shortcut — **6 / 6 / 8 / 9 / 6**

This is a native launcher entry/bootstrap, not a full screen. It exposes Car Dock appropriately on the DeX desktop. The icon is recognizable; shown/permission/display-unavailable outcomes lack a unified branded presentation.

### Update — **3 / 5 / 7 / 2 / 3**

Almost the entire 1920×1200 surface is empty. Version/status and full-width outline actions form a shallow stack at the top with no focused panel, release-note hierarchy, progress surface, or trust framing. This is the clearest phone-layout-stretched-to-desktop example and the weakest commercially visible screen.

### License / Activation — **5 / 6 / 8 / 7 / 6**

Source audit only because the device was licensed. The form is centered, scroll-safe, capped at 520 dp, masks the key, uses a 56 dp CTA, and preserves `FLAG_SECURE`/obscured-touch protection. Keep all security boundaries. Presentation is plain: default type/color, limited trust cues, and small request-ID/status text. Improve semantic error/status treatment without exposing license material or weakening masking/capture protection.

### Settings, onboarding, empty/error/dialog surfaces

- Dedicated Settings: **CHƯA CÓ**. Do not invent one in a visual milestone.
- General onboarding: **CHƯA CÓ**; activation is the conditional startup gate.
- Empty/unavailable states are understandable but mostly plain text or `+` symbols.
- Template/About use capped custom dialogs; launch/import/export/backup/restore/merge/rename/delete use Material dialogs/sheets; Car assignment uses a scroll-bounded alert. Touch sizes are generally good, but width, severity, icon and button hierarchy are not unified.

## Strong points to preserve

1. Workspace preview as the product-specific visual language.
2. CAR-022 workspace-first two-column Car dashboard.
3. Editor’s canvas-first 16:10 composition.
4. Shared 48/56/64 dp touch discipline and 64 dp divider hit area.
5. Explicit launch, transfer, update, cancellation and license states.
6. Responsive grids/caps already present in App Picker, template picker, About and cards.
7. Floating Dock’s persistent edge model, collapsed/expanded modes, previews, drag/snap and background execution.

## Weaknesses by severity

### CRITICAL

1. `DexWorkspaceTouchTheme(darkTheme = false)` uses `dynamicLightColorScheme` on Android 12+, contradicting the approved deterministic AMOLED-dark direction across all Compose UI.
2. Compose and Floating Dock look like separate products: wallpaper-derived beige versus hard-coded navy View styling.

### HIGH

1. Home and Update use weak desktop composition: full-width stacks and excessive whitespace.
2. `AppTypography` is default `Typography()`; there is no branded title/section/body/metadata hierarchy.
3. Background, cards, fields, settings and dialogs have insufficient surface depth.
4. Home utilities visually outweigh workspace content.
5. Update presentation is not commercially ready despite its functional flow.

### MEDIUM

1. Car launch content and configuration need clearer sections and width constraints.
2. Editor chrome and selected states need polish, without interaction changes.
3. App Picker items/filters are unnecessarily large even for touch.
4. Dialog success/warning/error/destructive severity is visually generic.
5. Empty/loading/unavailable states and top-level actions are text-heavy.
6. Three workspace-preview renderers lack shared visual parity.

### LOW

1. Repeated/local radii, borders, preview gaps and icon sizes.
2. Minimum-height metadata rows can look padded with short content.
3. Dock branding, collapse and drag affordances are understated.
4. Pressed/focused/selected transitions are mostly default or immediate.

## Reference-direction comparison

Reference image: **MISSING**.

**KEEP:** preview model, Car dashboard IA/grid, editor canvas, touch policy, Floating Dock behavior.

**CHANGE:** deterministic near-black layered surfaces; restrained blue primary and sparse cyan accent; stronger type hierarchy; constrained desktop controls; one-pixel borders/consistent radii; shared Compose/View brand; workspace content first on Home.

**DO NOT COPY/INVENT:** mockup-only features, new Settings/onboarding/navigation/actions, neon glow, heavy glass/blur, or decorative animation.

## Component and theme consistency audit

### Existing base

- `ui/design/Spacing`: 2, 4, 8, 16, 24, 32, 48 dp.
- `TouchTargets`: 48 minimum, 56 secondary/filter, 64 primary/divider.
- `Dimensions`: editor geometry, preview icons/borders, card/grid dimensions, breakpoints and dialog caps.
- `DesignerShapes`: workspace 16 dp, cell 6 dp, pill feedback.
- Reusable Library card/snapshot, templates, editor components, and Car workspace components are ready to receive tokens.

### Duplication/drift

- Global 16/6 dp shapes coexist with Car-local 16/10/6 dp and Material defaults.
- Theme has only `TouchBlue #0061A4` and `TouchBlueDark #9ECAFF`; overlay separately hard-codes multiple navy/blue/white values.
- `WorkspaceGrid`, `AppPickerGrid`, and `TemplateGrid` all equal 16 dp; other local gaps/padding remain feature literals.
- No product typography tokens; screens choose default styles/weights.
- 64/56 dp policy coexists with default Material buttons/text actions and a separate overlay-card family.
- Section headers vary by feature.
- Library `WorkspaceSnapshot`, Car `CarWorkspacePreview`, and View-based `CarFloatingWorkspaceShortcut` are legitimately separate renderers, but should share a spec for surfaces, borders, gaps, icon tiers, labels and empty state.

### `ui/designsystem/` decision

There is enough need for a design-system layer, but creating it beside existing `ui/design/` would initially add duplication. UI-001 should evolve/consolidate the current package, or perform one explicit migration—not maintain two sources of truth. Export equivalent Android-compatible tokens/resources for Floating Dock.

### Theme findings

| Concern | Current | Finding |
|---|---|---|
| Color | forced light default; dynamic wallpaper palette on S+ | nondeterministic and opposite target |
| Typography | default `Typography()` | no branded hierarchy |
| Shapes | no custom `MaterialTheme.shapes`; local editor/Car values | inconsistent chrome |
| Dimensions | useful `Spacing`/`TouchTargets`/`Dimensions` | good base; missing semantic presentation and width tokens |
| States | mostly Material defaults/local aliases | selected/pressed/focus/error need deterministic rules |
| Overlay | local View/Paint colors and drawing values | cannot follow Compose theme |

Notable hard-coded visual values occur in `CarWorkspaceDashboard` (radii/border/preview/icon), `LicenseGate` (520 dp cap and local spacing/sizing), and `AndroidCarOverlayPlatform` (palette/drawing). Feature geometry may remain local; visual values should become named tokens.

## Motion opportunities (maximum five)

1. Workspace card press/focus: 150 ms surface/border/subtle scale; never delay launch.
2. Selected/active border: 160–180 ms in Car slots, Library selection and editor selection.
3. Floating Dock expand/collapse: 180–220 ms reveal using existing state; preserve position/gesture arbitration.
4. Editor selection: 150 ms border/surface/icon only; no geometry animation during drag.
5. Library filter or Car section content transition: 180–220 ms short fade/translation with stable layout.

No looping glow, decorative motion, heavy springs, or blur animation.

## Redesign risk map

**LOW:** colors/type/shapes/spacing/icons/borders/state/motion tokens; backgrounds; card chrome; section headers; content-width constraints; static state illustrations; overlay visual parity if hit regions remain unchanged.

**MEDIUM:** shared screen/card/button/status components; aligning three preview renderers; rearranging Home utilities or Car configuration while preserving callers/accessibility order; navigation visuals without route changes; state-linked motion.

**HIGH — avoid:** editor gestures/merge/undo/persistence; launch validation/bounds/sequencing/partial failure/display targeting; overlay lifecycle/gesture arbitration/background execution/snap persistence; repository/import/export; license proof/token/refresh/revocation/`FLAG_SECURE`/masking; update signature/manifest/install behavior.

## Recommended redesign order

1. **UI-001 Design System Foundation:** deterministic AMOLED palette, semantic type/shape/state/motion/width tokens, Compose/View parity.
2. **UI-002 Home / Library:** rebalance landing hierarchy and utilities; preserve all actions/previews.
3. **UI-003 Update + License + shared status/dialog surfaces:** unify commercially weak/trust-critical presentation without behavior/security changes.
4. **UI-004 Editor + App Picker visual polish:** chrome, selected states, labels/icons and density; no gesture/state changes.
5. **UI-005 Car Mode alignment:** token/section polish of correct CAR-022 structure.
6. **UI-006 Floating Dock parity:** View-compatible tokens and affordances; no overlay behavior change.
7. **UI-007 Motion and final consistency/accessibility audit.**

## Recommended UI-001 Design Tokens

Recommendations only; not implemented.

| Token | Value | Intent |
|---|---|---|
| `Background` | `#070A0F` | near-black AMOLED root with layer visibility |
| `Surface` | `#111720` | primary cards/panels/dialogs |
| `SurfaceAlt` | `#182231` | selected/raised/input/toolbar layer |
| `Primary` | `#4DA3FF` | action, focus, active border |
| `Secondary` | `#8EA2B8` | restrained secondary content |
| `Accent` | `#48D6D2` | sparse status/highlight, not broad neon fill |
| `OnBackground` | `#F3F7FC` | primary text |
| `OnSurfaceMuted` | `#AAB8C8` | metadata |
| `Outline` | `#2A394A` | subtle boundaries |
| `Error` | `#FF6B7A` | error |
| `Warning` | `#FFC857` | warning/partial |
| `Success` | `#55D68B` | ready/success |
| `CardRadius` | 16 dp | primary surfaces |
| `SmallRadius` | 8 dp | compact controls/inner previews |
| `ScreenPadding` | 16 / 24 / 32 dp | compact/default/wide |
| `CardGap` | 16 dp | grid/card gap |
| `SectionGap` | 32 dp | major sections |
| `ContentMaxWidth` | 1440 / 960 / 520 dp | grid / form-settings / activation |
| `TouchTarget` | 48 dp | absolute minimum |
| `TouchTargetComfortable` | 56 dp | normal touch controls |
| `CarTouchTarget` | 64 dp | Car primary actions |
| `BorderDefault/Selected` | 1 / 2 dp | surface/selection |
| `TitleLarge` | 32 sp / 40 sp, 600 | screen title |
| `TitleSection` | 22 sp / 28 sp, 600 | section title |
| `Body` | 16 sp / 24 sp, 400 | body |
| `Label` | 14 sp / 20 sp, 500 | control label |
| `Metadata` | 13 sp / 18 sp, 400 | secondary data |
| `MotionFast` | 150 ms | press/selection |
| `MotionNormal` | 200 ms | expand/content transition |

Keep the existing 48/56/64 dp touch policy. Car Mode/Dock should prefer 64 dp for primary actions and never depend on hover.

## Screenshot index

All screenshots are ignored runtime output under `build/ui-audit/` and must not be committed.

| Path | Evidence |
|---|---|
| `build/ui-audit/current.png` | DeX desktop baseline |
| `build/ui-audit/home.png` | Home / Library at 1920×1200 |
| `build/ui-audit/car-mode.png` | Car dashboard/configuration |
| `build/ui-audit/floating-dock-collapsed.png` | collapsed left-edge dock |
| `build/ui-audit/floating-dock-expanded.png` | expanded eight-slot dock |
| `build/ui-audit/editor.png` | existing two-cell editor |
| `build/ui-audit/editor-selected.png` | App Picker reached from editor; filename retained |
| `build/ui-audit/update.png` | Update screen |

`build/ui-audit/sort-dialog.png` was an invalid transitional/desktop capture and was not used.

## Source evidence

- Shell/routes: `MainActivity.kt`, `navigation/TouchNavigation.kt`
- Theme/tokens: `ui/theme/*`, `ui/design/*`
- Home/Library: `ui/screens/HomeScreen.kt`, `workspace/library/ui/*`, `workspace/snapshot/ui/*`
- Creation/editor/picker: `workspace/templates/ui/*`, `ui/screens/LayoutDesignerScreen.kt`, `ui/screens/AppPickerScreen.kt`, `workspace/designer/ui/*`, `workspace/apppicker/presentation/*`
- Launch/transfers: `workspace/launcher/presentation/WorkspaceLaunchStatusDialog.kt`, dialogs in `TouchNavigation.kt`
- Car: `feature/car/CarScreen.kt`, `CarWorkspaceDashboard.kt`, `CarWorkspaceShortcutSelector.kt`
- Dock: `feature/car/overlay/AndroidCarOverlayPlatform.kt`, `CarFloatingDockCoordinator.kt`, `CarFloatingWorkspaceShortcut.kt`
- License/About: `license/ui/LicenseGate.kt`, `about/ui/AboutDialog.kt`

## Conclusion

The product does not need a UI rewrite. It needs a deterministic shared visual system, desktop-aware composition on Home/Update, and controlled alignment between Compose and Android View rendering. The fastest low-risk path to paid-product quality is UI-001 tokens first, then Home and trust/status surfaces, followed by visual-only polish of editor, Car Mode, and Floating Dock.
