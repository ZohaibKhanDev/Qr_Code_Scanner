package com.dev.qrcodescanner

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Stable
class CreateState {
    var type by mutableStateOf(ContentType.Text)
    val values = mutableStateMapOf<String, String>()
    var style by mutableStateOf(QrStyle())
    var result by mutableStateOf<Bitmap?>(null)
    var generating by mutableStateOf(false)
}

private val ContentType.accent: Pair<Color, Color>
    get() = when (this) {
        ContentType.Text -> Color(0xFF6366F1) to Color(0xFF8B5CF6)
        ContentType.Link -> Color(0xFF0EA5E9) to Color(0xFF2563EB)
        ContentType.WiFi -> Color(0xFF14B8A6) to Color(0xFF10B981)
        ContentType.Contact -> Color(0xFFF59E0B) to Color(0xFFF97316)
        ContentType.Email -> Color(0xFFF43F5E) to Color(0xFFEC4899)
        ContentType.Phone -> Color(0xFF16A34A) to Color(0xFF22C55E)
        ContentType.Sms -> Color(0xFF8B5CF6) to Color(0xFFD946EF)
        ContentType.Barcode -> Color(0xFF475569) to Color(0xFF0F172A)
    }

private val ContentType.icon: ImageVector
    get() = when (this) {
        ContentType.Text -> Icons.Rounded.TextFields
        ContentType.Link -> Icons.Rounded.Link
        ContentType.WiFi -> Icons.Rounded.Wifi
        ContentType.Contact -> Icons.Rounded.Person
        ContentType.Email -> Icons.Rounded.Email
        ContentType.Phone -> Icons.Rounded.Phone
        ContentType.Sms -> Icons.Rounded.Sms
        ContentType.Barcode -> Icons.Rounded.ViewWeek
    }

private val ContentType.tagline: String
    get() = when (this) {
        ContentType.Text -> "Plain note"
        ContentType.Link -> "Website"
        ContentType.WiFi -> "Join network"
        ContentType.Contact -> "Contact card"
        ContentType.Email -> "Compose mail"
        ContentType.Phone -> "Dial number"
        ContentType.Sms -> "Send message"
        ContentType.Barcode -> "Code 128"
    }

private val ContentType.hint: String
    get() = when (this) {
        ContentType.Text -> "Encode any plain text message."
        ContentType.Link -> "Open a website when the code is scanned."
        ContentType.WiFi -> "Let people join your network without typing a password."
        ContentType.Contact -> "Share a contact card that can be saved instantly."
        ContentType.Email -> "Start a pre-filled email when scanned."
        ContentType.Phone -> "Dial a phone number when scanned."
        ContentType.Sms -> "Start a pre-filled text message when scanned."
        ContentType.Barcode -> "Create a Code 128 barcode for products and inventory."
    }

@Composable
fun QrGeneratorScreen(
    state: CreateState,
    onMessage: (String) -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val actions = rememberCodeActions(onMessage, onSaved)
    val isBarcode = state.type == ContentType.Barcode

    val accentStart by animateColorAsState(
        targetValue = state.type.accent.first,
        animationSpec = tween(500),
        label = "accentStart"
    )
    val accentEnd by animateColorAsState(
        targetValue = state.type.accent.second,
        animationSpec = tween(500),
        label = "accentEnd"
    )

    fun generate() {
        focusManager.clearFocus()

        val payload = state.type.buildPayload(state.values)

        if (payload == null) {
            onMessage("Please fill in the required fields.")
            return
        }

        scope.launch {
            state.generating = true
            try {
                state.result = if (isBarcode) {
                    QrEngine.renderCode128(payload)
                } else {
                    QrEngine.renderQr(context, payload, state.style)
                }
                onMessage("Code generated successfully.")
                scrollState.animateScrollTo(scrollState.maxValue)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e("QrGeneratorScreen", "Code generation failed", exception)
                onMessage(exception.message ?: "Something went wrong.")
            } finally {
                state.generating = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Reveal(delayMillis = 0) {
            Hero(
                type = state.type,
                start = accentStart,
                end = accentEnd,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        Reveal(delayMillis = 80) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionHeader(
                    index = "01",
                    title = "Choose a type",
                    subtitle = "Pick what your code should do",
                    accent = accentStart,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                TypeCarousel(
                    selected = state.type,
                    onSelect = {
                        state.type = it
                        state.result = null
                    }
                )
            }
        }

        Reveal(delayMillis = 160) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionHeader(
                    index = "02",
                    title = "Add details",
                    subtitle = "Fields marked * are required",
                    accent = accentStart,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                DetailsCard(
                    state = state,
                    start = accentStart,
                    end = accentEnd,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }

        if (!isBarcode) {
            Reveal(delayMillis = 240) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionHeader(
                        index = "03",
                        title = "Customize",
                        subtitle = "Shape, colors and extras",
                        accent = accentStart,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    StyleOptionsCard(
                        style = state.style,
                        onStyleChange = {
                            state.style = it
                            state.result = null
                        },
                        showAppLogo = true,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
        }

        Reveal(delayMillis = 320) {
            GenerateButton(
                generating = state.generating,
                start = accentStart,
                end = accentEnd,
                onClick = { generate() },
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        Reveal(delayMillis = 400) {
            ScannerPreview(
                bitmap = state.result,
                generating = state.generating,
                isBarcode = isBarcode,
                formatLabel = if (isBarcode) "Code 128" else "QR Code",
                start = accentStart,
                end = accentEnd,
                onSave = { state.result?.let(actions::save) },
                onShare = { state.result?.let(actions::share) },
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun Reveal(
    delayMillis: Int,
    content: @Composable () -> Unit
) {
    val visibleState = remember {
        MutableTransitionState(false).apply { targetState = true }
    }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(tween(450, delayMillis)) +
                slideInVertically(tween(450, delayMillis)) { it / 6 }
    ) {
        content()
    }
}

@Composable
private fun Hero(
    type: ContentType,
    start: Color,
    end: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "hero")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.linearGradient(listOf(start, end)))
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = size.minDimension * 0.75f,
                center = Offset(
                    size.width * (0.92f - 0.06f * drift),
                    size.height * (0.05f + 0.12f * drift)
                )
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = size.minDimension * 0.5f,
                center = Offset(
                    size.width * (0.08f + 0.06f * drift),
                    size.height * (1.0f - 0.1f * drift)
                )
            )

            val cell = 6.dp.toPx()
            val gap = 3.dp.toPx()
            val cols = 7
            val originX = size.width - cols * (cell + gap) - 18.dp.toPx()
            val originY = 20.dp.toPx()

            for (x in 0 until cols) {
                for (y in 0 until cols) {
                    val finder = (x < 3 && y < 3) ||
                            (x >= cols - 3 && y < 3) ||
                            (x < 3 && y >= cols - 3)
                    val filled = finder || ((x * 5 + y * 3 + x * y) % 3 == 0)
                    if (filled) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = if (finder) 0.32f else 0.16f),
                            topLeft = Offset(
                                originX + x * (cell + gap),
                                originY + y * (cell + gap)
                            ),
                            size = Size(cell, cell),
                            cornerRadius = CornerRadius(cell * 0.3f)
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.padding(
                start = 24.dp,
                top = 26.dp,
                end = 92.dp,
                bottom = 26.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.2f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = type.icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
                Text(
                    text = "${type.label} code",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            Text(
                text = "Create something scannable",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )

            Text(
                text = "Turn text, links, and other information into a beautiful code.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.88f)
            )
        }
    }
}

@Composable
private fun SectionHeader(
    index: String,
    title: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = index,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = accent.copy(alpha = 0.9f)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TypeCarousel(
    selected: ContentType,
    onSelect: (ContentType) -> Unit
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ContentType.entries.forEach { type ->
            TypeCard(
                type = type,
                selected = selected == type,
                onClick = { onSelect(type) }
            )
        }
    }
}

@Composable
private fun TypeCard(
    type: ContentType,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val (start, end) = type.accent
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.96f,
        label = "tileScale"
    )
    val elevation by animateDpAsState(
        targetValue = if (selected) 10.dp else 0.dp,
        label = "tileElevation"
    )
    val titleColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    val subtitleColor = if (selected) {
        Color.White.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val surface = MaterialTheme.colorScheme.surfaceContainerLow

    Column(
        modifier = Modifier
            .width(116.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = start,
                spotColor = start
            )
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.background(Brush.linearGradient(listOf(start, end)))
                } else {
                    Modifier.background(surface)
                }
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    if (selected) Color.White.copy(alpha = 0.22f) else start.copy(alpha = 0.14f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = type.icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (selected) Color.White else start
            )
        }

        Column {
            Text(
                text = type.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = type.tagline,
                style = MaterialTheme.typography.labelSmall,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DetailsCard(
    state: CreateState,
    start: Color,
    end: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(start, end))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = state.type.icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.White
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${state.type.label} details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = state.type.hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        state.values.clear()
                        state.result = null
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Clear fields",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedContent(
                targetState = state.type,
                label = "fields"
            ) { type ->
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    type.fields.forEach { spec ->
                        SpecField(
                            spec = spec,
                            value = state.values[spec.key].orEmpty(),
                            onValueChange = {
                                state.values[spec.key] = it
                                state.result = null
                            }
                        )
                    }

                    if (type == ContentType.WiFi) {
                        SectionLabel("Security")
                        PillSelector(
                            options = WifiSecurityOptions,
                            selected = state.values[WifiSecurityKey] ?: "WPA",
                            start = start,
                            end = end,
                            onSelect = {
                                state.values[WifiSecurityKey] = it
                                state.result = null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PillSelector(
    options: List<String>,
    selected: String,
    start: Color,
    end: Color,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (isSelected) {
                            Modifier.background(Brush.horizontalGradient(listOf(start, end)))
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) {
                        Color.White
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun GenerateButton(
    generating: Boolean,
    start: Color,
    end: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .shadow(
                elevation = if (generating) 0.dp else 12.dp,
                shape = shape,
                ambientColor = start,
                spotColor = start
            )
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(start, end)))
            .clickable(enabled = !generating, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (generating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.5.dp
                )
                Text(
                    text = "Generating...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = Color.White
                )
                Text(
                    text = "Generate Code",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun ScannerPreview(
    bitmap: Bitmap?,
    generating: Boolean,
    isBarcode: Boolean,
    formatLabel: String,
    start: Color,
    end: Color,
    onSave: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "scan")
    val scan by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLine"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Your code",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            generating -> "Building your code..."
                            bitmap != null -> "Ready to save or share"
                            else -> "Fill in the details and generate"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(start, end)))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = formatLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            Box(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .then(
                        if (isBarcode) {
                            Modifier.height(200.dp)
                        } else {
                            Modifier.aspectRatio(1f)
                        }
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(
                    targetState = bitmap,
                    label = "codeFade"
                ) { current ->
                    if (current != null) {
                        val imageBitmap = remember(current) { current.asImageBitmap() }
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = "$formatLabel preview",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(28.dp)
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QrCode2,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = start.copy(alpha = 0.35f)
                            )
                            Text(
                                text = if (generating) {
                                    "Building your code..."
                                } else {
                                    "Your code will appear here"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF64748B),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(14.dp)
                ) {
                    val length = 30.dp.toPx()
                    val strokeWidth = 4.dp.toPx()
                    val width = size.width
                    val height = size.height

                    fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                        drawLine(
                            color = start,
                            start = Offset(x, y),
                            end = Offset(x + dx * length, y),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = start,
                            start = Offset(x, y),
                            end = Offset(x, y + dy * length),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }

                    corner(0f, 0f, 1f, 1f)
                    corner(width, 0f, -1f, 1f)
                    corner(0f, height, 1f, -1f)
                    corner(width, height, -1f, -1f)

                    if (generating) {
                        val y = height * scan
                        drawRect(
                            color = start.copy(alpha = 0.12f),
                            topLeft = Offset(0f, y - 40.dp.toPx()),
                            size = Size(width, 40.dp.toPx())
                        )
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(Color.Transparent, start, end, Color.Transparent)
                            ),
                            topLeft = Offset(0f, y - 2.dp.toPx()),
                            size = Size(width, 4.dp.toPx())
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = onSave,
                    enabled = bitmap != null,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = start.copy(alpha = 0.16f),
                        contentColor = start
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Save", fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onShare,
                    enabled = bitmap != null,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Share", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}