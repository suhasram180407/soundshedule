package com.soundschedule.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import com.soundschedule.utils.AppLog

const val ACTION_UPDATE_WIDGET = "com.soundschedule.ACTION_UPDATE_WIDGET"

/**
 * AppWidgetProvider implementing the SoundSchedule home-screen widget.
 * Responds to system widget updates, audio ringer mode changes, resize events, and app events.
 */
class SoundScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        AppLog.i(TAG, "onUpdate called for ${appWidgetIds.size} widgets.")
        for (id in appWidgetIds) {
            SoundScheduleWidgetUpdater.updateWidgetInstance(context, appWidgetManager, id)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        AppLog.i(TAG, "onAppWidgetOptionsChanged for widgetId $appWidgetId")
        SoundScheduleWidgetUpdater.updateWidgetInstance(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        AppLog.d(TAG, "onReceive triggered with action: $action")

        when (action) {
            AudioManager.RINGER_MODE_CHANGED_ACTION,
            ACTION_UPDATE_WIDGET,
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED -> {
                SoundScheduleWidgetUpdater.updateAllWidgets(context)
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        AppLog.i(TAG, "SoundSchedule widget enabled on home screen.")
        SoundScheduleWidgetUpdater.updateAllWidgets(context)
    }

    companion object {
        private const val TAG = "WidgetProvider"
    }
}
