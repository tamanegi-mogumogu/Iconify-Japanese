package com.drdisagree.iconify.config;

/* Modified from AOSPMods
 * https://github.com/siavash79/AOSPMods/blob/canary/app/src/main/java/sh/siava/AOSPMods/XPrefs.java
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see [http://www.gnu.org/licenses/].
 */

import static com.drdisagree.iconify.common.Resources.SharedXPref;

import android.content.Context;
import android.content.SharedPreferences;

import com.crossbowffs.remotepreferences.RemotePreferences;
import com.drdisagree.iconify.BuildConfig;
import com.drdisagree.iconify.common.Const;
import com.drdisagree.iconify.xposed.HookEntry;
import com.drdisagree.iconify.xposed.ModPack;

public class XPrefs {

    private static final SharedPreferences.OnSharedPreferenceChangeListener listener = (sharedPreferences, key) -> loadEverything(key);
    public static SharedPreferences Xprefs;
    private static String packageName;

    public static void init(Context context) {
        packageName = context.getPackageName();
        Xprefs = new RemotePreferences(context, BuildConfig.APPLICATION_ID, SharedXPref, true);
        Xprefs.registerOnSharedPreferenceChangeListener(listener);
    }

    public static void loadEverything(String... key) {
        if (key.length > 0 && (key[0] == null || Const.PREF_UPDATE_EXCLUSIONS.stream().anyMatch(exclusion -> key[0].startsWith(exclusion))))
            return;

        for (ModPack thisMod : HookEntry.runningMods) {
            thisMod.updatePrefs(key);
        }
    }
}