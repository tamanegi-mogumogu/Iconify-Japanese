package com.drdisagree.iconify.ui.fragments;

import static com.drdisagree.iconify.common.Const.SWITCH_ANIMATION_DELAY;
import static com.drdisagree.iconify.common.Preferences.AGGRESSIVE_QSPANEL_BLUR_SWITCH;
import static com.drdisagree.iconify.common.Preferences.BLUR_RADIUS_VALUE;
import static com.drdisagree.iconify.common.Preferences.NOTIF_TRANSPARENCY_SWITCH;
import static com.drdisagree.iconify.common.Preferences.QSALPHA_LEVEL;
import static com.drdisagree.iconify.common.Preferences.QSPANEL_BLUR_SWITCH;
import static com.drdisagree.iconify.common.Preferences.QS_TRANSPARENCY_SWITCH;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;

import com.drdisagree.iconify.R;
import com.drdisagree.iconify.config.RPrefs;
import com.drdisagree.iconify.databinding.FragmentXposedTransparencyBlurBinding;
import com.drdisagree.iconify.ui.base.BaseFragment;
import com.drdisagree.iconify.ui.utils.ViewHelper;
import com.drdisagree.iconify.utils.SystemUtil;
import com.google.android.material.slider.Slider;

public class XposedTransparencyBlur extends BaseFragment {

    private FragmentXposedTransparencyBlurBinding binding;

    @SuppressLint("SetTextI18n")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentXposedTransparencyBlurBinding.inflate(inflater, container, false);
        View view = binding.getRoot();

        // Header
        ViewHelper.setHeader(requireContext(), getParentFragmentManager(), binding.header.toolbar, R.string.activity_title_transparency_blur);

        // Qs Panel & Notification Shade Transparency
        binding.enableQsTransparency.setChecked(RPrefs.getBoolean(QS_TRANSPARENCY_SWITCH, false));
        binding.enableQsTransparency.setOnCheckedChangeListener(qsTransparencyListener);

        binding.enableNotifTransparency.setChecked(RPrefs.getBoolean(NOTIF_TRANSPARENCY_SWITCH, false));
        binding.enableNotifTransparency.setOnCheckedChangeListener(notifTransparencyListener);

        binding.qsTransparencyContainer.setOnClickListener(v -> binding.enableQsTransparency.toggle());
        binding.notifTransparencyContainer.setOnClickListener(v -> binding.enableNotifTransparency.toggle());

        // Tansparency Alpha
        final int[] transparency = {RPrefs.getInt(QSALPHA_LEVEL, 60)};
        binding.transparencyOutput.setText(getResources().getString(R.string.opt_selected) + ' ' + transparency[0] + "%");
        binding.transparencySeekbar.setValue(transparency[0]);
        binding.transparencySeekbar.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                transparency[0] = (int) slider.getValue();
                binding.transparencyOutput.setText(getResources().getString(R.string.opt_selected) + ' ' + transparency[0] + "%");
                RPrefs.putInt(QSALPHA_LEVEL, transparency[0]);
            }
        });

        // Qs Panel Blur Enabler
        RPrefs.putBoolean(QSPANEL_BLUR_SWITCH, SystemUtil.isBlurEnabled(false));
        binding.enableBlur.setChecked(RPrefs.getBoolean(QSPANEL_BLUR_SWITCH, false));
        binding.enableBlur.setOnCheckedChangeListener((buttonView, isChecked) -> {
            RPrefs.putBoolean(QSPANEL_BLUR_SWITCH, isChecked);
            if (isChecked) {
                SystemUtil.enableBlur(false);
            } else {
                binding.enableAggressiveBlur.setChecked(false);
                SystemUtil.disableBlur(false);
            }
            binding.aggressiveBlurContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
        binding.blurContainer.setOnClickListener(v -> binding.enableBlur.toggle());

        // Aggressive Qs Panel Blur Enabler
        RPrefs.putBoolean(AGGRESSIVE_QSPANEL_BLUR_SWITCH, SystemUtil.isBlurEnabled(true));
        binding.enableAggressiveBlur.setChecked(RPrefs.getBoolean(AGGRESSIVE_QSPANEL_BLUR_SWITCH, false));
        binding.aggressiveBlurContainer.setVisibility(binding.enableBlur.isChecked() ? View.VISIBLE : View.GONE);
        binding.enableAggressiveBlur.setOnCheckedChangeListener((buttonView, isChecked) -> {
            RPrefs.putBoolean(AGGRESSIVE_QSPANEL_BLUR_SWITCH, isChecked);
            if (isChecked) {
                SystemUtil.enableBlur(true);
            } else {
                SystemUtil.disableBlur(true);
            }
        });
        binding.aggressiveBlurContainer.setOnClickListener(v -> binding.enableAggressiveBlur.toggle());

        final int[] blur_radius = {RPrefs.getInt(BLUR_RADIUS_VALUE, 23)};
        binding.blurOutput.setText(getResources().getString(R.string.opt_selected) + ' ' + blur_radius[0] + "px");
        binding.blurSeekbar.setValue(blur_radius[0]);
        binding.blurSeekbar.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                blur_radius[0] = (int) slider.getValue();
                binding.blurOutput.setText(getResources().getString(R.string.opt_selected) + ' ' + blur_radius[0] + "px");
                RPrefs.putInt(BLUR_RADIUS_VALUE, blur_radius[0]);
                new Handler(Looper.getMainLooper()).postDelayed(SystemUtil::handleSystemUIRestart, SWITCH_ANIMATION_DELAY);
            }
        });

        return view;
    }

    CompoundButton.OnCheckedChangeListener qsTransparencyListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            RPrefs.putBoolean(QS_TRANSPARENCY_SWITCH, isChecked);

            if (isChecked) {
                RPrefs.putBoolean(NOTIF_TRANSPARENCY_SWITCH, false);
                binding.enableNotifTransparency.setOnCheckedChangeListener(null);
                binding.enableNotifTransparency.setChecked(false);
                binding.enableNotifTransparency.setOnCheckedChangeListener(notifTransparencyListener);
            }

            new Handler(Looper.getMainLooper()).postDelayed(SystemUtil::handleSystemUIRestart, SWITCH_ANIMATION_DELAY);
        }
    };

    CompoundButton.OnCheckedChangeListener notifTransparencyListener = (buttonView, isChecked) -> {
        RPrefs.putBoolean(NOTIF_TRANSPARENCY_SWITCH, isChecked);

        if (isChecked) {
            RPrefs.putBoolean(QS_TRANSPARENCY_SWITCH, false);
            binding.enableQsTransparency.setOnCheckedChangeListener(null);
            binding.enableQsTransparency.setChecked(false);
            binding.enableQsTransparency.setOnCheckedChangeListener(qsTransparencyListener);
        }

        new Handler(Looper.getMainLooper()).postDelayed(SystemUtil::handleSystemUIRestart, SWITCH_ANIMATION_DELAY);
    };
}