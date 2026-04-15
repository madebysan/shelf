package com.madebysan.shelf.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class SleepTimerOption(
    val label: String,
    val minutes: Int // 0 = off, -1 = end of chapter
)

@Composable
fun SleepTimerDialog(
    currentMinutes: Int,
    hasChapters: Boolean = false,
    onDismiss: () -> Unit,
    onSelect: (minutes: Int) -> Unit
) {
    val options = buildList {
        add(SleepTimerOption("Off", 0))
        add(SleepTimerOption("15 minutes", 15))
        add(SleepTimerOption("30 minutes", 30))
        add(SleepTimerOption("45 minutes", 45))
        add(SleepTimerOption("60 minutes", 60))
        add(SleepTimerOption("90 minutes", 90))
        if (hasChapters) {
            add(SleepTimerOption("End of Chapter", -1))
        }
    }

    var selected by remember { mutableIntStateOf(currentMinutes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep Timer") },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = option.minutes }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == option.minutes,
                            onClick = { selected = option.minutes }
                        )
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(selected) }) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
