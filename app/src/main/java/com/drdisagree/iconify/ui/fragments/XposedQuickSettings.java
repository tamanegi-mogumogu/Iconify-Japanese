package com.drdisagree.iconify.ui.fragments;

import static com.drdisagree.iconify.common.Preferences.HIDE_QSLABEL_SWITCH;
import static com.drdisagree.iconify.common.Preferences.QQS_TOPMARGIN;
import static com.drdisagree.iconify.common.Preferences.QS_TOPMARGIN;
import static com.drdisagree.iconify.common.Preferences.VERTICAL_QSTILE_SWITCH;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.drdisagree.iconify.R;
import com.drdisagree.iconify.config.RPrefs;
import com.drdisagree.iconify.databinding.FragmentXposedQuickSettingsBinding;
import com.drdisagree.iconify.ui.base.BaseFragment;
import com.drdisagree.iconify.ui.utils.ViewHelper;
import com.google.android.material.slider.Slider;

public class XposedQuickSettings extends BaseFragment {

    private FragmentXposedQuickSettingsBinding binding;

    @SuppressLint("SetTextI18n")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentXposedQuickSettingsBinding.inflate(inflater, container, false);
        View view = binding.getRoot();

        // Header
        ViewHelper.setHeader(requireContext(), getParentFragmentManager(), binding.header.toolbar, R.string.activity_title_quick_settings);

        // Vertical QS Tile
        binding.verticalTile.setSwitchChecked(RPrefs.getBoolean(VERTICAL_QSTILE_SWITCH, false));
        binding.verticalTile.setSwitchChangeListener((buttonView, isChecked) -> {
            RPrefs.putBoolean(VERTICAL_QSTILE_SWITCH, isChecked);
            binding.hideTileLabel.setEnabled(isChecked);
        });

        // Hide label for vertical tiles
        binding.hideTileLabel.setEnabled(binding.verticalTile.isSwitchChecked());
        binding.hideTileLabel.setSwitchChecked(RPrefs.getBoolean(HIDE_QSLABEL_SWITCH, false));
        binding.hideTileLabel.setSwitchChangeListener((buttonView, isChecked) -> {
            RPrefs.putBoolean(HIDE_QSLABEL_SWITCH, isChecked);
        });

        // QQS panel top margin slider
        binding.qqsTopMargin.setSliderValue(RPrefs.getInt(QQS_TOPMARGIN, 100));
        binding.qqsTopMargin.setOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                RPrefs.putInt(QQS_TOPMARGIN, (int) slider.getValue());
            }
        });
        binding.qqsTopMargin.setResetClickListener(v -> {
            RPrefs.clearPref(QQS_TOPMARGIN);
            return true;
        });

        // QS panel top margin slider
        binding.qsTopMargin.setSliderValue(RPrefs.getInt(QS_TOPMARGIN, 100));
        binding.qsTopMargin.setOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                RPrefs.putInt(QS_TOPMARGIN, (int) slider.getValue());
            }
        });
        binding.qsTopMargin.setResetClickListener(v -> {
            RPrefs.clearPref(QS_TOPMARGIN);
            return true;
        });

        return view;
    }
}