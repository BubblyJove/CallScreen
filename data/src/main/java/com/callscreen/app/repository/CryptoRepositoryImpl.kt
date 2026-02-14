package com.callscreen.app.repository

import android.content.SharedPreferences
import com.callscreen.app.model.CryptoPaymentChallenge
import io.realm.Realm
import java.util.UUID
import javax.inject.Inject

class CryptoRepositoryImpl @Inject constructor(
    private val sharedPrefs: SharedPreferences
) : CryptoRepository {

    companion object {
        private const val PREF_CRYPTO_ENABLED = "crypto_challenge_enabled"
        private const val PREF_CHALLENGE_PRICE = "crypto_challenge_price_cents"
        private const val PREF_TOKEN_TYPE = "crypto_preferred_token"
        private const val PREF_ALCHEMY_KEY = "alchemy_api_key"
        private const val PREF_WALLET_PREFIX = "crypto_wallet_"
        private const val DEFAULT_PRICE_CENTS = 20 // $0.20
    }

    override fun createPaymentChallenge(
        phoneNumber: String,
        usdCents: Int,
        tokenType: CryptoPaymentChallenge.TokenType,
        walletAddress: String,
        exactAmount: String
    ): CryptoPaymentChallenge {
        val challenge = CryptoPaymentChallenge().apply {
            this.id = UUID.randomUUID().toString()
            this.phoneNumber = phoneNumber
            this.walletAddress = walletAddress
            this.tokenType = tokenType
            this.exactAmount = exactAmount
            this.usdCents = usdCents
            this.createdAt = System.currentTimeMillis()
            this.expiresAt = System.currentTimeMillis() + CryptoPaymentChallenge.TTL_MS
            this.status = CryptoPaymentChallenge.PaymentStatus.PENDING
        }

        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction { r ->
                r.insertOrUpdate(challenge)
            }
        }

        return challenge
    }

    override fun getActivePaymentChallenge(phoneNumber: String): CryptoPaymentChallenge? {
        return Realm.getDefaultInstance().use { realm ->
            realm.where(CryptoPaymentChallenge::class.java)
                .equalTo("phoneNumber", phoneNumber)
                .equalTo("statusString", CryptoPaymentChallenge.PaymentStatus.PENDING.name)
                .or()
                .equalTo("phoneNumber", phoneNumber)
                .equalTo("statusString", CryptoPaymentChallenge.PaymentStatus.CONFIRMING.name)
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
        }
    }

    override fun getPaymentChallengeById(id: String): CryptoPaymentChallenge? {
        return Realm.getDefaultInstance().use { realm ->
            realm.where(CryptoPaymentChallenge::class.java)
                .equalTo("id", id)
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
        }
    }

    override fun updateChallengeStatus(
        id: String,
        status: CryptoPaymentChallenge.PaymentStatus,
        confirmations: Int,
        txHash: String
    ) {
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction {
                realm.where(CryptoPaymentChallenge::class.java)
                    .equalTo("id", id)
                    .findFirst()
                    ?.apply {
                        this.status = status
                        this.confirmations = confirmations
                        if (txHash.isNotEmpty()) this.txHash = txHash
                    }
            }
        }
    }

    override fun getWalletAddress(tokenType: CryptoPaymentChallenge.TokenType): String {
        return sharedPrefs.getString("${PREF_WALLET_PREFIX}${tokenType.name}", "") ?: ""
    }

    override fun setWalletAddress(tokenType: CryptoPaymentChallenge.TokenType, address: String) {
        sharedPrefs.edit().putString("${PREF_WALLET_PREFIX}${tokenType.name}", address).apply()
    }

    override fun getAlchemyApiKey(): String {
        return sharedPrefs.getString(PREF_ALCHEMY_KEY, "") ?: ""
    }

    override fun setAlchemyApiKey(key: String) {
        sharedPrefs.edit().putString(PREF_ALCHEMY_KEY, key).apply()
    }

    override fun getChallengePrice(): Int {
        return sharedPrefs.getInt(PREF_CHALLENGE_PRICE, DEFAULT_PRICE_CENTS)
    }

    override fun setChallengePrice(cents: Int) {
        sharedPrefs.edit().putInt(PREF_CHALLENGE_PRICE, cents.coerceAtLeast(0)).apply()
    }

    override fun isCryptoChallengeEnabled(): Boolean {
        return sharedPrefs.getBoolean(PREF_CRYPTO_ENABLED, false)
    }

    override fun setCryptoChallengeEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(PREF_CRYPTO_ENABLED, enabled).apply()
    }

    override fun getPreferredTokenType(): CryptoPaymentChallenge.TokenType {
        val name = sharedPrefs.getString(PREF_TOKEN_TYPE, CryptoPaymentChallenge.TokenType.ETH.name)
        return try {
            CryptoPaymentChallenge.TokenType.valueOf(name ?: CryptoPaymentChallenge.TokenType.ETH.name)
        } catch (e: IllegalArgumentException) {
            CryptoPaymentChallenge.TokenType.ETH
        }
    }

    override fun setPreferredTokenType(tokenType: CryptoPaymentChallenge.TokenType) {
        sharedPrefs.edit().putString(PREF_TOKEN_TYPE, tokenType.name).apply()
    }
}
