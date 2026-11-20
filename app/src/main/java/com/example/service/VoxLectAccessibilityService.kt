package com.example.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.model.ScreenTextNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import java.util.UUID

class VoxLectAccessibilityService : AccessibilityService() {

    private var lastAutoSelectionSignature: String = ""
    private var lastAutoSelectionTimeMs: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeServiceRef = WeakReference(this)
        _isConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName?.toString() == packageName) return

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            val engine = VoxLectEngine.getInstance(applicationContext)
            if (!engine.settings.value.autoReadSelection) return

            val from = event.fromIndex
            val to = event.toIndex
            if (from >= 0 && to > from) {
                val fullText = event.text.joinToString(" ").ifBlank {
                    event.source?.text?.toString().orEmpty()
                }
                if (fullText.length >= to) {
                    val selected = fullText.substring(from, to).trim()
                    val now = System.currentTimeMillis()
                    if (selected.length >= 3 &&
                        (selected != lastAutoSelectionSignature || now - lastAutoSelectionTimeMs > 1200L)
                    ) {
                        lastAutoSelectionSignature = selected
                        lastAutoSelectionTimeMs = now
                        engine.processAndSpeak(selected, sourceLabel = "Sélection texte")
                    }
                }
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (activeServiceRef?.get() == this) {
            activeServiceRef = null
        }
        _isConnected.value = false
        super.onDestroy()
    }

    fun captureVisibleTextNodes(): List<ScreenTextNode> {
        val collected = mutableListOf<ScreenTextNode>()
        val seenTextAndBounds = HashSet<String>()

        val windowRoots = mutableListOf<AccessibilityNodeInfo>()
        runCatching {
            windows?.forEach { window ->
                val root = window.root
                if (root != null && root.packageName?.toString() != packageName) {
                    windowRoots.add(root)
                }
            }
        }
        if (windowRoots.isEmpty()) {
            rootInActiveWindow?.let { root ->
                if (root.packageName?.toString() != packageName) {
                    windowRoots.add(root)
                }
            }
        }

        for (root in windowRoots) {
            traverseNode(root, collected, seenTextAndBounds)
        }

        return collected.sortedWith(
            compareBy<ScreenTextNode>({ it.boundsInScreen.top }, { it.boundsInScreen.left })
        )
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo?,
        output: MutableList<ScreenTextNode>,
        seen: MutableSet<String>
    ) {
        if (node == null || !node.isVisibleToUser) return

        val nodeText = node.text?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }

        if (!nodeText.isNullOrBlank() && nodeText.length >= 2) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (!rect.isEmpty && rect.width() >= 24 && rect.height() >= 20) {
                val signature = "${nodeText}|${rect.left},${rect.top},${rect.right},${rect.bottom}"
                if (seen.add(signature)) {
                    output.add(
                        ScreenTextNode(
                            id = UUID.randomUUID().toString(),
                            text = nodeText,
                            boundsInScreen = rect,
                            className = node.className?.toString().orEmpty()
                        )
                    )
                }
            }
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull()
            if (child != null) {
                traverseNode(child, output, seen)
            }
        }
    }

    companion object {
        @Volatile
        private var activeServiceRef: WeakReference<VoxLectAccessibilityService>? = null

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        fun getActiveInstance(): VoxLectAccessibilityService? = activeServiceRef?.get()
    }
}
