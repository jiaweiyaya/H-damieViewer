package com.jiaweiya.hdamieviewer.pages

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.Replay
import java.security.MessageDigest
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.AnimatedVisibility

// ==================== Iwara 详情 API 专属数据模型 ====================

data class IwaraTagDetail(
    val id: String,
    val type: String?
)

data class IwaraDetailAvatar(
    val id: String?,
    val name: String?
)

data class IwaraDetailUser(
    val name: String?,
    val username: String?,
    val avatar: IwaraDetailAvatar?
)

data class IwaraVideoDetail(
    val id: String,
    val title: String,
    val body: String?, // 简介
    val numViews: Int?,
    val numLikes: Int?,
    val createdAt: String?,
    val tags: List<IwaraTagDetail>?,
    val user: IwaraDetailUser?,
    val fileUrl: String? // 直链解析接口
)

data class IwaraFileSource(
    val view: String?,
    val download: String?
)

data class IwaraVideoFormat(
    val name: String?,
    val src: IwaraFileSource?
)

// ==================== 网络数据拉取请求 ====================

suspend fun fetchVideoDetail(videoId: String): IwaraVideoDetail? {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.iwara.tv/video/$videoId")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val json = reader.readText()
                Gson().fromJson(json, IwaraVideoDetail::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

suspend fun fetchVideoFormats(fileUrl: String): List<IwaraVideoFormat> {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL(fileUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            val xVersion = calculateXVersion(fileUrl)
            if (xVersion.isNotEmpty()) {
                connection.setRequestProperty("X-Version", xVersion) // 动态注入校验头
            }
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val json = reader.readText()
                val type = object : TypeToken<List<IwaraVideoFormat>>() {}.type
                Gson().fromJson<List<IwaraVideoFormat>>(json, type) ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

// ==================== 播放器页面主体 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    videoId: String,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit
) {
    var videoDetail by remember { mutableStateOf<IwaraVideoDetail?>(null) }
    var videoUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var availableFormats by remember { mutableStateOf<List<IwaraVideoFormat>>(emptyList()) }
    var currentResolutionName by remember { mutableStateOf("Source") }
    var pendingSeekPosition by remember { mutableLongStateOf(-1L) }

    val context = LocalContext.current

    // 获取当前窗口的 Activity（定义在此处，供 DisposableEffect 等早期代码使用）
    val activity = remember(context) { context as? android.app.Activity }

    val sharedPrefs = remember { context.getSharedPreferences("HDAmieViewerDB", Context.MODE_PRIVATE) }
    val playerType = sharedPrefs.getInt("player_type", 0) // 去除 remember，保证每次进入实时读取最新

    // 【核心修复 1】：将 ExoPlayer 的生命周期提升到最顶层管理，脱离分支，使其在旋转重绘时不受干扰
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    // 声明视频真实宽高状态
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }

    // 监听 ExoPlayer 视频尺寸变化
    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoWidth = videoSize.width
                    videoHeight = videoSize.height
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // 动态计算播放器窗口比例
    val playerAspectRatio = remember(videoWidth, videoHeight) {
        if (videoWidth > 0 && videoHeight > 0) {
            if (videoWidth > videoHeight) {
                // 宽屏视频（长 > 高）：动态匹配视频原始比例，左右撑满
                videoWidth.toFloat() / videoHeight.toFloat()
            } else {
                // 窄屏/竖屏视频（高 >= 长）：播放器区域固定为 1:1，左右留出黑边
                1f
            }
        } else {
            16f / 9f // 默认初始比例 16:9
        }
    }

    // 监听播放链接，有直链时自动装载并准备播放
    LaunchedEffect(videoUrl) {
        if (!videoUrl.isNullOrEmpty()) {
            val mediaItem = MediaItem.fromUri(videoUrl!!)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()

            // 【核心修复】：如果有待恢复的进度点，在 prepare 后由协程自动无缝寻轨
            if (pendingSeekPosition >= 0L) {
                exoPlayer.seekTo(pendingSeekPosition)
                pendingSeekPosition = -1L // 重置临时标记
            }

            exoPlayer.playWhenReady = true
        }
    }

    // 只有当整个页面彻底关闭销毁时，才释放播放器资源并还原屏幕方向
    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
            // 👈 离开播放页时强制恢复屏幕方向与显示状态栏，防止主页变成横屏
            activity?.let { act ->
                act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val controller = androidx.core.view.WindowCompat.getInsetsController(act.window, act.window.decorView)
                controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    var debugLog by remember { mutableStateOf("") }
    var showDebugDialog by remember { mutableStateOf(false) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // 智能劫持物理返回键：横屏时拦截返回键恢复竖屏；竖屏时不拦截（让系统原生的预测性返回手势正常工作）
    BackHandler(enabled = isLandscape) {
        activity?.let { act ->
            // 👈 强制切换为 PORTRAIT（竖屏）
            act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            val controller = androidx.core.view.WindowCompat.getInsetsController(act.window, act.window.decorView)
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    // 回到竖屏后解除方向锁定，恢复系统传感器自动旋转
    LaunchedEffect(isLandscape) {
        if (!isLandscape) {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(videoId) {
        isLoading = true
        debugLog = "--- 开始排查视频加载链路 ---\n"
        debugLog += "1. 视频 ID: $videoId, 播放器类型代码: $playerType\n"

        val detail = fetchVideoDetail(videoId)
        videoDetail = detail

        if (detail != null) {
            debugLog += "✅ 2. 获取详情成功!\n"
            debugLog += "   - 视频标题: ${detail.title}\n"
            debugLog += "   - 直链路由 fileUrl: ${detail.fileUrl}\n"

            if (!detail.fileUrl.isNullOrEmpty()) {
                debugLog += "3. 开始请求直链接口并获取原始数据...\n"

                val rawJson = fetchRawFormatsJson(detail.fileUrl)
                debugLog += "   - 接口响应报文:\n$rawJson\n\n"

                if (rawJson != null && !rawJson.startsWith("HTTP 状态异常") && !rawJson.startsWith("连接异常")) {
                    try {
                        val type = object : TypeToken<List<IwaraVideoFormat>>() {}.type
                        val formats: List<IwaraVideoFormat> = Gson().fromJson(rawJson, type)

                        debugLog += "4. 格式解析：发现 ${formats.size} 个画质分支\n"
                        formats.forEach {
                            debugLog += "   - 分支 [${it.name}]: view=${it.src?.view}\n"
                        }

                        val validFormats = formats.filter { !it.src?.view.isNullOrEmpty() }
                        availableFormats = validFormats // 1. 将解析到的全部有效分支赋给状态，供顶栏菜单展示

                        val bestFormat = validFormats.find { it.name == "Source" }
                            ?: validFormats.find { it.name == "540" } // 2. 匹配 "540" 与 "360" 格式
                            ?: validFormats.find { it.name == "360" }
                            ?: validFormats.firstOrNull()

                        val rawUrl = bestFormat?.src?.view
                        videoUrl = if (rawUrl?.startsWith("//") == true) "https:$rawUrl" else rawUrl
                        currentResolutionName = bestFormat?.name ?: "Source" // 3. 设置默认分辨率名称

                        debugLog += "   - 最终提取的播放直链: $videoUrl\n"
                    } catch (e: Exception) {
                        debugLog += "❌ 4. 格式解析失败 (可能接口返回结构已发生变动): ${e.localizedMessage}\n"
                    }
                } else {
                    debugLog += "❌ 3. 请求直链接口中断\n"
                }
            } else {
                debugLog += "❌ 2. 详情数据中 fileUrl 字段为空或未返回\n"
            }
        } else {
            debugLog += "❌ 1. 获取视频详情请求失败 (可能受限于代理或连接超时)\n"
        }
        isLoading = false
    }

    // 修改后：仅负责精准捕捉时间点并更新 Url 状态，将播放逻辑安全移交给顶层 LaunchedEffect
    val onResolutionSelected = { format: IwaraVideoFormat ->
        val rawUrl = format.src?.view
        val newUrl = if (rawUrl?.startsWith("//") == true) "https:$rawUrl" else rawUrl
        if (!newUrl.isNullOrEmpty() && newUrl != videoUrl) {
            pendingSeekPosition = exoPlayer.currentPosition // 1. 精准记录当前毫秒数
            videoUrl = newUrl
            currentResolutionName = format.name ?: "Source"
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp)
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (videoDetail != null) {
            val detail = videoDetail!!
            val videoTitle = detail.title
            val videoAuthor = detail.user?.name ?: detail.user?.username ?: "未知作者"
            val videoDesc = detail.body ?: "该视频暂无相关简介信息。"
            val videoTags = detail.tags?.map { it.id } ?: emptyList()
            val views = detail.numViews ?: 0
            val likes = detail.numLikes ?: 0
            val dateStr = detail.createdAt?.take(10) ?: "未知日期"

            val avatarUrl = if (detail.user?.avatar != null) {
                "https://files.iwara.tv/image/avatar/${detail.user.avatar.id}/${detail.user.avatar.name}"
            } else ""

            // 【自适应重绘】：如果是横屏模式，强制不展示任何图文列表，让播放器占满屏幕
            if (isLandscape) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    if (!videoUrl.isNullOrEmpty()) {
                        MpvMinimalPlayer(
                            exoPlayer = exoPlayer,
                            videoUrl = videoUrl!!,
                            availableFormats = availableFormats,
                            currentResolutionName = currentResolutionName,
                            onResolutionSelected = onResolutionSelected,
                            onBackClick = {
                                // 横屏下按左上角返回按钮：强行恢复竖屏
                                activity?.let { act ->
                                    act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    val controller = androidx.core.view.WindowCompat.getInsetsController(act.window, act.window.decorView)
                                    controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                                }
                            },
                            onHomeClick = {
                                // 横屏下按主页按钮：还原方向并导航回主页
                                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                onHomeClick()
                            },
                            onFullscreenClick = {
                                // 点击右下角退出全屏按钮：强行恢复竖屏
                                activity?.let { act ->
                                    act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    val controller = androidx.core.view.WindowCompat.getInsetsController(act.window, act.window.decorView)
                                    controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                // 正常的竖屏 Scaffold 滚动列表
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 1. 顶部视频播放器区域（自适应比例，带状态栏安全避让与平滑过渡）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                            .statusBarsPadding()
                            .animateContentSize() // 尺寸变化时带平滑动画
                            .aspectRatio(playerAspectRatio)
                    ) {
                        if (!videoUrl.isNullOrEmpty()) {
                            when (playerType) {
                                1 -> MpvMinimalPlayer(
                                    exoPlayer = exoPlayer,
                                    videoUrl = videoUrl!!,
                                    availableFormats = availableFormats,
                                    currentResolutionName = currentResolutionName,
                                    onResolutionSelected = onResolutionSelected,
                                    onBackClick = onBackClick,
                                    onHomeClick = onHomeClick,
                                    onFullscreenClick = {
                                        // 点击右下角全屏，旋转为横屏并隐藏系统栏
                                        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                        activity?.window?.let { win ->
                                            val controller = androidx.core.view.WindowCompat.getInsetsController(win, win.decorView)
                                            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                                            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                                2 -> NativeMediaPlayer(videoUrl = videoUrl!!, modifier = Modifier.fillMaxSize())
                                else -> VideoPlayer(exoPlayer = exoPlayer, modifier = Modifier.fillMaxSize())
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        }

                        // 只有在非“极简美化版 (MpvPlayer)”时，才在外层堆叠常驻的控制顶栏
                        if (playerType != 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = onBackClick,
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                                ) {
                                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                                }
                                IconButton(
                                    onClick = onHomeClick,
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                                ) {
                                    Icon(imageVector = Icons.Default.Home, contentDescription = "主页", tint = Color.White)
                                }
                            }
                        }
                    }

                    // 2. 作者栏配置
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                if (avatarUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = "用户头像",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = videoAuthor.take(1),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = videoAuthor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(text = "专栏创作者", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Button(
                            onClick = {},
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("关注", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 13.sp)
                        }
                    }

                    // 3. 视频标题、播放量与日期信息
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = videoTitle, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
                        Text(
                            text = "${formatCount(views)}次播放  |  $dateStr",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    ExpandableDescription(
                        text = videoDesc,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        ActionButton(Icons.Default.TaskAlt, "快速打卡")
                        ActionButton(Icons.Default.FavoriteBorder, "加入喜欢")
                        ActionButton(Icons.Default.BookmarkBorder, "加入清单")
                        ActionButton(Icons.Default.Download, "下载")
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (videoTags.isNotEmpty()) {
                        TagsSection(
                            tags = videoTags,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedButton(
                        onClick = { showDebugDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("查看并复制接口调试信息", fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Text("拉取视频数据失败，请检查连接", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (showDebugDialog) {
        AlertDialog(
            onDismissRequest = { showDebugDialog = false },
            title = { Text("接口调试控制台", fontWeight = FontWeight.Bold) },
            text = {
                Box(
                    modifier = Modifier
                        .height(300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SelectionContainer {
                        Text(
                            text = debugLog,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDebugDialog = false }
                ) {
                    Text("关闭")
                }
            }
        )
    }
}

// ExoPlayer 核心渲染模块：接受外部传入的播放器，不再自行创建/销毁
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(exoPlayer: ExoPlayer, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true // 加载播放控制进度条和暂停按钮
            }
        },
        modifier = modifier
    )
}

// ==================== 辅助 UI 封装组件 ====================

// 1. 视频简介：3 行折叠/展开组件
@Composable
fun ExpandableDescription(text: String, modifier: Modifier = Modifier) {
    var isExpanded by remember { mutableStateOf(false) }
    var hasOverflow by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize() // 平滑拉伸动画
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = text,
                maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = {
                    // 仅在收起状态下测量是否溢出，防止展开后溢出状态消失导致按钮闪烁
                    if (!isExpanded) {
                        hasOverflow = it.hasVisualOverflow
                    }
                },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
                modifier = Modifier
                    .padding(end = if (hasOverflow && !isExpanded) 48.dp else 0.dp) // 收起且溢出时，为“展开”键留出 48.dp 空间
                    .clickable { if (isExpanded) isExpanded = false }
            )

            // 收起状态下的“展开”按键（嵌入在第三行行末）
            if (hasOverflow && !isExpanded) {
                Text(
                    text = "展开",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(MaterialTheme.colorScheme.background)
                        .clickable { isExpanded = true }
                        .padding(start = 4.dp)
                )
            }
        }

        // 【新增】：如果处于展开状态，在文本最后一行的“下一行”的“右侧”显示一个“折叠”按钮
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp), // 留出一点上行边距，代表“下一行”
                contentAlignment = Alignment.CenterEnd // 居右对齐
            ) {
                Text(
                    text = "折叠",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable { isExpanded = false }
                        .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                )
            }
        }
    }
}

// 2. 快速操作行中的单个按键
@Composable
fun ActionButton(icon: ImageVector, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable { }
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// 3. 标签栏：最大 2 行自适应展开折叠
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TagsSection(tags: List<String>, modifier: Modifier = Modifier) {
    var isExpanded by remember { mutableStateOf(false) }
    var hasMoreThanTwoRows by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = if (hasMoreThanTwoRows && !isExpanded) 36.dp else 0.dp)
                .then(if (!isExpanded) Modifier.heightIn(max = 84.dp) else Modifier)
                .onGloballyPositioned { coordinates ->
                    val heightDp = with(density) { coordinates.size.height.toDp() }
                    if (!isExpanded && heightDp >= 84.dp) {
                        hasMoreThanTwoRows = true
                    }
                }
        ) {
            tags.forEach { tag ->
                SuggestionChip(
                    onClick = {},
                    label = { Text(tag, fontSize = 12.sp) },
                    shape = RoundedCornerShape(8.dp),
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                )
            }
        }

        if (hasMoreThanTwoRows) {
            IconButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "标签折叠控制",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ==================== 播放器扩展内核 ====================

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MpvMinimalPlayer(
    exoPlayer: ExoPlayer,
    videoUrl: String,
    availableFormats: List<IwaraVideoFormat>,
    currentResolutionName: String,
    onResolutionSelected: (IwaraVideoFormat) -> Unit,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    // 播放进度与控制栏显示状态
    var currentPosition by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(false) }
    var isEnded by remember { mutableStateOf(false) }
    var isResolutionMenuExpanded by remember { mutableStateOf(false) }

    // 获取屏幕旋转方向
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // 依据是否横屏，动态放大按钮与图标尺寸
    val topBtnSize = if (isLandscape) 44.dp else 36.dp
    val topIconSize = if (isLandscape) 22.dp else 18.dp
    // 👈 播放/暂停按钮在横屏下放大 50%
    val playBtnSize = if (isLandscape) 54.dp else 36.dp // 36 * 1.5 = 54
    val playIconSize = if (isLandscape) 33.dp else 22.dp // 22 * 1.5 = 33

    // 监听横屏下的控制栏显隐，同步显示/隐藏手机系统状态栏
    val context = LocalContext.current
    val activity = remember(context) { context as? android.app.Activity }

    // 读取用户设置的横屏左右边距
    val sharedPrefs = remember { context.getSharedPreferences("HDAmieViewerDB", Context.MODE_PRIVATE) }
    val leftMarginDp = remember(isLandscape) {
        if (isLandscape) sharedPrefs.getInt("fullscreen_margin_left", 0).dp else 0.dp
    }
    val rightMarginDp = remember(isLandscape) {
        if (isLandscape) sharedPrefs.getInt("fullscreen_margin_right", 0).dp else 0.dp
    }

    LaunchedEffect(isControlsVisible, isLandscape) {
        activity?.window?.let { win ->
            val controller = androidx.core.view.WindowCompat.getInsetsController(win, win.decorView)
            if (isLandscape) {
                if (isControlsVisible) {
                    // 控制栏展开：显示系统状态栏
                    controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                } else {
                    // 控制栏隐藏：隐藏系统状态栏
                    controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        }
    }

    @Composable
    fun ResolutionSelectorBox() {
        val buttonBgColor by animateColorAsState(
            targetValue = if (isResolutionMenuExpanded) Color.Black.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.35f),
            animationSpec = tween(250),
            label = "buttonBgColor"
        )

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(buttonBgColor)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = if (isResolutionMenuExpanded) 0.15f else 0.08f),
                    shape = RoundedCornerShape(6.dp)
                )
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                .clickable {
                    isResolutionMenuExpanded = !isResolutionMenuExpanded
                }
                .padding(
                    horizontal = if (isResolutionMenuExpanded) 4.dp else 2.dp,
                    vertical = if (isResolutionMenuExpanded) 4.dp else 2.dp
                ),
            contentAlignment = Alignment.TopEnd
        ) {
            if (!isResolutionMenuExpanded) {
                Text(
                    text = currentResolutionName,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val qualityPriority = listOf("preview", "360", "540", "720", "1080", "Source")
                    val sortedFormats = availableFormats.sortedBy { format ->
                        qualityPriority.indexOf(format.name ?: "")
                    }

                    sortedFormats.forEach { format ->
                        val isSelected = format.name == currentResolutionName
                        Text(
                            text = format.name ?: "未知",
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    isResolutionMenuExpanded = false
                                    onResolutionSelected(format)
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    // 进度轮询监听
    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            bufferedPosition = exoPlayer.bufferedPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            isPlaying = exoPlayer.isPlaying
            isBuffering = exoPlayer.playbackState == 2
            isEnded = exoPlayer.playbackState == 4
            kotlinx.coroutines.delay(250) // 250 毫秒高频刷新细进度条，获得最佳拖动回馈
        }
    }

    // 自动退场：播放状态下 3 秒无操作自动缩回控制栏
    LaunchedEffect(isControlsVisible, isPlaying, isResolutionMenuExpanded) {
        if (isControlsVisible && isPlaying && !isResolutionMenuExpanded) {
            kotlinx.coroutines.delay(3000)
            isControlsVisible = false
        }
    }

    LaunchedEffect(isEnded) {
        if (isEnded) {
            isControlsVisible = true
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // 点击空白区域的处理逻辑：
                if (isResolutionMenuExpanded) {
                    // 1. 如果画质菜单处于展开状态，仅收起菜单，不改变控制栏显示状态
                    isResolutionMenuExpanded = false
                } else {
                    // 2. 菜单未展开时，正常开关控制栏
                    isControlsVisible = !isControlsVisible
                }
            }
    ) {
        // 底层视频画面
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // 完全关闭原生臃肿的大控制台
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering) {
            // 画面正中心显示白色加载转圈动画
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(44.dp),
                    color = Color.White,
                    strokeWidth = 3.dp
                )
            }
        } else if (isEnded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // 【空白点击】：仅作拦截，防止点击边缘时意外隐藏控制条或引发多余穿透
                    },
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = {
                        // 【唯一触发点】：只有点击中心这个重播按钮，才会重新播放视频
                        exoPlayer.seekTo(0L)
                        exoPlayer.play()
                    },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = "重新播放",
                        tint = Color.White,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
        }

        // Bilibili 风格的高级 Compose 自定义悬浮控制面板
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                // 1. 顶部控制栏背景阴影（固定高度，保持从最左到最右）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isLandscape) 64.dp else 52.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                            )
                        )
                )

                // 顶部控制图标与画质菜单
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(
                            start = if (isLandscape) leftMarginDp + 12.dp else 12.dp,
                            end = if (isLandscape) rightMarginDp + 12.dp else 12.dp,
                            top = if (isLandscape) 2.dp else 8.dp,
                            bottom = 6.dp
                        ),
                    verticalAlignment = Alignment.Top
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(topBtnSize)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White,
                            modifier = Modifier.size(topIconSize)
                        )
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    IconButton(
                        onClick = onHomeClick,
                        modifier = Modifier.size(topBtnSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "主页",
                            tint = Color.White,
                            modifier = Modifier.size(topIconSize)
                        )
                    }

                    // 将后续组件推到屏幕最右侧
                    Spacer(modifier = Modifier.weight(1f))

                    // 仅在竖屏时显示在顶部
                    if (!isLandscape) {
                        Box(
                            modifier = Modifier.height(36.dp),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            Box(
                                modifier = Modifier.wrapContentSize(align = Alignment.TopEnd, unbounded = true)
                            ) {
                                ResolutionSelectorBox()
                            }
                        }
                    }
                }

                // 2. 底部控制栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                            )
                        )
                        .padding(start = if (isLandscape) leftMarginDp + 12.dp else 12.dp, end = if (isLandscape) rightMarginDp + 12.dp else 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左下角：播放/暂停按钮（横屏同步放大）
                    IconButton(
                        onClick = {
                            if (!isBuffering) {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                isPlaying = exoPlayer.isPlaying
                            }
                        },
                        modifier = Modifier.size(playBtnSize) // 54.dp
                    ) {
                        if (isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "播放暂停",
                                tint = Color.White,
                                modifier = Modifier.size(playIconSize)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // 进度条部分（代码保持原样）
                    var widthPx by remember { mutableIntStateOf(0) }
                    val density = LocalDensity.current

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .onGloballyPositioned { widthPx = it.size.width }
                            .pointerInput(duration) {
                                detectTapGestures { offset ->
                                    if (widthPx > 0 && duration > 0) {
                                        val fraction = (offset.x / widthPx).coerceIn(0f, 1f)
                                        val targetPos = (fraction * duration).toLong()
                                        exoPlayer.seekTo(targetPos)
                                        currentPosition = targetPos
                                    }
                                }
                            }
                            .pointerInput(duration) {
                                detectHorizontalDragGestures { change, _ ->
                                    change.consume()
                                    if (widthPx > 0 && duration > 0) {
                                        val fraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                                        val targetPos = (fraction * duration).toLong()
                                        exoPlayer.seekTo(targetPos)
                                        currentPosition = targetPos
                                    }
                                }
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val fraction = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
                        val bufferedFraction = if (duration > 0) (bufferedPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(1.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(bufferedFraction)
                                    .fillMaxHeight()
                                    .background(Color.White.copy(alpha = 0.45f), RoundedCornerShape(1.dp))
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp))
                            )
                        }

                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    val thumbSizePx = 8.dp.toPx()
                                    val maxOffsetPx = widthPx - thumbSizePx
                                    translationX = fraction * maxOffsetPx
                                }
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 播放时间文本（代码保持原样）
                    Text(
                        text = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // 横屏模式下，画质按钮显示在全屏按钮左侧
                    if (isLandscape) {
                        Spacer(modifier = Modifier.width(8.dp))
                        // 1. 外层 Box 固定 36.dp 占位，保证底栏 Row 高度恒定，进度条等控件绝对不移动！
                        Box(
                            modifier = Modifier.height(36.dp),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            // 2. 内层 Box 使用 unbounded = true 解除 36.dp 限制，允许画质菜单完全向上展开！
                            Box(
                                modifier = Modifier.wrapContentSize(align = Alignment.BottomEnd, unbounded = true)
                            ) {
                                ResolutionSelectorBox()
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 右下角：全屏 / 退出全屏切换按钮
                    IconButton(
                        onClick = onFullscreenClick,
                        modifier = Modifier.size(topBtnSize)
                    ) {
                        Icon(
                            // 👈 横屏全屏时切换为 FullscreenExit 图标
                            imageVector = if (isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isLandscape) "退出全屏" else "全屏",
                            tint = Color.White,
                            modifier = Modifier.size(if (isLandscape) 28.dp else 24.dp)
                        )
                    }
                }
            }
        }
    }
}

// MediaPlayer (系统原生 VideoView 版)：极轻，没有任何外部第三方播放器依赖
@Composable
fun NativeMediaPlayer(videoUrl: String, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            android.widget.VideoView(ctx).apply {
                setVideoPath(videoUrl)
                val controller = android.widget.MediaController(ctx)
                controller.setAnchorView(this)
                setMediaController(controller) // 绑定最基础的原生 MediaController 条
                start() // 自动开始播放
            }
        },
        modifier = modifier
    )
}

// 获取直链 API 接口的原生响应报文
suspend fun fetchRawFormatsJson(fileUrl: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL(fileUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            val xVersion = calculateXVersion(fileUrl)
            if (xVersion.isNotEmpty()) {
                connection.setRequestProperty("X-Version", xVersion) // 动态注入校验头
            }
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            if (connection.responseCode == 200) {
                connection.inputStream.bufferedReader().readText()
            } else {
                "HTTP 状态异常: ${connection.responseCode} (${connection.responseMessage})"
            }
        } catch (e: Exception) {
            "连接异常: ${e.localizedMessage ?: e.message ?: "未知网络错误"}"
        }
    }
}

// ==================== 播放时间数值格式化辅助函数 ====================

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

// 依据 Iwara 安全风控规范，利用 SHA-1 动态签名生成 X-Version 请求头，解锁 540p/Source 等高画质
fun calculateXVersion(fileUrl: String): String {
    return try {
        // 1. 提取文件的 UUID
        val path = fileUrl.substringBefore("?")
        val fileId = path.split("/").last()

        // 2. 提取过期时间戳 expires
        val query = fileUrl.substringAfter("?")
        val expires = query.split("&")
            .find { it.startsWith("expires=") }
            ?.substringAfter("=") ?: ""

        // 3. 拼接系统当前最新服役的 Salt 盐值
        val salt = "mSvL05GfEmeEmsEYfGCnVpEjYgTJraJN"
        val rawString = "${fileId}_${expires}_$salt"

        // 4. 计算 SHA-1 并输出为 16 进制小写格式
        val digest = MessageDigest.getInstance("SHA-1")
        val bytes = digest.digest(rawString.toByteArray(Charsets.UTF_8))
        bytes.joinToString("") { String.format("%02x", it) }
    } catch (e: Exception) {
        e.printStackTrace()
        ""
    }
}