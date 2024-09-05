package com.drdisagree.iconify.xposed.modules

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.graphics.drawable.TransitionDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaController.PlaybackInfo
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.drdisagree.iconify.BuildConfig
import com.drdisagree.iconify.common.Const.FRAMEWORK_PACKAGE
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.common.Preferences.ICONIFY_QS_HEADER_CONTAINER_SHADE_TAG
import com.drdisagree.iconify.common.Preferences.OP_QS_HEADER_BLUR_LEVEL
import com.drdisagree.iconify.common.Preferences.OP_QS_HEADER_EXPANSION_Y
import com.drdisagree.iconify.common.Preferences.OP_QS_HEADER_FADE_LEVEL
import com.drdisagree.iconify.common.Preferences.OP_QS_HEADER_SWITCH
import com.drdisagree.iconify.common.Preferences.OP_QS_HEADER_TOP_MARGIN
import com.drdisagree.iconify.common.Preferences.OP_QS_HEADER_VIBRATE
import com.drdisagree.iconify.utils.color.monet.quantize.QuantizerCelebi
import com.drdisagree.iconify.utils.color.monet.score.Score
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.utils.Helpers.findClassInArray
import com.drdisagree.iconify.xposed.modules.utils.Helpers.isMethodAvailable
import com.drdisagree.iconify.xposed.modules.utils.SettingsLibUtils.Companion.getColorAttr
import com.drdisagree.iconify.xposed.modules.utils.TouchAnimator
import com.drdisagree.iconify.xposed.modules.utils.VibrationUtils
import com.drdisagree.iconify.xposed.modules.utils.ViewHelper.applyBlur
import com.drdisagree.iconify.xposed.modules.utils.ViewHelper.toPx
import com.drdisagree.iconify.xposed.modules.views.QsOpHeaderView
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import com.drdisagree.iconify.xposed.utils.XPrefs.XprefsIsInitialized
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge.hookAllConstructors
import de.robv.android.xposed.XposedBridge.hookAllMethods
import de.robv.android.xposed.XposedBridge.log
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.callStaticMethod
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.findClassIfExists
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.properties.Delegates

@Suppress("DiscouragedApi")
class OpQsHeader(context: Context?) : ModPack(context!!) {

    private var showOpQsHeaderView = false
    private var vibrateOnClick = false
    private var mediaBlurLevel = 10f
    private var mediaFadeLevel = 0
    private var topMarginValue = 0
    private var expansionAmount = 0

    private var mQsHeaderContainer: LinearLayout = LinearLayout(mContext)
    private var mQsHeaderContainerShade: LinearLayout = LinearLayout(mContext).apply {
        tag = ICONIFY_QS_HEADER_CONTAINER_SHADE_TAG
    }
    private var mQsPanelView: ViewGroup? = null
    private var mQuickStatusBarHeader: FrameLayout? = null
    private var mQQSContainerAnimator: TouchAnimator? = null
    private var mQsOpHeaderView: QsOpHeaderView? = null
    private lateinit var mHeaderQsPanel: LinearLayout

    private var colorActive: Int? = null
    private var colorInactive: Int? = null
    private var colorLabelActive: Int? = null
    private var colorLabelInactive: Int? = null
    private var colorAccent by Delegates.notNull<Int>()
    private var colorPrimary by Delegates.notNull<Int>()

    private var mMediaTitle: String? = null
    private var mMediaArtist: String? = null
    private var mMediaArtwork: Bitmap? = null
    private var mPreviousMediaArtwork: Bitmap? = null
    private var mPreviousMediaProcessedArtwork: Bitmap? = null
    private var mMediaIsPlaying = false

    private lateinit var appContext: Context
    private var mHandler: Handler = Handler(Looper.getMainLooper())
    private val artworkExtractorScope = CoroutineScope(Dispatchers.Main + Job())
    private var mMediaUpdater = CoroutineScope(Dispatchers.Main)
    private var mMediaUpdaterJob: Job? = null
    private var mMediaController: MediaController? = null
    private var mMediaMetadata: MediaMetadata? = null
    private var mActivityStarter: Any? = null
    private var mMediaOutputDialogFactory: Any? = null
    private var mNotificationMediaManager: Any? = null
    private var mBluetoothController: Any? = null
    private var mBluetoothTileDialogViewModel: Any? = null
    private var mInternetDialogManager: Any? = null
    private var mInternetDialogFactory: Any? = null
    private var mAccessPointController: Any? = null
    private var qsTileViewImplInstance: Any? = null
    private lateinit var mConnectivityManager: ConnectivityManager
    private lateinit var mTelephonyManager: TelephonyManager
    private lateinit var mWifiManager: WifiManager
    private lateinit var mBluetoothManager: BluetoothManager

    private var qsTileCornerRadius by Delegates.notNull<Float>()
    private lateinit var opMediaBackgroundDrawable: GradientDrawable
    private lateinit var mediaSessionLegacyHelperClass: Class<*>

    private var deferredInternetActiveColorAction: (() -> Unit)? = null
    private var deferredInternetInactiveColorAction: (() -> Unit)? = null
    private var deferredBluetoothActiveColorAction: (() -> Unit)? = null
    private var deferredBluetoothInactiveColorAction: (() -> Unit)? = null
    private var deferredMediaPlayerInactiveColorAction: (() -> Unit)? = null

    private var lastUpdateTime = 0L
    private var cooldownTime = 50 // milliseconds

    override fun updatePrefs(vararg key: String) {
        if (!XprefsIsInitialized) return

        Xprefs.apply {
            showOpQsHeaderView = getBoolean(OP_QS_HEADER_SWITCH, false)
            vibrateOnClick = getBoolean(OP_QS_HEADER_VIBRATE, false)
            mediaBlurLevel = getSliderInt(OP_QS_HEADER_BLUR_LEVEL, 10).toFloat()
            mediaFadeLevel = getSliderInt(OP_QS_HEADER_FADE_LEVEL, 0)
            topMarginValue = getSliderInt(OP_QS_HEADER_TOP_MARGIN, 0)
            expansionAmount = getSliderInt(OP_QS_HEADER_EXPANSION_Y, 0)
        }

        if (key.isNotEmpty() &&
            (key[0] == OP_QS_HEADER_VIBRATE ||
                    key[0] == OP_QS_HEADER_BLUR_LEVEL ||
                    key[0] == OP_QS_HEADER_FADE_LEVEL)
        ) {
            updateMediaPlayer(force = true)
        }
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        val qsPanelClass = findClass(
            "$SYSTEMUI_PACKAGE.qs.QSPanel",
            loadPackageParam.classLoader
        )
        val qsImplClass = findClassInArray(
            loadPackageParam.classLoader,
            "$SYSTEMUI_PACKAGE.qs.QSImpl",
            "$SYSTEMUI_PACKAGE.qs.QSFragment"
        )
        val qsContainerImplClass = findClass(
            "$SYSTEMUI_PACKAGE.qs.QSContainerImpl",
            loadPackageParam.classLoader
        )
        val qsTileViewImplClass = findClass(
            "$SYSTEMUI_PACKAGE.qs.tileimpl.QSTileViewImpl",
            loadPackageParam.classLoader
        )
        val qsPanelControllerBase = findClassIfExists(
            "$SYSTEMUI_PACKAGE.qs.QSPanelControllerBase",
            loadPackageParam.classLoader
        )
        val qsPanelControllerClass = findClass(
            "$SYSTEMUI_PACKAGE.qs.QSPanelController",
            loadPackageParam.classLoader
        )
        val qsSecurityFooterUtilsClass = findClassIfExists(
            "$SYSTEMUI_PACKAGE.qs.QSSecurityFooterUtils",
            loadPackageParam.classLoader
        )
        val quickStatusBarHeaderClass = findClass(
            "$SYSTEMUI_PACKAGE.qs.QuickStatusBarHeader",
            loadPackageParam.classLoader
        )
        val scrimControllerClass = findClass(
            "$SYSTEMUI_PACKAGE.statusbar.phone.ScrimController",
            loadPackageParam.classLoader
        )
        val dependencyClass = findClass(
            "$SYSTEMUI_PACKAGE.Dependency",
            loadPackageParam.classLoader
        )
        val activityStarterClass = findClass(
            "$SYSTEMUI_PACKAGE.plugins.ActivityStarter",
            loadPackageParam.classLoader
        )
        val bluetoothControllerImplClass = findClass(
            "$SYSTEMUI_PACKAGE.statusbar.policy.BluetoothControllerImpl",
            loadPackageParam.classLoader
        )
        val notificationMediaManagerClass = findClass(
            "$SYSTEMUI_PACKAGE.statusbar.NotificationMediaManager",
            loadPackageParam.classLoader
        )
        val mediaControlPanelClass = findClassInArray(
            loadPackageParam.classLoader,
            "$SYSTEMUI_PACKAGE.media.controls.ui.controller.MediaControlPanel",
            "$SYSTEMUI_PACKAGE.media.controls.ui.MediaControlPanel"
        )
        val networkControllerImplClass = findClass(
            "$SYSTEMUI_PACKAGE.statusbar.connectivity.NetworkControllerImpl",
            loadPackageParam.classLoader
        )
        val bluetoothTileClass = findClass(
            "$SYSTEMUI_PACKAGE.qs.tiles.BluetoothTile",
            loadPackageParam.classLoader
        )
        mediaSessionLegacyHelperClass = findClass(
            "$FRAMEWORK_PACKAGE.media.session.MediaSessionLegacyHelper",
            loadPackageParam.classLoader
        )
        launchableImageView = findClassIfExists(
            "$SYSTEMUI_PACKAGE.animation.view.LaunchableImageView",
            loadPackageParam.classLoader
        )
        launchableLinearLayout = findClassIfExists(
            "$SYSTEMUI_PACKAGE.animation.view.LaunchableLinearLayout",
            loadPackageParam.classLoader
        )

        initResources()

        if (qsSecurityFooterUtilsClass == null) {
            hookAllConstructors(quickStatusBarHeaderClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    mActivityStarter = callStaticMethod(
                        dependencyClass,
                        "get",
                        activityStarterClass
                    )
                }
            })
        } else {
            hookAllConstructors(qsSecurityFooterUtilsClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    mActivityStarter = getObjectField(
                        param.thisObject,
                        "mActivityStarter"
                    )
                }
            })
        }

        hookAllConstructors(bluetoothControllerImplClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                mBluetoothController = param.thisObject
            }
        })

        hookAllConstructors(notificationMediaManagerClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                mNotificationMediaManager = param.thisObject
            }
        })

        hookAllConstructors(mediaControlPanelClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                mMediaOutputDialogFactory =
                    getObjectField(param.thisObject, "mMediaOutputDialogFactory")
            }
        })

        hookAllConstructors(networkControllerImplClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                mAccessPointController = getObjectField(param.thisObject, "mAccessPoints")
                try {
                    mInternetDialogManager =
                        getObjectField(param.thisObject, "mInternetDialogManager")
                } catch (ignored: Throwable) {
                }
                try {
                    mInternetDialogFactory =
                        getObjectField(param.thisObject, "mInternetDialogFactory")
                } catch (ignored: Throwable) {
                }
            }
        })

        hookAllConstructors(bluetoothTileClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                mBluetoothTileDialogViewModel = getObjectField(param.thisObject, "mDialogViewModel")
            }
        })

        hookAllMethods(qsTileViewImplClass, "init", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                qsTileViewImplInstance = param.thisObject
            }
        })

        hookAllConstructors(qsPanelControllerClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                onColorsInitialized()
            }
        })

        hookAllMethods(scrimControllerClass, "updateThemeColors", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                onColorsInitialized()
            }
        })

        hookAllMethods(quickStatusBarHeaderClass, "onFinishInflate", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!showOpQsHeaderView) return

                mQuickStatusBarHeader = param.thisObject as FrameLayout

                mHeaderQsPanel = (param.thisObject as FrameLayout).findViewById(
                    mContext.resources.getIdentifier(
                        "quick_qs_panel",
                        "id",
                        SYSTEMUI_PACKAGE
                    )
                )

                mQsHeaderContainer.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    orientation = LinearLayout.HORIZONTAL
                }

                mQsHeaderContainerShade.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    orientation = LinearLayout.VERTICAL
                }

                (mQsHeaderContainer.parent as? ViewGroup)?.removeView(mQsHeaderContainer)
                mQuickStatusBarHeader!!.addView(mQsHeaderContainer, -1)

                val relativeLayout = RelativeLayout(mContext).apply {
                    layoutParams = RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.TOP
                    }
                    clipChildren = false
                    clipToPadding = false

                    mQsOpHeaderView = QsOpHeaderView(mContext).apply {
                        setOnAttachListener {
                            ControllersProvider.getInstance().apply {
                                registerWifiCallback(mWifiCallback)
                                registerMobileDataCallback(mMobileDataCallback)
                                registerBluetoothCallback(mBluetoothCallback)
                            }
                        }
                        setOnDetachListener {
                            ControllersProvider.getInstance().apply {
                                unRegisterWifiCallback(mWifiCallback)
                                unRegisterMobileDataCallback(mMobileDataCallback)
                                unRegisterBluetoothCallback(mBluetoothCallback)
                            }
                        }
                        setOnClickListeners(
                            onClickListener = mOnClickListener,
                            onLongClickListener = mOnLongClickListener
                        )
                    }
                    mQsHeaderContainer.addView(mQsOpHeaderView)
                    updateOpHeaderView()

                    (mQsHeaderContainer.parent as? ViewGroup)?.removeView(mQsHeaderContainer)
                    addView(mQsHeaderContainer)

                    (mHeaderQsPanel.parent as? ViewGroup)?.removeView(mHeaderQsPanel)
                    addView(mHeaderQsPanel)

                    (mHeaderQsPanel.layoutParams as RelativeLayout.LayoutParams).apply {
                        addRule(RelativeLayout.BELOW, mQsOpHeaderView!!.id)
                    }
                }

                mQuickStatusBarHeader!!.addView(
                    relativeLayout,
                    mQuickStatusBarHeader!!.childCount
                )

                buildHeaderViewExpansion()
            }
        })

        hookAllMethods(quickStatusBarHeaderClass, "updateResources", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                mQuickStatusBarHeader = param.thisObject as FrameLayout

                mHeaderQsPanel = (param.thisObject as FrameLayout).findViewById(
                    mContext.resources.getIdentifier(
                        "quick_qs_panel",
                        "id",
                        SYSTEMUI_PACKAGE
                    )
                )

                buildHeaderViewExpansion()

                if (isLandscape) {
                    if (mQsHeaderContainer.parent != mQsHeaderContainerShade) {
                        (mQsHeaderContainer.parent as? ViewGroup)?.removeView(mQsHeaderContainer)
                        mQsHeaderContainerShade.addView(mQsHeaderContainer, -1)
                    }
                    mQsHeaderContainerShade.visibility = View.VISIBLE
                } else {
                    if (mQsHeaderContainer.parent != mQuickStatusBarHeader) {
                        (mQsHeaderContainer.parent as? ViewGroup)?.removeView(mQsHeaderContainer)
                        mQuickStatusBarHeader?.addView(mQsHeaderContainer, -1)
                    }
                    mQsHeaderContainerShade.visibility = View.GONE
                }

                updateOpHeaderView()
            }
        })

        val updateQsTopMargin = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!showOpQsHeaderView) return

                val mQsPanel = try {
                    getObjectField(param.thisObject, "mQSPanel")
                } catch (ignored: Throwable) {
                    (param.thisObject as FrameLayout).findViewById(
                        mContext.resources.getIdentifier(
                            "quick_settings_panel",
                            "id",
                            SYSTEMUI_PACKAGE
                        )
                    )
                } as LinearLayout

                (mQsPanel.layoutParams as MarginLayoutParams).topMargin =
                    if (isLandscape) 0 else mContext.toPx(136 + topMarginValue + expansionAmount)
            }
        }

        hookAllMethods(qsContainerImplClass, "onFinishInflate", updateQsTopMargin)
        hookAllMethods(qsContainerImplClass, "updateResources", updateQsTopMargin)

        hookAllMethods(qsPanelClass, "reAttachMediaHost", object : XC_MethodReplacement() {
            override fun replaceHookedMethod(param: MethodHookParam): Any? {
                if (!showOpQsHeaderView) return param.result
                return null
            }
        })

        val hasSwitchAllContentToParent = qsPanelClass.declaredMethods.any {
            it.name == "switchAllContentToParent"
        }
        val hasSwitchToParentMethod = qsPanelClass.declaredMethods.any { method ->
            method.name == "switchToParent" &&
                    method.parameterTypes.contentEquals(
                        arrayOf(View::class.java, ViewGroup::class.java, Int::class.java)
                    )
        }

        if (hasSwitchAllContentToParent && hasSwitchToParentMethod) {
            hookAllMethods(qsPanelClass, "switchAllContentToParent", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!showOpQsHeaderView) return

                    val parent = param.args[0] as ViewGroup
                    val mMovableContentStartIndex = getObjectField(
                        param.thisObject, "mMovableContentStartIndex"
                    ) as Int
                    val index = if (parent === param.thisObject) mMovableContentStartIndex else 0
                    val targetParentId = mContext.resources.getIdentifier(
                        "quick_settings_panel",
                        "id",
                        SYSTEMUI_PACKAGE
                    )

                    if (parent.id == targetParentId) {
                        val checkExistingView =
                            parent.findViewWithTag<ViewGroup?>(ICONIFY_QS_HEADER_CONTAINER_SHADE_TAG)
                        if (checkExistingView != null) {
                            mQsHeaderContainerShade = checkExistingView as LinearLayout
                            if (parent.indexOfChild(mQsHeaderContainerShade) == index) {
                                return
                            }
                        }

                        callMethod(
                            param.thisObject,
                            "switchToParent",
                            mQsHeaderContainerShade,
                            parent,
                            index
                        )
                    }
                }
            })

            if (showOpQsHeaderView) {
                findAndHookMethod(
                    qsPanelClass,
                    "switchToParent",
                    View::class.java,
                    ViewGroup::class.java,
                    Int::class.java,
                    object : XC_MethodReplacement() {
                        override fun replaceHookedMethod(param: MethodHookParam): Any? {
                            val view = param.args[0] as View
                            val parent = param.args[1] as ViewGroup
                            val tempIndex = param.args[2] as Int
                            val index = if (view.tag == ICONIFY_QS_HEADER_CONTAINER_SHADE_TAG) {
                                tempIndex
                            } else {
                                tempIndex + 1
                            }
                            val tag = callMethod(param.thisObject, "getDumpableTag")

                            callMethod(
                                param.thisObject,
                                "switchToParent",
                                view,
                                parent,
                                index.coerceAtMost(parent.childCount),
                                tag
                            )

                            return null
                        }
                    }
                )
            }
        } else { // Some ROMs don't have this method switchAllContentToParent()
            hookAllMethods(
                qsPanelControllerBase,
                "onInit",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        mQsPanelView = getObjectField(
                            param.thisObject,
                            "mView"
                        ) as ViewGroup
                    }
                }
            )

            findAndHookMethod(
                qsPanelClass,
                "switchToParent",
                View::class.java,
                ViewGroup::class.java,
                Int::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!showOpQsHeaderView ||
                            mQsPanelView == null ||
                            (param.args[1] as? ViewGroup) == null
                        ) return

                        val parent = param.args[1] as ViewGroup
                        val mMovableContentStartIndex = getObjectField(
                            mQsPanelView, "mMovableContentStartIndex"
                        ) as Int
                        val index = if (parent === mQsPanelView) mMovableContentStartIndex else 0
                        val targetParentId = mContext.resources.getIdentifier(
                            "quick_settings_panel",
                            "id",
                            SYSTEMUI_PACKAGE
                        )

                        if (parent.id == targetParentId) {
                            val mQsHeaderContainerShadeParent =
                                mQsHeaderContainerShade.parent as? ViewGroup
                            if (mQsHeaderContainerShadeParent != parent ||
                                mQsHeaderContainerShadeParent.indexOfChild(mQsHeaderContainerShade) != index
                            ) {
                                mQsHeaderContainerShadeParent?.removeView(mQsHeaderContainerShade)
                                parent.addView(mQsHeaderContainerShade, index)
                            }
                        }

                        param.args[2] = ((param.args[2] as Int) + 1).coerceAtMost(parent.childCount)
                    }
                }
            )
        }

        hookAllMethods(qsImplClass, "setQsExpansion", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!showOpQsHeaderView) return

                val onKeyguard = callMethod(
                    param.thisObject,
                    "isKeyguardState"
                ) as Boolean
                val mShowCollapsedOnKeyguard = getObjectField(
                    param.thisObject,
                    "mShowCollapsedOnKeyguard"
                ) as Boolean

                val onKeyguardAndExpanded = onKeyguard && !mShowCollapsedOnKeyguard
                val expansion = param.args[0] as Float

                setExpansion(onKeyguardAndExpanded, expansion);
            }
        })

        hookResources()
    }

    private fun hookResources() {
        hookAllMethods(
            Resources::class.java,
            "getBoolean",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!showOpQsHeaderView) return

                    val resId1 = mContext.resources.getIdentifier(
                        "config_use_split_notification_shade",
                        "bool",
                        SYSTEMUI_PACKAGE
                    )

                    val resId2 = mContext.resources.getIdentifier(
                        "config_skinnyNotifsInLandscape",
                        "bool",
                        SYSTEMUI_PACKAGE
                    )

                    if (param.args[0] == resId1) {
                        param.result = isLandscape
                    } else if (param.args[0] == resId2) {
                        param.result = false
                    }
                }
            }
        )

        hookAllMethods(
            Resources::class.java,
            "getInteger",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!showOpQsHeaderView) return

                    val resId1 = mContext.resources.getIdentifier(
                        "quick_settings_max_rows",
                        "integer",
                        SYSTEMUI_PACKAGE
                    )

                    if (param.args[0] == resId1) {
                        param.result = 3
                    }
                }
            }
        )

        hookAllMethods(
            Resources::class.java,
            "getDimensionPixelSize",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!showOpQsHeaderView) return

                    val res1 = mContext.resources.getIdentifier(
                        "qs_brightness_margin_top",
                        "dimen",
                        SYSTEMUI_PACKAGE
                    )

                    if (res1 != 0 && param.args[0] == res1) {
                        param.result = 0
                    }
                }
            }
        )
    }

    private fun updateOpHeaderView() {
        if (mQsOpHeaderView == null) return

        onColorsInitialized()
        updateMediaController()
        startMediaUpdater()
        updateInternetState()
        updateBluetoothState()
    }

    private fun buildHeaderViewExpansion() {
        if (!showOpQsHeaderView ||
            mQsOpHeaderView == null ||
            !::mHeaderQsPanel.isInitialized
        ) return

        val resources = mContext.resources
        val largeScreenHeaderActive = resources.getBoolean(
            resources.getIdentifier(
                "config_use_large_screen_shade_header",
                "bool",
                SYSTEMUI_PACKAGE
            )
        )
        val derivedTopMargin = if (isLandscape) 0 else topMarginValue

        val params = mQsHeaderContainer.layoutParams as MarginLayoutParams
        val qqsHeaderResId = if (largeScreenHeaderActive) resources.getIdentifier(
            "qqs_layout_margin_top",
            "dimen",
            SYSTEMUI_PACKAGE
        )
        else resources.getIdentifier(
            "large_screen_shade_header_min_height",
            "dimen",
            SYSTEMUI_PACKAGE
        )
        val topMargin = resources.getDimensionPixelSize(qqsHeaderResId)
        params.topMargin = topMargin + mContext.toPx(derivedTopMargin)
        mQsHeaderContainer.layoutParams = params

        (mHeaderQsPanel.layoutParams as MarginLayoutParams).topMargin =
            topMargin + mContext.toPx(136 + derivedTopMargin)

        val qsHeaderHeight = resources.getDimensionPixelOffset(
            resources.getIdentifier(
                "qs_header_height",
                "dimen",
                SYSTEMUI_PACKAGE
            )
        ) - resources.getDimensionPixelOffset(qqsHeaderResId)

        val mQQSExpansionY = if (isLandscape) {
            0
        } else {
            qsHeaderHeight + 16 - topMargin + expansionAmount
        }

        val builderP: TouchAnimator.Builder = TouchAnimator.Builder()
            .addFloat(
                mQsHeaderContainer,
                "translationY",
                0F,
                mContext.toPx(mQQSExpansionY).toFloat()
            )

        mQQSContainerAnimator = builderP.build()
    }

    private fun setExpansion(forceExpanded: Boolean, expansionFraction: Float) {
        val keyguardExpansionFraction = if (forceExpanded) 1f else expansionFraction
        mQQSContainerAnimator?.setPosition(keyguardExpansionFraction)

        mQsHeaderContainer.alpha = if (forceExpanded) expansionFraction else 1f
    }

    private val mWifiCallback: ControllersProvider.OnWifiChanged =
        object : ControllersProvider.OnWifiChanged {
            override fun onWifiChanged(mWifiIndicators: Any?) {
                updateInternetState()
            }
        }

    private val mMobileDataCallback: ControllersProvider.OnMobileDataChanged =
        object : ControllersProvider.OnMobileDataChanged {
            override fun setMobileDataIndicators(mMobileDataIndicators: Any?) {
                updateInternetState()
            }

            override fun setNoSims(show: Boolean, simDetected: Boolean) {
                updateInternetState()
            }

            override fun setIsAirplaneMode(mIconState: Any?) {
                updateInternetState()
            }
        }

    private val mBluetoothCallback: ControllersProvider.OnBluetoothChanged =
        object : ControllersProvider.OnBluetoothChanged {
            override fun onBluetoothChanged(enabled: Boolean) {
                updateBluetoothState(enabled)
            }
        }

    private val mMediaCallback: MediaController.Callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            super.onPlaybackStateChanged(state)
            updateMediaController()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)
            mMediaMetadata = metadata
            updateMediaController()
        }
    }

    private fun startMediaUpdater() {
        mMediaUpdaterJob?.cancel()

        mMediaUpdaterJob = mMediaUpdater.launch {
            while (isActive) {
                updateMediaController()
                delay(1000)
            }
        }
    }

    private fun stopMediaUpdater() {
        mMediaUpdaterJob?.cancel()
    }

    private fun toggleInternetState(v: View) {
        mHandler.post {
            if (mAccessPointController != null) {
                if (isMethodAvailable(
                        mInternetDialogManager,
                        "create",
                        Boolean::class.java,
                        Boolean::class.java,
                        Boolean::class.java,
                        View::class.java
                    )
                ) {
                    callMethod(
                        mInternetDialogManager,
                        "create",
                        true,
                        callMethod(mAccessPointController, "canConfigMobileData"),
                        callMethod(mAccessPointController, "canConfigWifi"),
                        v
                    )
                } else if (isMethodAvailable(
                        mInternetDialogManager,
                        "create",
                        View::class.java,
                        Boolean::class.java,
                        Boolean::class.java
                    )
                ) {
                    callMethod(
                        mInternetDialogManager,
                        "create",
                        v,
                        callMethod(mAccessPointController, "canConfigMobileData"),
                        callMethod(mAccessPointController, "canConfigWifi")
                    )
                } else if (isMethodAvailable(
                        mInternetDialogFactory,
                        "create",
                        Boolean::class.java,
                        Boolean::class.java,
                        View::class.java
                    )
                ) {
                    callMethod(
                        mInternetDialogFactory,
                        "create",
                        callMethod(mAccessPointController, "canConfigMobileData"),
                        callMethod(mAccessPointController, "canConfigWifi"),
                        v
                    )
                } else {
                    log(TAG + "No internet dialog available")
                }
            }
        }

        mHandler.postDelayed({
            updateInternetState()
        }, 250)
    }

    private fun updateInternetState() {
        val isWiFiConnected = isWiFiConnected
        val isMobileDataConnected = isMobileDataConnected

        val internetLabel: CharSequence = mContext.getString(
            mContext.resources.getIdentifier(
                "quick_settings_internet_label",
                "string",
                SYSTEMUI_PACKAGE
            )
        )
        val noInternetIconResId = mContext.resources.getIdentifier(
            "ic_qs_no_internet_available",
            "drawable",
            SYSTEMUI_PACKAGE
        )

        if (isWiFiConnected || isMobileDataConnected) {
            if (isWiFiConnected) {
                val signalLevel = getWiFiSignalStrengthLevel()
                val wifiIconResId = when (signalLevel) {
                    4 -> "ic_wifi_signal_4"
                    3 -> "ic_wifi_signal_3"
                    2 -> "ic_wifi_signal_2"
                    1 -> "ic_wifi_signal_1"
                    else -> "ic_wifi_signal_0"
                }

                mQsOpHeaderView?.setInternetText(getWiFiSSID())
                mQsOpHeaderView?.setInternetIcon(
                    mContext.resources.getIdentifier(
                        wifiIconResId,
                        "drawable",
                        FRAMEWORK_PACKAGE
                    )
                )
            } else {
                val signalLevel = getMobileDataSignalStrengthLevel()
                val maxBars = 4

                val mobileDataIconResId = mContext.resources.getIdentifier(
                    "ic_signal_cellular_${signalLevel}_${maxBars}_bar",
                    "drawable",
                    FRAMEWORK_PACKAGE
                )

                val networkType = getNetworkType()
                if (networkType == null) {
                    mQsOpHeaderView?.setInternetText(getCarrierName())
                } else {
                    mQsOpHeaderView?.setInternetText(
                        String.format(
                            "%s, %s",
                            getCarrierName(),
                            networkType
                        )
                    )
                }
                mQsOpHeaderView?.setInternetIcon(mobileDataIconResId)
            }

            updateInternetActiveColors()
        } else {
            mQsOpHeaderView?.setInternetText(internetLabel)
            mQsOpHeaderView?.setInternetIcon(noInternetIconResId)

            updateInternetInactiveColors()
        }
    }

    private val isWiFiConnected: Boolean
        get() {
            val network: Network? = mConnectivityManager.activeNetwork
            return if (network != null) {
                val capabilities = mConnectivityManager.getNetworkCapabilities(network)
                capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            } else {
                false
            }
        }

    @Suppress("deprecation")
    private fun getWiFiSignalStrengthLevel(): Int {
        val wifiInfo = mWifiManager.connectionInfo ?: return 0
        val level = WifiManager.calculateSignalLevel(wifiInfo.rssi, 5)
        return level.coerceIn(0, 4)
    }

    @Suppress("deprecation")
    private fun getWiFiSSID(): String {
        val result: CharSequence = mContext.getString(
            mContext.resources.getIdentifier(
                "quick_settings_internet_label",
                "string",
                SYSTEMUI_PACKAGE
            )
        )
        val wifiInfo = mWifiManager.connectionInfo
        return if (wifiInfo?.hiddenSSID == true || wifiInfo?.ssid === WifiManager.UNKNOWN_SSID) {
            result.toString()
        } else {
            wifiInfo?.ssid?.replace("\"", "") ?: result.toString()
        }
    }

    private val isMobileDataConnected: Boolean
        get() {
            val network: Network? = mConnectivityManager.activeNetwork
            return if (network != null) {
                val capabilities = mConnectivityManager.getNetworkCapabilities(network)
                capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            } else {
                false
            }
        }

    private fun getMobileDataSignalStrengthLevel(): Int {
        val signalStrength = mTelephonyManager.signalStrength
        return signalStrength?.level?.coerceIn(0, 4) ?: 0
    }

    private fun getCarrierName(): String {
        val internetLabel: CharSequence = mContext.getString(
            mContext.resources.getIdentifier(
                "quick_settings_internet_label",
                "string",
                SYSTEMUI_PACKAGE
            )
        )

        val activeNetwork = mConnectivityManager.activeNetwork
        val networkCapabilities = mConnectivityManager.getNetworkCapabilities(activeNetwork)

        if (networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) {
            return mTelephonyManager.networkOperatorName.ifEmpty { internetLabel.toString() }
        }

        return internetLabel.toString()
    }

    @SuppressLint("MissingPermission")
    @Suppress("deprecation")
    private fun getNetworkType(): String? {
        return when (mTelephonyManager.networkType) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G"

            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"

            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_UMTS -> "3G"

            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_GPRS -> "2G"

            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_1xRTT -> "CDMA"

            TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
            else -> null
        }
    }

    private val isBluetoothEnabled: Boolean
        get() {
            return mBluetoothManager.adapter != null && mBluetoothManager.adapter.isEnabled
        }

    @SuppressLint("MissingPermission")
    private fun getBluetoothConnectedDevice(): String {
        return if (isBluetoothEnabled && mBluetoothController != null) {
            callMethod(mBluetoothController, "getConnectedDeviceName") as String? ?: ""
        } else {
            ""
        }.ifEmpty {
            mContext.resources.getString(
                mContext.resources.getIdentifier(
                    "quick_settings_bluetooth_label",
                    "string",
                    SYSTEMUI_PACKAGE
                )
            )
        }
    }

    private fun toggleBluetoothState(v: View) {
        mHandler.post {
            if (mBluetoothTileDialogViewModel != null) {
                try {
                    callMethod(
                        mBluetoothTileDialogViewModel,
                        "showDialog",
                        mContext,
                        v
                    )
                } catch (ignored: Throwable) {
                    val isAutoOn = Settings.System.getInt(
                        mContext.contentResolver,
                        "qs_bt_auto_on", 0
                    ) == 1

                    callMethod(
                        mBluetoothTileDialogViewModel,
                        "showDialog",
                        mContext,
                        v,
                        isAutoOn
                    )
                }
            } else if (mBluetoothController != null) {
                callMethod(mBluetoothController, "setBluetoothEnabled", !isBluetoothEnabled)
            }
        }

        mHandler.postDelayed({
            updateBluetoothState()
        }, 250)
    }

    private fun updateBluetoothState(enabled: Boolean = isBluetoothEnabled) {
        if (enabled) {
            mQsOpHeaderView?.setBlueToothText(getBluetoothConnectedDevice())

            updateBluetoothActiveColors()
        } else {
            mQsOpHeaderView?.setBlueToothText(
                mContext.resources.getIdentifier(
                    "quick_settings_bluetooth_label",
                    "string",
                    SYSTEMUI_PACKAGE
                )
            )

            updateBluetoothInactiveColors()
        }
    }

    private val isMediaControllerAvailable: Boolean
        get() {
            val mediaController = activeLocalMediaController
            return mediaController != null && !mediaController.packageName.isNullOrEmpty()
        }

    private val activeLocalMediaController: MediaController?
        get() {
            val mediaSessionManager = mContext.getSystemService(MediaSessionManager::class.java)
            var localController: MediaController? = null
            val remoteMediaSessionLists: MutableList<String> = ArrayList()

            for (controller: MediaController in mediaSessionManager.getActiveSessions(null)) {
                val pi = controller.playbackInfo ?: continue
                val playbackState = controller.playbackState ?: continue
                if (playbackState.state != PlaybackState.STATE_PLAYING) continue

                if (pi.playbackType == PlaybackInfo.PLAYBACK_TYPE_REMOTE) {
                    if (localController != null
                        && localController.packageName!!.contentEquals(controller.packageName)
                    ) {
                        localController = null
                    }

                    if (!remoteMediaSessionLists.contains(controller.packageName)) {
                        remoteMediaSessionLists.add(controller.packageName)
                    }

                    continue
                }
                if (pi.playbackType == PlaybackInfo.PLAYBACK_TYPE_LOCAL) {
                    if (localController == null
                        && !remoteMediaSessionLists.contains(controller.packageName)
                    ) {
                        localController = controller
                    }
                }
            }

            return localController
        }

    private fun updateMediaController() {
        val localController = activeLocalMediaController

        if (localController != null && !sameSessions(mMediaController, localController)) {
            if (mMediaController != null) {
                mMediaController!!.unregisterCallback(mMediaCallback)
                mMediaController = null
            }

            mMediaController = localController
            mMediaController!!.registerCallback(mMediaCallback)
        }

        mMediaMetadata = if (isMediaControllerAvailable) mMediaController!!.metadata else null

        updateMediaPlayerView()
    }

    private fun sameSessions(a: MediaController?, b: MediaController): Boolean {
        if (a == b) {
            return true
        }
        if (a == null) {
            return false
        }
        return false
    }

    private enum class MediaAction {
        TOGGLE_PLAYBACK,
        PLAY_PREVIOUS,
        PLAY_NEXT
    }

    private fun performMediaAction(action: MediaAction) {
        when (action) {
            MediaAction.TOGGLE_PLAYBACK -> toggleMediaPlaybackState()
            MediaAction.PLAY_PREVIOUS -> dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            MediaAction.PLAY_NEXT -> dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_NEXT)
        }
        updateMediaController()
    }

    private fun toggleMediaPlaybackState() {
        if (mMediaIsPlaying) {
            stopMediaUpdater()
            dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_PAUSE)
        } else {
            startMediaUpdater()
            dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_PLAY)
        }

        mQsOpHeaderView?.setMediaPlayingIcon(mMediaIsPlaying)
    }

    private fun dispatchMediaKeyWithWakeLockToMediaSession(keycode: Int) {
        val helper = callStaticMethod(
            mediaSessionLegacyHelperClass,
            "getHelper",
            mContext
        ) ?: return

        var event: KeyEvent? = KeyEvent(
            SystemClock.uptimeMillis(),
            SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, keycode, 0
        )
        callMethod(helper, "sendMediaButtonEvent", event, true)
        event = KeyEvent.changeAction(event, KeyEvent.ACTION_UP)
        callMethod(helper, "sendMediaButtonEvent", event, true)
    }

    private val isMediaPlaying: Boolean
        get() {
            return (isMediaControllerAvailable && PlaybackState.STATE_PLAYING == callMethod(
                mNotificationMediaManager,
                "getMediaControllerPlaybackState",
                mMediaController
            ))
        }

    private fun updateMediaPlayerView() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime < cooldownTime) return
        lastUpdateTime = currentTime

        mMediaMetadata?.apply {
            mMediaTitle = getText(MediaMetadata.METADATA_KEY_TITLE)?.toString()
            mMediaArtist = getText(MediaMetadata.METADATA_KEY_ARTIST)?.toString()
            mMediaArtwork = getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: getBitmap(MediaMetadata.METADATA_KEY_ART)
        } ?: run {
            mMediaTitle = null
            mMediaArtist = null
            mMediaArtwork = null
        }

        mMediaIsPlaying = isMediaPlaying

        updateMediaPlayer()
    }

    private fun updateMediaPlayer(force: Boolean = false) {
        if (mQsOpHeaderView == null) return

        artworkExtractorScope.launch {
            val mMediaPlayerBackground = mQsOpHeaderView!!.mediaPlayerBackground
            val requireUpdate = !areBitmapsEqual(mPreviousMediaArtwork, mMediaArtwork) ||
                    mMediaPlayerBackground.drawable == null

            var artworkDrawable: Drawable? = null
            var processedArtwork: Bitmap? = null
            var filteredArtwork: Bitmap? = null
            var dominantColor: Int? = null

            if (requireUpdate || force) {
                if (!::opMediaBackgroundDrawable.isInitialized) {
                    initResources()
                }

                processedArtwork = processArtwork(mMediaArtwork, mMediaPlayerBackground)
                dominantColor = extractDominantColor(processedArtwork)
                filteredArtwork = processedArtwork?.let {
                    applyColorFilterToBitmap(it, dominantColor)
                }
                val newArtworkDrawable = when {
                    filteredArtwork != null -> BitmapDrawable(mContext.resources, filteredArtwork)
                    else -> opMediaBackgroundDrawable
                }
                val transitionDuration = 500

                when {
                    mPreviousMediaArtwork == null && filteredArtwork != null -> {
                        artworkDrawable = TransitionDrawable(
                            arrayOf(
                                opMediaBackgroundDrawable,
                                newArtworkDrawable
                            )
                        ).apply {
                            isCrossFadeEnabled = true
                            startTransition(transitionDuration)
                        }
                    }

                    mPreviousMediaArtwork != null && filteredArtwork != null -> {
                        artworkDrawable = TransitionDrawable(
                            arrayOf(
                                BitmapDrawable(mContext.resources, mPreviousMediaProcessedArtwork),
                                newArtworkDrawable
                            )
                        ).apply {
                            isCrossFadeEnabled = true
                            startTransition(transitionDuration)
                        }
                    }

                    mPreviousMediaArtwork != null && filteredArtwork == null -> {
                        artworkDrawable = TransitionDrawable(
                            arrayOf(
                                BitmapDrawable(mContext.resources, mPreviousMediaProcessedArtwork),
                                newArtworkDrawable
                            )
                        ).apply {
                            isCrossFadeEnabled = true
                            startTransition(transitionDuration)
                        }
                    }

                    else -> {
                        artworkDrawable = opMediaBackgroundDrawable
                    }
                }
            }

            mQsOpHeaderView?.setMediaTitle(
                mMediaTitle ?: appContext.getString(
                    appContext.resources.getIdentifier(
                        "media_player_not_playing",
                        "string",
                        appContext.packageName
                    )
                )
            )
            mQsOpHeaderView?.setMediaArtist(mMediaArtist)

            mQsOpHeaderView?.setMediaPlayingIcon(mMediaIsPlaying)

            withContext(Dispatchers.Main) {
                val appIcon = mNotificationMediaManager?.let {
                    try {
                        callMethod(it, "getMediaIcon") as Icon?
                    } catch (ignored: Throwable) {
                        try {
                            mMediaController?.packageName?.let { packageName ->
                                val drawable =
                                    mContext.packageManager.getApplicationIcon(packageName)
                                Icon.createWithBitmap((drawable as BitmapDrawable).bitmap)
                            }
                        } catch (ignored: Throwable) {
                            null
                        }
                    }
                }
                if (appIcon != null && mMediaTitle != null) {
                    mQsOpHeaderView?.setMediaAppIcon(appIcon)
                } else {
                    mQsOpHeaderView?.resetMediaAppIcon()
                }

                if (requireUpdate || force) {
                    mMediaPlayerBackground.apply {
                        setImageDrawable(artworkDrawable)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        clipToOutline = true
                        applyBlur(if (processedArtwork != null) mediaBlurLevel else 0f)
                    }

                    mPreviousMediaArtwork = mMediaArtwork
                    mPreviousMediaProcessedArtwork = filteredArtwork

                    val onDominantColor = getContrastingTextColor(dominantColor)

                    mQsOpHeaderView?.setMediaAppIconColor(
                        backgroundColor = dominantColor ?: colorAccent,
                        iconColor = onDominantColor ?: colorPrimary
                    )

                    if (processedArtwork == null || onDominantColor == null) {
                        updateMediaPlayerInactiveColors()
                    } else {
                        val derivedOnDominantColor = if (mediaFadeLevel > 20) {
                            colorLabelInactive ?: onDominantColor
                        } else {
                            onDominantColor
                        }

                        mQsOpHeaderView?.setMediaPlayerItemsColor(derivedOnDominantColor)

                        if (mediaFadeLevel != 0) {
                            val fadeFilter = ColorUtils.blendARGB(
                                Color.TRANSPARENT,
                                Color.BLACK,
                                mediaFadeLevel / 100f
                            )
                            mMediaPlayerBackground.setColorFilter(
                                fadeFilter,
                                PorterDuff.Mode.SRC_ATOP
                            )
                        } else {
                            mMediaPlayerBackground.colorFilter = null
                        }
                    }
                }
            }
        }
    }

    private fun onColorsInitialized() {
        initResources()

        if (mQsOpHeaderView == null) return

        deferredInternetActiveColorAction?.invoke()
        deferredInternetInactiveColorAction?.invoke()
        deferredBluetoothActiveColorAction?.invoke()
        deferredBluetoothInactiveColorAction?.invoke()
        deferredMediaPlayerInactiveColorAction?.invoke()

        updateInternetState()
        updateBluetoothState()
        updateMediaPlayer(force = true)
    }

    private fun updateInternetActiveColors() {
        if (colorActive != null && colorLabelActive != null) {
            applyInternetActiveColors()
        } else {
            deferredInternetActiveColorAction = { applyInternetActiveColors() }
        }
    }

    private fun applyInternetActiveColors() {
        mQsOpHeaderView?.setInternetTileColor(
            tileColor = colorActive,
            labelColor = colorLabelActive
        )
        deferredInternetActiveColorAction = null
    }

    private fun updateInternetInactiveColors() {
        if (colorInactive != null && colorLabelInactive != null) {
            applyInternetInactiveColors()
        } else {
            deferredInternetInactiveColorAction = { applyInternetInactiveColors() }
        }
    }

    private fun applyInternetInactiveColors() {
        mQsOpHeaderView?.setInternetTileColor(
            tileColor = colorInactive,
            labelColor = colorLabelInactive
        )
        deferredInternetInactiveColorAction = null
    }

    private fun updateBluetoothActiveColors() {
        if (colorActive != null && colorLabelActive != null) {
            applyBluetoothActiveColors()
        } else {
            deferredBluetoothActiveColorAction = { applyBluetoothActiveColors() }
        }
    }

    private fun applyBluetoothActiveColors() {
        mQsOpHeaderView?.setBluetoothTileColor(
            tileColor = colorActive,
            labelColor = colorLabelActive
        )
        deferredBluetoothActiveColorAction = null
    }

    private fun updateBluetoothInactiveColors() {
        if (colorInactive != null && colorLabelInactive != null) {
            applyBluetoothInactiveColors()
        } else {
            deferredBluetoothInactiveColorAction = { applyBluetoothInactiveColors() }
        }
    }

    private fun applyBluetoothInactiveColors() {
        mQsOpHeaderView?.setBluetoothTileColor(
            tileColor = colorInactive,
            labelColor = colorLabelInactive
        )
        deferredBluetoothInactiveColorAction = null
    }

    private fun updateMediaPlayerInactiveColors() {
        if (colorLabelInactive != null) {
            applyInactiveMediaPlayerColors()
        } else {
            deferredMediaPlayerInactiveColorAction = { applyInactiveMediaPlayerColors() }
        }
    }

    private fun applyInactiveMediaPlayerColors() {
        mQsOpHeaderView?.setMediaPlayerItemsColor(colorLabelInactive)
        colorInactive?.let {
            mQsOpHeaderView?.mediaPlayerBackground?.setColorFilter(it)
        }
        deferredMediaPlayerInactiveColorAction = null
    }

    private suspend fun processArtwork(
        bitmap: Bitmap?,
        mMediaAlbumArtBg: ImageView
    ): Bitmap? {
        return withContext(Dispatchers.IO) {
            if (bitmap == null) return@withContext null

            val width = mMediaAlbumArtBg.width
            val height = mMediaAlbumArtBg.height

            getScaledRoundedBitmap(bitmap, width, height)
        }
    }

    private fun getScaledRoundedBitmap(
        bitmap: Bitmap,
        width: Int,
        height: Int
    ): Bitmap? {
        if (width <= 0 || height <= 0) return null

        val widthScale = width.toFloat() / bitmap.width
        val heightScale = height.toFloat() / bitmap.height
        val scaleFactor = maxOf(widthScale, heightScale)

        val scaledWidth = (bitmap.width * scaleFactor).toInt()
        val scaledHeight = (bitmap.height * scaleFactor).toInt()

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)

        val xOffset = (scaledWidth - width) / 2
        val yOffset = (scaledHeight - height) / 2

        val croppedBitmap = Bitmap.createBitmap(
            scaledBitmap,
            xOffset,
            yOffset,
            width,
            height
        )

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val paint = Paint().apply {
            isAntiAlias = true
            shader = BitmapShader(croppedBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }

        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())

        Canvas(output).drawRoundRect(rect, qsTileCornerRadius, qsTileCornerRadius, paint)

        return output
    }

    private fun applyColorFilterToBitmap(bitmap: Bitmap, color: Int?): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        val canvas = Canvas(output)

        val paint = Paint().apply {
            isAntiAlias = true
            shader = LinearGradient(
                0f, 0f, bitmap.width.toFloat(), 0f, // Horizontal gradient
                intArrayOf(
                    ColorUtils.blendARGB(
                        color ?: Color.BLACK,
                        Color.TRANSPARENT,
                        0.4f
                    ), // Start color (left)
                    ColorUtils.blendARGB(
                        color ?: Color.BLACK,
                        Color.TRANSPARENT,
                        0.6f
                    ), // Left to Middle color
                    ColorUtils.blendARGB(
                        color ?: Color.BLACK,
                        Color.TRANSPARENT,
                        0.8f
                    ), // Middle color (less intensity)
                    ColorUtils.blendARGB(
                        color ?: Color.BLACK,
                        Color.TRANSPARENT,
                        0.6f
                    ), // Right to Middle color
                    ColorUtils.blendARGB(
                        color ?: Color.BLACK,
                        Color.TRANSPARENT,
                        0.4f
                    )  // End color (right)
                ),
                floatArrayOf(0f, 0.2f, 0.5f, 0.8f, 1f), // Positions for the colors
                Shader.TileMode.CLAMP
            )
        }

        canvas.drawBitmap(bitmap, 0f, 0f, null)

        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)

        return output
    }

    private suspend fun extractDominantColor(bitmap: Bitmap?): Int? =
        suspendCancellableCoroutine { cont ->
            if (bitmap == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }

            Palette.from(bitmap).generate { palette ->
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val fallbackColor = Score.score(QuantizerCelebi.quantize(pixels, 25)).firstOrNull()
                val dominantColor = palette?.getDominantColor(fallbackColor ?: Color.BLACK)
                cont.resume(dominantColor)
            }
        }

    private fun getContrastingTextColor(color: Int?): Int? {
        if (color == null) return null

        val luminance = (0.299 * Color.red(color) +
                0.587 * Color.green(color) +
                0.114 * Color.blue(color)) / 255

        return if (luminance > 0.5) Color.BLACK else Color.WHITE
    }

    private val mOnClickListener = View.OnClickListener { v ->
        if (v === mQsOpHeaderView?.internetTile) {
            toggleInternetState(v)
            vibrate()
        } else if (v === mQsOpHeaderView?.bluetoothTile) {
            toggleBluetoothState(v)
            vibrate()
        } else if (v === mQsOpHeaderView?.mediaPlayerPrevBtn) {
            performMediaAction(MediaAction.PLAY_PREVIOUS)
        } else if (v === mQsOpHeaderView?.mediaPlayerPlayPauseBtn) {
            performMediaAction(MediaAction.TOGGLE_PLAYBACK)
        } else if (v === mQsOpHeaderView?.mediaPlayerNextBtn) {
            performMediaAction(MediaAction.PLAY_NEXT)
        } else if (v === mQsOpHeaderView?.mediaPlayerBackground) {
            launchMediaPlayer()
        } else if (v === mQsOpHeaderView?.mediaOutputSwitcher) {
            launchMediaOutputSwitcher(v)
        }
    }

    private val mOnLongClickListener = OnLongClickListener { v ->
        if (mActivityStarter == null) return@OnLongClickListener false

        if (v === mQsOpHeaderView?.internetTile) {
            callMethod(
                mActivityStarter,
                "postStartActivityDismissingKeyguard",
                Intent(Settings.ACTION_WIFI_SETTINGS),
                0
            )
            vibrate()
            return@OnLongClickListener true
        } else if (v === mQsOpHeaderView?.bluetoothTile) {
            callMethod(
                mActivityStarter,
                "postStartActivityDismissingKeyguard",
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
                0
            )
            vibrate()
            return@OnLongClickListener true
        } else {
            return@OnLongClickListener false
        }
    }

    private fun launchMediaOutputSwitcher(v: View) {
        val packageName: String? = mMediaController?.packageName
        if (packageName != null && mMediaOutputDialogFactory != null) {
            callMethod(mMediaOutputDialogFactory, "create", packageName, true, v)
        }
    }

    private fun launchMediaPlayer() {
        if (mActivityStarter == null) return

        val packageName: String? = mMediaController?.packageName
        val appIntent = if (packageName != null) Intent(
            mContext.packageManager.getLaunchIntentForPackage(packageName)
        )
        else null

        if (appIntent != null) {
            appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appIntent.setPackage(packageName)
            callMethod(mActivityStarter, "startActivity", appIntent, true)
            return
        }
    }

    private fun areBitmapsEqual(bitmap1: Bitmap?, bitmap2: Bitmap?): Boolean {
        if (bitmap1 == null && bitmap2 == null) {
            return true
        }
        if (bitmap1 == null || bitmap2 == null) {
            return false
        }
        if (bitmap1.width != bitmap2.width || bitmap1.height != bitmap2.height) {
            return false
        }

        val buffer1 = ByteBuffer.allocate(bitmap1.byteCount)
        val buffer2 = ByteBuffer.allocate(bitmap2.byteCount)

        bitmap1.copyPixelsToBuffer(buffer1)
        bitmap2.copyPixelsToBuffer(buffer2)

        return buffer1.array().contentEquals(buffer2.array())
    }

    private fun vibrate() {
        if (vibrateOnClick) {
            VibrationUtils.triggerVibration(mContext, 2)
        }
    }

    private fun initResources() {
        try {
            appContext = mContext.createPackageContext(
                BuildConfig.APPLICATION_ID,
                Context.CONTEXT_IGNORE_SECURITY
            )
        } catch (ignored: PackageManager.NameNotFoundException) {
        }

        qsTileViewImplInstance?.let { thisObject ->
            colorActive = getObjectField(
                thisObject,
                "colorActive"
            ) as Int
            colorInactive = getObjectField(
                thisObject,
                "colorInactive"
            ) as Int
            colorLabelActive = getObjectField(
                thisObject,
                "colorLabelActive"
            ) as Int
            colorLabelInactive = getObjectField(
                thisObject,
                "colorLabelInactive"
            ) as Int
        }

        mContext.apply {
            colorAccent = getColorAttr(
                this,
                resources.getIdentifier(
                    "colorAccent",
                    "attr",
                    FRAMEWORK_PACKAGE
                )
            ).defaultColor
            colorPrimary = getColorAttr(
                this,
                resources.getIdentifier(
                    "colorPrimary",
                    "attr",
                    FRAMEWORK_PACKAGE
                )
            ).defaultColor

            qsTileCornerRadius = resources.getDimensionPixelSize(
                resources.getIdentifier(
                    "qs_corner_radius",
                    "dimen",
                    SYSTEMUI_PACKAGE
                )
            ).toFloat()
        }

        opMediaBackgroundDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = qsTileCornerRadius
            colorInactive?.let { colors = intArrayOf(it, it) }
        }

        mContext.apply {
            mConnectivityManager =
                getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            mTelephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            mWifiManager = getSystemService(WifiManager::class.java)
            mBluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        }
    }

    private val isLandscape: Boolean
        get() = mContext.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    companion object {
        private val TAG = "Iconify - ${OpQsHeader::class.java.simpleName}: "

        var launchableImageView: Class<*>? = null
        var launchableLinearLayout: Class<*>? = null
    }
}