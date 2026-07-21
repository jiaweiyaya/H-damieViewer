package com.jiaweiya.hdamieviewer.pages

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiaweiya.hdamieviewer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import androidx.compose.foundation.LocalIndication

// 补回缺少的常量及转换函数
val colorOptions = listOf(
    0xFF9E77ED, 0xFFFF0000, 0xFFE91E63, 0xFF9C27B0,
    0xFF673AB7, 0xFF3F51B5, 0xFF03A9F4, 0xFF00BCD4,
    0xFF009688, 0xFF4CAF50, 0xFF8BC34A, 0xFFCDDC39,
    0xFFFFEB3B, 0xFFFFC107, 0xFFFF9800, 0xFFFF5722
).map { it.toLong() }

val darkColorOptions = listOf(
    0xFFD0BCFF, 0xFFF2B8B5, 0xFFFFB1C8, 0xFFD8B4F8,
    0xFFC5BAFF, 0xFF9ECAFF, 0xFF9EEDFF, 0xFFA7F1FF,
    0xFF80CBC4, 0xFFA5D6A7, 0xFFC5E1A5, 0xFFE6EE9C,
    0xFFFFF59D, 0xFFFFE082, 0xFFFFCC80, 0xFFFFAB91
).map { it.toLong() }

fun resolveThemeColor(color: Long, isDark: Boolean): Long {
    val lightIndex = colorOptions.indexOf(color)
    if (isDark && lightIndex != -1) {
        return darkColorOptions[lightIndex]
    }
    val darkIndex = darkColorOptions.indexOf(color)
    if (!isDark && darkIndex != -1) {
        return colorOptions[darkIndex]
    }
    return color
}

// 应用图标实体定义与数据映射
data class AppIconData(val id: Int, val alias: String, val iconRes: Int, val name: String)
val appIconsList = listOf(
    AppIconData(1, "com.jiaweiya.hdamieviewer.Alias1", R.drawable.app_icon, "默认图标"),
    AppIconData(2, "com.jiaweiya.hdamieviewer.Alias2", R.drawable.app_icon2, "FlowCourse"),
    AppIconData(3, "com.jiaweiya.hdamieviewer.Alias3", R.drawable.app_icon3, "JM地狱！"),
    AppIconData(4, "com.jiaweiya.hdamieviewer.Alias4", R.drawable.app_icon4, "待定"),
    AppIconData(5, "com.jiaweiya.hdamieviewer.Alias5", R.drawable.app_icon5, "待定"),
    AppIconData(6, "com.jiaweiya.hdamieviewer.Alias6", R.drawable.app_icon6, "待定")
)

fun changeAppIcon(context: Context, targetAlias: String) {
    val pm = context.packageManager
    appIconsList.forEach { iconData ->
        val state = if (iconData.alias == targetAlias) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        try {
            pm.setComponentEnabledSetting(
                ComponentName(context, iconData.alias),
                state,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    Toast.makeText(context, "图标更换成功，可能需要几秒钟在桌面上生效", Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(
    themeMode: Int,
    onThemeChange: (Int) -> Unit,
    themeColor: Long,
    onThemeColorChange: (Long) -> Unit,
    updateChannel: Int,
    onUpdateChannelChange: (Int) -> Unit,
    autoCheckUpdate: Boolean,
    onAutoCheckUpdateChange: (Boolean) -> Unit,
    onManualCheckUpdate: () -> Unit,
    onNavigateToExportBackup: () -> Unit,
    onImportBackupClick: () -> Unit,
    onNavigateToAbout: () -> Unit,
    playerType: Int,
    onPlayerTypeChange: (Int) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var showThemeDialog by remember { mutableStateOf(false) }
    var showThemeColorDialog by remember { mutableStateOf(false) }
    var showChannelDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showAppIconDialog by remember { mutableStateOf(false) }
    var showPlayerDialog by remember { mutableStateOf(false) }

    var cacheSizeStr by remember { mutableStateOf("计算中...") }

    var currentAppVersion by remember { mutableStateOf("获取中...") }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            currentAppVersion = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0" } catch (e: Exception) { "1.0.0" }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val size = getTotalCacheSize(context)
            cacheSizeStr = formatCacheSize(size)
        }
    }

    val themeOptions = listOf("跟随系统", "浅色模式", "深色模式")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            Row(
                modifier = Modifier.offset(x = 4.dp, y = 6.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FloatingScaleButton(
                    imageRes = R.drawable.issue, // 请确保你在 drawable 目录有这三个图标资源
                    text = "反馈问题",
                    onClick = { Toast.makeText(context, "跳转反馈反馈...", Toast.LENGTH_SHORT).show() }
                )
                FloatingScaleButton(
                    imageRes = R.drawable.user_agreement,
                    text = "用户协议",
                    onClick = { Toast.makeText(context, "跳转用户协议...", Toast.LENGTH_SHORT).show() }
                )
                FloatingScaleButton(
                    imageRes = R.drawable.jiaweiya_icon,
                    text = "关于此应用",
                    onClick = onNavigateToAbout
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
        ) {
            // 1. 主题与外观分区
            item {
                ScrollFadeIn {
                    Text(
                        text = "主题与外观",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showThemeColorDialog = true }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "切换主题色",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(6.dp)
                                )
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showThemeColorDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(3.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(themeColor))
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showThemeDialog = true }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "切换主题",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val themeIcons = listOf(
                                Icons.Default.BrightnessAuto,
                                Icons.Default.WbSunny,
                                Icons.Default.Brightness3
                            )
                            Icon(
                                imageVector = themeIcons[themeMode],
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                themeOptions[themeMode],
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 应用图标切换配置栏
                    AppIconSettingsRow(onOpenDialog = { showAppIconDialog = true })

                    // 播放器选择 Row（已正确移动到第一分区内部，不再直接暴露在 LazyColumn 作用域中）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPlayerDialog = true }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("播放器选择", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val playerNames = listOf("ExoPlayer (默认)", "MpvPlayer (极简)", "MediaPlayer (系统)")
                            Icon(
                                imageVector = Icons.Default.Slideshow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(playerNames[playerType], fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // 2. 应用更新分区
            item {
                ScrollFadeIn {
                    Text(
                        text = "应用更新",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showChannelDialog = true }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "更新通道",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (updateChannel == 1) "CL频道 (预览版)" else "正式版频道",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onManualCheckUpdate() }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "立即检查更新",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "当前版本 $currentAppVersion",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "每天首次打开时检查更新",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(checked = autoCheckUpdate, onCheckedChange = onAutoCheckUpdateChange)
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // 3. 其他分区
            item {
                ScrollFadeIn {
                    Text(
                        text = "其他",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                    )

                    SettingsRow(
                        title = "导出应用数据",
                        subtitle = "选择性导出系统配置、备份信息为加密文件",
                        onClick = onNavigateToExportBackup,
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "进入",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )

                    SettingsRow(
                        title = "导入应用数据",
                        subtitle = "从外部加密备份文件选择性恢复或合并配置",
                        onClick = onImportBackupClick,
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "进入",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )

                    SettingsRow(
                        title = "清除缓存",
                        subtitle = "当前缓存占用 $cacheSizeStr 的存储空间",
                        onClick = { showClearCacheDialog = true },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "进入",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
                }
            }

            item {
                Spacer(modifier = Modifier.height(120.dp))
            }
        }

        // 弹窗已安全移至外层
        if (showPlayerDialog) {
            PlayerSelectionDialog(
                currentPlayer = playerType,
                onDismiss = { showPlayerDialog = false },
                onSave = { newPlayer ->
                    onPlayerTypeChange(newPlayer)
                    showPlayerDialog = false
                }
            )
        }
    }

    val isSystemDark = isSystemInDarkTheme()
    val isAppDark = themeMode == 2 || (themeMode == 0 && isSystemDark)
    val activeColorOptions = if (isAppDark) darkColorOptions else colorOptions

    if (showThemeColorDialog) {
        ColorSelectionDialog(
            currentColor = themeColor,
            colorOptions = activeColorOptions,
            onDismiss = { showThemeColorDialog = false },
            onSave = {
                onThemeColorChange(it)
                showThemeColorDialog = false
            }
        )
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = themeMode,
            onDismiss = { showThemeDialog = false },
            onSave = { newTheme ->
                onThemeChange(newTheme)
                showThemeDialog = false
            }
        )
    }

    if (showChannelDialog) {
        ChannelSelectionDialog(
            currentChannel = updateChannel,
            onDismiss = { showChannelDialog = false },
            onSave = { newChannel ->
                onUpdateChannelChange(newChannel)
                showChannelDialog = false
            }
        )
    }

    if (showAppIconDialog) {
        val sharedPrefs = context.getSharedPreferences("HDAmieViewerDB", Context.MODE_PRIVATE)
        val currentIconId = sharedPrefs.getInt("app_icon_id", 1)
        IconSelectionDialog(
            currentId = currentIconId,
            onDismiss = { showAppIconDialog = false },
            onSave = { newIcon ->
                sharedPrefs.edit().putInt("app_icon_id", newIcon.id).commit()
                changeAppIcon(context, newIcon.alias)
                showAppIconDialog = false
            }
        )
    }

    if (showClearCacheDialog) {
        val coroutineScope = rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("确认清除缓存？", fontWeight = FontWeight.Bold) },
            text = { Text("清除缓存将删除应用运行中产生的临时文件。这不会影响你的任何配置。确认清除吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearCacheDialog = false
                        coroutineScope.launch {
                            val success = withContext(Dispatchers.IO) { clearAppCache(context) }
                            if (success) {
                                Toast.makeText(context, "缓存清除成功", Toast.LENGTH_SHORT).show()
                                val newSize = withContext(Dispatchers.IO) { getTotalCacheSize(context) }
                                cacheSizeStr = formatCacheSize(newSize)
                            }
                        }
                    }
                ) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) { Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        )
    }
}

// 弹性物理反馈悬浮按钮组件
@Composable
fun FloatingScaleButton(
    imageRes: Int,
    text: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "floating_button_scale"
    )

    Column(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = text,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(13.dp))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AppIconSettingsRow(onOpenDialog: () -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("HDAmieViewerDB", Context.MODE_PRIVATE)

    var currentIconId by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            currentIconId = sharedPrefs.getInt("app_icon_id", 1)
        }
    }

    val currentIconData = appIconsList.find { it.id == currentIconId } ?: appIconsList.first()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDialog() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("更换应用图标", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                painter = painterResource(id = currentIconData.iconRes),
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(5.dp))
            )
            Text(currentIconData.name, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun IconSelectionDialog(currentId: Int, onDismiss: () -> Unit, onSave: (AppIconData) -> Unit) {
    var selectedId by remember { mutableIntStateOf(currentId) }
    val itemBoundsInRoot = remember { mutableStateMapOf<Int, Rect>() }
    var boxBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current
    val context = LocalContext.current
    var showConfirmDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择图标", fontWeight = FontWeight.Bold) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords -> boxBoundsInRoot = coords.boundsInRoot() }
            ) {
                val targetItemRoot = itemBoundsInRoot[selectedId] ?: Rect.Zero
                val targetRelative = if (targetItemRoot != Rect.Zero && boxBoundsInRoot != Rect.Zero) {
                    targetItemRoot.translate(-boxBoundsInRoot.left, -boxBoundsInRoot.top)
                } else Rect.Zero

                if (targetRelative != Rect.Zero) {
                    val padding = 8.dp
                    val paddingPx = with(density) { padding.toPx() }
                    val animSpec = spring<Float>(dampingRatio = 0.65f, stiffness = 400f)

                    val animLeft by animateFloatAsState(targetRelative.left - paddingPx, animSpec, label = "X")
                    val animTop by animateFloatAsState(targetRelative.top - paddingPx, animSpec, label = "Y")
                    val animWidth by animateFloatAsState(targetRelative.width + paddingPx * 2, animSpec, label = "W")
                    val animHeight by animateFloatAsState(targetRelative.height + paddingPx * 2, animSpec, label = "H")

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset { IntOffset(animLeft.roundToInt(), animTop.roundToInt()) }
                            .size(with(density) { animWidth.toDp() }, with(density) { animHeight.toDp() })
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val chunkedIcons = appIconsList.chunked(3)
                    chunkedIcons.forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            rowItems.forEach { item ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .onGloballyPositioned { coords -> itemBoundsInRoot[item.id] = coords.boundsInRoot() }
                                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                            selectedId = item.id
                                        }
                                        .width(60.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = item.iconRes),
                                        contentDescription = item.name,
                                        modifier = Modifier.size(60.dp).clip(RoundedCornerShape(13.dp))
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.name,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val hasChanged = selectedId != currentId
            val animatedContainerColor by animateColorAsState(
                targetValue = if (hasChanged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                animationSpec = tween(300), label = "btnBg"
            )
            Button(
                onClick = { showConfirmDialog = true },
                enabled = hasChanged,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = animatedContainerColor)
            ) {
                Text(text = "保存并应用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    )

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("确认更换？", fontWeight = FontWeight.Bold) },
            text = { Text("更换应用图标后，应用将自动重启以刷新桌面缓存。是否确认更换？") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onSave(appIconsList.first { it.id == selectedId })
                        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        if (intent != null) {
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            context.startActivity(intent)
                            Runtime.getRuntime().exit(0)
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("确认重启")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        )
    }
}

// 补回条目布局与 Dialog 组件
@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(16.dp))
            trailingContent()
        }
    }
}

@Composable
fun ColorSelectionDialog(
    currentColor: Long,
    colorOptions: List<Long>,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit
) {
    var selectedColor by remember { mutableLongStateOf(currentColor) }
    val itemBoundsInRoot = remember { mutableStateMapOf<Long, Rect>() }
    var boxBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择颜色", fontWeight = FontWeight.Bold) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .onGloballyPositioned { coords -> boxBoundsInRoot = coords.boundsInRoot() }
            ) {
                val targetItemRoot = itemBoundsInRoot[selectedColor] ?: Rect.Zero
                val targetRelative = if (targetItemRoot != Rect.Zero && boxBoundsInRoot != Rect.Zero) {
                    targetItemRoot.translate(-boxBoundsInRoot.left, -boxBoundsInRoot.top)
                } else Rect.Zero

                if (targetRelative != Rect.Zero) {
                    val padding = 8.dp
                    val paddingPx = with(density) { padding.toPx() }
                    val animSpec = spring<Float>(dampingRatio = 0.65f, stiffness = 400f)

                    val animLeft by animateFloatAsState(targetRelative.left - paddingPx, animSpec, label = "X")
                    val animTop by animateFloatAsState(targetRelative.top - paddingPx, animSpec, label = "Y")
                    val animWidth by animateFloatAsState(targetRelative.width + paddingPx * 2, animSpec, label = "W")
                    val animHeight by animateFloatAsState(targetRelative.height + paddingPx * 2, animSpec, label = "H")

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset { IntOffset(animLeft.roundToInt(), animTop.roundToInt()) }
                            .size(with(density) { animWidth.toDp() }, with(density) { animHeight.toDp() })
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    val chunkedColors = colorOptions.chunked(4)
                    chunkedColors.forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            rowItems.forEach { colorVal ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .onGloballyPositioned { coords -> itemBoundsInRoot[colorVal] = coords.boundsInRoot() }
                                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                            selectedColor = colorVal
                                        }
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(colorVal)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (currentColor == colorVal) {
                                        Icon(Icons.Default.Check, contentDescription = "选中", tint = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val hasChanged = selectedColor != currentColor
            Button(
                onClick = { onSave(selectedColor) },
                enabled = hasChanged,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "保存并应用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
fun ThemeSelectionDialog(
    currentTheme: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var selectedTheme by remember { mutableIntStateOf(currentTheme) }
    val itemBoundsInRoot = remember { mutableStateMapOf<Int, Rect>() }
    var boxBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current

    val themeOptions = listOf(
        Triple("跟随系统", Icons.Default.BrightnessAuto, 0),
        Triple("浅色模式", Icons.Default.WbSunny, 1),
        Triple("深色模式", Icons.Default.Brightness3, 2)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择主题", fontWeight = FontWeight.Bold) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .onGloballyPositioned { coords -> boxBoundsInRoot = coords.boundsInRoot() }
            ) {
                val targetItemRoot = itemBoundsInRoot[selectedTheme] ?: Rect.Zero
                val targetRelative = if (targetItemRoot != Rect.Zero && boxBoundsInRoot != Rect.Zero) {
                    targetItemRoot.translate(-boxBoundsInRoot.left, -boxBoundsInRoot.top)
                } else Rect.Zero

                if (targetRelative != Rect.Zero) {
                    val animSpec = spring<Float>(dampingRatio = 0.65f, stiffness = 400f)
                    val animLeft by animateFloatAsState(targetRelative.left, animSpec, label = "X")
                    val animTop by animateFloatAsState(targetRelative.top, animSpec, label = "Y")
                    val animWidth by animateFloatAsState(targetRelative.width, animSpec, label = "W")
                    val animHeight by animateFloatAsState(targetRelative.height, animSpec, label = "H")

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset { IntOffset(animLeft.roundToInt(), animTop.roundToInt()) }
                            .size(with(density) { animWidth.toDp() }, with(density) { animHeight.toDp() })
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    themeOptions.forEach { (title, icon, index) ->
                        val isSelected = selectedTheme == index
                        val itemBgColor by animateColorAsState(
                            targetValue = if (isSelected) Color.Transparent
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            animationSpec = tween(300),
                            label = "itemBgAnim"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .onGloballyPositioned { coords -> itemBoundsInRoot[index] = coords.boundsInRoot() }
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    selectedTheme = index
                                }
                                .clip(RoundedCornerShape(12.dp))
                                .background(itemBgColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = title,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val hasChanged = selectedTheme != currentTheme
            Button(
                onClick = { onSave(selectedTheme) },
                enabled = hasChanged,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "保存并应用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
fun ChannelSelectionDialog(
    currentChannel: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var selectedChannel by remember { mutableIntStateOf(currentChannel) }
    val itemBoundsInRoot = remember { mutableStateMapOf<Int, Rect>() }
    var boxBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current

    val channelOptions = listOf(
        Triple("正式版频道", Icons.Default.Check, 0),
        Triple("CL频道 (预览版)", Icons.Default.Build, 1)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择更新通道", fontWeight = FontWeight.Bold) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .onGloballyPositioned { coords -> boxBoundsInRoot = coords.boundsInRoot() }
            ) {
                val targetItemRoot = itemBoundsInRoot[selectedChannel] ?: Rect.Zero
                val targetRelative = if (targetItemRoot != Rect.Zero && boxBoundsInRoot != Rect.Zero) {
                    targetItemRoot.translate(-boxBoundsInRoot.left, -boxBoundsInRoot.top)
                } else Rect.Zero

                if (targetRelative != Rect.Zero) {
                    val animSpec = spring<Float>(dampingRatio = 0.65f, stiffness = 400f)
                    val animLeft by animateFloatAsState(targetRelative.left, animSpec, label = "X")
                    val animTop by animateFloatAsState(targetRelative.top, animSpec, label = "Y")
                    val animWidth by animateFloatAsState(targetRelative.width, animSpec, label = "W")
                    val animHeight by animateFloatAsState(targetRelative.height, animSpec, label = "H")

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset { IntOffset(animLeft.roundToInt(), animTop.roundToInt()) }
                            .size(with(density) { animWidth.toDp() }, with(density) { animHeight.toDp() })
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    channelOptions.forEach { (title, icon, index) ->
                        val isSelected = selectedChannel == index
                        val itemBgColor by animateColorAsState(
                            targetValue = if (isSelected) Color.Transparent
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            animationSpec = tween(300),
                            label = "itemBgAnim"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .onGloballyPositioned { coords -> itemBoundsInRoot[index] = coords.boundsInRoot() }
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    selectedChannel = index
                                }
                                .clip(RoundedCornerShape(12.dp))
                                .background(itemBgColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = title,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val hasChanged = selectedChannel != currentChannel
            Button(
                onClick = { onSave(selectedChannel) },
                enabled = hasChanged,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "保存并应用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
fun ScrollFadeIn(content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(350),
        label = "scroll_fade_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
fun PlayerSelectionDialog(
    currentPlayer: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var selectedPlayer by remember { mutableIntStateOf(currentPlayer) }
    val itemBoundsInRoot = remember { mutableStateMapOf<Int, Rect>() }
    var boxBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current

    val playerOptions = listOf(
        Triple("ExoPlayer (系统默认)", Icons.Default.PlayCircle, 0),
        Triple("MpvPlayer (极简美化)", Icons.Default.Slideshow, 1),
        Triple("MediaPlayer (系统原生)", Icons.Default.Tv, 2)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择默认播放器", fontWeight = FontWeight.Bold) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .onGloballyPositioned { coords -> boxBoundsInRoot = coords.boundsInRoot() }
            ) {
                val targetItemRoot = itemBoundsInRoot[selectedPlayer] ?: Rect.Zero
                val targetRelative = if (targetItemRoot != Rect.Zero && boxBoundsInRoot != Rect.Zero) {
                    targetItemRoot.translate(-boxBoundsInRoot.left, -boxBoundsInRoot.top)
                } else Rect.Zero

                if (targetRelative != Rect.Zero) {
                    val animSpec = spring<Float>(dampingRatio = 0.65f, stiffness = 400f)
                    val animLeft by animateFloatAsState(targetRelative.left, animSpec, label = "X")
                    val animTop by animateFloatAsState(targetRelative.top, animSpec, label = "Y")
                    val animWidth by animateFloatAsState(targetRelative.width, animSpec, label = "W")
                    val animHeight by animateFloatAsState(targetRelative.height, animSpec, label = "H")

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset { IntOffset(animLeft.roundToInt(), animTop.roundToInt()) }
                            .size(with(density) { animWidth.toDp() }, with(density) { animHeight.toDp() })
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    playerOptions.forEach { (title, icon, index) ->
                        val isSelected = selectedPlayer == index
                        val itemBgColor by animateColorAsState(
                            targetValue = if (isSelected) Color.Transparent
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            animationSpec = tween(300),
                            label = "itemBgAnim"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .onGloballyPositioned { coords -> itemBoundsInRoot[index] = coords.boundsInRoot() }
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    selectedPlayer = index
                                }
                                .clip(RoundedCornerShape(12.dp))
                                .background(itemBgColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = title,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val hasChanged = selectedPlayer != currentPlayer
            Button(
                onClick = { onSave(selectedPlayer) },
                enabled = hasChanged,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "保存并应用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

private fun getFolderSize(file: java.io.File?): Long {
    if (file == null || !file.exists()) return 0L
    if (file.isFile) return file.length()
    var size = 0L
    val files = file.listFiles()
    if (files != null) {
        for (f in files) { size += getFolderSize(f) }
    }
    return size
}

private fun getTotalCacheSize(context: Context): Long {
    var size = getFolderSize(context.cacheDir)
    val extCacheDir = context.externalCacheDir
    if (extCacheDir != null) { size += getFolderSize(extCacheDir) }
    return size
}

private fun deleteDirContent(file: java.io.File?): Boolean {
    if (file == null || !file.exists()) return false
    if (file.isDirectory) {
        val children = file.listFiles()
        if (children != null) {
            for (child in children) { deleteDirContent(child) }
        }
    }
    return file.delete()
}

private fun clearAppCache(context: Context): Boolean {
    var success = true
    try {
        val cacheFiles = context.cacheDir.listFiles()
        if (cacheFiles != null) {
            for (f in cacheFiles) { success = success && deleteDirContent(f) }
        }
        val extCacheDir = context.externalCacheDir
        if (extCacheDir != null) {
            val extCacheFiles = extCacheDir.listFiles()
            if (extCacheFiles != null) {
                for (f in extCacheFiles) { success = success && deleteDirContent(f) }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        success = false
    }
    return success
}

private fun formatCacheSize(sizeInBytes: Long): String {
    val sizeInKiB = sizeInBytes.toDouble() / 1024.0
    if (sizeInKiB < 1024.0) { return String.format("%.3f KiB", sizeInKiB) }
    val sizeInMiB = sizeInKiB / 1024.0
    return String.format("%.3f MiB", sizeInMiB)
}