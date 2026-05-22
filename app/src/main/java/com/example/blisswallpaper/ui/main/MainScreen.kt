package com.example.blisswallpaper.ui.main

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.blisswallpaper.BlissWallpaperService
import com.example.blisswallpaper.R
import com.example.blisswallpaper.data.DefaultDataRepository
import com.example.blisswallpaper.theme.BlissWallpaperTheme
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.math.sin

@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(DefaultDataRepository()) },
) {
  val context = LocalContext.current

  // Dark slate base color palette for premium feel
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF0F172A)) // Slate 900
  ) {
    Column(
      modifier = modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp, vertical = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      // Header Section
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 16.dp)
      ) {
        Text(
          text = stringResource(R.string.wallpaper_name),
          color = Color.White,
          fontSize = 28.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "by " + stringResource(R.string.wallpaper_author),
          color = Color(0xFF94A3B8), // Slate 400
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium
        )
      }

      // Live Interactive Preview Box (Device Frame Mockup)
      Box(
        modifier = Modifier
          .weight(1f)
          .padding(vertical = 24.dp)
          .aspectRatio(9f / 19.5f) // Realistic phone aspect ratio
          .clip(RoundedCornerShape(32.dp))
          .border(4.dp, Color(0xFF334155), RoundedCornerShape(32.dp)) // Slate 700 border
          .background(Color.Black)
      ) {
        BlissLivePreview(modifier = Modifier.fillMaxSize())
      }

      // App Information & Apply Button
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(bottom = 16.dp)
      ) {
        Text(
          text = stringResource(R.string.app_description),
          color = Color(0xFF94A3B8), // Slate 400
          fontSize = 14.sp,
          textAlign = TextAlign.Center,
          lineHeight = 20.sp,
          modifier = Modifier.padding(horizontal = 8.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        // Premium Gradient Button
        Button(
          onClick = { triggerApplyLiveWallpaper(context) },
          colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
          contentPadding = PaddingValues(),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(
              width = 1.dp,
              brush = Brush.linearGradient(
                colors = listOf(Color(0xFF60A5FA), Color(0xFF3B82F6))
              ),
              shape = RoundedCornerShape(16.dp)
            )
        ) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(
                brush = Brush.linearGradient(
                  colors = listOf(
                    Color(0xFF2563EB), // Windows Blue
                    Color(0xFF0284C7)  // Cyan Blue
                  )
                )
              ),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = stringResource(R.string.apply_wallpaper),
              color = Color.White,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 0.5.sp
            )
          }
        }
      }
    }
  }
}

@Composable
fun BlissLivePreview(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val bgBitmap = remember { ImageBitmap.imageResource(context.resources, R.drawable.bliss_hill_bg) }
  val bgForegroundBitmap = remember { ImageBitmap.imageResource(context.resources, R.drawable.bliss_hill_foreground) }
  val cloudsBitmap = remember { ImageBitmap.imageResource(context.resources, R.drawable.bliss_clouds) }
  val wispyBitmap = remember { ImageBitmap.imageResource(context.resources, R.drawable.bliss_clouds_wispy) }

  // Load and apply edge-fading in a remember block to keep performance high
  val processedClouds = remember(cloudsBitmap) {
    val androidBitmap = cloudsBitmap.asAndroidBitmap()
    fadeEdgesAndroid(androidBitmap, 0.15f).asImageBitmap()
  }
  val processedWispy = remember(wispyBitmap) {
    val androidBitmap = wispyBitmap.asAndroidBitmap()
    fadeEdgesAndroid(androidBitmap, 0.15f).asImageBitmap()
  }

  // Animation ticks
  var elapsedSeconds by remember { mutableFloatStateOf(0f) }
  LaunchedEffect(Unit) {
    val startTime = SystemClock.elapsedRealtime()
    while (isActive) {
      withFrameMillis { frameTime ->
        elapsedSeconds = (frameTime - startTime) / 1000f
      }
    }
  }

  // Sway parallax offset between 0.0 and 1.0 over a 12-second cycle to preview the 3D-depth swipe effect
  val swipeOffset = (sin(elapsedSeconds * 2.0 * Math.PI / 12.0).toFloat() + 1f) / 2f

  Canvas(modifier = modifier) {
    val canvasW = size.width
    val canvasH = size.height

    // 1. Draw Background
    val bgScale = canvasH / bgBitmap.height
    val bgW = bgBitmap.width * bgScale
    val bgH = bgBitmap.height * bgScale

    val maxBgShift = max(0f, bgW - canvasW)
    val bgX = -maxBgShift * swipeOffset
    val bgY = canvasH - bgH // Align bottom to screen bottom

    drawImage(
      image = bgBitmap,
      dstOffset = IntOffset(bgX.toInt(), bgY.toInt()),
      dstSize = IntSize(bgW.toInt(), bgH.toInt())
    )

    // 2. Draw Wispy Clouds (Slower, higher altitude)
    val cloud2Speed = 6f // pixels per second
    val cloud2Offset = (elapsedSeconds * cloud2Speed)
    drawCloudLayerCompose(
      canvas = this,
      bitmap = processedWispy,
      scrollOffset = cloud2Offset,
      hillShiftX = bgX,
      canvasW = canvasW,
      canvasH = canvasH,
      scaleFactor = 0.4f,
      yPositionFactor = 0.02f,
      parallaxFactor = 0.15f
    )

    // 3. Draw Fluffy Clouds (Faster, lower altitude)
    val cloud1Speed = 12f // pixels per second
    val cloud1Offset = (elapsedSeconds * cloud1Speed)
    drawCloudLayerCompose(
      canvas = this,
      bitmap = processedClouds,
      scrollOffset = cloud1Offset,
      hillShiftX = bgX,
      canvasW = canvasW,
      canvasH = canvasH,
      scaleFactor = 0.7f,
      yPositionFactor = 0.28f,
      parallaxFactor = 0.35f
    )

    // 4. Draw Foreground Hill (drawn on top of the clouds to sandwich them)
    drawImage(
      image = bgForegroundBitmap,
      dstOffset = IntOffset(bgX.toInt(), bgY.toInt()),
      dstSize = IntSize(bgW.toInt(), bgH.toInt())
    )
  }
}

private fun drawCloudLayerCompose(
  canvas: DrawScope,
  bitmap: ImageBitmap,
  scrollOffset: Float,
  hillShiftX: Float,
  canvasW: Float,
  canvasH: Float,
  scaleFactor: Float,
  yPositionFactor: Float,
  parallaxFactor: Float
) {
  // Scale the cloud texture based on a base tile width of 1024 to span 4x width naturally
  val cloudScale = (canvasW * 1.5f * scaleFactor) / 1024f
  val scaledW = bitmap.width * cloudScale
  val scaledH = bitmap.height * cloudScale
  val startY = canvasH * yPositionFactor

  val fadePercent = 0.15f
  val overlapW = scaledW * fadePercent
  val period = scaledW - overlapW

  // Parallax shift based on home screen swipes (scrolls appropriately slower than hills)
  val swipeShiftX = hillShiftX * parallaxFactor

  // Performance optimization: mod scrollOffset by period to avoid large loops over time
  val wrappedScroll = if (period > 0f) scrollOffset % period else scrollOffset
  var drawX = swipeShiftX + wrappedScroll

  // Wrap drawX to fit in the range [-period, 0] to start tiling
  while (drawX > 0) {
    drawX -= period
  }
  while (drawX < -period) {
    drawX += period
  }

  // Draw tiles overlapping by overlapW (adding period instead of scaledW)
  var tileX = drawX
  while (tileX < canvasW) {
    canvas.drawImage(
      image = bitmap,
      dstOffset = IntOffset(tileX.toInt(), startY.toInt()),
      dstSize = IntSize(scaledW.toInt(), scaledH.toInt())
    )
    tileX += period
  }
}

private fun fadeEdgesAndroid(src: Bitmap, fadePercent: Float): Bitmap {
  val w = src.width
  val h = src.height
  val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
  val canvas = android.graphics.Canvas(result)
  canvas.drawBitmap(src, 0f, 0f, null)

  val maskPaint = android.graphics.Paint().apply {
    isAntiAlias = true
    xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
  }

  val fadeW = w * fadePercent
  maskPaint.shader = android.graphics.LinearGradient(
    0f, 0f, fadeW, 0f,
    0x00FFFFFF, 0xFFFFFFFF.toInt(),
    android.graphics.Shader.TileMode.CLAMP
  )
  canvas.drawRect(0f, 0f, fadeW, h.toFloat(), maskPaint)

  maskPaint.shader = android.graphics.LinearGradient(
    w - fadeW, 0f, w.toFloat(), 0f,
    0xFFFFFFFF.toInt(), 0x00FFFFFF,
    android.graphics.Shader.TileMode.CLAMP
  )
  canvas.drawRect(w - fadeW, 0f, w.toFloat(), h.toFloat(), maskPaint)

  return result
}

private fun triggerApplyLiveWallpaper(context: Context) {
  try {
    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
      putExtra(
        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
        ComponentName(context, BlissWallpaperService::class.java)
      )
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  } catch (e: Exception) {
    // Fallback if specific live wallpaper intent fails
    try {
      val fallbackIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(fallbackIntent)
    } catch (ex: Exception) {
      ex.printStackTrace()
    }
  }
}
