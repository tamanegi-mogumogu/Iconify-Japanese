package com.drdisagree.iconify.utils.overlay.compiler;

import static com.drdisagree.iconify.common.Const.GMS_PACKAGE;
import static com.drdisagree.iconify.common.Const.SETTINGS_PACKAGE;
import static com.drdisagree.iconify.common.Const.WELLBEING_PACKAGE;
import static com.drdisagree.iconify.utils.helper.Logger.writeLog;

import android.util.Log;

import com.drdisagree.iconify.common.Resources;
import com.drdisagree.iconify.utils.FileUtil;
import com.drdisagree.iconify.utils.RootUtil;
import com.drdisagree.iconify.utils.SystemUtil;
import com.drdisagree.iconify.utils.helper.BinaryInstaller;
import com.drdisagree.iconify.utils.overlay.OverlayUtil;
import com.topjohnwu.superuser.Shell;

import java.io.IOException;
import java.util.Objects;

public class SettingsIconsCompiler {

    private static final String TAG = SettingsIconsCompiler.class.getSimpleName();
    private static final String[] mPackages = new String[]{SETTINGS_PACKAGE, WELLBEING_PACKAGE, GMS_PACKAGE};
    private static int mIconSet = 1, mIconBg = 1;
    private static boolean mForce = false;

    public static boolean buildOverlay(int iconSet, int iconBg, String resources, boolean force) throws IOException {
        mIconSet = iconSet;
        mIconBg = iconBg;
        mForce = force;

        preExecute();
        moveOverlaysToCache();

        for (int i = 0; i < mPackages.length; i++) {
            String overlay_name = "SIP" + (i + 1);

            // Create AndroidManifest.xml
            if (OverlayCompiler.createManifest(overlay_name, mPackages[i], Resources.TEMP_CACHE_DIR + "/" + mPackages[i] + "/" + overlay_name)) {
                Log.e(TAG, "Failed to create Manifest for " + overlay_name + "! Exiting...");
                postExecute(true);
                return true;
            }

            // Write resources
            if (!Objects.equals(resources, "") && writeResources(Resources.TEMP_CACHE_DIR + "/" + mPackages[i] + "/" + overlay_name, resources)) {
                Log.e(TAG, "Failed to write resource for " + overlay_name + "! Exiting...");
                postExecute(true);
                return true;
            }

            // Build APK using AAPT
            if (OverlayCompiler.runAapt(Resources.TEMP_CACHE_DIR + "/" + mPackages[i] + "/" + overlay_name, mPackages[i])) {
                Log.e(TAG, "Failed to build " + overlay_name + "! Exiting...");
                postExecute(true);
                return true;
            }

            // ZipAlign the APK
            if (OverlayCompiler.zipAlign(Resources.UNSIGNED_UNALIGNED_DIR + "/" + overlay_name + "-unsigned-unaligned.apk")) {
                Log.e(TAG, "Failed to align " + overlay_name + "-unsigned-unaligned.apk! Exiting...");
                postExecute(true);
                return true;
            }

            // Sign the APK
            if (OverlayCompiler.apkSigner(Resources.UNSIGNED_DIR + "/" + overlay_name + "-unsigned.apk")) {
                Log.e(TAG, "Failed to sign " + overlay_name + "-unsigned.apk! Exiting...");
                postExecute(true);
                return true;
            }
        }

        postExecute(false);
        return false;
    }

    private static void preExecute() throws IOException {
        // Create symbolic link
        BinaryInstaller.symLinkBinaries();

        // Clean data directory
        Shell.cmd("rm -rf " + Resources.TEMP_DIR).exec();
        Shell.cmd("rm -rf " + Resources.DATA_DIR + "/CompileOnDemand").exec();

        // Extract overlay from assets
        for (String aPackage : mPackages)
            FileUtil.copyAssets("CompileOnDemand/" + aPackage + "/SIP" + mIconSet);

        // Create temp directory
        Shell.cmd("rm -rf " + Resources.TEMP_DIR + "; mkdir -p " + Resources.TEMP_DIR).exec();
        Shell.cmd("mkdir -p " + Resources.TEMP_OVERLAY_DIR).exec();
        Shell.cmd("mkdir -p " + Resources.TEMP_CACHE_DIR).exec();
        Shell.cmd("mkdir -p " + Resources.UNSIGNED_UNALIGNED_DIR).exec();
        Shell.cmd("mkdir -p " + Resources.UNSIGNED_DIR).exec();
        Shell.cmd("mkdir -p " + Resources.SIGNED_DIR).exec();
        for (String aPackages : mPackages)
            Shell.cmd("mkdir -p " + Resources.TEMP_CACHE_DIR + "/" + aPackages + "/").exec();
        if (!mForce) {
            Shell.cmd("mkdir -p " + Resources.BACKUP_DIR).exec();
        }

        if (mForce) {
            // Disable the overlay in case it is already enabled
            String[] overlayNames = new String[mPackages.length];
            for (int i = 1; i <= mPackages.length; i++) {
                overlayNames[i - 1] = "IconifyComponentSIP" + i + ".overlay";
            }
            OverlayUtil.disableOverlays(overlayNames);
        }
    }

    private static void postExecute(boolean hasErroredOut) {
        // Move all generated overlays to module
        if (!hasErroredOut) {
            for (int i = 1; i <= mPackages.length; i++) {
                Shell.cmd("cp -rf " + Resources.SIGNED_DIR + "/IconifyComponentSIP" + i + ".apk " + Resources.OVERLAY_DIR + "/IconifyComponentSIP" + i + ".apk").exec();
                RootUtil.setPermissions(644, Resources.OVERLAY_DIR + "/IconifyComponentSIP" + i + ".apk");

                if (mForce) {
                    // Move to files dir and install
                    Shell.cmd("cp -rf " + Resources.SIGNED_DIR + "/IconifyComponentSIP" + i + ".apk " + Resources.DATA_DIR + "/IconifyComponentSIP" + i + ".apk").exec();
                    RootUtil.setPermissions(644, Resources.DATA_DIR + "/IconifyComponentSIP" + i + ".apk");
                    Shell.cmd("pm install -r " + Resources.DATA_DIR + "/IconifyComponentSIP" + i + ".apk").exec();
                    Shell.cmd("rm -rf " + Resources.DATA_DIR + "/IconifyComponentSIP" + i + ".apk").exec();
                }
            }

            if (mForce) {
                // Move to system overlay dir
                SystemUtil.mountRW();
                for (int i = 1; i <= mPackages.length; i++) {
                    Shell.cmd("cp -rf " + Resources.SIGNED_DIR + "/IconifyComponentSIP" + i + ".apk " + Resources.SYSTEM_OVERLAY_DIR + "/IconifyComponentSIP" + i + ".apk").exec();
                    RootUtil.setPermissions(644, "/system/product/overlay/IconifyComponentSIP" + i + ".apk");
                }
                SystemUtil.mountRO();

                // Enable the overlays
                String[] overlayNames = new String[mPackages.length];
                for (int i = 1; i <= mPackages.length; i++) {
                    overlayNames[i - 1] = "IconifyComponentSIP" + i + ".overlay";
                }
                OverlayUtil.enableOverlays(overlayNames);
            } else {
                for (int i = 1; i <= mPackages.length; i++) {
                    Shell.cmd("cp -rf " + Resources.SIGNED_DIR + "/IconifyComponentSIP" + i + ".apk " + Resources.BACKUP_DIR + "/IconifyComponentSIP" + i + ".apk").exec();
                }
            }
        }

        // Clean temp directory
        Shell.cmd("rm -rf " + Resources.TEMP_DIR).exec();
        Shell.cmd("rm -rf " + Resources.DATA_DIR + "/CompileOnDemand").exec();
    }

    private static void moveOverlaysToCache() {
        for (int i = 0; i < mPackages.length; i++) {
            Shell.cmd("mv -f \"" + Resources.DATA_DIR + "/CompileOnDemand/" + mPackages[i] + "/" + "SIP" + mIconSet + "\" \"" + Resources.TEMP_CACHE_DIR + "/" + mPackages[i] + "/" + "SIP" + (i + 1) + "\"").exec();
        }

        if (mIconBg == 1) {
            for (int i = 0; i < mPackages.length; i++) {
                Shell.cmd("rm -rf \"" + Resources.TEMP_CACHE_DIR + "/" + mPackages[i] + "/" + "SIP" + (i + 1) + "/res/drawable\"", "cp -rf \"" + Resources.TEMP_CACHE_DIR + "/" + mPackages[i] + "/" + "SIP" + (i + 1) + "/res/drawable-night\" \"" + Resources.TEMP_CACHE_DIR + "/" + mPackages[i] + "/" + "SIP" + (i + 1) + "/res/drawable\"").exec();
                Shell.cmd("rm -rf \"" + Resources.TEMP_CACHE_DIR + "/" + mPackages[i] + "/" + "SIP" + (i + 1) + "/res/drawable-anydpi\"", "cp -rf \"" + Resources.TEMP_CACHE_DIR + "/" + mPackages[i] + "/" + "SIP" + (i + 1) + "/res/drawable-night-anydpi\" \"" + Resources.TEMP_CACHE_DIR + "/" + mPackages[i] + "/" + "SIP" + (i + 1) + "/res/drawable-anydpi\"").exec();
            }
        }
    }

    private static boolean writeResources(String source, String resources) {
        Shell.Result result = Shell.cmd("rm -rf " + source + "/res/values/Iconify.xml", "printf '" + resources + "' > " + source + "/res/values/Iconify.xml;").exec();

        if (result.isSuccess())
            Log.i(TAG + " - WriteResources", "Successfully written resources for SettingsIcons");
        else {
            Log.e(TAG + " - WriteResources", "Failed to write resources for SettingsIcons" + '\n' + String.join("\n", result.getOut()));
            writeLog(TAG + " - WriteResources", "Failed to write resources for SettingsIcons", result.getOut());
        }

        return !result.isSuccess();
    }
}
