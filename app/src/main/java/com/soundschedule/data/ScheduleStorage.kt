package com.soundschedule.data

import android.content.Context
import com.soundschedule.utils.AppLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

class ScheduleStorage private constructor(
    private val getFile: () -> File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    constructor(context: Context, fileName: String = "sound_schedules.json") : this(
        getFile = { File(context.filesDir, fileName) },
        ioDispatcher = Dispatchers.IO
    )

    constructor(file: File, ioDispatcher: CoroutineDispatcher = Dispatchers.IO) : this(
        getFile = { file },
        ioDispatcher = ioDispatcher
    )

    private val tag = "ScheduleStorage"
    private val mutex = Mutex()
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val targetFile: File
        get() = getFile()

    private val tempFile: File
        get() = File(targetFile.parentFile, "${targetFile.name}.tmp")

    private val backupFile: File
        get() = File(targetFile.parentFile, "${targetFile.name}.bak")

    suspend fun loadState(): ScheduleDataState = mutex.withLock {
        withContext(ioDispatcher) {
            readStateInternal()
        }
    }

    fun loadStateSync(): ScheduleDataState = synchronized(this) {
        readStateInternal()
    }

    suspend fun saveState(state: ScheduleDataState): Boolean = mutex.withLock {
        withContext(ioDispatcher) {
            writeStateInternal(state)
        }
    }

    fun saveStateSync(state: ScheduleDataState): Boolean = synchronized(this) {
        writeStateInternal(state)
    }

    private fun readStateInternal(): ScheduleDataState {
        val file = targetFile
        val bak = backupFile

        // If target file doesn't exist but backup does (interrupted write), restore backup
        if (!file.exists() && bak.exists()) {
            bak.copyTo(file, overwrite = true)
        }

        if (!file.exists() || file.length() == 0L) {
            AppLog.d(tag, "No saved schedule file found. Seeding with default college schedule.")
            val defaultEvents = DefaultCollegeSchedule.createDefaultEvents()
            val initialState = ScheduleDataState(
                isMasterEnabled = true,
                events = defaultEvents,
                nextEventId = (defaultEvents.maxOfOrNull { it.id } ?: 0L) + 1L
            )
            writeStateInternal(initialState)
            return initialState
        }

        return try {
            val content = file.readText(Charsets.UTF_8)
            json.decodeFromString<ScheduleDataState>(content)
        } catch (e: Exception) {
            AppLog.e(tag, "Failed to parse saved schedule state, recovering with defaults: ${e.message}", e)
            val defaultEvents = DefaultCollegeSchedule.createDefaultEvents()
            val recoveredState = ScheduleDataState(
                isMasterEnabled = true,
                events = defaultEvents,
                nextEventId = (defaultEvents.maxOfOrNull { it.id } ?: 0L) + 1L
            )
            writeStateInternal(recoveredState)
            recoveredState
        }
    }

    private fun writeStateInternal(state: ScheduleDataState): Boolean {
        return try {
            val parent = targetFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }

            val serialized = json.encodeToString(ScheduleDataState.serializer(), state)
            val tmp = tempFile
            tmp.writeText(serialized, Charsets.UTF_8)

            // Atomic rename via backup
            val bak = backupFile
            if (targetFile.exists()) {
                if (bak.exists()) bak.delete()
                targetFile.renameTo(bak)
            }

            if (tmp.renameTo(targetFile)) {
                if (bak.exists()) bak.delete()
                AppLog.d(tag, "Schedule state saved successfully (${state.events.size} events, masterEnabled=${state.isMasterEnabled})")
                true
            } else {
                // If rename failed, restore backup
                if (bak.exists()) {
                    bak.renameTo(targetFile)
                }
                AppLog.e(tag, "Failed to rename temp file to target file")
                false
            }
        } catch (e: IOException) {
            AppLog.e(tag, "Failed to write schedule state: ${e.message}", e)
            false
        }
    }
}
