package com.jiaweiya.hdamieviewer.pages

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiaweiya.hdamieviewer.TimeProfile
import com.jiaweiya.hdamieviewer.TimetableData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportBackupScreen(
    backupData: BackupData?,
    currentTimetables: List<TimetableData>, // 保持入参签名兼容 MainActivity
    currentTimeProfiles: List<TimeProfile>, // 保持入参签名兼容 MainActivity
    onImportSuccess: (List<TimetableData>, List<TimeProfile>) -> Unit, // 保持入参签名兼容 MainActivity
    onBackClick: () -> Unit
) {
    if (backupData == null) {
        onBackClick()
        return
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // 数据库名称修改为新项目对应的库
    val sharedPrefs = remember { context.getSharedPreferences("HDAmieViewerDB", Context.MODE_PRIVATE) }

    // 准备数据源
    val settingsInBackup = remember(backupData) { backupData.settings?.keys?.toList() ?: emptyList() }

    // 状态配置
    val selectedSettingsMap = remember { mutableStateMapOf<String, Boolean>().apply { settingsInBackup.forEach { put(it, true) } } }
    var isSettingsExpanded by remember { mutableStateOf(false) }

    // 动态计算三态复选状态
    val checkedSettingsCount = settingsInBackup.count { selectedSettingsMap[it] == true }
    val settingsToggleState = when {
        checkedSettingsCount == settingsInBackup.size -> ToggleableState.On
        checkedSettingsCount == 0 -> ToggleableState.Off
        else -> ToggleableState.Indeterminate
    }

    // 导入核心实现
    fun executeImport() {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val editor = sharedPrefs.edit()

                // 1. 系统设置导入
                if (checkedSettingsCount > 0) {
                    backupData.settings?.forEach { (category, map) ->
                        if (selectedSettingsMap[category] == true) {
                            map.forEach { (key, value) ->
                                when (value) {
                                    is Boolean -> editor.putBoolean(key, value)
                                    is Float -> editor.putFloat(key, value)
                                    is Int -> editor.putInt(key, value)
                                    is Long -> editor.putLong(key, value)
                                    is String -> editor.putString(key, value)
                                    is Double -> {
                                        // 核心安全修饰：由于 Gson 在不显式指定类型时会将 JSON 数值反序列化为 Double，
                                        // 这里对 theme_color 进行安全转换，防止 ClassCastException 导致崩溃。
                                        if (key == "theme_color") {
                                            editor.putLong(key, value.toLong())
                                        } else {
                                            editor.putInt(key, value.toInt())
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                editor.apply()

                withContext(Dispatchers.Main) {
                    onImportSuccess(emptyList(), emptyList())
                    Toast.makeText(context, "导入成功，部分全局配置已应用", Toast.LENGTH_SHORT).show()
                    onBackClick()
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0.dp),
                title = { Text("确认恢复备份", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { executeImport() }) {
                        Text("确认导入", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp)
        ) {
            // 系统设置导入项
            if (settingsInBackup.isNotEmpty()) {
                item {
                    ExpandableGroupHeader(
                        title = "系统配置选项",
                        isExpanded = isSettingsExpanded,
                        onExpandClick = { isSettingsExpanded = !isSettingsExpanded },
                        toggleState = settingsToggleState,
                        onToggleClick = {
                            val nextChecked = settingsToggleState != ToggleableState.On
                            settingsInBackup.forEach { selectedSettingsMap[it] = nextChecked }
                        }
                    )

                    AnimatedVisibility(
                        visible = isSettingsExpanded,
                        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp)
                        ) {
                            settingsInBackup.forEach { category ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val next = !(selectedSettingsMap[category] ?: false)
                                            selectedSettingsMap[category] = next
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(category, fontSize = 14.sp)
                                    Checkbox(
                                        checked = selectedSettingsMap[category] ?: false,
                                        onCheckedChange = { checked ->
                                            selectedSettingsMap[category] = checked
                                        }
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun ExpandableGroupHeader(
    title: String,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    toggleState: ToggleableState,
    onToggleClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onExpandClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(20.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )

        Spacer(modifier = Modifier.width(12.dp))

        TriStateCheckbox(
            state = toggleState,
            onClick = onToggleClick
        )
    }
}