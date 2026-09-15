package com.aistra.hail.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.aistra.hail.app.HailData
import com.aistra.hail.services.SwipeFreezeService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && HailData.swipeFreezeEnabled
            && HailData.workingMode.startsWith(HailData.SU)
        ) ContextCompat.startForegroundService(context, Intent(context, SwipeFreezeService::class.java))
    }
}
