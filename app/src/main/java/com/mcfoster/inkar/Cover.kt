package com.mcfoster.inkar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/** Crops an image to 600 x 800 and returns it as a JPEG data URL (same as the HTML app). */
fun uriToCover(ctx: Context, uri: Uri): String? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 600 && bounds.outHeight / (sample * 2) >= 800) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val src = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        val out = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val r = maxOf(600f / src.width, 800f / src.height)
        val w = src.width * r
        val h = src.height * r
        canvas.drawBitmap(src, null, RectF((600 - w) / 2, (800 - h) / 2, (600 + w) / 2, (800 + h) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
        val bos = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 82, bos)
        "data:image/jpeg;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }
}
