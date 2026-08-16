# Glyph Torch Toy

A Glyph Toy for the Nothing Phone (3). Core behavior: the whole Glyph
Matrix lights up at true full brightness the instant the toy is selected
in the carousel, and turns off the instant you cycle away. On top of
that, each of four signals the toy can receive is independently
configurable to launch an app or app shortcut of your choice:

- **Activated** — the toy becomes the selected toy
- **Deactivated** — you cycle away to another toy
- **Long press** — a regular long press on the Glyph Button
- **Extra long press** — holding past a configurable timer (default
  1000ms) from the moment you press down, without releasing or
  triggering a regular long press first

Configure all four from the app itself: tap a row, tap an app to launch
the app itself, or (if that app offers one) tap "+" to expand its
shortcut-creator entries and pick one. No confirm/cancel — tapping
commits immediately. The extra-long-press timer is a plain numeric field
on the same screen.

## Extra-long-press mechanics

On `EVENT_ACTION_DOWN`, a timer starts (length from the config screen).
`EVENT_CHANGE` (the system's own long-press signal) doesn't fire
anything by itself — it just gets remembered. If `EVENT_ACTION_UP`
arrives before the timer does, that resolves the press: the long-press
action fires if `EVENT_CHANGE` had been seen, otherwise it was too
short a tap to mean anything. If the timer fires first, that's the
extra long press: its action fires instead, a distinct haptic plays
(200ms pulse, 100ms gap, 5ms pulse, 100ms gap, 5ms pulse), and the
eventual `ACTION_UP` is a no-op. The two are mutually exclusive by
design — reaching extra-long-press suppresses the regular long-press
action rather than firing both.

## Shortcuts: ACTION_CREATE_SHORTCUT only, not static/dynamic shortcuts

Two other shortcut mechanisms were tried and dropped, both confirmed on
real hardware:

- `LauncherApps.getShortcuts()` (dynamic + pinned shortcuts) requires
  the calling app to be the phone's default launcher
  (`hasShortcutHostPermission()`), which this app deliberately isn't.
  Never implemented for that reason.
- Static shortcuts (parsed directly from each app's `shortcuts.xml`, no
  permission needed) were implemented, then removed: most of them
  pointed at non-exported activities and threw `SecurityException` when
  launched from outside their own app. In practice only a small
  fraction of an app's static shortcuts were actually launchable this
  way, and there was no reliable way to tell which ones without trying.

What's used instead: `ACTION_CREATE_SHORTCUT`, the older "pick a
shortcut" mechanism that predates `ShortcutManager`. Any app can declare
an activity that responds to it; the picker launches that activity via
`startActivityForResult` and captures whatever it returns
(`EXTRA_SHORTCUT_INTENT` + `EXTRA_SHORTCUT_NAME`). No special permission
needed. Some apps show their own picker UI when launched this way (e.g.
Automate's flow list); others with only one thing to offer just return
a result immediately with no visible UI at all — both are valid,
expected uses of this API, not a bug. The "+" only appears on an app's
row if it declares at least one such activity.

## SYSTEM_ALERT_WINDOW is required for actions to fire in the background

Confirmed on hardware: with the config screen in the foreground,
configured actions fire correctly. With it closed, they silently don't.
This matches Android's background-activity-start restrictions (in
effect since Android 10) — starting an activity from a context with no
visible UI, like this bound service reacting to a Glyph Button press,
is blocked by default. One of the documented exemptions is holding the
`SYSTEM_ALERT_WINDOW` permission. This is a "special access" permission
granted through a Settings toggle, not a runtime dialog — the config
screen shows a prompt linking directly to that toggle whenever it isn't
already granted.

(The narrower Android-15-specific version of this exemption you may see
mentioned online — requiring an actual visible overlay window, not just
the permission — applies to starting *foreground services* from the
background, a different restriction. For starting *activities*, which
is what this app does, holding the permission is sufficient on its own.)

## Package visibility

Android 11+ hides other installed apps from `PackageManager` queries by
default unless the querying app declares what it's looking for. Without
this, the picker only sees a handful of apps the OS happens to leave
visible regardless. Fixed with a `<queries>` block in the manifest
declaring the two intents the picker actually queries for — the
documented, Play-Store-friendly way to get this visibility without the
much more broadly scoped `QUERY_ALL_PACKAGES` permission.

## What's in here

- `GlyphTorchToyService.java` — the toy: the unchanged full-brightness
  activation/deactivation behavior, the four-signal event state machine
  above, and firing the configured action for each signal.
- `MainActivity.java` — the config screen.
- `ActionTarget.java` — encodes a configured action (launch-this-app or
  launch-this-shortcut) as a simple string for SharedPreferences.
- `GlyphActionsConfig.java` — reads/writes the four action slots and the
  timer value.

Brightness note (still applies): the true per-pixel ceiling for the raw
`setMatrixFrame(int[])` path is 4095, not the 0-255 documented for
`GlyphMatrixObject`'s brightness parameter — confirmed by decompiling
`GlyphMatrixUtils.convertToGlyphMatrix()`.

## Build & install

Everything needed to reproduce a build — the pinned debug keystore, the
Glyph Matrix SDK fetch, the CI workflow — is already wired up in this
repo. Open in Android Studio and Run, or push to `main` and grab the
APK from the latest GitHub Release; both sign with the same pinned
keystore (`debug.keystore.b64` at the repo root, auto-decoded by a
Gradle task for local builds and by a CI step for Actions builds), so
updates always install cleanly over each other.
