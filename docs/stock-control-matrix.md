# Odin Handheld Controls: Clean-Room Matrix

This document records behavioral facts used to design the public Odin Settings
replacement. It does not contain decompiled source, proprietary resources, or
vendor implementation code. Private artifacts are referenced only so the local
workbench can reproduce the observations.

## Evidence boundary

Published, redacted evidence in the `odin2-mini` workbench:

- `docs/ports/odin-settings/stock-inventory.md`
- `docs/ports/odin-settings/stock-app-inventory.md`
- `docs/ports/odin-settings/settings-state.md`
- `docs/ports/odin-settings/control-path-map.md`
- `docs/device-inventory/current/package-odin-settings.txt`
- `docs/device-inventory/current/package-touch-mapping.txt`
- `docs/device-inventory/current/package-game-assistant.txt`

Private local evidence, never copied into this repository:

- `.cache/private/build-host-runs/20260710T134000Z-generic-ab-ota-avb-bacon-corrected/artifacts/lineage_odin2_mini-target_files.zip`
  - `SYSTEM/app/TouchMapping/TouchMapping.apk`: DEX class/string inventory and
    embedded `lib/arm64-v8a/librsinput.so` symbol/string inventory.
  - `SYSTEM/app/GameAssistant/GameAssistant.apk`: DEX class/string and asset-name
    inventory.

The private archive contains stock TouchMapping and GameAssistant artifacts but
contains the public replacement OdinSettings app, not the stock OdinSettings
implementation. Stock OdinSettings conclusions therefore come only from the
published package/component/settings inventory above.

## Control-path matrix

Safety classes:

- **Local**: in-process data or UI state; no privileged device effect.
- **Framework**: Android setting, display, input, or overlay effect requiring a
  reviewed platform integration.
- **Privileged I/O**: `/dev`, sysfs, GPIO, UART, MCU, firmware, or virtual-input
  creation. Disabled until separately validated and reviewed.

| Capability | Stock behavioral evidence | Required replacement behavior | Confidence | Safety | Current state |
|---|---|---|---|---|---|
| Controller profile model | TouchMapping DEX names `StandardGamepadConfig`, `FlippedGamepadConfig`, `XboxGamepadConfig`, `XboxWithGuideGamepadConfig`, `RetroGamepadConfig`, and `GamepadToMouseConfig`. | Maintain public, immutable profile definitions independent of Android UI and hardware access. | High | Local | Implemented for standard and flipped face-button profiles. |
| Button interpretation | TouchMapping DEX exposes gamepad/button/joystick input models and event interpreters. The native inventory identifies Xbox/Odin virtual controller names. | Translate Android controller key events to semantic buttons, then map them through the selected preview profile. | High | Local | Implemented in the read-only controller test surface. |
| System-wide virtual controller | `librsinput.so` names `/dev/uinput`, `NativeGamepadDevice`, `NativeKeyboardDevice`, `NativeMouseDevice`, and virtual Xbox/mouse devices. | Put all virtual-device creation behind a narrow privileged adapter. Never imply a profile is active when the adapter is unavailable. | High | Privileged I/O | Adapter contract implemented; default adapter always rejects. No uinput writer exists. |
| Gamepad-to-mouse | TouchMapping DEX names standard mouse and gamepad-to-mouse configs; stock settings include `global_gamepad_to_mouse_mode` and first/second mouse-key candidates. | Model separately from controller layout. Require an explicit, reviewed virtual-input backend and lifecycle policy. | High | Privileged I/O | Deliberately not implemented. |
| Touch overlay mapping | TouchMapping DEX includes touchscreen interpreters and virtual joystick operations. GameAssistant contains per-game config assets, a mapping editor, floating service, and the TouchMapping SDK/Binder model. | Keep per-game touch overlays outside the first controller-profile increment; later consume a public service API rather than native vendor code. | High | Framework / Privileged I/O | Deferred. No overlay service or fake editor. |
| Mapping service boundary | TouchMapping DEX names `IServerApi`, `MappingProxy`, get/set gamepad config transactions, and input listeners. GameAssistant embeds the client-side SDK model. | Separate UI/client state from a service/adapter boundary with explicit result types and capability status. | High | Framework | Public contracts and fail-closed coordinator implemented; no Binder service yet. |
| Joystick calibration and tuning | Native JNI names calibration, dead-zone, sensitivity, square-track, trigger, and M1/M2 setters. It also names `/mnt/vendor/persist/odin/joys` and ADC nodes. | Treat calibration as a distinct privileged capability with bounded values, read-back, restore, and device identity checks. | High | Privileged I/O | Deliberately disabled; no files or nodes are opened. |
| MCU/controller transport | Native strings name `/dev/rscom`, `/dev/rstouch`, `/dev/rsinput`, UART framing, MCU power, and MCU firmware update paths. | Never share this backend with ordinary mapping UI. Any future transport must be separately reviewed, allowlisted, and recovery-aware. | High | Privileged I/O | Out of scope and absent. |
| Fan control | Live validation mapped `/sys/class/gpio5_pwm2/{state,duty,period,speed}` with a required `50000 ns` period. `state=1` followed by a `25000 ns` PWM high-time produced a tach value of `3300`; cleanup restored `state=0` and the `10000 ns` baseline high-time. | Allow only Off (`state=0`, then `10000`), Quiet (`state=1`, then `5000`), and Sport (`state=1`, then `25000`). Gate exact device identity and period, read back every write, and best-effort disable after a failed non-Off transaction. Treat the tach node as pulses times 300, not guaranteed RPM. | Live-validated path and bounded values | Privileged I/O | Implemented behind exact identity/path/period checks. No arbitrary tuning or Custom mode. |
| External display policy | Stock settings inventory observes `force_landscape_when_output_video` and `close_screen_when_output_video`. Native strings name `/sys/hdmi/driver_ctl` and an HDMI backlight control. GameAssistant references display listeners and an HDMI index setting. | Keep desired display policy separate from hardware adapters. Do not write stock settings or HDMI nodes until reversible Android 16 behavior is validated. | Medium-high | Framework / Privileged I/O | Policy model and disabled status surfaced; no toggle or writer. |
| Odin Settings integration | Package inventory proves the Settings `EXTRA_SETTINGS` activity, keep-screen-on and force-landscape QS tiles, and provider authority. | Preserve entry points while keeping unvalidated actions unavailable. | High | Framework | Settings entry and fail-closed QS tiles preserved. |
| Floating game assistant | Package and DEX inventories prove a floating-window tile/service, FPS display, brightness controls, gamepad test, and scheme editor. | Reintroduce only after overlay lifecycle, permissions, and TouchMapping replacement service are designed. | High | Framework | Deferred. The replacement provides a non-overlay input tester only. |

## Clean-room conclusions

1. Stock TouchMapping is not merely a UI. It combines a profile/config model,
   Binder-style API, event interpretation, virtual input devices, and dangerous
   hardware transports.
2. GameAssistant is principally a client/editor/overlay layer over that mapping
   stack, with additional display and brightness behavior. It should not own
   privileged controller I/O in the replacement.
3. Stock OdinSettings proves user entry points and settings candidates, but the
   current evidence does not prove safe write semantics for its tiles, fan, or
   external-display controls.
4. The first safe increment is therefore a tested profile and policy domain, a
   useful local controller-input preview, and a privileged adapter that is
   unavailable by construction. System-wide remapping comes later through a
   separately reviewed service/backend.
