package com.github.itube

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DownloadCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        if (id >= 0) VideoDownloader.cancel(context, id)
    }

    companion object {
        const val EXTRA_ID = "com.github.itube.extra.DOWNLOAD_ID"
    }
}
