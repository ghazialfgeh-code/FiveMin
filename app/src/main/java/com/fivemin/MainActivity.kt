package com.fivemin

import android.Manifest
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Notification permission (Android 13+)
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Duration input: number + unit
    var amountText by remember {
        val ms = Prefs.getIntervalMillis(context)
        val minutes = ms / 60_000
        mutableStateOf(if (minutes >= 60 && minutes % 60 == 0L) (minutes / 60).toString() else minutes.toString())
    }
    var isHours by remember {
        val minutes = Prefs.getIntervalMillis(context) / 60_000
        mutableStateOf(minutes >= 60 && minutes % 60 == 0L)
    }

    var vibration by remember { mutableStateOf(Prefs.isVibrationEnabled(context)) }
    var toneName by remember { mutableStateOf(toneDisplayName(context, Prefs.getToneUri(context))) }
    var status by remember { mutableStateOf(Prefs.getStatus(context)) }
    var remainingMs by remember { mutableLongStateOf(0L) }
    var message by remember { mutableStateOf<String?>(null) }

    // Tone picker: custom audio file from storage
    val toneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }
            Prefs.setToneUri(context, uri.toString())
            toneName = toneDisplayName(context, uri.toString())
        }
    }

    // Live refresh of remaining time and status
    LaunchedEffect(Unit) {
        while (true) {
            status = Prefs.getStatus(context)
            val next = Prefs.getNextFireAt(context)
            remainingMs = if (next <= 0L) 0L else (next - System.currentTimeMillis()).coerceAtLeast(0L)
            kotlinx.coroutines.delay(500)
        }
    }

    // Preview the tone for a few seconds
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    fun stopPreview() {
        previewPlayer?.let {
            try { if (it.isPlaying) it.stop() } catch (_: IllegalStateException) {}
            it.release()
        }
        previewPlayer = null
    }
    DisposableEffect(Unit) { onDispose { stopPreview(); handler.removeCallbacksAndMessages(null) } }

    fun intervalMillis(): Long? {
        val amount = amountText.toLongOrNull() ?: return null
        if (amount <= 0L) return null
        return if (isHours) amount * 3_600_000L else amount * 60_000L
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("5 MIN", fontSize = 40.sp, fontWeight = FontWeight.Bold)

        Text(
            text = formatTime(remainingMs),
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        AssistChip(
            onClick = {},
            label = { Text("الحالة: ${statusLabel(status)}", fontSize = 18.sp) }
        )

        HorizontalDivider()

        Text("مدة التكرار", fontSize = 20.sp)

        OutlinedTextField(
            value = amountText,
            onValueChange = { new -> amountText = new.filter { it.isDigit() }.take(6) },
            label = { Text("القيمة") },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 28.sp, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterChip(
                selected = !isHours,
                onClick = { isHours = false },
                label = { Text("دقائق", fontSize = 18.sp) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = isHours,
                onClick = { isHours = true },
                label = { Text("ساعات", fontSize = 18.sp) },
                modifier = Modifier.weight(1f)
            )
        }

        Button(
            onClick = {
                val ms = intervalMillis()
                if (ms == null) {
                    message = "أدخل مدة صحيحة أكبر من صفر"
                    return@Button
                }
                Prefs.setIntervalMillis(context, ms)
                val current = Prefs.getStatus(context)
                // Apply during operation: restart the countdown only if it is waiting
                if (current == AlarmService.STATUS_ACTIVE) {
                    AlarmController.scheduleMain(context, ms)
                } else if (current == AlarmService.STATUS_STOPPED) {
                    AlarmController.scheduleMain(context, ms)
                    Prefs.setStatus(context, AlarmService.STATUS_ACTIVE)
                    startServiceCompat(context, null)
                }
                // While ringing or snoozing, the new value applies to the next cycle
                message = "تم تطبيق المدة"
            },
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            Text("بدء / تطبيق", fontSize = 22.sp)
        }

        HorizontalDivider()

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = vibration, onCheckedChange = {
                vibration = it
                Prefs.setVibrationEnabled(context, it)
            })
            Spacer(Modifier.width(12.dp))
            Text("الاهتزاز", fontSize = 18.sp)
        }

        Text("النغمة: $toneName", fontSize = 16.sp, textAlign = TextAlign.Center)

        OutlinedButton(
            onClick = { toneLauncher.launch(arrayOf("audio/*")) },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("اختيار النغمة", fontSize = 18.sp)
        }

        OutlinedButton(
            onClick = {
                stopPreview()
                val uri = Prefs.getToneUri(context)?.let { Uri.parse(it) }
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                try {
                    previewPlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        setDataSource(context, uri)
                        prepare()
                        start()
                    }
                    handler.postDelayed({ stopPreview() }, 4000L)
                } catch (_: Exception) {
                    message = "تعذّر تشغيل النغمة"
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("معاينة النغمة", fontSize = 18.sp)
        }

        Button(
            onClick = {
                context.startService(AlarmService.intent(context, AlarmService.ACTION_FINAL_STOP))
                status = AlarmService.STATUS_STOPPED
                message = "تم الإيقاف النهائي"
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            Text("إيقاف نهائي", fontSize = 22.sp)
        }

        message?.let {
            Text(it, fontSize = 16.sp, textAlign = TextAlign.Center)
        }
    }
}

private fun startServiceCompat(context: android.content.Context, action: String?) {
    val intent = if (action != null) {
        AlarmService.intent(context, action)
    } else {
        Intent(context, AlarmService::class.java)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

private fun statusLabel(status: String): String = when (status) {
    AlarmService.STATUS_RINGING -> "يرن الآن"
    AlarmService.STATUS_SNOOZING -> "غفوة"
    AlarmService.STATUS_ACTIVE -> "نشط"
    else -> "متوقف"
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ENGLISH, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ENGLISH, "%02d:%02d", minutes, seconds)
    }
}

private fun toneDisplayName(context: android.content.Context, uriString: String?): String {
    if (uriString == null) return "النغمة الافتراضية للمنبه"
    val uri = Uri.parse(uriString)
    val ringtone = RingtoneManager.getRingtone(context, uri)
    return ringtone?.getTitle(context) ?: "ملف مخصص"
}
