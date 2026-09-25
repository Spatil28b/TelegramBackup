package com.example.telegrambackup.ui.common

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.math.abs

/**
 * Smart auto-hide NestedScrollConnection for the bottom navigation bar.
 * Rules:
 * - If screen is NOT scrollable (consumed.y == 0), the bar will NEVER auto-hide on touch/drag.
 * - Only hides when the user actively scrolls DOWN on scrollable content past [thresholdDp].
 * - Shows smoothly when the user scrolls UP on scrollable content.
 */
@Composable
fun rememberSmartScrollNavBarConnection(
    thresholdDp: Float = 16f,
    initialVisible: Boolean = true
): Pair<MutableState<Boolean>, NestedScrollConnection> {
    val isVisible = remember { mutableStateOf(initialVisible) }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // If content is not scrollable or at boundary without consuming, consumed.y is 0.
                // We NEVER hide the nav bar unless scroll delta was actually consumed by scrollable content!
                val dy = consumed.y
                if (abs(dy) > thresholdDp) {
                    if (dy < 0 && isVisible.value) {
                        // Scrolling DOWN through real content -> Hide bar
                        isVisible.value = false
                    } else if (dy > 0 && !isVisible.value) {
                        // Scrolling UP through real content -> Reveal bar
                        isVisible.value = true
                    }
                }
                return Offset.Zero
            }
        }
    }

    return Pair(isVisible, connection)
}
