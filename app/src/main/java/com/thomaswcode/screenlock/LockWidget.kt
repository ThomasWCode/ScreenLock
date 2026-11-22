package com.thomaswcode.screenlock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

// Home screen widget: one button that locks the touchscreen and shows whether it is locked (as
// ScreenKeeper's and Phone Relay's toggles). Once locked a finger can't reach it, so only an injected
// tap (adb, Phone Relay) can turn it off again from here; a person unlocks with the two-finger hold
class LockWidget : AppWidgetProvider() {

    companion object {
        private const val ACTION_TOGGLE = "com.thomaswcode.screenlock.WIDGET_TOGGLE"

        // Redraws every placed widget (called whenever the lock or the service changes)
        fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, LockWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, buildViews(context))
        }

        private fun buildViews(context: Context): RemoteViews {
            val connected = LockService.instance != null
            val active = LockService.isLocked
            val state = context.getString(
                when {
                    !connected -> R.string.widget_setup
                    active -> R.string.tile_on
                    else -> R.string.tile_off
                }
            )
            // With the service on, a tap is a broadcast to this widget, which the service acts on in this
            // process. With it off, a tap opens the app, which says how to turn it on
            val tap = if (connected) {
                PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(context, LockWidget::class.java).setAction(ACTION_TOGGLE),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            } else {
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
            return RemoteViews(context.packageName, R.layout.widget_toggle).apply {
                setInt(
                    android.R.id.background,
                    "setBackgroundResource",
                    if (active) R.drawable.widget_background_on else R.drawable.widget_background_off
                )
                setTextViewText(R.id.widget_state, state)
                setContentDescription(android.R.id.background, context.getString(R.string.widget_state, state))
                setOnClickPendingIntent(android.R.id.background, tap)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE) {
            LockService.instance?.toggle() ?: update(context)
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetManager.updateAppWidget(appWidgetIds, buildViews(context))
    }
}
