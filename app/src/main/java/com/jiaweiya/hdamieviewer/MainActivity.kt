package com.jiaweiya.hdamieviewer

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jiaweiya.hdamieviewer.pages.AboutPage
import com.jiaweiya.hdamieviewer.pages.BackupCrypto
import com.jiaweiya.hdamieviewer.pages.BackupData
import com.jiaweiya.hdamieviewer.pages.FullscreenMarginSettingsPage
import com.jiaweiya.hdamieviewer.pages.FullscreenSwipeSettingsScreen
import com.jiaweiya.hdamieviewer.pages.NonFullscreenSwipeSettingsScreen
import com.jiaweiya.hdamieviewer.pages.SearchResultsScreen
import com.jiaweiya.hdamieviewer.pages.ExportBackupScreen
import com.jiaweiya.hdamieviewer.pages.HomeScreen
import com.jiaweiya.hdamieviewer.pages.ImportBackupScreen
import com.jiaweiya.hdamieviewer.iwara.IwaraAccountManager
import com.jiaweiya.hdamieviewer.iwara.IwaraLoginDialog
import com.jiaweiya.hdamieviewer.pages.MainDrawerSheet
import com.jiaweiya.hdamieviewer.pages.SettingsPage
import com.jiaweiya.hdamieviewer.ui.theme.HDAmieViewerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.ui.unit.sp
import com.jiaweiya.hdamieviewer.pages.resolveThemeColor
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.jiaweiya.hdamieviewer.pages.DownloadManagerScreen
import com.jiaweiya.hdamieviewer.pages.VideoPlayerScreen

data class GithubRelease(
    val tag_name: String,
    val body: String,
    val html_url: String
)

suspend fun checkAppUpdate(
    currentVersion: String,
    channel: Int,
    onResult: (release: GithubRelease?, isLatest: Boolean, errorMsg: String?) -> Unit // 增加 errorMsg 参数
) {
    withContext(Dispatchers.IO) {
        try {
            val urlString = if (channel == 1) {
                "https://api.github.com/repos/jiaweiyaya/H-damieViewer/releases"
            } else {
                "https://api.github.com/repos/jiaweiyaya/H-damieViewer/releases/latest"
            }

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val json = connection.inputStream.bufferedReader().readText()
                val gson = Gson()

                val release = if (channel == 1) {
                    val type = object : TypeToken<List<GithubRelease>>() {}.type
                    val releases = gson.fromJson<List<GithubRelease>>(json, type)
                    releases.firstOrNull()
                } else {
                    gson.fromJson(json, GithubRelease::class.java)
                }

                if (release != null) {
                    val remoteVersion = release.tag_name.replace(Regex("[^0-9.]"), "")
                    val localVersion = currentVersion.replace(Regex("[^0-9.]"), "")

                    fun toInts(v: String) = v.split(".").map { it.toIntOrNull() ?: 0 }
                    val remoteParts = toInts(remoteVersion)
                    val localParts = toInts(localVersion)

                    var isNewer = false
                    for (i in 0 until maxOf(remoteParts.size, localParts.size)) {
                        val r = remoteParts.getOrNull(i) ?: 0
                        val l = localParts.getOrNull(i) ?: 0
                        if (r > l) { isNewer = true; break }
                        if (r < l) { break }
                    }

                    withContext(Dispatchers.Main) {
                        // 成功时 errorMsg 传 null
                        if (isNewer) onResult(release, false, null) else onResult(null, true, null)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onResult(null, false, "GitHub 响应数据解析为空")
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    // 捕捉 HTTP 非 200 错误（例如 403 频率受限，404 库不存在等）
                    onResult(null, false, "HTTP 错误代码: $responseCode (${connection.responseMessage})")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                // 捕捉物理连接异常（例如超时、断网、域名无解析等）
                onResult(null, false, "网络连接异常: ${e.localizedMessage ?: e.message ?: "未知错误"}")
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedPrefs = getSharedPreferences("HDAmieViewerDB", Context.MODE_PRIVATE)

        setContent {
            // 本地偏好设置状态加载
            var themeMode by remember { mutableStateOf(sharedPrefs.getInt("theme_mode", 0)) }
            var updateChannel by remember { mutableStateOf(sharedPrefs.getInt("update_channel", 0)) }
            val isSystemDark = isSystemInDarkTheme()
            val isAppDark = themeMode == 2 || (themeMode == 0 && isSystemDark)
            val defaultColor = if (isAppDark) 0xFFD0BCFFL else 0xFF9E77EDL
            var themeColor by remember { mutableStateOf(sharedPrefs.getLong("theme_color", defaultColor)) }
            var autoCheckUpdate by remember { mutableStateOf(sharedPrefs.getBoolean("auto_check_update", true)) }
            // 默认：0-ExoPlayer, 1-MpvPlayer, 2-MediaPlayer
            var playerType by remember { mutableStateOf(sharedPrefs.getInt("player_type", 0)) }

            // 监听并实时存储状态
            LaunchedEffect(themeMode, themeColor, updateChannel, autoCheckUpdate, playerType) {
                withContext(Dispatchers.IO) {
                    sharedPrefs.edit()
                        .putInt("theme_mode", themeMode)
                        .putLong("theme_color", themeColor)
                        .putInt("update_channel", updateChannel)
                        .putBoolean("auto_check_update", autoCheckUpdate)
                        .putInt("player_type", playerType)
                        .apply()
                }
            }

            val resolvedThemeColor = resolveThemeColor(themeColor, isAppDark)

            // 【核心修复】：显式将 resolvedThemeColor 传递给 HDAmieViewerTheme
            HDAmieViewerTheme(darkTheme = isAppDark, themeColor = resolvedThemeColor) {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val coroutineScope = rememberCoroutineScope()
                val context = LocalContext.current

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val isHomeRoute = currentRoute == "Home"

                val currentAppVersion = remember {
                    try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
                    } catch (e: Exception) {
                        "1.0.0"
                    }
                }

                // 加载 Iwara 账号登录状态
                var iwaraAccount by remember { mutableStateOf(IwaraAccountManager.loadUser(context)) }
                var showIwaraLoginDialog by remember { mutableStateOf(false) }

                var timetables by remember { mutableStateOf<List<TimetableData>>(emptyList()) }
                var timeProfiles by remember { mutableStateOf<List<TimeProfile>>(emptyList()) }
                var pendingBackupToImport by remember { mutableStateOf<BackupData?>(null) }
                var pendingBackupToVerify by remember { mutableStateOf<BackupData?>(null) }
                var updateInfo by remember { mutableStateOf<GithubRelease?>(null) }

                // 每天首次自动更新逻辑
                LaunchedEffect(Unit) {
                    if (autoCheckUpdate) {
                        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                        val lastCheckDate = sharedPrefs.getString("last_update_check_date", "")
                        if (lastCheckDate != todayStr) {
                            checkAppUpdate(currentAppVersion, updateChannel) { release, _, errorMsg ->
                                if (release != null) {
                                    updateInfo = release
                                } else if (errorMsg != null) {
                                    // 静默检查在后台输出 Log 以便调试，不弹 Toast 打扰用户
                                    android.util.Log.e("AppUpdate", "自动静默检测失败: $errorMsg")
                                }
                            }
                            sharedPrefs.edit().putString("last_update_check_date", todayStr).apply()
                        }
                    }
                }

                val importDocLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    if (uri != null) {
                        coroutineScope.launch {
                            try {
                                val encryptedText = withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                                }
                                if (encryptedText != null) {
                                    val decryptedText = BackupCrypto.decrypt(encryptedText)
                                    val backupData = Gson().fromJson(decryptedText, BackupData::class.java)

                                    if (backupData.appVersion != currentAppVersion) {
                                        pendingBackupToVerify = backupData
                                    } else {
                                        pendingBackupToImport = backupData
                                        navController.navigate("ImportBackup")
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "解析备份失败", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                BackHandler(enabled = drawerState.isOpen) {
                    coroutineScope.launch { drawerState.close() }
                }

                // 侧边栏跟随手指滑动渐变模糊
                val density = LocalDensity.current
                val maxDrawerOffsetPx = with(density) { 320.dp.toPx() }
                val blurProgress = remember(drawerState.offset.value) {
                    if (maxDrawerOffsetPx > 0f) {
                        ((maxDrawerOffsetPx + drawerState.offset.value) / maxDrawerOffsetPx).coerceIn(0f, 1f)
                    } else 0f
                }
                val blurRadius = (blurProgress * 16f).dp

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = isHomeRoute, // 👈 仅在主页允许右滑手势展开侧边栏
                    drawerContent = {
                        MainDrawerSheet(
                            iwaraAccount = iwaraAccount,
                            onCloseDrawer = { coroutineScope.launch { drawerState.close() } },
                            onNavigateToSettings = { navController.navigate("Settings") },
                            onNavigateToAbout = { navController.navigate("About") },
                            onCheckUpdate = {
                                Toast.makeText(context, "正在检查更新...", Toast.LENGTH_SHORT).show()
                                coroutineScope.launch {
                                    val sharedPrefs = context.getSharedPreferences("HDAmieViewerDB", Context.MODE_PRIVATE)
                                    val updateChannel = sharedPrefs.getInt("update_channel", 0)

                                    checkAppUpdate(currentVersion = currentAppVersion, channel = updateChannel) { release, isLatest, errorMsg ->
                                        if (release != null) {
                                            updateInfo = release
                                        } else if (isLatest) {
                                            Toast.makeText(context, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                                        } else {
                                            // 显示具体的 HTTP 状态码或连接报错
                                            Toast.makeText(context, "检查更新失败\n原因: $errorMsg", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            onOpenIwaraLogin = {
                                showIwaraLoginDialog = true
                            },
                            onLogoutIwara = {
                                // 1. 彻底擦除账号数据与 WebView Cookie
                                IwaraAccountManager.logoutAndClearCookies(context)
                                // 2. 重新加载为空账号，侧边栏卡片秒变"未登录"
                                iwaraAccount = IwaraAccountManager.loadUser(context)
                                Toast.makeText(context, "已退出登录并清除网页缓存", Toast.LENGTH_SHORT).show()
                            },
                            onNavigateToDownloadManager = {
                                navController.navigate("DownloadManager")
                            }
                        )
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .blur(blurRadius)
                    ) {
                        NavHost(navController = navController, startDestination = "Home") {
                            composable(
                                route = "Home",
                                popEnterTransition = { scaleIn(initialScale = 0.9f, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400)) },
                                exitTransition = { scaleOut(targetScale = 0.9f, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400)) }
                            ) {
                                HomeScreen(
                                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                                    onVideoClick = { videoId ->
                                        navController.navigate("Player/$videoId")
                                    },
                                    onAuthorClick = { username ->
                                        navController.navigate("Profile/$username")
                                    },
                                    onNavigateToSearchResults = { query, type, sort ->
                                        navController.navigate("SearchResults/$query/$type/$sort")
                                    }
                                )
                            }

                            composable(
                                route = "Settings",
                                enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
                                exitTransition = { scaleOut(targetScale = 0.9f, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400)) },
                                popEnterTransition = { scaleIn(initialScale = 0.9f, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400)) },
                                popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) }
                            ) {
                                SettingsPage(
                                    themeMode = themeMode,
                                    onThemeChange = { themeMode = it },
                                    themeColor = themeColor,
                                    onThemeColorChange = { themeColor = it },
                                    updateChannel = updateChannel,
                                    onUpdateChannelChange = { updateChannel = it },
                                    autoCheckUpdate = autoCheckUpdate,
                                    onAutoCheckUpdateChange = { autoCheckUpdate = it },
                                    onManualCheckUpdate = {
                                        Toast.makeText(context, "正在检查更新...", Toast.LENGTH_SHORT).show()
                                        coroutineScope.launch {
                                            checkAppUpdate(currentAppVersion, updateChannel) { release, isLatest, errorMsg ->
                                                if (release != null) {
                                                    updateInfo = release
                                                } else if (isLatest) {
                                                    Toast.makeText(context, "当前已是最新版本 ($currentAppVersion)", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    // 显示具体的 HTTP 状态码或连接报错
                                                    Toast.makeText(context, "检查更新失败\n原因: $errorMsg", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    },
                                    onNavigateToExportBackup = { navController.navigate("ExportBackup") },
                                    onImportBackupClick = { importDocLauncher.launch(arrayOf("*/*")) },
                                    onNavigateToAbout = { navController.navigate("About") },
                                    onBackClick = { navController.popBackStack() },
                                    playerType = playerType,
                                    onPlayerTypeChange = { playerType = it },
                                    onNavigateToFullscreenMarginSettings = {
                                        navController.navigate("FullscreenMarginSettings")
                                    },
                                    onNavigateToFullscreenSwipeSettings = {
                                        navController.navigate("FullscreenSwipeSettings")
                                    },
                                    onNavigateToNonFullscreenSwipeSettings = {
                                        navController.navigate("NonFullscreenSwipeSettings")
                                    }
                                )
                            }

                            composable(
                                route = "About",
                                enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Up, animationSpec = tween(400)) },
                                popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Down, animationSpec = tween(400)) }
                            ) {
                                AboutPage(onBackClick = { navController.popBackStack() })
                            }

                            // 搜索结果页路由
                            composable(
                                route = "SearchResults/{query}/{type}/{sort}",
                                arguments = listOf(
                                    navArgument("query") { type = NavType.StringType },
                                    navArgument("type") { type = NavType.StringType },
                                    navArgument("sort") { type = NavType.StringType }
                                ),
                                // 从屏幕下方向上滑入进入页面
                                enterTransition = {
                                    slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Up, animationSpec = tween(400))
                                },
                                // 从播放器按返回回到搜索页时，直接原地呈现，不播放二次滑入动画
                                popEnterTransition = {
                                    fadeIn(animationSpec = tween(0))
                                },
                                // 按返回正常退出搜索页时，向下滑出屏幕
                                popExitTransition = {
                                    slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Down, animationSpec = tween(400))
                                }
                            ) { backStackEntry ->
                                val query = backStackEntry.arguments?.getString("query") ?: ""
                                val type = backStackEntry.arguments?.getString("type") ?: "videos"
                                val sort = backStackEntry.arguments?.getString("sort") ?: "relevance"

                                SearchResultsScreen(
                                    initialQuery = query,
                                    initialType = type,
                                    initialSort = sort,
                                    onBackClick = { navController.popBackStack() },
                                    onVideoClick = { videoId ->
                                        navController.navigate("Player/$videoId")
                                    }
                                )
                            }

// 全屏屏幕边距调整页面路由
                            composable(
                                route = "FullscreenMarginSettings",
                                enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
                                popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) }
                            ) {
                                FullscreenMarginSettingsPage(
                                    onBackClick = { navController.popBackStack() }
                                )
                            }

                            // 全屏滑动进度设置页面路由
                            composable(
                                route = "FullscreenSwipeSettings",
                                enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
                                popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) }
                            ) {
                                FullscreenSwipeSettingsScreen(
                                    onBackClick = { navController.popBackStack() }
                                )
                            }

                            // 非全屏滑动进度设置页面路由
                            composable(
                                route = "NonFullscreenSwipeSettings",
                                enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
                                popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) }
                            ) {
                                NonFullscreenSwipeSettingsScreen(
                                    onBackClick = { navController.popBackStack() }
                                )
                            }

                            composable(
                                route = "ExportBackup",
                                enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
                                popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) }
                            ) {
                                ExportBackupScreen(
                                    currentAppVersion = currentAppVersion,
                                    timetables = timetables,
                                    timeProfiles = timeProfiles,
                                    onBackClick = { navController.popBackStack() }
                                )
                            }

                            composable(
                                route = "ImportBackup",
                                enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
                                popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) }
                            ) {
                                ImportBackupScreen(
                                    backupData = pendingBackupToImport,
                                    currentTimetables = timetables,
                                    currentTimeProfiles = timeProfiles,
                                    onImportSuccess = { updatedTimetables, updatedProfiles ->
                                        timetables = updatedTimetables
                                        timeProfiles = updatedProfiles
                                        themeMode = sharedPrefs.getInt("theme_mode", 0)
                                        themeColor = sharedPrefs.getLong("theme_color", defaultColor)
                                        updateChannel = sharedPrefs.getInt("update_channel", 0)
                                        autoCheckUpdate = sharedPrefs.getBoolean("auto_check_update", true)
                                    },
                                    onBackClick = {
                                        pendingBackupToImport = null
                                        navController.popBackStack()
                                    }
                                )
                            }

                            // 1. 注册播放器路由
                            composable(
                                route = "Player/{videoId}",
                                arguments = listOf(navArgument("videoId") { type = NavType.StringType }),
                                enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
                                popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) }
                            ) { backStackEntry ->
                                val videoId = backStackEntry.arguments?.getString("videoId") ?: ""
                                VideoPlayerScreen(
                                    videoId = videoId,
                                    onBackClick = { navController.popBackStack() },
                                    onHomeClick = {
                                        // 返回主页面 Home，并清空中间栈
                                        navController.navigate("Home") {
                                            popUpTo("Home") { inclusive = true }
                                        }
                                    },
                                    onNavigateToSearchResults = { query, type, sort ->
                                        navController.navigate("SearchResults/$query/$type/$sort")
                                    }
                                )
                            }

                            // 下载管理路由（放在 NavHost 内部）
                            composable(
                                route = "DownloadManager",
                                enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
                                exitTransition = { scaleOut(targetScale = 0.9f, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400)) },
                                popEnterTransition = { scaleIn(initialScale = 0.9f, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400)) },
                                popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) }
                            ) {
                                DownloadManagerScreen(
                                    onBackClick = { navController.popBackStack() },
                                    onVideoClick = { videoId ->
                                        navController.navigate("Player/$videoId")
                                    }
                                )
                            }

                            // 注册作者主页路由
                            composable(
                                route = "Profile/{username}",
                                arguments = listOf(navArgument("username") { type = NavType.StringType }),
                                enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
                                popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) }
                            ) { backStackEntry ->
                                val username = backStackEntry.arguments?.getString("username") ?: ""
                                com.jiaweiya.hdamieviewer.iwara.IwaraProfilePage(
                                    username = username,
                                    onBackClick = { navController.popBackStack() },
                                    onVideoClick = { videoId ->
                                        navController.navigate("Player/$videoId")
                                    },
                                    onAuthorClick = { newUsername ->
                                        if (newUsername != username) {
                                            navController.navigate("Profile/$newUsername")
                                        }
                                    }
                                )
                            }
                        }

                        // 版本不一致弹窗提示
                        if (pendingBackupToVerify != null) {
                            AlertDialog(
                                onDismissRequest = { pendingBackupToVerify = null },
                                title = { Text("备份版本不一致警告", fontWeight = FontWeight.Bold) },
                                text = {
                                    Text("该备份文件的源应用版本为 [${pendingBackupToVerify!!.appVersion}]，当前运行的版本为 [$currentAppVersion]。是否继续导入？")
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            pendingBackupToImport = pendingBackupToVerify
                                            pendingBackupToVerify = null
                                            navController.navigate("ImportBackup")
                                        }
                                    ) { Text("继续导入") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { pendingBackupToVerify = null }) {
                                        Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            )
                        }

                        // 新版本弹窗更新提示
                        if (updateInfo != null) {
                            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                            AlertDialog(
                                onDismissRequest = { updateInfo = null },
                                title = { Text("发现新版本：${updateInfo!!.tag_name}", fontWeight = FontWeight.Bold) },
                                text = {
                                    Text(updateInfo!!.body, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                confirmButton = {
                                    Button(onClick = {
                                        uriHandler.openUri(updateInfo!!.html_url)
                                        updateInfo = null
                                    }) { Text("前往 GitHub 下载") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { updateInfo = null }) { Text("暂不更新", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            )
                        }

                        // 显示 Iwara WebView 登录弹窗
                        if (showIwaraLoginDialog) {
                            IwaraLoginDialog(
                                onDismissRequest = { showIwaraLoginDialog = false },
                                onLoginSuccess = { newUser ->
                                    iwaraAccount = newUser
                                    IwaraAccountManager.saveUser(context, newUser)
                                    showIwaraLoginDialog = false
                                    Toast.makeText(context, "登录成功: ${newUser.username}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Immutable
data class NodeTime(val label: String, val start: String, val end: String, val isVisible: Boolean = true)

@Immutable
data class TimeProfile(val id: Int, val name: String, val nodes: List<NodeTime>)

@Immutable
data class TimetableData(
    val id: Int,
    val name: String,
    val courses: List<Course>,
    val termStart: String? = null,
    val timeProfileId: Int = 1,
    val totalWeeks: Int = 20
)

@Immutable
data class Course(
    val id: Int, val name: String, val room: String, val teacher: String,
    val dayOfWeek: Int, val startNode: Int, val endNode: Int, val weekList: List<Int>,
    val bgColor: Long = 0xFFE8EAF6L, val textColor: Long = 0xFF000000L,
    val credits: String = "", val notes: String = ""
)