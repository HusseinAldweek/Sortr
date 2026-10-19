package com.sortr.app.ui.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

enum class MediaAccess {
    /** Every photo is visible. */
    FULL,

    /** Android 14+: the user picked "Select photos", so only those are visible. */
    PARTIAL,
    DENIED,
}

object MediaPermissions {

    /**
     * What to ask for: photos and videos in one prompt. On Android 14+ adding the "user
     * selected" permission shows the "Allow all / Select photos" choice.
     */
    val requested: Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /**
     * False when photos are allowed but videos aren't, e.g. access granted before Sortr
     * handled videos. "Select photos" access covers both, since the user picks items themselves.
     * Android also reports the "selected" permission as granted alongside full photo access,
     * so it only counts when full photo access is missing.
     */
    fun hasVideoAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (granted(Manifest.permission.READ_MEDIA_VIDEO)) return true
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !granted(Manifest.permission.READ_MEDIA_IMAGES) &&
            granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    }

    fun currentAccess(context: Context): MediaAccess {
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> when {
                granted(Manifest.permission.READ_MEDIA_IMAGES) -> MediaAccess.FULL
                granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> MediaAccess.PARTIAL
                else -> MediaAccess.DENIED
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                if (granted(Manifest.permission.READ_MEDIA_IMAGES)) MediaAccess.FULL else MediaAccess.DENIED
            else ->
                if (granted(Manifest.permission.READ_EXTERNAL_STORAGE)) MediaAccess.FULL else MediaAccess.DENIED
        }
    }

    fun appSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
