package com.dev.qrcodescanner

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private enum class Destination(
    val label: String,
    val subtitle: String,
    val icon: ImageVector
) {
    Create("Create", "Text, links, Wi-Fi & more", Icons.Rounded.QrCode2),
    Media("Media", "Image, camera & PDF", Icons.Rounded.PermMedia),
    Library("Library", "Your saved codes", Icons.Rounded.PhotoLibrary)
}

@Composable
fun MainScreen() {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var libraryVersion by remember { mutableIntStateOf(0) }
    var seenVersion by remember { mutableIntStateOf(0) }
    val createState = remember { CreateState() }
    val mediaState = remember { MediaState() }
    val destinations = Destination.entries
    val current = destinations[selectedIndex]

    val showMessage = remember<(String) -> Unit> {
        { message ->
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    val onSaved = remember<() -> Unit> { { libraryVersion++ } }

    LaunchedEffect(selectedIndex, libraryVersion) {
        if (destinations[selectedIndex] == Destination.Library) {
            seenVersion = libraryVersion
        }
    }
    val hasNewSaved = libraryVersion > seenVersion

    BackHandler(enabled = selectedIndex != 0) {
        selectedIndex = 0
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            StudioTopBar(
                current = current,
                hasNewSaved = hasNewSaved,
                onOpenLibrary = { selectedIndex = Destination.Library.ordinal },
                onCreate = { selectedIndex = Destination.Create.ordinal }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp)
                )
            }
        },
        bottomBar = {
            StudioNavBar(
                destinations = destinations,
                selectedIndex = selectedIndex,
                showLibraryBadge = hasNewSaved,
                onSelect = { selectedIndex = it }
            )
        }
    ) { innerPadding ->
        Crossfade(
            targetState = selectedIndex,
            modifier = Modifier.padding(innerPadding),
            label = "destination"
        ) { index ->
            when (destinations[index]) {
                Destination.Create -> QrGeneratorScreen(
                    state = createState,
                    onMessage = showMessage,
                    onSaved = onSaved
                )

                Destination.Media -> MediaQrScreen(
                    state = mediaState,
                    onMessage = showMessage,
                    onSaved = onSaved
                )

                Destination.Library -> LibraryScreen(
                    refreshKey = libraryVersion,
                    onMessage = showMessage
                )
            }
        }
    }
}

@Composable
private fun StudioTopBar(
    current: Destination,
    hasNewSaved: Boolean,
    onOpenLibrary: () -> Unit,
    onCreate: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val surface = MaterialTheme.colorScheme.surface

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
        color = surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(primary, tertiary))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.QrCode2,
                    contentDescription = null,
                    tint = onPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Code Studio",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Crossfade(targetState = current, label = "topBarSubtitle") { destination ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = destination.subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            if (current == Destination.Library) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(primary, tertiary)))
                        .clickable(role = Role.Button, onClick = onCreate),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Create a new code",
                        tint = onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(primary.copy(alpha = 0.1f))
                        .clickable(role = Role.Button, onClick = onOpenLibrary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = "Open your library",
                        tint = primary,
                        modifier = Modifier.size(23.dp)
                    )
                    if (hasNewSaved) {
                        BadgeDot(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-5).dp, y = 5.dp),
                            ringColor = surface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeDot(
    modifier: Modifier,
    ringColor: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = modifier
            .size(12.dp)
            .background(MaterialTheme.colorScheme.error, CircleShape)
            .border(2.dp, ringColor, CircleShape)
    )
}

@Composable
private fun StudioNavBar(
    destinations: List<Destination>,
    selectedIndex: Int,
    showLibraryBadge: Boolean,
    onSelect: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            ),
            shadowElevation = 14.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                destinations.forEachIndexed { index, destination ->
                    StudioNavItem(
                        destination = destination,
                        selected = selectedIndex == index,
                        showBadge = destination == Destination.Library &&
                                showLibraryBadge && selectedIndex != index,
                        onClick = { onSelect(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.StudioNavItem(
    destination: Destination,
    selected: Boolean,
    showBadge: Boolean,
    onClick: () -> Unit
) {
    val weight by animateFloatAsState(
        targetValue = if (selected) 1.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "navWeight"
    )
    val fill by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        label = "navFill"
    )
    val tint by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "navTint"
    )

    Box(
        modifier = Modifier
            .weight(weight)
            .height(54.dp)
            .clip(RoundedCornerShape(26.dp))
            .clickable(role = Role.Tab, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .alpha(fill)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                )
        )

        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = if (selected) null else destination.label,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
                if (showBadge) {
                    BadgeDot(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 5.dp, y = (-3).dp),
                        ringColor = MaterialTheme.colorScheme.surface
                    )
                }
            }
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Row {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = tint,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}