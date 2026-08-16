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

Configure all four from the app itself (it has a launcher icon again,
specifically for this): tap a row, tap an app to launch the app itself,
or tap the "+" next to an app to expand its shortcuts and pick one of
those instead. No confirm/cancel — tapping commits immediately. The
extra-long-press timer is a plain numeric field on the same screen.

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

## Two known limitations — not yet verified on real hardware

**Shortcuts are static-only.** Fully general access to another app's
shortcuts (`LauncherApps.getShortcuts()`) requires the calling app to be
the phone's default launcher, which this app deliberately isn't. Instead
it reads each app's declared `shortcuts.xml` resource directly via
`PackageManager` — no special permission needed, but it only surfaces
**static** shortcuts. Dynamic shortcuts (generated at runtime — "message
Mom," "resume last document") and pinned shortcuts are invisible this
way, no workaround short of becoming a launcher. Some static shortcuts
also point at non-exported activities and will throw a
`SecurityException` if launched from outside their own app; that's
caught silently at fire time (see `GlyphTorchToyService.fireAction()`),
not filtered out of the picker list.

**Launching anything from the service at all might get blocked.** Since
Android 10, `startActivity()` from a background context (no visible
UI — which describes this bound service) is disallowed by default
unless a specific exemption applies. Whether Nothing OS's IPC delivery
of these toy events counts as one of those exemptions isn't something
that can be confirmed from the public SDK — it's closed-source on
Nothing's side. It may just work (a real hardware button press is a
legitimate user interaction), or it may silently fail with nothing but
a logcat warning and no visible error. This needs testing on the actual
device to know for sure; there's no notification-based fallback
implemented yet if it turns out to be blocked.

## What's in here

- `GlyphTorchToyService.java` — the toy: the unchanged full-brightness
  activation/deactivation behavior, the four-signal event state machine
  above, and firing the configured action for each signal.
- `MainActivity.java` — the config screen.
- `ActionTarget.java` — encodes a configured action (launch-this-app or
  launch-this-shortcut) as a simple string for SharedPreferences.
- `GlyphActionsConfig.java` — reads/writes the four action slots and the
  timer value.
- `ShortcutUtils.java` — the static-shortcut XML parser described above.

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
