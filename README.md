# Glyph Torch Toy

A minimal Glyph Toy for the Nothing Phone (3): long-press the Glyph Button
to flash the entire Glyph Matrix to full brightness as a torch, long-press
again to turn it off.

No UI. No launcher icon. No AndroidX or any other library — the only
dependency is the official Glyph Matrix SDK itself. The app exists purely
to register `GlyphTorchToyService` with Nothing OS.

## What's in here

- `GlyphTorchToyService.java` — the entire app. A `Service` with an
  intent-filter for `com.nothing.glyph.TOY` plus the required name/image/
  summary metadata in the manifest — that's the documented registration
  method Nothing OS scans for to list a toy in Settings → Glyph Interface
  → Glyph Toys.
- `.github/workflows/build.yml` — CI workflow. On every push to `main`, it
  fetches the official Glyph Matrix SDK aar from
  `Nothing-Developer-Programme/GlyphMatrix-Developer-Kit`, builds a debug
  APK, and attaches it to a new GitHub Release tagged `build-<run number>`.

All SDK class names, constants, and method signatures used here were
confirmed by decompiling the actual `.aar` (via `javap`), not just read off
the README — so things like `GlyphToy.EVENT_CHANGE`, `Glyph.DEVICE_23112`,
and the `setMatrixFrame(int[])` signature are exact, not guessed.

## One thing to verify yourself

`setGlyphMatrixTimeout(boolean)` exists in the SDK but isn't documented
anywhere in Nothing's README. It's used here (see the comment in
`GlyphTorchToyService.java`) on the assumption that it disables the "Glyph
Toys timeout" so the torch doesn't auto-dim while toggled on. This is
inferred from the method name, not confirmed — test it, and if the torch
still times out, that call isn't doing what its name implies and can be
removed.

## Getting the APK

Check the Releases page for the latest build — every push to `main`
produces a new one with the APK attached.

## Installing

Sideload the APK (allow installs from unknown sources for whatever app you
use to open it). There's no launcher icon, so it won't appear on the home
screen or app drawer — that's expected. Go to Settings → Glyph Interface →
Glyph Toys, find "Glyph Torch" in the list, and add it to your active toy
rotation. Tap the Glyph Button on the back until you cycle to the torch
icon, then long-press to toggle it on/off.

## If you want hold-to-shine instead of toggle

Swap the `EVENT_CHANGE` check in `serviceHandler.handleMessage()` for two
checks on `GlyphToy.EVENT_ACTION_DOWN` / `GlyphToy.EVENT_ACTION_UP`, calling
`drawFullBrightness()` on down and `drawIdle()` on up. Both constants are
already confirmed present in the SDK, just unused in this toggle version.
