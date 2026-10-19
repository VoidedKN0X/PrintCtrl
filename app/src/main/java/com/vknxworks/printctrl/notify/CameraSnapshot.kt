package com.vknxworks.printctrl.notify

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.net.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/** One picture from a printer's camera, through Bambuddy. Null when there's no camera or no access. */
object CameraSnapshot {
    /** [sample] shrinks the picture: 2 is half the width and height, 4 a quarter. */
    suspend fun fetch(context: Context, printerId: Int, sample: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val api = context.container.bambuddy.value ?: return@runCatching null
            val token = api.cameraToken()
            api.conn.client.newCall(Request.Builder().url(api.cameraSnapshotUrl(printerId, token)).build()).await().use { r ->
                if (!r.isSuccessful) return@use null
                val bytes = r.body?.bytes() ?: return@use null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }.getOrNull()
    }
}
