package com.drdisagree.iconify.xposed.modules;

import static com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE;
import static com.drdisagree.iconify.common.Preferences.HEADER_IMAGE_ALPHA;
import static com.drdisagree.iconify.common.Preferences.HEADER_IMAGE_HEIGHT;
import static com.drdisagree.iconify.common.Preferences.HEADER_IMAGE_LANDSCAPE_SWITCH;
import static com.drdisagree.iconify.common.Preferences.HEADER_IMAGE_OVERLAP;
import static com.drdisagree.iconify.common.Preferences.HEADER_IMAGE_SWITCH;
import static com.drdisagree.iconify.common.Preferences.HEADER_IMAGE_ZOOMTOFIT;
import static com.drdisagree.iconify.config.XPrefs.Xprefs;
import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.drdisagree.iconify.xposed.ModPack;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HeaderImage extends ModPack implements IXposedHookLoadPackage {

    private static final String TAG = "Iconify - " + HeaderImage.class.getSimpleName() + ": ";
    private boolean showHeaderImage = false;
    private int imageHeight = 140;
    private int headerImageAlpha = 100;
    private boolean zoomToFit = false;
    private boolean headerImageOverlap = false;
    private boolean hideLandscapeHeaderImage = true;
    private LinearLayout mQsHeaderLayout = null;
    private ImageView mQsHeaderImageView = null;

    public HeaderImage(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;

        showHeaderImage = Xprefs.getBoolean(HEADER_IMAGE_SWITCH, false);
        headerImageAlpha = Xprefs.getInt(HEADER_IMAGE_ALPHA, 100);
        imageHeight = Xprefs.getInt(HEADER_IMAGE_HEIGHT, 140);
        zoomToFit = Xprefs.getBoolean(HEADER_IMAGE_ZOOMTOFIT, false);
        headerImageOverlap = Xprefs.getBoolean(HEADER_IMAGE_OVERLAP, false);
        hideLandscapeHeaderImage = Xprefs.getBoolean(HEADER_IMAGE_LANDSCAPE_SWITCH, true);

        if (Key.length > 0 && (Objects.equals(Key[0], HEADER_IMAGE_SWITCH) || Objects.equals(Key[0], HEADER_IMAGE_LANDSCAPE_SWITCH) || Objects.equals(Key[0], HEADER_IMAGE_ALPHA) || Objects.equals(Key[0], HEADER_IMAGE_HEIGHT) || Objects.equals(Key[0], HEADER_IMAGE_ZOOMTOFIT))) {
            updateQSHeaderImage();
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        final Class<?> QuickStatusBarHeader = findClass(SYSTEMUI_PACKAGE + ".qs.QuickStatusBarHeader", loadPackageParam.classLoader);
        final Class<?> QSContainerImpl = findClass(SYSTEMUI_PACKAGE + ".qs.QSContainerImpl", loadPackageParam.classLoader);

        try {
            hookAllMethods(QuickStatusBarHeader, "onFinishInflate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    FrameLayout mQuickStatusBarHeader = (FrameLayout) param.thisObject;

                    mQsHeaderLayout = new LinearLayout(mContext);
                    LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, imageHeight, mContext.getResources().getDisplayMetrics()));
                    layoutParams.leftMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, -16, mContext.getResources().getDisplayMetrics());
                    layoutParams.rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, -16, mContext.getResources().getDisplayMetrics());
                    mQsHeaderLayout.setLayoutParams(layoutParams);
                    mQsHeaderLayout.setVisibility(View.GONE);

                    mQsHeaderImageView = new ImageView(mContext);
                    mQsHeaderImageView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    mQsHeaderLayout.addView(mQsHeaderImageView);

                    mQuickStatusBarHeader.addView(mQsHeaderLayout, 0);

                    updateQSHeaderImage();
                }
            });

            hookAllMethods(QuickStatusBarHeader, "updateResources", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    updateQSHeaderImage();
                }
            });

            hookAllMethods(QuickStatusBarHeader, "onMeasure", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    View mDatePrivacyView = (View) getObjectField(param.thisObject, "mDatePrivacyView");
                    int mTopViewMeasureHeight = getIntField(param.thisObject, "mTopViewMeasureHeight");

                    if ((int) callMethod(mDatePrivacyView, "getMeasuredHeight") != mTopViewMeasureHeight) {
                        setObjectField(param.thisObject, "mTopViewMeasureHeight", callMethod(mDatePrivacyView, "getMeasuredHeight"));
                        callMethod(param.thisObject, "updateAnimators");
                    }
                }
            });

            hookAllMethods(QSContainerImpl, "onFinishInflate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (headerImageOverlap) return;

                    FrameLayout mHeader = (FrameLayout) getObjectField(param.thisObject, "mHeader");
                    ((FrameLayout) param.thisObject).removeView(mHeader);
                    ((FrameLayout) param.thisObject).addView(mHeader, 0);
                    ((FrameLayout) param.thisObject).requestLayout();
                }
            });
        } catch (Throwable throwable) {
            log(TAG + throwable);
        }
    }

    private void updateQSHeaderImage() {
        if (mQsHeaderLayout == null || mQsHeaderImageView == null) {
            return;
        }

        if (!showHeaderImage) {
            mQsHeaderLayout.setVisibility(View.GONE);
            return;
        }


        loadImageOrGif(mQsHeaderImageView);
        mQsHeaderImageView.setImageAlpha((int) (headerImageAlpha / 100.0 * 255.0));

        mQsHeaderLayout.getLayoutParams().height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, imageHeight, mContext.getResources().getDisplayMetrics());
        mQsHeaderLayout.requestLayout();

        Configuration config = mContext.getResources().getConfiguration();
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE && hideLandscapeHeaderImage) {
            mQsHeaderLayout.setVisibility(View.GONE);
        } else {
            mQsHeaderLayout.setVisibility(View.VISIBLE);
        }
    }

    private void addOrRemoveProperty(View view, int property, boolean flag) {
        try {
            RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) view.getLayoutParams();
            if (flag) {
                layoutParams.addRule(property);
            } else {
                layoutParams.removeRule(property);
            }
            view.setLayoutParams(layoutParams);
        } catch (Throwable throwable) {
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) view.getLayoutParams();
            if (flag) {
                layoutParams.gravity = property;
            } else {
                layoutParams.gravity = Gravity.NO_GRAVITY;
            }
            view.setLayoutParams(layoutParams);
        }
    }

    private void loadImageOrGif(ImageView iv) {
        try {
            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            executor.scheduleAtFixedRate(() -> {
                File Android = new File(Environment.getExternalStorageDirectory() + "/Android");

                if (Android.isDirectory()) {
                    try {
                        ImageDecoder.Source source = ImageDecoder.createSource(new File(Environment.getExternalStorageDirectory() + "/.iconify_files/header_image.png"));

                        Drawable drawable = ImageDecoder.decodeDrawable(source);
                        iv.setImageDrawable(drawable);
                        iv.setClipToOutline(true);
                        if (!zoomToFit) {
                            iv.setScaleType(ImageView.ScaleType.FIT_XY);
                        } else {
                            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            iv.setAdjustViewBounds(false);
                            iv.setCropToPadding(false);
                            iv.setMinimumWidth(ViewGroup.LayoutParams.MATCH_PARENT);
                            addOrRemoveProperty(iv, RelativeLayout.CENTER_IN_PARENT, true);
                        }

                        if (drawable instanceof AnimatedImageDrawable) {
                            ((AnimatedImageDrawable) drawable).start();
                        }
                    } catch (Throwable ignored) {
                    }

                    executor.shutdown();
                    executor.shutdownNow();
                }
            }, 0, 5, TimeUnit.SECONDS);

        } catch (Throwable ignored) {
        }
    }
}