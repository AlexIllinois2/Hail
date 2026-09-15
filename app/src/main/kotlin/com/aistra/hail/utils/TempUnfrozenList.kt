package com.aistra.hail.utils

import java.util.concurrent.ConcurrentHashMap

/**
 * Packages (unfrozen via Hail) that are candidates for auto freezing
 * when their task is removed from recents, mapped to the unfreeze timestamp.
 *
 * @see com.aistra.hail.services.SwipeFreezeService
 */
object TempUnfrozenList {
    private val map = ConcurrentHashMap<String, Long>()

    fun add(packageName: String) {
        map[packageName] = System.currentTimeMillis()
    }

    fun remove(packageName: String) {
        map.remove(packageName)
    }

    fun snapshot(): Map<String, Long> = map.toMap()

    fun clear() = map.clear()
}
