package com.worddaily.worddaily_video_exporter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.effect.TextureOverlay
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.util.concurrent.Executors

@UnstableApi
class WorddailyVideoExporterPlugin : FlutterPlugin, MethodChannel.MethodCallHandler {
    private lateinit var context: Context
    private var channel: MethodChannel? = null
    private val handler = Handler(Looper.getMainLooper())
    private var executor = Executors.newSingleThreadExecutor()
    private var job: Job? = null

    private class Job(val id: String) {
        var output: File? = null
        var staging: File? = null
        var transformer: Transformer? = null
        var progress: Runnable? = null
    }
    private data class VideoMetadata(val dimensions: DisplayDimensions, val hasAudio: Boolean)
    private data class Prepared(
        val input: File,
        val outro: File,
        val output: File,
        val staging: File,
        val logo: Bitmap,
        val dimensions: DisplayDimensions
    )

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        if (executor.isShutdown) executor = Executors.newSingleThreadExecutor()
        channel = MethodChannel(binding.binaryMessenger, "worddaily_video_exporter").also {
            it.setMethodCallHandler(this)
        }
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "export" -> {
                if (job != null) { result.error("busy", "An export is already running.", null); return }
                val id = call.argument<String>("id")
                val input = call.argument<String>("inputPath")
                val logo = call.argument<String>("logoPath")
                val outro = call.argument<String>("outroPath")
                val output = call.argument<String>("outputPath")
                val username = call.argument<String>("creatorUsername")?.trim()
                if (id.isNullOrBlank() || input.isNullOrBlank() || logo.isNullOrBlank() ||
                    outro.isNullOrBlank() || output.isNullOrBlank() || username.isNullOrBlank()) {
                    result.error("invalid_argument", "Local video, logo, outro, username and output path are required.", null)
                    return
                }
                val current = Job(id)
                job = current
                result.success(null)
                emit(current, "progress", mapOf("progress" to null))
                // File/metadata/bitmap IO stays off the UI thread; Transformer stays on main.
                executor.execute {
                    try {
                        val prepared = prepare(input, logo, outro, output)
                        handler.post {
                            if (job !== current) {
                                prepared.logo.recycle(); prepared.staging.delete(); prepared.output.delete()
                            } else {
                                current.output = prepared.output
                                current.staging = prepared.staging
                                start(current, prepared, username)
                            }
                        }
                    } catch (error: Exception) {
                        handler.post {
                            if (job === current) fail(current, "invalid_input", error.message ?: "Could not read input files.")
                        }
                    }
                }
            }
            "getVideoOrientation" -> {
                val input = call.argument<String>("inputPath")
                if (input.isNullOrBlank()) {
                    result.error("invalid_argument", "A local source video is required.", null)
                    return
                }
                // Metadata IO must not block Flutter's platform thread.
                executor.execute {
                    try {
                        val file = File(input).canonicalFile
                        require(file.isFile && file.canRead()) { "Input video is not a readable local file." }
                        val orientation = readVideoMetadata(file).dimensions.orientation.wireValue
                        handler.post { result.success(orientation) }
                    } catch (error: Exception) {
                        handler.post {
                            result.error("invalid_input", error.message ?: "Could not inspect video.", null)
                        }
                    }
                }
            }
            "cancel" -> {
                val current = job
                if (current != null && call.argument<String>("id") == current.id) {
                    current.transformer?.cancel()
                    finish(current, "cancelled")
                }
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    private fun readVideoMetadata(file: File): VideoMetadata {
        val metadata = MediaMetadataRetriever()
        try {
            metadata.setDataSource(file.absolutePath)
            require(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes") {
                "Video file has no video track."
            }
            val width = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val height = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val rotation = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            val dimensions = VideoDimensions.displayed(width, height, rotation)
            val hasAudio = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            return VideoMetadata(dimensions, hasAudio)
        } finally {
            metadata.release()
        }
    }

    private fun prepare(inputPath: String, logoPath: String, outroPath: String, outputPath: String): Prepared {
        val input = File(inputPath).canonicalFile
        val logo = File(logoPath).canonicalFile
        val outro = File(outroPath).canonicalFile
        val output = File(outputPath).canonicalFile
        require(input.isFile && input.canRead()) { "Input video is not a readable local file." }
        require(logo.isFile && logo.canRead()) { "Logo is not a readable PNG, JPEG or WebP file." }
        require(outro.isFile && outro.canRead()) { "Outro is not a readable local MP4 file." }
        require(outro.extension.equals("mp4", ignoreCase = true)) { "Outro must be an MP4 file." }
        require(outro != input && outro != logo) { "Outro must be separate from the source and logo." }
        require(output != input && output != logo && output != outro) {
            "Output must not replace an input file."
        }
        require(output.extension.equals("mp4", ignoreCase = true)) { "Output must end in .mp4." }
        val parent = output.parentFile
        require(parent != null && parent.isDirectory && parent.canWrite()) {
            "Output folder must exist and be writable."
        }

        val sourceMetadata = readVideoMetadata(input)
        val outroMetadata = readVideoMetadata(outro)
        require(outroMetadata.hasAudio) { "Approved outro must contain its sound track." }
        require(sourceMetadata.dimensions.orientation == outroMetadata.dimensions.orientation) {
            "Wrong outro orientation: expected ${sourceMetadata.dimensions.orientation.wireValue}, " +
                "received ${outroMetadata.dimensions.orientation.wireValue}."
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(logo.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) {
            "Logo must be a supported raster image (not SVG)."
        }
        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        while (bounds.outWidth / options.inSampleSize > 512 ||
            bounds.outHeight / options.inSampleSize > 512) {
            options.inSampleSize *= 2
        }
        val bitmap = requireNotNull(BitmapFactory.decodeFile(logo.path, options)) {
            "Could not decode logo."
        }
        var reserved = false
        var staging: File? = null
        try {
            require(output.createNewFile()) { "Output already exists; choose a new path." }
            reserved = true
            staging = File.createTempFile(".worddaily-export-", ".mp4", parent)
            check(staging.delete()) { "Could not prepare output." }
            return Prepared(input, outro, output, staging, bitmap, sourceMetadata.dimensions)
        } catch (error: Exception) {
            bitmap.recycle()
            staging?.delete()
            if (reserved) output.delete()
            throw error
        }
    }

    private fun start(current: Job, files: Prepared, username: String) {
        try {
            val overlay = MovingWatermark(files.logo, username)
            // Effects are scoped to the original video. The approved outro stays clean.
            val sourceItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(files.input)))
                .setEffects(Effects(emptyList(), listOf(OverlayEffect(listOf<TextureOverlay>(overlay)))))
                .build()
            val outroItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(files.outro)))
                .setEffects(Effects(emptyList(), listOf(
                    Presentation.createForWidthAndHeight(
                        files.dimensions.width,
                        files.dimensions.height,
                        Presentation.LAYOUT_SCALE_TO_FIT
                    )
                )))
                .build()
            // Sequential items retain their own audio. Force a silent track if the
            // original is muted so the outro's approved AAC sound is not discarded.
            val sequence = EditedMediaItemSequence.Builder(sourceItem, outroItem)
                .experimentalSetForceAudioTrack(true)
                .build()
            val composition = Composition.Builder(sequence).build()
            val transformer = Transformer.Builder(context)
                .setLooper(Looper.getMainLooper())
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (job !== current) return
                        if (files.staging.length() == 0L || !files.staging.renameTo(files.output)) {
                            fail(current, "output_failed", "Could not finalize the exported MP4.")
                            return
                        }
                        finish(current, "completed", mapOf("outputPath" to files.output.absolutePath))
                    }
                    override fun onError(composition: Composition, exportResult: ExportResult, exception: ExportException) {
                        if (job === current) fail(current, "media3_${exception.errorCode}", exception.message ?: "Video export failed.")
                    }
                }).build()
            current.transformer = transformer
            transformer.start(composition, files.staging.absolutePath)
            val progress = object : Runnable {
                override fun run() {
                    if (job !== current) return
                    val holder = ProgressHolder()
                    val available = transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE
                    emit(current, "progress", mapOf("progress" to if (available) holder.progress / 100.0 else null))
                    handler.postDelayed(this, 250)
                }
            }
            current.progress = progress
            handler.post(progress)
        } catch (error: Exception) {
            current.transformer?.cancel()
            if (!files.logo.isRecycled) files.logo.recycle()
            fail(current, "export_failed", error.message ?: "Could not start export.")
        }
    }

    private fun fail(current: Job, code: String, message: String) =
        finish(current, "error", mapOf("code" to code, "message" to message))

    private fun finish(current: Job, type: String, data: Map<String, Any?> = emptyMap()) {
        if (job !== current) return
        job = null
        current.progress?.let { handler.removeCallbacks(it) }
        current.staging?.delete()
        if (type != "completed") current.output?.delete()
        emit(current, type, data)
    }

    private fun emit(current: Job, type: String, data: Map<String, Any?> = emptyMap()) {
        channel?.invokeMethod("event", mapOf("id" to current.id, "type" to type) + data)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        job?.let { current -> current.transformer?.cancel(); finish(current, "cancelled") }
        channel?.setMethodCallHandler(null)
        channel = null
        executor.shutdown()
    }
}
