package com.dev.qrcodescanner

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Info
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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

    val detailsReady = state.type.buildPayload(state.values) != null
    val hasResult = state.result != null
    val readyCount = listOf(detailsReady, hasResult).count { it }

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
                        accentStart.copy(alpha = 0.13f),
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
            CreateHero(
                type = state.type,
                start = accentStart,
                end = accentEnd,
                readyCount = readyCount,
                total = 2
            )

            CreateStepSection(
                number = 1,
                title = "Choose a type",
                subtitle = "Pick what your code should do.",
                done = true,
                accent = accentStart
            ) {
                CreateTypeCarousel(
                    selected = state.type,
                    onSelect = {
                        state.type = it
                        state.result = null
                    }
                )
            }

            CreateStepSection(
                number = 2,
                title = "Add details",
                subtitle = "Fields marked * are required.",
                done = detailsReady,
                accent = accentStart
            ) {
                CreateDetailsCard(
                    state = state,
                    start = accentStart,
                    end = accentEnd
                )
            }

            if (!isBarcode) {
                CreateStepSection(
                    number = 3,
                    title = "Customize",
                    subtitle = "Shape, colors and extras.",
                    done = false,
                    accent = accentStart
                ) {
                    StyleOptionsCard(
                        style = state.style,
                        onStyleChange = {
                            state.style = it
                            state.result = null
                        },
                        showAppLogo = true
                    )
                }
            }

            CreateStepSection(
                number = if (isBarcode) 3 else 4,
                title = "Preview and share",
                subtitle = when {
                    state.generating -> "Building your code."
                    hasResult -> "Ready to save or share."
                    else -> "Your finished code appears here."
                },
                done = hasResult,
                accent = accentStart,
                isLast = true
            ) {
                CreatePreviewCard(
                    bitmap = state.result,
                    generating = state.generating,
                    isBarcode = isBarcode,
                    formatLabel = if (isBarcode) "Code 128" else "QR Code",
                    start = accentStart,
                    end = accentEnd,
                    onSave = { state.result?.let(actions::save) },
                    onShare = { state.result?.let(actions::share) }
                )
            }
        }

        CreateGenerateBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            detailsReady = detailsReady,
            hasResult = hasResult,
            generating = state.generating,
            start = accentStart,
            end = accentEnd,
            onGenerate = ::generate
        )
    }
}

@Composable
private fun CreateHero(
    type: ContentType,
    start: Color,
    end: Color,
    readyCount: Int,
    total: Int
) {
    val fraction by animateFloatAsState(
        targetValue = readyCount / total.toFloat(),
        label = "heroProgress"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = start,
        shadowElevation = 12.dp
    ) {
        Box(modifier = Modifier.background(Brush.linearGradient(listOf(start, end)))) {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 80.dp, y = (-90).dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.09f))
            )
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-40).dp, y = 50.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
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
                            color = Color.White,
                            lineHeight = 30.sp
                        )
                        Text(
                            text = "Turn text, links and other information into a code people can scan.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .padding(13.dp)
                    ) {
                        CreateMotif(
                            modifier = Modifier.fillMaxSize(),
                            color = Color.White,
                            center = start
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
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "$readyCount of $total done",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    CreateProgressTrack(
                        fraction = fraction,
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.22f),
                        height = 7.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateProgressTrack(
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
private fun CreateMotif(
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


@Composable
private fun CreateStepSection(
    number: Int,
    title: String,
    subtitle: String,
    done: Boolean,
    accent: Color,
    isLast: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val railColor = MaterialTheme.colorScheme.outlineVariant
    val nodeColor by animateColorAsState(
        targetValue = if (done) accent else MaterialTheme.colorScheme.surfaceVariant,
        label = "stepNode"
    )
    val nodeContent by animateColorAsState(
        targetValue = if (done) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun CreateTypeCarousel(
    selected: ContentType,
    onSelect: (ContentType) -> Unit
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ContentType.entries.forEach { type ->
            CreateTypeCard(
                type = type,
                selected = selected == type,
                onClick = { onSelect(type) }
            )
        }
    }
}

@Composable
private fun CreateTypeCard(
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
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

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
                    Modifier
                        .background(surface)
                        .border(1.dp, outline, shape)
                }
            )
            .clickable(role = Role.Button, onClick = onClick)
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
private fun CreateDetailsCard(
    state: CreateState,
    start: Color,
    end: Color
) {
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
            modifier = Modifier.padding(18.dp),
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
                        CreatePillSelector(
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
private fun CreatePillSelector(
    options: List<String>,
    selected: String,
    start: Color,
    end: Color,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
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
                    .clip(RoundedCornerShape(14.dp))
                    .then(
                        if (isSelected) {
                            Modifier.background(Brush.horizontalGradient(listOf(start, end)))
                        } else {
                            Modifier
                        }
                    )
                    .clickable(role = Role.Tab) { onSelect(option) },
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
private fun CreatePreviewCard(
    bitmap: Bitmap?,
    generating: Boolean,
    isBarcode: Boolean,
    formatLabel: String,
    start: Color,
    end: Color,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your code",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
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
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color.White)
                    .border(1.dp, Color.Black.copy(alpha = 0.06f), RoundedCornerShape(26.dp)),
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
                                .padding(26.dp)
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

                ScannerCorners(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(14.dp),
                    color = start
                )

                if (generating) {
                    ScanSweep(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(14.dp),
                        start = start,
                        end = end
                    )
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

@Composable
private fun ScannerCorners(
    modifier: Modifier,
    color: Color
) {
    Canvas(modifier = modifier) {
        val length = 30.dp.toPx()
        val strokeWidth = 4.dp.toPx()
        val width = size.width
        val height = size.height

        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(
                color = color,
                start = Offset(x, y),
                end = Offset(x + dx * length, y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
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
    }
}

@Composable
private fun ScanSweep(
    modifier: Modifier,
    start: Color,
    end: Color
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

    Canvas(modifier = modifier) {
        val y = size.height * scan
        drawRect(
            color = start.copy(alpha = 0.12f),
            topLeft = Offset(0f, y - 40.dp.toPx()),
            size = Size(size.width, 40.dp.toPx())
        )
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, start, end, Color.Transparent)
            ),
            topLeft = Offset(0f, y - 2.dp.toPx()),
            size = Size(size.width, 4.dp.toPx())
        )
    }
}


@Composable
private fun CreateGenerateBar(
    modifier: Modifier,
    detailsReady: Boolean,
    hasResult: Boolean,
    generating: Boolean,
    start: Color,
    end: Color,
    onGenerate: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shadowElevation = 18.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CreateStatusPill(
                    ok = detailsReady,
                    okText = "Details ready",
                    todoText = "Fill required fields",
                    accent = start
                )
                CreateStatusPill(
                    ok = hasResult,
                    okText = "Code ready",
                    todoText = "Not generated yet",
                    accent = start
                )
            }

            val shape = RoundedCornerShape(20.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .alpha(if (detailsReady || generating) 1f else 0.75f)
                    .shadow(
                        elevation = if (generating) 0.dp else 10.dp,
                        shape = shape,
                        ambientColor = start,
                        spotColor = start
                    )
                    .clip(shape)
                    .background(Brush.horizontalGradient(listOf(start, end)))
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
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                        Text(
                            text = "Generating",
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
                            text = if (hasResult) "Generate again" else "Generate code",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateStatusPill(
    ok: Boolean,
    okText: String,
    todoText: String,
    accent: Color
) {
    val container by animateColorAsState(
        targetValue = if (ok) {
            accent.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        },
        label = "pillContainer"
    )
    val tint = if (ok) accent else MaterialTheme.colorScheme.onSurfaceVariant

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