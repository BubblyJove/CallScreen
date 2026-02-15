package com.callscreen.app.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.callscreen.app.model.CryptoPaymentChallenge
import com.callscreen.app.util.ScreenLog
import io.realm.Realm
import java.util.UUID
import javax.inject.Inject

class CryptoRepositoryImpl @Inject constructor(
    private val context: Context,
    private val sharedPrefs: SharedPreferences
) : CryptoRepository {

    companion object {
        private const val TAG = "CryptoRepo"
        private const val PREF_CRYPTO_ENABLED = "crypto_challenge_enabled"
        private const val PREF_CHALLENGE_PRICE = "crypto_challenge_price_cents"
        private const val PREF_TOKEN_TYPE = "crypto_preferred_token"
        private const val PREF_ALCHEMY_KEY = "alchemy_api_key"
        private const val PREF_WALLET_PREFIX = "crypto_wallet_"
        private const val DEFAULT_PRICE_CENTS = 20 // $0.20
        private const val ENCRYPTED_PREFS_FILE = "crypto_prefs"
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            ENCRYPTED_PREFS_FILE,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ).also { encrypted ->
            migrateToEncryptedPrefs(encrypted)
        }
    }

    private fun migrateToEncryptedPrefs(encrypted: SharedPreferences) {
        val oldKey = sharedPrefs.getString(PREF_ALCHEMY_KEY, null)
        if (oldKey != null) {
            val editor = encrypted.edit()
            editor.putString(PREF_ALCHEMY_KEY, oldKey)
            for (tokenType in CryptoPaymentChallenge.TokenType.values()) {
                val walletKey = "${PREF_WALLET_PREFIX}${tokenType.name}"
                sharedPrefs.getString(walletKey, null)?.let { editor.putString(walletKey, it) }
            }
            editor.apply()

            val clearEditor = sharedPrefs.edit()
            clearEditor.remove(PREF_ALCHEMY_KEY)
            for (tokenType in CryptoPaymentChallenge.TokenType.values()) {
                clearEditor.remove("${PREF_WALLET_PREFIX}${tokenType.name}")
            }
            clearEditor.apply()
            ScreenLog.d(TAG, "Migrated sensitive keys to encrypted storage")
        }
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

        ScreenLog.d(TAG, "createPaymentChallenge: phone=$phoneNumber amount=$exactAmount " +
            "$tokenType wallet=$walletAddress id=${challenge.id}")

        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction { r ->
                r.insertOrUpdate(challenge)
            }
        }

        ScreenLog.d(TAG, "createPaymentChallenge: SAVED to Realm, id=${challenge.id}")
        return challenge
    }

    override fun getActivePaymentChallenge(phoneNumber: String): CryptoPaymentChallenge? {
        return Realm.getDefaultInstance().use { realm ->
            // Perf: use `in()` instead of `or()` with repeated equalTo — single index scan
            realm.where(CryptoPaymentChallenge::class.java)
                .equalTo("phoneNumber", phoneNumber)
                .`in`("statusString", arrayOf(
                    CryptoPaymentChallenge.PaymentStatus.PENDING.name,
                    CryptoPaymentChallenge.PaymentStatus.CONFIRMING.name
                ))
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
        ScreenLog.d(TAG, "updateChallengeStatus: id=$id status=$status confirmations=$confirmations txHash=${txHash.take(20)}")
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction {
                realm.where(CryptoPaymentChallenge::class.java)
                    .equalTo("id", id)
                    .findFirst()
                    ?.apply {
                        this.status = status
                        this.confirmations = confirmations
                        if (txHash.isNotEmpty()) this.txHash = txHash
                    } ?: ScreenLog.w(TAG, "updateChallengeStatus: challenge $id NOT FOUND in Realm")
            }
        }
    }

    override fun getWalletAddress(tokenType: CryptoPaymentChallenge.TokenType): String {
        return encryptedPrefs.getString("${PREF_WALLET_PREFIX}${tokenType.name}", "") ?: ""
    }

    override fun setWalletAddress(tokenType: CryptoPaymentChallenge.TokenType, address: String) {
        encryptedPrefs.edit().putString("${PREF_WALLET_PREFIX}${tokenType.name}", address).apply()
    }

    override fun getAlchemyApiKey(): String {
        val key = encryptedPrefs.getString(PREF_ALCHEMY_KEY, "") ?: ""
        ScreenLog.d(TAG, "getAlchemyApiKey: ${if (key.isBlank()) "BLANK (not configured!)" else "present (${key.length} chars)"}")
        return key
    }

    override fun setAlchemyApiKey(key: String) {
        encryptedPrefs.edit().putString(PREF_ALCHEMY_KEY, key).apply()
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

    override fun getActivePendingChallenges(): List<CryptoPaymentChallenge> {
        return Realm.getDefaultInstance().use { realm ->
            val results = realm.where(CryptoPaymentChallenge::class.java)
                .`in`("statusString", arrayOf(
                    CryptoPaymentChallenge.PaymentStatus.PENDING.name,
                    CryptoPaymentChallenge.PaymentStatus.CONFIRMING.name
                ))
                .greaterThan("expiresAt", System.currentTimeMillis())
                .findAll()
            val copied = realm.copyFromRealm(results)
            ScreenLog.d(TAG, "getActivePendingChallenges: found ${copied.size} active challenges")
            copied.forEach { c ->
                ScreenLog.d(TAG, "  challenge: phone=${c.phoneNumber} amount=${c.exactAmount} " +
                    "${c.tokenType.name} status=${c.status.name} id=${c.id}")
            }
            copied
        }
    }
}
