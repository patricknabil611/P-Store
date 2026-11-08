package com.example.ui.components

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppIconView(
    appName: String,
    packageName: String?,
    isInstalled: Boolean,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val installedIconBitmap: ImageBitmap? = remember(packageName, isInstalled) {
        if (isInstalled && packageName != null) {
            loadAppIcon(context, packageName)
        } else {
            null
        }
    }

    if (installedIconBitmap != null) {
        Image(
            bitmap = installedIconBitmap,
            contentDescription = "$appName icon",
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp))
        )
    } else {
        // Colored circle with first letter or meaningful initial
        val initial = getInitials(appName)
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Text(
                text = initial,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun loadAppIcon(context: Context, packageName: String): ImageBitmap? {
    return try {
        val pm = context.packageManager
        val drawable = pm.getApplicationIcon(packageName)
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

private fun getInitials(name: String): String {
    val parts = name.trim().split(" ")
    return if (parts.size >= 2) {
        // e.g. "P Money" -> "M" or "PM"
        "${parts[0].firstOrNull() ?: 'P'}${parts[1].firstOrNull() ?: ""}".take(2).uppercase()
    } else {
        name.firstOrNull()?.toString()?.uppercase() ?: "P"
    }
}
