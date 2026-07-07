package com.odin2.odinsettings;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class OdinControlRegistry {
    public static final String LINEAGE_TARGET = "LineageOS 24 / Android 17";
    public static final String HARDWARE_WRITE_POLICY = "blocked_until_validated";

    public static final List<OdinControl> CONTROLS = Collections.unmodifiableList(Arrays.asList(
            new OdinControl(
                    "keep_screen_on",
                    "Keep screen on",
                    "observed_entrypoint",
                    "Stock QS tile observed; candidate setting key is read-only until validated.",
                    "Implement the visible UI or QS tile in fail-closed mode; keep writes disabled until the exact reversible path is validated.",
                    new String[] {"settings/system/keep_screen_on"}),
            new OdinControl(
                    "force_landscape",
                    "Force landscape",
                    "observed_entrypoint",
                    "Stock QS tile observed; orientation keys are candidates, not write targets.",
                    "Implement the visible UI or QS tile in fail-closed mode; keep writes disabled until the exact reversible path is validated.",
                    new String[] {
                            "settings/system/force_landscape",
                            "settings/system/force_landscape_when_output_video",
                            "settings/system/close_screen_when_output_video"}),
            new OdinControl(
                    "fan_mode",
                    "Fan mode",
                    "expected_unmapped",
                    "Candidate setting keys observed; stock service or node still unknown.",
                    "Show a disabled/read-only settings row while stock UI labels, provider keys, and before/after evidence are mapped.",
                    new String[] {
                            "settings/system/fan_mode",
                            "settings/system/smart_fan_mode_switch",
                            "settings/system/is_quick_set_performance_and_fan_enable"}),
            new OdinControl(
                    "performance_mode",
                    "Performance mode",
                    "expected_unmapped",
                    "Candidate setting key observed; thermal or power path still unknown.",
                    "Show a disabled/read-only settings row while stock UI labels, provider keys, and before/after evidence are mapped.",
                    new String[] {
                            "settings/system/performance_mode",
                            "settings/system/is_quick_set_performance_and_fan_enable"}),
            new OdinControl(
                    "touch_mapping",
                    "Touch mapping",
                    "observed_entrypoint",
                    "Stock TouchMapping activity observed; MCU boundary still unknown.",
                    "Expose a fail-closed deep link or placeholder to the TouchMapping surface; do not write private MCU or gamepad state.",
                    new String[] {
                            "settings/system/no_create_gamepad_button_layout",
                            "settings/system/global_gamepad_to_mouse_mode",
                            "settings/system/first_gamepad_to_mouse_key",
                            "settings/system/second_gamepad_to_mouse_key"}),
            new OdinControl(
                    "game_assistant",
                    "Game assistant",
                    "observed_entrypoint",
                    "Stock GameAssistant activity and floating tile observed; overlay behavior still unknown.",
                    "Expose a fail-closed deep link or disabled overlay entry; do not start private overlay/service writes.",
                    new String[] {"settings/secure/sysui_qs_tiles"})));

    private OdinControlRegistry() {}
}
