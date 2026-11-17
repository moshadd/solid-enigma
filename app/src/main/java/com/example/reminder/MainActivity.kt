package com.example.reminder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)

        btnStart.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            } else {
                startReminder()
            }
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, ReminderService::class.java))
            tvStatus.text = "已停止"
        }
    }

    private fun startReminder() {
        val intent = Intent(this, ReminderService::class.java)
            .setAction(ReminderService.ACTION_START)
        ContextCompat.startForegroundService(this, intent)
        tvStatus.text = "已启动：每 3~5 分钟随机响一次"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            startReminder()
            if (grantResults.isNotEmpty() &&
                grantResults[0] != PackageManager.PERMISSION_GRANTED
            ) {
                tvStatus.text = "已启动，但通知权限被拒绝；建议允许通知"
            }
        }
    }
}