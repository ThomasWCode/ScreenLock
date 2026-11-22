package com.thomaswcode.screenlock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView

// Setup screen: whether the lock's accessibility service is on, a way to its settings, and how the lock works
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.open_settings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        val connected = LockService.instance != null
        findViewById<TextView>(R.id.status).setText(
            if (connected) R.string.status_ready else R.string.status_service_off
        )
        findViewById<Button>(R.id.open_settings).setText(
            if (connected) R.string.action_settings else R.string.action_turn_on
        )
        LockWidget.update(this)
    }
}
