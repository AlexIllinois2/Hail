package com.aistra.hail.services

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.aistra.hail.BuildConfig
import com.aistra.hail.R
import com.aistra.hail.app.AppManager
import com.aistra.hail.app.HailData
import com.aistra.hail.ui.main.MainActivity
import com.aistra.hail.utils.HShell
import com.aistra.hail.utils.HTarget
import com.aistra.hail.utils.HUI
import com.aistra.hail.utils.TempUnfrozenList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Auto freezes checked apps when their task is removed from recents (e.g. swiped away).
 *
 * Requires a root working mode: it polls [DUMP_RECENTS_COMMAND] and `pidof` via a root shell.
 * A package is frozen only on a state transition, so that unfrozen-but-never-launched
 * packages and the other app in split screen are never affected:
 * - task seen in recents, then disappeared: swiped away or cleaned;
 * - task remains, but its process was alive and is now dead: system cleaned the process.
 */
class SwipeFreezeService : Service() {
    private val channelID = javaClass.simpleName
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val states = ConcurrentHashMap<String, FreezeState>()

    private class FreezeState {
        var taskSeen = false
        var processAlive = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        scope.launch { pollLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        states.clear()
        TempUnfrozenList.clear()
        super.onDestroy()
    }

    private fun startAsForeground() {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, channelID)
            .setContentTitle(getString(R.string.swipe_freeze_notification_title))
            .setSmallIcon(R.drawable.ic_round_frozen)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
                )
            ).build()
        when {
            HTarget.U -> startForeground(
                NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )

            else -> startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(
            channelID, NotificationManagerCompat.IMPORTANCE_LOW
        ).setName(getString(R.string.swipe_freeze)).build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    private val pollIntervalMillis: Long
        get() {
            val seconds = HailData.swipeFreezeInterval
            val interactive = getSystemService<PowerManager>()?.isInteractive == true
            return ((if (interactive) seconds else seconds * IDLE_INTERVAL_FACTOR) * 1000).toLong()
        }

    private suspend fun CoroutineScope.pollLoop() {
        seedCheckedApps()
        var abnormalDumps = 0
        while (isActive) {
            delay(pollIntervalMillis)
            val snapshot = TempUnfrozenList.snapshot()
            if (snapshot.isEmpty()) continue

            val recents = HShell.execute(DUMP_RECENTS_COMMAND, true).second.orEmpty()
            if (recents.isBlank()) {
                // dumpsys keeps returning nothing: the ROM may be incompatible, stop before misfreezing.
                if (++abnormalDumps >= ABNORMAL_DUMPS_LIMIT) {
                    HUI.showToast(R.string.swipe_freeze_abnormal, isLengthLong = true)
                    stopSelf()
                    break
                }
                continue
            }
            abnormalDumps = 0

            val now = System.currentTimeMillis()
            snapshot.forEach { (packageName, unfrozenAt) ->
                if (now - unfrozenAt < (HailData.swipeFreezeDelay * 1000).toLong()) return@forEach
                val state = states.getOrPut(packageName) { FreezeState() }
                when {
                    recents.containsPackage(packageName) -> when {
                        !state.taskSeen -> {
                            state.taskSeen = true
                            state.processAlive = isProcessAlive(packageName)
                        }

                        state.processAlive -> {
                            if (!isProcessAlive(packageName)) freezeApp(packageName) // Process died while task remained
                        }
                    }

                    state.taskSeen -> freezeApp(packageName) // Task removed: swiped away or cleaned
                }
            }
        }
    }

    private fun seedCheckedApps() {
        HailData.checkedList.filterNot {
            it.whitelisted || it.packageName == BuildConfig.APPLICATION_ID
        }.forEach {
            if (!AppManager.isAppFrozen(it.packageName)) TempUnfrozenList.add(it.packageName)
        }
    }

    private fun isProcessAlive(packageName: String): Boolean =
        HShell.execute("pidof $packageName", true).second?.isNotBlank() == true

    private fun freezeApp(packageName: String) {
        states.remove(packageName)
        TempUnfrozenList.remove(packageName)
        if (!AppManager.setAppFrozen(packageName, true)) {
            HUI.showToast(R.string.permission_denied)
        }
    }

    private companion object {
        const val NOTIFICATION_ID = 101
        const val ABNORMAL_DUMPS_LIMIT = 3
        const val IDLE_INTERVAL_FACTOR = 10
        const val DUMP_RECENTS_COMMAND = "dumpsys activity recents"
    }
}

/** Matches [packageName] as a whole word, so that `com.foo` does not match `com.foobar`. */
private fun String.containsPackage(packageName: String): Boolean {
    var index = indexOf(packageName)
    while (index >= 0) {
        val before = getOrNull(index - 1)
        val after = getOrNull(index + packageName.length)
        if ((before == null || !before.isPackageChar()) && (after == null || !after.isPackageChar())) return true
        index = indexOf(packageName, index + 1)
    }
    return false
}

private fun Char.isPackageChar(): Boolean = isLetterOrDigit() || this == '.' || this == '_'
