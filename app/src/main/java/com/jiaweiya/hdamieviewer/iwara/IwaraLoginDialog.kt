package com.jiaweiya.hdamieviewer.iwara

import android.annotation.SuppressLint
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.gson.Gson
import com.google.gson.JsonObject

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun IwaraLoginDialog(
    onDismissRequest: () -> Unit,
    onLoginSuccess: (IwaraAccount) -> Unit
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(false) } // 默认设为 false，防止卡死
    var isExtracting by remember { mutableStateOf(false) }

    // 触发账号信息提取逻辑
    fun triggerAccountExtraction() {
        val wv = webViewRef ?: run {
            Toast.makeText(context, "网页仍在初始化中，请稍后", Toast.LENGTH_SHORT).show()
            return
        }
        isExtracting = true

        // 5秒保底超时处理，防止按钮卡死在“识别中...”
        wv.postDelayed({
            if (isExtracting) {
                isExtracting = false
                Toast.makeText(context, "提取超时，请确认页面已显示个人主页后重试", Toast.LENGTH_SHORT).show()
            }
        }, 5000)

        val jsScript = """
            (async function() {
                try {
                    let username = '';
                    let handle = '';
                    let avatarUrl = '';

                    // 拼接 Iwara API 返回的 Avatar 对象的工具函数
                    function buildAvatarUrl(avatarObj) {
                        if (!avatarObj) return '';
                        if (typeof avatarObj === 'string') {
                            return avatarObj.startsWith('http') ? avatarObj : 'https://files.iwara.tv/image/avatar/' + avatarObj;
                        }
                        let id = avatarObj.id || '';
                        let name = avatarObj.name || '';
                        let path = avatarObj.path || 'avatar';
                        if (id && name) {
                            return 'https://files.iwara.tv/image/' + path + '/' + id + '/' + name;
                        } else if (id) {
                            return 'https://files.iwara.tv/image/avatar/' + id + '/avatar.jpg';
                        } else if (avatarObj.avatarUrl) {
                            let url = avatarObj.avatarUrl;
                            return url.startsWith('http') ? url : 'https://files.iwara.tv' + url;
                        }
                        return '';
                    }

                    // 1. 优先从 LocalStorage 或 API 获取
                    try {
                        let token = localStorage.getItem('token') || localStorage.getItem('access_token');
                        if (token) {
                            let res = await fetch('https://api.iwara.tv/user/me', {
                                headers: { 'Authorization': 'Bearer ' + token }
                            });
                            if (res.ok) {
                                let u = await res.json();
                                username = u.name || u.username || '';
                                handle = u.username || '';
                                avatarUrl = buildAvatarUrl(u.avatar);
                            }
                        }
                    } catch(e) {}

                    // 2. 提取页面中的 Handle (@xxx)
                    if (!handle) {
                        let match = document.body.innerText.match(/@([a-zA-Z0-9_-]+)/);
                        if (match) handle = match[1];
                    }

                    // 3. 通过 API 查询句柄获得个人资料
                    if (handle && (!username || !avatarUrl)) {
                        try {
                            let res = await fetch('https://api.iwara.tv/user/' + handle);
                            if (res.ok) {
                                let u = await res.json();
                                if (!username) username = u.name || u.username;
                                if (!avatarUrl) avatarUrl = buildAvatarUrl(u.avatar);
                            }
                        } catch(e) {}
                    }

                    // 4. 全扫描 DOM 节点（扫描 <img> 标签 + CSS 背景图）
                    if (!avatarUrl) {
                        let candidates = [];

                        // 扫描所有 <img> 标签 (包含 src, currentSrc, data-src)
                        document.querySelectorAll('img').forEach(img => {
                            if (!img.closest('[class*="following"], [class*="followers"]')) {
                                let src = img.currentSrc || img.src || img.getAttribute('data-src') || '';
                                if (src && (src.includes('files.iwara.tv') || src.includes('avatar') || src.includes('picture'))) {
                                    candidates.push(src);
                                }
                            }
                        });

                        // 扫描所有带有 CSS background-image 的 <div>/<span>
                        document.querySelectorAll('*').forEach(el => {
                            if (!el.closest('[class*="following"], [class*="followers"]')) {
                                let bg = window.getComputedStyle(el).backgroundImage;
                                if (bg && bg !== 'none' && bg.includes('url(')) {
                                    let match = bg.match(/url\((['"]?)(.*?)\1\)/);
                                    if (match && match[2] && (match[2].includes('iwara') || match[2].includes('avatar') || match[2].includes('image'))) {
                                        candidates.push(match[2]);
                                    }
                                }
                            }
                        });

                        if (candidates.length > 0) {
                            avatarUrl = candidates[0];
                        }
                    }

                    if (!username && handle) {
                        let handleEl = Array.from(document.querySelectorAll('*')).find(el => 
                            el.children.length === 0 && el.innerText && el.innerText.trim() === '@' + handle
                        );
                        if (handleEl && handleEl.parentElement) {
                            let prev = handleEl.previousElementSibling;
                            if (prev && prev.innerText && !prev.innerText.includes('My profile')) {
                                username = prev.innerText.trim();
                            }
                        }
                    }

                    // 输出结果
                    if (username || handle || avatarUrl) {
                        let result = {
                            success: true,
                            username: username || handle || 'Iwara User',
                            handle: handle || username || '',
                            avatarUrl: avatarUrl || ''
                        };
                        console.log("IWARA_LOGIN_SUCCESS:" + JSON.stringify(result));
                    } else {
                        console.log("IWARA_LOGIN_FAILED:未能解析到账号信息");
                    }
                } catch(e) {
                    console.log("IWARA_LOGIN_FAILED:" + e.toString());
                }
            })();
        """.trimIndent()

        wv.evaluateJavascript(jsScript, null)
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶部标题栏
                TopAppBar(
                    title = {
                        Column {
                            Text("Iwara 登录", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "请在下方登录 Iwara 账号，登录完成后将自动获取您的个人信息。",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            isLoading = true
                            webViewRef?.reload()
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                        IconButton(onClick = onDismissRequest) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    }
                )

                // 仅在明确刷新时显示顶部细进度条
                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // WebView 主体
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewRef = this
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true

                                    setSupportZoom(true)
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    useWideViewPort = true
                                    loadWithOverviewMode = true

                                    userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                                }

                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                webChromeClient = object : WebChromeClient() {
                                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                        val msg = consoleMessage?.message() ?: ""
                                        if (msg.startsWith("IWARA_LOGIN_SUCCESS:")) {
                                            isExtracting = false
                                            val jsonStr = msg.removePrefix("IWARA_LOGIN_SUCCESS:")
                                            try {
                                                val json = Gson().fromJson(jsonStr, JsonObject::class.java)
                                                val account = IwaraAccount(
                                                    isLoggedIn = true,
                                                    username = json.get("username")?.asString ?: "Iwara User",
                                                    handle = json.get("handle")?.asString ?: "",
                                                    avatarUrl = json.get("avatarUrl")?.asString ?: ""
                                                )
                                                onLoginSuccess(account)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "数据解析异常", Toast.LENGTH_SHORT).show()
                                            }
                                            return true
                                        } else if (msg.startsWith("IWARA_LOGIN_FAILED:")) {
                                            isExtracting = false
                                            Toast.makeText(context, "未能检测到有效的登录信息，请确认已在页面中登录", Toast.LENGTH_LONG).show()
                                            return true
                                        }
                                        return super.onConsoleMessage(consoleMessage)
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isLoading = false
                                        webViewRef = view
                                    }
                                }

                                loadUrl("https://www.iwara.tv/login")
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // 底部按钮：去掉强制等待加载限制，随时可点击
                    Button(
                        onClick = { triggerAccountExtraction() },
                        enabled = !isExtracting, // 仅在正在解析数据时防止重复点击
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                    ) {
                        if (isExtracting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("正在提取信息...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("提取账号信息并登录", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}