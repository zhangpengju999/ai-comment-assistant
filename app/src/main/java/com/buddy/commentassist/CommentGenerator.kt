package com.buddy.commentassist

import android.content.Context
import android.os.Handler
import android.os.Looper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 评论生成器：调用 OpenAI 兼容的 chat/completions 接口。
 * 支持抖音 / 小红书 / 视频号三种平台风格。
 */
object CommentGenerator {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 成功回调 text；失败回调 error 信息（均在主线程） */
    fun generate(
        context: Context,
        platform: String,
        screenText: String,
        callback: (success: String?, error: String?) -> Unit
    ) {
        val baseUrl = Prefs.baseUrl(context).trimEnd('/')
        val apiKey = Prefs.apiKey(context)
        val model = Prefs.model(context)

        if (apiKey.isBlank()) {
            mainHandler.post { callback(null, "未配置 API Key，请先到主界面填写并保存") }
            return
        }

        val body = JSONObject().apply {
            put("model", model)
            put("temperature", 0.9)
            put("max_tokens", 500)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemPrompt(platform, Prefs.role(context))))
                put(JSONObject().put("role", "user").put("content", userPrompt(screenText, platform)))
            })
        }

        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback(null, "网络请求失败：${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                val respBody = try {
                    response.body?.string()
                } catch (e: Exception) {
                    null
                }
                if (!response.isSuccessful || respBody == null) {
                    mainHandler.post { callback(null, "HTTP ${response.code}：${respBody?.take(200) ?: "响应为空"}") }
                    return
                }
                try {
                    val content = JSONObject(respBody)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                        .trim()
                    mainHandler.post { callback(content.ifBlank { null }, if (content.isBlank()) "模型返回了空内容" else null) }
                } catch (e: Exception) {
                    mainHandler.post { callback(null, "解析响应失败：${e.message}\n原文：${respBody.take(200)}") }
                }
            }
        })
    }

    private fun systemPrompt(platform: String, role: String): String {
        val style = when (platform) {
            "抖音" -> """平台：抖音评论区。风格要求：
1. 口语化短句，网感强，像随手打出来的真实评论
2. 最多带1-2个emoji，不堆砌
3. 结尾可以带互动钩子（提问/共鸣），引导别人回复
4. 50~80字，禁止客套开场白，禁止"首先/其次"式排比"""
            "小红书" -> """平台：小红书评论区。风格要求：
1. 亲测分享感，真诚种草或吐槽，像朋友聊天
2. 适度emoji和换行，可用"谁懂啊/真的会谢/码住"这类社区语感
3. 100字左右，段落感强，别写成小作文"""
            else -> """平台：视频号（微信生态）评论区。风格要求：
1. 语气稳重真诚，偏成熟用户风格，言之有物、略带正能量
2. 基本不用emoji，逻辑清晰
3. 80~100字"""
        }
        return """你是一位活跃在社交平台上的真实用户，正在帖子下写评论。你的人设：${role.ifBlank { "一个对互联网产品和AI很懂行的普通人" }}。
$style
硬性要求：只输出评论正文本身，不要任何解释、引号或前缀。评论必须针对帖子内容，有具体观点，不要空洞夸奖。"""
    }

    private fun userPrompt(screenText: String, platform: String): String {
        val cleaned = screenText
            .lines()
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .take(3500)
        return """以下是从手机屏幕上识别到的帖子内容（可能有少量界面按钮文字等噪音，请忽略噪音，抓住帖子主体）：

$cleaned

请以上述内容为对象，写一条适合发在$platform评论区的评论。"""
    }
}
