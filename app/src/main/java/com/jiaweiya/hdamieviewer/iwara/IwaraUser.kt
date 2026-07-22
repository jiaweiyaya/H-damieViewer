package com.jiaweiya.hdamieviewer.iwara

import android.content.Context

data class IwaraAccount(
    val isLoggedIn: Boolean = false,
    val username: String = "",
    val handle: String = "",
    val avatarUrl: String = ""
)

object IwaraAccountManager {
    private const val PREF_NAME = "HDAmieViewerDB"
    private const val KEY_IS_LOGGED_IN = "iwara_is_logged_in"
    private const val KEY_USERNAME = "iwara_username"
    private const val KEY_HANDLE = "iwara_handle"
    private const val KEY_AVATAR_URL = "iwara_avatar_url"

    fun loadUser(context: Context): IwaraAccount {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return IwaraAccount(
            isLoggedIn = sp.getBoolean(KEY_IS_LOGGED_IN, false),
            username = sp.getString(KEY_USERNAME, "") ?: "",
            handle = sp.getString(KEY_HANDLE, "") ?: "",
            avatarUrl = sp.getString(KEY_AVATAR_URL, "") ?: ""
        )
    }

    fun saveUser(context: Context, user: IwaraAccount) {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sp.edit()
            .putBoolean(KEY_IS_LOGGED_IN, user.isLoggedIn)
            .putString(KEY_USERNAME, user.username)
            .putString(KEY_HANDLE, user.handle)
            .putString(KEY_AVATAR_URL, user.avatarUrl)
            .apply()
    }

    fun clearUser(context: Context) {
        saveUser(context, IwaraAccount(false, "", "", ""))
    }
}