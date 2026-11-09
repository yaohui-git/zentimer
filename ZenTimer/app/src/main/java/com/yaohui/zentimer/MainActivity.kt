@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.yaohui.zentimer

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * 两套禅意色板：深色为冷墨蓝 + 暖米金；浅色为宣纸米白 + 赭金，
 * 保持同样的「底 / 浮起 / 卡片 / 强调 / 次级」层次，不做花哨配色。
 */
private data class StillColors(
    val bg: Color, val elevated: Color, val card: Color,
    val accent: Color, val accentDim: Color, val text: Color,
    val secondary: Color, val track: Color, val stroke: Color,
    val divider: Color, val isDark: Boolean
)

private val DarkPalette = StillColors(
    bg = Color(0xFF080D14), elevated = Color(0xFF101923), card = Color(0xFF16222E),
    accent = Color(0xFFE8CFA0), accentDim = Color(0xFF8C7E63),
    text = Color(0xFFF3F0E9), secondary = Color(0xFF8C9BAA),
    track = Color(0xFF1F2D3A), stroke = Color(0xFF2B3A48),
    divider = Color(0xFF1B2834), isDark = true
)

private val LightPalette = StillColors(
    bg = Color(0xFFF6F1E6), elevated = Color(0xFFFFFBF3), card = Color(0xFFEFE7D8),
    accent = Color(0xFF8A6528), accentDim = Color(0xFFA8916C),
    text = Color(0xFF2C2620), secondary = Color(0xFF796F61),
    track = Color(0xFFDED4C0), stroke = Color(0xFFD3C8B2),
    divider = Color(0xFFE3DAC9), isDark = false
)

private val LocalStillColors = staticCompositionLocalOf { DarkPalette }

/**
 * 保留 C.xxx 这一层：140 处调用点一行都不用改，颜色由 StillTheme 按主题注入。
 * 只有两处 Canvas 的 DrawScope 不是 Composable 上下文，那里在绘制前先取局部变量。
 */
private object C {
    val bg: Color @Composable get() = LocalStillColors.current.bg
    val elevated: Color @Composable get() = LocalStillColors.current.elevated
    val card: Color @Composable get() = LocalStillColors.current.card
    val accent: Color @Composable get() = LocalStillColors.current.accent
    val accentDim: Color @Composable get() = LocalStillColors.current.accentDim
    val text: Color @Composable get() = LocalStillColors.current.text
    val secondary: Color @Composable get() = LocalStillColors.current.secondary
    val track: Color @Composable get() = LocalStillColors.current.track
    val stroke: Color @Composable get() = LocalStillColors.current.stroke
    val divider: Color @Composable get() = LocalStillColors.current.divider
    val isDark: Boolean @Composable get() = LocalStillColors.current.isDark
}

/** 三个子页共用一套顶栏与返回逻辑；主页不再堆底部导航，保持禅意留白。 */
private enum class Screen { PRESETS, SETTINGS, ABOUT }

class MainActivity : ComponentActivity() {

    /** 只在本次进程里问一次，避免反复弹窗。 */
    private var askedNotifPerm = false

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TimerEngine.init(this)   // 内部调用 Lang.init(app)
        Theme.init(this)
        // Android 15+（targetSdk >= 35）强制 edge-to-edge：内容会画到状态栏/挖孔之下。
        // 这里不按机型硬编码，改由 Compose 的 safeDrawing insets 在内容层统一避开，
        // 背景星空图仍铺满整屏，视觉上仍是沉浸式。
        setContent {
            StillTheme { StillScreen() }
        }
    }

    /**
     * 计时进行中才允许界面盖在锁屏之上：锁屏时点通知 / 状态栏胶囊，直接看到倒计时，
     * 不必先解锁。系统闹钟的响铃界面用的就是这条（setShowWhenLocked）。
     * 没有在计时就不放行，避免应用在锁屏上被无意唤起。
     */
    private fun applyLockScreenVisibility() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return
        setShowWhenLocked(TimerEngine.ui.value.running)
    }

    override fun onResume() {
        super.onResume()
        applyLockScreenVisibility()
        askNotificationPermission()
    }

    /**
     * Android 13 起，通知权限默认是关闭的；不授予的话，通知栏和锁屏上一条通知都没有，
     * 状态栏胶囊更无从谈起（实测 Redmi Note 15 Pro 就是 app 级 importance=NONE）。
     * 这是本应用唯一会弹的系统授权框，且只与「计时可见性」有关，不涉及任何数据。
     */
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33 || askedNotifPerm) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        askedNotifPerm = true
        runCatching { notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    override fun onDestroy() {
        super.onDestroy()
        // v2.3：练习进行中退出界面时不能释放音频——前台服务还在跑，背景音与结束铃都要留给它。
        // 以前无条件 releaseAudio()，按返回退出后背景音会悄悄停掉。
        if (isFinishing && !TimerEngine.ui.value.running) TimerEngine.releaseAudio()
    }
}

/**
 * 主题总入口：把色板注入 CompositionLocal，再交给 MaterialTheme。
 * 切换只重组界面，不重建 Activity、不打断计时；「跟随系统」用 isSystemInDarkTheme()，
 * 系统切换深浅模式会自动重组。浅色底同时把状态栏图标翻成深色，避免白底白字。
 */
@Composable
private fun StillTheme(content: @Composable () -> Unit) {
    val mode by Theme.mode.collectAsState()
    val dark = when (mode) {
        Theme.Mode.DARK -> true
        Theme.Mode.LIGHT -> false
        Theme.Mode.SYSTEM -> isSystemInDarkTheme()
    }
    val palette = if (dark) DarkPalette else LightPalette
    val view = LocalView.current
    val ctx = LocalContext.current

    SideEffect {
        val window = (ctx as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }

    CompositionLocalProvider(LocalStillColors provides palette) {
        MaterialTheme(
            colorScheme = if (dark) {
                darkColorScheme(
                    background = palette.bg, surface = palette.elevated,
                    primary = palette.accent, onPrimary = palette.bg, onSurface = palette.text
                )
            } else {
                lightColorScheme(
                    background = palette.bg, surface = palette.elevated,
                    primary = palette.accent, onPrimary = palette.bg, onSurface = palette.text
                )
            },
            content = content
        )
    }
}

@Composable
private fun StillScreen() {
    // 订阅语言状态：切换时整棵子树重组，文案即时刷新，计时状态完全不受影响。
    // 注意这里必须真的读一次值（watch 内部读 .value）：只声明一个没读过的
    // collectAsState() 是不会订阅的，设置页标题就会一直停在旧语言。
    Lang.watch()
    val s by TimerEngine.ui.collectAsState()
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    // 轻量尺寸适配：列宽与抽屉按屏宽收敛；不做折叠屏铰链感知 / 双栏（复杂度高）
    val cfg = LocalConfiguration.current
    val drawerWidth = minOf(300, (cfg.screenWidthDp * 0.84f).toInt()).dp

    var screen by remember { mutableStateOf<Screen?>(null) }
    var picker by remember { mutableStateOf<String?>(null) }
    var saveDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Preset?>(null) }
    var presetRev by remember { mutableStateOf(0) }
    var presetSheet by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var backOnce by remember { mutableStateOf(false) }
    val running = s.running

    // 界面只订阅 TimerEngine.ui 渲染，不再自己驱动状态机。
    // 到点结算的唯一入口在前台服务心跳里；UI 再跑一套 tick 就会重新变成「两个写入者」。
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1800L)
            toast = null
        }
    }
    KeepScreenOn(running)

    // v2.3 统一返回栈：浮层（选择器 / 保存 / 删除确认）由各自窗口自己关闭，
    // 这里只处理「抽屉 → 子页 → 主页」，主页再按一次才退出，避免误触直接退出应用。
    val hasOverlay = picker != null || saveDialog || confirmDelete != null || presetSheet
    BackHandler(enabled = !hasOverlay) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            screen != null -> screen = null
            else -> {
                if (backOnce) activity?.finish()
                else {
                    backOnce = true
                    toast = Lang.t("back_again")
                    scope.launch {
                        delay(2000L)
                        backOnce = false
                    }
                }
            }
        }
    }

    // 抽屉从左/右侧滑出：把布局方向临时翻成 Rtl，抽屉就落在右边（菜单图标也在右上）。
    // 内容与抽屉内部各自翻回 Ltr，不污染任何文案排版。
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = true,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ModalDrawerSheet(
                        modifier = Modifier.width(drawerWidth),
                        drawerContainerColor = C.elevated
                    ) {
                        DrawerContent(
                            onSelect = { target ->
                                scope.launch { drawerState.close() }
                                screen = target
                            }
                        )
                    }
                }
            }
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(C.bg)
                ) {
                    // 沉浸背景铺满全屏是刻意的；前景内容另由 safeDrawingPadding 避开系统栏
                    // 星空底图只在深色主题铺；浅色留一片干净的宣纸底，避免脏灰
                    if (running && C.isDark) {
                        Image(
                            painter = painterResource(R.drawable.bg_stars),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().alpha(0.20f),
                            contentScale = ContentScale.Crop
                        )
                    }

                    when (screen) {
                        null -> if (running) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .fillMaxWidth()
                                    .widthIn(max = 560.dp)
                                    .safeDrawingPadding()
                                    .padding(horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                RingBlock(s)
                                Spacer(Modifier.height(32.dp))
                                ActionRow(s)
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .widthIn(max = 560.dp)
                                    .safeDrawingPadding()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                TopBar(
                                    onMenu = { scope.launch { drawerState.open() } },
                                    onPresets = { presetSheet = true }
                                )
                                Spacer(Modifier.height(12.dp))
                                RingBlock(s)
                                Spacer(Modifier.height(20.dp))
                                ActionRow(s)
                                // v2.3 修订：预设改为顶栏书签 + 底部面板，主屏不再为它留整块区域。
                                // 时长 / 间隔 / 准备这些每次都要调的控件回到首屏，不再被挤到折叠线以下。
                                Spacer(Modifier.height(16.dp))
                                SettingsCard(s, onPick = { picker = it })
                                Spacer(Modifier.height(20.dp))
                            }
                        }
                        else -> Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .widthIn(max = 560.dp)
                                .safeDrawingPadding()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 16.dp)
                        ) {
                            SubPageBar(
                                title = when (screen) {
                                    Screen.PRESETS -> Lang.t("title_presets")
                                    Screen.SETTINGS -> Lang.t("title_settings")
                                    else -> Lang.t("title_about")
                                },
                                onBack = { screen = null }
                            )
                            Spacer(Modifier.height(20.dp))
                            when (screen) {
                                Screen.PRESETS -> PresetsPage(
                                    ctx = ctx,
                                    rev = presetRev,
                                    onToast = { toast = it },
                                    onSave = { saveDialog = true },
                                    onDelete = { confirmDelete = it }
                                )
                                Screen.SETTINGS -> SettingsPage(ctx, onToast = { toast = it })
                                Screen.ABOUT -> AboutPage(ctx, onToast = { toast = it })
                                null -> Unit
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }

                    // 计时中仍留一个低存在感的入口，不破坏沉浸，也不会把人关在外面
                    if (running && screen == null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(top = 6.dp, end = 20.dp)
                        ) {
                            MenuIcon(onClick = { scope.launch { drawerState.open() } }, dim = 0.5f)
                        }
                    }

                    if (toast != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 28.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(C.elevated)
                                .border(1.dp, C.stroke, RoundedCornerShape(20.dp))
                                .padding(horizontal = 18.dp, vertical = 11.dp)
                        ) {
                            Text(text = toast ?: "", color = C.accent, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    // 浮层放在抽屉之外：Dialog / BottomSheet 都是独立窗口，不受抽屉布局影响
    val key = picker
    if (key != null) {
        SoundPicker(key, s, onDismiss = { picker = null })
    }
    if (presetSheet) {
        PresetSheet(
            ctx = ctx,
            s = s,
            rev = presetRev,
            onToast = { toast = it },
            onSave = { saveDialog = true },
            onManage = { screen = Screen.PRESETS },
            onDismiss = { presetSheet = false }
        )
    }
    if (saveDialog) {
        SavePresetDialog(
            ctx = ctx,
            onDismiss = { saveDialog = false },
            onDone = {
                presetRev++
                toast = it
            }
        )
    }
    val deleting = confirmDelete
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = C.elevated,
            title = { Text(Lang.t("preset_delete_confirm"), color = C.text, fontSize = 16.sp) },
            text = { Text(deleting.name, color = C.secondary, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    PresetStore.remove(ctx, deleting.id)
                    presetRev++
                    confirmDelete = null
                    toast = Lang.t("preset_deleted")
                }) { Text(Lang.t("btn_delete_ok"), color = C.accent) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(Lang.t("cancel"), color = C.secondary)
                }
            }
        )
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val activity = LocalContext.current as? Activity
    LaunchedEffect(enabled) {
        if (enabled) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    DisposableEffect(Unit) {
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

// ---------- 顶部与导航 ----------

@Composable
private fun TopBar(onMenu: () -> Unit, onPresets: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = Lang.t("app_title"),
                color = C.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 4.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(text = Lang.greetingLine(), color = C.secondary, fontSize = 12.sp)
        }
        // 预设入口与菜单共用这一行，不新增任何常驻高度
        BookmarkIcon(onClick = onPresets)
        Spacer(Modifier.width(10.dp))
        MenuIcon(onClick = onMenu)
    }
}

/** 自绘书签轮廓：细线、无字、与菜单圆钮同尺寸，避免再引入一张图片资源。 */
@Composable
private fun BookmarkIcon(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, C.stroke, RoundedCornerShape(20.dp))
            .clickable(onClickLabel = Lang.t("preset_quick"), onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // DrawScope 不是 Composable 上下文，颜色先在外面取好
        val accent = C.accent
        Canvas(modifier = Modifier.size(15.dp)) {
            val w = size.width
            val h = size.height
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.20f, 0f)
                lineTo(w * 0.80f, 0f)
                lineTo(w * 0.80f, h)
                lineTo(w * 0.50f, h * 0.70f)
                lineTo(w * 0.20f, h)
                close()
            }
            drawPath(path, color = accent, style = Stroke(width = 1.6.dp.toPx()))
        }
    }
}

@Composable
private fun MenuIcon(onClick: () -> Unit, dim: Float = 1f) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, C.stroke, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // 图标原为深灰小图，染成暖米金才能在深色底上看见
        Image(
            painter = painterResource(R.drawable.ic_menu),
            contentDescription = Lang.t("menu_settings"),
            colorFilter = ColorFilter.tint(C.accent),
            modifier = Modifier.size(18.dp).alpha(dim)
        )
    }
}

@Composable
private fun SubPageBar(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, C.stroke, RoundedCornerShape(20.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_back),
                contentDescription = Lang.t("btn_back"),
                colorFilter = ColorFilter.tint(C.accent),
                modifier = Modifier.width(10.dp).height(18.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            color = C.text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 2.sp
        )
    }
}

/** 侧滑抽屉：从边缘滑入、点遮罩或按返回收起，头部只放身份信息，不堆设置项。 */
@Composable
private fun DrawerContent(onSelect: (Screen) -> Unit) {
    Column(modifier = Modifier.fillMaxHeight().safeDrawingPadding()) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(C.card)
                    .border(1.dp, C.stroke, CircleShape),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = Lang.tx("app_title"),
                    color = C.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 3.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = Lang.tx("about_tagline"),
                    color = C.secondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = C.divider)
        Spacer(Modifier.height(10.dp))
        DrawerItem(R.drawable.ic_repeat, Lang.tx("menu_presets")) { onSelect(Screen.PRESETS) }
        DrawerItem(R.drawable.ic_selection, Lang.tx("menu_settings")) { onSelect(Screen.SETTINGS) }
        DrawerItem(R.drawable.ic_monk, Lang.tx("menu_about")) { onSelect(Screen.ABOUT) }
        Spacer(Modifier.weight(1f))
        HorizontalDivider(thickness = 1.dp, color = C.divider)
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(
                text = "${Lang.tx("about_version")} ${AppInfo.VERSION}",
                color = C.accentDim,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(text = AppInfo.AUTHOR, color = C.secondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun DrawerItem(icon: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(C.accent),
            modifier = Modifier.size(22.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.width(16.dp))
        Text(text = label, color = C.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(text = "›", color = C.accentDim, fontSize = 18.sp)
    }
}

// ---------- 圆环 ----------

@Composable
private fun RingBlock(s: TimerEngine.Ui) {
    val displayMs = if (s.phase == TimerEngine.Phase.PREP || (s.phase == TimerEngine.Phase.PAUSED && s.prepLeftMs > 0L))
        s.prepLeftMs else s.remainingMs
    val stateKey = when (s.phase) {
        TimerEngine.Phase.IDLE -> "state_idle"
        TimerEngine.Phase.PREP -> "state_prep"
        TimerEngine.Phase.RUNNING -> if (s.infinite) "state_infinite" else "state_running"
        TimerEngine.Phase.PAUSED -> "state_paused"
        TimerEngine.Phase.DONE -> "state_done"
    }
    val hint = when {
        s.catchUp -> Lang.t("catchup")
        s.phase == TimerEngine.Phase.DONE ->
            Lang.t("hint_tap_again") + " · " + Lang.minutes(s.durationMin)
        s.phase == TimerEngine.Phase.IDLE -> Lang.t("hint_tap_ring")
        else -> ""
    }

    Box(
        modifier = Modifier.size(ringSize()),
        contentAlignment = Alignment.Center
    ) {
        RingCanvas(s.progress)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable { TimerEngine.mainAction() }
        ) {
            Text(
                text = TimerEngine.formatTime(displayMs),
                color = C.text,
                fontSize = 60.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(text = Lang.t(stateKey), color = C.secondary, fontSize = 13.sp)
            if (hint.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(text = hint, color = C.accentDim, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun RingCanvas(progress: Float) {
    // DrawScope 不是 Composable 上下文，先把色板读成局部变量再进 Canvas
    val accent = C.accent
    val track = C.track
    val dot = C.text
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = 5.dp.toPx()
        val inset = stroke * 2f
        val topLeft = Offset(inset, inset)
        val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)

        if (progress > 0f) {
            drawArc(
                color = accent.copy(alpha = 0.10f),
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke * 5f, cap = StrokeCap.Round)
            )
        }
        drawArc(
            color = track,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke)
        )
        if (progress > 0.001f) {
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            val radius = arcSize.width / 2f
            val cx = topLeft.x + radius
            val cy = topLeft.y + radius
            val angle = Math.toRadians((-90.0 + 360.0 * progress))
            val px = cx + radius * cos(angle).toFloat()
            val py = cy + radius * sin(angle).toFloat()
            drawCircle(color = accent.copy(alpha = 0.22f), radius = stroke * 2.1f, center = Offset(px, py))
            drawCircle(color = dot, radius = stroke * 0.62f, center = Offset(px, py))
        }
    }
}

/** 圆环按最短边与横竖屏收敛：小屏不撑破、横屏不挤掉按钮，常规竖屏保持 268dp 不变。 */
@Composable
private fun ringSize(): Dp {
    val cfg = LocalConfiguration.current
    val shortest = minOf(cfg.screenWidthDp, cfg.screenHeightDp)
    return when {
        cfg.orientation == Configuration.ORIENTATION_LANDSCAPE -> 200.dp
        shortest < 330 -> 190.dp
        shortest < 365 -> 220.dp
        else -> 268.dp
    }
}

// ---------- 按钮 ----------

@Composable
private fun ActionRow(s: TimerEngine.Ui) {
    val mainText = when (s.phase) {
        TimerEngine.Phase.IDLE -> Lang.t("btn_start")
        // 完成态圆环停在 0:00，只写「开始冥想」会让人以为点下去是 0 秒。
        // 把这一次真正会用的时长写进按钮，直接回答「是 0 还是 5 分钟」。
        TimerEngine.Phase.DONE -> Lang.t("btn_again") + " · " + Lang.minutes(s.durationMin)
        TimerEngine.Phase.RUNNING, TimerEngine.Phase.PREP -> Lang.t("btn_pause")
        TimerEngine.Phase.PAUSED -> Lang.t("btn_resume")
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (s.running) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(C.card)
                    .border(1.dp, C.stroke, RoundedCornerShape(28.dp))
                    .clickable { TimerEngine.stop() }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = Lang.t("btn_stop"), color = C.secondary, fontSize = 15.sp)
            }
            Spacer(Modifier.width(12.dp))
        }
        Button(
            onClick = { TimerEngine.mainAction() },
            modifier = Modifier.weight(2f).height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.accent, contentColor = C.bg)
        ) {
            Text(text = mainText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ---------- 预设：顶栏书签 → 底部面板 ----------

/**
 * v2.3 修订（用户反馈：「预设控件挤占时间设置区」）。
 * 选预设是高频，但保存 / 管理是低频配置动作 —— 整块常驻主页等于把两者摆成同一层级。
 * 现在主页只留顶栏一个书签入口；点开是和铃声选择器同款的底部面板：
 * 预设一点即应用并收起，保存 / 管理收在面板里，最近应用的一套高亮「已选」。
 */
@Composable
private fun PresetSheet(
    ctx: Context,
    s: TimerEngine.Ui,
    rev: Int,
    onToast: (String) -> Unit,
    onSave: () -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit
) {
    val list = remember(rev) { PresetStore.all(ctx) }
    var activeId by remember(rev) { mutableStateOf(PresetStore.lastId(ctx)) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun close(after: () -> Unit = {}) {
        scope.launch {
            sheetState.hide()
            onDismiss()
            after()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = C.elevated,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = Lang.t("preset_quick"),
                    color = C.text,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = Lang.t("preset_manage"),
                    color = C.accentDim,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { close { onManage() } }.padding(8.dp)
                )
            }
            Spacer(Modifier.height(10.dp))

            if (list.isEmpty()) {
                Text(text = Lang.t("preset_quick_empty"), color = C.secondary, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
            } else {
                list.forEach { p ->
                    val active = p.id == activeId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (active) C.card else Color.Transparent)
                            .border(
                                1.dp,
                                if (active) C.accentDim else C.stroke,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                if (s.running) {
                                    onToast(Lang.t("preset_running_block"))
                                } else {
                                    TimerEngine.applyPreset(p)
                                    PresetStore.markApplied(ctx, p.id)
                                    activeId = p.id
                                    onToast(Lang.t("preset_applied"))
                                    close()
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = p.name,
                                color = C.text,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = p.summary(),
                                color = C.secondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (active) {
                            Spacer(Modifier.width(8.dp))
                            Text(text = Lang.t("picker_selected"), color = C.accent, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            OutlinedButton(
                onClick = { close { onSave() } },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(23.dp),
                border = BorderStroke(1.dp, C.accentDim),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = C.accent)
            ) {
                Text(text = "+ ${Lang.t("preset_add")}", fontSize = 14.sp)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// ---------- 主页设置卡片：时间在上、铃声在下 ----------

@Composable
private fun SettingsCard(s: TimerEngine.Ui, onPick: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = C.elevated),
        border = BorderStroke(1.dp, C.stroke)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            // v2.3：时长 / 间隔 / 准备比挑铃声更常用，整组上移到卡片顶部，触达更近
            SectionLabel(Lang.t("section_duration"))
            OptionRow(
                // 内测期常驻的短档 1 / 3 分钟。只有一个版本，不做构建分叉：
                // 所有包一致，避免「装了测试包还是正式包」带来的行为偏差。
                // 「滑动可选任意时长」11-09 04:03 已裁决：暂缓，以后再说。
                // 不实现、不排期；保持固定档位，待用户重新提出再单开一轮评估。
                items = listOf(1, 3, 5, 10, 15, 20, 30, 45, 60, 90, 120, -1),
                selected = s.durationMin,
                label = { Lang.minutes(it) },
                onSelect = { TimerEngine.setDuration(it) }
            )

            SectionLabel(Lang.t("section_interval"))
            OptionRow(
                items = listOf(0, 2, 5, 10, 15),
                selected = s.intervalMin,
                label = { if (it == 0) Lang.off() else Lang.minutes(it) },
                onSelect = { TimerEngine.setInterval(it) }
            )

            SectionLabel(Lang.t("section_prep"))
            OptionRow(
                items = listOf(0, 10, 30, 60, 180, 300),
                selected = s.prepSec,
                label = { if (it == 0) Lang.off() else Lang.seconds(it) },
                onSelect = { TimerEngine.setPrep(it) }
            )

            Spacer(Modifier.height(12.dp))
            SettingDivider()

            val sb = SoundCatalog.bell(s.startBell)
            val ib = SoundCatalog.bell(s.intervalBell)
            val eb = SoundCatalog.bell(s.endBell)
            val am = SoundCatalog.ambience(s.ambience)
            SettingRow(Lang.t("label_start_bell"), sb.name, sb.icon, s.startTimes, "start") { onPick("start") }
            SettingDivider()
            SettingRow(Lang.t("label_interval_bell"), ib.name, ib.icon, s.intervalTimes, "interval") { onPick("interval") }
            SettingDivider()
            SettingRow(Lang.t("label_end_bell"), eb.name, eb.icon, s.endTimes, "end") { onPick("end") }
            SettingDivider()
            SettingRow(Lang.t("label_ambience"), am.name, am.icon, null, null) { onPick("ambience") }

            Spacer(Modifier.height(6.dp))
            Text(
                text = Lang.t("settings_bell_times"),
                color = C.accentDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    value: String,
    icon: Int,
    times: Int?,
    timesKey: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(C.card),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                contentScale = ContentScale.Fit
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            color = C.text,
            fontSize = 15.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            color = C.accent,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (times != null && timesKey != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(C.card)
                    .border(1.dp, C.stroke, RoundedCornerShape(10.dp))
                    .clickable { TimerEngine.cycleTimes(timesKey) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = Lang.times(times), color = C.accent, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SettingDivider() {
    HorizontalDivider(thickness = 1.dp, color = C.divider)
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = C.secondary,
        fontSize = 12.sp,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 10.dp)
    )
}

@Composable
private fun OptionRow(
    items: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { v ->
            val on = v == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (on) C.accent else Color.Transparent)
                    .border(1.dp, if (on) C.accent else C.stroke, RoundedCornerShape(20.dp))
                    .clickable { onSelect(v) }
                    .padding(horizontal = 15.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label(v),
                    color = if (on) C.bg else C.secondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// ---------- 声音选择器：反复试听，人工确认才生效 ----------

/**
 * v2.3：从底部滑出的现代选择器。点卡片只试听并高亮，可反复点、反复比较；
 * 只有按「确定」才写回设置，取消 / 下滑 / 按返回都不改动原有选择。
 * 图片区统一为正方形，横图与方图不再参差。
 */
@Composable
private fun SoundPicker(key: String, s: TimerEngine.Ui, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val isAmbience = key == "ambience"
    val pool = if (isAmbience) SoundCatalog.ambiences else SoundCatalog.bells
    val current = when (key) {
        "start" -> s.startBell
        "interval" -> s.intervalBell
        "end" -> s.endBell
        else -> s.ambience
    }
    val title = when (key) {
        "start" -> Lang.t("picker_start")
        "interval" -> Lang.t("picker_interval")
        "end" -> Lang.t("picker_end")
        else -> Lang.t("picker_ambience")
    }
    var selected by remember(key) { mutableStateOf(current) }
    var playing by remember(key) { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun stopPreview() {
        BellPlayer.release()
        AmbiencePlayer.stop()
    }

    fun close() {
        stopPreview()
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = { close() },
        sheetState = sheetState,
        containerColor = C.elevated,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 18.dp)
        ) {
            Text(text = title, color = C.accent, fontSize = 17.sp)
            Spacer(Modifier.height(6.dp))
            Text(text = Lang.t("picker_hint"), color = C.secondary, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(pool, key = { it.id }) { sound ->
                    val chosen = sound.id == selected
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(C.card)
                            .border(
                                width = if (chosen) 1.5.dp else 1.dp,
                                color = if (chosen) C.accent else C.stroke,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                playing = sound.id
                                selected = sound.id
                                if (isAmbience) {
                                    BellPlayer.release()
                                    if (sound.res != 0) AmbiencePlayer.start(ctx, sound.res, 0.5f)
                                    else AmbiencePlayer.stop()
                                } else {
                                    AmbiencePlayer.stop()
                                    if (sound.res != 0) BellPlayer.play(ctx, sound.res, 0.85f)
                                }
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(C.bg)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(sound.icon),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = sound.name,
                                color = if (chosen) C.accent else C.text,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = when {
                                    playing == sound.id -> Lang.t("picker_playing")
                                    chosen -> Lang.t("picker_selected")
                                    else -> " "
                                },
                                color = if (chosen) C.accentDim else C.divider,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                        if (chosen) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(C.accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "✓", color = C.bg, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, C.stroke, RoundedCornerShape(24.dp))
                        .clickable { close() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = Lang.t("cancel"), color = C.secondary, fontSize = 15.sp)
                }
                Box(
                    modifier = Modifier
                        .weight(1.6f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(C.accent)
                        .clickable {
                            TimerEngine.setSound(key, selected)
                            close()
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = Lang.t("btn_apply"),
                        color = C.bg,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ---------- 预设 ----------

@Composable
private fun PresetsPage(
    ctx: Context,
    rev: Int,
    onToast: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: (Preset) -> Unit
) {
    val list = remember(rev) { PresetStore.all(ctx) }
    val s by TimerEngine.ui.collectAsState()

    if (list.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = Lang.t("preset_empty"), color = C.text, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            Text(text = Lang.t("preset_empty_hint"), color = C.secondary, fontSize = 12.sp)
        }
    } else {
        list.forEach { p ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = C.elevated),
                border = BorderStroke(1.dp, C.stroke)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            onToast(
                                if (TimerEngine.applyPreset(p)) Lang.t("preset_applied")
                                else Lang.t("preset_running_block")
                            )
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(SoundCatalog.bell(p.endBell).icon),
                        contentDescription = null,
                        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(C.card),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = p.name, color = C.text, fontSize = 15.sp, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        Text(text = p.summary(), color = C.secondary, fontSize = 12.sp, maxLines = 1)
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable { onDelete(p) },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = Lang.t("btn_delete"),
                            colorFilter = ColorFilter.tint(C.secondary),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(C.card)
            .border(1.dp, C.stroke, RoundedCornerShape(28.dp))
            .clickable(onClick = onSave)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = Lang.t("preset_save"), color = C.accent, fontSize = 15.sp)
    }
    Spacer(Modifier.height(12.dp))
    Text(
        text = Lang.t("preset_max") + if (s.running) " · " + Lang.t("preset_running_block") else "",
        color = C.accentDim,
        fontSize = 11.sp
    )
}

@Composable
private fun SavePresetDialog(ctx: Context, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf(PresetStore.defaultName(TimerEngine.ui.value)) }
    val focus = remember { FocusRequester() }
    // 弹出即聚焦，直接能打字，不让用户先点一下输入框
    LaunchedEffect(Unit) {
        delay(120L)
        runCatching { focus.requestFocus() }
    }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = C.elevated),
            border = BorderStroke(1.dp, C.stroke)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(text = Lang.t("preset_dialog_title"), color = C.accent, fontSize = 16.sp)
                Spacer(Modifier.height(14.dp))
                BasicTextField(
                    value = text,
                    onValueChange = { if (it.length <= 24) text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = C.text, fontSize = 15.sp),
                    cursorBrush = SolidColor(C.accent),
                    modifier = Modifier.focusRequester(focus),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(C.card)
                                .border(1.dp, C.stroke, RoundedCornerShape(12.dp))
                                .padding(14.dp)
                        ) {
                            if (text.isEmpty()) {
                                Text(text = Lang.t("preset_name_hint"), color = C.secondary, fontSize = 15.sp)
                            }
                            inner()
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.align(Alignment.End)) {
                    TextButton(onClick = onDismiss) {
                        Text(text = Lang.t("cancel"), color = C.secondary)
                    }
                    TextButton(onClick = {
                        val name = text.ifBlank { PresetStore.defaultName(TimerEngine.ui.value) }
                        val ok = PresetStore.save(ctx, TimerEngine.snapshot(name))
                        onDone(if (ok) Lang.t("preset_saved") else Lang.t("preset_max"))
                        onDismiss()
                    }) {
                        Text(text = Lang.t("btn_confirm"), color = C.accent)
                    }
                }
            }
        }
    }
}

// ---------- 设置 ----------

@Composable
private fun SettingsPage(ctx: Context, onToast: (String) -> Unit) {
    val cur by Lang.mode.collectAsState()
    val themeMode by Theme.mode.collectAsState()

    SectionLabel(Lang.t("settings_language"))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        listOf(
            Lang.Mode.HANS to Lang.t("lang_hans"),
            Lang.Mode.HANT to Lang.t("lang_hant"),
            Lang.Mode.EN to Lang.t("lang_en")
        ).forEach { (mode, label) ->
            val on = cur == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) C.accent else C.elevated)
                    .border(1.dp, if (on) C.accent else C.stroke, RoundedCornerShape(16.dp))
                    .clickable { Lang.set(ctx, mode) }
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (on) C.bg else C.text,
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = Lang.t("settings_language_note"),
            color = C.secondary,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        // 「跟随系统」只占说明行右侧一小块，不新增常驻控件层级
        Text(
            text = "\u21ba " + Lang.t("lang_follow_system"),
            color = C.accent,
            fontSize = 12.sp,
            modifier = Modifier.clickable {
                Lang.followSystem(ctx)
                onToast(Lang.t("lang_follow_system"))
            }.padding(6.dp)
        )
    }
    Spacer(Modifier.height(28.dp))
    HorizontalDivider(thickness = 1.dp, color = C.divider)
    Spacer(Modifier.height(24.dp))

    SectionLabel(Lang.t("settings_theme"))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        listOf(
            Theme.Mode.SYSTEM to Lang.t("theme_system"),
            Theme.Mode.DARK to Lang.t("theme_dark"),
            Theme.Mode.LIGHT to Lang.t("theme_light")
        ).forEach { (mode, label) ->
            val on = themeMode == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) C.accent else C.elevated)
                    .border(1.dp, if (on) C.accent else C.stroke, RoundedCornerShape(16.dp))
                    .clickable { Theme.set(ctx, mode) }
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (on) C.bg else C.text,
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(text = Lang.t("theme_note"), color = C.secondary, fontSize = 12.sp)
    Spacer(Modifier.height(28.dp))
    HorizontalDivider(thickness = 1.dp, color = C.divider)
    Spacer(Modifier.height(24.dp))

    // 计时通知自检：锁屏与状态栏能不能看到倒计时，取决于系统这边两三个开关。
    // 这里直接读出真实状态，并且整块可点，一键跳到本应用的通知设置。
    SectionLabel(Lang.t("settings_notify"))
    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    val notifyOn = nm?.areNotificationsEnabled() == true
    val channelOn = nm?.getNotificationChannel(TimerService.CHANNEL_ALARM)
        ?.let { it.importance != NotificationManager.IMPORTANCE_NONE } ?: true
    val liveOk = Build.VERSION.SDK_INT < 36 ||
        runCatching { nm?.canPostPromotedNotifications() }.getOrNull() == true
    val status = when {
        !notifyOn -> Lang.t("notify_off")
        !channelOn -> Lang.t("notify_channel_off")
        else -> Lang.t("notify_on")
    }
    // 授权框在页面里再要一次；被永久拒绝就只能去系统设置
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (!granted) openNotificationSettings(ctx) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable {
                if (!notifyOn && Build.VERSION.SDK_INT >= 33) {
                    runCatching { permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        .onFailure { openNotificationSettings(ctx) }
                } else {
                    openNotificationSettings(ctx)
                }
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = C.card),
        border = BorderStroke(1.dp, if (notifyOn && channelOn) C.stroke else C.accentDim)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = status,
                    color = if (notifyOn && channelOn) C.text else C.accent,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (!liveOk) Lang.t("notify_live_off") else Lang.t("notify_hint"),
                    color = C.secondary,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(text = "\u203a", color = C.accentDim, fontSize = 20.sp)
        }
    }
    Spacer(Modifier.height(28.dp))
    HorizontalDivider(thickness = 1.dp, color = C.divider)
    Spacer(Modifier.height(24.dp))
    Text(text = Lang.t("settings_bell_times"), color = C.secondary, fontSize = 13.sp)
}

/** 跳到本应用的通知设置；个别 ROM 没有该页面时退回应用详情页。 */
private fun openNotificationSettings(ctx: Context) {
    val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { ctx.startActivity(i) }.isSuccess) return
    runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.parse("package:" + ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

// ---------- 关于 ----------

@Composable
private fun AboutPage(ctx: Context, onToast: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(C.elevated)
                .border(1.dp, C.stroke, CircleShape),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(18.dp))
        Text(text = Lang.t("app_title"), color = C.text, fontSize = 21.sp, fontWeight = FontWeight.Light, letterSpacing = 4.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            text = Lang.t("about_tagline"),
            color = C.secondary,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 18.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(text = "${Lang.t("about_version")} ${AppInfo.VERSION}", color = C.accentDim, fontSize = 12.sp)
    }
    Spacer(Modifier.height(22.dp))

    // v2.3 修订：作者 / 邮箱 / 微信 / 公众号合成一张 2×2 信息栏，
    // 从原来的四张独立卡片压成一栏，省出的高度让打赏入口留在首屏。
    ContactGrid(ctx, onToast)
    Spacer(Modifier.height(14.dp))

    // v2.3 打赏：按用户决定只做「提示添加微信打赏」，点一下复制微信号并唤起微信
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable {
                AppInfo.copy(ctx, AppInfo.WECHAT)
                val opened = AppInfo.openWechat(ctx)
                onToast(if (opened) Lang.t("donate_hint") else Lang.t("donate_open_failed"))
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = C.card),
        border = BorderStroke(1.dp, C.accentDim)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = Lang.t("donate_title"), color = C.accent, fontSize = 15.sp)
                Spacer(Modifier.height(5.dp))
                Text(text = Lang.t("donate_body"), color = C.secondary, fontSize = 12.sp)
                Spacer(Modifier.height(7.dp))
                Text(text = AppInfo.WECHAT, color = C.text, fontSize = 13.sp, letterSpacing = 1.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text(text = "›", color = C.accent, fontSize = 22.sp)
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(text = Lang.t("donate_note"), color = C.accentDim, fontSize = 11.sp)
    Spacer(Modifier.height(20.dp))

    AboutRow(
        Lang.t("about_repo"),
        if (AppInfo.repoIsUrl) AppInfo.REPO else Lang.t("about_repo_pending"),
        if (AppInfo.repoIsUrl) ({ AppInfo.repo(ctx) }) else null
    )
    Spacer(Modifier.height(8.dp))

    AboutBlock(Lang.t("about_license"), Lang.t("about_license_body"))
    AboutBlock(Lang.t("about_privacy"), Lang.t("about_privacy_body"))
    AboutBlock(Lang.t("about_assets"), Lang.t("about_assets_body"))
    Spacer(Modifier.height(8.dp))
    Text(text = Lang.t("about_offline_note"), color = C.accentDim, fontSize = 11.sp)
    Spacer(Modifier.height(22.dp))
    Text(text = Lang.t("about_custom"), color = C.secondary, fontSize = 12.sp)
    Spacer(Modifier.height(6.dp))
    Text(
        text = Lang.t("about_custom_body"),
        color = C.accentDim,
        fontSize = 11.sp,
        lineHeight = 17.sp
    )
}

/** 一栏放下四项联系信息：2×2 紧凑网格，点某一项仍保留原来的复制 / 邮件动作。 */
@Composable
private fun ContactGrid(ctx: Context, onToast: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = C.elevated),
        border = BorderStroke(1.dp, C.stroke)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(text = Lang.t("about_contact"), color = C.secondary, fontSize = 11.sp)
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                ContactCell(Lang.t("about_author"), AppInfo.AUTHOR, null)
                ContactCell(Lang.t("about_email"), AppInfo.EMAIL) {
                    if (!AppInfo.mail(ctx)) onToast(AppInfo.EMAIL)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                ContactCell(Lang.t("about_wechat"), AppInfo.WECHAT) {
                    if (AppInfo.copy(ctx, AppInfo.WECHAT)) onToast(Lang.t("copied"))
                }
                ContactCell(Lang.t("about_official"), AppInfo.OFFICIAL_ACCOUNT) {
                    if (AppInfo.copy(ctx, AppInfo.OFFICIAL_ACCOUNT)) onToast(Lang.t("copied"))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(text = Lang.t("about_contact_body"), color = C.accentDim, fontSize = 11.sp)
        }
    }
}

@Composable
private fun RowScope.ContactCell(label: String, value: String, onClick: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 2.dp)
    ) {
        Text(text = label, color = C.secondary, fontSize = 10.sp)
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            color = C.accent,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AboutRow(label: String, value: String, onClick: (() -> Unit)?) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = C.elevated),
        border = BorderStroke(1.dp, C.stroke)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 13.dp)
        ) {
            Text(text = label, color = C.secondary, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Text(text = value, color = C.accent, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AboutBlock(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(text = title, color = C.text, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Text(text = body, color = C.secondary, fontSize = 12.sp, lineHeight = 19.sp)
    }
    HorizontalDivider(thickness = 1.dp, color = C.divider)
}
