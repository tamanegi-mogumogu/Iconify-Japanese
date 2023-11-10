package com.drdisagree.iconify.ui.activities;

import static com.drdisagree.iconify.common.Const.TRANSITION_DELAY;
import static com.drdisagree.iconify.common.Preferences.FIRST_INSTALL;
import static com.drdisagree.iconify.common.Preferences.ON_HOME_PAGE;
import static com.drdisagree.iconify.common.Preferences.UPDATE_DETECTED;
import static com.drdisagree.iconify.common.Preferences.VER_CODE;
import static com.drdisagree.iconify.common.Preferences.XPOSED_ONLY_MODE;
import static com.drdisagree.iconify.utils.SystemUtil.isDarkMode;
import static com.drdisagree.iconify.utils.helper.Logger.writeLog;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.TransitionDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager2.widget.ViewPager2;

import com.airbnb.lottie.LottieCompositionFactory;
import com.airbnb.lottie.RenderMode;
import com.drdisagree.iconify.BuildConfig;
import com.drdisagree.iconify.R;
import com.drdisagree.iconify.common.Resources;
import com.drdisagree.iconify.config.Prefs;
import com.drdisagree.iconify.config.RPrefs;
import com.drdisagree.iconify.databinding.ActivityOnboardingBinding;
import com.drdisagree.iconify.ui.adapters.OnboardingAdapter;
import com.drdisagree.iconify.ui.base.BaseActivity;
import com.drdisagree.iconify.ui.dialogs.InstallationDialog;
import com.drdisagree.iconify.ui.utils.Animatoo;
import com.drdisagree.iconify.utils.FileUtil;
import com.drdisagree.iconify.utils.ModuleUtil;
import com.drdisagree.iconify.utils.RootUtil;
import com.drdisagree.iconify.utils.SystemUtil;
import com.drdisagree.iconify.utils.extension.TaskExecutor;
import com.drdisagree.iconify.utils.helper.BackupRestore;
import com.drdisagree.iconify.utils.overlay.OverlayUtil;
import com.drdisagree.iconify.utils.overlay.compiler.OnboardingCompiler;
import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class Onboarding extends BaseActivity {

    private static final String mData = "mDataKey";
    public static boolean skippedInstallation = false;
    private static boolean hasErroredOut = false, rebootRequired = false;
    private static StartInstallationProcess installModule = null;
    private final String TAG = Onboarding.class.getSimpleName();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ActivityOnboardingBinding binding;
    private int previousPage = 0;
    private InstallationDialog progressDialog;
    private String logger = null, prev_log = null;
    private int selectedItemPosition = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Prefs.putBoolean(ON_HOME_PAGE, false);

        OnboardingAdapter mAdapter = new OnboardingAdapter(this, this);
        binding.viewPager.setAdapter(mAdapter);
        binding.viewPager.setOffscreenPageLimit(mAdapter.getItemCount());

        // Progress dialog while installing
        progressDialog = new InstallationDialog(this);

        binding.viewPager.setCurrentItem(selectedItemPosition);

        AtomicBoolean clickedContinue = new AtomicBoolean(false);

        // Skip button
        binding.btnSkip.setOnClickListener(v -> {
            int lastItemIndex = Objects.requireNonNull(binding.viewPager.getAdapter()).getItemCount() - 1;
            binding.viewPager.setCurrentItem(lastItemIndex, true);
            Animatoo.animateSlideLeft(this);
        });

        // Next button
        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                final int duration = 200;

                if (position != previousPage) {
                    handler.removeCallbacksAndMessages(null);

                    if (position > previousPage) {
                        getWindow().getDecorView().setBackground(getWindowDrawables()[previousPage]);
                        binding.btnNextStep.setBackground(getButtonDrawables()[previousPage]);

                        ((TransitionDrawable) getWindow().getDecorView().getBackground()).startTransition(duration);
                        ((TransitionDrawable) binding.btnNextStep.getBackground()).startTransition(duration);

                        handler.postDelayed(() -> {
                            getWindow().getDecorView().setBackground(getWindowDrawables()[position]);
                            binding.btnNextStep.setBackground(getButtonDrawables()[position]);
                        }, duration);
                    } else {
                        getWindow().getDecorView().setBackground(getWindowDrawables()[position]);
                        binding.btnNextStep.setBackground(getButtonDrawables()[position]);

                        ((TransitionDrawable) getWindow().getDecorView().getBackground()).startTransition(0);
                        ((TransitionDrawable) binding.btnNextStep.getBackground()).startTransition(0);
                        ((TransitionDrawable) getWindow().getDecorView().getBackground()).reverseTransition(duration);
                        ((TransitionDrawable) binding.btnNextStep.getBackground()).reverseTransition(duration);

                        handler.postDelayed(() -> {
                            getWindow().getDecorView().setBackground(getWindowDrawables()[position]);
                            binding.btnNextStep.setBackground(getButtonDrawables()[position]);
                        }, duration);
                    }

                    previousPage = position;
                } else {
                    getWindow().getDecorView().setBackground(getWindowDrawables()[position]);
                    binding.btnNextStep.setBackground(getButtonDrawables()[position]);
                }

                if (position == 2) {
                    binding.btnSkip.setVisibility(View.INVISIBLE);
                    binding.btnNextStep.setText(R.string.btn_lets_go);
                } else {
                    binding.btnSkip.setVisibility(View.VISIBLE);
                    binding.btnNextStep.setText(R.string.btn_next);
                }

                if (position == 2) {
                    // Reboot button
                    if (rebootRequired) {
                        Prefs.putBoolean(XPOSED_ONLY_MODE, false);
                        showInfoInstant(R.string.need_reboot_title, R.string.need_reboot_desc);
                        binding.btnNextStep.setText(R.string.btn_reboot);
                        binding.btnNextStep.setTextColor(getResources().getColor(R.color.onboarding_btn_text, getTheme()));
                    }

                    // Skip installation on long click
                    binding.btnNextStep.setOnLongClickListener(v -> {
                        skippedInstallation = true;
                        hasErroredOut = false;

                        Shell.getShell(shell -> {
                            if (RootUtil.isDeviceRooted()) {
                                if (RootUtil.isMagiskInstalled() || RootUtil.isKSUInstalled()) {
                                    if (!SystemUtil.hasStoragePermission()) {
                                        showInfo(R.string.need_storage_perm_title, R.string.need_storage_perm_desc);
                                        Toast.makeText(Onboarding.this, R.string.toast_storage_access, Toast.LENGTH_SHORT).show();

                                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                            clickedContinue.set(true);
                                            SystemUtil.requestStoragePermission(Onboarding.this);
                                        }, clickedContinue.get() ? 10 : 2000);
                                    } else {
                                        if (!ModuleUtil.moduleExists()) {
                                            Prefs.clearAllPrefs();
                                            RPrefs.clearAllPrefs();

                                            handleInstallation();
                                        } else {
                                            Prefs.putBoolean(XPOSED_ONLY_MODE, true);
                                            Intent intent = new Intent(Onboarding.this, HomePage.class);
                                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                                            startActivity(intent);
                                            Animatoo.animateSlideLeft(Onboarding.this);
                                            Toast.makeText(Onboarding.this, R.string.toast_skipped_installation, Toast.LENGTH_LONG).show();
                                        }
                                    }
                                } else {
                                    showInfo(R.string.magisk_not_found_title, R.string.magisk_not_found_desc);
                                }
                            } else {
                                showInfo(R.string.root_not_found_title, R.string.root_not_found_desc);
                            }
                        });
                        return true;
                    });
                } else {
                    binding.btnNextStep.setOnLongClickListener(null);
                }
            }
        });

        // Start installation on click
        final boolean[] isClickable = {true};
        binding.btnNextStep.setOnClickListener(v -> {
            if (isClickable[0]) {
                isClickable[0] = false;

                if (binding.viewPager.getCurrentItem() > binding.viewPager.getChildCount()) {
                    skippedInstallation = false;
                    hasErroredOut = false;

                    Shell.getShell(shell -> {
                        if (RootUtil.isDeviceRooted()) {
                            if (RootUtil.isMagiskInstalled() || RootUtil.isKSUInstalled()) {
                                if (!SystemUtil.hasStoragePermission()) {
                                    showInfo(R.string.need_storage_perm_title, R.string.need_storage_perm_desc);
                                    Toast.makeText(Onboarding.this, R.string.toast_storage_access, Toast.LENGTH_SHORT).show();

                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                        clickedContinue.set(true);
                                        SystemUtil.requestStoragePermission(this);
                                    }, clickedContinue.get() ? 10 : 2000);
                                } else {
                                    boolean moduleExists = ModuleUtil.moduleExists();
                                    boolean overlayExists = OverlayUtil.overlayExists();

                                    if ((Prefs.getInt(VER_CODE) != BuildConfig.VERSION_CODE) || !moduleExists || !overlayExists) {
                                        if (!moduleExists || !overlayExists) {
                                            Prefs.clearAllPrefs();
                                            RPrefs.clearAllPrefs();
                                        }

                                        handleInstallation();
                                    } else {
                                        Prefs.putBoolean(XPOSED_ONLY_MODE, false);
                                        Intent intent = new Intent(Onboarding.this, HomePage.class);
                                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                                        startActivity(intent);
                                        Animatoo.animateSlideLeft(Onboarding.this);
                                    }
                                }
                            } else {
                                showInfo(R.string.magisk_not_found_title, R.string.magisk_not_found_desc);
                            }
                        } else {
                            showInfo(R.string.root_not_found_title, R.string.root_not_found_desc);
                        }
                    });
                } else {
                    binding.viewPager.setCurrentItem(binding.viewPager.getCurrentItem() + 1, true);
                }

                new Handler(Looper.getMainLooper()).postDelayed(() -> isClickable[0] = true, TRANSITION_DELAY + 50);
            }
        });

        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::onBackPressed);
        } else {
            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    onBackPressed();
                }
            });
        }
    }

    private void handleInstallation() {
        LottieCompositionFactory.fromRawRes(this, !isDarkMode() ? R.raw.loading_day : R.raw.loading_night).addListener(result -> {
            binding.loadingAnim.setMaxWidth(binding.btnNextStep.getHeight());
            binding.loadingAnim.setMaxHeight(binding.btnNextStep.getHeight());
            binding.btnNextStep.setTextColor(Color.TRANSPARENT);
            binding.loadingAnim.setAnimation(!isDarkMode() ? R.raw.loading_day : R.raw.loading_night);
            binding.loadingAnim.setRenderMode(RenderMode.HARDWARE);
            binding.loadingAnim.setVisibility(View.VISIBLE);

            installModule = new Onboarding.StartInstallationProcess();
            installModule.execute();
        });
    }

    private void showInfo(int title, int desc) {
        if (getCurrentFragment() instanceof com.drdisagree.iconify.ui.fragments.Onboarding) {
            ((com.drdisagree.iconify.ui.fragments.Onboarding) getCurrentFragment()).animateUpdateTextView(title, desc);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void showInfoInstant(int title, int desc) {
        if (getCurrentFragment() instanceof com.drdisagree.iconify.ui.fragments.Onboarding) {
            ((com.drdisagree.iconify.ui.fragments.Onboarding) getCurrentFragment()).updateTextView(title, desc);
        }
    }

    private Fragment getCurrentFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        return fragmentManager.findFragmentByTag("f" + binding.viewPager.getCurrentItem());
    }

    private TransitionDrawable[] getWindowDrawables() {
        TransitionDrawable[] transitionDrawable = new TransitionDrawable[3];
        transitionDrawable[0] = (TransitionDrawable) ResourcesCompat.getDrawable(getResources(), R.drawable.onboarding_background_1, getTheme());
        transitionDrawable[1] = (TransitionDrawable) ResourcesCompat.getDrawable(getResources(), R.drawable.onboarding_background_2, getTheme());
        transitionDrawable[2] = (TransitionDrawable) ResourcesCompat.getDrawable(getResources(), R.drawable.onboarding_background_3, getTheme());
        return transitionDrawable;
    }

    private TransitionDrawable[] getButtonDrawables() {
        TransitionDrawable[] transitionDrawable = new TransitionDrawable[3];
        transitionDrawable[0] = (TransitionDrawable) ResourcesCompat.getDrawable(getResources(), R.drawable.onboarding_button_transition_1, getTheme());
        transitionDrawable[1] = (TransitionDrawable) ResourcesCompat.getDrawable(getResources(), R.drawable.onboarding_button_transition_2, getTheme());
        transitionDrawable[2] = (TransitionDrawable) ResourcesCompat.getDrawable(getResources(), R.drawable.onboarding_button_transition_3, getTheme());
        return transitionDrawable;
    }

    private void cancelledInstallation() {
        Prefs.clearPref(XPOSED_ONLY_MODE);
        Shell.cmd("rm -rf " + Resources.DATA_DIR).exec();
        Shell.cmd("rm -rf " + Resources.TEMP_DIR).exec();
        Shell.cmd("rm -rf " + Resources.BACKUP_DIR).exec();
        Shell.cmd("rm -rf " + Resources.MODULE_DIR).exec();
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(this::onBackPressed);
            } catch (Exception ignored) {
            }
        }

        if (progressDialog != null) {
            progressDialog.dismiss();
        }

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }

        if (installModule != null) {
            installModule.cancel(true);
        }

        cancelledInstallation();

        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (binding.viewPager.getCurrentItem() == 0) {
            super.onBackPressed();
        } else {
            binding.viewPager.setCurrentItem(binding.viewPager.getCurrentItem() - 1, true);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(mData, binding.viewPager.getCurrentItem());
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        selectedItemPosition = savedInstanceState.getInt(mData);
        if (selectedItemPosition == 2) {
            binding.btnSkip.setVisibility(View.INVISIBLE);
            binding.btnNextStep.setText(R.string.btn_lets_go);
        } else {
            binding.btnSkip.setVisibility(View.VISIBLE);
            binding.btnNextStep.setText(R.string.btn_next);
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class StartInstallationProcess extends TaskExecutor<Void, Integer, Integer> {
        @SuppressLint("SetTextI18n")
        @Override
        protected void onPreExecute() {
            showInfo(R.string.onboarding_title_3, R.string.onboarding_desc_3);
            binding.btnNextStep.setText(R.string.btn_lets_go);

            progressDialog.show(getResources().getString(R.string.installing), getResources().getString(R.string.init_module_installation));
        }

        @SuppressLint("SetTextI18n")
        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);

            String title = getResources().getString(R.string.step) + ' ' + values[0] + "/6";
            getResources().getString(R.string.loading_dialog_wait);
            String desc = switch (values[0]) {
                case 1 -> getResources().getString(R.string.module_installation_step1);
                case 2 -> getResources().getString(R.string.module_installation_step2);
                case 3 -> getResources().getString(R.string.module_installation_step3);
                case 4 -> getResources().getString(R.string.module_installation_step4);
                case 5 -> getResources().getString(R.string.module_installation_step5);
                case 6 -> getResources().getString(R.string.module_installation_step6);
                default -> getResources().getString(R.string.loading_dialog_wait);
            };

            progressDialog.setMessage(title, desc);

            if (logger != null && !Objects.equals(prev_log, logger)) {
                progressDialog.setLogs(logger);
                prev_log = logger;
            }
        }

        @Override
        protected Integer doInBackground(Void... voids) {
            int step = 0;

            logger = "Creating blank module template";
            publishProgress(++step);
            ModuleUtil.handleModule();

            logger = null;
            publishProgress(++step);
            try {
                logger = "Cleaning iconify data directory";
                publishProgress(step);
                // Clean data directory
                Shell.cmd("rm -rf " + Resources.DATA_DIR + "/Overlays").exec();

                logger = "Extracting overlays from assets";
                publishProgress(step);
                if (skippedInstallation) {
                    logger = "Skipped...";
                    publishProgress(step);
                } else {
                    // Extract overlays from assets
                    FileUtil.copyAssets("Overlays");
                }

                logger = "Creating temporary directories";
                publishProgress(step);
                // Create temp directory
                Shell.cmd("mkdir -p " + Resources.TEMP_OVERLAY_DIR).exec();
                Shell.cmd("mkdir -p " + Resources.UNSIGNED_UNALIGNED_DIR).exec();
                Shell.cmd("mkdir -p " + Resources.UNSIGNED_DIR).exec();
                Shell.cmd("mkdir -p " + Resources.SIGNED_DIR).exec();
            } catch (IOException e) {
                hasErroredOut = true;
                Log.e(TAG, e.toString());
            }

            logger = null;
            publishProgress(++step);
            File dir;
            if (skippedInstallation) {
                logger = "Skipping overlay builder...";
                publishProgress(step);
            } else {
                // Create AndroidManifest.xml and build Overlay using AAPT
                dir = new File(Resources.DATA_DIR + "/Overlays");
                if (dir.listFiles() == null) hasErroredOut = true;

                if (!hasErroredOut) {
                    for (File pkg : Objects.requireNonNull(dir.listFiles())) {
                        if (pkg.isDirectory()) {
                            for (File overlay : Objects.requireNonNull(pkg.listFiles())) {
                                if (overlay.isDirectory()) {
                                    String overlay_name = overlay.toString().replace(pkg.toString() + '/', "");

                                    if (OnboardingCompiler.createManifest(overlay_name, pkg.toString().replace(Resources.DATA_DIR + "/Overlays/", ""), overlay.getAbsolutePath())) {
                                        hasErroredOut = true;
                                    }

                                    logger = "Building Overlay for " + overlay_name;
                                    publishProgress(step);

                                    if (!hasErroredOut && OnboardingCompiler.runAapt(overlay.getAbsolutePath(), overlay_name)) {
                                        hasErroredOut = true;
                                    }
                                }
                                if (hasErroredOut) break;
                            }
                        }
                        if (hasErroredOut) break;
                    }
                }
            }

            logger = null;
            publishProgress(++step);
            if (skippedInstallation) {
                logger = "Skipping zipaligning process...";
                publishProgress(step);
            } else {
                // ZipAlign the Overlay
                dir = new File(Resources.UNSIGNED_UNALIGNED_DIR);
                if (dir.listFiles() == null) hasErroredOut = true;

                if (!hasErroredOut) {
                    for (File overlay : Objects.requireNonNull(dir.listFiles())) {
                        if (!overlay.isDirectory()) {
                            String overlay_name = overlay.toString().replace(Resources.UNSIGNED_UNALIGNED_DIR + '/', "").replace("-unaligned", "");

                            logger = "Zip aligning Overlay " + overlay_name.replace("-unsigned.apk", "");
                            publishProgress(step);

                            if (OnboardingCompiler.zipAlign(overlay.getAbsolutePath(), overlay_name)) {
                                hasErroredOut = true;
                            }
                        }
                        if (hasErroredOut) break;
                    }
                }
            }

            logger = null;
            publishProgress(++step);
            if (skippedInstallation) {
                logger = "Skipping signing process...";
                publishProgress(step);
            } else {
                // Sign the Overlay
                dir = new File(Resources.UNSIGNED_DIR);
                if (dir.listFiles() == null) hasErroredOut = true;

                if (!hasErroredOut) {
                    for (File overlay : Objects.requireNonNull(dir.listFiles())) {
                        if (!overlay.isDirectory()) {
                            String overlay_name = overlay.toString().replace(Resources.UNSIGNED_DIR + '/', "").replace("-unsigned", "");

                            logger = "Signing Overlay " + overlay_name.replace(".apk", "");
                            publishProgress(step);

                            int attempt = 3;
                            while (attempt-- != 0) {
                                hasErroredOut = OnboardingCompiler.apkSigner(overlay.getAbsolutePath(), overlay_name);

                                if (!hasErroredOut) break;
                                else try {
                                    Thread.sleep(1000);
                                } catch (Exception ignored) {
                                }
                            }
                        }
                        if (hasErroredOut) break;
                    }
                }
            }

            logger = "Moving overlays to system directory";
            publishProgress(++step);
            if (skippedInstallation) {
                logger = "Skipping...";
                publishProgress(step);
            }
            // Move all generated overlays to system dir and flash as module
            if (!hasErroredOut) {
                Shell.cmd("cp -a " + Resources.SIGNED_DIR + "/. " + Resources.TEMP_MODULE_OVERLAY_DIR).exec();
                BackupRestore.restoreFiles();

                try {
                    hasErroredOut = ModuleUtil.flashModule(ModuleUtil.createModule(Resources.TEMP_MODULE_DIR, Resources.TEMP_DIR + "/Iconify.zip"));
                } catch (Exception e) {
                    hasErroredOut = true;
                    writeLog(TAG, "Failed to create/flash module zip", e);
                    Log.e(TAG, "Failed to create/flash module zip\n" + e);
                }
            }

            logger = "Cleaning temporary directories";
            publishProgress(step);
            // Clean temp directory
            Shell.cmd("rm -rf " + Resources.TEMP_DIR).exec();
            Shell.cmd("rm -rf " + Resources.DATA_DIR + "/Overlays").exec();

            logger = "Installtion process finished";
            publishProgress(step);

            return null;
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        protected void onPostExecute(Integer integer) {
            progressDialog.hide();

            if (!hasErroredOut) {
                if (!skippedInstallation) {
                    if (BuildConfig.VERSION_CODE != SystemUtil.getSavedVersionCode()) {
                        if (Prefs.getBoolean(FIRST_INSTALL, true)) {
                            Prefs.putBoolean(FIRST_INSTALL, true);
                            Prefs.putBoolean(UPDATE_DETECTED, false);
                        } else {
                            Prefs.putBoolean(FIRST_INSTALL, false);
                            Prefs.putBoolean(UPDATE_DETECTED, true);
                        }
                        Prefs.putInt(VER_CODE, BuildConfig.VERSION_CODE);
                    }

                    Prefs.putBoolean(XPOSED_ONLY_MODE, false);

                    if (OverlayUtil.overlayExists()) {
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            Intent intent = new Intent(Onboarding.this, HomePage.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            Animatoo.animateSlideLeft(Onboarding.this);
                        }, 10);
                    } else {
                        rebootRequired = true;
                        showInfo(R.string.need_reboot_title, R.string.need_reboot_desc);
                        binding.btnNextStep.setText(R.string.btn_reboot);
                        binding.btnNextStep.setTextColor(getResources().getColor(R.color.onboarding_btn_text, getTheme()));
                        binding.btnNextStep.setOnClickListener(view -> SystemUtil.restartDevice());
                        binding.btnNextStep.setOnLongClickListener(null);
                    }
                } else {
                    Prefs.putBoolean(XPOSED_ONLY_MODE, true);
                    Intent intent = new Intent(Onboarding.this, HomePage.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    Animatoo.animateSlideLeft(Onboarding.this);
                    Toast.makeText(Onboarding.this, R.string.one_time_reboot_needed, Toast.LENGTH_LONG).show();
                }
            } else {
                cancelledInstallation();
                showInfo(R.string.installation_failed_title, R.string.installation_failed_desc);
                binding.btnNextStep.setText(R.string.btn_lets_go);
            }

            binding.loadingAnim.setVisibility(View.GONE);
            binding.btnNextStep.setTextColor(getResources().getColor(R.color.onboarding_btn_text, getTheme()));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(Onboarding.this::onBackPressed);
                } catch (Exception ignored) {
                }
            }
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            cancelledInstallation();
        }
    }
}
