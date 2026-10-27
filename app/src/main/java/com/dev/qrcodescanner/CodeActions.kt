package com.dev.qrcodescanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val foregroundColors = listOf(
    AndroidColor.BLACK,
    AndroidColor.RED,
    AndroidColor.BLUE,
    AndroidColor.rgb(46, 125, 50),
    AndroidColor.rgb(103, 58, 183)
)

private val backgroundColors = listOf(
    AndroidColor.WHITE,
    AndroidColor.rgb(255, 249, 196),
    AndroidColor.rgb(227, 242, 253),
    AndroidColor.rgb(252, 228, 236)
)

class CodeActions(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onMessage: (String) -> Unit,
    private val onSaved: () -> Unit
) {
    fun save(bitmap: Bitmap) {
        scope.launch {
            try {
                QrEngine.saveToLibrary(context, bitmap)
                onSaved()
                onMessage("Saved to your library.")
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e("CodeActions", "Saving code failed", exception)
                onMessage(exception.message ?: "Unable to save the code.")
            }
        }
    }

    fun share(bitmap: Bitmap) {
        scope.launch {
            try {
                QrEngine.shareBitmap(context, bitmap)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e("CodeActions", "Sharing code failed", exception)
                onMessage(exception.message ?: "Unable to share the code.")
            }
        }
    }
}

@Composable
fun rememberCodeActions(
    onMessage: (String) -> Unit,
    onSaved: () -> Unit
): CodeActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return CodeActions(context, scope, onMessage, onSaved)
}

@Composable
fun ScreenHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun SpecField(
    spec: FieldSpec,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = {
            Text(if (spec.required) "${spec.label} *" else spec.label)
        },
        placeholder = if (spec.placeholder.isNotEmpty()) {
            { Text(spec.placeholder) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = spec.keyboardType),
        singleLine = !spec.multiline,
        minLines = if (spec.multiline) 3 else 1,
        maxLines = if (spec.multiline) 6 else 1,
        shape = RoundedCornerShape(16.dp),
        supportingText = if (spec.multiline) {
            { Text("${value.length} characters") }
        } else {
            null
        }
    )
}

@Composable
fun ColorOptions(
    colors: List<Int>,
    selectedColor: Int,
    onColorSelected: (Int) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        colors.forEach { colorValue ->
            val selected = colorValue == selectedColor

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(colorValue))
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.LightGray
                        },
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(colorValue) }
            )
        }
    }
}

@Composable
fun StyleOptionsCard(
    style: QrStyle,
    onStyleChange: (QrStyle) -> Unit,
    showAppLogo: Boolean,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier) {
        SectionTitle("Style")

        SectionLabel("Shape")
        ChipRow {
            QrShapeStyles.forEach { shape ->
                FilterChip(
                    selected = style.shape == shape,
                    onClick = { onStyleChange(style.copy(shape = shape)) },
                    label = { Text(shape) }
                )
            }
        }

        SectionLabel("Extras")
        ChipRow {
            FilterChip(
                selected = style.useGradient,
                onClick = {
                    onStyleChange(style.copy(useGradient = !style.useGradient))
                },
                label = { Text("Gradient frame") }
            )

            if (showAppLogo) {
                FilterChip(
                    selected = style.useAppLogo,
                    onClick = {
                        onStyleChange(style.copy(useAppLogo = !style.useAppLogo))
                    },
                    label = { Text("Center logo") }
                )
            }
        }

        SectionLabel("Foreground color")
        ColorOptions(
            colors = foregroundColors,
            selectedColor = style.foreground,
            onColorSelected = { onStyleChange(style.copy(foreground = it)) }
        )

        SectionLabel("Background color")
        ColorOptions(
            colors = backgroundColors,
            selectedColor = style.background,
            onColorSelected = { onStyleChange(style.copy(background = it)) }
        )
    }
}

@Composable
fun CodePreviewCard(
    bitmap: Bitmap?,
    formatLabel: String,
    caption: String?,
    onSave: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Preview",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = formatLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "$formatLabel preview",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        color = Color(0xFFF1F5F9)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "QR",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                    Text(
                        text = "Your code will appear here",
                        color = Color(0xFF64748B),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Text(
            text = when {
                bitmap == null -> "Enter your content and generate a code to see the preview."
                caption != null -> caption
                else -> "Your code is ready to save or share."
            },
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(
                onClick = onSave,
                enabled = bitmap != null,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
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
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
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

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}