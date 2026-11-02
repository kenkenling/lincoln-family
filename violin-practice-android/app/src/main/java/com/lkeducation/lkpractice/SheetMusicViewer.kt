package com.lkeducation.lkpractice

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun rememberSheetMusicDisplayName(uriText: String?): String {
    val context = LocalContext.current
    val fallback = uriText
        ?.let(Uri::parse)
        ?.lastPathSegment
        ?.substringAfterLast('/')
        ?.takeIf(String::isNotBlank)
        ?: "Attached sheet music"
    var displayName by remember(uriText) { mutableStateOf(fallback) }

    LaunchedEffect(uriText) {
        val uri = uriText?.let(Uri::parse) ?: return@LaunchedEffect
        displayName = withContext(Dispatchers.IO) {
            querySheetMusicDisplayName(context.contentResolver, uri) ?: fallback
        }
    }
    return displayName
}

@Composable
fun SheetMusicViewerScreen(
    uri: Uri,
    onClose: () -> Unit,
    onOpenExternally: (Uri, String) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val fallbackDisplayName = rememberSheetMusicDisplayName(uri.toString())
    val loadState by produceState<SheetMusicLoadState>(
        initialValue = SheetMusicLoadState.Loading,
        key1 = uri,
    ) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching { openSheetMusicDocument(context, uri) }
                .fold(
                    onSuccess = SheetMusicLoadState::Ready,
                    onFailure = { SheetMusicLoadState.Error(sheetMusicErrorMessage(it)) },
                )
        }
        value = loaded
        awaitDispose {
            (loaded as? SheetMusicLoadState.Ready)?.document?.close()
        }
    }

    BackHandler(onBack = onClose)
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = CloudWhite,
    ) {
        Column(Modifier.fillMaxSize()) {
            SheetMusicViewerHeader(
                title = (loadState as? SheetMusicLoadState.Ready)
                    ?.document
                    ?.displayName
                    ?: fallbackDisplayName,
                onClose = onClose,
                onOpenExternally = {
                    val mimeType = (loadState as? SheetMusicLoadState.Ready)
                        ?.document
                        ?.mimeType
                        ?: context.contentResolver.getType(uri)
                        ?: "*/*"
                    onOpenExternally(uri, mimeType)
                },
            )
            when (val state = loadState) {
                SheetMusicLoadState.Loading -> SheetMusicLoading()
                is SheetMusicLoadState.Error -> SheetMusicError(
                    message = state.message,
                    onOpenExternally = {
                        onOpenExternally(
                            uri,
                            context.contentResolver.getType(uri) ?: "*/*",
                        )
                    },
                )
                is SheetMusicLoadState.Ready -> SheetMusicDocumentContent(state.document)
            }
        }
    }
}

@Composable
private fun SheetMusicViewerHeader(
    title: String,
    onClose: () -> Unit,
    onOpenExternally: () -> Unit,
) {
    Surface(color = DeepSky, tonalElevation = 4.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close sheet music",
                    tint = Paper,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Sheet music",
                    color = Paper,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    title,
                    color = Paper.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onOpenExternally) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = Paper,
                )
                Text("Other app", color = Paper)
            }
        }
    }
}

@Composable
private fun SheetMusicLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(color = DeepSky)
            Text("Preparing sheet music…", color = Muted)
        }
    }
}

@Composable
private fun SheetMusicError(
    message: String,
    onOpenExternally: (() -> Unit)?,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(48.dp),
            )
            Text(
                "Sheet music unavailable",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(message, color = Muted)
            if (onOpenExternally != null) {
                OutlinedButton(onClick = onOpenExternally) {
                    Text("Try another app")
                }
            }
        }
    }
}

@Composable
private fun SheetMusicDocumentContent(document: SheetMusicDocument) {
    var pageIndex by remember(document) { mutableIntStateOf(0) }
    var resetZoomRequest by remember(document) { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.roundToPx() }
            val renderWidth = sheetMusicRenderWidth(widthPx)
            val pageState by produceState<SheetMusicPageState>(
                initialValue = SheetMusicPageState.Loading,
                key1 = document,
                key2 = pageIndex,
                key3 = renderWidth,
            ) {
                val rendered = withContext(Dispatchers.IO) {
                    runCatching { document.renderPage(pageIndex, renderWidth) }
                }
                value = rendered.fold(
                    onSuccess = SheetMusicPageState::Ready,
                    onFailure = { SheetMusicPageState.Error(sheetMusicErrorMessage(it)) },
                )
                awaitDispose {
                    rendered.getOrNull()?.let { bitmap ->
                        if (!bitmap.isRecycled) bitmap.recycle()
                    }
                }
            }
            when (val state = pageState) {
                SheetMusicPageState.Loading -> SheetMusicLoading()
                is SheetMusicPageState.Error -> SheetMusicError(
                    message = state.message,
                    onOpenExternally = null,
                )
                is SheetMusicPageState.Ready -> ZoomableSheetMusicPage(
                    bitmap = state.bitmap,
                    description = sheetMusicPageLabel(pageIndex, document.pageCount),
                    resetZoomRequest = resetZoomRequest,
                )
            }
        }
        SheetMusicPageControls(
            pageIndex = pageIndex,
            pageCount = document.pageCount,
            onPrevious = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
            onNext = {
                pageIndex = (pageIndex + 1).coerceAtMost(document.pageCount - 1)
            },
            onResetZoom = { resetZoomRequest += 1 },
        )
    }
}

@Composable
private fun ZoomableSheetMusicPage(
    bitmap: Bitmap,
    description: String,
    resetZoomRequest: Int,
) {
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(resetZoomRequest) {
        scale = 1f
        offset = Offset.Zero
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF263238))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val transformState = rememberTransformableState { _, zoomChange, panChange, _ ->
            val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
            val maxPanX = widthPx * (nextScale - 1f) / 2f
            val maxPanY = heightPx * (nextScale - 1f) / 2f
            scale = nextScale
            offset = if (nextScale <= 1.01f) {
                Offset.Zero
            } else {
                Offset(
                    x = (offset.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                    y = (offset.y + panChange.y).coerceIn(-maxPanY, maxPanY),
                )
            }
        }
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .transformable(transformState),
        )
    }
}

@Composable
private fun SheetMusicPageControls(
    pageIndex: Int,
    pageCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onResetZoom: () -> Unit,
) {
    Surface(color = Paper, shadowElevation = 6.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onPrevious,
                    enabled = pageIndex > 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Previous")
                }
                Text(
                    sheetMusicPageLabel(pageIndex, pageCount),
                    fontWeight = FontWeight.SemiBold,
                )
                Button(
                    onClick = onNext,
                    enabled = pageIndex < pageCount - 1,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Next")
                }
            }
            TextButton(
                onClick = onResetZoom,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Reset zoom")
            }
            Text(
                "Pinch to zoom and drag to move around the page.",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

private sealed interface SheetMusicLoadState {
    data object Loading : SheetMusicLoadState
    data class Ready(val document: SheetMusicDocument) : SheetMusicLoadState
    data class Error(val message: String) : SheetMusicLoadState
}

private sealed interface SheetMusicPageState {
    data object Loading : SheetMusicPageState
    data class Ready(val bitmap: Bitmap) : SheetMusicPageState
    data class Error(val message: String) : SheetMusicPageState
}
