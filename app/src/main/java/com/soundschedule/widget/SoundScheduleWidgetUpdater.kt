package com.soundschedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.soundschedule.MainActivity
import com.soundschedule.R
import com.soundschedule.audio.RingerModeManager
import com.soundschedule.data.ScheduleRepository
import com.soundschedule.data.ScheduleRepositoryImpl
import com.soundschedule.data.SoundMode
import com.soundschedule.utils.AppLog
import com.soundschedule.utils.TimeUtils
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Immutable presentation model representing widget display state.
 * Strictly adheres to clean monochrome hierarchy:
 * CURRENT schedule & interval, NEXT schedule & interval.
 */
data class SoundScheduleWidgetModel(
    val isMasterEnabled: Boolean,
    val currentMode: SoundMode,
    val hasCurrent: Boolean,
    val currentTitle: String,
    val currentTimeRange: String?,
    val currentLabel: String?,
    val hasNext: Boolean,
    val nextTitle: String,
    val nextTimeRange: String?,
    val nextLabel: String?
)

object SoundScheduleWidgetUpdater {

    private const val TAG = "WidgetUpdater"

    fun formatTimeRange(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int): String {
        return String.format(Locale.US, "%02d:%02d – %02d:%02d", startHour, startMinute, endHour, endMinute)
    }

    /**
     * Pure function calculating the display model for the widget.
     * Fully decoupled for 100% deterministic unit testing.
     */
    fun buildWidgetModel(
        repository: ScheduleRepository,
        ringerModeManager: RingerModeManager,
        now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
    ): SoundScheduleWidgetModel {
        val currentMode = ringerModeManager.getCurrentMode()
        val isMasterEnabled = repository.isMasterEnabledSync()

        val schedules = repository.getAllSchedulesSync().filter { it.enabled && it.daysOfWeek.isNotEmpty() }
        val activeSession = schedules.firstOrNull { TimeUtils.isSessionActive(it, now) }

        // 1. Current Session State (Part 10, 22)
        val hasCurrent = activeSession != null
        val currentTitle: String
        val currentTimeRange: String?
        val currentLabel: String?

        if (activeSession != null) {
            currentTitle = activeSession.mode.displayName
            currentTimeRange = formatTimeRange(
                activeSession.startHour,
                activeSession.startMinute,
                activeSession.endHour,
                activeSession.endMinute
            )
            currentLabel = activeSession.label.ifBlank { null }
        } else {
            currentTitle = "No active schedule"
            currentTimeRange = null
            currentLabel = null
        }

        // 2. Next Upcoming Schedule State (Part 11, 23)
        val upcomingOccurrences = schedules.mapNotNull { event ->
            val checkTime = if (activeSession?.id == event.id) {
                now.plusMinutes(1)
            } else {
                now
            }
            val nextTime = TimeUtils.calculateNextOccurrence(event, checkTime)
            if (nextTime != null) Pair(event, nextTime) else null
        }

        val nextPair = upcomingOccurrences.minByOrNull { it.second }
        val hasNext = nextPair != null
        val nextTitle: String
        val nextTimeRange: String?
        val nextLabel: String?

        if (nextPair != null) {
            val (nextEvent, _) = nextPair
            nextTitle = nextEvent.mode.displayName
            nextTimeRange = formatTimeRange(
                nextEvent.startHour,
                nextEvent.startMinute,
                nextEvent.endHour,
                nextEvent.endMinute
            )
            nextLabel = nextEvent.label.ifBlank { null }
        } else {
            nextTitle = "No upcoming schedule"
            nextTimeRange = null
            nextLabel = null
        }

        return SoundScheduleWidgetModel(
            isMasterEnabled = isMasterEnabled,
            currentMode = currentMode,
            hasCurrent = hasCurrent,
            currentTitle = currentTitle,
            currentTimeRange = currentTimeRange,
            currentLabel = currentLabel,
            hasNext = hasNext,
            nextTitle = nextTitle,
            nextTimeRange = nextTimeRange,
            nextLabel = nextLabel
        )
    }

    /**
     * Builds and binds the Android [RemoteViews] for the monochrome widget layout.
     * When [isCompact] is true, renders [R.layout.widget_sound_schedule_compact] (CURRENT only).
     * When [isCompact] is false, renders [R.layout.widget_sound_schedule] (CURRENT + NEXT).
     */
    fun buildRemoteViews(
        context: Context,
        model: SoundScheduleWidgetModel,
        isCompact: Boolean = false
    ): RemoteViews {
        val layoutId = if (isCompact) {
            R.layout.widget_sound_schedule_compact
        } else {
            R.layout.widget_sound_schedule
        }
        val views = RemoteViews(context.packageName, layoutId)

        // 1. Master Schedule status badge (Part 21)
        if (model.isMasterEnabled) {
            views.setTextViewText(R.id.widget_master_badge, "● ON")
            views.setInt(R.id.widget_master_badge, "setBackgroundResource", R.drawable.widget_badge_master_on)
        } else {
            views.setTextViewText(R.id.widget_master_badge, "MASTER OFF")
            views.setInt(R.id.widget_master_badge, "setBackgroundResource", R.drawable.widget_badge_master_off)
        }

        val hasCurrent = model.hasCurrent
        val hasNext = model.hasNext

        if (isCompact) {
            // Compact Layout (Parts 7, 12, 18, 19, 20):
            // Shows ONLY CURRENT if active. If no CURRENT, shows NEXT. If neither, shows NO SCHEDULES.
            if (hasCurrent) {
                views.setViewVisibility(R.id.widget_single_section, View.VISIBLE)
                views.setViewVisibility(R.id.widget_empty_state, View.GONE)
                views.setTextViewText(R.id.widget_single_header, "CURRENT")
                views.setTextViewText(R.id.widget_single_time, model.currentTimeRange ?: "")
                views.setTextViewText(R.id.widget_single_mode, model.currentTitle)
                if (model.currentLabel != null) {
                    views.setViewVisibility(R.id.widget_single_label, View.VISIBLE)
                    views.setTextViewText(R.id.widget_single_label, model.currentLabel)
                } else {
                    views.setViewVisibility(R.id.widget_single_label, View.GONE)
                }
            } else if (hasNext) {
                views.setViewVisibility(R.id.widget_single_section, View.VISIBLE)
                views.setViewVisibility(R.id.widget_empty_state, View.GONE)
                views.setTextViewText(R.id.widget_single_header, "NEXT")
                views.setTextViewText(R.id.widget_single_time, model.nextTimeRange ?: "")
                views.setTextViewText(R.id.widget_single_mode, model.nextTitle)
                if (model.nextLabel != null) {
                    views.setViewVisibility(R.id.widget_single_label, View.VISIBLE)
                    views.setTextViewText(R.id.widget_single_label, model.nextLabel)
                } else {
                    views.setViewVisibility(R.id.widget_single_label, View.GONE)
                }
            } else {
                views.setViewVisibility(R.id.widget_single_section, View.GONE)
                views.setViewVisibility(R.id.widget_empty_state, View.VISIBLE)
                views.setTextViewText(R.id.widget_empty_text, if (!model.isMasterEnabled) "MASTER OFF" else "NO SCHEDULES")
            }
        } else {
            // Expanded Layout (Parts 8, 14, 15, 16, 17):
            // Shows CURRENT and NEXT when space permits.
            if (!hasCurrent && !hasNext) {
                views.setViewVisibility(R.id.widget_current_section, View.GONE)
                views.setViewVisibility(R.id.widget_divider_section, View.GONE)
                views.setViewVisibility(R.id.widget_next_section, View.GONE)
                views.setViewVisibility(R.id.widget_empty_state, View.VISIBLE)
                views.setTextViewText(R.id.widget_empty_text, if (!model.isMasterEnabled) "MASTER OFF" else "NO SCHEDULES")
            } else {
                views.setViewVisibility(R.id.widget_empty_state, View.GONE)

                // Current Section
                if (hasCurrent) {
                    views.setViewVisibility(R.id.widget_current_section, View.VISIBLE)
                    if (model.currentTimeRange != null) {
                        views.setViewVisibility(R.id.widget_current_time, View.VISIBLE)
                        views.setTextViewText(R.id.widget_current_time, model.currentTimeRange)
                    } else {
                        views.setViewVisibility(R.id.widget_current_time, View.GONE)
                    }
                    views.setTextViewText(R.id.widget_current_mode, model.currentTitle)
                    if (model.currentLabel != null) {
                        views.setViewVisibility(R.id.widget_current_label, View.VISIBLE)
                        views.setTextViewText(R.id.widget_current_label, model.currentLabel)
                    } else {
                        views.setViewVisibility(R.id.widget_current_label, View.GONE)
                    }
                } else {
                    views.setViewVisibility(R.id.widget_current_section, View.GONE)
                }

                // Divider only if BOTH current and next exist
                if (hasCurrent && hasNext) {
                    views.setViewVisibility(R.id.widget_divider_section, View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.widget_divider_section, View.GONE)
                }

                // Next Section
                if (hasNext) {
                    views.setViewVisibility(R.id.widget_next_section, View.VISIBLE)
                    if (model.nextTimeRange != null) {
                        views.setViewVisibility(R.id.widget_next_time, View.VISIBLE)
                        views.setTextViewText(R.id.widget_next_time, model.nextTimeRange)
                    } else {
                        views.setViewVisibility(R.id.widget_next_time, View.GONE)
                    }
                    views.setTextViewText(R.id.widget_next_mode, model.nextTitle)
                    if (model.nextLabel != null) {
                        views.setViewVisibility(R.id.widget_next_label, View.VISIBLE)
                        views.setTextViewText(R.id.widget_next_label, model.nextLabel)
                    } else {
                        views.setViewVisibility(R.id.widget_next_label, View.GONE)
                    }
                } else {
                    views.setViewVisibility(R.id.widget_next_section, View.GONE)
                }
            }
        }

        // One tap anywhere on widget opens MainActivity (Part 15, 24, 25)
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        return views
    }

    /**
     * Builds responsive RemoteViews for Android 12+ (API 31+) supporting automatic size transitions.
     */
    fun buildResponsiveRemoteViews(context: Context, model: SoundScheduleWidgetModel): RemoteViews {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val compactViews = buildRemoteViews(context, model, isCompact = true)
            val expandedViews = buildRemoteViews(context, model, isCompact = false)
            RemoteViews(
                mapOf(
                    SizeF(140f, 70f) to compactViews,
                    SizeF(140f, 115f) to expandedViews
                )
            )
        } else {
            buildRemoteViews(context, model, isCompact = false)
        }
    }

    /**
     * Updates a single widget instance with structured before/after logging (Part 3, 33).
     */
    fun updateWidgetInstance(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        options: Bundle? = null
    ) {
        try {
            val opt = options ?: appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWidth = opt?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
            val minHeight = opt?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
            val width = if (minWidth > 0) minWidth else 180
            val height = if (minHeight > 0) minHeight else 90

            val repository = ScheduleRepositoryImpl.getInstance(context)
            val ringerModeManager = RingerModeManager.getInstance(context)
            val model = buildWidgetModel(repository, ringerModeManager)

            val currentStr = if (model.hasCurrent) "${model.currentTimeRange} ${model.currentTitle}" else "None"
            val nextStr = if (model.hasNext) "${model.nextTimeRange} ${model.nextTitle}" else "None"

            // Structured logging conforming exactly to Part 3 & 33
            AppLog.i(
                "SoundScheduleWidget",
                """
                update started
                widgetId = $appWidgetId
                width = $width
                height = $height
                current = $currentStr
                next = $nextStr
                update completed
                """.trimIndent()
            )

            val remoteViews = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                buildResponsiveRemoteViews(context, model)
            } else {
                val isCompact = height < 115
                buildRemoteViews(context, model, isCompact = isCompact)
            }

            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        } catch (e: Exception) {
            AppLog.e("SoundScheduleWidget", "Widget update failed for widgetId $appWidgetId: ${e.message}", e)
        }
    }

    /**
     * Updates all active SoundSchedule home-screen widgets via event-driven broadcast/call.
     * Zero continuous polling, zero foreground service.
     */
    fun updateAllWidgets(context: Context) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val componentName = ComponentName(context, SoundScheduleWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)

            if (widgetIds.isEmpty()) {
                AppLog.d(TAG, "No active home-screen widgets found to update.")
                return
            }

            val repository = ScheduleRepositoryImpl.getInstance(context)
            val ringerModeManager = RingerModeManager.getInstance(context)
            val model = buildWidgetModel(repository, ringerModeManager)
            val responsiveViews = buildResponsiveRemoteViews(context, model)

            for (widgetId in widgetIds) {
                updateWidgetInstance(context, appWidgetManager, widgetId)
            }

            // Also update by component name for any un-indexed instances
            appWidgetManager.updateAppWidget(componentName, responsiveViews)
            AppLog.i(TAG, "Updated ${widgetIds.size} widget instances with state: master=${model.isMasterEnabled}, hasCurrent=${model.hasCurrent}, hasNext=${model.hasNext}")
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to update widgets: ${e.message}", e)
        }
    }
}
