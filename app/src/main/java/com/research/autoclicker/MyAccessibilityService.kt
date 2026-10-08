package com.research.autoclicker

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MyAccessibilityService : AccessibilityService() {

    // روی Main اجرا می‌شود تا دسترسی به نودها امن باشد
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // فقط یک اسکن در هر لحظه؛ رویدادهای پشت‌سرهم ادغام می‌شوند (رفع مشکل هنگ کردن)
    private var scanJob: Job? = null
    private var lastClickAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        AutomationState.init(this)
        AutomationState.setServiceConnected(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !AutomationState.isRunning.value) return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) return

        // اگر اپ هدف مشخص شده، رویدادهای اپ‌های دیگر نادیده گرفته شوند
        val targetPkg = AutomationState.targetPackage.value.trim()
        if (targetPkg.isNotEmpty() && event.packageName?.toString() != targetPkg) return

        // اگر یک اسکن در صف است، رویداد جدید فقط همان را کافی می‌داند
        if (scanJob?.isActive == true) return

        // اگر هنوز در فاصله‌ی بین دو کلیک هستیم، تا پایان آن صبر کن تا تغییری از دست نرود
        val remainingCooldown = lastClickAt + cooldownMs() - SystemClock.elapsedRealtime()
        val wait = maxOf(scanDelayMs(), remainingCooldown)

        scanJob = serviceScope.launch {
            delay(wait)
            scanAndClick()
        }
    }

    /** ضریب سرعت ۱ تا ۱۰ (هرچه بزرگ‌تر، تأخیر کمتر) */
    private fun speedFactor(): Float = AutomationState.speed.value.coerceIn(1f, 10f)

    /** تأخیر قبل از اسکن: سرعت ۱ = ۵۰۰ms ، سرعت ۱۰ = ۵۰ms */
    private fun scanDelayMs(): Long = (500f / speedFactor()).toLong()

    /** فاصله‌ی حداقل بین دو کلیک: سرعت ۱ = ۲ ثانیه، سرعت ۱۰ = ۲۰۰ms */
    private fun cooldownMs(): Long = (2000f / speedFactor()).toLong()

    private fun scanAndClick() {
        if (!AutomationState.isRunning.value) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastClickAt < cooldownMs()) return

        val rootNode = rootInActiveWindow ?: return

        val targetPkg = AutomationState.targetPackage.value.trim()
        if (targetPkg.isNotEmpty() && rootNode.packageName?.toString() != targetPkg) return

        // ۱. فیلترها
        val required = AutomationState.requiredKeyword.value.trim()
        if (required.isNotEmpty() &&
            rootNode.findAccessibilityNodeInfosByText(required).isNullOrEmpty()
        ) return

        val excluded = AutomationState.excludedKeyword.value.trim()
        if (excluded.isNotEmpty() &&
            !rootNode.findAccessibilityNodeInfosByText(excluded).isNullOrEmpty()
        ) {
            AutomationState.recordInfo("رد شد: «$excluded» روی صفحه بود")
            return
        }

        // ۲. پیدا کردن دکمه‌ی هدف و کلیک
        val (targets, label) = findTargets(rootNode)
        for (node in targets) {
            if (clickNodeOrParent(node)) {
                lastClickAt = now
                AutomationState.recordClick("کلیک روی «$label»")
                break
            }
        }
    }

    /** اگر شناسه‌ی View داده شده باشد با آن، وگرنه با متن جستجو می‌کند */
    private fun findTargets(root: AccessibilityNodeInfo): Pair<List<AccessibilityNodeInfo>, String> {
        val viewId = AutomationState.targetViewId.value.trim()
        if (viewId.isNotEmpty()) {
            // شناسه‌ی کامل به شکل package:id/name است؛ اگر فقط name داده شده، کامل می‌شود
            val fullId = when {
                viewId.contains(":id/") -> viewId
                root.packageName != null -> "${root.packageName}:id/$viewId"
                else -> viewId
            }
            return (root.findAccessibilityNodeInfosByViewId(fullId) ?: emptyList()) to fullId
        }

        val text = AutomationState.targetText.value.trim()
        if (text.isEmpty()) return emptyList<AccessibilityNodeInfo>() to ""
        return (root.findAccessibilityNodeInfosByText(text) ?: emptyList()) to text
    }

    /** خود نود یا نزدیک‌ترین والد قابل‌کلیک را کلیک می‌کند */
    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 10) {
            if (current.isClickable && current.isEnabled && current.isVisibleToUser) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
            depth++
        }
        return false
    }

    override fun onInterrupt() {
        // سیستم بازخورد را قطع کرده؛ کاری لازم نیست
    }

    override fun onDestroy() {
        serviceScope.cancel()
        AutomationState.setServiceConnected(false)
        super.onDestroy()
    }
}
