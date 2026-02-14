package com.callscreen.app.feature.settings.crypto

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.Toast
import com.callscreen.app.R
import com.callscreen.app.common.base.QkActivity
import com.callscreen.app.model.CryptoPaymentChallenge
import com.callscreen.app.repository.CryptoRepository
import com.callscreen.app.util.ScreenLog
import dagger.android.AndroidInjection
import timber.log.Timber
import javax.inject.Inject

class CryptoSettingsActivity : QkActivity() {

    @Inject lateinit var cryptoRepository: CryptoRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        ScreenLog.d("CryptoSettings", "onCreate starting")
        AndroidInjection.inject(this)
        ScreenLog.d("CryptoSettings", "Injection complete")
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crypto_settings)
        ScreenLog.d("CryptoSettings", "Layout inflated")

        showBackButton(true)
        title = getString(R.string.crypto_settings_title)

        val enableSwitch = findViewById<Switch>(R.id.cryptoEnabled)
        val priceInput = findViewById<EditText>(R.id.challengePrice)
        val tokenSpinner = findViewById<Spinner>(R.id.tokenType)
        val ethWalletInput = findViewById<EditText>(R.id.ethWallet)
        val usdcWalletInput = findViewById<EditText>(R.id.usdcWallet)
        val usdtWalletInput = findViewById<EditText>(R.id.usdtWallet)
        val alchemyKeyInput = findViewById<EditText>(R.id.alchemyKey)
        val saveButton = findViewById<android.view.View>(R.id.saveButton)

        // Load current values
        enableSwitch.isChecked = cryptoRepository.isCryptoChallengeEnabled()
        priceInput.setText(cryptoRepository.getChallengePrice().toString())
        ethWalletInput.setText(cryptoRepository.getWalletAddress(CryptoPaymentChallenge.TokenType.ETH))
        usdcWalletInput.setText(cryptoRepository.getWalletAddress(CryptoPaymentChallenge.TokenType.USDC))
        usdtWalletInput.setText(cryptoRepository.getWalletAddress(CryptoPaymentChallenge.TokenType.USDT))
        alchemyKeyInput.setText(cryptoRepository.getAlchemyApiKey())

        // Token type spinner
        val tokenTypes = CryptoPaymentChallenge.TokenType.values().map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, tokenTypes)
        tokenSpinner.adapter = adapter
        val currentToken = cryptoRepository.getPreferredTokenType()
        tokenSpinner.setSelection(CryptoPaymentChallenge.TokenType.values().indexOf(currentToken))

        // Save handler
        saveButton.setOnClickListener {
            try {
                cryptoRepository.setCryptoChallengeEnabled(enableSwitch.isChecked)

                val price = priceInput.text.toString().toIntOrNull() ?: 20
                cryptoRepository.setChallengePrice(price)

                val selectedToken = CryptoPaymentChallenge.TokenType.values()[tokenSpinner.selectedItemPosition]
                cryptoRepository.setPreferredTokenType(selectedToken)

                cryptoRepository.setWalletAddress(CryptoPaymentChallenge.TokenType.ETH, ethWalletInput.text.toString().trim())
                cryptoRepository.setWalletAddress(CryptoPaymentChallenge.TokenType.USDC, usdcWalletInput.text.toString().trim())
                cryptoRepository.setWalletAddress(CryptoPaymentChallenge.TokenType.USDT, usdtWalletInput.text.toString().trim())
                cryptoRepository.setAlchemyApiKey(alchemyKeyInput.text.toString().trim())

                Toast.makeText(this, R.string.crypto_settings_saved, Toast.LENGTH_SHORT).show()
                ScreenLog.d("CryptoSettings", "Settings saved")
            } catch (e: Exception) {
                Timber.e(e, "Failed to save crypto settings")
                ScreenLog.e("CryptoSettings", "Failed to save", e)
                Toast.makeText(this, R.string.crypto_settings_error, Toast.LENGTH_SHORT).show()
            }
        }

        ScreenLog.d("CryptoSettings", "Activity created")
    }
}
