package com.github.itube

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.Call

sealed class DownloadOption {
    abstract val label: String
    abstract val formatLabel: String
    abstract val approxSize: String?

    data class Video(
        val height: Int,
        val fps: Int,
        override val label: String,
        override val formatLabel: String,
        override val approxSize: String?,
        val videoStreamUrl: String,
        val audioStreamUrl: String?,
        val isMuxed: Boolean,
        val isWebm: Boolean = false,
    ) : DownloadOption()

    data class Audio(
        val bitrate: Int,
        override val label: String,
        override val formatLabel: String,
        override val approxSize: String?,
        val streamUrl: String,
    ) : DownloadOption()
}

object VideoDownloader {
    private const val TAG = "VideoDownloader"
    private const val CHANNEL_ID = "downloads_channel"
    private const val CHANNEL_NAME = "Downloads"
    private val nextId = AtomicInteger(1000)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val jobs = ConcurrentHashMap<Int, Job>()

    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time download progress and completion"
                setShowBadge(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun extractOptions(info: StreamInfo): List<DownloadOption> {
        val durationSec = info.duration.takeIf { it > 0 } ?: 0L
        val options = mutableListOf<DownloadOption>()

        // 1. Video Options
        val muxedStreams = info.videoStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.height > 0 }
            .groupBy { it.height }

        val videoOnlyStreams = info.videoOnlyStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.height > 0 }
            .groupBy { it.height }

        val bestM4a = info.audioStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .sortedWith(
                compareBy<org.schabi.newpipe.extractor.stream.AudioStream> {
                    if (it.format == org.schabi.newpipe.extractor.MediaFormat.M4A) 0 else 1
                }.thenByDescending { it.averageBitrate }
            ).firstOrNull()

        val bestOpus = info.audioStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && (it.format == org.schabi.newpipe.extractor.MediaFormat.WEBMA_OPUS || it.codec?.contains("opus", ignoreCase = true) == true) }
            .maxByOrNull { it.averageBitrate }
            ?: bestM4a

        val allHeights = (muxedStreams.keys + videoOnlyStreams.keys).sortedDescending()

        for (height in allHeights) {
            val muxed = muxedStreams[height]?.maxByOrNull { it.fps }
            if (muxed != null) {
                val sizeStr = if (durationSec > 0 && muxed.bitrate > 0) {
                    formatBytes((muxed.bitrate.toLong() * durationSec) / 8)
                } else null

                val fpsStr = if (muxed.fps > 30) " · ${muxed.fps}fps" else ""
                options.add(
                    DownloadOption.Video(
                        height = height,
                        fps = muxed.fps,
                        label = "${height}p",
                        formatLabel = "MP4$fpsStr",
                        approxSize = sizeStr,
                        videoStreamUrl = muxed.content,
                        audioStreamUrl = null,
                        isMuxed = true,
                        isWebm = false,
                    )
                )
            } else {
                val list = videoOnlyStreams[height].orEmpty()
                val avc = list.filter { it.codec?.startsWith("avc", ignoreCase = true) == true }.maxByOrNull { it.fps * 1000 + it.bitrate }
                val vp9 = list.filter { it.format == org.schabi.newpipe.extractor.MediaFormat.WEBM || it.codec?.contains("vp9", ignoreCase = true) == true }.maxByOrNull { it.fps * 1000 + it.bitrate }
                val chosen = avc ?: vp9 ?: list.maxByOrNull { it.fps * 1000 + it.bitrate }

                if (chosen != null) {
                    val isWebm = chosen.format == org.schabi.newpipe.extractor.MediaFormat.WEBM || chosen.codec?.contains("vp9", ignoreCase = true) == true
                    val selectedAudio = if (isWebm) bestOpus else bestM4a
                    val audioBitrate = selectedAudio?.averageBitrate ?: 128
                    val totalBitrate = chosen.bitrate + (audioBitrate * 1000)
                    val sizeStr = if (durationSec > 0 && totalBitrate > 0) {
                        formatBytes((totalBitrate.toLong() * durationSec) / 8)
                    } else null

                    val fpsStr = if (chosen.fps > 30) " · ${chosen.fps}fps" else ""
                    val containerTag = if (isWebm) "WebM" else "MP4"
                    options.add(
                        DownloadOption.Video(
                            height = height,
                            fps = chosen.fps,
                            label = "${height}p",
                            formatLabel = "$containerTag$fpsStr (HD)",
                            approxSize = sizeStr,
                            videoStreamUrl = chosen.content,
                            audioStreamUrl = selectedAudio?.content,
                            isMuxed = false,
                            isWebm = isWebm,
                        )
                    )
                }
            }
        }

        // 2. Audio Options
        val audios = info.audioStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .sortedByDescending { it.averageBitrate }

        val m4aAudios = audios.filter { it.format == org.schabi.newpipe.extractor.MediaFormat.M4A }
        val finalAudios = if (m4aAudios.isNotEmpty()) m4aAudios else audios

        finalAudios.forEach { audio ->
            val kbps = audio.averageBitrate
            val sizeStr = if (durationSec > 0 && kbps > 0) {
                formatBytes((kbps.toLong() * 1000L * durationSec) / 8)
            } else null

            val titleLabel = when {
                kbps >= 160 -> "High Quality Audio"
                kbps >= 120 -> "Standard Audio"
                else -> "Audio (${kbps} kbps)"
            }

            options.add(
                DownloadOption.Audio(
                    bitrate = kbps,
                    label = titleLabel,
                    formatLabel = "M4A · ${kbps} kbps",
                    approxSize = sizeStr,
                    streamUrl = audio.content,
                )
            )
        }

        return options
    }

    fun download(context: Context, video: VideoItem, description: String, option: DownloadOption) {
        val appContext = context.applicationContext
        init(appContext)
        val notificationId = nextId.incrementAndGet()
        launchTracked(appContext, notificationId, video, "Preparing download…") {
            performDownload(appContext, notificationId, video, description, option)
        }
    }

    /** Downloads each video in a public album or playlist sequentially at its best available quality. */
    fun downloadCollection(context: Context, collection: PublicCollection) {
        val appContext = context.applicationContext
        init(appContext)
        val notificationId = nextId.incrementAndGet()
        val collectionItem = VideoItem(collection.url, collection.title, collection.subtitle, thumbnail = collection.thumbnail)
        launchTracked(appContext, notificationId, collectionItem, "Loading playlist…") {
            val details = YouTube.publicCollection(collection.url, collection.isAlbum)
            details.videos.forEach { video ->
                currentCoroutineContext().ensureActive()
                DownloadLibrary.updateActive(ActiveDownload(notificationId, video, "Preparing download…"))
                try {
                    val info = StreamCache.getOrLoad(video.id)
                    val best = extractOptions(info)
                        .filterIsInstance<DownloadOption.Video>()
                        .maxWithOrNull(compareBy<DownloadOption.Video> { it.height }.thenBy { it.fps })
                        ?: throw IOException("No downloadable video quality is available")
                    val description = info.description?.let { text ->
                        if (text.type == org.schabi.newpipe.extractor.stream.Description.HTML) {
                            androidx.core.text.HtmlCompat.fromHtml(text.content, androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
                        } else text.content.trim()
                    }.orEmpty()
                    performDownload(appContext, notificationId, video, description, best)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    currentCoroutineContext().ensureActive()
                    Log.e(TAG, "Collection download failed for '${video.title}'", error)
                    showFailedNotification(appContext, notificationId, video.title, error.message ?: "Download error")
                }
            }
        }
    }

    fun cancel(context: Context, notificationId: Int) {
        jobs.remove(notificationId)?.cancel(CancellationException("Download cancelled"))
        NotificationManagerCompat.from(context.applicationContext).cancel(notificationId)
        DownloadLibrary.finishActive(notificationId)
    }

    private fun launchTracked(context: Context, notificationId: Int, item: VideoItem, initialStatus: String, block: suspend () -> Unit) {
        DownloadLibrary.updateActive(ActiveDownload(notificationId, item, initialStatus))
        notifyProgress(context, notificationId, item.title, initialStatus, null)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } catch (cancelled: CancellationException) {
                NotificationManagerCompat.from(context).cancel(notificationId)
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                Log.e(TAG, "Download failed for '${item.title}'", error)
                showFailedNotification(context, notificationId, item.title, error.message ?: "Download error")
            } finally {
                jobs.remove(notificationId)
                DownloadLibrary.finishActive(notificationId)
            }
        }
        jobs[notificationId] = job
        job.start()
    }

    private fun cancelPendingIntent(context: Context, notificationId: Int) = PendingIntent.getBroadcast(
        context,
        notificationId,
        Intent(context, DownloadCancelReceiver::class.java).putExtra(DownloadCancelReceiver.EXTRA_ID, notificationId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun downloadsPendingIntent(context: Context, notificationId: Int) = PendingIntent.getActivity(
        context,
        notificationId + 50_000,
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_DOWNLOADS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun notifyProgress(context: Context, id: Int, title: String, status: String, progress: Int?) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Downloading: $title")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress?.coerceIn(0, 100) ?: 0, progress == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(downloadsPendingIntent(context, id))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent(context, id))
        runCatching { NotificationManagerCompat.from(context).notify(id, builder.build()) }
    }

    private suspend fun performDownload(
        context: Context,
        notificationId: Int,
        video: VideoItem,
        description: String,
        option: DownloadOption
    ) = withContext(Dispatchers.IO) {
        val title = video.title
        val nm = NotificationManagerCompat.from(context)
        val cleanTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Downloading: $title")
            .setContentText("${option.label} · Starting…")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, 0, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(downloadsPendingIntent(context, notificationId))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent(context, notificationId))

        try {
            nm.notify(notificationId, builder.build())
        } catch (_: SecurityException) {}

        var lastUpdate = 0L
        fun updateProgress(currentProgress: Int, text: String) {
            val now = System.currentTimeMillis()
            if (now - lastUpdate >= 300 || currentProgress == 100) {
                lastUpdate = now
                DownloadLibrary.updateActive(ActiveDownload(notificationId, video, text, currentProgress.coerceIn(0, 100)))
                builder.setProgress(100, currentProgress.coerceIn(0, 100), false)
                builder.setContentText(text)
                try {
                    nm.notify(notificationId, builder.build())
                } catch (_: SecurityException) {}
            }
        }

        val cacheDir = context.cacheDir
        when (option) {
            is DownloadOption.Audio -> {
                val tempFile = File(cacheDir, "dl_${System.currentTimeMillis()}_${cleanTitle}.m4a")
                try {
                    downloadFile(option.streamUrl, tempFile) { read, total ->
                        val percent = if (total > 0) ((read * 100) / total).toInt() else 0
                        val text = "${option.label} · $percent% (${formatBytes(read)} / ${formatBytes(total)})"
                        updateProgress(percent, text)
                    }
                    val fileName = "$cleanTitle [Audio].m4a"
                    val savedUri = saveToDownloads(context, tempFile, fileName, "audio/mp4")
                    showCompleteNotification(context, notificationId, title, option.label, savedUri, "audio/mp4")
                } finally {
                    tempFile.delete()
                }
            }
            is DownloadOption.Video -> {
                val isWebm = option.isWebm
                val ext = if (isWebm) "webm" else "mp4"
                val audioExt = if (isWebm) "opus" else "m4a"
                val mime = if (isWebm) "video/webm" else "video/mp4"

                if (option.isMuxed || option.audioStreamUrl == null) {
                    val tempFile = File(cacheDir, "dl_${System.currentTimeMillis()}_${cleanTitle}.$ext")
                    try {
                        downloadFile(option.videoStreamUrl, tempFile) { read, total ->
                            val percent = if (total > 0) ((read * 100) / total).toInt() else 0
                            val text = "${option.label} · $percent% (${formatBytes(read)} / ${formatBytes(total)})"
                            updateProgress(percent, text)
                        }
                        val fileName = "$cleanTitle [${option.label}].$ext"
                        val savedUri = saveToDownloads(context, tempFile, fileName, mime)
                            ?: throw IOException("Couldn't save the video to Downloads")
                        DownloadLibrary.register(context, video, description, savedUri, fileName)
                        showCompleteNotification(context, notificationId, title, option.label, savedUri, mime)
                    } finally {
                        tempFile.delete()
                    }
                } else {
                    // Separate video and audio: download both, then mux
                    val tempVideo = File(cacheDir, "vid_${System.currentTimeMillis()}_$cleanTitle.$ext")
                    val tempAudio = File(cacheDir, "aud_${System.currentTimeMillis()}_$cleanTitle.$audioExt")
                    val tempMuxed = File(cacheDir, "mux_${System.currentTimeMillis()}_$cleanTitle.$ext")

                    try {
                        // 1. Download video (0% -> 80%)
                        downloadFile(option.videoStreamUrl, tempVideo) { read, total ->
                            val p = if (total > 0) ((read * 80) / total).toInt() else 0
                            updateProgress(p, "Video ${option.label} · $p% (${formatBytes(read)})")
                        }

                        // 2. Download audio (80% -> 95%)
                        downloadFile(option.audioStreamUrl, tempAudio) { read, total ->
                            val p = 80 + (if (total > 0) ((read * 15) / total).toInt() else 0)
                            updateProgress(p, "Audio · $p% (${formatBytes(read)})")
                        }

                        // 3. Mux (95% -> 100%)
                        updateProgress(96, "Muxing video & audio…")
                        val muxSuccess = runCatching {
                            muxAudioVideo(tempVideo, tempAudio, tempMuxed, isWebm)
                        }.isSuccess

                        val finalFile = if (muxSuccess && tempMuxed.exists() && tempMuxed.length() > 0) {
                            tempMuxed
                        } else {
                            tempVideo
                        }

                        val fileName = "$cleanTitle [${option.label}].$ext"
                        val savedUri = saveToDownloads(context, finalFile, fileName, mime)
                            ?: throw IOException("Couldn't save the video to Downloads")
                        DownloadLibrary.register(context, video, description, savedUri, fileName)
                        showCompleteNotification(context, notificationId, title, option.label, savedUri, mime)
                    } finally {
                        tempVideo.delete()
                        tempAudio.delete()
                        tempMuxed.delete()
                    }
                }
            }
        }
    }

    private const val CHUNK_SIZE = 2L * 1024L * 1024L // 2MB ranged chunks: completely bypasses YouTube CDN rate limiter!
    private const val PARALLEL_WORKERS = 3 // 3 concurrent streams for maximum 5G / 1Gbps bandwidth saturation

    private suspend fun downloadFile(
        url: String,
        targetFile: File,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        targetFile.parentFile?.mkdirs()

        // Probe total content length using a 1-byte Range request
        var totalBytes = -1L
        var supportsRange = false

        try {
            val probeRequest = Request.Builder()
                .url(url)
                .header("User-Agent", Net.USER_AGENT)
                .header("Range", "bytes=0-0")
                .build()

            Net.client.newCall(probeRequest).execute().use { response ->
                if (response.code == 206) {
                    supportsRange = true
                    val cr = response.header("Content-Range")
                    if (cr != null && cr.contains("/")) {
                        totalBytes = cr.substringAfter("/").trim().toLongOrNull() ?: -1L
                    }
                } else if (response.isSuccessful) {
                    totalBytes = response.body?.contentLength() ?: -1L
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Range probe failed, attempting direct stream: ${e.message}")
        }

        if (!supportsRange || totalBytes <= 0) {
            downloadDirectStream(url, targetFile, onProgress)
            return@withContext
        }

        val chunkCount = ((totalBytes + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()
        val nextChunkIndex = AtomicInteger(0)
        val downloadedBytesTotal = AtomicLong(0L)
        val activeCalls = ConcurrentHashMap<Call, Boolean>()

        val raf = RandomAccessFile(targetFile, "rw")
        raf.setLength(totalBytes)
        val fileChannel = raf.channel

        val currentJob = currentCoroutineContext()[Job]
        val cancellation = currentJob?.invokeOnCompletion {
            activeCalls.keys.forEach { call -> runCatching { call.cancel() } }
            activeCalls.clear()
        }

        try {
            coroutineScope {
                val workers = (0 until minOf(PARALLEL_WORKERS, chunkCount)).map {
                    launch {
                        val buffer = ByteArray(64 * 1024)
                        while (isActive) {
                            val chunkIdx = nextChunkIndex.getAndIncrement()
                            if (chunkIdx >= chunkCount) break

                            val chunkStart = chunkIdx * CHUNK_SIZE
                            val chunkEnd = minOf(chunkStart + CHUNK_SIZE - 1, totalBytes - 1)
                            val expectedChunkSize = chunkEnd - chunkStart + 1

                            var chunkSuccess = false
                            var attempt = 0

                            while (!chunkSuccess && attempt < 5 && isActive) {
                                attempt++
                                try {
                                    val chunkReq = Request.Builder()
                                        .url(url)
                                        .header("User-Agent", Net.USER_AGENT)
                                        .header("Range", "bytes=$chunkStart-$chunkEnd")
                                        .build()

                                    val call = Net.client.newCall(chunkReq)
                                    activeCalls[call] = true

                                    try {
                                        call.execute().use { resp ->
                                            if (resp.code != 206 && resp.code != 200) {
                                                throw IOException("Unexpected HTTP ${resp.code} for chunk $chunkIdx")
                                            }
                                            val stream = resp.body?.byteStream() ?: throw IOException("Empty body for chunk $chunkIdx")
                                            var currentChunkOffset = chunkStart
                                            var bytesReadThisChunk = 0L
                                            var r: Int

                                            while (stream.read(buffer).also { r = it } != -1) {
                                                ensureActive()
                                                val byteBuffer = ByteBuffer.wrap(buffer, 0, r)
                                                while (byteBuffer.hasRemaining()) {
                                                    val written = fileChannel.write(byteBuffer, currentChunkOffset)
                                                    currentChunkOffset += written
                                                }
                                                bytesReadThisChunk += r
                                                val currentProgress = downloadedBytesTotal.addAndGet(r.toLong())
                                                onProgress(currentProgress, totalBytes)
                                            }

                                            if (bytesReadThisChunk == expectedChunkSize) {
                                                chunkSuccess = true
                                            } else {
                                                downloadedBytesTotal.addAndGet(-bytesReadThisChunk)
                                                throw IOException("Short chunk read: got $bytesReadThisChunk expected $expectedChunkSize")
                                            }
                                        }
                                    } finally {
                                        activeCalls.remove(call)
                                    }
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Log.w(TAG, "Chunk $chunkIdx ($chunkStart-$chunkEnd) attempt $attempt failed: ${e.message}")
                                    if (attempt >= 5) throw e
                                    delay(200L * attempt)
                                }
                            }
                        }
                    }
                }
                workers.forEach { it.join() }
            }
            fileChannel.force(true)
        } finally {
            cancellation?.dispose()
            activeCalls.clear()
            runCatching { fileChannel.close() }
            runCatching { raf.close() }
        }
    }

    private suspend fun downloadDirectStream(
        url: String,
        targetFile: File,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", Net.USER_AGENT)
            .build()

        val call = Net.client.newCall(request)
        val cancellation = currentCoroutineContext()[Job]?.invokeOnCompletion {
            call.cancel()
        }

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("Network error ${response.code}")
                val body = response.body ?: throw IOException("Empty body")
                val totalBytes = body.contentLength()
                targetFile.parentFile?.mkdirs()

                targetFile.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            currentCoroutineContext().ensureActive()
                            output.write(buffer, 0, read)
                            bytesRead += read
                            onProgress(bytesRead, totalBytes)
                        }
                        output.flush()
                    }
                }
            }
        } finally {
            cancellation?.dispose()
        }
    }

    private fun muxAudioVideo(videoFile: File, audioFile: File, outputFile: File, isWebm: Boolean = false) {
        val videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
        val audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }
        val muxerFormat = if (isWebm) MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        val muxer = MediaMuxer(outputFile.absolutePath, muxerFormat)

        try {
            var videoTrackIndex = -1
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoExtractor.selectTrack(i)
                    videoTrackIndex = muxer.addTrack(format)
                    break
                }
            }

            var audioTrackIndex = -1
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioExtractor.selectTrack(i)
                    audioTrackIndex = muxer.addTrack(format)
                    break
                }
            }

            if (videoTrackIndex == -1) throw IOException("No video track found")
            muxer.start()

            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()

            // Copy video
            while (true) {
                val sampleSize = videoExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                bufferInfo.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(videoTrackIndex, buffer, bufferInfo)
                videoExtractor.advance()
            }

            // Copy audio if available
            if (audioTrackIndex != -1) {
                while (true) {
                    val sampleSize = audioExtractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                    bufferInfo.flags = audioExtractor.sampleFlags
                    muxer.writeSampleData(audioTrackIndex, buffer, bufferInfo)
                    audioExtractor.advance()
                }
            }
        } finally {
            runCatching { muxer.stop() }
            runCatching { muxer.release() }
            runCatching { videoExtractor.release() }
            runCatching { audioExtractor.release() }
        }
    }

    private fun saveToDownloads(context: Context, sourceFile: File, fileName: String, mimeType: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/iTube")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            try {
                val output = resolver.openOutputStream(uri) ?: throw IOException("Couldn't open the Downloads file")
                output.use { out ->
                    sourceFile.inputStream().use { input -> input.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (error: Exception) {
                resolver.delete(uri, null, null)
                throw error
            }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "iTube")
            dir.mkdirs()
            val destFile = File(dir, fileName)
            sourceFile.copyTo(destFile, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf(mimeType), null)
            Uri.fromFile(destFile)
        }
    }

    private fun showCompleteNotification(
        context: Context,
        notificationId: Int,
        title: String,
        quality: String,
        fileUri: Uri?,
        mimeType: String
    ) {
        val nm = NotificationManagerCompat.from(context)

        val pendingIntent = fileUri?.let { uri ->
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Download Complete")
            .setContentText("$title · $quality")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }

        try {
            nm.notify(notificationId, builder.build())
        } catch (_: SecurityException) {}
    }

    private fun showFailedNotification(
        context: Context,
        notificationId: Int,
        title: String,
        reason: String
    ) {
        val nm = NotificationManagerCompat.from(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Download Failed")
            .setContentText("$title · $reason")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)

        try {
            nm.notify(notificationId, builder.build())
        } catch (_: SecurityException) {}
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            val kb = bytes / 1024.0
            String.format(Locale.US, "%.0f KB", kb)
        }
    }
}
