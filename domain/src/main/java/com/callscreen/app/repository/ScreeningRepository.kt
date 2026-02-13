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

import com.callscreen.app.model.ChallengeState
import com.callscreen.app.model.PendingScreenedMessage
import com.callscreen.app.model.WhitelistedContact
import io.reactivex.Flowable
import io.realm.RealmResults

interface ScreeningRepository {

    fun isWhitelisted(phoneNumber: String): Boolean

    fun whitelistContact(phoneNumber: String, displayName: String, source: WhitelistedContact.WhitelistSource)

    fun removeWhitelistedContact(phoneNumber: String)

    fun getWhitelistedContacts(): RealmResults<WhitelistedContact>

    fun getWhitelistedContactsFlowable(): Flowable<RealmResults<WhitelistedContact>>

    fun getChallengeForNumber(phoneNumber: String): ChallengeState?

    fun saveChallengeState(challenge: ChallengeState)

    fun deleteChallengeState(phoneNumber: String)

    fun incrementAttempts(phoneNumber: String)

    fun insertPendingMessage(phoneNumber: String, body: String, type: PendingScreenedMessage.MessageType)

    fun getPendingMessages(): RealmResults<PendingScreenedMessage>

    fun getPendingMessagesForNumber(phoneNumber: String): RealmResults<PendingScreenedMessage>

    fun deliverPendingMessages(phoneNumber: String)

    fun rejectPendingMessages(phoneNumber: String)

    fun deletePendingMessage(id: Long)

}
