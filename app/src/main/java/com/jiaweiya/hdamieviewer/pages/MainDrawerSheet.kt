package com.jiaweiya.hdamieviewer.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jiaweiya.hdamieviewer.R
import com.jiaweiya.hdamieviewer.iwara.IwaraAccount

@Composable
fun MainDrawerSheet(
    iwaraAccount: IwaraAccount,
    onCloseDrawer: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenIwaraLogin: () -> Unit,
    onLogoutIwara: () -> Unit,
    onNavigateToDownloadManager: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(320.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Profile 顶部卡片 1：应用开发者信息
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCloseDrawer(); onNavigateToAbout() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painter = painterResource(id = R.drawable.jiaweiya_icon),
                                contentDescription = "Jiaweiya",
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Jiaweiya",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "H-damieViewer",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "关于此应用",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }

                    // 检查更新按钮
                    Text(
                        text = "检查更新",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onCheckUpdate() }
                            .padding(4.dp)
                    )
                }
            }

            // Profile 顶部卡片 2：Iwara 账号卡片 (与上方卡片 1 100% 同款样式)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onCloseDrawer()
                                onOpenIwaraLogin()
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (iwaraAccount.isLoggedIn && iwaraAccount.avatarUrl.isNotEmpty()) {
                                val context = LocalContext.current
                                val avatarRequest = remember(iwaraAccount.avatarUrl) {
                                    ImageRequest.Builder(context)
                                        .data(iwaraAccount.avatarUrl)
                                        .crossfade(true)
                                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                                        .addHeader("Referer", "https://www.iwara.tv/")
                                        .build()
                                }
                                AsyncImage(
                                    model = avatarRequest,
                                    contentDescription = "Iwara Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            } else {
                                // 默认未登录或无头像图标
                                Image(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "默认头像",
                                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = if (iwaraAccount.isLoggedIn) iwaraAccount.username else "未登录",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                "Iwara",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (iwaraAccount.isLoggedIn) "@${iwaraAccount.handle.ifEmpty { iwaraAccount.username }}" else "点击登录账号",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }

                    // 👈【新增】：已登录时，在卡片右上角显示红色的"退出登录"
                    if (iwaraAccount.isLoggedIn) {
                        Text(
                            text = "退出登录",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    onLogoutIwara()
                                }
                                .padding(4.dp)
                        )
                    }

                    // 重新登录 / 登录按钮
                    Text(
                        text = if (iwaraAccount.isLoggedIn) "重新登录" else "登录账号",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                onCloseDrawer()
                                onOpenIwaraLogin()
                            }
                            .padding(4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 第一排：设置 + 关于
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

            Spacer(modifier = Modifier.height(4.dp))

            // 第二排：独立一行展示“下载管理” 👈
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                DrawerMenuItem(
                    icon = Icons.Default.DownloadDone,
                    text = "下载管理",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    onCloseDrawer()
                    onNavigateToDownloadManager()
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "v1.0.0 (H-damieViewer)",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 16.dp)
            )
        }
    }
}

@Composable
fun DrawerMenuItem(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = text, modifier = Modifier.size(20.dp)) },
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