package com.github.itube.backend

import android.content.Context
import android.content.ContextWrapper
import android.content.ServiceConnection
import android.util.Log

/**
 * ContextWrapper that intercepts [unbindService] to prevent crashes from
 * [IllegalArgumentException] ("Service not registered") when Android or Media3
 * encounters service disconnect / unbind race conditions.
 */
class SafeUnbindContext(base: Context) : ContextWrapper(base) {
    override fun unbindService(conn: ServiceConnection) {
        try {
            super.unbindService(conn)
        } catch (e: IllegalArgumentException) {
            Log.w("AirStream", "Safely caught Service not registered on unbind: ${e.message}")
        }
    }
}
