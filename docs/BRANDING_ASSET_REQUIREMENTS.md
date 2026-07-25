# Branding Asset Requirements

The approved concept sheet is
`docs/branding/DexWorkspaceTouch-branding-approved.png`. The clean vector master derived from that
approved concept is `docs/branding/DexWorkspaceTouch-icon-master.svg`.

The mark represents an external display containing one large workspace region on the left and two
smaller regions on the right, with a touch gesture over the large region. It contains no text and
uses no Samsung trademark or device branding.

## Palette

- Background: `#1A1B3A`
- Blue workspace: `#2563EB`
- Purple workspace: `#7C3AED`
- Cyan workspace and touch feedback: `#22D3EE`
- Display frame and hand: `#FFFFFF`

## Android asset set

The production asset set includes:

- adaptive foreground and background layers;
- legacy launcher icons for supported density buckets;
- round launcher icon;
- monochrome adaptive layer where appropriate;
- Android 12 splash icon using the same brand mark;
- a high-contrast `#1A1B3A` splash background.

The adaptive foreground is transparent outside the mark and does not contain its own square
background. Important geometry stays between viewport coordinates 22 and 86 in a 108-unit
viewport. The hand is intentionally secondary to the display and occupies roughly 25% of the
mark's visual area. The monochrome layer is a single-color, gradient-free silhouette.

Generated audit images are stored under `docs/branding/previews/`:

- `launcher-mask-audit.png`: circle, rounded-square, squircle, and Samsung-style masks;
- `monochrome-themed-audit.png`: light, dark, and accent themed treatments;
- `splash-audit.png`: phone portrait and DeX window proportions.

Verify adaptive masks, cropping, round presentation, and splash presentation on Samsung launchers
on both test devices. The splash must use the platform theme/API, must not delay startup, and must
not introduce a heavy animation. Final device verification remains required after reinstall because
Samsung launchers may retain cached icons.
