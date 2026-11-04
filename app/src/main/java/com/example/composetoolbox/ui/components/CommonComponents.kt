package com.example.composetoolbox.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.composetoolbox.ui.LocalOpenDrawer
import com.example.composetoolbox.ui.theme.ComposeToolboxTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Shows a hamburger IconButton only when the shell provides a drawer opener (CompositionLocal). */
@Composable
fun DrawerMenuButton() {
    val openDrawer = LocalOpenDrawer.current ?: return
    IconButton(onClick = openDrawer) {
        Icon(Icons.Filled.Menu, contentDescription = "Open menu")
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Inbox,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Stateless label/value pair (state hoisting: it only displays what it is given). */
@Composable
fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 4.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Custom `Layout`: measures every child with exactly the same width
 * (available width / child count) and centres them vertically.
 */
@Composable
fun EqualWidthRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        if (measurables.isEmpty() || !constraints.hasBoundedWidth) {
            return@Layout layout(constraints.minWidth, constraints.minHeight) {}
        }
        val itemWidth = constraints.maxWidth / measurables.size
        val placeables = measurables.map {
            it.measure(Constraints(minWidth = itemWidth, maxWidth = itemWidth, maxHeight = constraints.maxHeight))
        }
        val height = placeables.maxOf { it.height }
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { index, placeable ->
                placeable.placeRelative(index * itemWidth, (height - placeable.height) / 2)
            }
        }
    }
}

/**
 * `SubcomposeLayout`: composes the items once just to find the widest one,
 * then composes them again so that every item gets that same width.
 */
@Composable
fun UniformWidthRow(
    items: List<String>,
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    itemContent: @Composable (String) -> Unit,
) {
    SubcomposeLayout(modifier) { constraints ->
        val gap = spacing.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val widest = subcompose("measure") {
            items.forEach { Box(propagateMinConstraints = true) { itemContent(it) } }
        }.maxOfOrNull { it.measure(loose).width } ?: 0

        val itemWidth = if (constraints.hasBoundedWidth && items.isNotEmpty()) {
            minOf(widest, (constraints.maxWidth - gap * (items.size - 1)) / items.size)
        } else widest

        val placeables = subcompose("content") {
            items.forEach { Box(propagateMinConstraints = true) { itemContent(it) } }
        }.map { it.measure(Constraints.fixedWidth(itemWidth.coerceAtLeast(0))) }

        val height = placeables.maxOfOrNull { it.height } ?: 0
        val width = placeables.size * itemWidth + gap * (placeables.size - 1).coerceAtLeast(0)
        layout(width, height) {
            var x = 0
            placeables.forEach {
                it.placeRelative(x, 0)
                x += itemWidth + gap
            }
        }
    }
}

/**
 * "Slide to confirm" control built with Modifier.draggable + Animatable.
 * Releasing past 85% triggers [onConfirmed]; otherwise the thumb springs back.
 */
@Composable
fun SlideToConfirm(text: String, onConfirmed: () -> Unit, modifier: Modifier = Modifier) {
    val thumbSize = 56.dp
    val thumbPx = with(LocalDensity.current) { thumbSize.toPx() }
    var trackWidth by remember { mutableFloatStateOf(0f) }
    val maxOffset = (trackWidth - thumbPx).coerceAtLeast(0f)
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentOnConfirmed by rememberUpdatedState(onConfirmed)
    val progress = if (maxOffset > 0f) offsetX.value / maxOffset else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thumbSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .onSizeChanged { trackWidth = it.width.toFloat() },
    ) {
        Text(
            text,
            modifier = Modifier
                .align(Alignment.Center)
                .alpha(1f - progress),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .size(thumbSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { offsetX.snapTo((offsetX.value + delta).coerceIn(0f, maxOffset)) }
                    },
                    onDragStopped = {
                        if (maxOffset > 0f && offsetX.value >= maxOffset * 0.85f) {
                            offsetX.animateTo(maxOffset)
                            currentOnConfirmed()
                            offsetX.snapTo(0f)
                        } else {
                            offsetX.animateTo(0f)
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = text,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SlideToConfirmPreview() {
    ComposeToolboxTheme(dynamicColor = false) {
        SlideToConfirm(text = "Slide to check out", onConfirmed = {}, modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun EqualWidthRowPreview() {
    ComposeToolboxTheme(dynamicColor = false) {
        EqualWidthRow(Modifier.fillMaxWidth()) {
            StatItem("Total", "36")
            StatItem("Occupied", "4")
            StatItem("Free", "32")
        }
    }
}
