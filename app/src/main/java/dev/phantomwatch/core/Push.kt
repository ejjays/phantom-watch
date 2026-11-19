package dev.phantomwatch.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.phantomwatch.MainActivity
import dev.phantomwatch.R
import dev.phantomwatch.ui.Cyan

object Push {
    const val CHANNEL = "releases"
    const val TOPIC = "releases"
    private const val ID = 7

    fun init(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.push_channel_releases),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.push_channel_releases_note)
            },
        )
        runCatching { FirebaseMessaging.getInstance().register() }
            .onSuccess { task ->
                task.addOnCompleteListener {
                    AppLog.d("push register ${it.isSuccessful} ${it.exception?.message.orEmpty()}")
                }
            }
        runCatching { FirebaseMessaging.getInstance().subscribeToTopic(TOPIC) }
            .onSuccess { task -> task.addOnCompleteListener { AppLog.d("push topic ${it.isSuccessful}") } }
    }

    fun show(context: Context, title: String, body: String) {
        val open = Intent(context, MainActivity::class.java)
            .putExtra(PushOpen.EXTRA, PushOpen.ABOUT)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(
            context,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val note = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setColor(Cyan.toArgb())
            .setLargeIcon(PushArt.thumb(context))
            .setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(PushArt.banner(context))
                    .bigLargeIcon(null as Bitmap?)
                    .setBigContentTitle(title)
                    .setSummaryText(body),
            )
            .setContentIntent(tap)
            .addAction(0, context.getString(R.string.push_open), tap)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID, note) }
    }
}

// tap lands in activity, tab switch happens in ui
object PushOpen {
    const val EXTRA = "pw_open"
    const val ABOUT = "about"

    var about by mutableStateOf(false)
        private set

    fun take(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA) != ABOUT) return
        about = true
        intent.removeExtra(EXTRA)
    }

    fun consume() {
        about = false
    }
}

class PushService : FirebaseMessagingService() {
    override fun onRegistered(token: String) {
        AppLog.d("push registered")
        runCatching { FirebaseMessaging.getInstance().subscribeToTopic(Push.TOPIC) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"].orEmpty().ifBlank {
            message.notification?.title ?: getString(R.string.push_release_title)
        }
        val body = data["body"].orEmpty().ifBlank {
            message.notification?.body.orEmpty().ifBlank { getString(R.string.push_release_body) }
        }
        Push.show(this, title, body)
    }
}
