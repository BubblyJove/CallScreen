package com.callscreen.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.callscreen.app.data.PendingMessage
import com.callscreen.app.ui.MainViewModel
import com.callscreen.app.ui.components.ContactAvatar
import com.callscreen.app.ui.components.EmptyState
import com.callscreen.app.util.ContactCache
import com.callscreen.app.util.formatRelativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val messages by viewModel.heldMessages.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Pending (${messages.size})") })

        if (messages.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Shield,
                title = "All clear",
                subtitle = "No pending messages to review"
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    PendingMessageCard(
                        message = message,
                        onApprove = { viewModel.approveMessage(message) },
                        onReject = { viewModel.rejectMessage(message) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingMessageCard(
    message: PendingMessage,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val context = LocalContext.current
    val contactName = ContactCache.getDisplayName(context, message.phoneNumber)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            ContactAvatar(
                name = contactName,
                phoneNumber = message.phoneNumber
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (contactName != null) {
                            Text(
                                text = contactName,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Text(
                            text = message.phoneNumber,
                            style = if (contactName != null) MaterialTheme.typography.bodySmall
                            else MaterialTheme.typography.titleSmall,
                            color = if (contactName != null) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = formatRelativeTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Reject")
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = onApprove) {
                        Text("Approve")
                    }
                }
            }
        }
    }
}
