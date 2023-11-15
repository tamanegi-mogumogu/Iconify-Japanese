package com.drdisagree.iconify.xposed.modules;

import static com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE;
import static com.drdisagree.iconify.common.Preferences.DEPTH_WALLPAPER_CHANGED;
import static com.drdisagree.iconify.common.Preferences.DEPTH_WALLPAPER_FADE_ANIMATION;
import static com.drdisagree.iconify.common.Preferences.DEPTH_WALLPAPER_SWITCH;
import static com.drdisagree.iconify.common.Preferences.ICONIFY_DEPTH_WALLPAPER_TAG;
import static com.drdisagree.iconify.common.Preferences.ICONIFY_LOCKSCREEN_CLOCK_TAG;
import static com.drdisagree.iconify.common.Preferences.UNZOOM_DEPTH_WALLPAPER;
import static com.drdisagree.iconify.config.XPrefs.Xprefs;
import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findClassIfExists;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.ImageDecoder;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.drdisagree.iconify.xposed.ModPack;
import com.drdisagree.iconify.xposed.modules.utils.DisplayUtils;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

@SuppressLint("DiscouragedApi")
public class DepthWallpaper extends ModPack {

    private static final String TAG = "Iconify - " + DepthWallpaper.class.getSimpleName() + ": ";
    private boolean showDepthWallpaper = false;
    private boolean showFadingAnimation = false;
    private FrameLayout mDepthWallpaperLayout = null;
    private ImageView mDepthWallpaperBackground = null;
    private ImageView mDepthWallpaperForeground = null;

    public DepthWallpaper(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;

        showDepthWallpaper = Xprefs.getBoolean(DEPTH_WALLPAPER_SWITCH, false);
        showFadingAnimation = Xprefs.getBoolean(DEPTH_WALLPAPER_FADE_ANIMATION, false);

        if (Key.length > 0 && (Objects.equals(Key[0], DEPTH_WALLPAPER_SWITCH) ||
                Objects.equals(Key[0], DEPTH_WALLPAPER_CHANGED))
        ) {
            updateWallpaper();
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        Class<?> KeyguardBottomAreaViewClass = findClass(SYSTEMUI_PACKAGE + ".statusbar.phone.KeyguardBottomAreaView", loadPackageParam.classLoader);

        hookAllMethods(KeyguardBottomAreaViewClass, "onFinishInflate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!showDepthWallpaper) return;

                View view = (View) param.thisObject;
                ViewGroup mIndicationArea = view.findViewById(mContext.getResources().getIdentifier("keyguard_indication_area", "id", mContext.getPackageName()));

                mIndicationArea.setClipChildren(false);
                mIndicationArea.setClipToPadding(false);
                mIndicationArea.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                mIndicationArea.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
                ((ViewGroup.MarginLayoutParams) mIndicationArea.getLayoutParams()).bottomMargin = 0;

                // Create a new layout for the indication text views
                LinearLayout mIndicationTextView = new LinearLayout(mContext);
                LinearLayout.LayoutParams mIndicationViewParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                int bottomMargin = mContext.getResources().getDimensionPixelSize(mContext.getResources().getIdentifier("keyguard_indication_margin_bottom", "dimen", mContext.getPackageName()));
                mIndicationViewParams.setMargins(0, 0, 0, bottomMargin);
                mIndicationViewParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                mIndicationTextView.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                mIndicationTextView.setOrientation(LinearLayout.VERTICAL);
                mIndicationTextView.setLayoutParams(mIndicationViewParams);

                // Add the indication text views to the new layout
                TextView mTopIndicationView = mIndicationArea.findViewById(mContext.getResources().getIdentifier("keyguard_indication_text", "id", mContext.getPackageName()));
                TextView mLockScreenIndicationView = mIndicationArea.findViewById(mContext.getResources().getIdentifier("keyguard_indication_text_bottom", "id", mContext.getPackageName()));

                // We added a blank view to the top of the layout to push the indication text views to the bottom
                // The reason we did this is because gravity is not working properly on the indication text views
                View blankView = new View(mContext);
                blankView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

                // Remove the existing indication text views from the indication area
                ((ViewGroup) mTopIndicationView.getParent()).removeView(mTopIndicationView);
                ((ViewGroup) mLockScreenIndicationView.getParent()).removeView(mLockScreenIndicationView);

                // Add the indication text views to the new layout
                mIndicationTextView.addView(blankView);
                mIndicationTextView.addView(mTopIndicationView);
                mIndicationTextView.addView(mLockScreenIndicationView);

                FrameLayout mIndicationAreaDupe = new FrameLayout(mContext);
                mIndicationAreaDupe.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                mIndicationAreaDupe.addView(mIndicationTextView, -1);
                mIndicationArea.addView(mIndicationAreaDupe);

                // Get the depth wallpaper layout
                mDepthWallpaperLayout = mIndicationArea.findViewWithTag(ICONIFY_DEPTH_WALLPAPER_TAG);

                // Create the depth wallpaper layout if it doesn't exist
                if (mDepthWallpaperLayout == null) {
                    mDepthWallpaperLayout = new FrameLayout(mContext);
                    mDepthWallpaperLayout.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    mDepthWallpaperLayout.setTag(ICONIFY_DEPTH_WALLPAPER_TAG);
                    mIndicationAreaDupe.addView(mDepthWallpaperLayout, 0);
                }

                mDepthWallpaperBackground = new ImageView(mContext);
                mDepthWallpaperForeground = new ImageView(mContext);

                mDepthWallpaperBackground.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                mDepthWallpaperForeground.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                mDepthWallpaperLayout.addView(mDepthWallpaperBackground, 0);
                mDepthWallpaperLayout.addView(mDepthWallpaperForeground, -1);

                // Fix the bottom shortcuts pushing the wallpaper
                int[] offset = {0};
                try {
                    offset[0] = mContext.getResources().getDimensionPixelSize(mContext.getResources().getIdentifier("keyguard_affordance_fixed_height", "dimen", mContext.getPackageName()))
                            + mContext.getResources().getDimensionPixelSize(mContext.getResources().getIdentifier("keyguard_affordance_horizontal_offset", "dimen", mContext.getPackageName()));
                } catch (Throwable ignored) {
                }

                try {
                    ImageView startButton = view.findViewById(mContext.getResources().getIdentifier("start_button", "id", mContext.getPackageName()));
                    startButton.getViewTreeObserver().addOnGlobalLayoutListener(() -> ((ViewGroup.MarginLayoutParams) mIndicationTextView.getLayoutParams()).setMarginStart(
                            startButton.getVisibility() != View.GONE ? offset[0] : 0)
                    );
                } catch (Throwable ignored) {
                }

                try {
                    ImageView endButton = view.findViewById(mContext.getResources().getIdentifier("end_button", "id", mContext.getPackageName()));
                    endButton.getViewTreeObserver().addOnGlobalLayoutListener(() -> ((ViewGroup.MarginLayoutParams) mIndicationTextView.getLayoutParams()).setMarginEnd(
                            endButton.getVisibility() != View.GONE ? offset[0] : 0)
                    );
                } catch (Throwable ignored) {
                }

                if (Build.VERSION.SDK_INT >= 34) {
                    try {
                        LinearLayout keyguard_settings_button = view.findViewById(mContext.getResources().getIdentifier("keyguard_settings_button", "id", mContext.getPackageName()));
                        keyguard_settings_button.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                            ((ViewGroup.MarginLayoutParams) mIndicationTextView.getLayoutParams()).setMarginStart(keyguard_settings_button.getVisibility() != View.GONE ? offset[0] : 0);
                            ((ViewGroup.MarginLayoutParams) mIndicationTextView.getLayoutParams()).setMarginEnd(keyguard_settings_button.getVisibility() != View.GONE ? offset[0] : 0);
                        });
                    } catch (Throwable ignored) {
                    }
                }

                updateWallpaper();

                registerScreenStateChecker();
            }
        });

        hookAllMethods(KeyguardBottomAreaViewClass, "onConfigurationChanged", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                updateWallpaper();
            }
        });

        Class<?> NotificationPanelViewControllerClass = findClassIfExists(SYSTEMUI_PACKAGE + ".shade.NotificationPanelViewController", loadPackageParam.classLoader);
        if (NotificationPanelViewControllerClass == null)
            NotificationPanelViewControllerClass = findClass(SYSTEMUI_PACKAGE + ".statusbar.phone.NotificationPanelViewController", loadPackageParam.classLoader);

        XC_MethodHook moveKeyguardBottomArea = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!showDepthWallpaper) return;

                View mView = (View) getObjectField(param.thisObject, "mView");
                View keyguardBottomArea = mView.findViewById(mContext.getResources().getIdentifier("keyguard_bottom_area", "id", mContext.getPackageName()));
                ViewGroup parent = (ViewGroup) keyguardBottomArea.getParent();
                parent.removeView(keyguardBottomArea);
                parent.addView(keyguardBottomArea, 0);
            }
        };

        hookAllMethods(NotificationPanelViewControllerClass, "onFinishInflate", moveKeyguardBottomArea);
        hookAllMethods(NotificationPanelViewControllerClass, "reInflateViews", moveKeyguardBottomArea);

        XC_MethodHook noKeyguardIndicationPadding = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (showDepthWallpaper) {
                    if (Build.VERSION.SDK_INT >= 33) {
                        try {
                            int resId = mContext.getResources().getIdentifier("keyguard_indication_area_padding", "dimen", mContext.getPackageName());
                            if (param.args[0].equals(resId)) {
                                param.setResult(0);
                            }
                        } catch (Throwable ignored) {
                        }
                    } else {
                        // These resources are only available on Android 12L and below
                        try {
                            int resId = mContext.getResources().getIdentifier("keyguard_indication_margin_bottom", "dimen", mContext.getPackageName());
                            if (param.args[0].equals(resId)) {
                                param.setResult(0);
                            }
                        } catch (Throwable ignored) {
                        }

                        try {
                            int resId = mContext.getResources().getIdentifier("keyguard_indication_margin_bottom_fingerprint_in_display", "dimen", mContext.getPackageName());
                            if (param.args[0].equals(resId)) {
                                param.setResult(0);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        };

        hookAllMethods(Resources.class, "getDimensionPixelOffset", noKeyguardIndicationPadding);
        hookAllMethods(Resources.class, "getDimensionPixelSize", noKeyguardIndicationPadding);
    }

    // Broadcast receiver for checking screen state
    private void registerScreenStateChecker() {
        if (mDepthWallpaperLayout == null) return;

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);

        BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null) {
                    new Handler(Looper.getMainLooper()).post(() -> updateFadeAnimation());
                }
            }
        };

        mContext.registerReceiver(screenStateReceiver, filter);

        updateFadeAnimation();
    }

    private void updateWallpaper() {
        if (mDepthWallpaperLayout == null) return;

        if (!showDepthWallpaper) {
            mDepthWallpaperLayout.setVisibility(View.GONE);
            return;
        }

        try {
            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            executor.scheduleAtFixedRate(() -> {
                File Android = new File(Environment.getExternalStorageDirectory() + "/Android");

                if (Android.isDirectory()) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        try {
                            ImageDecoder.Source backgroundImg = ImageDecoder.createSource(new File(Environment.getExternalStorageDirectory() + "/.iconify_files/depth_wallpaper_bg.png"));
                            ImageDecoder.Source foregroundImg = ImageDecoder.createSource(new File(Environment.getExternalStorageDirectory() + "/.iconify_files/depth_wallpaper_fg.png"));

                            Drawable backgroundDrawable = ImageDecoder.decodeDrawable(backgroundImg);
                            Drawable foregroundDrawable = ImageDecoder.decodeDrawable(foregroundImg);

                            mDepthWallpaperBackground.setImageDrawable(backgroundDrawable);
                            mDepthWallpaperBackground.setClipToOutline(true);
                            mDepthWallpaperBackground.setScaleType(ImageView.ScaleType.CENTER_CROP);

                            boolean zoomWallpaper = !Xprefs.getBoolean(UNZOOM_DEPTH_WALLPAPER, false);

                            if (zoomWallpaper) {
                                mDepthWallpaperBackground.setScaleX(1.1f);
                                mDepthWallpaperBackground.setScaleY(1.1f);
                            }

                            mDepthWallpaperForeground.setImageDrawable(foregroundDrawable);
                            mDepthWallpaperForeground.setClipToOutline(true);
                            mDepthWallpaperForeground.setScaleType(ImageView.ScaleType.CENTER_CROP);

                            if (zoomWallpaper) {
                                mDepthWallpaperForeground.setScaleX(1.1f);
                                mDepthWallpaperForeground.setScaleY(1.1f);
                            }

                            mDepthWallpaperLayout.setVisibility(View.VISIBLE);
                        } catch (Throwable ignored) {
                        }
                    });

                    executor.shutdown();
                    executor.shutdownNow();
                }
            }, 0, 5, TimeUnit.SECONDS);
        } catch (Throwable ignored) {
        }
    }

    private void updateFadeAnimation() {
        if (!showDepthWallpaper) return;

        View clockView = mDepthWallpaperLayout.findViewWithTag(ICONIFY_LOCKSCREEN_CLOCK_TAG);
        long animDuration = 800;

        if (DisplayUtils.isScreenOn(mContext)) {
            if (mDepthWallpaperBackground != null && mDepthWallpaperBackground.getAlpha() != 1f) {
                if (showFadingAnimation) {
                    Animation animation = mDepthWallpaperBackground.getAnimation();

                    if (!(animation != null && animation.hasStarted() && !animation.hasEnded())) {
                        mDepthWallpaperBackground.animate()
                                .alpha(1f)
                                .setDuration(animDuration)
                                .start();
                    }
                } else {
                    mDepthWallpaperBackground.setAlpha(1f);
                }
            }

            if (mDepthWallpaperForeground != null && mDepthWallpaperForeground.getAlpha() != 1f) {
                if (showFadingAnimation) {
                    Animation animation = mDepthWallpaperForeground.getAnimation();

                    if (!(animation != null && animation.hasStarted() && !animation.hasEnded())) {
                        mDepthWallpaperForeground.clearAnimation();
                        mDepthWallpaperForeground.animate()
                                .alpha(1f)
                                .setDuration(animDuration)
                                .start();
                    }
                } else {
                    mDepthWallpaperForeground.setAlpha(1f);
                }
            }

            if (clockView != null && clockView.getAlpha() != 1f) {
                if (showFadingAnimation) {
                    Animation animation = clockView.getAnimation();

                    if (!(animation != null && animation.hasStarted() && !animation.hasEnded())) {
                        clockView.clearAnimation();
                        clockView.animate()
                                .alpha(1f)
                                .setDuration(animDuration)
                                .start();
                    }
                } else {
                    clockView.setAlpha(1f);
                }
            }
        } else {
            if (mDepthWallpaperBackground != null && mDepthWallpaperBackground.getAlpha() != 0f) {
                if (showFadingAnimation) {
                    Animation animation = mDepthWallpaperBackground.getAnimation();

                    if (!(animation != null && animation.hasStarted() && !animation.hasEnded())) {
                        mDepthWallpaperBackground.clearAnimation();
                        mDepthWallpaperBackground.animate()
                                .alpha(0f)
                                .setDuration(animDuration)
                                .start();
                    }
                }
            }

            if (mDepthWallpaperForeground != null && mDepthWallpaperForeground.getAlpha() != 0f) {
                if (showFadingAnimation) {
                    Animation animation = mDepthWallpaperForeground.getAnimation();

                    if (!(animation != null && animation.hasStarted() && !animation.hasEnded())) {
                        mDepthWallpaperForeground.clearAnimation();
                        mDepthWallpaperForeground.animate()
                                .alpha(0f)
                                .setDuration(animDuration)
                                .start();
                    }
                }
            }

            if (clockView != null && clockView.getAlpha() != 0.7f) {
                if (showFadingAnimation) {
                    Animation animation = clockView.getAnimation();

                    if (!(animation != null && animation.hasStarted() && !animation.hasEnded())) {
                        clockView.clearAnimation();
                        clockView.animate()
                                .alpha(0.7f)
                                .setDuration(animDuration)
                                .start();
                    }
                }
            }
        }
    }
}