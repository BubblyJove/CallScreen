package com.callscreen.app.repository

import com.callscreen.app.model.ChallengeState
import com.callscreen.app.model.PendingScreenedMessage
import com.callscreen.app.model.WhitelistedContact
import com.callscreen.app.util.PhoneNumberUtils
import io.reactivex.Flowable
import io.realm.Realm
import io.realm.RealmResults
import timber.log.Timber
import javax.inject.Inject

class ScreeningRepositoryImpl @Inject constructor(
    private val phoneNumberUtils: PhoneNumberUtils
) : ScreeningRepository {

    companion object {
        private const val MAX_WHITELIST_SCAN = 10_000
        private const val MAX_PENDING_MESSAGES = 1_000
    }

    override fun isWhitelisted(phoneNumber: String): Boolean {
        if (phoneNumber.isBlank()) return false
        return Realm.getDefaultInstance().use { realm ->
            // Perf: try direct equality match first (O(1) via Realm index) before
            // falling back to the expensive phoneNumberUtils.compare() scan
            val normalized = phoneNumberUtils.normalizeNumber(phoneNumber)
            val directMatch = realm.where(WhitelistedContact::class.java)
                .equalTo("phoneNumber", phoneNumber)
                .or()
                .equalTo("phoneNumber", normalized)
                .findFirst()
            if (directMatch != null) return@use true

            // Perf: fall back to full comparison only if direct match fails
            realm.where(WhitelistedContact::class.java)
                .limit(MAX_WHITELIST_SCAN.toLong())
                .findAll()
                .any { contact -> phoneNumberUtils.compare(contact.phoneNumber, phoneNumber) }
        }
    }

    override fun whitelistContact(
        phoneNumber: String,
        displayName: String,
        source: WhitelistedContact.WhitelistSource
    ) {
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
    }

    override fun removeWhitelistedContact(phoneNumber: String) {
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction {
                realm.where(WhitelistedContact::class.java)
                    .equalTo("phoneNumber", phoneNumber)
                    .findAll()
                    .deleteAllFromRealm()
            }
        }
    }

    override fun getWhitelistedContacts(): RealmResults<WhitelistedContact> {
        return Realm.getDefaultInstance()
            .where(WhitelistedContact::class.java)
            .sort("whitelistedAt", io.realm.Sort.DESCENDING)
            .findAllAsync()
    }

    override fun getWhitelistedContactsFlowable(): Flowable<RealmResults<WhitelistedContact>> {
        val realm = Realm.getDefaultInstance()
        return realm.where(WhitelistedContact::class.java)
            .sort("whitelistedAt", io.realm.Sort.DESCENDING)
            .findAllAsync()
            .asFlowable()
            .filter { it.isLoaded }
    }

    override fun getChallengeForNumber(phoneNumber: String): ChallengeState? {
        return Realm.getDefaultInstance().use { realm ->
            realm.where(ChallengeState::class.java)
                .equalTo("phoneNumber", phoneNumber)
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
        }
    }

    override fun getActiveChallenges(): RealmResults<ChallengeState> {
        return Realm.getDefaultInstance()
            .where(ChallengeState::class.java)
            .greaterThan("expiresAt", System.currentTimeMillis())
            .sort("createdAt", io.realm.Sort.DESCENDING)
            .findAllAsync()
    }

    override fun saveChallengeState(challenge: ChallengeState) {
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction { r ->
                r.insertOrUpdate(challenge)
            }
        }
    }

    override fun deleteChallengeState(phoneNumber: String) {
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction {
                realm.where(ChallengeState::class.java)
                    .equalTo("phoneNumber", phoneNumber)
                    .findAll()
                    .deleteAllFromRealm()
            }
        }
    }

    override fun incrementAttempts(phoneNumber: String) {
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction {
                realm.where(ChallengeState::class.java)
                    .equalTo("phoneNumber", phoneNumber)
                    .findFirst()
                    ?.let { it.attempts += 1 }
            }
        }
    }

    override fun insertPendingMessage(
        phoneNumber: String,
        body: String,
        type: PendingScreenedMessage.MessageType
    ) {
        if (phoneNumber.isBlank()) {
            Timber.w("Ignoring pending message with blank phone number")
            return
        }
        Realm.getDefaultInstance().use { realm ->
            val pendingCount = realm.where(PendingScreenedMessage::class.java)
                .equalTo("statusString", PendingScreenedMessage.MessageStatus.HELD.name)
                .count()
            if (pendingCount >= MAX_PENDING_MESSAGES) {
                Timber.w("Pending message cap reached (%d), dropping message from %s", pendingCount, phoneNumber)
                return
            }
            // Perf: compute maxId inside the transaction to avoid extra Realm snapshot
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
            }
        }
    }

    override fun getPendingMessages(): RealmResults<PendingScreenedMessage> {
        return Realm.getDefaultInstance()
            .where(PendingScreenedMessage::class.java)
            .equalTo("statusString", PendingScreenedMessage.MessageStatus.HELD.name)
            .sort("timestamp", io.realm.Sort.DESCENDING)
            .findAllAsync()
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
            realm.copyFromRealm(results)
        }
    }

    override fun deliverPendingMessages(phoneNumber: String) {
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction {
                realm.where(PendingScreenedMessage::class.java)
                    .equalTo("phoneNumber", phoneNumber)
                    .equalTo("statusString", PendingScreenedMessage.MessageStatus.HELD.name)
                    .findAll()
                    .forEach { msg ->
                        msg.status = PendingScreenedMessage.MessageStatus.DELIVERED
                    }
            }
        }
    }

    override fun rejectPendingMessages(phoneNumber: String) {
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction {
                realm.where(PendingScreenedMessage::class.java)
                    .equalTo("phoneNumber", phoneNumber)
                    .equalTo("statusString", PendingScreenedMessage.MessageStatus.HELD.name)
                    .findAll()
                    .forEach { msg ->
                        msg.status = PendingScreenedMessage.MessageStatus.REJECTED
                    }
            }
        }
    }

    override fun deletePendingMessage(id: Long) {
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
