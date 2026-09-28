/*
 * SwtFrontend - shared screenshot and recording gallery.
 * Copyright (C) 2026 RedClaw — GPLv3, see COPYING.
 */

package br.com.redclaw.swt.capture

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import br.com.redclaw.swt.R
import br.com.redclaw.swt.ui.theme.SwtTheme
import br.com.redclaw.swt.ui.theme.rememberSwtAccent
import coil.compose.AsyncImage
import java.text.DateFormat
import java.util.Date

/** Host gallery shared by built-in emulators and companion modules. */
class GalleryActivity : ComponentActivity() {
    private lateinit var store: CaptureStore
    private var captures by mutableStateOf<List<CaptureItem>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = CaptureStore(this)
        setContent {
            val accent = rememberSwtAccent()
            SwtTheme(accent = accent) {
                GalleryScreen(
                    captures = captures,
                    onScreenshot = {
                        startActivity(CaptureProjectionActivity.intent(this, CaptureMode.SCREENSHOT))
                    },
                    onRecord = {
                        startActivity(CaptureProjectionActivity.intent(this, CaptureMode.RECORDING))
                    },
                    onStopRecording = {
                        startService(CaptureProjectionService.stopIntent(this))
                    },
                    onOpen = { startActivity(CaptureViewerActivity.intent(this, it)) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        captures = store.list()
    }
}

@androidx.compose.runtime.Composable
private fun GalleryScreen(
    captures: List<CaptureItem>,
    onScreenshot: () -> Unit,
    onRecord: () -> Unit,
    onStopRecording: () -> Unit,
    onOpen: (CaptureItem) -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "Galeria", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "Capturas do SwtFrontend, jogos e módulos",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onScreenshot) { Text("Capturar tela") }
                Button(onClick = onRecord) { Text("Gravar tela") }
                Button(onClick = onStopRecording) { Text("Parar gravação") }
            }
            if (captures.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhuma captura ainda")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(180.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(captures, key = { it.file.absolutePath }) { item ->
                        CaptureCard(item = item, onClick = { onOpen(item) })
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun CaptureCard(item: CaptureItem, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().size(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (item.type == CaptureMediaType.IMAGE) {
                    AsyncImage(
                        model = item.file,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }
            Column(Modifier.padding(12.dp)) {
                Text(
                    text = if (item.type == CaptureMediaType.IMAGE) "Screenshot" else "Gravação",
                    style = MaterialTheme.typography.titleSmall,
                )
                item.sourceId?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(item.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
