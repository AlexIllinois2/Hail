package io.github.AlexIllinois2.snub.app

import android.content.Context
import androidx.preference.PreferenceManager

object DirtyDataUpdater {
    fun update(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().apply()
    }
}