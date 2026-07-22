package com.jiaweiya.hdamieviewer.pages

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.net.URLEncoder

// 选项配置实体
data class SearchFilterOption(val label: String, val apiKey: String)

// 放在 SearchResultsScreen.kt 文件的最外层（不要写在 @Composable 函数里面）
object SearchResultsCache {
    var cachedKey: String = ""
    var cachedResults: List<IwaraMedia>? = null
    var cachedLog: String = ""
}

val searchTypeOptions = listOf(
    SearchFilterOption("视频", "videos"),
    SearchFilterOption("图片", "images"),
    SearchFilterOption("发布", "posts"),
    SearchFilterOption("用户", "users"),
    SearchFilterOption("播放列表", "playlists"),
    SearchFilterOption("论坛", "forum"),
    SearchFilterOption("论坛主题", "threads")
)

val searchSortOptions = listOf(
    SearchFilterOption("按相关性排序", "relevance"),
    SearchFilterOption("按最新排序", "date"),
    SearchFilterOption("按观看次数排序", "views"),
    SearchFilterOption("按最喜欢排序", "likes")
)

// 100% 网页 HTML DOM 节点提取逻辑（不依赖任何 API）
fun parseHtmlDomViaWebView(
    webView: WebView,
    query: String,
    type: String,
    sort: String,
    page: Int = 0,
    onResult: (List<IwaraMedia>, String) -> Unit
) {
    val log = StringBuilder()
    val list = mutableListOf<IwaraMedia>()

    try {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val sortParam = if (sort == "relevance" || sort.isEmpty()) "" else "&sort=$sort"
        val webType = if (type == "video") "videos" else if (type == "image") "images" else type

        // 拼接真正的网页搜索地址
        val webUrl = "https://www.iwara.tv/search?type=$webType&page=$page&query=$encodedQuery$sortParam"

        log.append("=== 网页 HTML DOM 解析模式 ===\n\n")
        log.append("1. 加载目标网页: $webUrl\n")
        log.append("2. 检索词: \"$query\" | 类型: $webType | 排序: $sort\n\n")

        // 1. 命令 WebView 加载真正的搜索网页
        webView.loadUrl(webUrl)

        // 2. 给单页应用 (SPA) 渲染 DOM 卡片留出 2 秒加载时间，然后注入 JS 提取卡片
        webView.postDelayed({
            val jsCode = """
                (function() {
                    try {
                        let items = [];
                        let seenIds = new Set();
                        
                        // 获取页面上所有的视频/图片卡片链接
                        let links = Array.from(document.querySelectorAll('a[href*="/video/"], a[href*="/image/"]'));

                        links.forEach(a => {
                            let href = a.getAttribute('href') || '';
                            let parts = href.split('/');
                            if (parts.length >= 3) {
                                let id = parts[2];
                                if (id && id.length >= 5 && !seenIds.has(id)) {
                                    seenIds.add(id);

                                    // 向上寻找卡片容器
                                    let card = a.closest('.videoTeaser') || a.closest('.imageTeaser') || a.closest('[class*="Teaser"]') || a.closest('.card') || a.parentElement;

                                    // 提取标题
                                    let titleEl = card ? (card.querySelector('[class*="title"]') || card.querySelector('.title')) : null;
                                    let title = titleEl ? titleEl.innerText.trim() : (a.innerText.trim() || '无标题');

                                    // 提取封面缩略图
                                    let imgEl = card ? card.querySelector('img') : null;
                                    let thumb = imgEl ? (imgEl.currentSrc || imgEl.src || imgEl.getAttribute('data-src') || '') : '';

                                    // 提取作者
                                    let authorEl = card ? card.querySelector('a[href*="/profile/"]') : null;
                                    let author = authorEl ? authorEl.innerText.trim() : 'i站作者';

                                    if (title && id) {
                                        items.push({
                                            id: id,
                                            title: title,
                                            thumbnailUrl: thumb,
                                            authorName: author,
                                            views: 0,
                                            likes: 0
                                        });
                                    }
                                }
                            }
                        });

                        return JSON.stringify({
                            success: true,
                            count: items.length,
                            items: items
                        });
                    } catch(e) {
                        return JSON.stringify({ success: false, error: e.toString() });
                    }
                })()
            """.trimIndent()

            webView.evaluateJavascript(jsCode) { rawJsResult ->
                if (rawJsResult != null && rawJsResult != "null") {
                    try {
                        // 【核心修复】：解包 evaluateJavascript 产生的 JSON 转义引号，解决 IllegalStateException
                        val unescapedJson = Gson().fromJson(rawJsResult, String::class.java) ?: rawJsResult

                        log.append("3. 网页 DOM 抓取返回:\n$unescapedJson\n\n")

                        if (unescapedJson.contains("\"success\":true")) {
                            val jsonObj = JsonParser.parseString(unescapedJson).asJsonObject
                            val itemsArray = jsonObj.getAsJsonArray("items")

                            itemsArray.forEach { element ->
                                val obj = element.asJsonObject
                                list.add(
                                    IwaraMedia(
                                        id = obj.get("id").asString,
                                        title = obj.get("title").asString,
                                        thumbnailUrl = obj.get("thumbnailUrl").asString,
                                        authorName = obj.get("authorName").asString,
                                        views = obj.get("views").asInt,
                                        likes = obj.get("likes").asInt
                                    )
                                )
                            }
                            log.append("🎉 HTML DOM 解析成功：共提取到 ${list.size} 条卡片！")
                        } else {
                            log.append("❌ JS DOM 提取报错: $unescapedJson")
                        }
                    } catch (e: Exception) {
                        log.append("❌ 字符串解析异常: ${e.localizedMessage ?: e.message}")
                    }
                } else {
                    log.append("❌ WebView 评估 JS 返回为空，请检查网络/代理环境。")
                }
                onResult(list, log.toString())
            }
        }, 2500) // 延迟 2.5 秒确保 SPA 动态渲染完成
    } catch (e: Exception) {
        log.append("❌ 逻辑异常: ${e.localizedMessage ?: e.message}")
        onResult(list, log.toString())
    }
}

// 通用下拉选择框组件
@Composable
fun SearchFilterDropdown(
    selectedOption: SearchFilterOption,
    options: List<SearchFilterOption>,
    onOptionSelected: (SearchFilterOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedCard(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedOption.label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            fontSize = 12.sp,
                            fontWeight = if (option == selectedOption) FontWeight.Bold else FontWeight.Normal,
                            color = if (option == selectedOption) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

// 搜索结果呈现页面
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsScreen(
    initialQuery: String,
    initialType: String,
    initialSort: String,
    onBackClick: () -> Unit,
    onVideoClick: (String) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var searchQuery by remember { mutableStateOf(initialQuery) }
    var selectedType by remember { mutableStateOf(searchTypeOptions.find { it.apiKey == initialType } ?: searchTypeOptions.first()) }
    var selectedSort by remember { mutableStateOf(searchSortOptions.find { it.apiKey == initialSort } ?: searchSortOptions.first()) }

    val currentKey = "$searchQuery-${selectedType.apiKey}-${selectedSort.apiKey}"

    // 优先读取缓存数据
    var resultsList by remember {
        mutableStateOf<List<IwaraMedia>>(
            if (SearchResultsCache.cachedKey == currentKey) SearchResultsCache.cachedResults ?: emptyList()
            else emptyList()
        )
    }
    var debugLog by remember {
        mutableStateOf(
            if (SearchResultsCache.cachedKey == currentKey) SearchResultsCache.cachedLog
            else ""
        )
    }
    var isLoading by remember {
        mutableStateOf(
            SearchResultsCache.cachedKey != currentKey || SearchResultsCache.cachedResults == null
        )
    }

    // 下拉刷新状态
    var isRefreshing by remember { mutableStateOf(false) }

    var showDebugDialog by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isWebViewReady by remember { mutableStateOf(false) }

    fun doSearch(isRefresh: Boolean = false) {
        if (searchQuery.isBlank()) return
        focusManager.clearFocus()
        saveSearchHistory(context, searchQuery)

        if (isRefresh) {
            isRefreshing = true
        } else {
            isLoading = true
        }

        val wv = webViewRef
        if (wv != null && isWebViewReady) {
            parseHtmlDomViaWebView(wv, searchQuery, selectedType.apiKey, selectedSort.apiKey) { list, log ->
                resultsList = list
                debugLog = log

                // 写入缓存
                SearchResultsCache.cachedKey = currentKey
                SearchResultsCache.cachedResults = list
                SearchResultsCache.cachedLog = log

                isLoading = false
                isRefreshing = false
            }
        }
    }

    // 只有在无缓存时才触发网络搜索
    LaunchedEffect(isWebViewReady) {
        if (isWebViewReady) {
            if (SearchResultsCache.cachedKey != currentKey || SearchResultsCache.cachedResults == null) {
                doSearch()
            } else {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val interactionSource = remember { MutableInteractionSource() }

                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            interactionSource = interactionSource,
                            singleLine = true,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp
                            )
                        ) { innerTextField ->
                            OutlinedTextFieldDefaults.DecorationBox(
                                value = searchQuery,
                                innerTextField = innerTextField,
                                enabled = true,
                                singleLine = true,
                                visualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
                                interactionSource = interactionSource,
                                placeholder = { Text("搜索内容...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { searchQuery = "" },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "清空",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
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
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            )
                        }

                        IconButton(
                            onClick = { doSearch() },
                            modifier = Modifier
                                .size(44.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // 顶部的两个选择器（选择类型与排序方式） + 右侧接口调试按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SearchFilterDropdown(
                        selectedOption = selectedType,
                        options = searchTypeOptions,
                        onOptionSelected = {
                            selectedType = it
                            doSearch()
                        },
                        modifier = Modifier.weight(1f)
                    )

                    SearchFilterDropdown(
                        selectedOption = selectedSort,
                        options = searchSortOptions,
                        onOptionSelected = {
                            selectedSort = it
                            doSearch()
                        },
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = { showDebugDialog = true },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "搜索 DOM 调试",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // PullToRefreshBox 下拉刷新包装
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { doSearch(isRefresh = true) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (resultsList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("暂无相关搜索结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(resultsList) { media ->
                                MediaItemCard(
                                    mediaItem = media,
                                    isVideo = (selectedType.apiKey == "videos"),
                                    onVideoClick = onVideoClick
                                )
                            }
                        }
                    }
                }
            }

            // 后台 WebView 挂载：专门用于渲染 i 站搜索网页并解析 HTML DOM 节点
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(0) // 👈 将 WebView 背景色设为完全透明
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                webViewRef = view
                                isWebViewReady = true
                            }
                        }
                        loadUrl("https://www.iwara.tv")
                    }
                },
                modifier = Modifier
                    .size(1.dp)
                    .graphicsLayer { alpha = 0f } // 👈 容器 100% 透明，小白点彻底消失
            )
        }
    }

    // 接口调试控制台弹窗
    if (showDebugDialog) {
        AlertDialog(
            onDismissRequest = { showDebugDialog = false },
            title = { Text("HTML DOM 解析调试控制台", fontWeight = FontWeight.Bold) },
            text = {
                Box(
                    modifier = Modifier
                        .height(300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SelectionContainer {
                        Text(
                            text = if (debugLog.isEmpty()) "正在加载网页并解析 DOM 节点..." else debugLog,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
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