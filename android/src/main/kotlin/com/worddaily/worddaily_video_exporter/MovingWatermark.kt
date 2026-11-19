package com.worddaily.worddaily_video_exporter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

@UnstableApi
internal class MovingWatermark(private val logo: Bitmap, private val username: String) : BitmapOverlay() {
    companion object {
        // Draw at double resolution for antialiased glyphs and scale back to video pixels.
        // Keep this in sync with StaticOverlaySettings.setScale below.
        private const val RASTER_SCALE = 2f
    }

    private var card: Bitmap? = null

    override fun configure(videoSize: Size) {
        super.configure(videoSize)

        val shortSide = min(videoSize.width, videoSize.height).toFloat()
        val unit = (shortSide / 1080f).coerceIn(0.45f, 2.5f)
        val horizontalPadding = 8f * unit
        val verticalPadding = 6f * unit
        val logoBox = 66f * unit
        val logoGap = 9f * unit
        val lineGap = 2f * unit

        fun textPaint(size: Float, colorValue: Int, weight: Int): TextPaint =
            TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                color = colorValue
                typeface = Typeface.create(Typeface.SANS_SERIF, weight)
                textSize = size * unit
            }

        val brandPaint = textPaint(28f, Color.WHITE, Typeface.BOLD)
        val creatorPaint = textPaint(24f, Color.WHITE, Typeface.NORMAL)
        val sitePaint = textPaint(20f, Color.rgb(255, 211, 128), Typeface.NORMAL)

        // Cap long usernames without reserving a large empty rectangle for short ones.
        val maxCardWidth = shortSide * 0.53f
        val textBudget = (maxCardWidth - 2f * horizontalPadding - logoBox - logoGap)
            .coerceAtLeast(1f)
        fun fit(value: String, paint: TextPaint): String =
            TextUtils.ellipsize(value, paint, textBudget, TextUtils.TruncateAt.END).toString()

        val brand = fit("WordDaily", brandPaint)
        val creator = fit("@" + username.trim().removePrefix("@").take(64), creatorPaint)
        val site = fit("worddailyapp.com", sitePaint)

        val measuredTextWidth = max(
            brandPaint.measureText(brand),
            max(creatorPaint.measureText(creator), sitePaint.measureText(site))
        )
        val paints = listOf(brandPaint, creatorPaint, sitePaint)
        val labels = listOf(brand, creator, site)
        val lineHeights = paints.map { it.fontMetrics.descent - it.fontMetrics.ascent }
        val textHeight = lineHeights.sum() + 2f * lineGap
        val contentHeight = max(logoBox, textHeight)
        val logicalWidth = 2f * horizontalPadding + logoBox + logoGap + measuredTextWidth
        val logicalHeight = 2f * verticalPadding + contentHeight

        val bitmap = Bitmap.createBitmap(
            ceil(logicalWidth * RASTER_SCALE).toInt().coerceAtLeast(1),
            ceil(logicalHeight * RASTER_SCALE).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.scale(RASTER_SCALE, RASTER_SCALE)

        // A subtle background follows the actual logo and text bounds.
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(137, 12, 20, 32)
        }
        val cornerRadius = 8f * unit
        canvas.drawRoundRect(
            RectF(0f, 0f, logicalWidth, logicalHeight),
            cornerRadius, cornerRadius, background
        )

        // Trim transparent padding from the supplied logo when drawing, without
        // modifying the source bitmap or distorting its visible aspect ratio.
        val logoBounds = visibleLogoBounds(logo)
        val aspect = logoBounds.width().toFloat() / logoBounds.height().toFloat()
        val logoWidth = if (aspect >= 1f) logoBox else logoBox * aspect
        val logoHeight = if (aspect >= 1f) logoBox / aspect else logoBox
        val logoLeft = horizontalPadding + (logoBox - logoWidth) / 2f
        val logoTop = (logicalHeight - logoHeight) / 2f
        canvas.drawBitmap(
            logo,
            logoBounds,
            RectF(logoLeft, logoTop, logoLeft + logoWidth, logoTop + logoHeight),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )

        val textX = horizontalPadding + logoBox + logoGap
        var textTop = (logicalHeight - textHeight) / 2f
        for (index in paints.indices) {
            val paint = paints[index]
            canvas.drawText(labels[index], textX, textTop - paint.fontMetrics.ascent, paint)
            textTop += lineHeights[index] + lineGap
        }

        card?.recycle()
        card = bitmap
    }

    /** Ignore fully transparent margins common in FlutterFlow logo assets. */
    private fun visibleLogoBounds(bitmap: Bitmap): Rect {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var left = width
        var top = height
        var right = 0
        var bottom = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                if ((pixels[y * width + x] ushr 24) > 8) {
                    left = min(left, x)
                    top = min(top, y)
                    right = max(right, x + 1)
                    bottom = max(bottom, y + 1)
                }
            }
        }
        return if (right > left && bottom > top) Rect(left, top, right, bottom)
        else Rect(0, 0, width, height)
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap = checkNotNull(card)

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val (x, y) = CornerSchedule.anchor(presentationTimeUs)
        return StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(x * 0.94f, y * 0.94f)
            .setOverlayFrameAnchor(x, y)
            .setScale(1f / RASTER_SCALE, 1f / RASTER_SCALE)
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
