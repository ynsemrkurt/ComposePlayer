package com.plcoding.videoplayercompose

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class PIPActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.let {
            when (it.action) {
                MainActivity.ACTION_PREVIOUS -> {
                    Toast.makeText(context, "Previous", Toast.LENGTH_SHORT).show()
                }
                MainActivity.ACTION_PLAY -> {
                    Toast.makeText(context, "Play", Toast.LENGTH_SHORT).show()
                }
                MainActivity.ACTION_NEXT -> {
                    Toast.makeText(context, "Next", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    // Handle other actions if needed
                }
            }
        }
    }
}