package io.github.plvzfqrit.lockgroove.media

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Android only shares what's playing with notification listeners, so this is how
 * the app sees the music. It does nothing itself: media notifications just ask the
 * [MediaSessionWatcher] to take another look.
 */
class MediaListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        MediaSessionWatcher.requestRefresh(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null && isMediaNotification(sbn)) MediaSessionWatcher.requestRefresh(this)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn != null && isMediaNotification(sbn)) MediaSessionWatcher.requestRefresh(this)
    }

    private fun isMediaNotification(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification ?: return false
        return n.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true ||
                n.category == Notification.CATEGORY_TRANSPORT
    }
}
