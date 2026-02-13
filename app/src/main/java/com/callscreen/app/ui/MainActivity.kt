package com.callscreen.app.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callscreen.app.ui.screens.ConversationScreen
import com.callscreen.app.ui.screens.DebugLogScreen
import com.callscreen.app.ui.screens.InboxScreen
import com.callscreen.app.ui.screens.PendingScreen
import com.callscreen.app.ui.screens.SettingsScreen
import com.callscreen.app.ui.theme.CallScreenTheme
import com.callscreen.app.util.ContactCache
import com.callscreen.app.util.ScreenLog

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        results.forEach { (perm, granted) ->
            ScreenLog.d("Permissions", "${perm.substringAfterLast('.')}: $granted")
        }
        promptRolesIfNeeded()
    }

    private val defaultSmsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        ScreenLog.d("Roles", "Default SMS result: ${result.resultCode}")
        // Chain: after SMS role prompt, prompt call screening
        promptCallScreeningIfNeeded()
    }

    private val callScreeningLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        ScreenLog.d("Roles", "Call screening result: ${result.resultCode}")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        ScreenLog.d("App", "MainActivity.onCreate — API ${Build.VERSION.SDK_INT} (${Build.MODEL})")
        ScreenLog.d("App", "Package: $packageName")
        ScreenLog.d("App", "Default SMS app: ${isDefaultSmsApp()}")
        ScreenLog.d("App", "Call screener: ${isCallScreeningApp()}")
        logPermissionState()

        requestPermissions()

        setContent {
            CallScreenTheme {
                val viewModel: MainViewModel = viewModel()
                var selectedTab by rememberSaveable { mutableIntStateOf(0) }
                val heldCount by viewModel.heldCount.collectAsState()

                // Conversation detail state
                var openThreadId by rememberSaveable { mutableStateOf<Long?>(null) }
                var openThreadAddress by rememberSaveable { mutableStateOf("") }
                var openThreadName by rememberSaveable { mutableStateOf<String?>(null) }

                // Reactive role status — re-check on resume
                var isDefaultSms by rememberSaveable { mutableStateOf(isDefaultSmsApp()) }
                var isCallScreener by rememberSaveable { mutableStateOf(isCallScreeningApp()) }

                val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
                androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            isDefaultSms = isDefaultSmsApp()
                            isCallScreener = isCallScreeningApp()
                            ContactCache.invalidate()
                            viewModel.loadConversations()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                // If a conversation is open, show it full-screen
                val currentThreadId = openThreadId
                if (currentThreadId != null) {
                    ConversationScreen(
                        threadId = currentThreadId,
                        address = openThreadAddress,
                        displayName = openThreadName,
                        viewModel = viewModel,
                        onBack = {
                            openThreadId = null
                            viewModel.loadConversations()
                        }
                    )
                } else {
                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                NavigationBarItem(
                                    icon = {
                                        BadgedBox(
                                            badge = {
                                                if (heldCount > 0) {
                                                    Badge { Text(heldCount.toString()) }
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Filled.HourglassBottom, contentDescription = "Pending")
                                        }
                                    },
                                    label = { Text("Pending") },
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 }
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Filled.Forum, contentDescription = "Messages") },
                                    label = { Text("Messages") },
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 }
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                                    label = { Text("Settings") },
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 }
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Filled.BugReport, contentDescription = "Debug") },
                                    label = { Text("Debug") },
                                    selected = selectedTab == 3,
                                    onClick = { selectedTab = 3 }
                                )
                            }
                        }
                    ) { innerPadding ->
                        Column(modifier = Modifier.padding(innerPadding)) {
                            // Setup banner when roles not held
                            if (!isDefaultSms || !isCallScreener) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "Setup required",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Text(
                                                buildString {
                                                    if (!isDefaultSms) append("Default SMS")
                                                    if (!isDefaultSms && !isCallScreener) append(" & ")
                                                    if (!isCallScreener) append("Call Screening")
                                                    append(" role needed")
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                        Button(onClick = { promptRolesIfNeeded() }) {
                                            Text("Enable")
                                        }
                                    }
                                }
                            }

                            when (selectedTab) {
                                0 -> PendingScreen(viewModel)
                                1 -> InboxScreen(
                                    viewModel = viewModel,
                                    onOpenThread = { threadId, address, name ->
                                        openThreadId = threadId
                                        openThreadAddress = address
                                        openThreadName = name
                                    }
                                )
                                2 -> SettingsScreen(
                                    onRequestDefaultSms = { requestDefaultSmsApp() },
                                    onRequestCallScreening = { requestCallScreeningRole() },
                                    isDefaultSms = isDefaultSms,
                                    isCallScreener = isCallScreener,
                                    viewModel = viewModel
                                )
                                3 -> DebugLogScreen()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun promptRolesIfNeeded() {
        if (!isDefaultSmsApp()) {
            ScreenLog.d("Roles", "Prompting for Default SMS role")
            requestDefaultSmsApp()
        } else if (!isCallScreeningApp()) {
            ScreenLog.d("Roles", "Prompting for Call Screening role")
            requestCallScreeningRole()
        } else {
            ScreenLog.d("Roles", "Both roles already held")
        }
    }

    private fun promptCallScreeningIfNeeded() {
        if (!isCallScreeningApp()) {
            ScreenLog.d("Roles", "Chaining: prompting for Call Screening role")
            requestCallScreeningRole()
        }
    }

    private fun logPermissionState() {
        val perms = listOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS
        )
        for (p in perms) {
            val granted = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
            ScreenLog.d("Permissions", "${p.substringAfterLast('.')}: $granted")
        }
    }

    private fun requestPermissions() {
        val needed = mutableListOf<String>()
        val perms = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        for (p in perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needed.add(p)
            }
        }
        if (needed.isNotEmpty()) {
            ScreenLog.d("Permissions", "Requesting: ${needed.map { it.substringAfterLast('.') }}")
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            ScreenLog.d("Permissions", "All permissions already granted")
            promptRolesIfNeeded()
        }
    }

    private fun requestDefaultSmsApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_SMS)
            ) {
                defaultSmsLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
            }
        } else {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            defaultSmsLauncher.launch(intent)
        }
    }

    private fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
            ) {
                callScreeningLauncher.launch(
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                )
            }
        }
    }

    private fun isDefaultSmsApp(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            roleManager.isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            Telephony.Sms.getDefaultSmsPackage(this) == packageName
        }
    }

    private fun isCallScreeningApp(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        } else {
            val telecom = getSystemService(TelecomManager::class.java)
            telecom.defaultDialerPackage == packageName
        }
    }
}
