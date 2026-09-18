package com.buddy.commentassist

import android.content.Context

/** SharedPreferences 封装：角色人设 + API 配置 */
object Prefs {

    private const val FILE = "comment_assist"

    fun save(context: Context, role: String, baseUrl: String, apiKey: String, model: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("role", role)
            .putString("baseUrl", baseUrl)
            .putString("apiKey", apiKey)
            .putString("model", model)
            .apply()
    }

    fun role(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("role", "") ?: ""

    fun baseUrl(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString("baseUrl", "https://api.deepseek.com") ?: "https://api.deepseek.com"

    fun apiKey(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("apiKey", "") ?: ""

    fun model(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString("model", "deepseek-chat") ?: "deepseek-chat"
}
