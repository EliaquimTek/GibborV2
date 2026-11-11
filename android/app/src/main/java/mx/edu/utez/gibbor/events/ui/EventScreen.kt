package mx.edu.utez.gibbor.events.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import mx.edu.utez.gibbor.events.data.EventCatalog
import mx.edu.utez.gibbor.events.domain.Event
import mx.edu.utez.gibbor.events.domain.EventResource
import mx.edu.utez.gibbor.events.ui.qr.QrDialog

/**
 * Pestaña "Evento" (solo existe en la rama `events`).
 * Muestra al público los recursos del evento en curso y los recursos fijos del equipo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventScreen(
    modifier: Modifier = Modifier,
    event: Event? = EventCatalog.events.firstOrNull(),
    fixedResources: List<EventResource> = EventCatalog.fixedResources,
) {
    var qrResource by remember { mutableStateOf<EventResource?>(null) }
    val openResource: (EventResource) -> Unit = { qrResource = it }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Evento",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = inner.calculateTopPadding() + 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "header") { EventHeader(event) }

            if (event != null && event.resources.isNotEmpty()) {
                resourceSection("Del evento", event.resources, openResource)
            }
            if (fixedResources.isNotEmpty()) {
                resourceSection("GIBBOR", fixedResources, openResource)
            }
        }
    }

    qrResource?.let { resource ->
        QrDialog(resource = resource, onDismiss = { qrResource = null })
    }
}

private fun LazyListScope.resourceSection(
    title: String,
    resources: List<EventResource>,
    onResourceClick: (EventResource) -> Unit,
) {
    item(key = "section-$title") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .semantics { heading() },
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                resources.forEachIndexed { index, resource ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 60.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    ResourceRow(resource = resource, onClick = { onResourceClick(resource) })
                }
            }
        }
    }
}

@Composable
private fun EventHeader(event: Event?) {
    Column(
        modifier = Modifier.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (event == null) {
            Icon(
                Icons.Rounded.Event,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Sin evento seleccionado",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "Mientras tanto se muestran los recursos generales del equipo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                event.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            val meta = listOf(event.date, event.place).filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (event.description.isNotBlank()) {
                Text(
                    event.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}
