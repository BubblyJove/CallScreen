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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callscreen.app.ui.screens.DebugLogScreen
import com.callscreen.app.ui.screens.InboxScreen
import com.callscreen.app.ui.screens.PendingScreen
import com.callscreen.app.ui.screens.SettingsScreen
import com.callscreen.app.ui.theme.CallScreenTheme
import com.callscreen.app.util.ScreenLog

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Log permission results for debugging
        results.forEach { (perm, granted) ->
            ScreenLog.d("Permissions", "${perm.substringAfterLast('.')}: $granted")
        }
    }

    private val defaultSmsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* result handled by checking default SMS app state */ }

    private val callScreeningLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* result handled by checking call screening state */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Log startup info
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

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(Icons.Filled.HourglassBottom, contentDescription = "Pending") },
                                label = { Text("Pending") },
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Filled.CheckCircle, contentDescription = "Inbox") },
                                label = { Text("Inbox") },
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
                    when (selectedTab) {
                        0 -> PendingScreen(viewModel, Modifier.padding(innerPadding))
                        1 -> InboxScreen(viewModel, Modifier.padding(innerPadding))
                        2 -> SettingsScreen(
                            onRequestDefaultSms = { requestDefaultSmsApp() },
                            onRequestCallScreening = { requestCallScreeningRole() },
                            isDefaultSms = isDefaultSmsApp(),
                            isCallScreener = isCallScreeningApp(),
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                        3 -> DebugLogScreen(Modifier.padding(innerPadding))
                    }
                }
            }
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
