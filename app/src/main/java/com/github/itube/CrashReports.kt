package com.github.itube

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/** Saves the last uncaught app crash and offers its report on the next launch. */
object CrashReports {
    private const val REPORT_FILE = "itube_last_crash.txt"
    private const val MAX_LOG_LINES = 1_200
    private const val MAX_LOG_CHARS = 160_000

    var pendingReport by mutableStateOf<String?>(null)
        private set

    @Volatile private var installed = false
    @Volatile private var reportFile: File? = null

    fun install(context: Context) {
        synchronized(this) {
            if (installed) return
            installed = true
            val app = context.applicationContext
            val file = File(app.filesDir, REPORT_FILE)
            reportFile = file
            pendingReport = runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull()

            val previous = Thread.getDefaultUncaughtExceptionHandler()
            val handler = Thread.UncaughtExceptionHandler { thread, throwable ->
                runCatching { persist(file, createReport(app, thread, throwable)) }
                if (previous != null) {
                    previous.uncaughtException(thread, throwable)
                } else {
                    Process.killProcess(Process.myPid())
                    exitProcess(10)
                }
            }
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }
    }

    fun dismiss() {
        pendingReport = null
        reportFile?.delete()
    }

    private fun createReport(context: Context, thread: Thread, throwable: Throwable): String {
        val packageInfo = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val version = packageInfo?.versionName ?: BuildConfig.VERSION_NAME
        val stack = StringWriter().also { writer -> throwable.printStackTrace(PrintWriter(writer)) }.toString()
        val logs = captureAppLogcat(Process.myPid())
        return buildString {
            appendLine("iTube crash report")
            appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())}")
            appendLine("App: $version (${BuildConfig.VERSION_CODE})")
            appendLine("Package: ${context.packageName}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Process: ${Process.myPid()}")
            appendLine("Thread: ${thread.name} (${thread.id})")
            appendLine()
            appendLine("=== Exception ===")
            appendLine(stack)
            appendLine("=== Recent app logcat (up to $MAX_LOG_LINES lines) ===")
            appendLine(logs.ifBlank { "No app logcat output was available on this device." })
        }
    }

    private fun captureAppLogcat(pid: Int): String = runCatching {
        val process = ProcessBuilder(
            "/system/bin/logcat", "-d", "-v", "threadtime", "--pid=$pid", "-t", MAX_LOG_LINES.toString(),
        ).redirectErrorStream(true).start()
        val output = StringBuilder()
        process.inputStream.bufferedReader().use { reader ->
            var count = 0
            while (count < MAX_LOG_LINES) {
                val line = reader.readLine() ?: break
                if (output.length + line.length + 1 <= MAX_LOG_CHARS) output.appendLine(line)
                count++
            }
        }
        if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
        val text = output.toString()
        if (text.contains("Permission denied", ignoreCase = true) || text.contains("not permitted", ignoreCase = true)) ""
        else text
    }.getOrDefault("")

    private fun persist(file: File, report: String) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(report)
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
    }
}

@Composable
fun CrashReportDialog(report: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f),
            shape = RoundedCornerShape(28.dp),
            color = colors.surface,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("iTube closed unexpectedly", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(
                    "The crash and recent app logs were saved. Copy them all or share the report.",
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                SelectionContainer {
                    Text(
                        report,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 360.dp)
                            .background(colors.surfaceContainerLow, RoundedCornerShape(16.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        color = colors.onSurface,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("iTube crash logs", report))
                        Toast.makeText(context, "Crash logs copied", Toast.LENGTH_SHORT).show()
                    }) { Text("Copy all logs") }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "iTube crash logs")
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        runCatching { context.startActivity(Intent.createChooser(share, "Share crash logs")) }
                            .onFailure { Toast.makeText(context, "Couldn't open the share menu", Toast.LENGTH_SHORT).show() }
                    }) { Text("Share") }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Dismiss") }
            }
        }
    }
}
