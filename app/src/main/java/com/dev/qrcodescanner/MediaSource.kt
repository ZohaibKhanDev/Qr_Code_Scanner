package com.dev.qrcodescanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.text.format.Formatter
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class MediaSource(val label: String, val icon: ImageVector) {
    Gallery("Image", Icons.Rounded.AddPhotoAlternate),
    Camera("Camera", Icons.Rounded.PhotoCamera),
    Pdf("PDF", Icons.Rounded.PictureAsPdf)
}

@Stable
class MediaState {
    var source by mutableStateOf(MediaSource.Gallery)
    var linkText by mutableStateOf("")
    var pdfLink by mutableStateOf("")
    var galleryBitmap by mutableStateOf<Bitmap?>(null)
    var cameraBitmap by mutableStateOf<Bitmap?>(null)
    var pdfName by mutableStateOf<String?>(null)
    var pdfSize by mutableStateOf<Long?>(null)
    var style by mutableStateOf(QrStyle())
    var result by mutableStateOf<Bitmap?>(null)
    var generating by mutableStateOf(false)
}

private enum class LinkKind(val label: String, val prefix: String) {
    Website("Website", "https://"),
    Email("Email", "mailto:"),
    Phone("Phone", "tel:"),
    Text("Plain text", "")
}

private val knownPrefixes = listOf("https://", "http://", "mailto:", "tel:")

private fun detectKind(text: String): LinkKind? {
    val t = text.trim().lowercase()
    return when {
        t.isEmpty() -> null
        t.startsWith("http://") || t.startsWith("https://") -> LinkKind.Website
        t.startsWith("mailto:") -> LinkKind.Email
        t.startsWith("tel:") -> LinkKind.Phone
        else -> LinkKind.Text
    }
}

private fun stripKnownPrefix(text: String): String {
    val lower = text.lowercase()
    val hit = knownPrefixes.firstOrNull { lower.startsWith(it) }
    return if (hit != null) text.substring(hit.length) else text
}

@Composable
fun MediaQrScreen(
    state: MediaState,
    onMessage: (String) -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val scrollState = rememberScrollState()
    val actions = rememberCodeActions(onMessage, onSaved)
    var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bitmap = QrEngine.decodeSquareBitmap(context, uri)
                if (bitmap != null) {
                    state.galleryBitmap = bitmap
                    state.result = null
                } else {
                    onMessage("Unable to read that image.")
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { captured ->
        val uri = pendingCameraUri
        if (captured && uri != null) {
            scope.launch {
                val bitmap = QrEngine.decodeSquareBitmap(context, uri)
                if (bitmap != null) {
                    state.cameraBitmap = bitmap
                    state.result = null
                } else {
                    onMessage("Unable to read the captured photo.")
                }
            }
        }
    }

    val launchCamera = {
        val uri = QrEngine.createCameraUri(context)
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            onMessage("Camera permission is required to take a photo.")
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val (name, size) = QrEngine.queryFileInfo(context, uri)
            state.pdfName = name
            state.pdfSize = size
            state.result = null
        }
    }

    val onCameraClick = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            launchCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val isPdf = state.source == MediaSource.Pdf
    val selectedImage = when (state.source) {
        MediaSource.Gallery -> state.galleryBitmap
        MediaSource.Camera -> state.cameraBitmap
        MediaSource.Pdf -> null
    }
    val currentLink = if (isPdf) state.pdfLink else state.linkText
    val hasSource = if (isPdf) state.pdfName != null else selectedImage != null
    val hasLink = currentLink.isNotBlank()
    val hasResult = state.result != null
    val readyCount = listOf(hasSource, hasLink, hasResult).count { it }

    fun setLink(value: String) {
        if (isPdf) state.pdfLink = value else state.linkText = value
        state.result = null
    }

    fun generate() {
        val logo = when (state.source) {
            MediaSource.Gallery -> state.galleryBitmap
            MediaSource.Camera -> state.cameraBitmap
            MediaSource.Pdf -> null
        }
        val raw = currentLink.trim()
        val problem = when {
            isPdf && state.pdfName == null -> "Choose a PDF first."
            state.source == MediaSource.Gallery && logo == null -> "Choose an image first."
            state.source == MediaSource.Camera && logo == null -> "Take a photo first."
            raw.isEmpty() -> {
                if (isPdf) "Paste the link to your PDF."
                else "Enter the link or text for the code."
            }
            else -> null
        }

        if (problem != null) {
            onMessage(problem)
            return
        }

        val content = if (isPdf) normalizeUrl(raw) else raw

        scope.launch {
            state.generating = true
            try {
                state.result = QrEngine.renderQr(
                    context = context,
                    content = content,
                    style = state.style,
                    logoBitmap = logo
                )
                onMessage("Code generated successfully.")
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e("MediaQrScreen", "Code generation failed", exception)
                onMessage(exception.message ?: "Something went wrong.")
            } finally {
                state.generating = false
            }
        }
    }


    LaunchedEffect(state.result) {
        if (state.result != null) {
            delay(180)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            HeroBanner(readyCount = readyCount, total = 3)

            StepSection(
                number = 1,
                title = "Choose your source",
                subtitle = "Start with an image, a new photo, or a PDF.",
                done = hasSource
            ) {
                SourceSwitcher(
                    selected = state.source,
                    onSelect = {
                        state.source = it
                        state.result = null
                    }
                )

                when (state.source) {
                    MediaSource.Gallery -> ImageDropZone(
                        bitmap = state.galleryBitmap,
                        icon = Icons.Rounded.AddPhotoAlternate,
                        emptyTitle = "Add an image",
                        emptyHint = "A logo or photo for the center of your code.",
                        selectLabel = "Choose image",
                        changeLabel = "Change image",
                        onSelect = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        onClear = {
                            state.galleryBitmap = null
                            state.result = null
                        }
                    )

                    MediaSource.Camera -> ImageDropZone(
                        bitmap = state.cameraBitmap,
                        icon = Icons.Rounded.PhotoCamera,
                        emptyTitle = "Take a photo",
                        emptyHint = "Capture something to place in the center.",
                        selectLabel = "Open camera",
                        changeLabel = "Retake photo",
                        onSelect = onCameraClick,
                        onClear = {
                            state.cameraBitmap = null
                            state.result = null
                        }
                    )

                    MediaSource.Pdf -> PdfDropZone(
                        name = state.pdfName,
                        size = state.pdfSize,
                        onSelect = { pdfLauncher.launch(arrayOf("application/pdf")) }
                    )
                }
            }

            StepSection(
                number = 2,
                title = if (isPdf) "Add the document link" else "Set the destination",
                subtitle = if (isPdf) {
                    "Scanning the code opens this link."
                } else {
                    "Choose what the code opens or contains."
                },
                done = hasLink
            ) {
                if (!isPdf) {
                    val activeKind = detectKind(currentLink)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinkKind.entries.forEach { kind ->
                            QuickChip(
                                label = kind.label,
                                selected = activeKind == kind,
                                onClick = {
                                    val body = stripKnownPrefix(currentLink)
                                    setLink(kind.prefix + body)
                                }
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    ),
                    shadowElevation = 3.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        OutlinedTextField(
                            value = currentLink,
                            onValueChange = { setLink(it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text(if (isPdf) "PDF share URL" else "Website URL or text")
                            },
                            placeholder = {
                                Text(
                                    if (isPdf) "https://drive.google.com/..."
                                    else "https://yourwebsite.com"
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            minLines = 1,
                            maxLines = 3,
                            shape = RoundedCornerShape(18.dp),
                            leadingIcon = {
                                Icon(imageVector = Icons.Rounded.Link, contentDescription = null)
                            },
                            trailingIcon = {
                                if (currentLink.isEmpty()) {
                                    IconButton(onClick = {
                                        val pasted = clipboard.getText()?.text
                                        if (pasted.isNullOrBlank()) {
                                            onMessage("Clipboard is empty.")
                                        } else {
                                            setLink(pasted.trim())
                                        }
                                    }) {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentPaste,
                                            contentDescription = "Paste from clipboard"
                                        )
                                    }
                                } else {
                                    IconButton(onClick = { setLink("") }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Clear"
                                        )
                                    }
                                }
                            },
                            supportingText = {
                                Text(
                                    if (isPdf) {
                                        "Upload your PDF to cloud storage and turn on link access. The code stores the link, not the file."
                                    } else {
                                        "Your image is placed in the center of the code."
                                    }
                                )
                            }
                        )

                        if (currentLink.isNotBlank()) {
                            ScanMeter(length = currentLink.trim().length)
                        }
                    }
                }

                if (isPdf) {
                    InfoNote("Anyone scanning this code needs permission to open the shared PDF.")
                }
            }

            StepSection(
                number = 3,
                title = "Style it",
                subtitle = "Pick colors and shapes that match your brand.",
                done = false
            ) {
                StyleOptionsCard(
                    style = state.style,
                    onStyleChange = {
                        state.style = it
                        state.result = null
                    },
                    showAppLogo = false
                )
            }

            StepSection(
                number = 4,
                title = "Preview and share",
                subtitle = if (!hasResult) {
                    "Your finished code appears here."
                } else {
                    "Ready to save or share."
                },
                done = hasResult,
                isLast = true
            ) {
                CodePreviewCard(
                    bitmap = state.result,
                    formatLabel = "QR Code",
                    caption = if (isPdf) state.pdfName else null,
                    onSave = { state.result?.let(actions::save) },
                    onShare = { state.result?.let(actions::share) }
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Scan the code with your phone before sharing it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        GenerateBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            isPdf = isPdf,
            hasSource = hasSource,
            hasLink = hasLink,
            generating = state.generating,
            hasResult = hasResult,
            onGenerate = ::generate
        )
    }
}


@Composable
private fun HeroBanner(readyCount: Int, total: Int) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val fraction by animateFloatAsState(
        targetValue = readyCount / total.toFloat(),
        label = "heroProgress"
    )

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
                            text = "Media to QR",
                            style = MaterialTheme.typography.headlineMedium,
                            color = onPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 34.sp
                        )
                        Text(
                            text = "Put your image in the center of a code, or link a shared PDF. Style it, then save or share.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = onPrimary.copy(alpha = 0.86f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(108.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(onPrimary.copy(alpha = 0.15f))
                            .padding(14.dp)
                    ) {
                        QrMotif(
                            modifier = Modifier.fillMaxSize(),
                            color = onPrimary,
                            accent = onPrimary.copy(alpha = 0.95f),
                            center = primary
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Your progress",
                            style = MaterialTheme.typography.labelMedium,
                            color = onPrimary.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "$readyCount of $total done",
                            style = MaterialTheme.typography.labelMedium,
                            color = onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    ProgressTrack(
                        fraction = fraction,
                        color = onPrimary,
                        trackColor = onPrimary.copy(alpha = 0.22f),
                        height = 7.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressTrack(
    fraction: Float,
    color: Color,
    trackColor: Color,
    height: Dp
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
    }
}

@Composable
private fun QrMotif(
    modifier: Modifier,
    color: Color,
    accent: Color,
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
                if ((x * 7 + y * 13 + x * y) % 3 == 0) {
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
                color = accent,
                topLeft = Offset(ox * cell + stroke / 2f, oy * cell + stroke / 2f),
                size = Size(cell * 3 - stroke, cell * 3 - stroke),
                cornerRadius = CornerRadius(cell * 0.8f),
                style = Stroke(width = stroke)
            )
            drawRoundRect(
                color = accent,
                topLeft = Offset((ox + 1) * cell + cell * 0.08f, (oy + 1) * cell + cell * 0.08f),
                size = Size(cell * 0.84f, cell * 0.84f),
                cornerRadius = CornerRadius(cell * 0.3f)
            )
        }
        eye(0, 0)
        eye(n - 3, 0)
        eye(0, n - 3)

        drawRoundRect(
            color = accent,
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


@Composable
private fun StepSection(
    number: Int,
    title: String,
    subtitle: String,
    done: Boolean,
    isLast: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val railColor = MaterialTheme.colorScheme.outlineVariant
    val nodeColor by animateColorAsState(
        targetValue = if (done) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "stepNode"
    )
    val nodeContent by animateColorAsState(
        targetValue = if (done) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "stepNodeContent"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isLast) {
                    Modifier
                } else {
                    Modifier.drawBehind {
                        val x = 19.dp.toPx()
                        drawLine(
                            color = railColor,
                            start = Offset(x, 46.dp.toPx()),
                            end = Offset(x, size.height - 6.dp.toPx()),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
            ),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(nodeColor),
            contentAlignment = Alignment.Center
        ) {
            if (done) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Completed",
                    tint = nodeContent,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = nodeContent,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(top = 1.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            content()
        }
    }
}


@Composable
private fun SourceSwitcher(
    selected: MediaSource,
    onSelect: (MediaSource) -> Unit
) {
    val entries = MediaSource.entries
    val index = entries.indexOf(selected)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(4.dp)
    ) {
        val segment = maxWidth / entries.size
        val indicatorX by animateDpAsState(
            targetValue = segment * index,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "sourceIndicator"
        )

        Box(
            modifier = Modifier
                .offset(x = indicatorX)
                .width(segment)
                .height(50.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                )
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            entries.forEach { source ->
                val isSelected = source == selected
                val tint by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "sourceTint"
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(role = Role.Tab) { onSelect(source) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = source.icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = source.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = tint,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun Modifier.dashedOutline(color: Color, radius: Dp): Modifier = this.drawBehind {
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
private fun ImageDropZone(
    bitmap: Bitmap?,
    icon: ImageVector,
    emptyTitle: String,
    emptyHint: String,
    selectLabel: String,
    changeLabel: String,
    onSelect: () -> Unit,
    onClear: () -> Unit
) {
    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }
    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            .dashedOutline(primary.copy(alpha = 0.5f), 26.dp)
            .then(if (imageBitmap == null) Modifier.clickable(onClick = onSelect) else Modifier)
            .padding(20.dp)
    ) {
        if (imageBitmap == null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Text(
                    text = emptyTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = emptyHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(
                    onClick = onSelect,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(selectLabel)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(2.dp, primary),
                    shadowElevation = 4.dp
                ) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "Selected image",
                        modifier = Modifier
                            .size(104.dp)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(19.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Image ready",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "It will sit in the center of your QR code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = onSelect,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(changeLabel)
                        }
                        TextButton(onClick = onClear) {
                            Text("Remove")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfDropZone(
    name: String?,
    size: Long?,
    onSelect: () -> Unit
) {
    val context = LocalContext.current
    val error = MaterialTheme.colorScheme.error

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
            .dashedOutline(error.copy(alpha = 0.45f), 26.dp)
            .then(if (name == null) Modifier.clickable(onClick = onSelect) else Modifier)
            .padding(20.dp)
    ) {
        if (name == null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(error.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PictureAsPdf,
                        contentDescription = null,
                        tint = error,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Text(
                    text = "Choose a PDF",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Select the document you want people to open.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(
                    onClick = onSelect,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Browse files")
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(38.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (size != null) {
                        Text(
                            text = Formatter.formatShortFileSize(context, size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = onSelect,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Change PDF")
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickChip(
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
        label = "chipContainer"
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "chipContent"
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelLarge,
            color = content,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun ScanMeter(length: Int) {
    val level = when {
        length <= 40 -> 0
        length <= 90 -> 1
        else -> 2
    }
    val color by animateColorAsState(
        targetValue = when (level) {
            0 -> MaterialTheme.colorScheme.primary
            1 -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.error
        },
        label = "meterColor"
    )
    val fraction by animateFloatAsState(
        targetValue = (length / 120f).coerceIn(0.06f, 1f),
        label = "meterFraction"
    )
    val label = when (level) {
        0 -> "Easy to scan"
        1 -> "Scans well"
        else -> "Dense, a shorter link scans faster"
    }

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$length characters",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ProgressTrack(
            fraction = fraction,
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            height = 6.dp
        )
    }
}


@Composable
private fun GenerateBar(
    modifier: Modifier,
    isPdf: Boolean,
    hasSource: Boolean,
    hasLink: Boolean,
    generating: Boolean,
    hasResult: Boolean,
    onGenerate: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shadowElevation = 18.dp
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    ok = hasSource,
                    okText = if (isPdf) "PDF chosen" else "Image added",
                    todoText = if (isPdf) "Choose a PDF" else "Add an image"
                )
                StatusPill(
                    ok = hasLink,
                    okText = "Link added",
                    todoText = "Add a link"
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (generating) {
                            SolidColor(primary.copy(alpha = 0.7f))
                        } else {
                            Brush.horizontalGradient(listOf(primary, tertiary))
                        }
                    )
                    .clickable(
                        enabled = !generating,
                        role = Role.Button,
                        onClick = onGenerate
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (generating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(21.dp),
                            color = onPrimary,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Creating your code",
                            style = MaterialTheme.typography.titleMedium,
                            color = onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = if (hasResult) "Generate again" else "Generate QR code",
                            style = MaterialTheme.typography.titleMedium,
                            color = onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun StatusPill(
    ok: Boolean,
    okText: String,
    todoText: String
) {
    val container by animateColorAsState(
        targetValue = if (ok) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        },
        label = "pillContainer"
    )
    val tint = if (ok) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(shape = RoundedCornerShape(50.dp), color = container) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (ok) Icons.Rounded.Check else Icons.Rounded.Info,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = if (ok) okText else todoText,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun InfoNote(text: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}