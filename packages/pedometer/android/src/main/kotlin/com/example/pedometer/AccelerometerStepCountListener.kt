package com.example.pedometer

import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import com.github.psambit9791.jdsp.filter.Butterworth
import java.util.*
import kotlin.math.sqrt
import io.flutter.plugin.common.EventChannel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

// TODO: restart this service when device rebooted but user still wanted it on
class AccelerometerStepCountListener : SensorEventListener, Service() {
    private var sensorManager: SensorManager? = null
    private val notificationChannel = "alt_steps"

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    notificationChannel,
                    "alt_steps", NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val notification: Notification = Notification.Builder(this)
            .setSmallIcon(this.applicationInfo.icon)
            .setContentTitle("Pedometer: Active")
            .setContentText("Tracking steps using accelerometer...")
            .setChannelId(notificationChannel)
            .build()
        startForeground(666, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sensorManager = DataHolder.sensorManager
        sensorManager!!.registerListener(
            this,
            DataHolder.sensor, SensorManager.SENSOR_DELAY_GAME
        )

        return START_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
        sensorManager!!.unregisterListener(this)
    }

    private val bufferSize: Int = 100 // NOTE: Change below if changing this as well
    private var arrayBuffer: DoubleArray = DoubleArray(bufferSize)

    private val c = 1.05
    private val threshold = 1.2

    private var tick = 0
    @Volatile
    private var stepCount = 0

    private fun filterBuffer() {
        val samplingFreq = 50.0
        val order = 20
        val cutoff = 0.2 * (samplingFreq * 0.5)
        val filter = Butterworth(arrayBuffer, samplingFreq)
        arrayBuffer = filter.lowPassFilter(order, cutoff)
    }

    // Adapted from
    // Mladenov, M., & Mock, M. (2009).
    // A step counter service for Java-enabled devices using a built-in accelerometer.
    //
    // Performs some peak detection
    private fun checkSteps() {
        var peakCount = 0
        var peakAccumulate = 0.0
        for (i in 1 until arrayBuffer.size - 1) {
            val curr: Double = arrayBuffer[i]
            if (curr - arrayBuffer[i - 1] > 0 && curr - arrayBuffer[i + 1] > 0) {
                ++peakCount
                peakAccumulate += curr
            }
        }
        val peakMean = peakAccumulate / peakCount

        // second part
        for (i in 1 until arrayBuffer.size - 1) {
            val curr = arrayBuffer[i]
            if (curr - arrayBuffer[i - 1] > 0 && curr - arrayBuffer[i + 1] > 0
                && curr > c * peakMean && curr > threshold
            ) {
                ++stepCount // corrected from what looks like a mistake in the paper
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            val vec = event.values
            val mag: Double = sqrt( vec[0]*vec[0] + vec[1]*vec[1] + vec[2]*vec[2].toDouble())
            arrayBuffer[tick] = mag

            tick = (tick + 1) % bufferSize
            if (tick == 0) { // check buffer every `bufferSize` ticks
                filterBuffer()
                checkSteps()
                DataHolder.events?.success(stepCount)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

object DataHolder {
    @Volatile
    var events: EventChannel.EventSink? = null

    @Volatile
    var sensorManager: SensorManager? = null
    @Volatile
    var sensor: Sensor? = null

    @Volatile
    var started: Boolean = false
}