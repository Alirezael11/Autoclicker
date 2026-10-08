package com.research.autoclicker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkRed = Color(0xFFB71C1C)
private val Green = Color(0xFF2E7D32)

class MainActivity : ComponentActivity() {

    private var serviceEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AutomationState.init(this)

        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = DarkRed)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        MainScreen(
                            serviceEnabled = serviceEnabled,
                            onOpenAccessibility = { openAccessibilitySettings() },
                            onOpenAppInfo = { openAppInfo() }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // بعد از برگشتن از تنظیمات، وضعیت سرویس دوباره بررسی می‌شود
        serviceEnabled = isAccessibilityServiceEnabled(this)
        if (!serviceEnabled) AutomationState.stopAutomation()
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openAppInfo() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
        )
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val me = ComponentName(context, MyAccessibilityService::class.java)
    return enabled.split(':').any { entry ->
        ComponentName.unflattenFromString(entry) == me
    }
}

@Composable
fun MainScreen(
    serviceEnabled: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenAppInfo: () -> Unit
) {
    val isRunning by AutomationState.isRunning.collectAsState()
    val speed by AutomationState.speed.collectAsState()
    val targetText by AutomationState.targetText.collectAsState()
    val targetViewId by AutomationState.targetViewId.collectAsState()
    val targetPackage by AutomationState.targetPackage.collectAsState()
    val required by AutomationState.requiredKeyword.collectAsState()
    val excluded by AutomationState.excludedKeyword.collectAsState()
    val clickCount by AutomationState.clickCount.collectAsState()
    val lastAction by AutomationState.lastAction.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp)
    ) {
        Text(
            "ربات کلیک خودکار (پژوهشی)",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // ۱. وضعیت سرویس دسترس‌پذیری
        ServiceStatusCard(serviceEnabled, onOpenAccessibility, onOpenAppInfo)

        // ۲. دکمه‌ی شروع / توقف
        Button(
            onClick = {
                if (isRunning) AutomationState.stopAutomation() else AutomationState.startAutomation()
            },
            enabled = serviceEnabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) DarkRed else Green
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(56.dp)
        ) {
            Text(if (isRunning) "توقف" else "شروع", fontSize = 18.sp)
        }

        // ۳. کنترل سرعت (اکنون به سرویس وصل است)
        SpeedControlCard(speed = speed, onSpeedChange = { AutomationState.setSpeed(it) })

        // ۴. تنظیمات هدف
        SettingsCard(title = "هدف") {
            OutlinedTextField(
                value = targetText,
                onValueChange = { AutomationState.setTargetText(it) },
                label = { Text("متن دکمه (مثلاً Refresh)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = targetViewId,
                onValueChange = { AutomationState.setTargetViewId(it) },
                label = { Text("شناسه‌ی View (اختیاری؛ بر متن مقدم است)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = targetPackage,
                onValueChange = { AutomationState.setTargetPackage(it) },
                label = { Text("نام بسته‌ی اپ هدف (توصیه‌شده)") },
                placeholder = { Text("com.example.app") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "اگر نام بسته خالی باشد، روی هر اپی که دکمه‌ای با این متن داشته باشد کلیک می‌کند.",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

        // ۵. فیلترها
        SettingsCard(title = "فیلترها") {
            OutlinedTextField(
                value = required,
                onValueChange = { AutomationState.setRequiredKeyword(it) },
                label = { Text("فقط وقتی این متن روی صفحه هست کلیک کن") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = excluded,
                onValueChange = { AutomationState.setExcludedKeyword(it) },
                label = { Text("اگر این متن روی صفحه بود کلیک نکن") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ۶. آمار
        SettingsCard(title = "آمار") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("تعداد کلیک: $clickCount", fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { AutomationState.resetCounter() }) { Text("صفر کردن") }
            }
            if (lastAction.isNotEmpty()) {
                Text("آخرین رویداد: $lastAction", fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun ServiceStatusCard(
    serviceEnabled: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenAppInfo: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (serviceEnabled) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                if (serviceEnabled) "✅ سرویس دسترس‌پذیری فعال است"
                else "⚠️ سرویس دسترس‌پذیری غیرفعال است",
                fontWeight = FontWeight.Bold
            )
            if (!serviceEnabled) {
                Text(
                    "در تنظیمات دسترس‌پذیری، «Auto Clicker» را روشن کنید. " +
                        "اگر گزینه خاکستری بود (اندروید ۱۳ به بعد)، در «اطلاعات برنامه» از منوی ⋮ " +
                        "گزینه‌ی Allow restricted settings را بزنید و دوباره امتحان کنید.",
                    fontSize = 13.sp
                )
                Button(onClick = onOpenAccessibility, modifier = Modifier.fillMaxWidth()) {
                    Text("باز کردن تنظیمات دسترس‌پذیری")
                }
                OutlinedButton(onClick = onOpenAppInfo, modifier = Modifier.fillMaxWidth()) {
                    Text("اطلاعات برنامه")
                }
            }
        }
    }
}

@Composable
fun SpeedControlCard(speed: Float, onSpeedChange: (Float) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkRed) // رنگ قرمز تیره
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("AI Speed Boost", color = Color.White, fontWeight = FontWeight.Bold)
            Text("${speed.toInt()}x", color = Color.White)

            Slider(
                value = speed,
                onValueChange = onSpeedChange,
                valueRange = 1f..10f, // محدوده سرعت از ۱ تا ۱۰
                steps = 8, // ۱ تا ۱۰ یعنی ۸ پله بین آن‌ها
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White
                )
            )

            val delayMs = (500f / speed).toInt()
            Text(
                "تأخیر واکنش: حدود $delayMs میلی‌ثانیه",
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            content()
        }
    }
}
