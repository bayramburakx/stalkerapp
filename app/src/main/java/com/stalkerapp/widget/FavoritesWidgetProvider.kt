package com.stalkerapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.stalkerapp.MainActivity
import com.stalkerapp.R
import com.stalkerapp.data.Store
import com.stalkerapp.util.L10n

/**
 * Ana ekran widget'ı: favori kanalları listeler. Bir kanala dokununca uygulama
 * açılır ve o kanal oynatılır (`play_channel_id` ekstrası ile).
 */
class FavoritesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    private fun buildViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_favorites)
        val favs = runCatching { Store(context).favoriteChannels().take(6) }.getOrDefault(emptyList())
        val lang = runCatching { Store(context).settings().language }.getOrDefault("tr")
        views.setTextViewText(
            R.id.widget_header,
            "Portio — ${L10n.t(lang, "Favori Kanallar")} (${favs.size})"
        )
        views.removeAllViews(R.id.widget_rows)
        favs.forEach { ch ->
            val row = RemoteViews(context.packageName, R.layout.widget_channel_row)
            val number = if (ch.number > 0) "${ch.number}  " else ""
            row.setTextViewText(R.id.widget_row_text, "$number${ch.name}")
            val pi = playChannelIntent(context, ch.id)
            row.setOnClickPendingIntent(R.id.widget_row_text, pi)
            views.addView(R.id.widget_rows, row)
        }
        return views
    }

    private fun playChannelIntent(context: Context, channelId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_PLAY_CHANNEL, channelId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            ("widget_$channelId").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val EXTRA_PLAY_CHANNEL = "play_channel_id"

        /** Favoriler değişince widget'ı yenile. */
        fun notifyChanged(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, FavoritesWidgetProvider::class.java))
                if (ids.isEmpty()) return
                val intent = Intent(context, FavoritesWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
