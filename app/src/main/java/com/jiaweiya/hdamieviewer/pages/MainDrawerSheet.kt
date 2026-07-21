package com.jiaweiya.hdamieviewer.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiaweiya.hdamieviewer.R

@Composable
fun MainDrawerSheet(
    onCloseDrawer: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onCheckUpdate: () -> Unit
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
            // Profile 顶部卡片 (完全复刻 FlowCourse 项目细节)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
                                painter = painterResource(id = R.drawable.jiaweiya_icon), // 加载相同名称的本地资源头像
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
                                fontSize = 22.sp,
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

            Spacer(modifier = Modifier.height(8.dp))

            // 第一排按钮 (并列显示两个主要的跳转链接)
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