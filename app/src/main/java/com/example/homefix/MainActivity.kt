package com.example.homefix

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var toggle: MaterialSwitch
    private lateinit var status: TextView
    private lateinit var timeoutSlider: Slider
    private lateinit var timeoutLabel: TextView
    private lateinit var reactionSlider: Slider
    private lateinit var reactionLabel: TextView
    private lateinit var secondSlider: Slider
    private lateinit var secondLabel: TextView
    private lateinit var debugSwitch: MaterialSwitch
    private lateinit var debugSection: View
    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toggle = findViewById(R.id.toggle)
        status = findViewById(R.id.status)
        timeoutSlider = findViewById(R.id.timeoutSlider)
        timeoutLabel = findViewById(R.id.timeoutLabel)
        reactionSlider = findViewById(R.id.reactionSlider)
        reactionLabel = findViewById(R.id.reactionLabel)
        secondSlider = findViewById(R.id.secondSlider)
        secondLabel = findViewById(R.id.secondLabel)
        debugSwitch = findViewById(R.id.debugSwitch)
        debugSection = findViewById(R.id.debugSection)
        logView = findViewById(R.id.logView)
        logView.movementMethod = ScrollingMovementMethod()

        val openAccessibility: Button = findViewById(R.id.openAccessibility)
        val openBattery: Button = findViewById(R.id.openBattery)
        val refresh: Button = findViewById(R.id.refresh)
        val share: Button = findViewById(R.id.share)

        toggle.isChecked = Prefs.isEnabled(this)
        toggle.setOnCheckedChangeListener { _, checked ->
            Prefs.setEnabled(this, checked)
            HomeFixService.enabled = checked
            updateStatus()
        }

        timeoutSlider.valueFrom = 500f
        timeoutSlider.valueTo = 10_000f
        timeoutSlider.stepSize = 500f
        timeoutSlider.value = Prefs.getTimeoutMs(this).toFloat()
        updateTimeoutLabel(timeoutSlider.value)
        timeoutSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                Prefs.setTimeoutMs(this, value.toLong())
                HomeFixService.timeoutMs = value.toLong()
            }
            updateTimeoutLabel(value)
        }

        reactionSlider.valueFrom = 0f
        reactionSlider.valueTo = 1000f
        reactionSlider.stepSize = 50f
        reactionSlider.value = Prefs.getReactionMs(this).toFloat()
        updateReactionLabel(reactionSlider.value)
        reactionSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                Prefs.setReactionMs(this, value.toLong())
                HomeFixService.reactionMs = value.toLong()
            }
            updateReactionLabel(value)
        }

        secondSlider.valueFrom = 300f
        secondSlider.valueTo = 2000f
        secondSlider.stepSize = 50f
        secondSlider.value = Prefs.getSecondDelayMs(this).toFloat()
        updateSecondLabel(secondSlider.value)
        secondSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                Prefs.setSecondDelayMs(this, value.toLong())
                HomeFixService.secondDelayMs = value.toLong()
            }
            updateSecondLabel(value)
        }

        debugSwitch.isChecked = Prefs.isDebug(this)
        applyDebug(debugSwitch.isChecked)
        debugSwitch.setOnCheckedChangeListener { _, checked ->
            Prefs.setDebug(this, checked)
            HomeFixService.debug = checked
            applyDebug(checked)
        }

        openAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        openBattery.setOnClickListener {
            val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:$packageName"))
            try {
                startActivity(i)
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        refresh.setOnClickListener { logView.text = readLog() }
        share.setOnClickListener {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, readLog())
            }
            startActivity(Intent.createChooser(send, getString(R.string.share_log)))
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        if (debugSection.visibility == View.VISIBLE) logView.text = readLog()
    }

    private fun applyDebug(debug: Boolean) {
        debugSection.visibility = if (debug) View.VISIBLE else View.GONE
    }

    private fun updateTimeoutLabel(value: Float) {
        timeoutLabel.text = getString(R.string.timeout_label, value / 1000f)
    }

    private fun updateReactionLabel(value: Float) {
        reactionLabel.text = getString(R.string.reaction_label, value.toInt())
    }

    private fun updateSecondLabel(value: Float) {
        secondLabel.text = getString(R.string.second_label, value.toInt())
    }

    private fun updateStatus() {
        val enabled = HomeFixApplication.isAccessibilityEnabled(this)
        status.text = if (enabled) getString(R.string.status_on) else getString(R.string.status_off)
    }

    private fun readLog(): String {
        return try {
            val f = File(filesDir, HomeFixService.LOG_FILE)
            if (!f.exists() || f.length() == 0L) {
                getString(R.string.no_log)
            } else {
                val lines = f.readLines()
                val tail = if (lines.size > 500) lines.takeLast(500) else lines
                tail.joinToString("\n")
            }
        } catch (e: Exception) {
            "error reading log: ${e.message}"
        }
    }
}
