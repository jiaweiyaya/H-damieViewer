package com.jiaweiya.hdamieviewer.pages

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Close
import androidx.activity.compose.BackHandler
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.pulltorefresh.PullToRefreshBox

// ==================== Iwara API 数据实体定义 ====================

data class IwaraUser(
    val name: String?,
    val username: String?
)

data class IwaraFile(
    val id: String?
)

data class IwaraVideoItem(
    val id: String,
    val title: String,
    val numViews: Int?,
    val numLikes: Int?,
    val user: IwaraUser?,
    val file: IwaraFile?,
    val thumbnail: Int?
)

data class IwaraImageItem(
    val id: String,
    val title: String,
    val numViews: Int?,
    val numLikes: Int?,
    val user: IwaraUser?,
    val files: List<IwaraFile>?
)

data class IwaraVideoListResponse(val results: List<IwaraVideoItem>?)
data class IwaraImageListResponse(val results: List<IwaraImageItem>?)

// 客户端本地渲染模型
data class IwaraMedia(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val authorName: String,
    val views: Int,
    val likes: Int
)

// ==================== 网络数据拉取逻辑 ====================

suspend fun fetchIwaraVideos(): List<IwaraMedia> {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.iwara.tv/videos?sort=trending&limit=5&rating=all")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val json = reader.readText()
                val response = Gson().fromJson(json, IwaraVideoListResponse::class.java)
                response.results?.map { video ->
                    val fileId = video.file?.id ?: ""
                    // 解析缩略图标识符 (一般为两位数如 00, 01)
                    val thumbId = if (video.thumbnail != null) String.format("%02d", video.thumbnail) else "00"
                    val thumbUrl = if (fileId.isNotEmpty()) {
                        "https://files.iwara.tv/image/thumbnail/$fileId/thumbnail-$thumbId.jpg"
                    } else ""
                    IwaraMedia(
                        id = video.id,
                        title = video.title,
                        thumbnailUrl = thumbUrl,
                        authorName = video.user?.name ?: video.user?.username ?: "未知作者",
                        views = video.numViews ?: 0,
                        likes = video.numLikes ?: 0
                    )
                } ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

suspend fun fetchIwaraImages(): List<IwaraMedia> {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.iwara.tv/images?sort=trending&limit=4&rating=all")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val json = reader.readText()
                val response = Gson().fromJson(json, IwaraImageListResponse::class.java)
                response.results?.map { img ->
                    val fileId = img.files?.firstOrNull()?.id ?: ""
                    val thumbUrl = if (fileId.isNotEmpty()) {
                        "https://files.iwara.tv/image/thumbnail/$fileId/thumbnail-00.jpg"
                    } else ""
                    IwaraMedia(
                        id = img.id,
                        title = img.title,
                        thumbnailUrl = thumbUrl,
                        authorName = img.user?.name ?: img.user?.username ?: "未知作者",
                        views = img.numViews ?: 0,
                        likes = img.numLikes ?: 0
                    )
                } ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

fun formatCount(count: Int): String {
    return if (count >= 1000) {
        String.format("%.1fk", count / 1000.0)
    } else {
        count.toString()
    }
}

// ==================== UI 呈现页面 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // API 数据流状态管理
    var popularVideos by remember { mutableStateOf<List<IwaraMedia>>(emptyList()) }
    var popularImages by remember { mutableStateOf<List<IwaraMedia>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // 搜索状态管理
    var isSearchActive by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var isRefreshing by remember { mutableStateOf(false) }

    // 初次加载生命周期
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val videos = fetchIwaraVideos()
            val images = fetchIwaraImages()
            popularVideos = videos
            popularImages = images
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    // 动态判断当前是深色模式还是浅色模式（无需外部传参，基于背景亮度自动适配）
    val isAppDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // 外层包装 Box：用于在搜索面板激活时，使整个主页面自动高斯模糊
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .blur(if (isSearchActive) 12.dp else 0.dp), // 展开时背景虚化
            topBar = {
                TopAppBar(
                    title = {
                        SearchBar(
                            isSearchActive = isSearchActive,
                            onSearchActiveChange = { isSearchActive = it },
                            searchText = searchText,
                            onSearchTextChange = { searchText = it }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "菜单")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { focusManager.clearFocus() })
                    }
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            isRefreshing = true
                            coroutineScope.launch {
                                try {
                                    val videos = fetchIwaraVideos()
                                    val images = fetchIwaraImages()
                                    popularVideos = videos
                                    popularImages = images
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                } finally {
                                    isRefreshing = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 1. 精选大卡片
                            val featuredVideo = popularVideos.firstOrNull()
                            if (featuredVideo != null) {
                                FeaturedCard(mediaItem = featuredVideo)
                            } else {
                                FeaturedCardPlaceholder()
                            }

                            // 2. 热门视频网格区域
                            val gridVideos = popularVideos.drop(1).take(4)
                            MediaGridSection(
                                title = "热门视频推荐",
                                mediaList = gridVideos,
                                isVideo = true
                            )

                            // 3. 热门图片网格区域
                            val gridImages = popularImages.take(4)
                            MediaGridSection(
                                title = "精选图片推荐",
                                mediaList = gridImages,
                                isVideo = false
                            )

                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }

                VerticalScrollbar(scrollState = scrollState)
            }
        }

        // 【极具质感的原地拉伸遮罩层】：代替 Dialog 窗口，实现完美的边缘滑动物理效果
        if (isSearchActive) {
            var animateTrigger by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                animateTrigger = true
            }

            // 遮罩不透明度动画：浅色背景轻柔（0.15f），深色背景深邃（0.5f）
            val backdropAlpha by animateFloatAsState(
                targetValue = if (animateTrigger) (if (isAppDark) 0.5f else 0.15f) else 0f,
                animationSpec = tween(300),
                label = "bg_alpha"
            )

            // 扩张动画：左侧边距从未对齐位置 72.dp（避开菜单栏）弹性扩散到全屏边距 12.dp
            val paddingStart by animateDpAsState(
                targetValue = if (animateTrigger) 12.dp else 72.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "padding_start"
            )
            val paddingEnd by animateDpAsState(
                targetValue = if (animateTrigger) 12.dp else 16.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "padding_end"
            )
            val cardHeight by animateDpAsState(
                targetValue = if (animateTrigger) 500.dp else 40.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "card_height"
            )
            val cardCornerRadius by animateDpAsState(
                targetValue = if (animateTrigger) 16.dp else 8.dp,
                animationSpec = tween(300),
                label = "card_corner"
            )

            // 拦截物理返回键：优雅折叠退场
            BackHandler {
                coroutineScope.launch {
                    animateTrigger = false
                    kotlinx.coroutines.delay(220)
                    isSearchActive = false
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = backdropAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        animateTrigger = false
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(220)
                            isSearchActive = false
                        }
                    }
            ) {
                Card(
                    modifier = Modifier
                        .padding(start = paddingStart, end = paddingEnd) // 横向弹性拉伸
                        .statusBarsPadding()
                        .padding(top = 10.dp) // 高度像素级对齐
                        .fillMaxWidth()
                        .height(cardHeight)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* 拦截点击事件以防止穿透 */ },
                    shape = RoundedCornerShape(cardCornerRadius),
                    colors = CardDefaults.cardColors(
                        // 深色模式使用稍亮的容器背景色，浅色模式保持面色
                        containerColor = if (isAppDark) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                    ),
                    // 深色模式增加微弱发光的卡片细线外边缘，浅色模式无边框
                    border = if (isAppDark) BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)) else null,
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    if (cardHeight > 100.dp) {
                        SearchExpandedContent(
                            searchText = searchText,
                            onSearchTextChange = { searchText = it },
                            onSearchTriggered = { query ->
                                saveSearchHistory(context, query)
                                animateTrigger = false
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(220)
                                    isSearchActive = false
                                }
                            },
                            onClose = {
                                animateTrigger = false
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(220)
                                    isSearchActive = false
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchBar(
    isSearchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        onClick = {
            onSearchTextChange("") // 每次进入前，强制置空当前输入框
            onSearchActiveChange(true)
        },
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(end = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.1f)
        ),
        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f))
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Text(
                    text = "搜索内容...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
            }
        }
    }
}

// pages/HomeScreen.kt -> SearchBar 函数

// ==================== 搜索历史数据的持久化工具函数 ====================

fun getSearchHistory(context: Context): List<String> {
    val prefs = context.getSharedPreferences("HDAmieViewerDB", Context.MODE_PRIVATE)
    val json = prefs.getString("search_history", "[]") ?: "[]"
    return try {
        val array: Array<String>? = Gson().fromJson(json, Array<String>::class.java)
        array?.toList() ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

fun saveSearchHistory(context: Context, query: String) {
    if (query.trim().isEmpty()) return
    val prefs = context.getSharedPreferences("HDAmieViewerDB", Context.MODE_PRIVATE)
    val currentList = getSearchHistory(context).toMutableList()
    currentList.remove(query) // 去重
    currentList.add(0, query) // 插入到最新
    val limitedList = currentList.take(10) // 限制最大存储10条历史
    prefs.edit().putString("search_history", Gson().toJson(limitedList)).apply()
}

fun deleteSearchHistoryItem(context: Context, query: String) {
    val prefs = context.getSharedPreferences("HDAmieViewerDB", Context.MODE_PRIVATE)
    val currentList = getSearchHistory(context).toMutableList()
    currentList.remove(query)
    prefs.edit().putString("search_history", Gson().toJson(currentList)).apply()
}

// ==================== 新版：带流式动画的搜索组件 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(modifier: Modifier = Modifier) {
    var isSearchActive by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // --- 1. 静态未选中状态（图2样式：细灰色边框，小角，无焦点） ---
    OutlinedCard(
        onClick = {
            searchText = ""
            isSearchActive = true
        },
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp) // 统一为 40.dp
            .padding(end = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.1f)
        ),
        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f))
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Text(
                    text = "搜索内容...", // 占位词
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
            }
        }
    }

    // --- 2. 悬浮展开弹窗与物理弹性动画 ---
    if (isSearchActive) {
        Dialog(
            onDismissRequest = { isSearchActive = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false, // 允许对话框铺满宽度
                decorFitsSystemWindows = true
            )
        ) {
            // 控制动画展开的逻辑触发阀
            var animateTrigger by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                animateTrigger = true
            }

            // 半透明遮罩层背景动画
            val backdropAlpha by animateFloatAsState(
                targetValue = if (animateTrigger) 0.6f else 0f,
                animationSpec = tween(300),
                label = "backdrop_alpha"
            )

            // 悬浮窗口宽度比、高度、圆角过渡弹性动画
            val cardWidthFraction by animateFloatAsState(
                targetValue = if (animateTrigger) 0.95f else 0.8f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "card_width"
            )
            val cardHeight by animateDpAsState(
                targetValue = if (animateTrigger) 500.dp else 40.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "card_height"
            )
            val cardCornerRadius by animateDpAsState(
                targetValue = if (animateTrigger) 16.dp else 8.dp,
                animationSpec = tween(300),
                label = "card_corners"
            )

            // 劫持返回键：退出时实现弹性折叠动画
            BackHandler {
                coroutineScope.launch {
                    animateTrigger = false
                    kotlinx.coroutines.delay(220) // 延迟关闭以确保动画缩回
                    isSearchActive = false
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = backdropAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // 点击黑障区关闭
                        animateTrigger = false
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(220)
                            isSearchActive = false
                        }
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(cardWidthFraction)
                        .height(cardHeight)
                        .statusBarsPadding()  // 1. 自动适配不同手机的状态栏高度
                        .padding(top = 10.dp) // 2. 边距微调，使展开前的卡片上边缘与主页搜索框上边缘重合
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* 拦截点击 */ },
                    shape = RoundedCornerShape(cardCornerRadius),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    // 当高度扩张到一定阈值时才进行渲染，避免小高度时内容积压重叠
                    if (cardHeight.value > 100f) {
                        SearchExpandedContent(
                            searchText = searchText,
                            onSearchTextChange = { searchText = it },
                            onSearchTriggered = { query ->
                                saveSearchHistory(context, query)
                                Toast.makeText(context, "搜索：$query", Toast.LENGTH_SHORT).show()
                                // 触发搜索后折叠并退出
                                animateTrigger = false
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(220)
                                    isSearchActive = false
                                }
                            },
                            onClose = {
                                animateTrigger = false
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(220)
                                    isSearchActive = false
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FeaturedCard(mediaItem: IwaraMedia, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (mediaItem.thumbnailUrl.isNotEmpty()) {
                AsyncImage(
                    model = mediaItem.thumbnailUrl,
                    contentDescription = mediaItem.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF3F51B5), Color(0xFF00BCD4))
                            )
                        )
                )
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(56.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .align(Alignment.BottomStart)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Column {
                    Text(
                        text = mediaItem.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "作者: ${mediaItem.authorName}  |  👁 ${formatCount(mediaItem.views)}  |  ❤️ ${formatCount(mediaItem.likes)}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun FeaturedCardPlaceholder(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF3F51B5), Color(0xFF00BCD4))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("未获取到推荐数据，请检查网络", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MediaGridSection(
    title: String,
    mediaList: List<IwaraMedia>,
    isVideo: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        val chunked = mediaList.chunked(2)
        for (row in 0 until 2) {
            val rowItems = chunked.getOrNull(row) ?: emptyList()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (col in 0 until 2) {
                    val mediaItem = rowItems.getOrNull(col)
                    if (mediaItem != null) {
                        MediaItemCard(
                            mediaItem = mediaItem,
                            isVideo = isVideo,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f)) // 用于在数据不齐时维持网格对齐
                    }
                }
            }
        }
    }
}

@Composable
fun MediaItemCard(mediaItem: IwaraMedia, isVideo: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (mediaItem.thumbnailUrl.isNotEmpty()) {
                    AsyncImage(
                        model = mediaItem.thumbnailUrl,
                        contentDescription = mediaItem.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = if (isVideo) {
                                        listOf(Color(0xFFE91E63), Color(0xFFFF9800))
                                    } else {
                                        listOf(Color(0xFF009688), Color(0xFF4CAF50))
                                    }
                                )
                            )
                    )
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isVideo) Icons.Default.PlayCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = mediaItem.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        Text(
            text = "${mediaItem.authorName}  |  👁 ${formatCount(mediaItem.views)}  |  ❤️ ${formatCount(mediaItem.likes)}",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Composable
fun BoxScope.VerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .width(4.dp)
            .align(Alignment.CenterEnd)
    ) {
        val totalHeight = size.height
        val maxValue = scrollState.maxValue.toFloat()
        if (maxValue > 0f) {
            val value = scrollState.value.toFloat()
            val viewportHeight = totalHeight
            val contentHeight = maxValue + viewportHeight
            val thumbHeight = (viewportHeight / contentHeight) * totalHeight
            val thumbTop = (value / maxValue) * (totalHeight - thumbHeight)

            drawRoundRect(
                color = Color.Gray.copy(alpha = 0.5f),
                topLeft = androidx.compose.ui.geometry.Offset(size.width - 4.dp.toPx(), thumbTop),
                size = androidx.compose.ui.geometry.Size(4.dp.toPx(), thumbHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }
    }
}

// ==================== 展开后的搜索面板布局 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchExpandedContent(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onSearchTriggered: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var historyList by remember { mutableStateOf(getSearchHistory(context)) }
    var showDeleteDialogFor by remember { mutableStateOf<String?>(null) }

    // 唤起时自动获取输入焦点
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 顶部输入：图1样式（OutlinedTextField：Label 嵌在边框线上）+ 放大镜按键
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically, // 垂直方向居中对齐
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val interactionSource = remember { MutableInteractionSource() }

            // 使用 BasicTextField 配合 M3 官方 outlined 包装盒定制紧凑版输入框
            androidx.compose.foundation.text.BasicTextField(
                value = searchText,
                onValueChange = onSearchTextChange,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp) // 1. 将输入框高度强制缩减为与右侧按钮完全一致的 48.dp
                    .focusRequester(focusRequester),
                interactionSource = interactionSource,
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                )
            ) { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = searchText,
                    innerTextField = innerTextField,
                    enabled = true,
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interactionSource,
                    label = { Text("搜索内容...", fontSize = 12.sp) }, // 2. 完美保留图 1 浮在边框上的 Label 样式
                    colors = OutlinedTextFieldDefaults.colors(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp), // 3. 压缩垂直内边距防止文字和光标被裁剪
                    trailingIcon = {
                        if (searchText.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchTextChange("") }, // 点击重置内容为空
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "清空内容",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = true,
                            isError = false,
                            interactionSource = interactionSource,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                )
            }

            // 搜索按键（高度保持为 48.dp）
            IconButton(
                onClick = { if (searchText.isNotBlank()) onSearchTriggered(searchText) },
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 历史数据大标题
        if (historyList.isNotEmpty()) {
            Text(
                text = "搜索历史",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // 历史数据纵向列表
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(historyList) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSearchTriggered(item) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = item,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 删除单条历史 X 按钮
                    IconButton(
                        onClick = { showDeleteDialogFor = item },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "删除历史",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        }
    }

    // 确认删除历史的二次确认询问对话框
    if (showDeleteDialogFor != null) {
        val targetItem = showDeleteDialogFor!!
        AlertDialog(
            onDismissRequest = { showDeleteDialogFor = null },
            title = { Text("确认删除？", fontWeight = FontWeight.Bold) },
            text = { Text("确定要删除搜索历史 \"$targetItem\" 吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteSearchHistoryItem(context, targetItem)
                        historyList = getSearchHistory(context) // 重新拉取同步本地状态
                        showDeleteDialogFor = null
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialogFor = null }) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}