package com.jiaweiya.hdamieviewer.iwara

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.jiaweiya.hdamieviewer.pages.DebugLogger
import com.jiaweiya.hdamieviewer.pages.IwaraActionHandler
import com.jiaweiya.hdamieviewer.pages.IwaraMedia
import com.jiaweiya.hdamieviewer.pages.MediaItemCard
import com.jiaweiya.hdamieviewer.pages.SearchExpandedContent
import com.jiaweiya.hdamieviewer.pages.fixImageUrl
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.filled.Delete

data class IwaraUserProfile(
    val id: String = "",
    val name: String = "",
    val username: String = "",
    val avatarUrl: String = "",
    val headerUrl: String = "",
    val about: String = "",
    val isFollowing: Boolean = false,
    val joinedTimeStr: String = "",
    val lastSeenTimeStr: String = ""
)

// 快速滑动检测 Hook (1秒内滑动超过4行判定为快速滑动，仅加载本地缓存，禁用网络请求)
@Composable
fun rememberIsFastScrolling(
    gridState: LazyGridState,
    rowsPerSecThreshold: Float = 4f
): Boolean {
    var isFastScrolling by remember { mutableStateOf(false) }
    var lastIndex by remember { mutableIntStateOf(0) }
    var lastTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(gridState.firstVisibleItemIndex) {
        val currentIndex = gridState.firstVisibleItemIndex
        val currentTime = System.currentTimeMillis()
        val timeDiffSec = (currentTime - lastTime) / 1000f

        if (timeDiffSec > 0.05f) {
            val indexDiff = kotlin.math.abs(currentIndex - lastIndex)
            val rowsDiff = indexDiff / 2f
            val rowsPerSec = rowsDiff / timeDiffSec

            if (rowsPerSec > rowsPerSecThreshold) {
                isFastScrolling = true
            }
            lastIndex = currentIndex
            lastTime = currentTime
        }
    }

    LaunchedEffect(gridState.isScrollInProgress) {
        if (!gridState.isScrollInProgress) {
            kotlinx.coroutines.delay(150)
            isFastScrolling = false
        }
    }

    return isFastScrolling
}

object ProfileActionHandler {
    var onProfileInfoResult: ((IwaraUserProfile) -> Unit)? = null
    var onProfilePartialResult: ((type: String, list: List<IwaraMedia>) -> Unit)? = null
    var onProfilePageResult: ((type: String, page: Int, hasMore: Boolean, list: List<IwaraMedia>) -> Unit)? = null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IwaraProfilePage(
    username: String,
    onBackClick: () -> Unit,
    onVideoClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var userProfile by remember { mutableStateOf(IwaraUserProfile()) }
    var authorVideos by remember { mutableStateOf<List<IwaraMedia>>(emptyList()) }
    var authorImages by remember { mutableStateOf<List<IwaraMedia>>(emptyList()) }
    var authorPosts by remember { mutableStateOf<List<IwaraMedia>>(emptyList()) }

    var videoPage by remember { mutableIntStateOf(0) }
    var hasMoreVideos by remember { mutableStateOf(true) }
    var isVideoLoadingMore by remember { mutableStateOf(false) }

    var imagePage by remember { mutableIntStateOf(0) }
    var hasMoreImages by remember { mutableStateOf(true) }
    var isImageLoadingMore by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    var isFollowLoading by remember { mutableStateOf(false) }

    var isSearchActive by remember { mutableStateOf(false) }

    var searchText by remember { mutableStateOf("") }

    var debugLog by remember { mutableStateOf("") }
    var showDebugDialog by remember { mutableStateOf(false) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isWebViewReady by remember { mutableStateOf(false) }

    val tabs = listOf("主页", "视频", "图片")
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { tabs.size })

    fun appendLog(msg: String) {
        val timeStr = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        val formattedLine = if (msg.startsWith("[")) msg else "[$timeStr] $msg"
        debugLog = if (debugLog.isBlank()) "$formattedLine\n" else "$debugLog$formattedLine\n"
    }

    LaunchedEffect(Unit) {
        DebugLogger.onLog = { msg -> appendLog(msg) }
    }

    fun loadProfileData() {
        val wv = webViewRef ?: return
        isLoading = true
        videoPage = 0
        imagePage = 0
        hasMoreVideos = true
        hasMoreImages = true
        debugLog = ""
        appendLog("=== 开启 1x1 WebView 纯 DOM 页面爬取: $username ===")

        ProfileActionHandler.onProfileInfoResult = { profile ->
            userProfile = profile
            isLoading = false
            isRefreshing = false
        }

        ProfileActionHandler.onProfilePartialResult = { type, list ->
            when (type) {
                "videos" -> authorVideos = list
                "images" -> authorImages = list
                "posts" -> authorPosts = list
            }
        }

        ProfileActionHandler.onProfilePageResult = { type, page, hasMore, list ->
            if (type == "videos") {
                authorVideos = if (page == 0) list else (authorVideos + list).distinctBy { it.id }
                hasMoreVideos = hasMore
                isVideoLoadingMore = false
            } else if (type == "images") {
                authorImages = if (page == 0) list else (authorImages + list).distinctBy { it.id }
                hasMoreImages = hasMore
                isImageLoadingMore = false
            }
        }

        // 👈 彻底改用 WebView 加载真实目标作者页面
        val cleanUser = username.replace("@", "").trim()
        wv.loadUrl("https://www.iwara.tv/profile/$cleanUser")
    }

    fun fetchNextPage(isVideo: Boolean) {
        val wv = webViewRef ?: return
        if (isVideo) {
            if (isVideoLoadingMore || !hasMoreVideos) return
            isVideoLoadingMore = true
            val nextPage = videoPage + 1
            videoPage = nextPage
            wv.evaluateJavascript("window.fetchUserMediaPage('videos', $nextPage, 'videos');", null)
        } else {
            if (isImageLoadingMore || !hasMoreImages) return
            isImageLoadingMore = true
            val nextPage = imagePage + 1
            imagePage = nextPage
            wv.evaluateJavascript("window.fetchUserMediaPage('images', $nextPage, 'images');", null)
        }
    }

    LaunchedEffect(isWebViewReady) {
        if (isWebViewReady) {
            loadProfileData()
        }
    }

    val isAppDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .blur(if (isSearchActive) 12.dp else 0.dp),
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            OutlinedCard(
                                onClick = {
                                    searchText = ""
                                    isSearchActive = true
                                },
                                modifier = Modifier
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
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                            Text(
                                                text = if (searchText.isNotBlank()) searchText else "搜索作者的作品...",
                                                color = if (searchText.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (searchText.isNotEmpty()) {
                                            IconButton(
                                                onClick = { searchText = "" },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "清空搜索",
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        }
                    )

                    SecondaryTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                text = {
                                    Text(
                                        text = title,
                                        fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp
                                    )
                                }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            isRefreshing = true
                            loadProfileData()
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when (page) {
                            0 -> AuthorHomeTabContent(
                                profile = userProfile,
                                latestVideos = authorVideos,
                                posts = authorPosts,
                                isLoading = isLoading,
                                isFollowLoading = isFollowLoading,
                                onVideoClick = onVideoClick,
                                onAuthorClick = onAuthorClick,
                                onFollowClick = {
                                    if (isFollowLoading) return@AuthorHomeTabContent
                                    val iwaraAccount = com.jiaweiya.hdamieviewer.iwara.IwaraAccountManager.loadUser(context)
                                    if (!iwaraAccount.isLoggedIn) {
                                        android.widget.Toast.makeText(context, "请先登录 Iwara 账号", android.widget.Toast.LENGTH_SHORT).show()
                                        return@AuthorHomeTabContent
                                    }
                                    isFollowLoading = true
                                    com.jiaweiya.hdamieviewer.pages.executeFollowViaWebView(
                                        webView = webViewRef,
                                        userId = userProfile.id,
                                        username = userProfile.username.ifEmpty { userProfile.name },
                                        isFollowingNow = userProfile.isFollowing,
                                        token = iwaraAccount.token
                                    ) { success, logText ->
                                        isFollowLoading = false
                                        if (success) {
                                            userProfile = userProfile.copy(isFollowing = !userProfile.isFollowing)
                                            android.widget.Toast.makeText(context, if (userProfile.isFollowing) "已关注作者" else "已取消关注", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            android.widget.Toast.makeText(context, "关注操作失败，可点击调试日志排查", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )

                            1 -> AuthorMediaListTabContent(
                                items = authorVideos,
                                isVideo = true,
                                isLoading = isVideoLoadingMore || isLoading,
                                hasMore = hasMoreVideos,
                                searchKeyword = searchText,
                                onLoadMore = { fetchNextPage(isVideo = true) },
                                onVideoClick = onVideoClick,
                                onAuthorClick = onAuthorClick
                            )

                            2 -> AuthorMediaListTabContent(
                                items = authorImages,
                                isVideo = false,
                                isLoading = isImageLoadingMore || isLoading,
                                hasMore = hasMoreImages,
                                searchKeyword = searchText,
                                onLoadMore = { fetchNextPage(isVideo = false) },
                                onVideoClick = onVideoClick,
                                onAuthorClick = onAuthorClick
                            )
                        }
                    }
                }

                FloatingActionButton(
                    onClick = { showDebugDialog = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 24.dp),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("调试日志", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (isSearchActive) {
            var animateTrigger by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { animateTrigger = true }

            val backdropAlpha by animateFloatAsState(
                targetValue = if (animateTrigger) (if (isAppDark) 0.5f else 0.15f) else 0f,
                animationSpec = tween(300),
                label = "bg_alpha"
            )

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
                        .padding(start = paddingStart, end = paddingEnd)
                        .statusBarsPadding()
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .height(cardHeight),
                    shape = RoundedCornerShape(cardCornerRadius),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAppDark) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    if (cardHeight > 100.dp) {
                        SearchExpandedContent(
                            searchText = searchText,
                            onSearchTextChange = { searchText = it },
                            onSearchTriggered = { query, _, _ ->
                                searchText = query
                                animateTrigger = false
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(220)
                                    isSearchActive = false
                                    if (pagerState.currentPage == 0) {
                                        pagerState.animateScrollToPage(1)
                                    }
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

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(0)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

                    CookieManager.getInstance().setAcceptCookie(true)

                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                            val msg = consoleMessage?.message() ?: ""
                            if (msg.startsWith("PROFILE_DEBUG_LOG:")) {
                                appendLog(msg.removePrefix("PROFILE_DEBUG_LOG:"))
                                return true
                            } else if (msg.startsWith("IWARA_ACTION_RESULT:")) {
                                val jsonStr = msg.removePrefix("IWARA_ACTION_RESULT:")
                                try {
                                    val jsonObj = com.google.gson.JsonParser.parseString(jsonStr).asJsonObject
                                    val action = jsonObj.get("action")?.asString ?: ""
                                    val success = jsonObj.get("success")?.asBoolean ?: false
                                    val log = jsonObj.get("log")?.asString ?: ""

                                    IwaraActionHandler.onActionResult?.invoke(action, success, log)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                return true
                            } else if (msg.startsWith("IWARA_PROFILE_INFO:")) {
                                val jsonStr = msg.removePrefix("IWARA_PROFILE_INFO:")
                                try {
                                    val pObj = com.google.gson.JsonParser.parseString(jsonStr).asJsonObject
                                    val newHeader = pObj.get("headerUrl")?.asString ?: ""
                                    val newAbout = pObj.get("about")?.asString ?: ""

                                    // 👈 核心修改：使用 ifEmpty 保护，绝不用空值覆盖已有背景图与简介！
                                    val profile = IwaraUserProfile(
                                        id = pObj.get("id")?.asString ?: userProfile.id,
                                        name = pObj.get("name")?.asString ?: userProfile.name,
                                        username = pObj.get("username")?.asString ?: userProfile.username,
                                        avatarUrl = pObj.get("avatarUrl")?.asString ?: userProfile.avatarUrl,
                                        headerUrl = newHeader.ifEmpty { userProfile.headerUrl },
                                        about = newAbout.ifEmpty { userProfile.about },
                                        isFollowing = pObj.get("isFollowing")?.asBoolean ?: userProfile.isFollowing,
                                        joinedTimeStr = pObj.get("joinedTimeStr")?.asString ?: userProfile.joinedTimeStr,
                                        lastSeenTimeStr = pObj.get("lastSeenTimeStr")?.asString ?: userProfile.lastSeenTimeStr
                                    )
                                    ProfileActionHandler.onProfileInfoResult?.invoke(profile)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                return true
                            } else if (msg.startsWith("IWARA_PROFILE_PARTIAL:")) {
                                val jsonStr = msg.removePrefix("IWARA_PROFILE_PARTIAL:")
                                try {
                                    val jsonObj = com.google.gson.JsonParser.parseString(jsonStr).asJsonObject
                                    val type = jsonObj.get("type").asString
                                    val arr = jsonObj.getAsJsonArray("data")

                                    val list = mutableListOf<IwaraMedia>()
                                    arr.forEach { el ->
                                        val obj = el.asJsonObject
                                        list.add(
                                            IwaraMedia(
                                                id = obj.get("id").asString,
                                                title = obj.get("title").asString,
                                                thumbnailUrl = obj.get("thumbnailUrl").asString,
                                                authorName = obj.get("authorName").asString,
                                                authorAvatarUrl = obj.get("authorAvatarUrl")?.asString ?: "",
                                                authorUsername = obj.get("authorUsername")?.asString ?: "",
                                                viewsStr = obj.get("viewsStr")?.asString ?: "",
                                                likesStr = obj.get("likesStr")?.asString ?: "",
                                                durationStr = obj.get("durationStr")?.asString ?: "",
                                                ratingStr = obj.get("ratingStr")?.asString ?: "",
                                                timeAgoStr = obj.get("timeAgoStr")?.asString ?: "",
                                                galleryCountStr = obj.get("galleryCountStr")?.asString ?: ""
                                            )
                                        )
                                    }
                                    ProfileActionHandler.onProfilePartialResult?.invoke(type, list)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                return true
                            } else if (msg.startsWith("IWARA_PROFILE_PAGE:")) {
                                val jsonStr = msg.removePrefix("IWARA_PROFILE_PAGE:")
                                try {
                                    val jsonObj = com.google.gson.JsonParser.parseString(jsonStr).asJsonObject
                                    val type = jsonObj.get("type").asString
                                    val page = jsonObj.get("page").asInt
                                    val hasMore = jsonObj.get("hasMore").asBoolean
                                    val arr = jsonObj.getAsJsonArray("data")

                                    val list = mutableListOf<IwaraMedia>()
                                    arr.forEach { el ->
                                        val obj = el.asJsonObject
                                        list.add(
                                            IwaraMedia(
                                                id = obj.get("id").asString,
                                                title = obj.get("title").asString,
                                                thumbnailUrl = obj.get("thumbnailUrl").asString,
                                                authorName = obj.get("authorName").asString,
                                                authorAvatarUrl = obj.get("authorAvatarUrl")?.asString ?: "",
                                                authorUsername = obj.get("authorUsername")?.asString ?: "",
                                                viewsStr = obj.get("viewsStr")?.asString ?: "",
                                                likesStr = obj.get("likesStr")?.asString ?: "",
                                                durationStr = obj.get("durationStr")?.asString ?: "",
                                                ratingStr = obj.get("ratingStr")?.asString ?: "",
                                                timeAgoStr = obj.get("timeAgoStr")?.asString ?: "",
                                                galleryCountStr = obj.get("galleryCountStr")?.asString ?: ""
                                            )
                                        )
                                    }
                                    ProfileActionHandler.onProfilePageResult?.invoke(type, page, hasMore, list)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                return true
                            }
                            return super.onConsoleMessage(consoleMessage)
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            webViewRef = view
                            isWebViewReady = true

                            // 👈 核心修改：仅当 WebView 导航到作者主页时，注入纯 DOM 爬虫脚本
                            if (url != null && url.contains("/profile/")) {
                                val cleanUser = username.replace("@", "").trim()
                                val domScraperJs = """
                                    (async function() {
                                        function sendLog(msg) {
                                            let now = new Date();
                                            let timeStr = now.toTimeString().split(' ')[0] + '.' + String(now.getMilliseconds()).padStart(3, '0');
                                            console.log("PROFILE_DEBUG_LOG:[" + timeStr + "] " + msg);
                                        }

                                        let cleanUser = '$cleanUser';
                                        sendLog('=== 1. 开启 API 资料 + DOM 动态轮询融合抓取 ===');

                                        try {
                                            let token = localStorage.getItem('token') || localStorage.getItem('access_token') || '';
                                            let headers = { 'Accept': 'application/json, text/plain, */*' };
                                            if (token) headers['Authorization'] = 'Bearer ' + token;

                                            // 1. 获取作者 User UUID
                                            let startTime = Date.now();
                                            sendLog('• 发起 [作者资料 API]: GET https://api.iwara.tv/profile/' + cleanUser);
                                            let uRes = await fetch('https://api.iwara.tv/profile/' + cleanUser, { headers }).catch(e => null);
                                            if (!uRes || !uRes.ok) {
                                                uRes = await fetch('https://api.iwara.tv/user/' + cleanUser, { headers }).catch(e => null);
                                            }

                                            let userData = (uRes && uRes.ok) ? await uRes.json() : null;
                                            let u = userData ? (userData.user || userData) : {};
                                            let userId = u.id || '';

                                            function formatAvatar(uObj) {
                                                if (!uObj || !uObj.avatar) return '';
                                                let a = uObj.avatar;
                                                let id = a.id || '';
                                                let name = a.name || '';
                                                if (id && name) return 'https://files.iwara.tv/image/avatar/' + id + '/' + name;
                                                if (id) return 'https://files.iwara.tv/image/avatar/' + id + '/avatar.jpg';
                                                return '';
                                            }

                                            function formatHeader(uObj) {
                                                if (!uObj) return '';
                                                let h = uObj.profileHeader || uObj.header || uObj.banner || (uObj.user ? (uObj.user.profileHeader || uObj.user.header || uObj.user.banner) : null);
                                                if (!h) return '';
                                                let id = typeof h === 'object' ? (h.id || '') : h;
                                                let name = typeof h === 'object' ? (h.name || (id + '.jpg')) : (id + '.jpg');
                                                if (id) return 'https://files.iwara.tv/image/profileHeader/' + id + '/' + name;
                                                return '';
                                            }

                                            // 立即发送基础资料上屏
                                            let profileInfo = {
                                                id: userId || cleanUser,
                                                name: u.name || u.username || cleanUser,
                                                username: u.username || cleanUser,
                                                avatarUrl: formatAvatar(u),
                                                headerUrl: formatHeader(u),
                                                about: u.about || u.description || (userData && userData.about) || '',
                                                isFollowing: !!(u.following || u.isFollowing || (userData && userData.following)),
                                                joinedTimeStr: u.createdAt ? (new Date(u.createdAt).toLocaleDateString()) : '',
                                                lastSeenTimeStr: u.seenAt ? (new Date(u.seenAt).toLocaleDateString()) : ''
                                            };

                                            sendLog('🎉 [作者资料 API 完成] 作者: ' + profileInfo.name + ' (User ID: ' + userId + ')');
                                            console.log("IWARA_PROFILE_INFO:" + JSON.stringify(profileInfo));

                                            // 2. DOM 动态轮询观察器（持续观察 5 秒，等待 Vue 渲染背景图、简介与评论区）
                                            let maxChecks = 20; // 20 * 250ms = 5秒
                                            let domTimer = setInterval(() => {
                                                // (1) 轮询提取 Header 背景图
                                                let headerImg = document.querySelector('.page-profile__header__background img');
                                                let domHeaderUrl = headerImg ? (headerImg.getAttribute('src') || headerImg.src) : '';
                                                if (domHeaderUrl.startsWith('//')) domHeaderUrl = 'https:' + domHeaderUrl;
                                                if (domHeaderUrl.includes('i.iwara.tv')) domHeaderUrl = domHeaderUrl.replace('i.iwara.tv', 'files.iwara.tv');

                                                // (2) 轮询提取简介 (.page-profile__content 内部)
                                                let domBioEl = document.querySelector('.page-profile__content .showMore__wrapper .markdown') || document.querySelector('.page-profile__content .markdown');
                                                let domBioText = domBioEl ? domBioEl.innerText.trim() : '';

                                                // (3) 轮询提取评论区
                                                let commentContainer = document.querySelector('.comments');
                                                let parsedComments = [];
                                                if (commentContainer) {
                                                    let colItems = commentContainer.querySelectorAll('.row .col-12, .row > div');
                                                    colItems.forEach((node, idx) => {
                                                        let textEl = node.querySelector('.comment__body') || node.querySelector('.text') || node;
                                                        let userEl = node.querySelector('.username') || node.querySelector('.text--bold') || node.querySelector('a');
                                                        let avatarEl = node.querySelector('.avatar img') || node.querySelector('img');

                                                        let text = textEl ? textEl.innerText.trim() : '';
                                                        if (text && !text.startsWith('Comments') && !text.startsWith('添加评论')) {
                                                            let avatarSrc = avatarEl ? (avatarEl.getAttribute('src') || avatarEl.src) : '';
                                                            if (avatarSrc.startsWith('//')) avatarSrc = 'https:' + avatarSrc;
                                                            if (avatarSrc.includes('i.iwara.tv')) avatarSrc = avatarSrc.replace('i.iwara.tv', 'files.iwara.tv');

                                                            parsedComments.push({
                                                                id: 'dom_c_' + idx + '_' + Math.random().toString(36).substring(2, 5),
                                                                title: text,
                                                                thumbnailUrl: '',
                                                                authorName: userEl ? userEl.innerText.trim() : 'i站用户',
                                                                authorAvatarUrl: avatarSrc,
                                                                authorUsername: '',
                                                                viewsStr: '',
                                                                likesStr: '',
                                                                timeAgoStr: ''
                                                            });
                                                        }
                                                    });
                                                }

                                                let updatedInfo = false;

                                                // 补全背景图
                                                if (domHeaderUrl && profileInfo.headerUrl !== domHeaderUrl) {
                                                    profileInfo.headerUrl = domHeaderUrl;
                                                    sendLog('🎉 [DOM 轮询观察器] 成功捕获 Header 背景图');
                                                    updatedInfo = true;
                                                }

                                                // 抓到真实简介，刷新资料
                                                if (domBioText && profileInfo.about !== domBioText) {
                                                    profileInfo.about = domBioText;
                                                    sendLog('🎉 [DOM 轮询观察器] 成功捕获真实简介 (' + domBioText.length + ' 字)');
                                                    updatedInfo = true;
                                                }

                                                if (updatedInfo) {
                                                    console.log("IWARA_PROFILE_INFO:" + JSON.stringify(profileInfo));
                                                }

                                                // 抓到评论区，刷新列表
                                                if (parsedComments.length > 0) {
                                                    sendLog('🎉 [DOM 轮询观察器] 成功捕获评论区 (' + parsedComments.length + ' 条)');
                                                    console.log("IWARA_PROFILE_PARTIAL:" + JSON.stringify({ type: 'posts', data: parsedComments }));
                                                }

                                                maxChecks--;
                                                if ((profileInfo.headerUrl && profileInfo.about && parsedComments.length > 0) || maxChecks <= 0) {
                                                    clearInterval(domTimer);
                                                }
                                            }, 250);

                                            // 3. 视频与图片列表分页
                                            window.fetchUserMediaPage = async function(endpoint, p, typeKey) {
                                                let targetId = userId || cleanUser;
                                                let url = 'https://api.iwara.tv/' + endpoint + '?user=' + targetId + '&page=' + p + '&limit=24';
                                                sendLog('• 发起 [' + endpoint + ' 第' + p + '页]: GET ' + url);
                                                try {
                                                    let r = await fetch(url, { headers });
                                                    if (!r.ok) return;
                                                    let data = await r.json();
                                                    let results = data ? (data.results || []) : [];

                                                    function formatCount(num) {
                                                        if (!num) return '0';
                                                        if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M';
                                                        if (num >= 1000) return (num / 1000).toFixed(1) + 'K';
                                                        return num.toString();
                                                    }
                                                    function formatDur(sec) {
                                                        if (!sec) return '';
                                                        let m = Math.floor(sec / 60);
                                                        let s = Math.floor(sec % 60);
                                                        return m + ':' + (s < 10 ? '0' : '') + s;
                                                    }
                                                    function formatImageThumb(item) {
                                                        if (!item) return '';
                                                        let t = item.thumbnail || item.file || (item.files && item.files[0] ? item.files[0] : null);
                                                        if (!t) return '';
                                                        let id = typeof t === 'object' ? (t.id || '') : t;
                                                        let name = typeof t === 'object' ? (t.name || (id + '.jpg')) : (id + '.jpg');
                                                        if (id) return 'https://files.iwara.tv/image/thumbnail/' + id + '/' + name;
                                                        return '';
                                                    }
                                                    function formatVideoThumb(item) {
                                                        if (!item || !item.file) return '';
                                                        let fileId = item.file.id || '';
                                                        let thumbId = item.thumbnail !== undefined && item.thumbnail !== null ? String(item.thumbnail).padStart(2, '0') : '00';
                                                        return 'https://files.iwara.tv/image/thumbnail/' + fileId + '/thumbnail-' + thumbId + '.jpg';
                                                    }
                                                    function formatGalleryCount(item) {
                                                        if (!item) return '';
                                                        let count = 0;
                                                        if (item.numImages !== undefined && item.numImages !== null) count = Number(item.numImages);
                                                        else if (Array.isArray(item.files)) count = item.files.length;
                                                        else if (item.numFiles !== undefined && item.numFiles !== null) count = Number(item.numFiles);
                                                        return Number.isFinite(count) && count > 1 ? String(count) : '';
                                                    }

                                                    let parsed = results.map(item => ({
                                                        id: item.id,
                                                        title: item.title,
                                                        thumbnailUrl: (endpoint === 'images') ? formatImageThumb(item) : formatVideoThumb(item),
                                                        authorName: profileInfo.name,
                                                        authorAvatarUrl: profileInfo.avatarUrl,
                                                        authorUsername: profileInfo.username,
                                                        viewsStr: formatCount(item.numViews),
                                                        likesStr: formatCount(item.numLikes),
                                                        durationStr: formatDur(item.duration),
                                                        ratingStr: (item.rating === 'ecchi') ? 'R-18' : '',
                                                        timeAgoStr: item.createdAt ? (new Date(item.createdAt).toLocaleDateString()) : '',
                                                        galleryCountStr: formatGalleryCount(item)
                                                    }));

                                                    sendLog('🎉 [' + endpoint + ' 第' + p + '页] 成功 (共 ' + parsed.length + ' 条)');
                                                    console.log("IWARA_PROFILE_PAGE:" + JSON.stringify({
                                                        type: typeKey,
                                                        page: p,
                                                        hasMore: results.length >= 24,
                                                        data: parsed
                                                    }));
                                                } catch(e) {
                                                    sendLog('❌ [' + endpoint + ' 第' + p + '页] 异常: ' + e);
                                                }
                                            };

                                            window.fetchUserMediaPage('videos', 0, 'videos');
                                            window.fetchUserMediaPage('images', 0, 'images');

                                        } catch(e) {
                                            sendLog('❌ 抓取捕获异常: ' + e.toString());
                                        }
                                    })();
                                """.trimIndent()

                                view?.evaluateJavascript(domScraperJs, null)
                            }
                        }
                    }
                    loadUrl("https://www.iwara.tv/")
                }
            },
            modifier = Modifier.size(1.dp).graphicsLayer { alpha = 0.01f }
        )
    }

    if (showDebugDialog) {
        AlertDialog(
            onDismissRequest = { showDebugDialog = false },
            title = { Text("作者主页调试日志控制台", fontWeight = FontWeight.Bold) },
            text = {
                Box(
                    modifier = Modifier
                        .height(300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            text = if (debugLog.isEmpty()) "正在加载数据中..." else debugLog,
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showDebugDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}

// 1. 作者主页 "主页" Tab 内容
@Composable
private fun AuthorHomeTabContent(
    profile: IwaraUserProfile,
    latestVideos: List<IwaraMedia>,
    posts: List<IwaraMedia>,
    isLoading: Boolean,
    isFollowLoading: Boolean = false,
    onVideoClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit,
    onFollowClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val cookie = remember { CookieManager.getInstance().getCookie("https://www.iwara.tv") ?: "" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val fixedHeaderUrl = remember(profile.headerUrl) { fixImageUrl(profile.headerUrl) }
                val fixedAvatarUrl = remember(profile.avatarUrl) { fixImageUrl(profile.avatarUrl) }

                val headerRequest = remember(fixedHeaderUrl, cookie) {
                    if (fixedHeaderUrl.isNotEmpty()) {
                        coil.request.ImageRequest.Builder(context)
                            .data(fixedHeaderUrl)
                            .crossfade(true)
                            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                            .addHeader("Referer", "https://www.iwara.tv/")
                            .apply { if (cookie.isNotEmpty()) addHeader("Cookie", cookie) }
                            .listener(
                                onStart = { DebugLogger.log("🖼️ [作者Banner发起] ...${fixedHeaderUrl.takeLast(40)}") },
                                onSuccess = { _, result -> DebugLogger.log("🎉 [作者Banner成功] ...${fixedHeaderUrl.takeLast(40)} (来源:${result.dataSource})") },
                                onError = { _, result -> DebugLogger.log("❌ [作者Banner失败] ...${fixedHeaderUrl.takeLast(40)} (原因:${result.throwable.message})") }
                            )
                            .build()
                    } else null
                }

                val avatarRequest = remember(fixedAvatarUrl, cookie) {
                    if (fixedAvatarUrl.isNotEmpty()) {
                        coil.request.ImageRequest.Builder(context)
                            .data(fixedAvatarUrl)
                            .crossfade(true)
                            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                            .addHeader("Referer", "https://www.iwara.tv/")
                            .apply { if (cookie.isNotEmpty()) addHeader("Cookie", cookie) }
                            .listener(
                                onStart = { DebugLogger.log("👤 [作者头像发起] ...${fixedAvatarUrl.takeLast(40)}") },
                                onSuccess = { _, result -> DebugLogger.log("🎉 [作者头像成功] ...${fixedAvatarUrl.takeLast(40)} (来源:${result.dataSource})") },
                                onError = { _, result -> DebugLogger.log("❌ [作者头像失败] ...${fixedAvatarUrl.takeLast(40)} (原因:${result.throwable.message})") }
                            )
                            .build()
                    } else null
                }

                if (headerRequest != null) {
                    AsyncImage(
                        model = headerRequest,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(listOf(Color(0xFF3F51B5), Color(0xFF00BCD4))))
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    if (profile.joinedTimeStr.isNotEmpty()) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "加入于 ${profile.joinedTimeStr}",
                                color = Color.White,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (profile.lastSeenTimeStr.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "最后登录: ${profile.lastSeenTimeStr}",
                                color = Color.White,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (avatarRequest != null) {
                        AsyncImage(
                            model = avatarRequest,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = profile.name.ifEmpty { "A" }.take(1).uppercase(),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.name.ifEmpty { if (isLoading) "正在加载..." else "i站作者" },
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (profile.username.isNotEmpty()) {
                            Text(
                                text = "@${profile.username}",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Button(
                        onClick = onFollowClick,
                        enabled = !isFollowLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (profile.isFollowing) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        if (isFollowLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = if (profile.isFollowing) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (profile.isFollowing) "已关注" else "关注", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

// 作者简介部分
        if (profile.about.isNotEmpty()) {
            AuthorBioCard(aboutText = profile.about)
        } else if (isLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text("正在加载作者简介...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 最新视频区域
        Text(
            text = "最新视频",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        if (isLoading && latestVideos.isEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text("正在加载作者视频...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (latestVideos.isEmpty()) {
            Text("暂无视频", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val chunked = latestVideos.take(8).chunked(2)
            for (rowItems in chunked) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    for (col in 0 until 2) {
                        val item = rowItems.getOrNull(col)
                        if (item != null) {
                            MediaItemCard(
                                mediaItem = item,
                                isVideo = true,
                                onVideoClick = onVideoClick,
                                onAuthorClick = onAuthorClick,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // 留言评论区
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "评论区",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        if (isLoading && posts.isEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text("正在加载评论区...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (posts.isEmpty()) {
            Text("暂无留言评论", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            posts.forEach { post ->
                AuthorPostCard(post = post)
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

// 简介折叠/展开组件
@Composable
private fun AuthorBioCard(aboutText: String) {
    var isExpanded by remember { mutableStateOf(false) }
    var hasOverflow by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = aboutText,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { result ->
                    if (!isExpanded && result.hasVisualOverflow) {
                        hasOverflow = true
                    }
                }
            )

            if (hasOverflow && !isExpanded) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "展开更多",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable { isExpanded = true }
                        .padding(4.dp)
                )
            } else if (isExpanded) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "收起",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable { isExpanded = false }
                        .padding(4.dp)
                )
            }
        }
    }
}

// 动态/评论卡片
@Composable
private fun AuthorPostCard(post: IwaraMedia) {
    val context = LocalContext.current
    val fixedAvatarUrl = remember(post.authorAvatarUrl) { fixImageUrl(post.authorAvatarUrl) }
    val cookie = remember { CookieManager.getInstance().getCookie("https://www.iwara.tv") ?: "" }

    val avatarRequest = remember(fixedAvatarUrl, cookie) {
        if (fixedAvatarUrl.isNotEmpty()) {
            coil.request.ImageRequest.Builder(context)
                .data(fixedAvatarUrl)
                .crossfade(true)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .addHeader("Referer", "https://www.iwara.tv/")
                .apply { if (cookie.isNotEmpty()) addHeader("Cookie", cookie) }
                .build()
        } else null
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (avatarRequest != null) {
                    AsyncImage(
                        model = avatarRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(22.dp).clip(CircleShape)
                    )
                }
                Text(
                    text = post.authorName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2196F3)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = post.timeAgoStr,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = post.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (post.viewsStr.isNotEmpty() && post.viewsStr != "回复") {
                    Text(
                        text = post.viewsStr,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                OutlinedButton(
                    onClick = { },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = "回复",
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("回复", fontSize = 11.sp)
                }
            }
        }
    }
}

// 视频/图片 Tab - 1.5 屏幕预加载区 + 快速滑动不请网只读本地缓存
@Composable
private fun AuthorMediaListTabContent(
    items: List<IwaraMedia>,
    isVideo: Boolean,
    isLoading: Boolean,
    hasMore: Boolean,
    searchKeyword: String,
    onLoadMore: () -> Unit,
    onVideoClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit
) {
    val gridState = rememberLazyGridState()
    val isFastScrolling = rememberIsFastScrolling(gridState)

    val filteredItems = remember(items, searchKeyword) {
        if (searchKeyword.isBlank()) items
        else items.filter { it.title.contains(searchKeyword, ignoreCase = true) }
    }

    // 计算当前【视口 ± 1.5 个屏幕】的预加载索引范围
    val firstVisible = gridState.firstVisibleItemIndex
    val visibleCount = gridState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(6)
    val bufferCount = (visibleCount * 1.5f).toInt()

    val minPreloadIndex = (firstVisible - bufferCount).coerceAtLeast(0)
    val maxPreloadIndex = firstVisible + visibleCount + bufferCount

    // 触底下一页
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (lastIndex != null && lastIndex >= filteredItems.size - 4 && hasMore && !isLoading) {
                    onLoadMore()
                }
            }
    }

    if (isLoading && filteredItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("正在加载作品...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else if (filteredItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("暂无相关内容", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(filteredItems, key = { _, item -> item.id }) { index, item ->
                val inZone = index in minPreloadIndex..maxPreloadIndex

                MediaItemCard(
                    mediaItem = item,
                    isVideo = isVideo,
                    isFastScrolling = isFastScrolling, // 👈 滑动过快时不走网络，仅读 Disk/Memory 缓存
                    inPreloadZone = inZone,            // 👈 超出 1.5 屏幕缓冲区卸载
                    onVideoClick = onVideoClick,
                    onAuthorClick = onAuthorClick
                )
            }

            if (isLoading) {
                item(span = { GridItemSpan(2) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("正在加载下一页...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}