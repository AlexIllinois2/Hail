package io.github.AlexIllinois2.snub.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import io.github.AlexIllinois2.snub.R
import io.github.AlexIllinois2.snub.utils.HPolicy

class DeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        HPolicy.enableBackupService()
        HPolicy.setOrganizationName(context.getString(R.string.app_name))
    }
}