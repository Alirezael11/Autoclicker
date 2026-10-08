package com.research.autoclicker

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * وضعیت و تنظیمات مشترک بین صفحه‌ی اصلی و سرویس دسترس‌پذیری.
 * هر دو در یک پروسه اجرا می‌شوند، پس یک object کافی است.
 * تنظیمات در SharedPreferences ذخیره می‌شوند تا بعد از بستن اپ بمانند.
 */
object AutomationState {

    private const val PREFS = "automation_prefs"
    private const val KEY_SPEED = "speed"
    private const val KEY_TARGET_TEXT = "target_text"
    private const val KEY_TARGET_VIEW_ID = "target_view_id"
    private const val KEY_TARGET_PACKAGE = "target_package"
    private const val KEY_REQUIRED = "required_keyword"
    private const val KEY_EXCLUDED = "excluded_keyword"

    private var prefs: SharedPreferences? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    /** سرعت بین ۱ تا ۱۰ — هرچه بزرگ‌تر، تأخیر کمتر */
    private val _speed = MutableStateFlow(10f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    /** متن دکمه‌ای که باید کلیک شود (مثلاً Refresh) */
    private val _targetText = MutableStateFlow("Refresh")
    val targetText: StateFlow<String> = _targetText.asStateFlow()

    /** شناسه‌ی View دکمه (اختیاری؛ اگر پر باشد به جای متن استفاده می‌شود) */
    private val _targetViewId = MutableStateFlow("")
    val targetViewId: StateFlow<String> = _targetViewId.asStateFlow()

    /** نام بسته‌ی اپ هدف (اختیاری ولی توصیه‌شده) */
    private val _targetPackage = MutableStateFlow("")
    val targetPackage: StateFlow<String> = _targetPackage.asStateFlow()

    /** فیلتر: فقط وقتی کلیک کن که این متن روی صفحه باشد */
    private val _requiredKeyword = MutableStateFlow("")
    val requiredKeyword: StateFlow<String> = _requiredKeyword.asStateFlow()

    /** فیلتر: اگر این متن روی صفحه بود کلیک نکن */
    private val _excludedKeyword = MutableStateFlow("")
    val excludedKeyword: StateFlow<String> = _excludedKeyword.asStateFlow()

    private val _clickCount = MutableStateFlow(0)
    val clickCount: StateFlow<Int> = _clickCount.asStateFlow()

    private val _lastAction = MutableStateFlow("")
    val lastAction: StateFlow<String> = _lastAction.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _speed.value = p.getFloat(KEY_SPEED, 10f).coerceIn(1f, 10f)
        _targetText.value = p.getString(KEY_TARGET_TEXT, "Refresh") ?: "Refresh"
        _targetViewId.value = p.getString(KEY_TARGET_VIEW_ID, "") ?: ""
        _targetPackage.value = p.getString(KEY_TARGET_PACKAGE, "") ?: ""
        _requiredKeyword.value = p.getString(KEY_REQUIRED, "") ?: ""
        _excludedKeyword.value = p.getString(KEY_EXCLUDED, "") ?: ""
    }

    fun startAutomation() {
        _isRunning.value = true
        _lastAction.value = "اجرا شروع شد"
    }

    fun stopAutomation() {
        _isRunning.value = false
        _lastAction.value = "اجرا متوقف شد"
    }

    fun setServiceConnected(connected: Boolean) {
        _serviceConnected.value = connected
        if (!connected) _isRunning.value = false
    }

    fun setSpeed(value: Float) {
        val v = value.coerceIn(1f, 10f)
        _speed.value = v
        prefs?.edit()?.putFloat(KEY_SPEED, v)?.apply()
    }

    fun setTargetText(value: String) = saveString(_targetText, KEY_TARGET_TEXT, value)
    fun setTargetViewId(value: String) = saveString(_targetViewId, KEY_TARGET_VIEW_ID, value)
    fun setTargetPackage(value: String) = saveString(_targetPackage, KEY_TARGET_PACKAGE, value)
    fun setRequiredKeyword(value: String) = saveString(_requiredKeyword, KEY_REQUIRED, value)
    fun setExcludedKeyword(value: String) = saveString(_excludedKeyword, KEY_EXCLUDED, value)

    fun recordClick(description: String) {
        _clickCount.value = _clickCount.value + 1
        _lastAction.value = description
    }

    fun recordInfo(description: String) {
        _lastAction.value = description
    }

    fun resetCounter() {
        _clickCount.value = 0
    }

    private fun saveString(flow: MutableStateFlow<String>, key: String, value: String) {
        flow.value = value
        prefs?.edit()?.putString(key, value)?.apply()
    }
}
