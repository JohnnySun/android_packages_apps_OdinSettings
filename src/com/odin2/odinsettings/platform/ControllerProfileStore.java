package com.odin2.odinsettings.platform;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.odin2.odinsettings.domain.ControllerProfile;
import com.odin2.odinsettings.domain.ControllerProfiles;

public final class ControllerProfileStore {
    public static final String KEY_PREVIEW_PROFILE = "preview_controller_profile";

    public static ControllerProfile read(Context context) {
        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        return ControllerProfiles.findOrDefault(preferences.getString(
                KEY_PREVIEW_PROFILE, ControllerProfiles.STANDARD_ID));
    }

    private ControllerProfileStore() {}
}
