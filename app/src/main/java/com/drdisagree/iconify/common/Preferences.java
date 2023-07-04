package com.drdisagree.iconify.common;

public class Preferences {

    // Xposed mods
    public static final String QS_TRANSPARENCY_SWITCH = "xposed_qstransparency";
    public static final String NOTIF_TRANSPARENCY_SWITCH = "xposed_notiftransparency";
    public static final String QSALPHA_LEVEL = "xposed_qsalpha";
    public static final String STATUSBAR_CLOCKBG_SWITCH = "xposed_sbclockbg";
    public static final String STATUSBAR_CLOCK_COLOR_OPTION = "xposed_sbclockcolor";
    public static final String STATUSBAR_CLOCK_COLOR_CODE = "xposed_sbclockcolorcode";
    public static final String CHIP_STATUSBAR_CLOCKBG_STYLE = "xposed_chipstatusbarclockbgstyle";
    public static final String QSPANEL_STATUSICONSBG_SWITCH = "xposed_qsstatusiconsbg";
    public static final String CHIP_QSSTATUSICONS_STYLE = "xposed_chipqsstatusiconsstyle";
    public static final String VERTICAL_QSTILE_SWITCH = "xposed_verticalqstile";
    public static final String HIDE_QSLABEL_SWITCH = "xposed_hideqslabel";
    public static final String HEADER_IMAGE_SWITCH = "xposed_headerimage";
    public static final String HEADER_IMAGE_LANDSCAPE_SWITCH = "xposed_headerimagelandscape";
    public static final String HEADER_IMAGE_HEIGHT = "xposed_headerimageheight";
    public static final String HEADER_IMAGE_ALPHA = "xposed_headerimagealpha";
    public static final String HEADER_IMAGE_ZOOMTOFIT = "xposed_headerimagezoomtofit";
    public static final String HIDE_STATUS_ICONS_SWITCH = "xposed_hidestatusicons";
    public static final String HEADER_CLOCK_SWITCH = "xposed_headerclock";
    public static final String HEADER_CLOCK_SIDEMARGIN = "xposed_headerclocksidemargin";
    public static final String HEADER_CLOCK_TOPMARGIN = "xposed_headerclocktopmargin";
    public static final String HEADER_CLOCK_CENTERED = "xposed_headerclockcentered";
    public static final String HEADER_CLOCK_LANDSCAPE_SWITCH = "xposed_headerclocklandscape";
    public static final String HEADER_CLOCK_TEXT_WHITE = "xposed_headerclocktextwhite";
    public static final String QSPANEL_HIDE_CARRIER = "xposed_qspanelhidecarrier";
    public static final String HEADER_CLOCK_STYLE = "xposed_headerclockstyle";
    public static final String HEADER_CLOCK_FONT_TEXT_SCALING = "xposed_headerclocktextscaling";
    public static final String LSCLOCK_SWITCH = "xposed_lockscreenclock";
    public static final String LSCLOCK_AUTOHIDE = "xposed_lockscreenclockautohide";
    public static final String LSCLOCK_STYLE = "xposed_lockscreenclockstyle";
    public static final String LSCLOCK_TOPMARGIN = "xposed_lockscreenclocktopmargin";
    public static final String LSCLOCK_BOTTOMMARGIN = "xposed_lockscreenclockbottommargin";
    public static final String LSCLOCK_COLOR_SWITCH = "xposed_lockscreenclockcolor";
    public static final String LSCLOCK_COLOR_CODE = "xposed_lockscreenclockcolorcode";
    public static final String LSCLOCK_FONT_SWITCH = "xposed_lockscreenclockfont";
    public static final String LSCLOCK_FONT_LINEHEIGHT = "xposed_lockscreenclockfontlineheight";
    public static final String LSCLOCK_FONT_TEXT_SCALING = "xposed_lockscreenclocktextscaling";
    public static final String LSCLOCK_TEXT_WHITE = "xposed_lockscreenclocktextwhite";
    public static final String FIXED_STATUS_ICONS_SWITCH = "xposed_fixedstatusicons";
    public static final String FIXED_STATUS_ICONS_SIDEMARGIN = "xposed_fixedstatusiconssidemargin";
    public static final String FIXED_STATUS_ICONS_TOPMARGIN = "xposed_fixedstatusiconstopmargin";
    public static final String HIDE_LOCKSCREEN_STATUSBAR = "xposed_hidelockscreenstatusbar";
    public static final String HIDE_LOCKSCREEN_CARRIER = "xposed_hidelockscreencarrier";
    public static final String LIGHT_QSPANEL = "xposed_lightqspanel";
    public static final String DUALTONE_QSPANEL = "xposed_dualtoneqspanel";
    public static final String BLACK_QSPANEL = "xposed_blackqspanel";
    public static final String FLUID_QSPANEL = "xposed_fluidqspanel";
    public static final String FLUID_NOTIF_TRANSPARENCY = "xposed_fluidnotiftransparency";
    public static final String CUSTOM_BATTERY_STYLE = "xposed_custombatterystyle";
    public static final String CUSTOM_BATTERY_WIDTH = "xposed_custombatterywidth";
    public static final String CUSTOM_BATTERY_HEIGHT = "xposed_custombatteryheight";
    public static final String CUSTOM_BATTERY_MARGIN = "xposed_custombatterymargin";
    public static final String HEADER_QQS_TOPMARGIN = "qqspanelTopMargin";

    // Battery styles
    public static final int BATTERY_STYLE_DEFAULT = 0;
    public static final int BATTERY_STYLE_DEFAULT_RLANDSCAPE = 1;
    public static final int BATTERY_STYLE_DEFAULT_LANDSCAPE = 2;
    public static final int BATTERY_STYLE_CUSTOM_RLANDSCAPE = 3;
    public static final int BATTERY_STYLE_CUSTOM_LANDSCAPE = 4;
    public static final int BATTERY_STYLE_PORTRAIT_CAPSULE = 5;
    public static final int BATTERY_STYLE_PORTRAIT_LORN = 6;
    public static final int BATTERY_STYLE_PORTRAIT_MX = 7;
    public static final int BATTERY_STYLE_PORTRAIT_AIROO = 8;
    public static final int BATTERY_STYLE_RLANDSCAPE_STYLE_A = 9;
    public static final int BATTERY_STYLE_LANDSCAPE_STYLE_A = 10;
    public static final int BATTERY_STYLE_RLANDSCAPE_STYLE_B = 11;
    public static final int BATTERY_STYLE_LANDSCAPE_STYLE_B = 12;
    public static final int BATTERY_STYLE_LANDSCAPE_IOS_15 = 13;
    public static final int BATTERY_STYLE_LANDSCAPE_IOS_16 = 14;
    public static final int BATTERY_STYLE_PORTRAIT_ORIGAMI = 15;
    public static final int BATTERY_STYLE_LANDSCAPE_SMILEY = 16;
    public static final int BATTERY_STYLE_LANDSCAPE_MIUI_PILL = 17;
    public static final int BATTERY_STYLE_LANDSCAPE_COLOROS = 18;
    public static final int BATTERY_STYLE_RLANDSCAPE_COLOROS = 19;

    // Preference keys
    public static final String STR_NULL = "null";
    public static final String UPDATE_SCHEDULE = "iconify_update_schedule";
    public static final String UPDATE_CHECK_TIME = "iconify_update_check_time";
    public static final String LAST_UPDATE_CHECK_TIME = "iconify_last_update_check_time";
    public static final String FIRST_INSTALL = "firstInstall";
    public static final String UPDATE_DETECTED = "updateDetected";
    public static final String ON_HOME_PAGE = "onHomePage";
    public static final String COLORED_BATTERY_SWITCH = "isColoredBatteryEnabled";
    public static final String COLOR_ACCENT_PRIMARY = "colorAccentPrimary";
    public static final String COLOR_ACCENT_SECONDARY = "colorAccentSecondary";
    public static final String CUSTOM_PRIMARY_COLOR_SWITCH = "customPrimaryColor";
    public static final String CUSTOM_SECONDARY_COLOR_SWITCH = "customSecondaryColor";
    public static final String QS_ROW_COLUMN_SWITCH = "fabricatedqsRowColumn";
    public static final String MONET_ENGINE_SWITCH = "customMonet";
    public static final String QSPANEL_BLUR_SWITCH = "qsBlurSwitch";
    public static final String UI_CORNER_RADIUS = "cornerRadius";
    public static final String MONET_STYLE = "customMonetStyle";
    public static final String MONET_ACCENT_SATURATION = "monetAccentSaturation";
    public static final String MONET_BACKGROUND_SATURATION = "monetBackgroundSaturation";
    public static final String MONET_BACKGROUND_LIGHTNESS = "monetBackgroundLightness";
    public static final String MONET_ACCURATE_SHADES = "monetAccurateShades";
    public static final String PORT_QSTILE_EXPANDED_HEIGHT = "portraitQsTileExpandedHeight";
    public static final String PORT_QSTILE_NONEXPANDED_HEIGHT = "portraitQsTileNonExpandedHeight";
    public static final String LAND_QSTILE_EXPANDED_HEIGHT = "landscapeQsTileExpandedHeight";
    public static final String LAND_QSTILE_NONEXPANDED_HEIGHT = "landscapeQsTileNonExpandedHeight";
    public static final String SELECTED_SETTINGS_ICONS_COLOR = "selectedSettignsIconsColor";
    public static final String SELECTED_SETTINGS_ICONS_BG = "selectedSettignsIconsBg";
    public static final String SELECTED_SETTINGS_ICONS_SHAPE = "selectedSettignsIconsShape";
    public static final String SELECTED_SETTINGS_ICONS_SIZE = "selectedSettignsIconsSize";
    public static final String SELECTED_SETTINGS_ICONS_SET = "selectedSettignsIconsSet";
    public static final String SELECTED_TOAST_FRAME = "selectedToastFrame";
    public static final String SELECTED_ICON_SHAPE = "selectedIconShape";
    public static final String RESTART_SYSUI_AFTER_BOOT = "restartSysuiAfterBoot";
    public static final String VOLUME_PANEL_BACKGROUND_WIDTH = "volumePanelBackgroundWidth";
    public static final String SELECTED_PROGRESSBAR = "selectedProgressbar";
    public static final String SELECTED_SWITCH = "selectedSwitch";
    public static final String COLORED_BATTERY_CHECK = "isColoredBatteryEnabledByUser";

    // Settings
    public static final String APP_LANGUAGE = "IconifyAppLanguage";
    public static final String APP_ICON = "IconifyAppIcon";
    public static final String APP_THEME = "IconifyAppTheme";
    public static final String USE_LIGHT_ACCENT = "useLightAccent";
    public static final String SHOW_XPOSED_WARN = "showXposedMenuWarn";
    public static final String FORCE_APPLY_XPOSED_CHOICE = "optionForceApplyXposed";

    // Others
    public static final String BOOT_ID = "boot_id";
    public static final String VER_CODE = "versionCode";
    public static final String EASTER_EGG = "iconify_easter_egg";
}
