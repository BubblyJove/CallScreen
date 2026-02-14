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

import com.callscreen.app.model.MessageContentFilter
import com.callscreen.app.model.MessageContentFilterData
import io.realm.Realm
import io.realm.RealmResults
import javax.inject.Inject

class MessageContentFilterRepositoryImpl @Inject constructor() : MessageContentFilterRepository {

    // Perf: cache compiled Regex objects keyed by their pattern string.
    // isBlocked() is called for every incoming message, and Regex compilation
    // is expensive. Filters rarely change, so the cache stays warm.
    private val regexCache = HashMap<String, Regex>(16)

    private fun getCachedRegex(pattern: String): Regex {
        return regexCache.getOrPut(pattern) { Regex(pattern) }
    }

    override fun createFilter(data: MessageContentFilterData) {
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()
            val maxId = realm.where(MessageContentFilter::class.java)
                .max("id")?.toLong() ?: -1

            realm.executeTransaction {
                realm.insert(MessageContentFilter(maxId + 1, data.value, data.caseSensitive, data.isRegex, data.includeContacts))
            }
        }
        // Perf: invalidate cache when filters change
        regexCache.clear()
    }

    override fun getMessageContentFilters(): RealmResults<MessageContentFilter> {
        return Realm.getDefaultInstance()
            .where(MessageContentFilter::class.java)
            .findAllAsync()
    }

    override fun getMessageContentFilter(id: Long): MessageContentFilter? {
        return Realm.getDefaultInstance()
            .where(MessageContentFilter::class.java)
            .equalTo("id", id)
            .findFirst()
    }

    override fun isBlocked(messageBody: String, address: String, contactsRepo: ContactRepository): Boolean {
        val isContact = contactsRepo.isContact(address)
        // Perf: pre-compute lowercase body once, outside the loop
        var lowercaseBody: String? = null

        return Realm.getDefaultInstance().use { realm ->
            realm.where(MessageContentFilter::class.java)
                .findAll()
                .any { filter ->
                    if (isContact && !filter.includeContacts) {
                        false
                    } else if (filter.isRegex) {
                        getCachedRegex(filter.value).matches(messageBody)
                    } else if (filter.caseSensitive) {
                        val regexp = "[\\s\\S]*\\b" + Regex.escape(filter.value) + "\\b[\\s\\S]*"
                        getCachedRegex(regexp).matches(messageBody)
                    } else {
                        // Perf: lazy-compute lowercase body only when needed
                        if (lowercaseBody == null) lowercaseBody = messageBody.lowercase()
                        val regexp = "[\\s\\S]*\\b" + Regex.escape(filter.value.lowercase()) + "\\b[\\s\\S]*"
                        getCachedRegex(regexp).matches(lowercaseBody!!)
                    }
                }
        }
    }

    override fun removeFilter(id: Long) {
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction {
                realm.where(MessageContentFilter::class.java)
                    .equalTo("id", id)
                    .findAll()
                    .deleteAllFromRealm()
            }
        }
        // Perf: invalidate cache when filters change
        regexCache.clear()
    }

}
