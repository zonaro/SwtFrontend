/*
 * SwtFrontend - internal capture viewer/player.
 * Copyright (C) 2026 RedClaw — GPLv3, see COPYING.
 */

package br.com.redclaw.swt.capture

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import br.com.redclaw.swt.ui.theme.SwtTheme
import br.com.redclaw.swt.ui.theme.rememberSwtAccent
import coil.compose.AsyncImage
import java.io.File

class CaptureViewerActivity : ComponentActivity() {
    private lateinit var item: CaptureItem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val file = intent.getStringExtra(EXTRA_PATH)?.let(::File)
        val store = CaptureStore(this)
        item = file?.let(store::parse) ?: run {
            finish()
            return
        }
        val canonicalParent = runCatching { item.file.canonicalFile.parentFile }.getOrNull()
        if (canonicalParent != store.directory.canonicalFile || !item.file.isFile) {
            finish()
            return
        }
        setContent {
            val accent = rememberSwtAccent()
            SwtTheme(accent = accent) {
                CaptureViewer(
                    item = item,
                    onShare = ::share,
                    onDelete = {
                        store.delete(item)
                        finish()
                    },
                )
            }
        }
    }

    private fun share() {
        val uri = FileProvider.getUriForFile(
            this,
            "$packageName.capture.files",
            item.file,
        )
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = if (item.type == CaptureMediaType.IMAGE) "image/png" else "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Compartilhar captura"))
    }

    companion object {
        private const val EXTRA_PATH = "capture_path"

        fun intent(context: Context, item: CaptureItem): Intent =
            Intent(context, CaptureViewerActivity::class.java)
                .putExtra(EXTRA_PATH, item.file.absolutePath)
    }
}

@Composable
private fun CaptureViewer(
    item: CaptureItem,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = if (item.type == CaptureMediaType.IMAGE) "Screenshot" else "Gravação",
            style = MaterialTheme.typography.headlineSmall,
        )
        if (item.type == CaptureMediaType.IMAGE) {
            AsyncImage(
                model = item.file,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        } else {
            AndroidView(
                factory = { context -> VideoView(context).apply {
                    setVideoPath(item.file.absolutePath)
                    setOnPreparedListener { start() }
                } },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onShare) { Text("Compartilhar") }
            Button(onClick = onDelete) { Text("Excluir") }
        }
    }
}
