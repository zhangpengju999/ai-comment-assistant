package com.buddy.commentassist

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍服务：负责读取当前屏幕上的可见文字（图文帖子的文本部分）。
 * 需要在系统设置中手动开启。
 */
class AssistReaderService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AssistReaderService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不需要实时监听，读屏按需触发即可
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /**
     * 遍历当前活动窗口的节点树，收集所有可见文字。
     * 返回拼接后的文本（最多 4000 字，防止 prompt 过长）。
     */
    fun captureText(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        val visited = HashSet<AccessibilityNodeInfo>()
        traverse(root, sb, visited)
        return sb.toString().trim().take(4000)
    }

    private fun traverse(node: AccessibilityNodeInfo, sb: StringBuilder, visited: MutableSet<AccessibilityNodeInfo>) {
        if (node in visited) return
        visited.add(node)

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrBlank() && node.isVisibleToUser) {
            // 去重：同一行文字常被多层容器重复携带
            if (!sb.endsWith(text)) {
                sb.append(text).append('\n')
            }
        }
        val desc = node.contentDescription?.toString()?.trim()
        if (!desc.isNullOrBlank() && node.isVisibleToUser) {
            sb.append(desc).append('\n')
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { traverse(it, sb, visited) }
        }
    }
}
