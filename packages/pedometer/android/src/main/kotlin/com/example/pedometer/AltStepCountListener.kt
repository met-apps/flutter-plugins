package com.example.pedometer

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import com.github.psambit9791.jdsp.filter.Butterworth
import kotlin.math.sqrt
import io.flutter.plugin.common.EventChannel
import android.os.Looper

class AltStepCountListener : SensorEventListener {
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
            val mag: Double = sqrt(vec[0] * vec[0] + vec[1] * vec[1] + vec[2] * vec[2].toDouble())
            arrayBuffer[tick] = mag

            tick = (tick + 1) % bufferSize
            if (tick == 0) { // check buffer every `bufferSize` ticks
                filterBuffer()
                checkSteps()

                // this needs to run on UIThread so we use [MainThreadEventSink] to post to the main thread
                DataHolder.events?.success(stepCount)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

object DataHolder {
    @Volatile
    var events: MainThreadEventSink? = null

    @Volatile
    var sensorManager: SensorManager? = null

    @Volatile
    var sensor: Sensor? = null

    @Volatile
    var started: Boolean = false
}

/**
 * Facilitates sending events on the main thread.
 */
class MainThreadEventSink(eventSink: EventChannel.EventSink) : EventChannel.EventSink {
    private val eventSink: EventChannel.EventSink = eventSink
    private val handler: Handler = Handler(Looper.getMainLooper())

    override fun success(o: Any?) {
        handler.post { eventSink.success(o) }
    }

    override fun error(s1: String, s2: String, o: Any) {
        handler.post { eventSink.error(s1, s2, o) }
    }

    override fun endOfStream() {
        handler.post { eventSink.endOfStream() }
    }
}