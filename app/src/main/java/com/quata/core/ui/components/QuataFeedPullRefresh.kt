package com.quata.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection as ComposeNestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource as ComposeNestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.quata.R
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.quataTheme

@Stable
class QuataFeedPullRefreshState internal constructor(
    internal val triggerDistancePx: Float,
    private val maxDistancePx: Float,
    private val isEnabled: () -> Boolean,
    private val isRefreshing: () -> Boolean,
    private val onRefresh: () -> Unit
) {
    var pullDistancePx by mutableFloatStateOf(0f)
        internal set

    val nestedScrollConnection: ComposeNestedScrollConnection = object : ComposeNestedScrollConnection {
        override fun onPreScroll(available: Offset, source: ComposeNestedScrollSource): Offset {
            val deltaY = available.y
            if (deltaY > 0f && isEnabled()) {
                val resistance = 1f - (pullDistancePx / maxDistancePx).coerceIn(0f, 0.72f)
                pullDistancePx = (pullDistancePx + deltaY * resistance).coerceAtMost(maxDistancePx)
                return Offset(0f, deltaY)
            }
            if (deltaY < 0f && pullDistancePx > 0f) {
                val consumed = minOf(pullDistancePx, -deltaY)
                pullDistancePx -= consumed
                return Offset(0f, -consumed)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (pullDistancePx >= triggerDistancePx && !isRefreshing()) {
                pullDistancePx = triggerDistancePx
                onRefresh()
            } else if (!isRefreshing()) {
                pullDistancePx = 0f
            }
            return Velocity.Zero
        }
    }
}

@Composable
fun rememberQuataFeedPullRefreshState(
    enabled: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
): QuataFeedPullRefreshState {
    val density = LocalDensity.current
    val triggerDistancePx = with(density) { QuataFeedPullRefreshTriggerDistance.toPx() }
    val maxDistancePx = with(density) { QuataFeedPullRefreshMaxDistance.toPx() }
    val enabledState = rememberUpdatedState(enabled)
    val refreshingState = rememberUpdatedState(isRefreshing)
    val refreshState = rememberUpdatedState(onRefresh)
    val state = remember(triggerDistancePx, maxDistancePx) {
        QuataFeedPullRefreshState(
            triggerDistancePx = triggerDistancePx,
            maxDistancePx = maxDistancePx,
            isEnabled = { enabledState.value },
            isRefreshing = { refreshingState.value },
            onRefresh = { refreshState.value() }
        )
    }
    LaunchedEffect(isRefreshing, triggerDistancePx) {
        if (isRefreshing && state.pullDistancePx < triggerDistancePx) {
            state.pullDistancePx = triggerDistancePx
        } else if (!isRefreshing) {
            state.pullDistancePx = 0f
        }
    }
    return state
}

@Composable
fun QuataFeedPullRefreshIndicator(
    state: QuataFeedPullRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val progress = if (isRefreshing) {
        1f
    } else {
        (state.pullDistancePx / state.triggerDistancePx).coerceIn(0f, 1f)
    }
    if (progress <= 0f && !isRefreshing) return

    val template = quataTheme()
    val density = LocalDensity.current
    val indicatorOffset = with(density) {
        (-QuataFeedPullRefreshIndicatorSize.toPx() + QuataFeedPullRefreshIndicatorTravel.toPx() * progress).toDp()
    }
    val indicatorScale = 0.74f + 0.26f * progress

    Surface(
        modifier = modifier
            .offset(y = indicatorOffset)
            .graphicsLayer {
                alpha = if (isRefreshing) 1f else (0.28f + progress * 0.72f)
                rotationZ = if (isRefreshing) 0f else progress * 180f
                scaleX = indicatorScale
                scaleY = indicatorScale
            }
            .size(QuataFeedPullRefreshIndicatorSize),
        shape = androidx.compose.foundation.shape.CircleShape,
        color = template.colors.surfaceRaised.copy(alpha = 0.96f),
        contentColor = template.colors.textPrimary,
        shadowElevation = 6.dp
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = QuataOrange,
                    strokeWidth = 2.5.dp
                )
            } else {
                CompactIcon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.common_refresh),
                    tint = if (progress >= 1f) QuataOrange else template.colors.textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

private val QuataFeedPullRefreshTriggerDistance = 96.dp
private val QuataFeedPullRefreshMaxDistance = 148.dp
private val QuataFeedPullRefreshIndicatorSize = 44.dp
private val QuataFeedPullRefreshIndicatorTravel = 72.dp
