package com.soundschedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.soundschedule.audio.RingerModeManager
import com.soundschedule.data.ScheduleRepositoryImpl
import com.soundschedule.scheduler.AlarmScheduler
import com.soundschedule.theme.DarkBackground
import com.soundschedule.theme.SoundScheduleTheme
import com.soundschedule.ui.MainScheduleScreen
import com.soundschedule.ui.MainScheduleViewModel
import com.soundschedule.ui.MainScheduleViewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: MainScheduleViewModel by viewModels {
        MainScheduleViewModelFactory(
            repository = ScheduleRepositoryImpl.getInstance(applicationContext),
            ringerModeManager = RingerModeManager.getInstance(applicationContext),
            alarmScheduler = AlarmScheduler.getInstance(applicationContext),
            onWidgetUpdateRequired = {
                com.soundschedule.widget.SoundScheduleWidgetUpdater.updateAllWidgets(applicationContext)
            }
        )
    }

    private val ringerReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            viewModel.refreshSystemSoundState()
            com.soundschedule.widget.SoundScheduleWidgetUpdater.updateAllWidgets(this@MainActivity)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        try {
            val repo = ScheduleRepositoryImpl.getInstance(applicationContext)
            val ringer = RingerModeManager.getInstance(applicationContext)
            val model = com.soundschedule.widget.SoundScheduleWidgetUpdater.buildWidgetModel(repo, ringer)
            val rvExpanded = com.soundschedule.widget.SoundScheduleWidgetUpdater.buildRemoteViews(this, model, isCompact = false)
            val rvCompact = com.soundschedule.widget.SoundScheduleWidgetUpdater.buildRemoteViews(this, model, isCompact = true)
            rvExpanded.apply(this, android.widget.FrameLayout(this))
            rvCompact.apply(this, android.widget.FrameLayout(this))
            com.soundschedule.utils.AppLog.i("WidgetTest", "SUCCESSFULLY INFLATED BOTH EXPANDED AND COMPACT WIDGET LAYOUTS")
        } catch (e: Throwable) {
            com.soundschedule.utils.AppLog.e("WidgetTest", "FAILED TO INFLATE WIDGET: ${e.message}", e)
        }
        handlePinWidgetRequest(intent)
        setContent {
            SoundScheduleTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                    color = DarkBackground
                ) {
                    MainScheduleScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePinWidgetRequest(intent)
    }

    private fun handlePinWidgetRequest(intent: android.content.Intent?) {
        if (intent?.action == "com.soundschedule.PIN_WIDGET") {
            val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(this)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                val provider = android.content.ComponentName(this, com.soundschedule.widget.SoundScheduleWidgetProvider::class.java)
                appWidgetManager.requestPinAppWidget(provider, null, null)
                com.soundschedule.utils.AppLog.i("MainActivity", "Requested launcher to pin SoundSchedule widget.")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = android.content.IntentFilter().apply {
            addAction(android.media.AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(android.app.NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(ringerReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(ringerReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(ringerReceiver)
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        // Refresh actual system ringer mode and Notification Policy Access status on resume
        viewModel.refreshSystemSoundState()
        // Refresh exact alarm permission and resynchronize alarms if granted
        viewModel.refreshExactAlarmPermission()
        // Refresh home-screen widget state
        com.soundschedule.widget.SoundScheduleWidgetUpdater.updateAllWidgets(this)
    }
}
