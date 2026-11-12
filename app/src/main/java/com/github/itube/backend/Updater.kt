package com.github.itube

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import coil.compose.AsyncImage
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Looks for newer d0x-dev/iTube GitHub releases on each cold app launch. A newer release opens the
 * What's New sheet automatically, and its Download button opens that release's signed APK URL.
 *
 * What's new (issue #30): the button opens a sheet with the notes of every release newer than this
 * build (scripts/publish-apk.sh posts each version's CHANGELOG.md section as its release notes), and
 * the sheet's own Update button installs.
 */
object AppUpdate {
    private const val TAG = "iTubeUpdate"
    /** Newest first; enough to cover the notes of every release a phone may have skipped. */
    private const val RELEASES_URL = "https://api.github.com/repos/d0x-dev/iTube/releases?per_page=30"
    private const val APK_ASSET = "iTube.apk"
    private const val KEY_RELEASE = "release"
    private const val KEY_RELEASE_HISTORY = "release_history"
    /** The version an install was started for: the next start of that version posts "updated". */
    private const val KEY_INSTALLING = "installing"
    /** The install asked the user; Android's own dialog then offers Open, so no notification. */
    private const val KEY_PROMPTED = "prompted"
    private const val CHANNEL = "updates"
    internal const val ACTION_RESULT = "com.github.itube.UPDATE_RESULT"

    data class Release(
        val version: String,
        val apkUrl: String,
        val size: Long,
        val sha256: String?,
        val releaseUrl: String = apkUrl,
        /** Newest first: this release's notes and those of the releases between it and this build. */
        val notes: List<ReleaseNotes> = emptyList(),
    )

    /** One release's full Markdown body as published on GitHub. */
    data class ReleaseNotes(val version: String, val body: String) {
        val blocks by lazy { parseMarkdown(body) }
    }

    enum class MarkdownKind { Heading, Paragraph, Bullet, Ordered, Quote, Image, Code, Divider }
    data class MarkdownBlock(val kind: MarkdownKind, val text: String = "", val url: String? = null, val level: Int = 0)
    internal data class ReleaseIndex(val latest: Release?, val history: List<ReleaseNotes>)

    enum class Phase { Ready, Downloading, Installing, Failed }

    /** The newer release, or null when this build is the newest (then nothing is shown). */
    var available by mutableStateOf<Release?>(null)
        private set
    /** Cached release history, including notes for the version currently installed. */
    var releaseHistory by mutableStateOf<List<ReleaseNotes>>(emptyList())
        private set
    var phase by mutableStateOf(Phase.Ready)
        private set
    /** Exposed to Settings so a manual check has clear feedback. */
    var checkingNow by mutableStateOf(false)
        private set
    var lastCheckMessage by mutableStateOf<String?>(null)
        private set
    /** Download progress 0…1 (written from the download thread). */
    var progress by mutableFloatStateOf(0f)
        private set
    /** The button pops in only the first time it shows in this process. */
    internal var announced = false

    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences
    private val scope = MainScope()
    private var checking = false
    /** Prevent repeat network requests when the same activity resumes during this app launch. */
    private var checkedThisProcess = false
    private var download: Deferred<File?>? = null
    private var downloadVersion: String? = null
    /** Sent to "Install unknown apps"; the install carries on when the window comes back. */
    private var awaitingPermission = false

    fun init(context: Context) {
        app = context.applicationContext
        prefs = app.getSharedPreferences("update", Context.MODE_PRIVATE)
        releaseHistory = savedHistory()
        // Shown straight away from the last check; the daily check corrects it.
        available = savedRelease()?.takeIf { isNewer(it.version) }
        // An installed update leaves its APK behind; keep only the one still to install.
        val keep = available?.let { apkFile(it).name }
        dir().listFiles()?.forEach { if (it.name != keep) it.delete() }
    }

    /** Called when the app enters foreground; check once per process start. */
    fun checkIfDue() {
        if (checking || checkedThisProcess) return
        checkedThisProcess = true
        check(force = false)
    }

    /** Checks GitHub immediately from Settings, regardless of the daily launch check. */
    fun checkNow() {
        if (checking) return
        check(force = true)
    }

    private fun check(force: Boolean) {
        if (checking) return
        checking = true
        checkingNow = true
        if (force) lastCheckMessage = null
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { fetchReleases() } }
            checking = false
            checkingNow = false
            result.onFailure {
                Log.w(TAG, "update check failed (${it.message})")
                lastCheckMessage = "Couldn't check for updates. Check your connection and try again."
                return@launch
            }
            val index = result.getOrNull() ?: return@launch
            val latest = index.latest
            prefs.edit()
                .apply {
                    if (latest != null) putString(KEY_RELEASE, latest.toJson()) else remove(KEY_RELEASE)
                    putString(KEY_RELEASE_HISTORY, JSONArray(index.history.map {
                        JSONObject().put("version", it.version).put("body", it.body)
                    }).toString())
                }
                .apply()
            releaseHistory = index.history
            val newer = latest?.takeIf { isNewer(it.version) }
            Log.i(TAG, "update check: latest ${latest?.version ?: "none"}, this ${BuildConfig.VERSION_NAME}" +
                if (newer != null) " → update available" else "")
            if (phase == Phase.Downloading || phase == Phase.Installing) return@launch
            if (newer != available) { available = newer; phase = Phase.Ready }
            lastCheckMessage = if (newer == null) "iTube is up to date." else "iTube ${newer.version} is available."
        }
    }

    /** Opens the GitHub release page directly in the browser so the user can download their architecture APK. */
    fun openDownload(context: Context) {
        val release = available ?: return
        val targetUrl = release.releaseUrl.ifBlank { release.apkUrl }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "update: couldn't open release link", it) }
    }

    /** The What's new sheet's Update button redirects directly to the GitHub release page. */
    fun install(context: Context) {
        openDownload(context)
    }

    /** Back from "Install unknown apps": install if it was allowed, else the button waits again. */
    fun onWindowResumed(context: Context) {
        if (!awaitingPermission) return
        awaitingPermission = false
        if (app.packageManager.canRequestPackageInstalls()) install(context)
    }

    /** PackageInstaller's answer to [commit] (via [UpdateReceiver]). */
    internal fun onInstallResult(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm == null) { phase = Phase.Failed; return }
                prefs.edit().putBoolean(KEY_PROMPTED, true).apply()
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // this process is replaced; onUpdated runs in the new one
            PackageInstaller.STATUS_FAILURE_ABORTED -> { // the user said no
                prefs.edit().remove(KEY_INSTALLING).remove(KEY_PROMPTED).apply()
                phase = Phase.Ready
            }
            else -> {
                Log.w(TAG, "update: install failed ($status: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)})")
                prefs.edit().remove(KEY_INSTALLING).remove(KEY_PROMPTED).apply()
                phase = Phase.Failed
            }
        }
    }

    /** The new version's first start after [commit] installed it. */
    internal fun onUpdated(context: Context) {
        val version = prefs.getString(KEY_INSTALLING, null) ?: return
        val prompted = prefs.getBoolean(KEY_PROMPTED, false)
        prefs.edit().remove(KEY_INSTALLING).remove(KEY_PROMPTED).apply()
        Log.i(TAG, "update: now on ${BuildConfig.VERSION_NAME} (installed $version)")
        // A silent update closed the app with no word; Android's own dialog already offered Open.
        if (prompted || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT))
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("iTube updated to ${BuildConfig.VERSION_NAME}")
            .setContentText("Tap to open")
            .setContentIntent(PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
            .setSilent(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(1, notification) }
            .onFailure { Log.w(TAG, "update: couldn't post the updated notification (${it.message})") }
    }

    private fun fetchReleases(): ReleaseIndex {
        val request = Request.Builder().url(RELEASES_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "iTube/${BuildConfig.VERSION_NAME}")
            .build()
        Net.client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            return parseReleaseIndex(JSONArray(response.body?.string().orEmpty()), BuildConfig.VERSION_NAME)
        }
    }

    /**
     * The releases list → the newest published release with its versioned AirStream APK, carrying the notes of every
     * release newer than [current] up to it (a phone that skipped versions sees all of their changes).
     */
    internal fun parseReleases(json: JSONArray, current: String): Release? = parseReleaseIndex(json, current).latest

    internal fun parseReleaseIndex(json: JSONArray, current: String): ReleaseIndex {
        val published = (0 until json.length()).map { json.getJSONObject(it) }
            .filter { !it.optBoolean("draft") && !it.optBoolean("prerelease") }
        val parsed = published.mapNotNull { parseRelease(it) }
        val history = parsed
            .mapNotNull { it.notes.firstOrNull() }
            .sortedWith { a, b -> compareVersions(b.version, a.version) }
        val latest = parsed.maxWithOrNull { a, b -> compareVersions(a.version, b.version) }
            ?: return ReleaseIndex(null, history)
        val notes = history
            .filter { compareVersions(it.version, current) > 0 && compareVersions(it.version, latest.version) <= 0 }
            .sortedWith { a, b -> compareVersions(b.version, a.version) }
        return ReleaseIndex(latest.copy(notes = notes), history)
    }

    /** Latest release JSON → version and release page URL. */
    internal fun parseRelease(json: JSONObject): Release? {
        val tag = json.optString("tag_name").ifBlank { return null }
        val version = tag.removePrefix("v").substringBefore('-')
        val htmlUrl = json.optString("html_url").ifBlank { "https://github.com/d0x-dev/iTube/releases/tag/$tag" }
        val assets = json.optJSONArray("assets") ?: JSONArray()
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk") }
        val directUrl = apk?.optString("browser_download_url")?.ifBlank { null }
            ?: "https://github.com/d0x-dev/iTube/releases/download/${Uri.encode(tag)}/${Uri.encode(releaseAssetName(version))}"
        return Release(
            version = version,
            apkUrl = directUrl,
            size = apk?.optLong("size") ?: 0L,
            sha256 = apk?.optString("digest")?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:"),
            releaseUrl = htmlUrl,
            notes = listOf(ReleaseNotes(version, json.optString("body"))),
        )
    }

    internal fun releaseAssetName(version: String) = "iTube_v${version}_signed.apk"

    /** Parse common GitHub Markdown blocks while retaining raw inline Markdown for styling. */
    internal fun parseMarkdown(body: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val paragraph = mutableListOf<String>()
        val code = mutableListOf<String>()
        var inCode = false
        fun flushParagraph() {
            val value = paragraph.joinToString(" ").trim()
            if (value.isNotEmpty()) blocks += MarkdownBlock(MarkdownKind.Paragraph, value)
            paragraph.clear()
        }
        for (raw in body.replace("\r\n", "\n").lines()) {
            val line = raw.trim()
            if (line.startsWith("```") || line.startsWith("~~~")) {
                if (inCode) {
                    blocks += MarkdownBlock(MarkdownKind.Code, code.joinToString("\n"))
                    code.clear()
                    inCode = false
                } else {
                    flushParagraph()
                    inCode = true
                }
                continue
            }
            if (inCode) { code += raw; continue }
            if (line.isEmpty()) { flushParagraph(); continue }
            val heading = Regex("^(#{1,6})\\s+(.+)$").matchEntire(line)
            val image = Regex("""^!\[([^]]*)]\((https?://[^ )]+)(?:\s+"[^"]*")?\)$""").matchEntire(line)
            val htmlImage = Regex("""(?i)^<img\b[^>]*src="([^"]+)"[^>]*>$""").matchEntire(line)
            val bullet = Regex("^\\s*[-*+]\\s+(.+)$").matchEntire(raw)
            val ordered = Regex("^\\s*(\\d+)[.)]\\s+(.+)$").matchEntire(raw)
            when {
                line == "---" || line == "***" || line == "___" -> { flushParagraph(); blocks += MarkdownBlock(MarkdownKind.Divider) }
                heading != null -> { flushParagraph(); blocks += MarkdownBlock(MarkdownKind.Heading, heading.groupValues[2], level = heading.groupValues[1].length) }
                image != null -> { flushParagraph(); blocks += MarkdownBlock(MarkdownKind.Image, image.groupValues[1], image.groupValues[2]) }
                htmlImage != null -> { flushParagraph(); blocks += MarkdownBlock(MarkdownKind.Image, "", htmlImage.groupValues[1]) }
                bullet != null -> { flushParagraph(); blocks += MarkdownBlock(MarkdownKind.Bullet, bullet.groupValues[1]) }
                ordered != null -> { flushParagraph(); blocks += MarkdownBlock(MarkdownKind.Ordered, "${ordered.groupValues[1]}. ${ordered.groupValues[2]}") }
                line.startsWith("> ") -> { flushParagraph(); blocks += MarkdownBlock(MarkdownKind.Quote, line.removePrefix("> ")) }
                else -> paragraph += line
            }
        }
        if (inCode) blocks += MarkdownBlock(MarkdownKind.Code, code.joinToString("\n"))
        flushParagraph()
        return blocks
    }

    /** "0.5.55" vs "0.5.54" → positive; missing parts count as 0. */
    internal fun compareVersions(a: String, b: String): Int {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    private fun isNewer(version: String) = compareVersions(version, BuildConfig.VERSION_NAME) > 0

    private fun metered() = app.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered != false

    /** The APK on disk, downloading it once; a tap during a Wi-Fi prefetch waits for that one. */
    private fun apkFor(release: Release): Deferred<File?> {
        download?.takeIf { it.isActive && downloadVersion == release.version }?.let { return it }
        downloadVersion = release.version
        return scope.async(Dispatchers.IO) {
            runCatching { downloadApk(release) }
                .onFailure { Log.w(TAG, "update: download of ${release.version} failed (${it.message})") }
                .getOrNull()
        }.also { download = it }
    }

    private fun downloadApk(release: Release): File {
        val file = apkFile(release)
        if (file.length() == release.size && (release.sha256 == null || file.sha256() == release.sha256)) {
            progress = 1f
            return file
        }
        progress = 0f
        val part = File(dir(), file.name + ".part")
        val request = Request.Builder().url(release.apkUrl).header("User-Agent", "iTube/${BuildConfig.VERSION_NAME}").build()
        Net.client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val body = checkNotNull(response.body) { "no body" }
            val total = release.size.takeIf { it > 0 } ?: body.contentLength()
            val digest = MessageDigest.getInstance("SHA-256")
            var read = 0L
            part.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        read += n
                        if (total > 0) progress = (read.toFloat() / total).coerceIn(0f, 1f)
                    }
                }
            }
            check(release.size <= 0 || read == release.size) { "got $read bytes, expected ${release.size}" }
            release.sha256?.let { check(digest.digest().hex() == it) { "sha256 mismatch" } }
        }
        check(part.renameTo(file)) { "couldn't rename ${part.name}" }
        Log.i(TAG, "update: downloaded ${release.version} (${file.length()} bytes)")
        return file
    }

    private fun commit(apk: File, release: Release) {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            setSize(apk.length())
            // Once AirStream installed itself, Android 12+ lets it update without asking.
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        val session = installer.openSession(id)
        try {
            session.openWrite(APK_ASSET, 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            prefs.edit().putString(KEY_INSTALLING, release.version).putBoolean(KEY_PROMPTED, false).commit()
            val result = Intent(app, UpdateReceiver::class.java).setAction(ACTION_RESULT)
            // Mutable: PackageInstaller fills in the status extras.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            Log.i(TAG, "update: installing ${release.version}")
            session.commit(PendingIntent.getBroadcast(app, id, result, flags).intentSender)
            session.close()
        } catch (e: Exception) {
            session.abandon()
            throw e
        }
    }

    private fun dir() = File(app.cacheDir, "update").apply { mkdirs() }
    private fun apkFile(release: Release) = File(dir(), "iTube-${release.version}.apk")

    private fun savedRelease(): Release? = prefs.getString(KEY_RELEASE, null)?.let {
        runCatching {
            val o = JSONObject(it)
            val notes = o.optJSONArray("notes") ?: JSONArray()
            Release(
                version = o.getString("version"),
                apkUrl = o.getString("url"),
                size = o.getLong("size"),
                sha256 = o.optString("sha256").ifBlank { null },
                releaseUrl = o.optString("releaseUrl").ifBlank { o.getString("url") },
                notes = (0 until notes.length()).map { notes.getJSONObject(it).let { n -> ReleaseNotes(n.getString("version"), n.getString("body")) } },
            )
        }.getOrNull()
    }

    private fun savedHistory(): List<ReleaseNotes> = prefs.getString(KEY_RELEASE_HISTORY, null)?.let { raw ->
        runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getJSONObject(it).let { item ->
                ReleaseNotes(item.getString("version"), item.optString("body"))
            } }.sortedWith { a, b -> compareVersions(b.version, a.version) }
        }.getOrNull()
    }.orEmpty()

    fun releaseNotesFor(version: String): ReleaseNotes? = releaseHistory.firstOrNull { it.version == version }

    private fun Release.toJson() =
        JSONObject().put("version", version).put("url", apkUrl).put("size", size).put("sha256", sha256 ?: "")
            .put("releaseUrl", releaseUrl)
            .put("notes", JSONArray(notes.map { JSONObject().put("version", it.version).put("body", it.body) }))
            .toString()

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().hex()
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}

/** PackageInstaller results, and the first start of a freshly installed version. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AppUpdate.ACTION_RESULT -> AppUpdate.onInstallResult(context, intent)
            Intent.ACTION_MY_PACKAGE_REPLACED -> AppUpdate.onUpdated(context)
        }
    }
}

/**
 * The header's Update pill, beside the profile picture; absent when there's nothing newer. A tap opens
 * [WhatsNewSheet]. While downloading it fills left to right with the percentage; "Retry" after a failure.
 */
@Composable
fun UpdateAction() {
    val release = AppUpdate.available ?: return
    val c = MaterialTheme.colorScheme
    val phase = AppUpdate.phase
    var sheet by remember { mutableStateOf(false) }
    val appear = remember { Animatable(if (AppUpdate.announced) 1f else 0f) }
    LaunchedEffect(Unit) {
        AppUpdate.announced = true
        appear.animateTo(1f, tween(420, easing = Motion.Pop))
    }
    Box(
        modifier = Modifier
            .graphicsLayer {
                val v = appear.value
                alpha = v.coerceIn(0f, 1f)
                scaleX = 0.6f + 0.4f * v
                scaleY = 0.6f + 0.4f * v
            }
            .pressable { sheet = true }
            .height(44.dp)
            .widthIn(min = 104.dp)
            .clip(CircleShape)
            .background(c.primary)
            .drawBehind {
                if (phase == AppUpdate.Phase.Downloading) {
                    drawRect(c.onPrimary.copy(alpha = 0.22f), size = Size(size.width * AppUpdate.progress, size.height))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                if (phase == AppUpdate.Phase.Failed) Icons.Rounded.Refresh else Icons.Rounded.SystemUpdateAlt,
                contentDescription = null,
                tint = c.onPrimary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                when (phase) {
                    AppUpdate.Phase.Ready -> "Update"
                    AppUpdate.Phase.Downloading -> "${(AppUpdate.progress * 100).toInt()}%"
                    AppUpdate.Phase.Installing -> "Installing"
                    AppUpdate.Phase.Failed -> "Retry"
                },
                color = c.onPrimary,
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                maxLines = 1,
            )
        }
    }
    if (sheet) UpdateNotesSheet(release, onDismiss = { sheet = false })
}

/**
 * What's new (issue #30): the notes of every release since this build, newest first (each under its
 * version when there are several), and the Update button under them, pinned while the notes scroll.
 * The button fills with the download and stays here through Installing; a silent update just closes
 * the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateNotesSheet(release: AppUpdate.Release, onDismiss: () -> Unit) {
    ReleaseNotesSheet(
        title = "What's new",
        notes = release.notes,
        downloadRelease = release,
        onDismiss = onDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstalledVersionNotesSheet(notes: AppUpdate.ReleaseNotes, onDismiss: () -> Unit) {
    ReleaseNotesSheet(
        title = "Release notes · iTube ${notes.version}",
        notes = listOf(notes),
        downloadRelease = null,
        onDismiss = onDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReleaseNotesSheet(
    title: String,
    notes: List<AppUpdate.ReleaseNotes>,
    downloadRelease: AppUpdate.Release?,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val several = notes.size > 1
    val hasContent = notes.any { it.blocks.isNotEmpty() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Text(
            title,
            fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        val meta = listOfNotNull(
            downloadRelease?.version?.let { "iTube $it" },
            downloadRelease?.size?.takeIf { it > 0 }?.let { "%.1f MB".format(Locale.US, it / 1_048_576.0) },
        ).joinDots()
        if (meta.isNotEmpty()) Text(
            meta,
            color = c.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 14.dp),
        )
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (!hasContent) {
                Text("No notes were published with this version.", color = c.onSurfaceVariant, fontSize = 15.sp, lineHeight = 21.sp)
            }
            notes.forEach { releaseNotes ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (several) {
                        Text(releaseNotes.version, color = c.primary, fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                    MarkdownReleaseBody(releaseNotes.blocks)
                }
            }
        }
        if (downloadRelease != null) {
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .pressable { AppUpdate.openDownload(context) }
                    .height(54.dp)
                    .clip(CircleShape)
                    .background(c.primary),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = null,
                        tint = c.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        "Download on GitHub",
                        color = c.onPrimary,
                        fontFamily = RoundedHeavy,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MarkdownReleaseBody(blocks: List<AppUpdate.MarkdownBlock>) {
    val c = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { block ->
            when (block.kind) {
                AppUpdate.MarkdownKind.Heading -> MarkdownInlineText(
                    block.text,
                    fontSize = when (block.level) { 1 -> 22.sp; 2 -> 20.sp; 3 -> 18.sp; else -> 16.sp },
                    fontWeight = FontWeight.Bold,
                    lineHeight = 27.sp,
                )
                AppUpdate.MarkdownKind.Paragraph -> MarkdownInlineText(block.text, fontSize = 15.sp, lineHeight = 22.sp)
                AppUpdate.MarkdownKind.Bullet -> Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 9.dp, end = 11.dp).size(6.dp).clip(CircleShape).background(c.primary))
                    MarkdownInlineText(block.text, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.weight(1f))
                }
                AppUpdate.MarkdownKind.Ordered -> MarkdownInlineText(block.text, fontSize = 15.sp, lineHeight = 22.sp)
                AppUpdate.MarkdownKind.Quote -> Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(end = 10.dp).size(width = 3.dp, height = 40.dp).clip(CircleShape).background(c.primary.copy(alpha = 0.7f)))
                    MarkdownInlineText(block.text, fontSize = 15.sp, lineHeight = 22.sp, italic = true, modifier = Modifier.weight(1f))
                }
                AppUpdate.MarkdownKind.Image -> {
                    block.url?.let { imageUrl ->
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = block.text.ifBlank { "Release image" },
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .pressable { uri.openUri(imageUrl) },
                        )
                    }
                    if (block.text.isNotBlank()) Text(block.text, color = c.onSurfaceVariant, fontSize = 12.sp)
                }
                AppUpdate.MarkdownKind.Code -> Text(
                    block.text,
                    color = c.onSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(c.surfaceContainerHigh).padding(12.dp),
                )
                AppUpdate.MarkdownKind.Divider -> Box(Modifier.fillMaxWidth().height(1.dp).background(c.outlineVariant))
            }
        }
    }
}

@Composable
private fun MarkdownInlineText(
    markdown: String,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight? = null,
    italic: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    val pattern = remember {
        Regex("""\[([^\]]+)]\((https?://[^ )]+)(?:\s+"[^"]*")?\)|\*\*(.+?)\*\*|__(.+?)__|\*(.+?)\*|_(.+?)_|`([^`]+)`|~~(.+?)~~""")
    }
    val annotated = buildAnnotatedString {
        var cursor = 0
        for (match in pattern.findAll(markdown)) {
            append(markdown.substring(cursor, match.range.first))
            val groups = match.groups
            when {
                groups[1] != null -> {
                    pushStringAnnotation("url", groups[2]!!.value)
                    withStyle(SpanStyle(color = c.primary, textDecoration = TextDecoration.Underline)) { append(groups[1]!!.value) }
                    pop()
                }
                groups[3] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(groups[3]!!.value) }
                groups[4] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(groups[4]!!.value) }
                groups[5] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(groups[5]!!.value) }
                groups[6] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(groups[6]!!.value) }
                groups[7] != null -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = c.surfaceContainerHigh)) { append(groups[7]!!.value) }
                groups[8] != null -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(groups[8]!!.value) }
            }
            cursor = match.range.last + 1
        }
        if (cursor < markdown.length) append(markdown.substring(cursor))
    }
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = TextStyle(color = c.onSurface, fontSize = fontSize, lineHeight = lineHeight, fontWeight = fontWeight),
        onClick = { offset -> annotated.getStringAnnotations("url", offset, offset).firstOrNull()?.let { uri.openUri(it.item) } },
    )
}
