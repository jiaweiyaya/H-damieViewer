package com.jiaweiya.hdamieviewer.pages

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import android.content.pm.ActivityInfo
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 全屏模式配置页面
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenSwipeSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? android.app.Activity }

    // 强行锁定为横屏全屏，退出恢复
    DisposableEffect(Unit) {
        activity?.let { act ->
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val sp = remember { context.getSharedPreferences("HDAmieViewerDB", Context.MODE_PRIVATE) }

    // 读取用户设置的全屏左右边距避让
    val leftMarginDp = remember { sp.getInt("fullscreen_margin_left", 0).dp }
    val rightMarginDp = remember { sp.getInt("fullscreen_margin_right", 0).dp }

    var isEnabled by remember { mutableStateOf(sp.getBoolean("fs_swipe_enabled", true)) }
    var isProportional by remember { mutableStateOf(sp.getBoolean("fs_swipe_proportional", false)) }

    var propSpeed by remember { mutableFloatStateOf(sp.getFloat("fs_swipe_speed_prop", 0.1f)) }
    var absSpeed by remember { mutableFloatStateOf(sp.getFloat("fs_swipe_speed_abs", 1.0f)) }

    var totalDurationMs by remember { mutableLongStateOf(20 * 60 * 1000L) }
    var currentPosMs by remember { mutableLongStateOf((totalDurationMs * 0.5f).toLong()) }
    var showDurationDialog by remember { mutableStateOf(false) }
    var showSpeedInputDialog by remember { mutableStateOf(false) }

    fun saveSettings(enabled: Boolean, prop: Boolean, pSpeed: Float, aSpeed: Float) {
        sp.edit()
            .putBoolean("fs_swipe_enabled", enabled)
            .putBoolean("fs_swipe_proportional", prop)
            .putFloat("fs_swipe_speed_prop", pSpeed)
            .putFloat("fs_swipe_speed_abs", aSpeed)
            .apply()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶栏（避开页边距）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(start = leftMarginDp + 8.dp, end = rightMarginDp + 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("全屏滑动进度设置", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            // 极简紧凑型控制面板区域（两行布局，避开页边距）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E))
                    .padding(start = leftMarginDp + 12.dp, end = rightMarginDp + 12.dp, top = 6.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 第一行：合并两个开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("开启滑动控制", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = {
                                isEnabled = it
                                saveSettings(isEnabled, isProportional, propSpeed, absSpeed)
                            },
                            modifier = Modifier.graphicsLayer { scaleX = 0.75f; scaleY = 0.75f }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("比例控制 (基于总时长%)", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = isProportional,
                            enabled = isEnabled,
                            onCheckedChange = {
                                isProportional = it
                                saveSettings(isEnabled, isProportional, propSpeed, absSpeed)
                            },
                            modifier = Modifier.graphicsLayer { scaleX = 0.75f; scaleY = 0.75f }
                        )
                    }
                }

                // 第二行：标题 + 滑条 + 末尾数值
                val currentSpeedVal = if (isProportional) propSpeed else absSpeed
                val speedText = if (isProportional) String.format("每 10px -> %.2f%%", currentSpeedVal)
                else String.format("每 10px -> %.1fs", currentSpeedVal)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("调节速度（点击值可输入）", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)

                    Box(modifier = Modifier.weight(1f)) {
                        if (isProportional) {
                            Slider(
                                value = propSpeed,
                                onValueChange = {
                                    propSpeed = it
                                    saveSettings(isEnabled, isProportional, propSpeed, absSpeed)
                                },
                                valueRange = 0.01f..20.0f,
                                enabled = isEnabled
                            )
                        } else {
                            Slider(
                                value = absSpeed,
                                onValueChange = {
                                    absSpeed = it
                                    saveSettings(isEnabled, isProportional, propSpeed, absSpeed)
                                },
                                valueRange = 0.1f..60.0f,
                                enabled = isEnabled
                            )
                        }
                    }

                    Text(
                        text = speedText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(enabled = isEnabled) { showSpeedInputDialog = true }
                    )
                }
            }

            // 试滑体验空白大区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(isEnabled, isProportional, propSpeed, absSpeed, totalDurationMs) {
                        if (!isEnabled) return@pointerInput
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            val units10px = dragAmount / 10.0f
                            val deltaMs = if (isProportional) {
                                (units10px * (propSpeed / 100.0f) * totalDurationMs).toLong()
                            } else {
                                (units10px * absSpeed * 1000.0f).toLong()
                            }
                            currentPosMs = (currentPosMs + deltaMs).coerceIn(0L, totalDurationMs)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isEnabled) "在此空白区域左右滑动体验测试效果" else "滑动控制已关闭",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp
                )

                // 底部全屏仿真实进度条（避开页边距）
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(start = leftMarginDp + 16.dp, end = rightMarginDp + 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val fraction = if (totalDurationMs > 0) (currentPosMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f) else 0f

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(1.5.dp)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.5.dp))
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "${formatTime(currentPosMs)} / ${formatTime(totalDurationMs)}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { showDurationDialog = true }
                    )
                }
            }
        }
    }

    if (showDurationDialog) {
        DurationInputDialog(
            currentDurationMs = totalDurationMs,
            onDismiss = { showDurationDialog = false },
            onSave = {
                totalDurationMs = it
                currentPosMs = (it * 0.5f).toLong()
                showDurationDialog = false
            }
        )
    }

    if (showSpeedInputDialog) {
        SpeedInputDialog(
            currentSpeed = if (isProportional) propSpeed else absSpeed,
            isProportional = isProportional,
            onDismiss = { showSpeedInputDialog = false },
            onSave = { newSpeed ->
                if (isProportional) {
                    propSpeed = newSpeed
                } else {
                    absSpeed = newSpeed
                }
                saveSettings(isEnabled, isProportional, propSpeed, absSpeed)
                showSpeedInputDialog = false
            }
        )
    }
}

// 非全屏模式配置页面
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NonFullscreenSwipeSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? android.app.Activity }

    // 进入非全屏设置页强行锁定为竖屏，退出时还原方向
    DisposableEffect(Unit) {
        activity?.let { act ->
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        onDispose {
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    val sp = remember { context.getSharedPreferences("HDAmieViewerDB", Context.MODE_PRIVATE) }

    var isEnabled by remember { mutableStateOf(sp.getBoolean("non_fs_swipe_enabled", true)) }
    var isProportional by remember { mutableStateOf(sp.getBoolean("non_fs_swipe_proportional", false)) }

    var propSpeed by remember { mutableFloatStateOf(sp.getFloat("non_fs_swipe_speed_prop", 0.05f)) }
    var absSpeed by remember { mutableFloatStateOf(sp.getFloat("non_fs_swipe_speed_abs", 1.0f)) }

    var totalDurationMs by remember { mutableLongStateOf(20 * 60 * 1000L) }
    var currentPosMs by remember { mutableLongStateOf((totalDurationMs * 0.5f).toLong()) }
    var showDurationDialog by remember { mutableStateOf(false) }
    var showSpeedInputDialog by remember { mutableStateOf(false) }

    fun saveSettings(enabled: Boolean, prop: Boolean, pSpeed: Float, aSpeed: Float) {
        sp.edit()
            .putBoolean("non_fs_swipe_enabled", enabled)
            .putBoolean("non_fs_swipe_proportional", prop)
            .putFloat("non_fs_swipe_speed_prop", pSpeed)
            .putFloat("non_fs_swipe_speed_abs", aSpeed)
            .apply()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("非全屏滑动进度设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 上方 16:9 假视频窗口测试区
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
                    .pointerInput(isEnabled, isProportional, propSpeed, absSpeed, totalDurationMs) {
                        if (!isEnabled) return@pointerInput
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            val units10px = dragAmount / 10.0f
                            val deltaMs = if (isProportional) {
                                (units10px * (propSpeed / 100.0f) * totalDurationMs).toLong()
                            } else {
                                (units10px * absSpeed * 1000.0f).toLong()
                            }
                            currentPosMs = (currentPosMs + deltaMs).coerceIn(0L, totalDurationMs)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isEnabled) "在此假视频窗口左右滑动测试" else "滑动控制已关闭",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp
                )

                // 底部进度条与时间显示
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val fraction = if (totalDurationMs > 0) (currentPosMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f) else 0f

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(1.5.dp)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.5.dp))
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "${formatTime(currentPosMs)} / ${formatTime(totalDurationMs)}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { showDurationDialog = true }
                    )
                }
            }

            // 下方参数调节选项区
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("开启滑动控制", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = {
                            isEnabled = it
                            saveSettings(isEnabled, isProportional, propSpeed, absSpeed)
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("比例控制 (基于总时长%)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = isProportional,
                        enabled = isEnabled,
                        onCheckedChange = {
                            isProportional = it
                            saveSettings(isEnabled, isProportional, propSpeed, absSpeed)
                        }
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    val currentSpeedVal = if (isProportional) propSpeed else absSpeed
                    val speedText = if (isProportional) String.format("每 10px -> %.2f%%", currentSpeedVal)
                    else String.format("每 10px -> %.1fs", currentSpeedVal)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("调节速度（点击值可输入）", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = speedText,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(enabled = isEnabled) { showSpeedInputDialog = true }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (isProportional) {
                        Slider(
                            value = propSpeed,
                            onValueChange = {
                                propSpeed = it
                                saveSettings(isEnabled, isProportional, propSpeed, absSpeed)
                            },
                            valueRange = 0.01f..20.0f, // 严格统一为 0.01% - 20%
                            enabled = isEnabled
                        )
                    } else {
                        Slider(
                            value = absSpeed,
                            onValueChange = {
                                absSpeed = it
                                saveSettings(isEnabled, isProportional, propSpeed, absSpeed)
                            },
                            valueRange = 0.1f..60.0f, // 严格统一为 0.1s - 60s
                            enabled = isEnabled
                        )
                    }
                }
            }
        }
    }

    if (showDurationDialog) {
        DurationInputDialog(
            currentDurationMs = totalDurationMs,
            onDismiss = { showDurationDialog = false },
            onSave = {
                totalDurationMs = it
                currentPosMs = (it * 0.5f).toLong()
                showDurationDialog = false
            }
        )
    }

    if (showSpeedInputDialog) {
        SpeedInputDialog(
            currentSpeed = if (isProportional) propSpeed else absSpeed,
            isProportional = isProportional,
            onDismiss = { showSpeedInputDialog = false },
            onSave = { newSpeed ->
                if (isProportional) {
                    propSpeed = newSpeed
                } else {
                    absSpeed = newSpeed
                }
                saveSettings(isEnabled, isProportional, propSpeed, absSpeed)
                showSpeedInputDialog = false
            }
        )
    }
}

// 视频总时长修改弹窗
@Composable
private fun DurationInputDialog(
    currentDurationMs: Long,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit
) {
    var textValue by remember { mutableStateOf((currentDurationMs / 60000L).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改测试视频时长", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("请输入视频总时长（分钟）：", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val minutes = textValue.trim().toLongOrNull() ?: 20L
                    val safeMs = (minutes.coerceIn(1L, 600L)) * 60 * 1000L
                    onSave(safeMs)
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

// 调节速度数值输入弹窗（自动限制在模式对应的边界内）
@Composable
private fun SpeedInputDialog(
    currentSpeed: Float,
    isProportional: Boolean,
    onDismiss: () -> Unit,
    onSave: (Float) -> Unit
) {
    val minVal = if (isProportional) 0.01f else 0.1f
    val maxVal = if (isProportional) 20.0f else 60.0f
    val unitStr = if (isProportional) "%" else "s"

    var inputText by remember { mutableStateOf(String.format(if (isProportional) "%.2f" else "%.1f", currentSpeed)) }
    val inputFloat = inputText.trim().toFloatOrNull()
    val isValid = inputFloat != null && inputFloat in minVal..maxVal

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改调节速度", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "请输入每 10px 的偏移数值（范围：$minVal$unitStr ~ $maxVal$unitStr）：",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    singleLine = true,
                    isError = !isValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (!isValid) {
                    Text(
                        text = "输入的数值必须介于 $minVal$unitStr 与 $maxVal$unitStr 之间",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (inputFloat != null) {
                        onSave(inputFloat.coerceIn(minVal, maxVal))
                    }
                },
                enabled = isValid,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}