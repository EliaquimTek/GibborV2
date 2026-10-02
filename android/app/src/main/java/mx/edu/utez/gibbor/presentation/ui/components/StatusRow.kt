package mx.edu.utez.gibbor.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import mx.edu.utez.gibbor.presentation.ui.theme.GibborTheme

/** Estado semántico de una fila de "Estado de protección". */
enum class StatusTone { Ok, Pending, Error, Neutral }

@Composable
fun StatusTone.color(): Color = when (this) {
    StatusTone.Ok -> GibborTheme.colors.success
    StatusTone.Pending -> GibborTheme.colors.warning
    StatusTone.Error -> GibborTheme.colors.sos
    StatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Fila de estado: ícono + etiqueta + valor (color semántico) y, opcionalmente, una acción inline.
 */
@Composable
fun StatusRow(
    icon: ImageVector,
    label: String,
    value: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val toneColor = tone.color()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = toneColor,
            modifier = Modifier.size(24.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { stateDescription = value },
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        color = toneColor,
                        strokeWidth = 1.5.dp,
                    )
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    color = toneColor,
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
