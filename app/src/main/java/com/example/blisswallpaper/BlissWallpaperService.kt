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
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import kotlin.math.max

class BlissWallpaperService : WallpaperService() {

    companion object {
        private const val TAG = "BlissWallpaperService"
        private const val FRAME_DELAY_MS = 16L // ~60 FPS
        private const val CLOUD_FADE_PERCENT = 0.15f
    }

    override fun onCreateEngine(): Engine {
        return BlissEngine()
    }

    inner class BlissEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var lastTime = SystemClock.elapsedRealtime()

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
        private var currentXOffset = 0.5f

        private val drawRunnable = Runnable { drawFrame() }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            loadBitmaps()
        }

        override fun onDestroy() {
            super.onDestroy()
            visible = false
            handler.removeCallbacks(drawRunnable)
            recycleBitmaps()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                lastTime = SystemClock.elapsedRealtime()
                handler.post(drawRunnable)
            } else {
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            screenW = width
            screenH = height
            if (visible) {
                handler.removeCallbacks(drawRunnable)
                handler.post(drawRunnable)
            }
        }

        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float,
            xOffsetStep: Float, yOffsetStep: Float,
            xPixelOffset: Int, yPixelOffset: Int
        ) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset)
            currentXOffset = xOffset
            if (visible) {
                handler.removeCallbacks(drawRunnable)
                handler.post(drawRunnable)
            }
        }

        private fun loadBitmaps() {
            try {
                // Decode options to optimize memory if needed, but since we are in drawable-nodpi it will not scale automatically.
                val rawBg = BitmapFactory.decodeResource(resources, R.drawable.bliss_hill_bg)
                val rawBgFg = BitmapFactory.decodeResource(resources, R.drawable.bliss_hill_foreground)
                val rawClouds1 = BitmapFactory.decodeResource(resources, R.drawable.bliss_clouds)
                val rawClouds2 = BitmapFactory.decodeResource(resources, R.drawable.bliss_clouds_wispy)

                bgBitmap = rawBg
                bgForegroundBitmap = rawBgFg

                // Process cloud layers to fade their left/right edges. This guarantees seamless looping
                // even if the generated textures have clouds that touch the boundaries.
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

        /**
         * Fades the left and right edges of a bitmap to transparent so it tiles seamlessly.
         */
        private fun fadeEdges(src: Bitmap, fadePercent: Float): Bitmap {
            val w = src.width
            val h = src.height
            val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)

            // Draw original
            canvas.drawBitmap(src, 0f, 0f, null)

            // Prepare mask paint
            val maskPaint = Paint().apply {
                isAntiAlias = true
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }

            // Left edge fade (0 to fadeWidth)
            val fadeW = w * fadePercent
            maskPaint.shader = LinearGradient(
                0f, 0f, fadeW, 0f,
                0x00FFFFFF, 0xFFFFFFFF.toInt(),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, fadeW, h.toFloat(), maskPaint)

            // Right edge fade (w - fadeWidth to w)
            maskPaint.shader = LinearGradient(
                w - fadeW, 0f, w.toFloat(), 0f,
                0xFFFFFFFF.toInt(), 0x00FFFFFF,
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(w - fadeW, 0f, w.toFloat(), h.toFloat(), maskPaint)

            return result
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    val now = SystemClock.elapsedRealtime()
                    val dt = (now - lastTime) / 1000f
                    lastTime = now

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

            // Schedule next frame if visible
            handler.removeCallbacks(drawRunnable)
            if (visible) {
                handler.postDelayed(drawRunnable, FRAME_DELAY_MS)
            }
        }

        private fun getCloudPeriod(bitmap: Bitmap?, scaleFactor: Float): Float {
            if (bitmap == null) return 1f
            // Period is (scaled_width - overlap) in canvas-space pixels
            val cloudScale = (screenW.toFloat() * 1.5f * scaleFactor) / 1024f
            val scaledW = bitmap.width * cloudScale
            val overlapW = scaledW * CLOUD_FADE_PERCENT
            return scaledW - overlapW
        }

        private fun updateAnimation(dt: Float) {
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

            // 1. Render Background (Hill and Sky)
            // Scale background to cover screen height. We crop width if it exceeds screen.
            val bgScale = screenH.toFloat() / bg.height
            val bgW = bg.width * bgScale
            val bgH = bg.height * bgScale

            // Swipe parallax shift
            val maxBgShift = max(0f, bgW - screenW)
            val bgX = -maxBgShift * currentXOffset
            val bgY = screenH - bgH // Align background bottom to screen bottom to ensure hill sits correctly

            val bgRect = RectF(bgX, bgY, bgX + bgW, bgY + bgH)
            canvas.drawBitmap(bg, null, bgRect, paint)

            // 2. Render Wispy Cloud Layer (Background layer, moves slower)
            clouds2Bitmap?.let { c2 ->
                // Draw higher in the sky (starting around 2% of screen height)
                drawCloudLayer(canvas, c2, cloud2Offset, screenW, screenH, bgX, 0.4f, 0.02f, 0.15f)
            }

            // 3. Render Fluffy Cloud Layer (Foreground layer, moves faster)
            clouds1Bitmap?.let { c1 ->
                // Draw slightly lower in the sky (starting around 30% of screen height)
                drawCloudLayer(canvas, c1, cloud1Offset, screenW, screenH, bgX, 0.7f, 0.28f, 0.35f)
            }

            // 4. Render Foreground Hill (drawn on top of the clouds to sandwich them)
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
            scaleFactor: Float, // Scale relative to screen width
            yPositionFactor: Float, // Vertical position as fraction of screen height
            parallaxFactor: Float // Shift speed relative to hills (must be < 1.0f)
        ) {
            // Scale the cloud texture based on a base tile width of 1024 to span 4x width naturally
            val cloudScale = (screenW.toFloat() * 1.5f * scaleFactor) / 1024f
            val scaledW = bitmap.width * cloudScale
            val scaledH = bitmap.height * cloudScale
            val startY = screenH * yPositionFactor

            // Calculate overlap and scroll period
            val overlapW = scaledW * CLOUD_FADE_PERCENT
            val period = scaledW - overlapW

            // Parallax shift based on home screen swipes (scrolls appropriately slower than hills)
            val swipeShiftX = hillShiftX * parallaxFactor

            // Total draw coordinate in X
            var drawX = swipeShiftX + scrollOffset

            // Wrap drawX to fit in the range [-period, 0] to start tiling
            while (drawX > 0) {
                drawX -= period
            }
            while (drawX < -period) {
                drawX += period
            }

            // Tile horizontally to cover the screen width, overlapping by overlapW
            var tileX = drawX
            while (tileX < screenW) {
                val destRect = RectF(tileX, startY, tileX + scaledW, startY + scaledH)
                canvas.drawBitmap(bitmap, null, destRect, paint)
                tileX += period
            }
        }
    }
}
