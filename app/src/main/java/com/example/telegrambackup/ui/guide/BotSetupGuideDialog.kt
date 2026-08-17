package com.example.telegrambackup.ui.guide

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotSetupGuideDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("How to Get Bot Credentials")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Step 1
                GuideStepCard(
                    stepNumber = "1",
                    title = "Create a Telegram Bot",
                    description = "1. Open Telegram app and search for @BotFather\n2. Send /newbot to BotFather\n3. Set a Name and Username for your bot\n4. Copy the long HTTP API Token provided.",
                    buttonLabel = "Open @BotFather",
                    onButtonClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/BotFather"))
                        context.startActivity(intent)
                    }
                )

                // Step 2
                GuideStepCard(
                    stepNumber = "2",
                    title = "Create Channel & Add Bot as Admin",
                    description = "1. Create a Telegram Channel (Public or Private)\n2. Open Channel Info -> Administrators -> Add Administrator\n3. Search for your Bot's username and grant 'Post Messages' permission."
                )

                // Step 3
                GuideStepCard(
                    stepNumber = "3",
                    title = "Get Channel Chat ID via Forward",
                    description = "1. Post any message in your channel.\n2. Forward that message to @raw_data_bot or @userinfobot.\n3. The bot will reply with your exact Chat ID (e.g. -1001234567890).",
                    buttonLabel = "Open @raw_data_bot",
                    onButtonClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/raw_data_bot"))
                        context.startActivity(intent)
                    }
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Got It!")
            }
        }
    )
}

@Composable
fun GuideStepCard(
    stepNumber: String,
    title: String,
    description: String,
    buttonLabel: String? = null,
    onButtonClick: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                    Text(stepNumber, modifier = Modifier.padding(4.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(description, style = MaterialTheme.typography.bodySmall)

            if (buttonLabel != null && onButtonClick != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onButtonClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(buttonLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
