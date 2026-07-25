# Branding Asset Requirements

The current manifest uses Android's `sym_def_app_icon` placeholder. It is not acceptable for the
final beta artifact. No arbitrary logo should be generated or inferred without design approval.

Provide an approved master artwork with a documented safe zone and colors. The Android asset set
must then include:

- adaptive foreground and background layers;
- legacy launcher icons for supported density buckets;
- round launcher icon;
- monochrome adaptive layer where appropriate;
- Android 12 splash icon using the same brand mark;
- light/dark-compatible splash background colors.

Verify adaptive masks, cropping, round presentation, and splash presentation on Samsung launchers
on both test devices. The splash must use the platform theme/API, must not delay startup, and must
not introduce a heavy animation. Font licenses and sensitive source artwork must not be committed
unless explicitly approved.
