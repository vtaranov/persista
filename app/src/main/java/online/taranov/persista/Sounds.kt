package online.taranov.persista

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build

object Sounds {
    fun title(ctx: Context, sound: String?): String =
        sound?.let { runCatching { RingtoneManager.getRingtone(ctx, Uri.parse(it))?.getTitle(ctx) }.getOrNull() }
            ?: ctx.getString(R.string.sound_default)

    fun picker(current: String?) = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
            current?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))

    // Null/absent/malformed results are not a request for the hidden "Silent" option.
    data class Picked(val sound: String?)
    fun picked(data: Intent?): Picked? = runCatching {
        if (data == null) return null
        val uri = if (Build.VERSION.SDK_INT >= 33)
            data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        else @Suppress("DEPRECATION") data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (uri == null) return null
        if (uri.scheme != "content" || uri.authority.isNullOrBlank() || uri.path.isNullOrBlank()) return null
        Picked(uri.takeUnless { RingtoneManager.isDefault(it) }?.toString())
    }.getOrNull()
}
