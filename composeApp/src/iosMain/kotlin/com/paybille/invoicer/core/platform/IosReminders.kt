package com.paybille.invoicer.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** iOS guarda como máximo 64 avisos pendientes por app. */
private const val IOS_PENDING_LIMIT = 60

class IosReminderScheduler : ReminderScheduler {

    override fun replaceAll(reminders: List<Reminder>) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removeAllPendingNotificationRequests()
        val nowMs = NSDate().timeIntervalSince1970 * 1000
        reminders
            .filter { it.atEpochMillis > nowMs }
            .sortedBy { it.atEpochMillis }
            .take(IOS_PENDING_LIMIT)
            .forEach { reminder ->
                val content = UNMutableNotificationContent()
                content.setTitle(reminder.title)
                content.setBody(reminder.body)
                content.setSound(UNNotificationSound.defaultSound())
                content.setUserInfo(mapOf<Any?, Any?>(SALE_KEY to (reminder.saleId ?: -1)))
                val seconds = (reminder.atEpochMillis - nowMs) / 1000.0
                val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
                val request = UNNotificationRequest.requestWithIdentifier(reminder.id, content, trigger)
                center.addNotificationRequest(request, withCompletionHandler = null)
            }
    }

    override fun cancelAll() {
        UNUserNotificationCenter.currentNotificationCenter().removeAllPendingNotificationRequests()
    }
}

private const val SALE_KEY = "saleId"

/**
 * Recibe el toque en un aviso y deja la venta en [NotificationRouter]. Se registra en
 * `MainViewController`, que guarda la única instancia en una propiedad de nivel superior: el
 * centro la guarda como `weak`. Es `class` y no `object`: Kotlin/Native no admite un `object`
 * que herede de una clase Obj-C (el enlazador del framework revienta).
 */
class IosNotificationDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        val value = didReceiveNotificationResponse.notification.request.content.userInfo[SALE_KEY]
        val saleId = (value as? Number)?.toInt() ?: -1
        if (saleId > 0) NotificationRouter.open(saleId)
        withCompletionHandler()
    }
}

@Composable
actual fun NotificationPermissionRequest(onResult: (granted: Boolean) -> Unit) {
    LaunchedEffect(Unit) {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { granted, _ ->
            dispatch_async(dispatch_get_main_queue()) { onResult(granted) }
        }
    }
}
