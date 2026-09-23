package com.example.telegrambackup.ui.common

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.math.abs

/**
 * Smart auto-hide NestedScrollConnection for the bottom navigation bar.
 * Detects vertical scroll deltas:
 * - Scrolling DOWN (user reading/scrolling list down): hides the bottom bar to maximize screen estate
 * - Scrolling UP (user wants controls): shows the bottom bar smoothly
 */
@Composable
fun rememberSmartScrollNavBarConnection(
    thresholdDp: Float = 14f,
    initialVisible: Boolean = true
): Pair<MutableState<Boolean>, NestedScrollConnection> {
    val isVisible = remember { mutableStateOf(initialVisible) }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (abs(dy) > thresholdDp) {
                    if (dy < 0 && isVisible.value) {
                        // Scrolling DOWN -> Hide bar
                        isVisible.value = false
                    } else if (dy > 0 && !isVisible.value) {
                        // Scrolling UP -> Reveal bar
                        isVisible.value = true
                    }
                }
                return Offset.Zero
            }
        }
    }

    return Pair(isVisible, connection)
}
