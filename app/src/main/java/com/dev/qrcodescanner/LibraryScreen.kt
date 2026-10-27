package com.dev.qrcodescanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LibraryScreen(
    refreshKey: Int,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<File>?>(null) }
    var pendingDelete by remember { mutableStateOf<File?>(null) }
    var preview by remember { mutableStateOf<File?>(null) }
    var newestFirst by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(refreshKey) {
        files = QrEngine.loadLibrary(context)
    }

    val loaded = files

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        if (loaded == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            val sorted = remember(loaded, newestFirst) {
                if (newestFirst) loaded.sortedByDescending { it.lastModified() }
                else loaded.sortedBy { it.lastModified() }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 20.dp,
                    bottom = 32.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LibraryHero(files = loaded)
                }

                if (loaded.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LibraryEmpty(modifier = Modifier.padding(top = 10.dp))
                    }
                } else {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sort by",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            LibraryChip(
                                label = "Newest",
                                selected = newestFirst,
                                onClick = { newestFirst = true }
                            )
                            LibraryChip(
                                label = "Oldest",
                                selected = !newestFirst,
                                onClick = { newestFirst = false }
                            )
                        }
                    }

                    items(sorted, key = { it.absolutePath }) { file ->
                        LibraryCard(
                            file = file,
                            onOpen = { preview = file },
                            onShare = {
                                runCatching { QrEngine.shareFile(context, file) }
                                    .onFailure { onMessage("Unable to share the code.") }
                            },
                            onDelete = { pendingDelete = file }
                        )
                    }
                }
            }
        }
    }

    preview?.let { file ->
        LibraryPreviewDialog(
            file = file,
            onDismiss = { preview = null },
            onShare = {
                runCatching { QrEngine.shareFile(context, file) }
                    .onFailure { onMessage("Unable to share the code.") }
            },
            onDelete = {
                preview = null
                pendingDelete = file
            }
        )
    }

    pendingDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Delete this code?", fontWeight = FontWeight.Bold) },
            text = { Text("It will be removed from your library. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        scope.launch {
                            if (QrEngine.deleteFromLibrary(file)) {
                                files = files?.filter { it != file }
                                onMessage("Code deleted.")
                            } else {
                                onMessage("Unable to delete the code.")
                            }
                        }
                    }
                ) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Keep it")
                }
            }
        )
    }
}

/* ------------------------------------------------------------------------- */
/* Hero with stats                                                            */
/* ------------------------------------------------------------------------- */

@Composable
private fun LibraryHero(files: List<File>) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    val count = files.size
    val totalBytes = files.sumOf { it.length() }
    val latest = files.maxOfOrNull { it.lastModified() }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = primary,
        shadowElevation = 12.dp
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(listOf(primary, tertiary))
            )
        ) {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 80.dp, y = (-90).dp)
                    .clip(CircleShape)
                    .background(onPrimary.copy(alpha = 0.09f))
            )
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-40).dp, y = 50.dp)
                    .clip(CircleShape)
                    .background(onPrimary.copy(alpha = 0.06f))
            )

            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Your library",
                            style = MaterialTheme.typography.headlineMedium,
                            color = onPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 34.sp
                        )
                        Text(
                            text = if (count == 0) {
                                "Codes you save will appear here."
                            } else {
                                "Tap a code to preview it, share it, or clean up."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = onPrimary.copy(alpha = 0.86f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(onPrimary.copy(alpha = 0.15f))
                            .padding(13.dp)
                    ) {
                        LibraryMotif(
                            modifier = Modifier.fillMaxSize(),
                            color = onPrimary,
                            center = primary
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LibraryStat(
                        modifier = Modifier.weight(1f),
                        label = "Codes",
                        value = count.toString()
                    )
                    LibraryStat(
                        modifier = Modifier.weight(1f),
                        label = "Storage",
                        value = if (count == 0) "0 B" else Formatter.formatShortFileSize(context, totalBytes)
                    )
                    LibraryStat(
                        modifier = Modifier.weight(1f),
                        label = "Latest",
                        value = if (latest == null) {
                            "None"
                        } else {
                            DateUtils.getRelativeTimeSpanString(
                                latest,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_RELATIVE
                            ).toString()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryStat(
    modifier: Modifier,
    label: String,
    value: String
) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(onPrimary.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = onPrimary,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = onPrimary.copy(alpha = 0.8f),
            maxLines = 1
        )
    }
}

@Composable
private fun LibraryMotif(
    modifier: Modifier,
    color: Color,
    center: Color
) {
    Canvas(modifier = modifier) {
        val n = 11
        val cell = size.width / n

        fun inFinder(x: Int, y: Int) =
            (x < 4 && y < 4) || (x >= n - 4 && y < 4) || (x < 4 && y >= n - 4)

        fun inCenter(x: Int, y: Int) = x in 4..6 && y in 4..6

        for (y in 0 until n) {
            for (x in 0 until n) {
                if (inFinder(x, y) || inCenter(x, y)) continue
                if ((x * 5 + y * 11 + x * y) % 3 == 0) {
                    drawRoundRect(
                        color = color.copy(alpha = 0.9f),
                        topLeft = Offset(x * cell + cell * 0.12f, y * cell + cell * 0.12f),
                        size = Size(cell * 0.76f, cell * 0.76f),
                        cornerRadius = CornerRadius(cell * 0.24f)
                    )
                }
            }
        }

        fun eye(ox: Int, oy: Int) {
            val stroke = cell * 0.5f
            drawRoundRect(
                color = color,
                topLeft = Offset(ox * cell + stroke / 2f, oy * cell + stroke / 2f),
                size = Size(cell * 3 - stroke, cell * 3 - stroke),
                cornerRadius = CornerRadius(cell * 0.8f),
                style = Stroke(width = stroke)
            )
            drawRoundRect(
                color = color,
                topLeft = Offset((ox + 1) * cell + cell * 0.08f, (oy + 1) * cell + cell * 0.08f),
                size = Size(cell * 0.84f, cell * 0.84f),
                cornerRadius = CornerRadius(cell * 0.3f)
            )
        }
        eye(0, 0)
        eye(n - 3, 0)
        eye(0, n - 3)

        drawRoundRect(
            color = color,
            topLeft = Offset(4 * cell, 4 * cell),
            size = Size(cell * 3, cell * 3),
            cornerRadius = CornerRadius(cell * 0.9f)
        )
        drawCircle(
            color = center,
            radius = cell * 0.7f,
            center = Offset(5.5f * cell, 5.5f * cell)
        )
    }
}

/* ------------------------------------------------------------------------- */
/* Grid card                                                                  */
/* ------------------------------------------------------------------------- */

@Composable
private fun LibraryCard(
    file: File,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val thumbnail by produceState<Bitmap?>(initialValue = null, file) {
        value = QrEngine.decodeThumbnail(file)
    }
    val modified = file.lastModified()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LibraryQrTile(
                bitmap = thumbnail,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = DateUtils.getRelativeTimeSpanString(
                        modified,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE
                    ).toString(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = DateUtils.formatDateTime(
                        context,
                        modified,
                        DateUtils.FORMAT_SHOW_DATE or
                                DateUtils.FORMAT_SHOW_TIME or
                                DateUtils.FORMAT_ABBREV_MONTH
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LibraryShareButton(
                    modifier = Modifier.weight(1f),
                    onClick = onShare
                )
                LibraryDeleteButton(onClick = onDelete)
            }
        }
    }
}

@Composable
private fun LibraryQrTile(
    bitmap: Bitmap?,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .drawBehind {
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.06f),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = "Saved code",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        }
    }
}

@Composable
private fun LibraryShareButton(
    modifier: Modifier,
    onClick: () -> Unit
) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary
                    )
                )
            )
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Share,
            contentDescription = null,
            tint = onPrimary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Share",
            style = MaterialTheme.typography.labelLarge,
            color = onPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LibraryDeleteButton(onClick: () -> Unit) {
    val error = MaterialTheme.colorScheme.error
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(error.copy(alpha = 0.12f))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Delete,
            contentDescription = "Delete code",
            tint = error,
            modifier = Modifier.size(20.dp)
        )
    }
}

/* ------------------------------------------------------------------------- */
/* Preview dialog                                                             */
/* ------------------------------------------------------------------------- */

@Composable
private fun LibraryPreviewDialog(
    file: File,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val full by produceState<Bitmap?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LibraryQrTile(
                    bitmap = full,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Saved code",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Saved " + DateUtils.formatDateTime(
                            context,
                            file.lastModified(),
                            DateUtils.FORMAT_SHOW_DATE or
                                    DateUtils.FORMAT_SHOW_TIME or
                                    DateUtils.FORMAT_ABBREV_MONTH
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LibraryShareButton(
                        modifier = Modifier.weight(1f),
                        onClick = onShare
                    )
                    LibraryDeleteButton(onClick = onDelete)
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Small pieces                                                               */
/* ------------------------------------------------------------------------- */

@Composable
private fun LibraryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "libraryChipContainer"
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "libraryChipContent"
    )

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(50.dp),
        color = container,
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = content,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

private fun Modifier.libraryDashedOutline(color: Color, radius: Dp): Modifier =
    this.drawBehind {
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(radius.toPx()),
            style = Stroke(
                width = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))
            )
        )
    }

@Composable
private fun LibraryEmpty(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            .libraryDashedOutline(primary.copy(alpha = 0.5f), 28.dp)
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.PhotoLibrary,
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(34.dp)
            )
        }
        Text(
            text = "No saved codes yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Generate a code and tap Save to keep it here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}