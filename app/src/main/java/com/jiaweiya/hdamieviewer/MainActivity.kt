// File: app/src/main/java/com/jiaweiya/hdamieviewer/MainActivity.kt
package com.jiaweiya.hdamieviewer

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiaweiya.hdamieviewer.ui.theme.HDAmieViewerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HDAmieViewerTheme {
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val coroutineScope = rememberCoroutineScope()
                val context = LocalContext.current

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        MainDrawerSheet(
                            onCloseDrawer = { coroutineScope.launch { drawerState.close() } },
                            onNavigateToSettings = {
                                Toast.makeText(context, "跳转至应用设置", Toast.LENGTH_SHORT).show()
                            },
                            onNavigateToAbout = {
                                Toast.makeText(context, "跳转至关于此应用", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                ) {
                    HomeScreen(
                        onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                        onNavigateToSettings = {
                            Toast.makeText(context, "打开应用设置", Toast.LENGTH_SHORT).show()
                        },
                        onNavigateToAbout = {
                            Toast.makeText(context, "打开关于此应用", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

// ==================== 主页面组件 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDrawer: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("i站客户端 (HDAmieViewer)", fontWeight = FontWeight.Bold) },
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
        ) {
            // 主页滑动内容区域
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. 搜索框
                SearchBar()

                // 2. 精选大卡片 (16:9)
                FeaturedCard()

                // 3. 视频区域 (2行2列)
                MediaGridSection(title = "推荐视频", isVideo = true)

                // 4. 图片区域 (2行2列)
                MediaGridSection(title = "精选图片", isVideo = false)

                Spacer(modifier = Modifier.height(32.dp))
            }

            // 5. 右侧物理滚动条
            VerticalScrollbar(scrollState = scrollState)
        }
    }
}

// 搜索框组件
@Composable
fun SearchBar(modifier: Modifier = Modifier) {
    var searchText by remember { mutableStateOf("") }
    OutlinedTextField(
        value = searchText,
        onValueChange = { searchText = it },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        placeholder = { Text("搜索你感兴趣的作品...") },
        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "搜索") },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

// 精选大卡片组件 (16:9)
@Composable
fun FeaturedCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 随便填充一张渐变占位图
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
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(56.dp)
                )
            }

            // 下1/3区域渐变加深并展示标题
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.35f)
                    .align(Alignment.BottomStart)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(
                    text = "【精选置顶】这是一篇测试用的大卡片标题内容展示",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// 媒体分栏区域组件 (包含 2行2列 的视频/图片卡片)
@Composable
fun MediaGridSection(title: String, isVideo: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // 2行布局
        for (row in 0 until 2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 2列布局
                for (col in 0 until 2) {
                    val itemIndex = row * 2 + col + 1
                    MediaItemCard(
                        title = "测试样例 - 这是一个很长很长用来展示两行折行以及省略号效果的标题标题 #$itemIndex",
                        isVideo = isVideo,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// 单个媒体项卡片 (16:9比例封面 + 最多2行字标题)
@Composable
fun MediaItemCard(title: String, isVideo: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            shape = RoundedCornerShape(12.dp)
        ) {
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
                    ),
                contentAlignment = Alignment.Center
            ) {
                // 随便填充一个矢量图标代表内容类型
                Icon(
                    imageVector = if (isVideo) Icons.Default.PlayCircle else Icons.Default.Info,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

// 自定义滚动条组件 (计算滚动进度并在右侧渲染指示条)
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

// ==================== 侧边栏组件 ====================

@Composable
fun MainDrawerSheet(
    onCloseDrawer: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        windowInsets = WindowInsets(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 顶部卡片 (完美复刻原项目中的卡片设计样式，使用 Compose 绘制头像避免依赖外部资源文件)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCloseDrawer(); onNavigateToAbout() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 自定义头像容器
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "HD",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "HDAmieViewer",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "关于此应用",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 第一行：左右排列的两个按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                DrawerMenuItem(
                    icon = Icons.Default.Settings,
                    text = "应用设置",
                    modifier = Modifier.weight(1f)
                ) {
                    onCloseDrawer()
                    onNavigateToSettings()
                }
                DrawerMenuItem(
                    icon = Icons.Default.Info,
                    text = "关于此应用",
                    modifier = Modifier.weight(1f)
                ) {
                    onCloseDrawer()
                    onNavigateToAbout()
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 底部版本展示
            Text(
                text = "v1.0.0 (开发预览版)",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 16.dp)
            )
        }
    }
}

// 侧边栏按钮封装组件
@Composable
fun DrawerMenuItem(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        icon = { Icon(imageVector = icon, contentDescription = text, modifier = Modifier.size(20.dp)) },
        label = {
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        selected = false,
        onClick = onClick,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}