package com.sortr.app.media

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.sortr.app.R

/** One photo or video to share: where it is and what type it is. */
class ShareItem(val uri: Uri, val mimeType: String)

/**
 * Opens Android's share menu for photos and videos. Sortr itself sends nothing anywhere; the
 * user picks an app, which gets read access to just these items.
 */
object Sharing {

    fun share(context: Context, items: List<ShareItem>) {
        if (items.isEmpty()) return
        val uris = ArrayList(items.map { it.uri })
        val type = commonType(items.map { it.mimeType })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }.apply {
            setType(type)
            // The clip carries the read grant to the app the user picks.
            clipData = ClipData.newRawUri(null, uris.first()).also { clip ->
                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(
                Intent.createChooser(intent, context.getString(R.string.share_title)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        } catch (e: Exception) {
            Log.w("Sharing", "No app to share with", e)
        }
    }

    fun shareUris(context: Context, uris: List<String>, mimeType: String = "image/*") =
        share(context, uris.map { ShareItem(it.toUri(), mimeType) })

    /** The exact type if all match, "image/" + any for mixed photos, any type for photos and videos. */
    private fun commonType(types: List<String>): String {
        val distinct = types.distinct()
        if (distinct.size == 1) return distinct.first()
        val families = distinct.map { it.substringBefore('/') }.distinct()
        return if (families.size == 1) "${families.first()}/*" else "*/*"
    }
}
