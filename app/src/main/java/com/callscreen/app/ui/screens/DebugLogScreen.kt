package com.callscreen.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callscreen.app.util.ScreenLog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Trigger recomposition when new log lines arrive
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val cb: () -> Unit = { revision++ }
        ScreenLog.onChange = cb
        onDispose { if (ScreenLog.onChange === cb) ScreenLog.onChange = null }
    }

    // Read the current log text (revision forces re-read)
    val logText = remember(revision) { ScreenLog.getAll() }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Debug Log") })

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return@Button
                clipboard.setPrimaryClip(ClipData.newPlainText("CallScreen log", logText))
                Toast.makeText(context, "Log copied", Toast.LENGTH_SHORT).show()
            }) {
                Text("Copy log")
            }
            OutlinedButton(onClick = { ScreenLog.clear() }) {
                Text("Clear")
            }
        }

        val vScroll = rememberScrollState()
        val hScroll = rememberScrollState()

        Text(
            text = if (logText.isBlank()) "(no log entries yet — try calling this phone)" else logText,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp)
                .verticalScroll(vScroll)
                .horizontalScroll(hScroll),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
