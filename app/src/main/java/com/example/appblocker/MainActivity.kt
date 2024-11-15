package com.example.appblocker
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationCompat
import com.example.appblocker.databinding.ActivityMainBinding

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private val monitoredApps = listOf(
        "com.instagram.android",
        "com.facebook.katana",
        "com.android.settings",
        "com.android.mms",
        "com.google.android.youtube"
    )
    private var timeSpentInApp = 0
    private lateinit var handler: Handler
    private lateinit var runnable: Runnable

    private var isMonitoring = false  // To track whether we are monitoring a monitored app
    private var pausedTime = 0  // Store the time when it's paused
    private var isPaused = false  // To track if the timer is paused
    private var isCooldownActive = false
    private var cooldownTimeLeft=30

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Start monitoring when the button is clicked
        binding.startMonitoringButton.setOnClickListener {
            if (checkPermissions()) {
                startMonitoring()
            } else {
                checkPermissions()
                if (checkPermissions()) {
                    startMonitoring()
                }
            }
        }
    }

    // Check if the necessary permissions are granted
    private fun checkPermissions(): Boolean {
        val appOpsManager = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        val usagePermissionGranted = mode == AppOpsManager.MODE_ALLOWED
        val overlayPermissionGranted = Settings.canDrawOverlays(this)

        if (!usagePermissionGranted) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return false
        }

        if (!overlayPermissionGranted) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            return false
        }
        return true
    }

    // Start monitoring app usage
    private fun startMonitoring() {
        timeSpentInApp = pausedTime

        handler = Handler(Looper.getMainLooper())
        runnable = object : Runnable {
            override fun run() {
                val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val end = System.currentTimeMillis()
                val start = end - 1000
                val appList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)

                appList?.let {
                    val topApp = it.maxByOrNull { app -> app.lastTimeUsed }
                    if (topApp != null && topApp.packageName in monitoredApps) {
                        if (!isCooldownActive) {
                            if (!isMonitoring) {
                                // Start monitoring if not already monitoring
                                isMonitoring = true
                            }
                            if (!isPaused) {
                            timeSpentInApp += 1
                            updateNotificationWithTime(timeSpentInApp)
                            }

                            if (timeSpentInApp >= 20) { // 20 seconds
                                 redirectToApp()
                                timeSpentInApp = 0
                                resetNotification()
                                startCooldownTimer()
                            }

                            if (isPaused && isMonitoring) {
                            isPaused = false
                            startMonitoring()
                            }
                        }
                        else{
                            redirectToApp()
                        }
                    }
                    else {
                        if (isMonitoring) {
                            pausedTime = timeSpentInApp
                            isMonitoring = false
                            isPaused = true
                        }
                        resetNotification()
                    }
                }
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(runnable)
    }

    private fun redirectToApp() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)

        startCooldownTimer()
    }

    private fun updateNotificationWithTime(seconds: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "tracking_channel"

        // Build the updated notification
        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("App Usage Tracker")
            .setContentText("Time spent in app: ${seconds}s")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        notificationManager.notify(1, builder.build())
    }

    private fun resetNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "tracking_channel"

        // Reset the notification to initial state
        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("App Usage Tracker")
            .setContentText("Time spent in app: ${timeSpentInApp}s")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        notificationManager.notify(1, builder.build())
    }

    private fun startCooldownTimer() {
        isCooldownActive = true

        // Update the UI to show the cool-down timer
        binding.cooldownTimerTextView.visibility = View.VISIBLE

        handler.post(object : Runnable {
            override fun run() {
                if (cooldownTimeLeft > 0) {
                    binding.cooldownTimerTextView.text = "Cool-down Time Left: ${cooldownTimeLeft}s"
                    cooldownTimeLeft--
                    handler.postDelayed(this, 1000)
                } else {
                    // Cool-down period ends
                    isCooldownActive = false
                    binding.cooldownTimerTextView.visibility = View.GONE
                }
            }
        })
    }
    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(runnable)
    }

    override fun onResume() {
        super.onResume()
        if (checkPermissions()) {
            if (isPaused) {
                handler.post(runnable)
                // If app was paused, resume from the last paused time
                isPaused = false
                startMonitoring()
            } else if (!isMonitoring) {
                startMonitoring()
            }
        }
    }
}