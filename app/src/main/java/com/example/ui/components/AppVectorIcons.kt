package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.catalog.AppIconType
import com.example.catalog.CatalogApp
import com.example.ui.theme.PStoreTheme

/**
 * Brand-colored rounded square badge with the catalog app's custom vector icon.
 * In Dark Mode ("Midnight Dock"), uses brandColorDark with dark glyph (#0D1017).
 * In Light Mode ("Clean Shelf"), uses brandColorLight with white glyph.
 */
@Composable
fun AppBrandIcon(
    app: CatalogApp,
    size: Dp,
    cornerRadius: Dp = (size.value * 0.28f).dp,
    modifier: Modifier = Modifier
) {
    val colors = PStoreTheme.colors
    val bgColor = Color(if (colors.isDark) app.brandColorDark else app.brandColorLight)
    val glyphColor = if (colors.isDark) colors.darkGlyphOnBrand else colors.lightGlyphOnBrand
    val glyphSize = (size.value * 0.56f).dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgColor)
    ) {
        AppCatalogGlyph(
            iconType = app.icon,
            tint = glyphColor,
            contentDescription = stringResource(R.string.cd_app_icon, app.displayName),
            modifier = Modifier.size(glyphSize)
        )
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

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = (minOf(w, h) * 0.095f).coerceAtLeast(2f)

        when (iconType) {
            AppIconType.COIN_DOLLAR -> {
                // Coin circle
                val radius = minOf(w, h) * 0.43f
                drawCircle(
                    color = tint,
                    radius = radius,
                    center = center,
                    style = Stroke(width = stroke)
                )
                // Vertical dollar bar
                drawLine(
                    color = tint,
                    start = Offset(w * 0.5f, h * 0.22f),
                    end = Offset(w * 0.5f, h * 0.78f),
                    strokeWidth = stroke * 0.85f,
                    cap = StrokeCap.Round
                )
                // S curve
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
                // Sleek fountain pen nib pointing top-right / top
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
                // Base collar
                drawLine(
                    color = tint,
                    start = Offset(w * 0.30f, h * 0.88f),
                    end = Offset(w * 0.70f, h * 0.88f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                // Slit from tip to center hole
                drawLine(
                    color = tint,
                    start = Offset(w * 0.50f, h * 0.12f),
                    end = Offset(w * 0.50f, h * 0.48f),
                    strokeWidth = stroke * 0.8f,
                    cap = StrokeCap.Round
                )
                // Center breather hole
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

            AppIconType.P_STORE -> Unit
        }
    }
}
