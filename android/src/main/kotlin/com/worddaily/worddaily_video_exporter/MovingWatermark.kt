package com.worddaily.worddaily_video_exporter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import kotlin.math.min
import kotlin.math.roundToInt

@UnstableApi
internal class MovingWatermark(private val logo: Bitmap, private val username: String) : BitmapOverlay() {
    private var card: Bitmap? = null

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        // Bitmap pixel dimensions are in the video's displayed coordinate space.
        val shortSide = min(videoSize.width, videoSize.height).toFloat()
        // Size against the short side so landscape exports do not get an oversized banner.
        val width = (shortSide * 0.43f).roundToInt().coerceAtLeast(1)
        val height = (shortSide * 0.085f).roundToInt().coerceAtLeast(1)
        val padding = height * 0.12f
        val icon = height - 2 * padding
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.color = Color.argb(170, 18, 18, 18)
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), padding, padding, paint)
        val scale = min(icon / logo.width, icon / logo.height)
        val lw = logo.width * scale
        val lh = logo.height * scale
        canvas.drawBitmap(logo, null,
            RectF(padding + (icon - lw) / 2, (height - lh) / 2,
                padding + (icon + lw) / 2, (height + lh) / 2), paint)
        val x = padding * 2 + icon
        val available = (width - x - padding).coerceAtLeast(1f)
        // Three readable lines beside the logo: brand, creator and official website.
        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        paint.textSize = height * 0.25f
        canvas.drawText(TextUtils.ellipsize("WordDaily", android.text.TextPaint(paint), available,
            TextUtils.TruncateAt.END).toString(), x, height * 0.31f, paint)

        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        paint.textSize = height * 0.22f
        val label = "@" + username.removePrefix("@").take(64)
        canvas.drawText(TextUtils.ellipsize(label, android.text.TextPaint(paint), available,
            TextUtils.TruncateAt.END).toString(), x, height * 0.57f, paint)

        paint.color = Color.rgb(255, 211, 128)
        paint.textSize = height * 0.19f
        canvas.drawText(TextUtils.ellipsize("worddailyapp.com", android.text.TextPaint(paint), available,
            TextUtils.TruncateAt.END).toString(), x, height * 0.82f, paint)
        card?.recycle()
        card = bitmap
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap = checkNotNull(card)

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val (x, y) = CornerSchedule.anchor(presentationTimeUs)
        return StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(x * 0.94f, y * 0.94f)
            .setOverlayFrameAnchor(x, y)
            .setAlphaScale(CornerSchedule.alphaScale(presentationTimeUs))
            .build()
    }

    override fun release() {
        try { super.release() } finally {
            card?.recycle()
            card = null
            logo.recycle()
        }
    }
}
