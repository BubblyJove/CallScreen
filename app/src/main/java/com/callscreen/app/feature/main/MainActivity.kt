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
package com.callscreen.app.feature.main

import android.Manifest
import android.animation.ObjectAnimator
import android.app.role.RoleManager
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewStub
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.snackbar.Snackbar
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.widget.textChanges
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDispose
import dagger.android.AndroidInjection
import com.callscreen.app.R
import com.callscreen.app.common.Navigator
import com.callscreen.app.common.androidxcompat.drawerOpen
import com.callscreen.app.feature.onboarding.OnboardingActivity
import com.callscreen.app.common.base.QkThemedActivity
import com.callscreen.app.common.util.extensions.autoScrollToStart
import com.callscreen.app.common.util.extensions.dismissKeyboard
import com.callscreen.app.common.util.extensions.resolveThemeColor
import com.callscreen.app.common.util.extensions.scrapViews
import com.callscreen.app.common.util.extensions.setBackgroundTint
import com.callscreen.app.common.util.extensions.setTint
import com.callscreen.app.common.util.extensions.setVisible
import com.callscreen.app.common.widget.TextInputDialog
import com.callscreen.app.feature.blocking.BlockingDialog
import com.callscreen.app.databinding.MainActivityBinding
import com.callscreen.app.databinding.MainPermissionHintBinding
import com.callscreen.app.databinding.MainSyncingBinding
import com.callscreen.app.feature.changelog.ChangelogDialog
import com.callscreen.app.feature.conversations.ConversationItemTouchCallback
import com.callscreen.app.feature.conversations.ConversationsAdapter
import com.callscreen.app.manager.ChangelogManager
import com.callscreen.app.repository.SyncRepository
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

class MainActivity : QkThemedActivity(), MainView {

    @Inject lateinit var blockingDialog: BlockingDialog
    @Inject lateinit var disposables: CompositeDisposable
    @Inject lateinit var navigator: Navigator
    @Inject lateinit var conversationsAdapter: ConversationsAdapter
    @Inject lateinit var drawerBadgesExperiment: DrawerBadgesExperiment
    @Inject lateinit var searchAdapter: SearchAdapter
    @Inject lateinit var itemTouchCallback: ConversationItemTouchCallback
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var binding: MainActivityBinding
    private lateinit var snackbarBinding: MainPermissionHintBinding
    private lateinit var syncingBinding: MainSyncingBinding

    override val onNewIntentIntent: Subject<Intent> = PublishSubject.create()
    override val activityResumedIntent: Subject<Boolean> = PublishSubject.create()
    override val queryChangedIntent by lazy { binding.toolbarSearch.textChanges() }
    override val composeIntent by lazy { binding.compose.clicks() }
    override val drawerToggledIntent: Observable<Boolean> by lazy {
        binding.drawerLayout.drawerOpen(Gravity.START)
    }
    override val homeIntent: Subject<Unit> = PublishSubject.create()
    override val navigationIntent: Observable<NavItem> by lazy {
        Observable.merge(listOf(
                backPressedSubject,
                binding.drawer.inbox.clicks().map { NavItem.INBOX },
                binding.drawer.archived.clicks().map { NavItem.ARCHIVED },
                binding.drawer.backup.clicks().map { NavItem.BACKUP },
                binding.drawer.scheduled.clicks().map { NavItem.SCHEDULED },
                binding.drawer.blocking.clicks().map { NavItem.BLOCKING },
                binding.drawer.messageUtils.clicks().map { NavItem.MESSAGE_UTILS },
                binding.drawer.screening.clicks().map { NavItem.SCREENING },
                binding.drawer.debugLog.clicks().map { NavItem.DEBUG_LOG },
                binding.drawer.settings.clicks().map { NavItem.SETTINGS },
//                plus.clicks().map { NavItem.PLUS },
//                help.clicks().map { NavItem.HELP },
                binding.drawer.invite.clicks().map { NavItem.INVITE }))
    }
    override val optionsItemIntent: Subject<Int> = PublishSubject.create()
//    override val plusBannerIntent by lazy { plusBanner.clicks() }
    override val dismissRatingIntent by lazy { binding.drawer.rateDismiss.clicks() }
    override val rateIntent by lazy { binding.drawer.rateOkay.clicks() }
    override val conversationsSelectedIntent by lazy { conversationsAdapter.selectionChanges }
    override val confirmDeleteIntent: Subject<List<Long>> = PublishSubject.create()
    override val renameConversationIntent: Subject<String> = PublishSubject.create()
    override val swipeConversationIntent by lazy { itemTouchCallback.swipes }
    override val changelogMoreIntent by lazy { changelogDialog.moreClicks }
    override val undoArchiveIntent: Subject<Unit> = PublishSubject.create()
    override val snackbarButtonIntent: Subject<Unit> = PublishSubject.create()

    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[MainViewModel::class.java]
    }
    private val toggle by lazy {
        ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.main_drawer_open_cd,
            0
        )
    }
    private val itemTouchHelper by lazy { ItemTouchHelper(itemTouchCallback) }
    private val progressAnimator by lazy {
        ObjectAnimator.ofInt(syncingBinding.syncingProgress, "progress", 0, 0)
    }
    private val changelogDialog by lazy { ChangelogDialog(this) }
    private val backPressedSubject: Subject<NavItem> = PublishSubject.create()

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)

        if (!prefs.onboardingComplete.get()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewModel.bindView(this)
        onNewIntentIntent.onNext(intent)

        snackbarBinding = MainPermissionHintBinding.bind(binding.snackbar.inflate()).also {
            it.snackbarButton.clicks()
                .autoDispose(scope(Lifecycle.Event.ON_DESTROY))
                .subscribe(snackbarButtonIntent)
        }

        syncingBinding = MainSyncingBinding.bind(binding.syncing.inflate()).also {
            it.syncingProgress.progressTintList = ColorStateList.valueOf(theme.blockingFirst().theme)
            it.syncingProgress.indeterminateTintList = ColorStateList.valueOf(theme.blockingFirst().theme)
        }

        toggle.syncState()
        binding.toolbar.setNavigationOnClickListener {
            dismissKeyboard()
            homeIntent.onNext(Unit)
        }

        itemTouchCallback.adapter = conversationsAdapter
        conversationsAdapter.autoScrollToStart(binding.recyclerView)

        // Don't allow clicks to pass through the drawer layout
        binding.drawer.root.clicks().autoDispose(scope()).subscribe()

        // Set the theme color tint to the recyclerView, progressbar, and FAB
        theme
                .autoDispose(scope())
                .subscribe { theme ->
                    // Set the color for the drawer icons
                    val states = arrayOf(
                            intArrayOf(android.R.attr.state_activated),
                            intArrayOf(-android.R.attr.state_activated))

                    ColorStateList(states, intArrayOf(theme.theme,
                        resolveThemeColor(android.R.attr.textColorSecondary)
                    ))
                        .let { tintList ->
                            binding.drawer.inboxIcon.imageTintList = tintList
                            binding.drawer.archivedIcon.imageTintList = tintList
                        }

                    // Miscellaneous views
                    listOf(binding.drawer.plusBadge1, binding.drawer.plusBadge2).forEach { badge ->
                        badge.setBackgroundTint(theme.theme)
                        badge.setTextColor(theme.textPrimary)
                    }
                    syncingBinding.syncingProgress.progressTintList = ColorStateList.valueOf(theme.theme)
                    syncingBinding.syncingProgress.indeterminateTintList = ColorStateList.valueOf(theme.theme)
                    binding.drawer.plusIcon.setTint(theme.theme)
                    binding.drawer.rateIcon.setTint(theme.theme)
                    binding.compose.setBackgroundTint(theme.theme)

                    // Set the FAB compose icon color
                    binding.compose.setTint(theme.textPrimary)
                }

        // Prompt for Default SMS + Call Screening roles
        promptRolesIfNeeded()
    }

    /**
     * Prompt user for Default SMS and Call Screening roles if not already granted.
     * Quik already handles Default SMS; we add Call Screening role prompt.
     */
    private fun promptRolesIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)

            // Prompt for call screening role
            if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                try {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                    startActivityForResult(intent, REQUEST_CALL_SCREENING_ROLE)
                } catch (e: Exception) {
                    timber.log.Timber.w(e, "Failed to request call screening role")
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) =
        intent?.let {
            super.onNewIntent(intent)
            it.run(onNewIntentIntent::onNext)
        } ?: Unit

    override fun render(state: MainState) {
        if (state.hasError) {
            finish()
            return
        }

        val addContact = when (state.page) {
            is Inbox -> state.page.addContact
            is Archived -> state.page.addContact
            else -> false
        }

        val markPinned = when (state.page) {
            is Inbox -> state.page.markPinned
            is Archived -> state.page.markPinned
            else -> true
        }

        val markRead = when (state.page) {
            is Inbox -> state.page.markRead
            is Archived -> state.page.markRead
            else -> true
        }

        val selectedConversations = when (state.page) {
            is Inbox -> state.page.selected
            is Archived -> state.page.selected
            else -> 0
        }

        binding.toolbarSearch.setVisible(state.page is Inbox &&
                state.page.selected == 0 ||
                state.page is Searching
        )
        binding.toolbarTitle.setVisible(binding.toolbarSearch.visibility != View.VISIBLE)

        binding.toolbar.menu.apply {
            findItem(R.id.select_all)?.isVisible =
                (conversationsAdapter.itemCount > 1) && selectedConversations != 0
            findItem(R.id.archive)?.isVisible =
                state.page is Inbox && selectedConversations != 0
            findItem(R.id.unarchive)?.isVisible =
                state.page is Archived && selectedConversations != 0
            findItem(R.id.delete)?.isVisible = selectedConversations != 0
            findItem(R.id.add)?.isVisible = addContact && selectedConversations != 0
            findItem(R.id.pin)?.isVisible = markPinned && selectedConversations != 0
            findItem(R.id.unpin)?.isVisible = !markPinned && selectedConversations != 0
            findItem(R.id.read)?.isVisible = ( markRead && selectedConversations != 0 ) ||
                    selectedConversations > 1
            findItem(R.id.unread)?.isVisible = ( !markRead && selectedConversations != 0 ) ||
                    selectedConversations > 1
            findItem(R.id.block)?.isVisible = selectedConversations != 0
            findItem(R.id.rename)?.isVisible = selectedConversations == 1
        }

        listOf(binding.drawer.plusBadge1, binding.drawer.plusBadge2).forEach { badge ->
            badge.isVisible = drawerBadgesExperiment.variant && !state.upgraded
        }
//        plus.isVisible = state.upgraded
        binding.drawer.plusBanner.isVisible = !state.upgraded
        binding.drawer.rateLayout.setVisible(state.showRating)

        binding.compose.setVisible(state.page is Inbox || state.page is Archived)
        conversationsAdapter.emptyView = binding.empty.takeIf {
            state.page is Inbox || state.page is Archived
        }
        searchAdapter.emptyView = binding.empty.takeIf { state.page is Searching }

        when (state.page) {
            is Inbox -> {
                showBackButton(state.page.selected > 0)
                title = getString(R.string.main_title_selected, state.page.selected)
                if (binding.recyclerView.adapter !== conversationsAdapter)
                    binding.recyclerView.adapter = conversationsAdapter
                conversationsAdapter.updateData(state.page.data)
                itemTouchHelper.attachToRecyclerView(binding.recyclerView)
                binding.empty.setText(R.string.inbox_empty_text)
            }

            is Searching -> {
                showBackButton(true)
                if (binding.recyclerView.adapter !== searchAdapter) binding.recyclerView.adapter = searchAdapter
                searchAdapter.data = state.page.data ?: listOf()
                itemTouchHelper.attachToRecyclerView(null)
                binding.empty.setText(R.string.inbox_search_empty_text)
            }

            is Archived -> {
                showBackButton(state.page.selected > 0)
                title = when (state.page.selected != 0) {
                    true -> getString(R.string.main_title_selected, state.page.selected)
                    false -> getString(R.string.title_archived)
                }
                if (binding.recyclerView.adapter !== conversationsAdapter)
                    binding.recyclerView.adapter = conversationsAdapter
                conversationsAdapter.updateData(state.page.data)
                itemTouchHelper.attachToRecyclerView(null)
                binding.empty.setText(R.string.archived_empty_text)
            }

            else -> {}
        }

        binding.drawer.inbox.isActivated = state.page is Inbox
        binding.drawer.archived.isActivated = state.page is Archived

        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START) && !state.drawerOpen)
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        else if (!binding.drawerLayout.isDrawerVisible(GravityCompat.START) && state.drawerOpen)
            binding.drawerLayout.openDrawer(GravityCompat.START)

        when (state.syncing) {
            is SyncRepository.SyncProgress.Idle -> {
                syncingBinding.root.isVisible = false
                snackbarBinding.root.isVisible = (!state.defaultSms ||
                        !state.smsPermission ||
                        !state.contactPermission ||
                        !state.notificationPermission)
            }

            is SyncRepository.SyncProgress.Running -> {
                syncingBinding.root.isVisible = true
                syncingBinding.syncingProgress.max = state.syncing.max
                progressAnimator.apply {
                    setIntValues(syncingBinding.syncingProgress.progress, state.syncing.progress)
                }.start()
                syncingBinding.syncingProgress.isIndeterminate = state.syncing.indeterminate
                snackbarBinding.root.isVisible = false
            }

            is SyncRepository.SyncProgress.ParsingEmojis -> {
                syncingBinding.root.isVisible = true
                syncingBinding.syncingLabel.setText(getString(R.string.main_sync_emojis))
                syncingBinding.syncingProgress.max = state.syncing.max
                progressAnimator.apply {
                    setIntValues(syncingBinding.syncingProgress.progress, state.syncing.progress)
                }.start()
                syncingBinding.syncingProgress.isIndeterminate = state.syncing.indeterminate
                snackbarBinding.root.isVisible = false
            }
        }

        when {
            !state.defaultSms -> {
                snackbarBinding.snackbarTitle.setText(R.string.main_default_sms_title)
                snackbarBinding.snackbarMessage.setText(R.string.main_default_sms_message)
                snackbarBinding.snackbarButton.setText(R.string.main_default_sms_change)
            }

            !state.smsPermission -> {
                snackbarBinding.snackbarTitle.setText(R.string.main_permission_required)
                snackbarBinding.snackbarMessage.setText(R.string.main_permission_sms)
                snackbarBinding.snackbarButton.setText(R.string.main_permission_allow)
            }

            !state.contactPermission -> {
                snackbarBinding.snackbarTitle.setText(R.string.main_permission_required)
                snackbarBinding.snackbarMessage.setText(R.string.main_permission_contacts)
                snackbarBinding.snackbarButton.setText(R.string.main_permission_allow)
            }

            !state.notificationPermission -> {
                snackbarBinding.snackbarTitle.setText(R.string.main_permission_required)
                snackbarBinding.snackbarMessage.setText(R.string.main_permission_notifications)
                snackbarBinding.snackbarButton.setText(R.string.main_permission_allow)
            }
        }
    }

    override fun onResume() =
        super.onResume().also { activityResumedIntent.onNext(true) }

    override fun onPause() =
        super.onPause().also { activityResumedIntent.onNext(false) }

    override fun onDestroy() =
        super.onDestroy().also { disposables.dispose() }

    override fun showBackButton(show: Boolean) =
        toggle.let {
            it.onDrawerSlide(binding.drawer.root, if (show) 1f else 0f)
            it.drawerArrowDrawable.color = when (show) {
                true -> resolveThemeColor(android.R.attr.textColorSecondary)
                false -> resolveThemeColor(android.R.attr.textColorPrimary)
            }
        }

    override fun requestDefaultSms() =
        navigator.showDefaultSmsDialog(this)

    override fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.ANSWER_PHONE_CALLS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            permissions += Manifest.permission.POST_NOTIFICATIONS

        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 0)
    }

    override fun clearSearch() {
        dismissKeyboard()
        binding.toolbarSearch.text = null
    }

    override fun clearSelection() = conversationsAdapter.clearSelection()

    override fun toggleSelectAll() = conversationsAdapter.toggleSelectAll()

    override fun themeChanged() = binding.recyclerView.scrapViews()

    override fun showBlockingDialog(conversations: List<Long>, block: Boolean) {
        blockingDialog.show(this, conversations, block)
    }

    override fun showDeleteDialog(conversations: List<Long>) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(
                resources.getQuantityString(
                    R.plurals.dialog_delete_message,
                    conversations.size,
                    conversations.size
                )
            )
            .setPositiveButton(R.string.button_delete) { _, _ -> confirmDeleteIntent.onNext(conversations) }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    override fun showRenameDialog(conversationName: String) =
        TextInputDialog(
            this,
            getString(R.string.info_name),
            renameConversationIntent::onNext
        )
            .setText(conversationName)
            .show()

    override fun showChangelog(changelog: ChangelogManager.CumulativeChangelog) =
        changelogDialog.show(changelog)

    override fun showArchivedSnackbar(countConversationsArchived: Int, isArchiving: Boolean) =
        Snackbar.make(
            binding.drawerLayout,
            if (isArchiving) {
                resources.getQuantityString(R.plurals.toast_archived, countConversationsArchived, countConversationsArchived)
            } else {
                resources.getQuantityString(R.plurals.toast_unarchived, countConversationsArchived, countConversationsArchived)
            },
            if (countConversationsArchived < 10) Snackbar.LENGTH_LONG
            else Snackbar.LENGTH_INDEFINITE
        ).let {
            it.setAction(R.string.button_undo) { undoArchiveIntent.onNext(Unit) }
            it.setActionTextColor(colors.theme().theme)
            it.show()
        }

    override fun onCreateOptionsMenu(menu: Menu?) =
        menu?.let {
            menuInflater.inflate(R.menu.main, it)
            super.onCreateOptionsMenu(it)
        } ?: false

    override fun onOptionsItemSelected(item: MenuItem) =
        optionsItemIntent.onNext(item.itemId).let { true }

    override fun onBackPressed() = backPressedSubject.onNext(NavItem.BACK)

    override fun drawerToggled(opened: Boolean) {
        if (opened) {
            dismissKeyboard()
            if (!binding.drawer.inbox.isInTouchMode)
                binding.drawer.inbox.requestFocus()
        } else
            binding.toolbarSearch.requestFocus()
    }

    companion object {
        private const val REQUEST_CALL_SCREENING_ROLE = 1001
    }
}
