package com.callscreen.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.callscreen.app.data.SmsRepository
import com.callscreen.app.ui.theme.CallScreenTheme
import com.callscreen.app.util.PhoneNumberUtil

class ComposeSmsActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Validate intent data (rule 2.3: validate external input)
        val intentNumber = intent?.data?.schemeSpecificPart
            ?.take(MAX_PHONE_LENGTH)
            ?: ""

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
                            onValueChange = { number = it.take(MAX_PHONE_LENGTH) },
                            label = { Text("To") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = message,
                            onValueChange = { message = it.take(MAX_MESSAGE_LENGTH) },
                            label = { Text("Message") },
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )
                        Button(
                            onClick = {
                                val normalized = PhoneNumberUtil.normalize(number)
                                if (normalized.isNotBlank() && message.isNotBlank()) {
                                    SmsRepository(this@ComposeSmsActivity)
                                        .sendMessage(normalized, message)
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

    companion object {
        private const val MAX_PHONE_LENGTH = 20
        private const val MAX_MESSAGE_LENGTH = 5000
    }
}
