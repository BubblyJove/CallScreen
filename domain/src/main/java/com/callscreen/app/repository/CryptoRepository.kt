/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.callscreen.app.repository

import com.callscreen.app.model.CryptoPaymentChallenge
import io.reactivex.Flowable

interface CryptoRepository {

    fun createPaymentChallenge(
        phoneNumber: String,
        usdCents: Int,
        tokenType: CryptoPaymentChallenge.TokenType,
        walletAddress: String,
        exactAmount: String
    ): CryptoPaymentChallenge

    fun getActivePaymentChallenge(phoneNumber: String): CryptoPaymentChallenge?

    fun getPaymentChallengeById(id: String): CryptoPaymentChallenge?

    fun updateChallengeStatus(
        id: String,
        status: CryptoPaymentChallenge.PaymentStatus,
        confirmations: Int = 0,
        txHash: String = ""
    )

    fun getWalletAddress(tokenType: CryptoPaymentChallenge.TokenType): String

    fun setWalletAddress(tokenType: CryptoPaymentChallenge.TokenType, address: String)

    fun getAlchemyApiKey(): String

    fun setAlchemyApiKey(key: String)

    fun getChallengePrice(): Int // USD cents

    fun setChallengePrice(cents: Int)

    fun isCryptoChallengeEnabled(): Boolean

    fun setCryptoChallengeEnabled(enabled: Boolean)

    fun getPreferredTokenType(): CryptoPaymentChallenge.TokenType

    fun setPreferredTokenType(tokenType: CryptoPaymentChallenge.TokenType)

}
