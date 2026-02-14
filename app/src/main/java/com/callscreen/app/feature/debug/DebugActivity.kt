package com.callscreen.app.feature.debug

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import com.callscreen.app.R
import com.callscreen.app.common.base.QkActivity
import com.callscreen.app.util.ScreenLog
import dagger.android.AndroidInjection
import java.util.Timer
import java.util.TimerTask

class DebugActivity : QkActivity() {

    private lateinit var logTextView: TextView
    private var refreshTimer: Timer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.debug_activity)

        showBackButton(true)
        title = getString(R.string.debug_title)

        logTextView = findViewById(R.id.logText)
        logTextView.movementMethod = ScrollingMovementMethod()
        logTextView.setTextIsSelectable(true)

        refreshLog()

        // Auto-refresh every 2 seconds
        refreshTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    runOnUiThread { refreshLog() }
                }
            }, 2000, 2000)
        }
    }

    private fun refreshLog() {
        logTextView.text = ScreenLog.getLog()
        // Auto-scroll to bottom
        val scrollAmount = logTextView.layout?.let {
            it.getLineTop(logTextView.lineCount) - logTextView.height
        } ?: 0
        if (scrollAmount > 0) {
            logTextView.scrollTo(0, scrollAmount)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu ?: return super.onCreateOptionsMenu(menu)
        menu.add(0, MENU_COPY, 0, R.string.debug_copy)
        menu.add(0, MENU_CLEAR, 1, R.string.debug_clear)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        MENU_COPY -> {
            val clipboard = getSystemService(android.content.ClipboardManager::class.java)
            clipboard?.setPrimaryClip(
                android.content.ClipData.newPlainText("Debug Log", ScreenLog.getLog())
            )
            android.widget.Toast.makeText(this, R.string.debug_copied, android.widget.Toast.LENGTH_SHORT).show()
            true
        }
        MENU_CLEAR -> {
            ScreenLog.clear()
            refreshLog()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        refreshTimer?.cancel()
    }

    companion object {
        private const val MENU_COPY = 1
        private const val MENU_CLEAR = 2
    }
}
