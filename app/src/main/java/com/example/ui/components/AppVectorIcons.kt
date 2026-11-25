package com.example.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.R
import com.example.catalog.AppCatalog
import com.example.catalog.AppIconType
import com.example.catalog.CatalogApp
import com.example.ui.theme.PStoreTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private object IconBitmapCache {
    val installedCache = LruCache<String, ImageBitmap>(40)
    val remoteCache = LruCache<String, ImageBitmap>(40)

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}

/**
 * Renders the app's icon with priority:
 * 1. P Store self icon (bundled vector)
 * 2. Real installed app icon from PackageManager when installed on device
 * 3. Optional remote icon from `p-store.json` (cached in memory)
 * 4. Tinted brand rounded-square with app initials (or custom vector glyph)
 */
@Composable
fun AppBrandIcon(
    app: CatalogApp,
    size: Dp,
    cornerRadius: Dp = (size.value * 0.28f).dp,
    modifier: Modifier = Modifier
) {
    val colors = PStoreTheme.colors
    val context = LocalContext.current
    val contentDesc = stringResource(R.string.cd_app_icon, app.displayName)
    val shape = RoundedCornerShape(cornerRadius)

    // 1. P Store itself always uses its brand vector icon
    if (app.isSelf || app.icon == AppIconType.P_STORE) {
        val bgColor = Color(if (colors.isDark) app.brandColorDark else app.brandColorLight)
        val glyphColor = if (colors.isDark) colors.darkGlyphOnBrand else colors.lightGlyphOnBrand
        val glyphSize = (size.value * 0.56f).dp
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(bgColor)
        ) {
            AppCatalogGlyph(
                iconType = AppIconType.P_STORE,
                tint = glyphColor,
                contentDescription = contentDesc,
                modifier = Modifier.size(glyphSize)
            )
        }
        return
    }

    // 2. Installed app's real icon from PackageManager
    val installedIcon: ImageBitmap? = remember(app.packageName) {
        if (app.packageName.isBlank()) {
            null
        } else {
            IconBitmapCache.installedCache.get(app.packageName) ?: try {
                val drawable = context.packageManager.getApplicationIcon(app.packageName)
                val bitmap = drawable.toBitmap(width = 168, height = 168).asImageBitmap()
                IconBitmapCache.installedCache.put(app.packageName, bitmap)
                bitmap
            } catch (_: Exception) {
                null
            }
        }
    }

    if (installedIcon != null) {
        Image(
            bitmap = installedIcon,
            contentDescription = contentDesc,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(shape)
        )
        return
    }

    // 3. Optional remote icon from p-store.json
    val iconUrl = app.iconUrl
    var remoteIcon by remember(iconUrl) {
        mutableStateOf(iconUrl?.let { IconBitmapCache.remoteCache.get(it) })
    }

    LaunchedEffect(iconUrl) {
        if (!iconUrl.isNullOrBlank() && remoteIcon == null) {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    val req = Request.Builder()
                        .url(iconUrl)
                        .header("User-Agent", "P-Store-Android-App")
                        .build()
                    IconBitmapCache.httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withContext null
                        val bytes = resp.body?.bytes() ?: return@withContext null
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
                } catch (_: Exception) {
                    null
                }
            }
            if (loaded != null) {
                IconBitmapCache.remoteCache.put(iconUrl, loaded)
                remoteIcon = loaded
            }
        }
    }

    if (remoteIcon != null) {
        Image(
            bitmap = remoteIcon!!,
            contentDescription = contentDesc,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(shape)
        )
        return
    }

    // 4. Fallback: Brand-colored tile with initials (or selected custom vector glyph)
    val bgColor = Color(if (colors.isDark) app.brandColorDark else app.brandColorLight)
    val glyphColor = if (colors.isDark) colors.darkGlyphOnBrand else colors.lightGlyphOnBrand
    val glyphSize = (size.value * 0.56f).dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(bgColor)
    ) {
        if (app.icon == AppIconType.INITIALS) {
            val initials = remember(app.displayName) { AppCatalog.extractInitials(app.displayName) }
            val fontSizeSp = (size.value * 0.36f).coerceIn(12f, 30f).sp
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = fontSizeSp,
                    fontWeight = FontWeight.Bold
                ),
                color = glyphColor,
                maxLines = 1
            )
        } else {
            AppCatalogGlyph(
                iconType = app.icon,
                tint = glyphColor,
                contentDescription = contentDesc,
                modifier = Modifier.size(glyphSize)
            )
        }
    }
}

@Composable
fun AppCatalogGlyph(
    iconType: AppIconType,
    tint: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    if (iconType == AppIconType.P_STORE) {
        Icon(
            painter = painterResource(id = R.drawable.p_store_icon),
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier
        )
        return
    }

    if (iconType == AppIconType.INITIALS) {
        Box(contentAlignment = Alignment.Center, modifier = modifier) {
            Text(
                text = "Aa",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = tint,
                maxLines = 1
            )
        }
        return
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = (minOf(w, h) * 0.095f).coerceAtLeast(2f)

        when (iconType) {
            AppIconType.COIN_DOLLAR -> {
                val radius = minOf(w, h) * 0.43f
                drawCircle(
                    color = tint,
                    radius = radius,
                    center = center,
                    style = Stroke(width = stroke)
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.5f, h * 0.22f),
                    end = Offset(w * 0.5f, h * 0.78f),
                    strokeWidth = stroke * 0.85f,
                    cap = StrokeCap.Round
                )
                val sPath = Path().apply {
                    moveTo(w * 0.62f, h * 0.36f)
                    cubicTo(w * 0.62f, h * 0.28f, w * 0.37f, h * 0.28f, w * 0.37f, h * 0.40f)
                    cubicTo(w * 0.37f, h * 0.51f, w * 0.63f, h * 0.49f, w * 0.63f, h * 0.61f)
                    cubicTo(w * 0.63f, h * 0.72f, w * 0.37f, h * 0.72f, w * 0.37f, h * 0.63f)
                }
                drawPath(
                    path = sPath,
                    color = tint,
                    style = Stroke(width = stroke * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            AppIconType.CHECKBOX -> {
                val inset = minOf(w, h) * 0.12f
                val rectSize = Size(w - inset * 2, h - inset * 2)
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(inset, inset),
                    size = rectSize,
                    cornerRadius = CornerRadius(w * 0.22f, h * 0.22f),
                    style = Stroke(width = stroke)
                )
                val checkPath = Path().apply {
                    moveTo(w * 0.30f, h * 0.52f)
                    lineTo(w * 0.44f, h * 0.66f)
                    lineTo(w * 0.72f, h * 0.36f)
                }
                drawPath(
                    path = checkPath,
                    color = tint,
                    style = Stroke(width = stroke * 1.05f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            AppIconType.FLAME -> {
                val flamePath = Path().apply {
                    moveTo(w * 0.52f, h * 0.10f)
                    cubicTo(w * 0.62f, h * 0.27f, w * 0.82f, h * 0.38f, w * 0.80f, h * 0.61f)
                    cubicTo(w * 0.78f, h * 0.80f, w * 0.64f, h * 0.90f, w * 0.50f, h * 0.90f)
                    cubicTo(w * 0.33f, h * 0.90f, w * 0.20f, h * 0.78f, w * 0.20f, h * 0.59f)
                    cubicTo(w * 0.20f, h * 0.44f, w * 0.30f, h * 0.35f, w * 0.34f, h * 0.26f)
                    cubicTo(w * 0.37f, h * 0.35f, w * 0.42f, h * 0.38f, w * 0.44f, h * 0.35f)
                    cubicTo(w * 0.47f, h * 0.28f, w * 0.45f, h * 0.17f, w * 0.52f, h * 0.10f)
                    close()
                }
                drawPath(path = flamePath, color = tint)
            }

            AppIconType.PEN_NIB -> {
                val nibPath = Path().apply {
                    moveTo(w * 0.50f, h * 0.10f)
                    lineTo(w * 0.82f, h * 0.52f)
                    lineTo(w * 0.67f, h * 0.76f)
                    lineTo(w * 0.33f, h * 0.76f)
                    lineTo(w * 0.18f, h * 0.52f)
                    close()
                }
                drawPath(
                    path = nibPath,
                    color = tint,
                    style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.30f, h * 0.88f),
                    end = Offset(w * 0.70f, h * 0.88f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.50f, h * 0.12f),
                    end = Offset(w * 0.50f, h * 0.48f),
                    strokeWidth = stroke * 0.8f,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = tint,
                    radius = minOf(w, h) * 0.085f,
                    center = Offset(w * 0.50f, h * 0.52f)
                )
            }

            AppIconType.DIE_FIVE -> {
                val inset = minOf(w, h) * 0.10f
                val rectSize = Size(w - inset * 2, h - inset * 2)
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(inset, inset),
                    size = rectSize,
                    cornerRadius = CornerRadius(w * 0.22f, h * 0.22f),
                    style = Stroke(width = stroke)
                )
                val pipRadius = minOf(w, h) * 0.082f
                val positions = listOf(
                    Offset(w * 0.32f, h * 0.32f),
                    Offset(w * 0.68f, h * 0.32f),
                    Offset(w * 0.50f, h * 0.50f),
                    Offset(w * 0.32f, h * 0.68f),
                    Offset(w * 0.68f, h * 0.68f)
                )
                positions.forEach { pos ->
                    drawCircle(color = tint, radius = pipRadius, center = pos)
                }
            }

            AppIconType.APPS_GRID -> {
                val gap = minOf(w, h) * 0.12f
                val inset = minOf(w, h) * 0.14f
                val tileSize = (minOf(w, h) - inset * 2 - gap) / 2f
                val r = CornerRadius(tileSize * 0.28f, tileSize * 0.28f)
                val offsets = listOf(
                    Offset(inset, inset),
                    Offset(inset + tileSize + gap, inset),
                    Offset(inset, inset + tileSize + gap),
                    Offset(inset + tileSize + gap, inset + tileSize + gap)
                )
                offsets.forEach { topLeft ->
                    drawRoundRect(
                        color = tint,
                        topLeft = topLeft,
                        size = Size(tileSize, tileSize),
                        cornerRadius = r,
                        style = Stroke(width = stroke * 0.9f)
                    )
                }
            }

            AppIconType.STAR -> {
                val starPath = Path().apply {
                    moveTo(w * 0.50f, h * 0.12f)
                    lineTo(w * 0.61f, h * 0.37f)
                    lineTo(w * 0.88f, h * 0.40f)
                    lineTo(w * 0.68f, h * 0.58f)
                    lineTo(w * 0.74f, h * 0.85f)
                    lineTo(w * 0.50f, h * 0.71f)
                    lineTo(w * 0.26f, h * 0.85f)
                    lineTo(w * 0.32f, h * 0.58f)
                    lineTo(w * 0.12f, h * 0.40f)
                    lineTo(w * 0.39f, h * 0.37f)
                    close()
                }
                drawPath(
                    path = starPath,
                    color = tint,
                    style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            AppIconType.BOLT -> {
                val boltPath = Path().apply {
                    moveTo(w * 0.56f, h * 0.10f)
                    lineTo(w * 0.24f, h * 0.54f)
                    lineTo(w * 0.49f, h * 0.54f)
                    lineTo(w * 0.44f, h * 0.90f)
                    lineTo(w * 0.76f, h * 0.46f)
                    lineTo(w * 0.51f, h * 0.46f)
                    close()
                }
                drawPath(path = boltPath, color = tint)
            }

            AppIconType.CODE -> {
                val leftBracket = Path().apply {
                    moveTo(w * 0.36f, h * 0.28f)
                    lineTo(w * 0.15f, h * 0.50f)
                    lineTo(w * 0.36f, h * 0.72f)
                }
                val rightBracket = Path().apply {
                    moveTo(w * 0.64f, h * 0.28f)
                    lineTo(w * 0.85f, h * 0.50f)
                    lineTo(w * 0.64f, h * 0.72f)
                }
                drawPath(
                    path = leftBracket,
                    color = tint,
                    style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    path = rightBracket,
                    color = tint,
                    style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.56f, h * 0.24f),
                    end = Offset(w * 0.44f, h * 0.76f),
                    strokeWidth = stroke * 0.9f,
                    cap = StrokeCap.Round
                )
            }

            AppIconType.P_STORE,
            AppIconType.INITIALS -> Unit
        }
    }
}
