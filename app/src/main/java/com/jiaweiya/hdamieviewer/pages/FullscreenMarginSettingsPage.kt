package com.jiaweiya.hdamieviewer.pages

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.roundToInt
import androidx.compose.foundation.interaction.MutableInteractionSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenMarginSettingsPage(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? android.app.Activity }
    val focusManager = LocalFocusManager.current
    val sharedPrefs = remember { context.getSharedPreferences("HDAmieViewerDB", Context.MODE_PRIVATE) }

    // 读取与保存本地边距数值（单位 dp，默认 0）
    var leftMargin by remember { mutableFloatStateOf(sharedPrefs.getInt("fullscreen_margin_left", 0).toFloat()) }
    var rightMargin by remember { mutableFloatStateOf(sharedPrefs.getInt("fullscreen_margin_right", 0).toFloat()) }

    var leftInputText by remember { mutableStateOf(leftMargin.roundToInt().toString()) }
    var rightInputText by remember { mutableStateOf(rightMargin.roundToInt().toString()) }

    fun saveMargins(left: Int, right: Int) {
        sharedPrefs.edit()
            .putInt("fullscreen_margin_left", left)
            .putInt("fullscreen_margin_right", right)
            .apply()
    }

    fun commitLeftInput() {
        val parsed = leftInputText.toIntOrNull()?.coerceIn(0, 100) ?: leftMargin.roundToInt()
        leftMargin = parsed.toFloat()
        leftInputText = parsed.toString()
        saveMargins(parsed, rightMargin.roundToInt())
    }

    fun commitRightInput() {
        val parsed = rightInputText.toIntOrNull()?.coerceIn(0, 100) ?: rightMargin.roundToInt()
        rightMargin = parsed.toFloat()
        rightInputText = parsed.toString()
        saveMargins(leftMargin.roundToInt(), parsed)
    }

    // 进入页面强制锁定横屏并隐藏系统栏，离开时恢复
    DisposableEffect(Unit) {
        activity?.let { act ->
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
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

    BackHandler { onBackClick() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // 点击空白区域时：清除输入焦点并同步校验输入框数值
            .pointerInput(Unit) {
                detectTapGestures {
                    focusManager.clearFocus()
                    commitLeftInput()
                    commitRightInput()
                }
            }
    ) {
        // 1. 左侧屏幕指示细线
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = leftMargin.dp)
                .width(2.dp)
                .background(MaterialTheme.colorScheme.primary)
                .align(Alignment.CenterStart)
        )

        // 2. 右侧屏幕指示细线
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(end = rightMargin.dp)
                .width(2.dp)
                .background(MaterialTheme.colorScheme.primary)
                .align(Alignment.CenterEnd)
        )

        // 3. 左上角返回按钮
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = Color.White
            )
        }

        // 4. 屏幕中央控制调距区域（分两行，完美复刻图2与图3样式）
        Column(
            modifier = Modifier
                .width(440.dp)
                .align(Alignment.Center)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 第一行：左边缘边距
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "左边缘边距：",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Slider(
                            value = leftMargin,
                            onValueChange = {
                                leftMargin = it
                                leftInputText = it.roundToInt().toString()
                                saveMargins(it.roundToInt(), rightMargin.roundToInt())
                            },
                            valueRange = 0f..100f,
                            thumb = {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(26.dp)
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                                )
                            },
                            colors = SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                    }

                    val interactionSourceLeft = remember { MutableInteractionSource() }
                    androidx.compose.foundation.text.BasicTextField(
                        value = leftInputText,
                        onValueChange = { leftInputText = it },
                        modifier = Modifier
                            .width(90.dp)
                            .fillMaxHeight()
                            .onFocusChanged { if (!it.isFocused) commitLeftInput() },
                        interactionSource = interactionSourceLeft,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            commitLeftInput()
                        }),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            color = Color.White
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
                    ) { innerTextField ->
                        OutlinedTextFieldDefaults.DecorationBox(
                            value = leftInputText,
                            innerTextField = innerTextField,
                            enabled = true,
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
                            interactionSource = interactionSourceLeft,
                            label = { Text("边距(dp)", fontSize = 10.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            container = {
                                OutlinedTextFieldDefaults.Container(
                                    enabled = true,
                                    isError = false,
                                    interactionSource = interactionSourceLeft,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 第二行：右边缘边距
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "右边缘边距：",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Slider(
                            value = rightMargin,
                            onValueChange = {
                                rightMargin = it
                                rightInputText = it.roundToInt().toString()
                                saveMargins(leftMargin.roundToInt(), it.roundToInt())
                            },
                            valueRange = 0f..100f,
                            thumb = {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(26.dp)
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                                )
                            },
                            colors = SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                    }

                    val interactionSourceRight = remember { MutableInteractionSource() }
                    androidx.compose.foundation.text.BasicTextField(
                        value = rightInputText,
                        onValueChange = { rightInputText = it },
                        modifier = Modifier
                            .width(90.dp)
                            .fillMaxHeight()
                            .onFocusChanged { if (!it.isFocused) commitRightInput() },
                        interactionSource = interactionSourceRight,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            commitRightInput()
                        }),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            color = Color.White
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
                    ) { innerTextField ->
                        OutlinedTextFieldDefaults.DecorationBox(
                            value = rightInputText,
                            innerTextField = innerTextField,
                            enabled = true,
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
                            interactionSource = interactionSourceRight,
                            label = { Text("边距(dp)", fontSize = 10.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            container = {
                                OutlinedTextFieldDefaults.Container(
                                    enabled = true,
                                    isError = false,
                                    interactionSource = interactionSourceRight,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}