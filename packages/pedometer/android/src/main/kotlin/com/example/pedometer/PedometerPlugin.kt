package com.example.pedometer

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

/** PedometerPlugin */
class PedometerPlugin : FlutterPlugin {
    private lateinit var stepDetectionChannel: EventChannel
    private lateinit var stepCountChannel: EventChannel
    private lateinit var altStepCountChannel: EventChannel
    private val CHANNEL = "pedometer/alt_pedometer"

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        MethodChannel(flutterPluginBinding.getBinaryMessenger(), CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "startPlatform" -> {
                    val ctx: Context = flutterPluginBinding.applicationContext
                    val mgr = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager

                    // using global(s) instead of passing stuff through Parcel/serialization
                    DataHolder.sensorManager = mgr
                    DataHolder.sensor = mgr.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

                    val intent = Intent(ctx, AccelerometerStepCountListener::class.java)
                    ctx.startForegroundService(intent)
                    result.success(null)
                }
                "stopPlatform" -> {
                    val ctx: Context = flutterPluginBinding.applicationContext
                    ctx.stopService(
                        Intent(
                            ctx,
                            AccelerometerStepCountListener::class.java
                        )
                    )
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }

        /// Create channels
        stepDetectionChannel = EventChannel(flutterPluginBinding.binaryMessenger, "step_detection")
        stepCountChannel = EventChannel(flutterPluginBinding.binaryMessenger, "step_count")
        altStepCountChannel = EventChannel(flutterPluginBinding.binaryMessenger, "alt_step_count")

        /// Create handlers
        val stepDetectionHandler = SensorStreamHandler(flutterPluginBinding, Sensor.TYPE_STEP_DETECTOR)
        val stepCountHandler = SensorStreamHandler(flutterPluginBinding, Sensor.TYPE_STEP_COUNTER)
        val altStepCountHandler = AltStreamHandler(flutterPluginBinding)

        /// Set handlers
        stepDetectionChannel.setStreamHandler(stepDetectionHandler)
        stepCountChannel.setStreamHandler(stepCountHandler)
        altStepCountChannel.setStreamHandler(altStepCountHandler)
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        stepDetectionChannel.setStreamHandler(null)
        stepCountChannel.setStreamHandler(null)
        altStepCountChannel.setStreamHandler(null)
    }

}
