package com.buddy.commentassist

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

/**
 * 悬浮窗服务：
 * - 收起时是一个 56dp 的圆形悬浮球，可拖动，点击展开
 * - 展开后：阅读当前屏幕 / 三平台评论生成 / 复制 / 关闭
 */
class FloatingService : Service() {

    companion object {
        private const val CHANNEL_ID = "floating_channel"
        private const val NOTI_ID = 1001
        private const val DRAG_THRESHOLD = 8
    }

    private lateinit var wm: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private var root: View? = null
    private var collapsed: View? = null
    private var panel: View? = null
    private var resultText: TextView? = null

    private lateinit var mainHandler: Handler
    private var screenText: String = ""
    private var lastResult: String = ""

    // 拖动状态
    private var downRawX = 0f
    private var downRawY = 0f
    private var startPanelX = 0
    private var startPanelY = 0
    private var moved = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        mainHandler = Handler(Looper.getMainLooper())
        startForegroundWithNotification()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        setupOverlay()
    }

    override fun onDestroy() {
        root?.let { wm.removeView(it) }
        super.onDestroy()
    }

    private fun startForegroundWithNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "评论小助手", NotificationManager.IMPORTANCE_LOW)
        )
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("AI 评论小助手运行中")
            .setContentText("悬浮窗已就绪")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(pi)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTI_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTI_ID, notification)
        }
    }

    private fun setupOverlay() {
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 300
        }

        root = LayoutInflater.from(this).inflate(R.layout.floating_panel, null)
        wm.addView(root, params)

        collapsed = root!!.findViewById(R.id.collapsedView)
        panel = root!!.findViewById(R.id.panelView)
        resultText = panel!!.findViewById(R.id.resultText)

        val dragListener = View.OnTouchListener { v, e ->
            handleTouch(v, e)
            true
        }
        collapsed!!.setOnTouchListener(dragListener)
        panel!!.findViewById<View>(R.id.headerBar).setOnTouchListener(dragListener)

        panel!!.findViewById<Button>(R.id.btnRead).setOnClickListener { onRead() }
        panel!!.findViewById<Button>(R.id.btnDouyin).setOnClickListener { onGenerate("抖音") }
        panel!!.findViewById<Button>(R.id.btnXhs).setOnClickListener { onGenerate("小红书") }
        panel!!.findViewById<Button>(R.id.btnChannel).setOnClickListener { onGenerate("视频号") }
        panel!!.findViewById<Button>(R.id.btnCopy).setOnClickListener { onCopy() }
        panel!!.findViewById<TextView>(R.id.btnStop).setOnClickListener { stopSelf() }
    }

    private fun handleTouch(v: View, e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = e.rawX
                downRawY = e.rawY
                startPanelX = params.x
                startPanelY = params.y
                moved = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (e.rawX - downRawX).toInt()
                val dy = (e.rawY - downRawY).toInt()
                if (!moved && (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD)) {
                    moved = true
                }
                if (moved) {
                    params.x = startPanelX + dx
                    params.y = startPanelY + dy
                    try {
                        wm.updateViewLayout(root, params)
                    } catch (_: Exception) {
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!moved) {
                    when (v.id) {
                        R.id.collapsedView -> showPanel(true)
                        R.id.headerBar -> showPanel(false)
                    }
                }
            }
        }
    }

    private fun showPanel(show: Boolean) {
        panel?.visibility = if (show) View.VISIBLE else View.GONE
        collapsed?.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun setResult(text: String) {
        lastResult = text
        resultText?.text = text
    }

    private fun onRead() {
        val svc = AssistReaderService.instance
        val text = svc?.captureText() ?: ""
        if (text.isBlank()) {
            setResult("⚠️ 没有读到内容。\n请确认：1) 已在系统设置开启本应用的无障碍服务；2) 当前页面是普通图文帖子页。")
            return
        }
        screenText = text
        val preview = if (text.length > 400) text.take(400) + "\n……(共${text.length}字)" else text
        setResult("✅ 已读取屏幕内容（${text.length}字）：\n\n$preview")
    }

    private fun onGenerate(platform: String) {
        if (screenText.isBlank()) {
            onRead()
            if (screenText.isBlank()) return
        }
        setButtonsEnabled(false)
        setResult("⏳ 正在生成${platform}评论版…")
        val appContext = applicationContext
        CommentGenerator.generate(appContext, platform, screenText) { success, error ->
            setButtonsEnabled(true)
            if (success != null) {
                setResult(success)
            } else {
                setResult("❌ 生成失败：$error")
            }
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        panel?.findViewById<Button>(R.id.btnDouyin)?.isEnabled = enabled
        panel?.findViewById<Button>(R.id.btnXhs)?.isEnabled = enabled
        panel?.findViewById<Button>(R.id.btnChannel)?.isEnabled = enabled
    }

    private fun onCopy() {
        if (lastResult.isBlank()) {
            Toast.makeText(this, "还没有可复制的内容", Toast.LENGTH_SHORT).show()
            return
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("comment", lastResult))
        Toast.makeText(this, "已复制 ✓", Toast.LENGTH_SHORT).show()
    }
}
