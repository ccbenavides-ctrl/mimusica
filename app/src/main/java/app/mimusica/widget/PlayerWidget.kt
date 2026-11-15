package app.mimusica.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.media3.session.MediaButtonReceiver
import app.mimusica.MainActivity
import app.mimusica.R

/** Widget de la pantalla de inicio: canción actual + anterior / reproducir / siguiente. */
class PlayerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val views = buildViews(
            context,
            prefs.getString(KEY_TITLE, null),
            prefs.getString(KEY_ARTIST, null),
            // Al reiniciar el teléfono nada está sonando.
            isPlaying = false,
        )
        appWidgetManager.updateAppWidget(appWidgetIds, views)
    }

    companion object {
        private const val PREFS = "widget"
        private const val KEY_TITLE = "titulo"
        private const val KEY_ARTIST = "artista"

        /** Llamado por el servicio de reproducción cada vez que cambia la canción o el estado. */
        fun update(context: Context, title: String?, artist: String?, isPlaying: Boolean) {
            runCatching {
                val app = context.applicationContext
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_TITLE, title)
                    .putString(KEY_ARTIST, artist)
                    .apply()
                val manager = AppWidgetManager.getInstance(app)
                val ids = manager.getAppWidgetIds(ComponentName(app, PlayerWidget::class.java))
                if (ids.isNotEmpty()) manager.updateAppWidget(ids, buildViews(app, title, artist, isPlaying))
            }
        }

        private fun buildViews(context: Context, title: String?, artist: String?, isPlaying: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_player)
            views.setTextViewText(R.id.widget_title, title?.takeIf { it.isNotBlank() } ?: "S")
            views.setTextViewText(R.id.widget_artist, artist?.takeIf { it.isNotBlank() } ?: "Toca para abrir")
            views.setImageViewResource(R.id.widget_play, if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
            views.setContentDescription(R.id.widget_play, if (isPlaying) "Pausar" else "Reproducir")

            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            views.setOnClickPendingIntent(R.id.widget_prev, mediaButton(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
            views.setOnClickPendingIntent(R.id.widget_play, mediaButton(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.widget_next, mediaButton(context, KeyEvent.KEYCODE_MEDIA_NEXT))
            return views
        }

        /** Los botones envían una "tecla multimedia": Media3 la entrega al servicio (y lo arranca si hace falta). */
        private fun mediaButton(context: Context, keyCode: Int): PendingIntent {
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
                .setComponent(ComponentName(context, MediaButtonReceiver::class.java))
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            return PendingIntent.getBroadcast(
                context, keyCode, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
