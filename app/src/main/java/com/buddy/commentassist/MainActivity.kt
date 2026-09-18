package com.buddy.commentassist

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var etRole: EditText
    private lateinit var etBaseUrl: EditText
    private lateinit var etApiKey: EditText
    private lateinit var etModel: EditText
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etRole = findViewById(R.id.etRole)
        etBaseUrl = findViewById(R.id.etBaseUrl)
        etApiKey = findViewById(R.id.etApiKey)
        etModel = findViewById(R.id.etModel)
        tvStatus = findViewById(R.id.tvStatus)

        // 回填已保存配置
        etRole.setText(Prefs.role(this))
        etBaseUrl.setText(Prefs.baseUrl(this))
        etApiKey.setText(Prefs.apiKey(this))
        etModel.setText(Prefs.model(this))

        findViewById<Button>(R.id.btnSave).setOnClickListener { savePrefs() }

        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                Toast.makeText(this, "悬浮窗权限已授权 ✓", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnA11y).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            savePrefs()
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请先授权悬浮窗权限", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (AssistReaderService.instance == null) {
                tvStatus.text = "⚠️ 无障碍服务未开启：悬浮窗可以启动，但「阅读」会读不到内容。\n请到系统设置 → 无障碍 → 开启「AI评论小助手读屏服务」。"
            } else {
                tvStatus.text = "✓ 一切就绪，悬浮窗已启动。切到任意图文帖子页面即可使用。"
            }
            ContextCompat.startForegroundService(this, Intent(this, FloatingService::class.java))
        }
    }

    private fun savePrefs() {
        Prefs.save(
            this,
            role = etRole.text.toString().trim(),
            baseUrl = etBaseUrl.text.toString().trim().ifBlank { "https://api.deepseek.com" },
            apiKey = etApiKey.text.toString().trim(),
            model = etModel.text.toString().trim().ifBlank { "deepseek-chat" }
        )
        Toast.makeText(this, "设置已保存 ✓", Toast.LENGTH_SHORT).show()
    }
}
