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
package com.callscreen.app.feature.conversations

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.callscreen.app.R
import com.callscreen.app.common.Navigator
import com.callscreen.app.common.base.QkBindingViewHolder
import com.callscreen.app.common.base.QkRealmAdapter
import com.callscreen.app.common.util.Colors
import com.callscreen.app.common.util.DateFormatter
import com.callscreen.app.common.util.extensions.resolveThemeColor
import com.callscreen.app.common.util.extensions.setTint
import com.callscreen.app.databinding.ConversationListItemBinding
import com.callscreen.app.model.Conversation
import com.callscreen.app.repository.ScheduledMessageRepository
import com.callscreen.app.util.PhoneNumberUtils
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import javax.inject.Inject

class ConversationsAdapter @Inject constructor(
    private val colors: Colors,
    private val context: Context,
    private val dateFormatter: DateFormatter,
    private val scheduledMessageRepo: ScheduledMessageRepository,
    private val navigator: Navigator,
    private val phoneNumberUtils: PhoneNumberUtils
) : QkRealmAdapter<Conversation, QkBindingViewHolder<ConversationListItemBinding>>() {
    private val disposables = CompositeDisposable()
    // Perf: track per-holder disposable so we dispose when the ViewHolder is rebound or recycled,
    // instead of leaking O(scroll-distance) Realm subscriptions until detach.
    private val holderDisposables = HashMap<RecyclerView.ViewHolder, Disposable>()

    init {
        // This is how we access the threadId for the swipe actions
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkBindingViewHolder<ConversationListItemBinding> {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = ConversationListItemBinding.inflate(layoutInflater, parent, false)

        if (viewType == 1) {
            val textColorPrimary = parent.context.resolveThemeColor(android.R.attr.textColorPrimary)

            binding.title.setTypeface(binding.title.typeface, Typeface.BOLD)

            binding.snippet.setTypeface(binding.snippet.typeface, Typeface.BOLD)
            binding.snippet.setTextColor(textColorPrimary)
            binding.snippet.maxLines = 5

            binding.unread.isVisible = true

            binding.date.setTypeface(binding.date.typeface, Typeface.BOLD)
            binding.date.setTextColor(textColorPrimary)
        }

        return QkBindingViewHolder(binding).apply {
            binding.root.setOnClickListener {
                val conversation = getItem(adapterPosition) ?: return@setOnClickListener
                when (toggleSelection(conversation.id, false)) {
                    true -> binding.root.isActivated = isSelected(conversation.id)
                    false -> navigator.showConversation(conversation.id)
                }
            }
            binding.root.setOnLongClickListener {
                val conversation = getItem(adapterPosition) ?: return@setOnLongClickListener true
                toggleSelection(conversation.id)
                binding.root.isActivated = isSelected(conversation.id)
                true
            }
        }
    }

    override fun onBindViewHolder(holder: QkBindingViewHolder<ConversationListItemBinding>, position: Int) {
        val conversation = getItem(position) ?: return
        val binding = holder.binding

        // If the last message wasn't incoming, then the colour doesn't really matter anyway
        val lastMessage = conversation.lastMessage
        val recipient = when {
            conversation.recipients.size == 1 || lastMessage == null -> conversation.recipients.firstOrNull()
            else -> conversation.recipients.find { recipient ->
                phoneNumberUtils.compare(recipient.address, lastMessage.address)
            }
        }
        val theme = colors.theme(recipient).theme

        holder.itemView.isActivated = isSelected(conversation.id)

        binding.avatars.recipients = conversation.recipients
        binding.title.collapseEnabled = conversation.recipients.size > 1
        // Perf: getTitle() returns plain text; no need for SpannableStringBuilder allocation
        binding.title.text = conversation.getTitle()
        binding.date.text = conversation.date.takeIf { it > 0 }?.let(dateFormatter::getConversationTimestamp)
        binding.snippet.text = when {
            conversation.draft.isNotEmpty() -> context.getString(R.string.main_sender_draft, conversation.draft)
            conversation.me -> context.getString(R.string.main_sender_you, conversation.snippet)
            else -> conversation.snippet
        }

        // Make the preview in italics if draft
        if (conversation.draft.isNotEmpty()) binding.snippet.setTypeface(null, Typeface.ITALIC)

        // Perf: dispose previous subscription for this ViewHolder before creating a new one.
        // Without this, each rebind leaks an additional Realm async query subscription.
        holderDisposables.remove(holder)?.dispose()
        val disposable = scheduledMessageRepo
            .getScheduledMessagesForConversation(conversation.id)
            .asFlowable()
            .toObservable()
            .subscribe { messages ->
                binding.scheduled.isVisible = messages.isNotEmpty()
            }
        holderDisposables[holder] = disposable
        disposables.add(disposable)

        binding.pinned.isVisible = conversation.pinned
        binding.unread.setTint(theme)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position)?.id ?: -1
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position)?.unread == false) 0 else 1
    }

    // Perf: clean up per-holder subscription when the ViewHolder is recycled
    override fun onViewRecycled(holder: QkBindingViewHolder<ConversationListItemBinding>) {
        super.onViewRecycled(holder)
        holderDisposables.remove(holder)?.dispose()
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        holderDisposables.clear()
        disposables.clear()
    }


}
