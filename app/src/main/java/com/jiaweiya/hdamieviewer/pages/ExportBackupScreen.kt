package com.jiaweiya.hdamieviewer.pages

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.gson.Gson
import com.jiaweiya.hdamieviewer.TimeProfile
import com.jiaweiya.hdamieviewer.TimetableData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// 备份包数据模型
data class BackupData(
    val appVersion: String,
    val settings: Map<String, Map<String, Any>>?, // key: 类别名, value: 内部具体的配置对
    val timetables: List<TimetableData>? = null,
    val timeProfiles: List<TimeProfile>? = null
)

object BackupCrypto {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private val KEY_BYTES: ByteArray by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.digest("HDAmieViewerBackupSaltKey1289Security".toByteArray(Charsets.UTF_8))
    }

    fun encrypt(plainText: String): String {
        val keySpec = SecretKeySpec(KEY_BYTES, "AES")
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        val iv = cipher.iv // 自动生成的随机 IV
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val cipherBase64 = Base64.encodeToString(cipherText, Base64.NO_WRAP)
        return "$ivBase64:$cipherBase64"
    }

    fun decrypt(encryptedText: String): String {
        val parts = encryptedText.split(":")
        if (parts.size != 2) throw IllegalArgumentException("非法的备份文件格式或已损坏")
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)

        val keySpec = SecretKeySpec(KEY_BYTES, "AES")
        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        val decryptedBytes = cipher.doFinal(cipherText)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBackupScreen(
    currentAppVersion: String,
    timetables: List<TimetableData>, // 保持入参签名兼容 MainActivity
    timeProfiles: List<TimeProfile>, // 保持入参签名兼容 MainActivity
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // 数据库名称修改为新项目对应的库
    val sharedPrefs = remember { context.getSharedPreferences("HDAmieViewerDB", Context.MODE_PRIVATE) }

    // 所有分类信息
    val settingsCategories = remember {
        listOf("主题与外观", "应用更新")
    }

    // 状态配置
    var isSettingsExpanded by remember { mutableStateOf(false) }
    val selectedSettingsMap = remember { mutableStateMapOf<String, Boolean>().apply { settingsCategories.forEach { put(it, true) } } }

    // 动态计算系统设置的三态复选状态
    val checkedSettingsCount = settingsCategories.count { selectedSettingsMap[it] == true }
    val settingsToggleState = when {
        checkedSettingsCount == settingsCategories.size -> ToggleableState.On
        checkedSettingsCount == 0 -> ToggleableState.Off
        else -> ToggleableState.Indeterminate
    }

    // 构建加密的 JSON 文本
    fun buildEncryptedBackupJson(): String {
        val settingsMap = mutableMapOf<String, Map<String, Any>>()

        settingsCategories.forEach { category ->
            if (selectedSettingsMap[category] == true) {
                val catMap = mutableMapOf<String, Any>()
                when (category) {
                    "主题与外观" -> {
                        catMap["theme_mode"] = sharedPrefs.getInt("theme_mode", 0)
                        catMap["theme_color"] = sharedPrefs.getLong("theme_color", 0xFF9E77EDL)
                    }
                    "应用更新" -> {
                        catMap["update_channel"] = sharedPrefs.getInt("update_channel", 0)
                    }
                }
                settingsMap[category] = catMap
            }
        }

        val backupObj = BackupData(
            appVersion = currentAppVersion,
            settings = if (settingsMap.isNotEmpty()) settingsMap else null,
            timetables = null,
            timeProfiles = null
        )

        val rawJson = Gson().toJson(backupObj)
        return BackupCrypto.encrypt(rawJson)
    }

    // 文件导出保存器
    val createDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val success = withContext(Dispatchers.IO) {
                    try {
                        val encryptedData = buildEncryptedBackupJson()
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(encryptedData.toByteArray(Charsets.UTF_8))
                        }
                        true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                }
                if (success) {
                    Toast.makeText(context, "备份导出成功", Toast.LENGTH_SHORT).show()
                    onBackClick()
                } else {
                    Toast.makeText(context, "备份导出失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0.dp),
                title = { Text("新建应用备份", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val timeStr = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                        val fileName = "hdamie-$currentAppVersion-$timeStr.hdbkup"
                        createDocLauncher.launch(fileName)
                    }) {
                        Text("导出", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
            // 所有配置项
            item {
                ExpandableGroupHeader(
                    title = "所有系统设置",
                    isExpanded = isSettingsExpanded,
                    onExpandClick = { isSettingsExpanded = !isSettingsExpanded },
                    toggleState = settingsToggleState,
                    onToggleClick = {
                        val nextChecked = settingsToggleState != ToggleableState.On
                        settingsCategories.forEach { selectedSettingsMap[it] = nextChecked }
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
                        settingsCategories.forEach { category ->
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