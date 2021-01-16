package com.example.pedometer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder

// TODO: restart this service when device rebooted but user still wanted it on
class AltStepCountService : Service() {
    private var sensorManager: SensorManager? = null
    private val notificationChannel = "alt_steps"

    private val handlerThread = HandlerThread("Accelerometer sensor thread")
    private val altStepCountListener: SensorEventListener = AltStepCountListener()

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // use notification channels if API version requires it
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    notificationChannel,
                    "alt_steps", NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        var notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, notificationChannel)
        } else {
            Notification.Builder(this)
        }
        notification = notification.setSmallIcon(this.applicationInfo.icon)
            .setContentTitle("Pedometer: Active")
            .setContentText("Tracking steps using accelerometer...")
        startForeground(666, notification.build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handlerThread.start()

        val handler = Handler(handlerThread.looper)
        sensorManager = DataHolder.sensorManager

        sensorManager!!.registerListener(
            altStepCountListener,
            DataHolder.sensor, SensorManager.SENSOR_DELAY_GAME,
            handler
        )

        // TODO: investigate and check if we need to save the previous step count.
        //       e.g. Android destroys service while the user is walking, then instantiates
        //            this class again, so we lose the local step count variable.
        return START_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        handlerThread.quitSafely()
        stopForeground(true)
        sensorManager!!.unregisterListener(altStepCountListener)
    }
}
