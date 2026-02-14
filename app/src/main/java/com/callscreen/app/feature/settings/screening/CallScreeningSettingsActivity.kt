package com.callscreen.app.feature.settings.screening

import android.os.Bundle
import android.widget.Switch
import com.callscreen.app.R
import com.callscreen.app.common.base.QkActivity
import dagger.android.AndroidInjection

class CallScreeningSettingsActivity : QkActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call_screening_settings)

        showBackButton(true)
        title = getString(R.string.call_screening_settings_title)

        val callScreeningEnabled = findViewById<Switch>(R.id.callScreeningEnabled)
        val screenUnknownOnly = findViewById<Switch>(R.id.screenUnknownOnly)
        val mathChallengeEnabled = findViewById<Switch>(R.id.mathChallengeEnabled)

        callScreeningEnabled.isChecked = prefs.callScreeningEnabled.get()
        screenUnknownOnly.isChecked = prefs.screenUnknownOnly.get()
        mathChallengeEnabled.isChecked = prefs.mathChallengeEnabled.get()

        callScreeningEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.callScreeningEnabled.set(isChecked)
        }
        screenUnknownOnly.setOnCheckedChangeListener { _, isChecked ->
            prefs.screenUnknownOnly.set(isChecked)
        }
        mathChallengeEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.mathChallengeEnabled.set(isChecked)
        }
    }
}
