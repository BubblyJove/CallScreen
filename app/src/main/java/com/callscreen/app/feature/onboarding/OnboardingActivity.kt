package com.callscreen.app.feature.onboarding

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PhoneForwarded
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.callscreen.app.R
import com.callscreen.app.feature.main.MainActivity
import com.callscreen.app.ui.theme.CallScreenTheme
import kotlinx.coroutines.launch

class OnboardingActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* results handled implicitly — user sees grant dialog */ }

    private val defaultSmsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* result handled implicitly */ }

    private val callScreeningLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* result handled implicitly */ }

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CallScreenTheme {
                OnboardingScreen(
                    onSetDefaultSms = ::requestDefaultSms,
                    onEnableCallScreening = ::requestCallScreening,
                    onGrantPermissions = ::requestPermissions,
                    onFinish = ::finishOnboarding
                )
            }
        }
    }

    private fun requestDefaultSms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
            defaultSmsLauncher.launch(intent)
        } else {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            defaultSmsLauncher.launch(intent)
        }
    }

    private fun requestCallScreening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val roleManager = getSystemService(RoleManager::class.java)
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                callScreeningLauncher.launch(intent)
            } catch (_: Exception) { }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.ANSWER_PHONE_CALLS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun finishOnboarding() {
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().putBoolean("onboardingComplete", true).apply()

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnboardingScreen(
    onSetDefaultSms: () -> Unit,
    onEnableCallScreening: () -> Unit,
    onGrantPermissions: () -> Unit,
    onFinish: () -> Unit
) {
    val pageCount = 4
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> OnboardingPage(
                    icon = Icons.Filled.Security,
                    title = stringResource(R.string.onboarding_welcome_title),
                    description = stringResource(R.string.onboarding_welcome_desc),
                    buttonText = null,
                    onButtonClick = {}
                )
                1 -> OnboardingPage(
                    icon = Icons.Filled.Sms,
                    title = stringResource(R.string.onboarding_sms_title),
                    description = stringResource(R.string.onboarding_sms_desc),
                    buttonText = stringResource(R.string.onboarding_sms_button),
                    onButtonClick = onSetDefaultSms
                )
                2 -> OnboardingPage(
                    icon = Icons.Filled.PhoneForwarded,
                    title = stringResource(R.string.onboarding_screening_title),
                    description = stringResource(R.string.onboarding_screening_desc),
                    buttonText = stringResource(R.string.onboarding_screening_button),
                    onButtonClick = onEnableCallScreening
                )
                3 -> OnboardingPage(
                    icon = Icons.Filled.Call,
                    title = stringResource(R.string.onboarding_permissions_title),
                    description = stringResource(R.string.onboarding_permissions_desc),
                    buttonText = stringResource(R.string.onboarding_permissions_button),
                    onButtonClick = onGrantPermissions
                )
            }
        }

        // Bottom navigation row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onFinish) {
                Text(stringResource(R.string.onboarding_skip))
            }

            // Page indicators
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(pageCount) { index ->
                    val color = if (index == pagerState.currentPage)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outlineVariant
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .padding(0.dp)
                    ) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(color = color)
                        }
                    }
                }
            }

            if (pagerState.currentPage < pageCount - 1) {
                TextButton(onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }) {
                    Text(stringResource(R.string.onboarding_next))
                }
            } else {
                Button(onClick = onFinish) {
                    Text(stringResource(R.string.onboarding_get_started))
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(
    icon: ImageVector,
    title: String,
    description: String,
    buttonText: String?,
    onButtonClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (buttonText != null) {
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onButtonClick) {
                Text(buttonText)
            }
        }
    }
}
