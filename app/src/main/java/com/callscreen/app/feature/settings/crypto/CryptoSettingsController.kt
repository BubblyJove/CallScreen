package com.callscreen.app.feature.settings.crypto

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bluelinelabs.conductor.archlifecycle.LifecycleController
import com.callscreen.app.R
import com.callscreen.app.injection.appComponent
import com.callscreen.app.model.CryptoPaymentChallenge
import com.callscreen.app.repository.CryptoRepository
import timber.log.Timber
import javax.inject.Inject

class CryptoSettingsController : LifecycleController() {

    @Inject lateinit var cryptoRepository: CryptoRepository

    init {
        appComponent.inject(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
        val view = inflater.inflate(R.layout.controller_crypto_settings, container, false)

        try {
            val enableSwitch = view.findViewById<Switch>(R.id.cryptoEnabled)
            val priceInput = view.findViewById<EditText>(R.id.challengePrice)
            val tokenSpinner = view.findViewById<Spinner>(R.id.tokenType)
            val ethWalletInput = view.findViewById<EditText>(R.id.ethWallet)
            val usdcWalletInput = view.findViewById<EditText>(R.id.usdcWallet)
            val usdtWalletInput = view.findViewById<EditText>(R.id.usdtWallet)
            val alchemyKeyInput = view.findViewById<EditText>(R.id.alchemyKey)
            val saveButton = view.findViewById<View>(R.id.saveButton)

            // Load current values
            enableSwitch.isChecked = cryptoRepository.isCryptoChallengeEnabled()
            priceInput.setText(cryptoRepository.getChallengePrice().toString())
            ethWalletInput.setText(cryptoRepository.getWalletAddress(CryptoPaymentChallenge.TokenType.ETH))
            usdcWalletInput.setText(cryptoRepository.getWalletAddress(CryptoPaymentChallenge.TokenType.USDC))
            usdtWalletInput.setText(cryptoRepository.getWalletAddress(CryptoPaymentChallenge.TokenType.USDT))
            alchemyKeyInput.setText(cryptoRepository.getAlchemyApiKey())

            // Token type spinner
            val tokenTypes = CryptoPaymentChallenge.TokenType.values().map { it.name }
            val adapter = ArrayAdapter(view.context, android.R.layout.simple_spinner_dropdown_item, tokenTypes)
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

                    Toast.makeText(view.context, R.string.crypto_settings_saved, Toast.LENGTH_SHORT).show()
                    Timber.d("Crypto settings saved")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to save crypto settings")
                    Toast.makeText(view.context, R.string.crypto_settings_error, Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize crypto settings view")
        }

        return view
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        activity?.title = activity?.getString(R.string.crypto_settings_title)
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun handleBack(): Boolean {
        router.popCurrentController()
        return true
    }
}
