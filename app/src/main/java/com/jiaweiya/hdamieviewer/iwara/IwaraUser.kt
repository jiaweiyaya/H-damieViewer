package com.jiaweiya.hdamieviewer.iwara

import android.content.Context

data class IwaraAccount(
    val isLoggedIn: Boolean = false,
    val username: String = "",
    val handle: String = "",
    val avatarUrl: String = "",
    val token: String = ""
)

object IwaraAccountManager {
    private const val PREF_NAME = "HDAmieViewerDB"
    private const val KEY_IS_LOGGED_IN = "iwara_is_logged_in"
    private const val KEY_USERNAME = "iwara_username"
    private const val KEY_HANDLE = "iwara_handle"
    private const val KEY_AVATAR_URL = "iwara_avatar_url"
    private const val KEY_TOKEN = "iwara_token"

    fun loadUser(context: Context): IwaraAccount {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return IwaraAccount(
            isLoggedIn = sp.getBoolean(KEY_IS_LOGGED_IN, false),
            username = sp.getString(KEY_USERNAME, "") ?: "",
            handle = sp.getString(KEY_HANDLE, "") ?: "",
            avatarUrl = sp.getString(KEY_AVATAR_URL, "") ?: "",
            token = sp.getString(KEY_TOKEN, "") ?: ""
        )
    }

    fun saveUser(context: Context, user: IwaraAccount) {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sp.edit()
            .putBoolean(KEY_IS_LOGGED_IN, user.isLoggedIn)
            .putString(KEY_USERNAME, user.username)
            .putString(KEY_HANDLE, user.handle)
            .putString(KEY_AVATAR_URL, user.avatarUrl)
            .putString(KEY_TOKEN, user.token)
            .apply()
    }

    fun clearUser(context: Context) {
        saveUser(context, IwaraAccount(false, "", "", "", ""))
    }

    // 彻底退出登录并清理本地账号 + WebView 全部 Cookie 与 WebStorage 缓存
    fun logoutAndClearCookies(context: Context) {
        // 1. 清空 SharedPreferences 存储
        clearUser(context)

        // 2. 强行擦除 Android 系统 WebView Cookie（账号 Session）
        try {
            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. 清空 WebView 本地 DOM 数据库与 LocalStorage
        try {
            android.webkit.WebStorage.getInstance().deleteAllData()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}