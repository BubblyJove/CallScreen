package com.callscreen.app.repository

import com.callscreen.app.model.ChallengeState
import com.callscreen.app.model.PendingScreenedMessage
import com.callscreen.app.model.WhitelistedContact
import com.callscreen.app.util.PhoneNumberUtils
import com.callscreen.app.util.ScreenLog
import io.reactivex.Flowable
import io.realm.Realm
import io.realm.RealmResults
import javax.inject.Inject

class ScreeningRepositoryImpl @Inject constructor(
    private val phoneNumberUtils: PhoneNumberUtils
) : ScreeningRepository {

    companion object {
        private const val TAG = "ScreeningRepo"
        private const val MAX_WHITELIST_SCAN = 10_000
        private const val MAX_PENDING_MESSAGES = 1_000
    }

    override fun isWhitelisted(phoneNumber: String): Boolean {
        if (phoneNumber.isBlank()) return false
        return Realm.getDefaultInstance().use { realm ->
            val normalized = phoneNumberUtils.normalizeNumber(phoneNumber)
            val directMatch = realm.where(WhitelistedContact::class.java)
                .equalTo("phoneNumber", phoneNumber)
                .or()
                .equalTo("phoneNumber", normalized)
                .findFirst()
            if (directMatch != null) {
                ScreenLog.d(TAG, "isWhitelisted($phoneNumber): YES (direct match)")
                return@use true
            }

            val fuzzyMatch = realm.where(WhitelistedContact::class.java)
                .limit(MAX_WHITELIST_SCAN.toLong())
                .findAll()
                .any { contact -> phoneNumberUtils.compare(contact.phoneNumber, phoneNumber) }

            ScreenLog.d(TAG, "isWhitelisted($phoneNumber): ${if (fuzzyMatch) "YES (fuzzy)" else "NO"}")
            fuzzyMatch
        }
    }

    override fun whitelistContact(
        phoneNumber: String,
        displayName: String,
        source: WhitelistedContact.WhitelistSource
    ) {
        ScreenLog.d(TAG, "whitelistContact: phone=$phoneNumber name=$displayName source=$source")
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction { r ->
                val contact = WhitelistedContact().apply {
                    this.phoneNumber = phoneNumber
                    this.displayName = displayName
                    this.whitelistedAt = System.currentTimeMillis()
                    this.source = source
                }
                r.insertOrUpdate(contact)
            }
        }
        ScreenLog.d(TAG, "whitelistContact: DONE for $phoneNumber")
    }

    override fun removeWhitelistedContact(phoneNumber: String) {
        ScreenLog.d(TAG, "removeWhitelistedContact: $phoneNumber")
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction {
                val count = realm.where(WhitelistedContact::class.java)
                    .equalTo("phoneNumber", phoneNumber)
                    .findAll()
                count.deleteAllFromRealm()
            }
        }
    }

    override fun getWhitelistedContacts(): RealmResults<WhitelistedContact> {
        val results = Realm.getDefaultInstance()
            .where(WhitelistedContact::class.java)
            .sort("whitelistedAt", io.realm.Sort.DESCENDING)
            .findAllAsync()
        ScreenLog.d(TAG, "getWhitelistedContacts: returning async query")
        return results
    }

    override fun getWhitelistedContactsFlowable(): Flowable<RealmResults<WhitelistedContact>> {
        val realm = Realm.getDefaultInstance()
        return realm.where(WhitelistedContact::class.java)
            .sort("whitelistedAt", io.realm.Sort.DESCENDING)
            .findAllAsync()
            .asFlowable()
            .filter { it.isLoaded }
            .doOnCancel { realm.close() }
    }

    override fun getChallengeForNumber(phoneNumber: String): ChallengeState? {
        return Realm.getDefaultInstance().use { realm ->
            val result = realm.where(ChallengeState::class.java)
                .equalTo("phoneNumber", phoneNumber)
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
            ScreenLog.d(TAG, "getChallengeForNumber($phoneNumber): ${if (result != null) "found type=${result.type} expired=${result.isExpired()}" else "none"}")
            result
        }
    }

    override fun getActiveChallenges(): RealmResults<ChallengeState> {
        val now = System.currentTimeMillis()
        val results = Realm.getDefaultInstance()
            .where(ChallengeState::class.java)
            .greaterThan("expiresAt", now)
            .sort("createdAt", io.realm.Sort.DESCENDING)
            .findAllAsync()
        ScreenLog.d(TAG, "getActiveChallenges: returning async query (expiresAt > $now)")
        return results
    }

    override fun saveChallengeState(challenge: ChallengeState) {
        ScreenLog.d(TAG, "saveChallengeState: phone=${challenge.phoneNumber} type=${challenge.type} " +
            "question=${challenge.challengeQuestion.take(60)} expires=${challenge.expiresAt}")
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction { r ->
                r.insertOrUpdate(challenge)
            }
        }
        ScreenLog.d(TAG, "saveChallengeState: DONE for ${challenge.phoneNumber}")
    }

    override fun deleteChallengeState(phoneNumber: String) {
        ScreenLog.d(TAG, "deleteChallengeState: $phoneNumber")
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction {
                val deleted = realm.where(ChallengeState::class.java)
                    .equalTo("phoneNumber", phoneNumber)
                    .findAll()
                val count = deleted.size
                deleted.deleteAllFromRealm()
                ScreenLog.d(TAG, "deleteChallengeState: removed $count entries for $phoneNumber")
            }
        }
    }

    override fun incrementAttempts(phoneNumber: String) {
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction {
                realm.where(ChallengeState::class.java)
                    .equalTo("phoneNumber", phoneNumber)
                    .findFirst()
                    ?.let {
                        it.attempts += 1
                        ScreenLog.d(TAG, "incrementAttempts: $phoneNumber now at ${it.attempts}")
                    }
            }
        }
    }

    override fun insertPendingMessage(
        phoneNumber: String,
        body: String,
        type: PendingScreenedMessage.MessageType
    ) {
        if (phoneNumber.isBlank()) {
            ScreenLog.w(TAG, "insertPendingMessage: REJECTED — blank phone number")
            return
        }
        Realm.getDefaultInstance().use { realm ->
            val pendingCount = realm.where(PendingScreenedMessage::class.java)
                .equalTo("statusString", PendingScreenedMessage.MessageStatus.HELD.name)
                .count()
            if (pendingCount >= MAX_PENDING_MESSAGES) {
                ScreenLog.w(TAG, "insertPendingMessage: REJECTED — cap reached ($pendingCount/$MAX_PENDING_MESSAGES) for $phoneNumber")
                return
            }
            realm.executeTransaction { r ->
                val maxId = r.where(PendingScreenedMessage::class.java)
                    .max("id")?.toLong() ?: 0
                val msg = PendingScreenedMessage().apply {
                    this.id = maxId + 1
                    this.phoneNumber = phoneNumber
                    this.body = body
                    this.timestamp = System.currentTimeMillis()
                    this.type = type
                    this.status = PendingScreenedMessage.MessageStatus.HELD
                }
                r.insert(msg)
                ScreenLog.d(TAG, "insertPendingMessage: SAVED id=${msg.id} phone=$phoneNumber " +
                    "body='${body.take(40)}' type=$type status=HELD")
            }
        }
    }

    override fun getPendingMessages(): RealmResults<PendingScreenedMessage> {
        val results = Realm.getDefaultInstance()
            .where(PendingScreenedMessage::class.java)
            .equalTo("statusString", PendingScreenedMessage.MessageStatus.HELD.name)
            .sort("timestamp", io.realm.Sort.DESCENDING)
            .findAllAsync()
        ScreenLog.d(TAG, "getPendingMessages: returning async query (status=HELD)")
        return results
    }

    override fun getPendingMessagesForNumber(phoneNumber: String): RealmResults<PendingScreenedMessage> {
        return Realm.getDefaultInstance()
            .where(PendingScreenedMessage::class.java)
            .equalTo("phoneNumber", phoneNumber)
            .equalTo("statusString", PendingScreenedMessage.MessageStatus.HELD.name)
            .sort("timestamp", io.realm.Sort.ASCENDING)
            .findAllAsync()
    }

    override fun getPendingMessagesForNumberSync(phoneNumber: String): List<PendingScreenedMessage> {
        return Realm.getDefaultInstance().use { realm ->
            val results = realm.where(PendingScreenedMessage::class.java)
                .equalTo("phoneNumber", phoneNumber)
                .equalTo("statusString", PendingScreenedMessage.MessageStatus.HELD.name)
                .sort("timestamp", io.realm.Sort.ASCENDING)
                .findAll()
            val copied = realm.copyFromRealm(results)
            ScreenLog.d(TAG, "getPendingMessagesForNumberSync($phoneNumber): found ${copied.size} HELD messages")
            copied
        }
    }

    override fun deliverPendingMessages(phoneNumber: String) {
        ScreenLog.d(TAG, "deliverPendingMessages: $phoneNumber")
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction {
                val msgs = realm.where(PendingScreenedMessage::class.java)
                    .equalTo("phoneNumber", phoneNumber)
                    .equalTo("statusString", PendingScreenedMessage.MessageStatus.HELD.name)
                    .findAll()
                ScreenLog.d(TAG, "deliverPendingMessages: marking ${msgs.size} messages as DELIVERED for $phoneNumber")
                msgs.forEach { msg ->
                    msg.status = PendingScreenedMessage.MessageStatus.DELIVERED
                }
            }
        }
    }

    override fun rejectPendingMessages(phoneNumber: String) {
        ScreenLog.d(TAG, "rejectPendingMessages: $phoneNumber")
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction {
                val msgs = realm.where(PendingScreenedMessage::class.java)
                    .equalTo("phoneNumber", phoneNumber)
                    .equalTo("statusString", PendingScreenedMessage.MessageStatus.HELD.name)
                    .findAll()
                ScreenLog.d(TAG, "rejectPendingMessages: marking ${msgs.size} messages as REJECTED for $phoneNumber")
                msgs.forEach { msg ->
                    msg.status = PendingScreenedMessage.MessageStatus.REJECTED
                }
            }
        }
    }

    override fun deletePendingMessage(id: Long) {
        ScreenLog.d(TAG, "deletePendingMessage: id=$id")
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction {
                realm.where(PendingScreenedMessage::class.java)
                    .equalTo("id", id)
                    .findAll()
                    .deleteAllFromRealm()
            }
        }
    }
}
