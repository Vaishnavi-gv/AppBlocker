package com.example.appblocker
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationCompat
import com.example.appblocker.databinding.ActivityMainBinding

class MainActivity : ComponentActivity() {

        private lateinit var binding: ActivityMainBinding
        private val monitoredApps = listOf("com.instagram.android", "com.facebook.katana", "com.android.settings", "com.android.mms")
        private var timeSpentInApp = 0
        private lateinit var handler: Handler
        private lateinit var runnable: Runnable


        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            // Start monitoring when the button is clicked
            binding.startMonitoringButton.setOnClickListener {
                if (checkPermissions()) {
                    startMonitoring()
                }
                else{
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
            timeSpentInApp = 0
            showNotificationWithCountdown()
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
                            timeSpentInApp += 1
                            if (timeSpentInApp >= 20) { // 20 seconds
                                redirectToApp()
                                timeSpentInApp = 0
                            }
                        } else {
                            timeSpentInApp = 0 // Reset if the user switches to another app
                        }
                    }
                    handler.postDelayed(this, 1000)
                }
            }
            handler.post(runnable)

            showNotificationWithCountdown()
        }

        // Redirect the user to the app
        private fun redirectToApp() {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)


        }

        // Show a notification with a countdown
        private fun showNotificationWithCountdown() {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "tracking_channel"

            // Create a notification channel for Android 8.0 and above
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, "App Blocker", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Notification for app tracking"
                }
                notificationManager.createNotificationChannel(channel)
            }



            // Build the notification
            val builder = NotificationCompat.Builder(this, channelId)
                .setContentTitle("App Usage Tracker")
                .setContentText("Monitoring your app usage...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)  // Ensure you have a valid icon
                .setOngoing(true)  // Makes the notification ongoing, so the user cannot swipe it away
                .setPriority(NotificationCompat.PRIORITY_HIGH)  // Make sure the priority is high

            // Show the notification
            notificationManager.notify(1, builder.build())
        }

    // Handle app lifecycle to stop monitoring when app is paused
        override fun onPause() {
            super.onPause()
            handler.removeCallbacks(runnable)
        }

        override fun onResume() {
            super.onResume()
            if (checkPermissions()) {
                startMonitoring()
            }
        }
}

