package com.jiaweiya.hdamieviewer.pages

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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.jiaweiya.hdamieviewer.iwara.IwaraAccountManager
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Collections
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 客户端本地渲染模型
data class IwaraMedia(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val authorName: String,
    val authorAvatarUrl: String = "",
    val viewsStr: String = "",
    val likesStr: String = "",
    val durationStr: String = "",
    val ratingStr: String = "",
    val timeAgoStr: String = "",
    val galleryCountStr: String = "",
    val views: Int = 0,
    val likes: Int = 0
)

fun formatCount(count: Int): String {
    return if (count >= 1000) {
        String.format("%.1fk", count / 1000.0)
    } else {
        count.toString()
    }
}

// 升级版：补全协议 + 直连 CDN 节点，解决 OkHttp 重定向丢失 Header 导致的灰色无图
fun fixImageUrl(rawUrl: String): String {
    if (rawUrl.isBlank()) return ""
    var trimmed = rawUrl.trim()

    // 1. 自动补全 https: 协议
    if (trimmed.startsWith("//")) {
        trimmed = "https:$trimmed"
    } else if (trimmed.startsWith("/")) {
        trimmed = "https://www.iwara.tv$trimmed"
    }

    // 2. 👈 核心修复：直接将重定向域名 i.iwara.tv 替换为直连真实 CDN 节点 files.iwara.tv
    // 避免跨域重定向时 OkHttp 自动抹除 Referer/Cookie 导致 403 灰色无图！
    if (trimmed.contains("i.iwara.tv")) {
        trimmed = trimmed.replace("i.iwara.tv", "files.iwara.tv")
    }

    return trimmed
}

// 实时图片与头像网络连通性深度诊断函数
suspend fun testImageNetworkAccess(urls: List<String>): String {
    return withContext(Dispatchers.IO) {
        val log = StringBuilder()
        log.append("\n=== 【图片/头像网络加载深度诊断】 ===\n")

        val cookieManager = android.webkit.CookieManager.getInstance()
        val cookie = cookieManager.getCookie("https://www.iwara.tv") ?: ""
        log.append("• 共享 Cookie 状态: ${if (cookie.isNotEmpty()) "已获取 (长度 ${cookie.length})" else "❌ 缺失 Cookie"}\n\n")

        urls.take(4).forEachIndexed { idx, rawUrl ->
            val fixedUrl = fixImageUrl(rawUrl)
            log.append("📍 [#${idx + 1}] 测试图片: $fixedUrl\n")
            if (fixedUrl.isEmpty()) {
                log.append("   ❌ 路径为空\n")
                return@forEachIndexed
            }

            try {
                val url = URL(fixedUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                conn.setRequestProperty("Referer", "https://www.iwara.tv/")
                if (cookie.isNotEmpty()) {
                    conn.setRequestProperty("Cookie", cookie)
                }

                val code = conn.responseCode
                val msg = conn.responseMessage
                val contentType = conn.contentType ?: "未知"
                val contentLength = conn.contentLengthLong

                log.append("   -> HTTP 响应状态码: $code ($msg)\n")
                log.append("   -> Content-Type: $contentType | 大小: $contentLength bytes\n")

                if (code in 200..299) {
                    log.append("   🎉 访问成功！网络与防盗链标头完全打通！\n")
                } else {
                    log.append("   ❌ 访问被拒绝！HTTP $code (可能触发了 Cloudflare 盾)\n")
                }
            } catch (e: Exception) {
                log.append("   ❌ 物理网络异常: ${e.localizedMessage ?: e.message}\n")
            }
            log.append("\n")
        }
        log.toString()
    }
}

// ==================== 全局内存缓存 ====================

object HomeMediaCache {
    var cachedVideos: List<IwaraMedia>? = null
    var cachedImages: List<IwaraMedia>? = null
    var cachedSubscriptions: List<IwaraMedia>? = null  // 订阅视频
    var cachedSubImages: List<IwaraMedia>? = null      // 订阅图片
    var cachedSubPosts: List<IwaraMedia>? = null       // 订阅帖子

    // 👈 新增：“视频”与“图片”分类页面的全局内存缓存
    var cachedVideoCategoryMap: Map<String, List<IwaraMedia>>? = null
    var cachedImageCategoryMap: Map<String, List<IwaraMedia>>? = null

    var cachedLog: String = ""
}

// 首页 DOM 抓取回调对象
object IwaraHomeActionHandler {
    var onHomeResult: ((
        videos: List<IwaraMedia>,
        images: List<IwaraMedia>,
        subVideos: List<IwaraMedia>,
        subImages: List<IwaraMedia>,
        subPosts: List<IwaraMedia>,
        log: String
    ) -> Unit)? = null
}

// 分类数据独立回调对象
object IwaraCategoryActionHandler {
    var onCategoryResult: ((Map<String, List<IwaraMedia>>) -> Unit)? = null
}

// 终极双引擎版：在 WebView 环境内直连官方 API，精准拉取作者/播放量/赞数/时长/R-18全量字段
fun parseHomePageDomViaWebView(
    webView: WebView,
    onResult: (videos: List<IwaraMedia>, images: List<IwaraMedia>, subVideos: List<IwaraMedia>, subImages: List<IwaraMedia>, subPosts: List<IwaraMedia>, log: String) -> Unit
) {
    val log = StringBuilder()
    log.append("=== 首页数据抓取引擎调试日志 ===\n")
    log.append("1. 目标地址: https://www.iwara.tv/\n")

    IwaraHomeActionHandler.onHomeResult = { videos, images, subVideos, subImages, subPosts, logText ->
        log.append(logText)
        onResult(videos, images, subVideos, subImages, subPosts, log.toString())
    }

    webView.loadUrl("https://www.iwara.tv/")

    webView.postDelayed({
        val jsCode = """
            (async function() {
            let log = '--- [1. WebView 内存 API 节点抓取] ---\n';
            try {
                let token = localStorage.getItem('token') || localStorage.getItem('access_token') || '';
                let headers = { 'Accept': 'application/json, text/plain, */*' };
                if (token) headers['Authorization'] = 'Bearer ' + token;

                log += '• 正在请求 API: 热门, 订阅(视频/图片/帖子)...\n';
                let [vRes, iRes, svRes, siRes, spRes] = await Promise.all([
                    fetch('https://api.iwara.tv/videos?sort=trending&limit=12', { headers }).catch(e => null),
                    fetch('https://api.iwara.tv/images?sort=trending&limit=12', { headers }).catch(e => null),
                    fetch('https://api.iwara.tv/videos?subscribed=true&limit=6', { headers }).catch(e => null),
                    fetch('https://api.iwara.tv/images?subscribed=true&limit=6', { headers }).catch(e => null),
                    fetch('https://api.iwara.tv/posts?subscribed=true&limit=6', { headers }).catch(e => null)
                ]);

                if (vRes && vRes.ok) {
                    let vData = await vRes.json();
                    let iData = (iRes && iRes.ok) ? await iRes.json() : { results: [] };
                    let svData = (svRes && svRes.ok) ? await svRes.json() : { results: [] };
                    let siData = (siRes && siRes.ok) ? await siRes.json() : { results: [] };
                    let spData = (spRes && spRes.ok) ? await spRes.json() : { results: [] };

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
                    function formatAvatar(u) {
                        if (!u || !u.avatar) return '';
                        let a = u.avatar;
                        let id = a.id || '';
                        let name = a.name || '';
                        if (id && name) return 'https://files.iwara.tv/image/avatar/' + id + '/' + name;
                        if (id) return 'https://files.iwara.tv/image/avatar/' + id + '/avatar.jpg';
                        return '';
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

                    function parseApiList(list, typeName) {
                        if (!list || !Array.isArray(list)) return [];
                        return list.map(item => {
                            // 👈 核心修复：使用 typeName.includes('图片') 判定，支持 '精选图片' 和 '订阅图片'
                            let isImage = (typeName && typeName.includes('图片')) || item.type === 'image';
                            let thumb = isImage ? formatImageThumb(item) : formatVideoThumb(item);
                            
                            let u = item.user || {};
                            return {
                                id: item.id,
                                title: item.title,
                                thumbnailUrl: thumb,
                                authorName: u.name || u.username || 'i站作者',
                                authorAvatarUrl: formatAvatar(u),
                                viewsStr: formatCount(item.numViews),
                                likesStr: formatCount(item.numLikes),
                                durationStr: formatDur(item.duration),
                                ratingStr: (item.rating === 'ecchi') ? 'R-18' : '',
                                timeAgoStr: item.createdAt ? (new Date(item.createdAt).toLocaleDateString()) : '',
                                galleryCountStr: formatGalleryCount(item)
                            };
                        });
                    }

                    function parsePostList(list) {
                        if (!list || !Array.isArray(list)) return [];
                        return list.map(item => {
                            let u = item.user || {};
                            return {
                                id: item.id,
                                title: item.title || item.body || '帖子内容',
                                thumbnailUrl: '',
                                authorName: u.name || u.username || 'i站作者',
                                authorAvatarUrl: formatAvatar(u),
                                viewsStr: item.numComments ? (item.numComments + ' 评论') : '',
                                likesStr: formatCount(item.numLikes),
                                durationStr: '',
                                ratingStr: '',
                                timeAgoStr: item.createdAt ? (new Date(item.createdAt).toLocaleDateString()) : '',
                                galleryCountStr: ''
                            };
                        });
                    }

                    let vItems = parseApiList(vData.results, '热门视频');
                    let iItems = parseApiList(iData.results, '精选图片');
                    let svItems = parseApiList(svData.results, '订阅视频');
                    let siItems = parseApiList(siData.results, '订阅图片');
                    let spItems = parsePostList(spData.results);

                    let result = {
                        success: true,
                        videos: vItems,
                        images: iItems,
                        subVideos: svItems,
                        subImages: siItems,
                        subPosts: spItems,
                        log: log
                    };
                    console.log("IWARA_HOME_DOM_RESULT:" + JSON.stringify(result));
                }
            } catch(e) {
                console.log("IWARA_HOME_DOM_RESULT:" + JSON.stringify({ success: false, error: e.toString(), log: log }));
            }
        })();
        """.trimIndent()

        webView.evaluateJavascript(jsCode, null)
    }, 1200)
}

// 按分类懒加载数据（视频/图片）：通过 WebView 拉取 i 站分类页面并解析
// 并发请求 5 大独立分类 URL 数据的函数
fun fetchCategoryDataViaWebView(
    webView: WebView,
    isVideo: Boolean,
    onResult: (Map<String, List<IwaraMedia>>) -> Unit
) {
    IwaraCategoryActionHandler.onCategoryResult = onResult
    val endpoint = if (isVideo) "videos" else "images"

    val jsCode = """
        (async function() {
            try {
                let token = localStorage.getItem('token') || localStorage.getItem('access_token') || '';
                let headers = { 'Accept': 'application/json, text/plain, */*' };
                if (token) headers['Authorization'] = 'Bearer ' + token;

                // 核心：精准请求 5 大 independent URL 排序接口
                let sorts = ['date', 'trending', 'popularity', 'views', 'likes'];
                let requests = sorts.map(s =>
                    fetch('https://api.iwara.tv/' + '$endpoint' + '?sort=' + s + '&page=0&limit=4', { headers })
                        .then(r => r.ok ? r.json() : { results: [] })
                        .catch(e => ({ results: [] }))
                );

                let results = await Promise.all(requests);
                let resultMap = {};

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

                // 头像正确固定路径为 /image/avatar/{id}/{name}，绝不带日期 path
                function formatAvatar(u) {
                    if (!u || !u.avatar) return '';
                    let a = u.avatar;
                    let id = a.id || '';
                    let name = a.name || '';
                    if (id && name) return 'https://files.iwara.tv/image/avatar/' + id + '/' + name;
                    if (id) return 'https://files.iwara.tv/image/avatar/' + id + '/avatar.jpg';
                    return '';
                }

                // 全兼容解析图片帖子的封面
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
                    
                    // 👈 核心修改：新增对 item.numImages 的解析（Iwara新版API实际使用的图片数量字段）
                    if (item.numImages !== undefined && item.numImages !== null) {
                        count = Number(item.numImages);
                    } else if (Array.isArray(item.files)) {
                        count = item.files.length;
                    } else if (item.numFiles !== undefined && item.numFiles !== null) {
                        count = Number(item.numFiles);
                    } else if (item.gallery && item.gallery.count !== undefined && item.gallery.count !== null) {
                        count = Number(item.gallery.count);
                    }
                    
                    // 普通单图不显示合集标志，合集至少包含 2 张图片。
                    return Number.isFinite(count) && count > 1 ? String(count) : '';
                }

                sorts.forEach((sortKey, idx) => {
                    let list = results[idx].results || [];
                    resultMap[sortKey] = list.map(item => {
                        let thumb = ('$endpoint' === 'images' || item.type === 'image') ? formatImageThumb(item) : formatVideoThumb(item);
                        let u = item.user || {};
                        let galleryCount = formatGalleryCount(item);

                        return {
                            id: item.id,
                            title: item.title,
                            thumbnailUrl: thumb,
                            authorName: u.name || u.username || 'i站作者',
                            authorAvatarUrl: formatAvatar(u),
                            viewsStr: formatCount(item.numViews),
                            likesStr: formatCount(item.numLikes),
                            durationStr: formatDur(item.duration),
                            ratingStr: item.rating === 'ecchi' ? 'R-18' : '',
                            timeAgoStr: item.createdAt ? new Date(item.createdAt).toLocaleDateString() : '',
                            galleryCountStr: galleryCount
                        };
                    });
                });

                console.log("IWARA_CATEGORY_RESULT:" + JSON.stringify({ success: true, data: resultMap }));
            } catch(e) {
                console.log("IWARA_CATEGORY_RESULT:" + JSON.stringify({ success: false, error: e.toString() }));
            }
        })();
    """.trimIndent()

    webView.evaluateJavascript(jsCode, null)
}

// ==================== UI 呈现页面 ====================

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onOpenDrawer: () -> Unit,
    onVideoClick: (String) -> Unit,
    onNavigateToSearchResults: (String, String, String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // 读取账号登录状态
    val iwaraAccount = remember { IwaraAccountManager.loadUser(context) }

    // API 数据流状态管理（优先从 HomeMediaCache 读取缓存）
    var popularVideos by remember { mutableStateOf<List<IwaraMedia>>(HomeMediaCache.cachedVideos ?: emptyList()) }
    var popularImages by remember { mutableStateOf<List<IwaraMedia>>(HomeMediaCache.cachedImages ?: emptyList()) }
    var subscriptionVideos by remember { mutableStateOf<List<IwaraMedia>>(HomeMediaCache.cachedSubscriptions ?: emptyList()) }
    var subscriptionImages by remember { mutableStateOf<List<IwaraMedia>>(HomeMediaCache.cachedSubImages ?: emptyList()) }
    var subscriptionPosts by remember { mutableStateOf<List<IwaraMedia>>(HomeMediaCache.cachedSubPosts ?: emptyList()) }

    var isLoading by remember { mutableStateOf(HomeMediaCache.cachedVideos == null) }
    var isRefreshing by remember { mutableStateOf(false) }

    // 搜索状态管理
    var isSearchActive by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }

    // 1x1 后台 WebView 引用
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isWebViewReady by remember { mutableStateOf(false) }

    var debugLog by remember { mutableStateOf(HomeMediaCache.cachedLog) }
    var showDebugDialog by remember { mutableStateOf(false) }

    // 按需懒加载状态（优先从 HomeMediaCache 全局缓存中读取，防止返回页面时被清空）
    var videoCategoryMap by remember {
        mutableStateOf<Map<String, List<IwaraMedia>>>(HomeMediaCache.cachedVideoCategoryMap ?: emptyMap())
    }
    var isVideoLoading by remember { mutableStateOf(false) }

    var imageCategoryMap by remember {
        mutableStateOf<Map<String, List<IwaraMedia>>>(HomeMediaCache.cachedImageCategoryMap ?: emptyMap())
    }
    var isImageLoading by remember { mutableStateOf(false) }

    // Tab 选项卡与 HorizontalPager 横滑状态管理
    val tabs = listOf("推荐", "视频", "图片", "订阅")
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { tabs.size })

    fun refreshHomeContent(isRefreshAction: Boolean = false) {
        val wv = webViewRef ?: return
        if (isRefreshAction) {
            isRefreshing = true
            // 👈 仅在手动下拉刷新时清空分类缓存
            HomeMediaCache.cachedVideoCategoryMap = null
            HomeMediaCache.cachedImageCategoryMap = null
            videoCategoryMap = emptyMap()
            imageCategoryMap = emptyMap()
        } else {
            isLoading = true
        }

        parseHomePageDomViaWebView(wv) { videos, images, subVideos, subImages, subPosts, logText ->
            popularVideos = videos
            popularImages = images
            subscriptionVideos = subVideos
            subscriptionImages = subImages
            subscriptionPosts = subPosts

            coroutineScope.launch {
                HomeMediaCache.cachedVideos = videos
                HomeMediaCache.cachedImages = images
                HomeMediaCache.cachedSubscriptions = subVideos
                HomeMediaCache.cachedSubImages = subImages
                HomeMediaCache.cachedSubPosts = subPosts
                HomeMediaCache.cachedLog = logText

                isLoading = false
                isRefreshing = false

                // 若是手动下拉刷新，且当前停留在“视频”或“图片”Tab，顺便同步刷一下当前 Tab 数据
                if (isRefreshAction) {
                    when (pagerState.currentPage) {
                        1 -> {
                            isVideoLoading = true
                            fetchCategoryDataViaWebView(wv, isVideo = true) { map ->
                                videoCategoryMap = map
                                HomeMediaCache.cachedVideoCategoryMap = map
                                isVideoLoading = false
                            }
                        }
                        2 -> {
                            isImageLoading = true
                            fetchCategoryDataViaWebView(wv, isVideo = false) { map ->
                                imageCategoryMap = map
                                HomeMediaCache.cachedImageCategoryMap = map
                                isImageLoading = false
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(isWebViewReady) {
        if (isWebViewReady) {
            if (HomeMediaCache.cachedVideos == null || HomeMediaCache.cachedImages == null) {
                refreshHomeContent()
            } else {
                isLoading = false
            }
        }
    }

    // 监听 Tab 页面滑动事件：只有缓存为空且滑动到对应 Tab 时才拉取
    LaunchedEffect(pagerState.currentPage, isWebViewReady) {
        if (!isWebViewReady) return@LaunchedEffect
        val wv = webViewRef ?: return@LaunchedEffect

        when (pagerState.currentPage) {
            1 -> { // 切换到 "视频" Tab
                if (videoCategoryMap.isEmpty()) {
                    isVideoLoading = true
                    fetchCategoryDataViaWebView(wv, isVideo = true) { map ->
                        videoCategoryMap = map
                        HomeMediaCache.cachedVideoCategoryMap = map
                        isVideoLoading = false
                    }
                }
            }
            2 -> { // 切换到 "图片" Tab
                if (imageCategoryMap.isEmpty()) {
                    isImageLoading = true
                    fetchCategoryDataViaWebView(wv, isVideo = false) { map ->
                        imageCategoryMap = map
                        HomeMediaCache.cachedImageCategoryMap = map
                        isImageLoading = false
                    }
                }
            }
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

                    // 搜索框下方的常驻 Tab 标签栏
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
                    // 使用 HorizontalPager 实现“推荐/视频/图片/订阅” 4大板块左右手势横滑
                    androidx.compose.foundation.pager.HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = { refreshHomeContent(isRefreshAction = true) },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            when (page) {
                                // 0. "推荐" 分页
                                0 -> RecommendTabPage(
                                    popularVideos = popularVideos,
                                    popularImages = popularImages,
                                    subscriptionVideos = subscriptionVideos,
                                    isLoggedIn = iwaraAccount.isLoggedIn,
                                    onVideoClick = onVideoClick,
                                    onShowDebugDialog = { showDebugDialog = true },
                                    scrollState = scrollState
                                )

                                // 1. "视频" 分页
                                1 -> CategoryMediaTabContent(
                                    isVideo = true,
                                    categoryMap = videoCategoryMap,
                                    isLoading = isVideoLoading,
                                    onVideoClick = onVideoClick,
                                    onMoreClick = { sortKey ->
                                        onNavigateToSearchResults("", "videos", sortKey)
                                    }
                                )

                                // 2. "图片" 分页
                                2 -> CategoryMediaTabContent(
                                    isVideo = false,
                                    categoryMap = imageCategoryMap,
                                    isLoading = isImageLoading,
                                    onVideoClick = onVideoClick,
                                    onMoreClick = { sortKey ->
                                        onNavigateToSearchResults("", "images", sortKey)
                                    }
                                )

                                // 3. "订阅" 分页
                                3 -> SubscriptionTabPage(
                                    isLoggedIn = iwaraAccount.isLoggedIn,
                                    subscriptionVideos = subscriptionVideos,
                                    subscriptionImages = subscriptionImages,
                                    subscriptionPosts = subscriptionPosts,
                                    isLoading = isLoading || isRefreshing, // 👈 传递全局加载/刷新状态
                                    onVideoClick = onVideoClick,
                                    onMoreClick = { subPath ->
                                        onNavigateToSearchResults("", subPath, "date")
                                    }
                                )
                            }
                        }
                    }
                }

                VerticalScrollbar(scrollState = scrollState)
            }
        }

        // 展开后的原地方形拉伸搜索遮罩面板
        if (isSearchActive) {
            var animateTrigger by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                animateTrigger = true
            }

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
                        .height(cardHeight)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { },
                    shape = RoundedCornerShape(cardCornerRadius),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAppDark) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                    ),
                    border = if (isAppDark) BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)) else null,
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    if (cardHeight > 100.dp) {
                        SearchExpandedContent(
                            searchText = searchText,
                            onSearchTextChange = { searchText = it },
                            onSearchTriggered = { query, type, sort ->
                                saveSearchHistory(context, query)
                                animateTrigger = false
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(220)
                                    isSearchActive = false
                                    val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                                    onNavigateToSearchResults(encodedQuery, type, sort)
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

        // 后台 1x1 完全透明 WebView 挂载点（用于抓取 i 站官网首页内容）
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(0)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                            val msg = consoleMessage?.message() ?: ""

                            // 1. 首页推荐数据监听
                            if (msg.startsWith("IWARA_HOME_DOM_RESULT:")) {
                                val jsonStr = msg.removePrefix("IWARA_HOME_DOM_RESULT:")
                                try {
                                    val jsonObj = com.google.gson.JsonParser.parseString(jsonStr).asJsonObject
                                    val isSuccess = jsonObj.get("success")?.asBoolean ?: false
                                    val jsLog = jsonObj.get("log")?.asString ?: ""

                                    val videoList = mutableListOf<IwaraMedia>()
                                    val imageList = mutableListOf<IwaraMedia>()
                                    val subVideoList = mutableListOf<IwaraMedia>()
                                    val subImageList = mutableListOf<IwaraMedia>()
                                    val subPostList = mutableListOf<IwaraMedia>()

                                    fun parseArray(key: String, targetList: MutableList<IwaraMedia>) {
                                        if (jsonObj.has(key) && jsonObj.get(key).isJsonArray) {
                                            val arr = jsonObj.getAsJsonArray(key)
                                            arr.forEach { element ->
                                                val obj = element.asJsonObject
                                                targetList.add(
                                                    IwaraMedia(
                                                        id = obj.get("id").asString,
                                                        title = obj.get("title").asString,
                                                        thumbnailUrl = obj.get("thumbnailUrl").asString,
                                                        authorName = obj.get("authorName").asString,
                                                        authorAvatarUrl = obj.get("authorAvatarUrl")?.asString ?: "",
                                                        viewsStr = obj.get("viewsStr")?.asString ?: "",
                                                        likesStr = obj.get("likesStr")?.asString ?: "",
                                                        durationStr = obj.get("durationStr")?.asString ?: "",
                                                        ratingStr = obj.get("ratingStr")?.asString ?: "",
                                                        timeAgoStr = obj.get("timeAgoStr")?.asString ?: "",
                                                        galleryCountStr = obj.get("galleryCountStr")?.asString ?: ""
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    if (isSuccess) {
                                        parseArray("videos", videoList)
                                        parseArray("images", imageList)
                                        parseArray("subVideos", subVideoList)
                                        parseArray("subImages", subImageList)
                                        parseArray("subPosts", subPostList)
                                    }

                                    val summaryLog = "$jsLog\n🎉 解析结果：视频 ${videoList.size}，图片 ${imageList.size}，订阅视频 ${subVideoList.size}，订阅图片 ${subImageList.size}，订阅帖子 ${subPostList.size}"

                                    IwaraHomeActionHandler.onHomeResult?.invoke(videoList, imageList, subVideoList, subImageList, subPostList, summaryLog)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                return true
                            }
                            // 2. 5 大分类数据监听 (语法对齐修复)
                            else if (msg.startsWith("IWARA_CATEGORY_RESULT:")) {
                                val jsonStr = msg.removePrefix("IWARA_CATEGORY_RESULT:")
                                try {
                                    val jsonObj = com.google.gson.JsonParser.parseString(jsonStr).asJsonObject
                                    if (jsonObj.get("success")?.asBoolean == true && jsonObj.has("data")) {
                                        val dataObj = jsonObj.getAsJsonObject("data")
                                        val resultMap = mutableMapOf<String, List<IwaraMedia>>()

                                        dataObj.keySet().forEach { sortKey ->
                                            val arr = dataObj.getAsJsonArray(sortKey)
                                            val mediaList = arr.map { element ->
                                                val obj = element.asJsonObject
                                                IwaraMedia(
                                                    id = obj.get("id").asString,
                                                    title = obj.get("title").asString,
                                                    thumbnailUrl = obj.get("thumbnailUrl").asString,
                                                    authorName = obj.get("authorName").asString,
                                                    authorAvatarUrl = obj.get("authorAvatarUrl")?.asString ?: "",
                                                    viewsStr = obj.get("viewsStr")?.asString ?: "",
                                                    likesStr = obj.get("likesStr")?.asString ?: "",
                                                    durationStr = obj.get("durationStr")?.asString ?: "",
                                                    ratingStr = obj.get("ratingStr")?.asString ?: "",
                                                    timeAgoStr = obj.get("timeAgoStr")?.asString ?: "",
                                                    galleryCountStr = obj.get("galleryCountStr")?.asString ?: ""
                                                )
                                            }
                                            resultMap[sortKey] = mediaList
                                        }

                                        // 触发 Kotlin 回调更新组件状态
                                        IwaraCategoryActionHandler.onCategoryResult?.invoke(resultMap)
                                    }
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
                        }
                    }
                    loadUrl("https://www.iwara.tv/")
                }
            },
            modifier = Modifier
                .size(1.dp)
                .graphicsLayer { alpha = 0.01f }
        )
    }

    // 首页 DOM 节点抓取调试控制台
    if (showDebugDialog) {
        AlertDialog(
            onDismissRequest = { showDebugDialog = false },
            title = { Text("首页 HTML DOM 调试控制台", fontWeight = FontWeight.Bold) },
            text = {
                Box(
                    modifier = Modifier
                        .height(300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            text = if (debugLog.isEmpty()) "正在加载网页数据中..." else debugLog,
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

// ==================== 4 大 Tab 子页面组件集合 ====================

// 1. "推荐" 选项卡页面内容
@Composable
private fun RecommendTabPage(
    popularVideos: List<IwaraMedia>,
    popularImages: List<IwaraMedia>,
    subscriptionVideos: List<IwaraMedia>,
    isLoggedIn: Boolean,
    onVideoClick: (String) -> Unit,
    onShowDebugDialog: () -> Unit,
    scrollState: ScrollState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 顶部精选大卡片 (占用第 1 个视频)
        val featuredVideo = popularVideos.firstOrNull()
        if (featuredVideo != null) {
            FeaturedCard(
                mediaItem = featuredVideo,
                onVideoClick = onVideoClick
            )
        } else {
            FeaturedCardPlaceholder()
        }

        // 2. 热门视频推荐 (跳过第 1 个视频，精准取接下来的 6 个，填满 2x3 网格)
        val gridVideos = popularVideos.drop(1).take(6)
        if (gridVideos.isNotEmpty()) {
            MediaGridSection(
                title = "热门视频推荐",
                mediaList = gridVideos,
                isVideo = true,
                onVideoClick = onVideoClick
            )
        }

        // 3. 精选图片推荐 (限制显示前 6 条)
        val gridImages = popularImages.take(6)
        if (gridImages.isNotEmpty()) {
            MediaGridSection(
                title = "精选图片推荐",
                mediaList = gridImages,
                isVideo = false,
                onVideoClick = {}
            )
        }

        // 4. 最新订阅 (已登录用户显示前 6 条)
        val gridSubs = subscriptionVideos.take(6)
        if (isLoggedIn && gridSubs.isNotEmpty()) {
            MediaGridSection(
                title = "最新订阅",
                mediaList = gridSubs,
                isVideo = true,
                onVideoClick = onVideoClick
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 放置在页面最下方的调试日志按钮
        OutlinedButton(
            onClick = onShowDebugDialog,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("查看并复制 DOM 解析调试日志", fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// 2. "视频" 与 "图片" 选项卡页面内容 (支持 5 大小分类 + 2x2=4 张卡片 + ">" 跳转)
@Composable
private fun CategoryMediaTabContent(
    isVideo: Boolean,
    categoryMap: Map<String, List<IwaraMedia>>,
    isLoading: Boolean,
    onVideoClick: (String) -> Unit,
    onMoreClick: (sortKey: String) -> Unit
) {
    val typeName = if (isVideo) "视频" else "图片"
    val categories = listOf(
        Triple("最新$typeName", "date", categoryMap["date"] ?: emptyList()),
        Triple("流行$typeName", "trending", categoryMap["trending"] ?: emptyList()),
        Triple("人气$typeName", "popularity", categoryMap["popularity"] ?: emptyList()),
        Triple("最多人观看", "views", categoryMap["views"] ?: emptyList()),
        Triple("最多赞", "likes", categoryMap["likes"] ?: emptyList())
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        categories.forEach { (title, sortKey, items) ->
            Column(modifier = Modifier.fillMaxWidth()) {
                // 分类标题行与右侧 > 按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMoreClick(sortKey) }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = { onMoreClick(sortKey) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "查看更多",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 👈 核心修改：数据为空时，若处于加载中则显示“正在加载中...”，加载完成才显示“暂无内容”
                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            LoadingInlineIndicator("正在加载中...")
                        } else {
                            Text("暂无内容", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    val chunked = items.take(4).chunked(2)
                    for (rowItems in chunked) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            for (col in 0 until 2) {
                                val item = rowItems.getOrNull(col)
                                if (item != null) {
                                    MediaItemCard(
                                        mediaItem = item,
                                        isVideo = isVideo,
                                        onVideoClick = onVideoClick,
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Box(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// 3. "订阅" 选项卡页面内容 (支持 订阅视频 / 订阅图片 / 订阅帖子 三大区域)
@Composable
private fun SubscriptionTabPage(
    isLoggedIn: Boolean,
    subscriptionVideos: List<IwaraMedia>,
    subscriptionImages: List<IwaraMedia>,
    subscriptionPosts: List<IwaraMedia>,
    isLoading: Boolean, // 👈 增加 isLoading 参数
    onVideoClick: (String) -> Unit,
    onMoreClick: (subPath: String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!isLoggedIn) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "请先在侧边栏登录 Iwara 账号以查看订阅内容",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        } else {
            // 区域 1：订阅视频
            SubscriptionSectionHeader(
                title = "订阅视频",
                onMoreClick = { onMoreClick("subscriptions/videos") }
            )
            if (subscriptionVideos.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (isLoading) {
                        LoadingInlineIndicator("正在加载中...")
                    } else {
                        Text("暂无订阅视频", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                val chunked = subscriptionVideos.take(6).chunked(2)
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
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // 区域 2：订阅图片
            SubscriptionSectionHeader(
                title = "订阅图片",
                onMoreClick = { onMoreClick("subscriptions/images") }
            )
            if (subscriptionImages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (isLoading) {
                        LoadingInlineIndicator("正在加载中...")
                    } else {
                        Text("暂无订阅图片", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                val chunked = subscriptionImages.take(6).chunked(2)
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
                                    isVideo = false,
                                    onVideoClick = onVideoClick,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // 区域 3：订阅帖子
            SubscriptionSectionHeader(
                title = "订阅帖子",
                onMoreClick = { onMoreClick("subscriptions/posts") }
            )
            if (subscriptionPosts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (isLoading) {
                        LoadingInlineIndicator("正在加载中...")
                    } else {
                        Text("暂无订阅帖子", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    subscriptionPosts.take(6).forEach { post ->
                        PostItemCard(postItem = post)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SubscriptionSectionHeader(
    title: String,
    onMoreClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onMoreClick() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "查看更多",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// 订阅帖子卡片渲染组件
@Composable
private fun PostItemCard(
    postItem: IwaraMedia,
    modifier: Modifier = Modifier
) {
    val fixedAvatarUrl = remember(postItem.authorAvatarUrl) { fixImageUrl(postItem.authorAvatarUrl) }

    Card(
        modifier = modifier.fillMaxWidth(),
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
                if (fixedAvatarUrl.isNotEmpty()) {
                    AsyncImage(
                        model = fixedAvatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(22.dp).clip(CircleShape)
                    )
                }
                Text(
                    text = postItem.authorName.ifEmpty { "i站作者" },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2196F3)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = postItem.timeAgoStr,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = postItem.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (postItem.viewsStr.isNotEmpty() || postItem.likesStr.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (postItem.likesStr.isNotEmpty()) {
                        Text("♥ ${postItem.likesStr}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (postItem.viewsStr.isNotEmpty()) {
                        Text(postItem.viewsStr, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            onSearchTextChange("")
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
    currentList.remove(query)
    currentList.add(0, query)
    val limitedList = currentList.take(10)
    prefs.edit().putString("search_history", Gson().toJson(limitedList)).apply()
}

fun deleteSearchHistoryItem(context: Context, query: String) {
    val prefs = context.getSharedPreferences("HDAmieViewerDB", Context.MODE_PRIVATE)
    val currentList = getSearchHistory(context).toMutableList()
    currentList.remove(query)
    prefs.edit().putString("search_history", Gson().toJson(currentList)).apply()
}

@Composable
fun FeaturedCard(
    mediaItem: IwaraMedia,
    modifier: Modifier = Modifier,
    onVideoClick: (String) -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clickable { onVideoClick(mediaItem.id) },
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
                        text = "作者: ${mediaItem.authorName}",
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
    onVideoClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (mediaList.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        val chunked = mediaList.chunked(2)
        for (rowItems in chunked) {
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
                            onVideoClick = onVideoClick,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun MediaItemCard(
    mediaItem: IwaraMedia,
    isVideo: Boolean,
    onVideoClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val fixedThumbUrl = remember(mediaItem.thumbnailUrl) { fixImageUrl(mediaItem.thumbnailUrl) }
    val fixedAvatarUrl = remember(mediaItem.authorAvatarUrl) { fixImageUrl(mediaItem.authorAvatarUrl) }

    // 提取系统 WebView 共享的 Cookie
    val cookie = remember {
        android.webkit.CookieManager.getInstance().getCookie("https://www.iwara.tv") ?: ""
    }

    // 带防盗链 + Cookie 的封面 Request
    val thumbRequest = remember(fixedThumbUrl, cookie) {
        if (fixedThumbUrl.isNotEmpty()) {
            coil.request.ImageRequest.Builder(context)
                .data(fixedThumbUrl)
                .crossfade(true)
                .addHeader(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                )
                .addHeader("Referer", "https://www.iwara.tv/")
                .apply {
                    if (cookie.isNotEmpty()) addHeader("Cookie", cookie)
                }
                .build()
        } else null
    }

    // 带防盗链 + Cookie 的头像 Request
    val avatarRequest = remember(fixedAvatarUrl, cookie) {
        if (fixedAvatarUrl.isNotEmpty()) {
            coil.request.ImageRequest.Builder(context)
                .data(fixedAvatarUrl)
                .crossfade(true)
                .addHeader(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                )
                .addHeader("Referer", "https://www.iwara.tv/")
                .apply {
                    if (cookie.isNotEmpty()) addHeader("Cookie", cookie)
                }
                .build()
        } else null
    }

    Column(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clickable { onVideoClick(mediaItem.id) },
            shape = RoundedCornerShape(8.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (thumbRequest != null) {
                    AsyncImage(
                        model = thumbRequest,
                        contentDescription = mediaItem.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onError = { error ->
                            android.util.Log.e("CoilError", "封面加载失败: $fixedThumbUrl", error.result.throwable)
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray))
                }

                // 1. 左上角: 播放量 👁 386
                if (mediaItem.viewsStr.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(3.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp), // 👈 仅留 1.dp 微小内边距
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.RemoveRedEye, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = mediaItem.viewsStr,
                                color = Color.White,
                                fontSize = 10.sp, // 👈 保持原本字号
                                lineHeight = 10.sp, // 👈 核心：限制行高，消除字体上下自带的空白
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 2. 右上角: 点赞数 ♥ 6
                if (mediaItem.likesStr.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(3.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = mediaItem.likesStr,
                                color = Color.White,
                                fontSize = 10.sp,
                                lineHeight = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 3. 左下角: 分级 R-18
                if (mediaItem.ratingStr.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomStart).padding(3.dp),
                        color = Color(0xFFE53935),
                        shape = RoundedCornerShape(2.dp)
                    ) {
                        Text(
                            text = mediaItem.ratingStr,
                            color = Color.White,
                            fontSize = 9.sp, // 👈 保持原本字号
                            lineHeight = 9.sp, // 👈 核心：消除默认多余高度，背景框将紧贴文字
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp) // 👈 仅比文字略高一点
                        )
                    }
                }

                // 4. 右下角: 如果是图片且有合集张数，展示“5 🖼️”；如果是视频，展示“3:01”
                if (!isVideo && mediaItem.galleryCountStr.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = mediaItem.galleryCountStr,
                                color = Color.White,
                                fontSize = 10.sp,
                                lineHeight = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(Icons.Default.Collections, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                        }
                    }
                } else if (mediaItem.durationStr.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = mediaItem.durationStr,
                                color = Color.White,
                                fontSize = 10.sp,
                                lineHeight = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 标题
        Text(
            text = mediaItem.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        // 作者头像、名字与时间
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (avatarRequest != null) {
                AsyncImage(
                    model = avatarRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(26.dp).clip(CircleShape),
                    onError = { error ->
                        android.util.Log.e("CoilError", "头像加载失败: $fixedAvatarUrl", error.result.throwable)
                    }
                )
            } else {
                // 默认头像兜底：当用户未设置头像时，显示首字母圆形图标
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mediaItem.authorName.take(1).uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Column {
                Text(
                    text = mediaItem.authorName.ifEmpty { "i站作者" },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2196F3),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (mediaItem.timeAgoStr.isNotEmpty()) {
                    Text(
                        text = mediaItem.timeAgoStr,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchExpandedContent(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onSearchTriggered: (query: String, type: String, sort: String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var historyList by remember { mutableStateOf(getSearchHistory(context)) }
    var showDeleteDialogFor by remember { mutableStateOf<String?>(null) }

    var selectedType by remember { mutableStateOf(searchTypeOptions.first()) }
    var selectedSort by remember { mutableStateOf(searchSortOptions.first()) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val interactionSource = remember { MutableInteractionSource() }

            BasicTextField(
                value = searchText,
                onValueChange = onSearchTextChange,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .focusRequester(focusRequester),
                interactionSource = interactionSource,
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (searchText.isNotBlank()) onSearchTriggered(searchText, selectedType.apiKey, selectedSort.apiKey)
                }),
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
                    label = { Text("搜索内容...", fontSize = 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    trailingIcon = {
                        if (searchText.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchTextChange("") },
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

            IconButton(
                onClick = {
                    if (searchText.isNotBlank()) {
                        onSearchTriggered(searchText, selectedType.apiKey, selectedSort.apiKey)
                    }
                },
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

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SearchFilterDropdown(
                selectedOption = selectedType,
                options = searchTypeOptions,
                onOptionSelected = { selectedType = it },
                modifier = Modifier.weight(1f)
            )

            SearchFilterDropdown(
                selectedOption = selectedSort,
                options = searchSortOptions,
                onOptionSelected = { selectedSort = it },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (historyList.isNotEmpty()) {
            Text(
                text = "搜索历史",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(historyList) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSearchTriggered(item, selectedType.apiKey, selectedSort.apiKey) }
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
                        historyList = getSearchHistory(context)
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

@Composable
private fun LoadingInlineIndicator(
    text: String = "正在加载中..."
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 12.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}