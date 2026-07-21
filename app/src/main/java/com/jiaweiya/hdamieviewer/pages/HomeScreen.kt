package com.jiaweiya.hdamieviewer.pages

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    // 1. 获取当前页面的焦点管理器
    val focusManager = LocalFocusManager.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { SearchBar() },
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
                // 2. 在最外层 Box 绑定点击手势，点击任意空白区域清除输入框焦点
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FeaturedCard()
                MediaGridSection(title = "视频推荐", isVideo = true)
                MediaGridSection(title = "精选图片", isVideo = false)
                Spacer(modifier = Modifier.height(32.dp))
            }

            VerticalScrollbar(scrollState = scrollState)
        }
    }
}

@Composable
fun SearchBar(modifier: Modifier = Modifier) {
    var searchText by remember { mutableStateOf("") }
    OutlinedTextField(
        value = searchText,
        onValueChange = { searchText = it },
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 8.dp),
        shape = RoundedCornerShape(28.dp),
        placeholder = { Text("搜索内容...") },
        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "搜索") },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

@Composable
fun FeaturedCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                    text = "精选推荐：测试标题内容展示",
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

        for (row in 0 until 2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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