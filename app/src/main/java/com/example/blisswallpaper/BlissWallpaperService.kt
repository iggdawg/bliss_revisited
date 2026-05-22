package com.example.blisswallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceHolder
import kotlin.math.max

class BlissWallpaperService : WallpaperService() {

    companion object {
        private const val TAG = "BlissWallpaperService"
        private const val CLOUD_FADE_PERCENT = 0.15f
    }

    override fun onCreateEngine(): Engine {
        return BlissEngine()
    }

    inner class BlissEngine : Engine() {
        private var visible = false
        private var lastFrameTimeNanos = 0L
        private var firstFrame = true

        // Bitmaps
        private var bgBitmap: Bitmap? = null
        private var bgForegroundBitmap: Bitmap? = null // Foreground hill
        private var clouds1Bitmap: Bitmap? = null // Fluffy clouds
        private var clouds2Bitmap: Bitmap? = null // Wispy clouds

        // Paint
        private val paint = Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
        }

        // Horizontal scroll offsets
        private var cloud1Offset = 0f
        private var cloud2Offset = 0f

        // Speeds (pixels per second)
        private val cloud1Speed = 12f // Fluffy clouds speed
        private val cloud2Speed = 6f  // Wispy clouds speed

        // Screen dimensions (stored to calculate exact canvas-space tiling period)
        private var screenW = 1080
        private var screenH = 2400

        // Swipe parallax variables
        private var targetXOffset = 0.5f
        private var currentXOffset = 0.5f

        // Reusable RectF instances to avoid object allocations in rendering loop
        private val bgRect = RectF()
        private val cloudTileRect = RectF()

        private val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (visible) {
                    val dt = if (lastFrameTimeNanos == 0L) {
                        0f
                    } else {
                        (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f
                    }
                    lastFrameTimeNanos = frameTimeNanos

                    drawFrame(dt)
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            loadBitmaps()
        }

        override fun onDestroy() {
            super.onDestroy()
            visible = false
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            recycleBitmaps()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                lastFrameTimeNanos = 0L
                firstFrame = true
                Choreographer.getInstance().postFrameCallback(frameCallback)
            } else {
                Choreographer.getInstance().removeFrameCallback(frameCallback)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            screenW = width
            screenH = height
        }

        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float,
            xOffsetStep: Float, yOffsetStep: Float,
            xPixelOffset: Int, yPixelOffset: Int
        ) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset)
            targetXOffset = xOffset
        }

        private fun loadBitmaps() {
            try {
                val rawBg = BitmapFactory.decodeResource(resources, R.drawable.bliss_hill_bg)
                val rawBgFg = BitmapFactory.decodeResource(resources, R.drawable.bliss_hill_foreground)
                val rawClouds1 = BitmapFactory.decodeResource(resources, R.drawable.bliss_clouds)
                val rawClouds2 = BitmapFactory.decodeResource(resources, R.drawable.bliss_clouds_wispy)

                bgBitmap = rawBg
                bgForegroundBitmap = rawBgFg

                if (rawClouds1 != null) {
                    clouds1Bitmap = fadeEdges(rawClouds1, 0.15f)
                    rawClouds1.recycle()
                }

                if (rawClouds2 != null) {
                    clouds2Bitmap = fadeEdges(rawClouds2, 0.15f)
                    rawClouds2.recycle()
                }

                Log.d(TAG, "Bitmaps loaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading wallpaper bitmaps", e)
            }
        }

        private fun recycleBitmaps() {
            bgBitmap?.recycle()
            bgBitmap = null
            bgForegroundBitmap?.recycle()
            bgForegroundBitmap = null
            clouds1Bitmap?.recycle()
            clouds1Bitmap = null
            clouds2Bitmap?.recycle()
            clouds2Bitmap = null
        }

        private fun fadeEdges(src: Bitmap, fadePercent: Float): Bitmap {
            val w = src.width
            val h = src.height
            val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)

            canvas.drawBitmap(src, 0f, 0f, null)

            val maskPaint = Paint().apply {
                isAntiAlias = true
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }

            val fadeW = w * fadePercent
            maskPaint.shader = LinearGradient(
                0f, 0f, fadeW, 0f,
                0x00FFFFFF, 0xFFFFFFFF.toInt(),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, fadeW, h.toFloat(), maskPaint)

            maskPaint.shader = LinearGradient(
                w - fadeW, 0f, w.toFloat(), 0f,
                0xFFFFFFFF.toInt(), 0x00FFFFFF,
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(w - fadeW, 0f, w.toFloat(), h.toFloat(), maskPaint)

            return result
        }

        private fun drawFrame(dt: Float) {
            val holder = surfaceHolder ?: return
            var canvas: Canvas? = null
            try {
                canvas = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    holder.lockHardwareCanvas()
                } else {
                    holder.lockCanvas()
                }
                if (canvas != null) {
                    updateAnimation(dt)
                    render(canvas)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error drawing frame", e)
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error unlocking canvas", e)
                    }
                }
            }
        }

        private fun getCloudPeriod(bitmap: Bitmap?, scaleFactor: Float): Float {
            if (bitmap == null) return 1f
            val cloudScale = (screenW.toFloat() * 1.5f * scaleFactor) / 1024f
            val scaledW = bitmap.width * cloudScale
            val overlapW = scaledW * CLOUD_FADE_PERCENT
            return scaledW - overlapW
        }

        private fun updateAnimation(dt: Float) {
            // Smoothly interpolate currentXOffset towards targetXOffset
            if (firstFrame) {
                currentXOffset = targetXOffset
                firstFrame = false
            } else if (dt > 0f) {
                val interpolationFactor = 1f - kotlin.math.exp(-15f * dt)
                currentXOffset += (targetXOffset - currentXOffset) * interpolationFactor
            }

            // Update cloud offsets (scrolling from left to right)
            cloud1Offset += cloud1Speed * dt
            cloud2Offset += cloud2Speed * dt

            // Wrap horizontal offsets using the correct canvas-space period to ensure seamless looping
            val p1 = getCloudPeriod(clouds1Bitmap, 0.7f)
            if (cloud1Offset >= p1) {
                cloud1Offset %= p1
            }

            val p2 = getCloudPeriod(clouds2Bitmap, 0.4f)
            if (cloud2Offset >= p2) {
                cloud2Offset %= p2
            }
        }

        private fun render(canvas: Canvas) {
            val screenW = canvas.width
            val screenH = canvas.height

            val bg = bgBitmap ?: return

            val bgScale = screenH.toFloat() / bg.height
            val bgW = bg.width * bgScale
            val bgH = bg.height * bgScale

            val maxBgShift = max(0f, bgW - screenW)
            val bgX = -maxBgShift * currentXOffset
            val bgY = screenH - bgH

            bgRect.set(bgX, bgY, bgX + bgW, bgY + bgH)
            canvas.drawBitmap(bg, null, bgRect, paint)

            clouds2Bitmap?.let { c2 ->
                drawCloudLayer(canvas, c2, cloud2Offset, screenW, screenH, bgX, 0.4f, 0.02f, 0.15f)
            }

            clouds1Bitmap?.let { c1 ->
                drawCloudLayer(canvas, c1, cloud1Offset, screenW, screenH, bgX, 0.7f, 0.28f, 0.35f)
            }

            bgForegroundBitmap?.let { fg ->
                canvas.drawBitmap(fg, null, bgRect, paint)
            }
        }

        private fun drawCloudLayer(
            canvas: Canvas,
            bitmap: Bitmap,
            scrollOffset: Float,
            screenW: Int,
            screenH: Int,
            hillShiftX: Float,
            scaleFactor: Float,
            yPositionFactor: Float,
            parallaxFactor: Float
        ) {
            val cloudScale = (screenW.toFloat() * 1.5f * scaleFactor) / 1024f
            val scaledW = bitmap.width * cloudScale
            val scaledH = bitmap.height * cloudScale
            val startY = screenH * yPositionFactor

            val overlapW = scaledW * CLOUD_FADE_PERCENT
            val period = scaledW - overlapW

            val swipeShiftX = hillShiftX * parallaxFactor

            var drawX = swipeShiftX + scrollOffset

            while (drawX > 0) {
                drawX -= period
            }
            while (drawX < -period) {
                drawX += period
            }

            var tileX = drawX
            while (tileX < screenW) {
                cloudTileRect.set(tileX, startY, tileX + scaledW, startY + scaledH)
                canvas.drawBitmap(bitmap, null, cloudTileRect, paint)
                tileX += period
            }
        }
    }
}
