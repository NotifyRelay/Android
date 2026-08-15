package com.xzyht.notifyrelay.ui.activity

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import com.xzyht.notifyrelay.R
import com.xzyht.notifyrelay.servers.appslist.AppListHelper
import com.xzyht.notifyrelay.ui.common.NotifyRelayTheme
import com.xzyht.notifyrelay.ui.common.ProvideNavigationEventDispatcherOwner
import com.xzyht.notifyrelay.ui.common.SetupSystemBars
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import notifyrelay.base.util.IntentUtils
import notifyrelay.base.util.Logger
import notifyrelay.base.util.PermissionHelper
import notifyrelay.base.util.ThemeSettingsManager
import notifyrelay.base.util.ToastUtils
import notifyrelay.data.StorageManager
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

class GuideActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isFirstLaunch = StorageManager.getBoolean(this, "isFirstLaunch", true, StorageManager.PrefsType.GENERAL)
        val fromInternal = intent.getBooleanExtra("fromInternal", false)
        val fromftp = intent.getBooleanExtra("fromftp", false)

        // 仅冷启动且权限满足时自动跳主界面，应用内跳转（fromInternal=true）始终渲染引导页。
        // 流程仿照 HyperCeiler：欢迎页 -> 权限设置 -> 使用须知 -> 基础设置 -> 完成页。
        if (!fromInternal && PermissionHelper.checkAllPermissions(this) && !isFirstLaunch) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // 沉浸式虚拟键，内容延伸到手势提示线区域
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            ProvideNavigationEventDispatcherOwner {
                val appContext = LocalContext.current
                val systemDarkTheme = isSystemInDarkTheme()
                var themeBaseIndex by remember {
                    mutableIntStateOf(ThemeSettingsManager.getThemeBaseIndex(appContext))
                }
                val isDarkTheme = when (themeBaseIndex) {
                    ThemeSettingsManager.THEME_LIGHT -> false
                    ThemeSettingsManager.THEME_DARK -> true
                    else -> systemDarkTheme
                }

                NotifyRelayTheme(darkTheme = isDarkTheme) {
                    SetupSystemBars(isDarkTheme)
                    GuideScreen(
                        themeBaseIndex = themeBaseIndex,
                        onThemeChanged = { newIndex ->
                            ThemeSettingsManager.setThemeBaseIndex(appContext, newIndex)
                            themeBaseIndex = newIndex
                        },
                        onContinue = {
                            // 首次启动后标记为已启动
                            StorageManager.putBoolean(this@GuideActivity, "isFirstLaunch", false, StorageManager.PrefsType.GENERAL)

                            if (fromftp) {
                                // 如果是从 FTP 请求跳转过来的，尝试重新启动 FTP 服务
                                Logger.d("GuideActivity", "从 FTP 请求跳转，尝试重新启动 FTP 服务")
                            }

                            startActivity(Intent(this@GuideActivity, MainActivity::class.java))
                            finish()
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 当从系统设置页面返回时，刷新权限状态
        GuideScreen.refreshTrigger++

        // 检查是否从 FTP 请求跳转过来，并且已经获取了文件管理权限
        val fromftp = intent.getBooleanExtra("fromftp", false)
        if (fromftp && PermissionHelper.checkManageExternalStoragePermission(this)) {
            Logger.d("GuideActivity", "从 FTP 请求跳转，已经获取文件管理权限，尝试重新启动 FTP 服务")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    object GuideScreen {
        // 用于触发刷新
        var refreshTrigger by mutableIntStateOf(0)
    }
}

private enum class GuideStep {
    WELCOME,
    REQUIRED_PERMISSIONS,
    OPTIONAL_PERMISSIONS,
    AGREEMENT,
    BASIC_SETTINGS,
    COMPLETE
}

private data class GuidePermissionUiState(
    val notificationListener: Boolean = false,
    val queryApps: Boolean = false,
    val postNotifications: Boolean = false,
    val bluetoothConnect: Boolean = false,
    val manageExternalStorage: Boolean = false,
    val backgroundUnlimited: Boolean = false,
    val overlay: Boolean = false
) {
    val requiredGranted: Boolean
        get() = notificationListener && queryApps && postNotifications
}

private fun readGuidePermissionState(context: Context): GuidePermissionUiState {
    val enabledListeners = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    )
    val hasNotification = enabledListeners?.contains(context.packageName) == true

    // 与 PermissionHelper.checkAllPermissions 保持一致：MIUI/澎湃系统还需要
    // 显式授予 com.android.permission.GET_INSTALLED_APPS，否则主界面会再次跳回引导页。
    val isMiuiOrPengpai = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
        try {
            val permissionInfo = context.packageManager.getPermissionInfo(
                "com.android.permission.GET_INSTALLED_APPS",
                0
            )
            permissionInfo.packageName == "com.lbe.security.miui"
        } catch (_: Exception) {
            false
        }
    val canQueryApps = AppListHelper.canQueryApps(context) &&
        (!isMiuiOrPengpai || ContextCompat.checkSelfPermission(
            context,
            "com.android.permission.GET_INSTALLED_APPS"
        ) == PackageManager.PERMISSION_GRANTED)

    return GuidePermissionUiState(
        notificationListener = hasNotification,
        queryApps = canQueryApps,
        postNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        },
        bluetoothConnect = PermissionHelper.checkBluetoothConnectPermission(context),
        manageExternalStorage = PermissionHelper.checkManageExternalStoragePermission(context),
        backgroundUnlimited = PermissionHelper.checkBackgroundUnlimitedPermission(context),
        overlay = PermissionHelper.checkOverlayPermission(context)
    )
}

@Composable
private fun GuideScreen(
    themeBaseIndex: Int,
    onThemeChanged: (Int) -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    var permissionState by remember { mutableStateOf(readGuidePermissionState(context)) }

    fun refreshPermissions() {
        permissionState = readGuidePermissionState(context)
    }

    // 页面首次进入时刷新一次，随后每秒轮询一次，确保从系统设置返回后状态能及时更新
    LaunchedEffect(Unit) {
        refreshPermissions()
        while (true) {
            delay(1.seconds)
            refreshPermissions()
        }
    }

    // GuideActivity.onResume 也会主动触发一次刷新
    val resumeTrigger = GuideActivity.GuideScreen.refreshTrigger
    LaunchedEffect(resumeTrigger) {
        refreshPermissions()
    }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { GuideStep.entries.size })
    val scope = rememberCoroutineScope()

    fun animateTo(step: GuideStep) {
        scope.launch {
            pagerState.animateScrollToPage(step.ordinal)
        }
    }

    BackHandler(enabled = pagerState.currentPage > 0) {
        val previous = GuideStep.entries.getOrNull(pagerState.currentPage - 1)
        if (previous != null) {
            animateTo(previous)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // 引导页不开放手势滑动，避免跳过必读步骤或权限页
            userScrollEnabled = false
        ) { page ->
            when (GuideStep.entries[page]) {
                GuideStep.WELCOME -> GuideWelcomePage(
                    onStart = { animateTo(GuideStep.REQUIRED_PERMISSIONS) }
                )

                GuideStep.REQUIRED_PERMISSIONS -> GuideRequiredPermissionPage(
                    permissionState = permissionState,
                    onBack = { animateTo(GuideStep.WELCOME) },
                    onNext = { animateTo(GuideStep.OPTIONAL_PERMISSIONS) }
                )

                GuideStep.OPTIONAL_PERMISSIONS -> GuideOptionalPermissionPage(
                    permissionState = permissionState,
                    onBack = { animateTo(GuideStep.REQUIRED_PERMISSIONS) },
                    onNext = { animateTo(GuideStep.AGREEMENT) }
                )

                GuideStep.AGREEMENT -> GuideAgreementPage(
                    onBack = { animateTo(GuideStep.OPTIONAL_PERMISSIONS) },
                    onNext = { animateTo(GuideStep.BASIC_SETTINGS) }
                )

                GuideStep.BASIC_SETTINGS -> GuideBasicSettingsPage(
                    selectedThemeIndex = themeBaseIndex,
                    onThemeSelected = onThemeChanged,
                    onBack = { animateTo(GuideStep.AGREEMENT) },
                    onNext = { animateTo(GuideStep.COMPLETE) }
                )

                GuideStep.COMPLETE -> GuideCompletePage(
                    requiredGranted = permissionState.requiredGranted,
                    onBackToPermissions = { animateTo(GuideStep.REQUIRED_PERMISSIONS) },
                    onEnter = onContinue
                )
            }
        }
    }
}

/**
 * 仿 HyperCeiler 欢迎页的呼吸光晕背景。
 */
@Composable
private fun GuideGlowBackground(modifier: Modifier = Modifier) {
    val colorScheme = MiuixTheme.colorScheme
    val infiniteTransition = rememberInfiniteTransition(label = "guideGlow")
    val drift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "guideGlowDrift"
    )
    val secondaryDrift by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "guideGlowSecondaryDrift"
    )

    Canvas(modifier = modifier) {
        val radius = size.minDimension * 0.95f
        val firstCenter = Offset(
            x = size.width * (0.15f + 0.70f * drift),
            y = size.height * (0.10f + 0.28f * (1f - drift))
        )
        val secondCenter = Offset(
            x = size.width * (0.10f + 0.80f * secondaryDrift),
            y = size.height * (0.58f + 0.32f * drift)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    colorScheme.primary.copy(alpha = 0.22f),
                    Color.Transparent
                ),
                center = firstCenter,
                radius = radius
            ),
            radius = radius,
            center = firstCenter
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    colorScheme.secondary.copy(alpha = 0.16f),
                    Color.Transparent
                ),
                center = secondCenter,
                radius = radius * 0.72f
            ),
            radius = radius * 0.72f,
            center = secondCenter
        )
    }
}

@Composable
private fun GuideAppLogo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(24.dp)

    // 与设置-关于页保持一致：通过 PackageManager 读取系统实际的应用图标，
    // 避免直接加载 adaptive-icon XML（painterResource 不支持该类型）。
    val appIcon = remember(context) {
        runCatching {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            if (drawable is BitmapDrawable) {
                drawable.bitmap.asImageBitmap()
            } else {
                val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
                val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
                val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
                bitmap.asImageBitmap()
            }
        }.getOrNull()
    }

    if (appIcon != null) {
        Image(
            bitmap = appIcon,
            contentDescription = "应用图标",
            modifier = modifier.clip(shape)
        )
    } else {
        // 兜底：读取失败时使用可被 painterResource 支持的矢量图层拼出图标
        Box(modifier = modifier.clip(shape)) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun GuideWelcomePage(onStart: () -> Unit) {
    val colorScheme = MiuixTheme.colorScheme
    var revealed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(350)
        revealed = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GuideGlowBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(
                visible = revealed,
                enter = fadeIn(animationSpec = tween(durationMillis = 900)) +
                    slideInVertically(
                        animationSpec = tween(durationMillis = 900),
                        initialOffsetY = { it / 3 }
                    )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GuideAppLogo(modifier = Modifier.size(92.dp))
                    Spacer(modifier = Modifier.height(28.dp))
                    Text(
                        text = "NotifyRelay",
                        style = MiuixTheme.textStyles.title1,
                        color = colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "跨设备通知转发 · 设备互联",
                        style = MiuixTheme.textStyles.subtitle,
                        color = colorScheme.onBackgroundVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "首次使用前，需要完成几项简单设置",
                        style = MiuixTheme.textStyles.body2,
                        color = colorScheme.onSurfaceVariantSummary
                    )
                }
            }

            AnimatedVisibility(
                visible = revealed,
                enter = fadeIn(animationSpec = tween(durationMillis = 500, delayMillis = 450))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(52.dp))
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(colorScheme.primary)
                            .clickable(onClick = onStart),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.guide_icon_arrow),
                            contentDescription = "开始",
                            modifier = Modifier.size(width = 31.dp, height = 22.dp),
                            tint = colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "开始",
                        style = MiuixTheme.textStyles.body2,
                        color = colorScheme.onBackground
                    )
                }
            }
        }
    }
}

@Composable
private fun GuidePageHeader(
    stepLabel: String,
    title: String,
    subtitle: String
) {
    val colorScheme = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(
            text = stepLabel,
            style = MiuixTheme.textStyles.footnote1,
            color = colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MiuixTheme.textStyles.title2,
            color = colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MiuixTheme.textStyles.body2,
            color = colorScheme.onSurfaceVariantSummary
        )
    }
}

@Composable
private fun GuidePermissionItem(
    title: String,
    summary: String,
    granted: Boolean,
    onClick: () -> Unit,
    grantedText: String = "已授权",
    pendingText: String = "去开启",
    extraContent: @Composable () -> Unit = {}
) {
    val colorScheme = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (granted) colorScheme.primary else colorScheme.outline)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                color = colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (granted) grantedText else pendingText,
                style = MiuixTheme.textStyles.body2,
                color = if (granted) colorScheme.primary else colorScheme.error
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = summary,
            style = MiuixTheme.textStyles.body2,
            color = colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 20.dp)
        )
        extraContent()
    }
    HorizontalDivider(color = colorScheme.dividerLine, thickness = 1.dp)
}

@Composable
private fun GuideSectionLabel(title: String, description: String) {
    val colorScheme = MiuixTheme.colorScheme
    Column(modifier = Modifier.padding(top = 16.dp, bottom = 10.dp)) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.title3,
            color = colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MiuixTheme.textStyles.body2,
            color = colorScheme.onSurfaceVariantSummary
        )
    }
}

@Composable
private fun GuideRequiredPermissionPage(
    permissionState: GuidePermissionUiState,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MiuixTheme.colorScheme

    fun showToast(message: String) {
        ToastUtils.showShortToast(context, message)
    }

    fun openNotificationListenerSettings() {
        showToast("请开启通知访问权限")
        IntentUtils.startActivity(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, addNewTaskFlag = true)
    }

    fun requestQueryAppsPermission() {
        try {
            val isMiuiOrPengpai = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
                try {
                    val permissionInfo = context.packageManager.getPermissionInfo("com.android.permission.GET_INSTALLED_APPS", 0)
                    permissionInfo != null && permissionInfo.packageName == "com.lbe.security.miui"
                } catch (_: Exception) {
                    false
                }
            if (isMiuiOrPengpai) {
                if (ContextCompat.checkSelfPermission(context, "com.android.permission.GET_INSTALLED_APPS") != PackageManager.PERMISSION_GRANTED) {
                    (context as? Activity)?.let { act ->
                        ActivityCompat.requestPermissions(
                            act,
                            arrayOf("com.android.permission.GET_INSTALLED_APPS"),
                            999
                        )
                        showToast("已请求应用列表权限，请在弹窗中允许")
                    } ?: run {
                        showToast("请在应用信息页面的权限管理-其他权限中允许<访问应用列表>")
                    }
                } else {
                    showToast("已获得应用列表权限")
                }
            } else {
                showToast("请在应用信息页面的权限管理-其他权限中允许<访问应用列表>")
                IntentUtils.startActivity(
                    context,
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                    true
                )
            }
        } catch (_: Exception) {
            showToast("请在应用信息页面的权限管理-其他权限中允许<访问应用列表>")
            IntentUtils.startActivity(
                context,
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
                true
            )
        }
    }

    fun requestPostNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            showToast("请求通知发送权限")
            (context as? Activity)?.requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        } else {
            showToast("请在系统设置中开启通知权限")
        }
    }

    fun openSelfStartSettings() {
        showToast("请在应用详情页启用自启动权限")
        IntentUtils.startActivity(
            context,
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
            true
        )
    }

    val requiredChecks = buildList {
        add(permissionState.notificationListener)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(permissionState.postNotifications)
        }
        add(permissionState.queryApps)
    }
    val requiredGrantedCount = requiredChecks.count { it }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        GuidePageHeader(
            stepLabel = "2 / 6",
            title = "必要权限",
            subtitle = if (permissionState.requiredGranted) {
                "所有必要权限已开启，可以继续下一步"
            } else {
                "通知转发依赖以下权限，请逐项开启（$requiredGrantedCount/${requiredChecks.size}）"
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            GuideSectionLabel(
                title = "必要权限",
                description = "缺少任一项都会影响通知读取、应用识别或后台服务运行"
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    GuidePermissionItem(
                        title = "通知访问权限",
                        summary = if (permissionState.notificationListener) {
                            "已允许读取通知内容，用于跨设备转发"
                        } else {
                            "用于读取通知内容，实现核心转发功能"
                        },
                        granted = permissionState.notificationListener,
                        onClick = ::openNotificationListenerSettings
                    )
                    GuidePermissionItem(
                        title = "应用列表权限",
                        summary = if (permissionState.queryApps) {
                            "已允许查询本机已安装应用，可辅助通知跳转"
                        } else {
                            "用于发现本机已安装应用，辅助通知跳转"
                        },
                        granted = permissionState.queryApps,
                        onClick = ::requestQueryAppsPermission
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        GuidePermissionItem(
                            title = "通知发送权限",
                            summary = if (permissionState.postNotifications) {
                                "已允许发送本地通知"
                            } else {
                                "用于发送本地通知，部分功能需要开启"
                            },
                            granted = permissionState.postNotifications,
                            onClick = ::requestPostNotificationPermission
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 自启动权限无法可靠读取状态，按需求直接使用 ArrowPreference 引导确认。
            ArrowPreference(
                title = "自启动权限",
                summary = "必选项：部分系统无法直接读取状态。用于保证通知监听服务在后台稳定运行，请点击前往应用详情确认并开启。",
                onClick = ::openSelfStartSettings
            )

            Spacer(modifier = Modifier.height(20.dp))
        }

        GuidePageFooter(
            hint = if (permissionState.requiredGranted) null else "完成必要权限授权后，按钮会自动变为可点击状态",
            nextText = if (permissionState.requiredGranted) "下一步" else "请先完成必要权限",
            nextEnabled = permissionState.requiredGranted,
            onBack = onBack,
            onNext = onNext,
            hintColor = colorScheme.error
        )
    }
}

@Composable
private fun GuideOptionalPermissionPage(
    permissionState: GuidePermissionUiState,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current

    fun showToast(message: String) {
        ToastUtils.showShortToast(context, message)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        GuidePageHeader(
            stepLabel = "3 / 6",
            title = "可选权限",
            subtitle = "以下权限建议开启，也可以稍后在系统设置或应用内设置中开启"
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            GuideSectionLabel(
                title = "建议开启",
                description = "用于优化设备发现、FTP、超级岛等增强功能"
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    GuidePermissionItem(
                        title = "蓝牙连接权限",
                        summary = if (permissionState.bluetoothConnect) {
                            "已授权，可优化设备发现速度并显示真实设备名"
                        } else {
                            "用于优化设备发现速度，显示真实设备名"
                        },
                        granted = permissionState.bluetoothConnect,
                        onClick = {
                            (context as? Activity)?.let { act ->
                                PermissionHelper.requestBluetoothConnectPermission(act)
                            }
                            showToast("开启后可优化设备发现速度，并以设备实际名称而非型号作为设备名")
                        }
                    )
                    GuidePermissionItem(
                        title = "文件管理权限",
                        summary = if (permissionState.manageExternalStorage) {
                            "已授权，FTP 功能可正常管理设备文件"
                        } else {
                            "用于支持 FTP 功能，管理设备文件"
                        },
                        granted = permissionState.manageExternalStorage,
                        onClick = {
                            showToast("跳转文件管理权限设置")
                            PermissionHelper.requestManageExternalStoragePermission(context)
                        }
                    )
                    GuidePermissionItem(
                        title = "后台无限制权限",
                        summary = if (permissionState.backgroundUnlimited) {
                            "已设置，应用可在后台保持运行"
                        } else {
                            "用于确保应用在后台正常运行，防止被系统杀死"
                        },
                        granted = permissionState.backgroundUnlimited,
                        grantedText = "已设置",
                        pendingText = "去设置",
                        onClick = {
                            showToast("跳转到电池优化设置，请将应用设为无限制")
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            intent.data = "package:${context.packageName}".toUri()
                            IntentUtils.startActivity(context, intent, true)
                        }
                    )
                    GuidePermissionItem(
                        title = "悬浮窗权限",
                        summary = if (permissionState.overlay) {
                            "已授权，可显示超级岛/悬浮岛复刻"
                        } else {
                            "用于支持超级岛/悬浮岛复刻，提升通知交互体验"
                        },
                        granted = permissionState.overlay,
                        onClick = {
                            showToast("跳转悬浮窗权限设置")
                            try {
                                (context as? Activity)?.let { act ->
                                    PermissionHelper.requestOverlayPermission(act)
                                } ?: run {
                                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                    intent.data = "package:${context.packageName}".toUri()
                                    IntentUtils.startActivity(context, intent, true)
                                }
                            } catch (_: Exception) {
                                showToast("无法跳转悬浮窗设置，请手动在系统设置中允许悬浮窗权限")
                            }
                        }
                    )
                }
            }

            if (Build.VERSION.SDK_INT >= 35) {
                Spacer(modifier = Modifier.height(12.dp))

                // 敏感通知权限无法可靠读取状态，按需求同样使用 ArrowPreference 引导。
                ArrowPreference(
                    title = "敏感通知访问权限",
                    summary = "Android 15+ 可选权限。状态无法直接读取；未开启时部分通知只会显示“已隐藏敏感通知”。点击尝试跳转授权，也可以复制下方 adb 命令授权。",
                    onClick = {
                        (context as? Activity)?.let { act ->
                            PermissionHelper.requestSensitiveNotificationPermission(act)
                        }
                    }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            IntentUtils.startActivity(
                                context,
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                                true
                            )
                        },
                        modifier = Modifier.weight(1f),
                        minWidth = 0.dp,
                        minHeight = 36.dp
                    ) {
                        Text(text = "去设置", style = MiuixTheme.textStyles.body2)
                    }
                    Button(
                        onClick = {
                            val adbCmd = "adb shell appops set ${context.packageName} RECEIVE_SENSITIVE_NOTIFICATIONS allow"
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("adb", adbCmd)
                            clipboard.setPrimaryClip(clip)
                            showToast("已复制 adb 命令到剪贴板")
                        },
                        modifier = Modifier.weight(1f),
                        minWidth = 0.dp,
                        minHeight = 36.dp
                    ) {
                        Text(text = "复制 adb 命令", style = MiuixTheme.textStyles.body2)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        GuidePageFooter(
            hint = null,
            nextText = "下一步",
            nextEnabled = true,
            onBack = onBack,
            onNext = onNext
        )
    }
}

@Composable
private fun GuideAgreementPage(
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val colorScheme = MiuixTheme.colorScheme
    var agreed by rememberSaveable { mutableStateOf(false) }

    val notices = listOf(
        "仅局域网传输" to "通知、剪贴板、文件等内容只会在你已配对并连接的设备之间传输，应用不会将数据上传到第三方服务器。",
        "注意通知隐私" to "通知可能包含聊天消息、验证码等敏感信息。请仅在可信设备上开启通知转发，并妥善保管配对密钥。",
        "按需授予权限" to "通知监听、通知发送和应用列表权限是必要权限；蓝牙、悬浮窗、文件管理、后台无限制等可选权限可以在系统设置中随时更改。",
        "功能存在系统差异" to "不同厂商对自启动、后台运行和敏感通知的管理策略不同，请在系统设置中按页面提示逐项确认。"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        GuidePageHeader(
            stepLabel = "4 / 6",
            title = "使用须知与授权说明",
            subtitle = "请阅读以下说明，确认后继续"
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    notices.forEachIndexed { index, notice ->
                        Row(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(
                                text = "${index + 1}.",
                                style = MiuixTheme.textStyles.body1,
                                color = colorScheme.primary,
                                modifier = Modifier.width(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = notice.first,
                                    style = MiuixTheme.textStyles.body1,
                                    color = colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = notice.second,
                                    style = MiuixTheme.textStyles.body2,
                                    color = colorScheme.onSurfaceVariantSummary
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    state = if (agreed) ToggleableState.On else ToggleableState.Off,
                    onClick = { agreed = !agreed },
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "我已阅读并同意上述使用须知与授权说明",
                    style = MiuixTheme.textStyles.body1,
                    color = colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { agreed = !agreed }
                )
            }
        }

        GuidePageFooter(
            hint = if (agreed) null else "请先阅读并同意使用须知",
            nextText = "同意并继续",
            nextEnabled = agreed,
            onBack = onBack,
            onNext = onNext
        )
    }
}

@Composable
private fun GuideThemeOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                color = colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = description,
                style = MiuixTheme.textStyles.body2,
                color = colorScheme.onSurfaceVariantSummary
            )
        }
    }
    HorizontalDivider(color = colorScheme.dividerLine, thickness = 1.dp)
}

@Composable
private fun GuideBasicSettingsPage(
    selectedThemeIndex: Int,
    onThemeSelected: (Int) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val themeOptions = listOf(
        Triple(ThemeSettingsManager.THEME_FOLLOW_SYSTEM, "跟随系统", "随系统深色模式自动切换"),
        Triple(ThemeSettingsManager.THEME_LIGHT, "浅色模式", "始终使用浅色外观"),
        Triple(ThemeSettingsManager.THEME_DARK, "深色模式", "始终使用深色外观")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        GuidePageHeader(
            stepLabel = "5 / 6",
            title = "基础设置",
            subtitle = "按你的偏好设置外观，进入应用后仍可随时修改"
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            GuideSectionLabel(
                title = "外观",
                description = "选择应用使用的明暗模式"
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    themeOptions.forEach { (index, title, description) ->
                        GuideThemeOption(
                            title = title,
                            description = description,
                            selected = selectedThemeIndex == index,
                            onClick = { onThemeSelected(index) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        GuidePageFooter(
            hint = null,
            nextText = "下一步",
            nextEnabled = true,
            onBack = onBack,
            onNext = onNext
        )
    }
}

@Composable
private fun GuideCompletePage(
    requiredGranted: Boolean,
    onBackToPermissions: () -> Unit,
    onEnter: () -> Unit
) {
    val colorScheme = MiuixTheme.colorScheme
    var revealed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(300)
        revealed = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GuideGlowBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            AnimatedVisibility(
                visible = revealed,
                enter = fadeIn(animationSpec = tween(durationMillis = 800)) +
                    slideInVertically(
                        animationSpec = tween(durationMillis = 800),
                        initialOffsetY = { it / 3 }
                    )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GuideAppLogo(modifier = Modifier.size(92.dp))
                    Spacer(modifier = Modifier.height(28.dp))
                    Text(
                        text = "设置完成",
                        style = MiuixTheme.textStyles.title1,
                        color = colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "NotifyRelay 已准备就绪",
                        style = MiuixTheme.textStyles.subtitle,
                        color = colorScheme.onBackgroundVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (requiredGranted) {
                            "必要权限已全部开启，现在可以开始跨设备转发通知了"
                        } else {
                            "检测到必要权限尚未全部开启，请返回权限设置页检查"
                        },
                        style = MiuixTheme.textStyles.body2,
                        color = colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (requiredGranted) {
                        onEnter()
                    } else {
                        onBackToPermissions()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 50.dp),
                minWidth = 0.dp,
                minHeight = 50.dp
            ) {
                Text(
                    text = if (requiredGranted) "进入 NotifyRelay" else "返回权限设置",
                    style = MiuixTheme.textStyles.button
                )
            }
        }
    }
}

@Composable
private fun GuidePageFooter(
    hint: String?,
    nextText: String,
    nextEnabled: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    hintColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        if (hint != null) {
            Text(
                text = hint,
                style = MiuixTheme.textStyles.footnote1,
                color = hintColor,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                text = "上一步",
                onClick = onBack,
                modifier = Modifier.defaultMinSize(minWidth = 88.dp, minHeight = 50.dp),
                minWidth = 88.dp,
                minHeight = 50.dp
            )
            Button(
                onClick = onNext,
                enabled = nextEnabled,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 50.dp),
                minWidth = 0.dp,
                minHeight = 50.dp
            ) {
                Text(
                    text = nextText,
                    style = MiuixTheme.textStyles.button
                )
            }
        }
    }
}
