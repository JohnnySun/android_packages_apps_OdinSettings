# android_packages_apps_OdinSettings

Odin Settings replacement app seed for AYN Odin2 Mini.

Source tree path: `packages/apps/OdinSettings`

This app uses the stock package name `com.odin2.odinsettings` so the system
Settings entry point can match the stock user journey. Hardware writes must stay
fail-closed until the workbench records a validated control path from stock
firmware.

Initial stock parity targets:

- `com.android.settings.action.EXTRA_SETTINGS` activity entry.
- Keep-screen-on quick settings tile.
- Force-landscape quick settings tile.
- Read-only rows for `fan_mode`, `performance_mode`, `touch_mapping`, and
  `game_assistant`.
- Visible mapping status, candidate read paths, and LineageOS 24 action text
  generated from the workbench bring-up matrix.

Reference matrix:
`docs/ports/odin-settings/bringup-readiness.md` in the `odin2-mini` workbench.

Workbench: `JohnnySun/odin2-mini`
