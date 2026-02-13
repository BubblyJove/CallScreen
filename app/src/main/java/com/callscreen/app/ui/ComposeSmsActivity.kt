package com.callscreen.app.ui

import android.os.Bundle
import android.telephony.SmsManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.callscreen.app.ui.theme.CallScreenTheme

class ComposeSmsActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intentNumber = intent?.data?.schemeSpecificPart ?: ""

        setContent {
            CallScreenTheme {
                var number by rememberSaveable { mutableStateOf(intentNumber) }
                var message by rememberSaveable { mutableStateOf("") }

                Scaffold(
                    topBar = { TopAppBar(title = { Text("New Message") }) }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = number,
                            onValueChange = { number = it },
                            label = { Text("To") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = message,
                            onValueChange = { message = it },
                            label = { Text("Message") },
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )
                        Button(
                            onClick = {
                                if (number.isNotBlank() && message.isNotBlank()) {
                                    sendSms(number, message)
                                    finish()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Send")
                        }
                    }
                }
            }
        }
    }

    private fun sendSms(number: String, message: String) {
        try {
            val smsManager = getSystemService(SmsManager::class.java)
            smsManager.sendTextMessage(number, null, message, null, null)
        } catch (e: Exception) {
            android.util.Log.e("ComposeSms", "Failed to send SMS", e)
        }
    }
}
