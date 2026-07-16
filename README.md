# android_packages_apps_OdinSettings

Maintainable Odin Settings replacement for AYN Odin2 Mini.

Source tree path: `packages/apps/OdinSettings`

This app uses the stock package name `com.odin2.odinsettings` so the system
Settings entry point can match the stock user journey. Hardware writes must stay
fail-closed until the workbench records a validated control path from stock
firmware.

The Android 16 increment currently provides:

- `com.android.settings.action.EXTRA_SETTINGS` activity entry.
- Immutable standard and flipped face-button controller profiles.
- A Binder-backed system controller profile control with confirmed readback.
- A local controller input tester that previews the last confirmed system
  profile without injecting events or changing the mapping itself.
- A policy and privileged-adapter boundary that denies unknown devices and
  fails closed when the controller service or confirmed readback is unavailable.
- Read-only external-display status and an allowlisted, fail-closed fan mode
  control for Off, Quiet, and Sport.
- Fail-closed keep-screen-on and force-landscape quick settings tiles.

Stock behavior and safety evidence is summarized in
`docs/stock-control-matrix.md`. No proprietary APK, native library, resource, or
decompiled implementation is part of this repository.

System-wide event injection, touch overlays, arbitrary fan tuning, MCU access,
joystick calibration, HDMI node writes, firmware updates, root, and OTA behavior
remain disabled until each path has dedicated evidence and review.

Workbench: `JohnnySun/odin2-mini`

Run the local host checks with:

```bash
./tests/run-host-tests.sh
```
