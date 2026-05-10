package com.quata.core.ui.effects

import android.os.SystemClock
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val MaxParticles = 112
private const val IdleFrameDelayMillis = 48L
private const val DefaultParticleLifetimeMillis = 540L

private val fluidPalette = listOf(
    Color(0xFFFF6B00),
    Color(0xFFFF7A14),
    Color(0xFFEA580C),
    Color(0xFFC2410C),
    Color(0xFFFF3D17),
    Color(0xFFE0303B),
    Color(0xFFD93BD2)
)

fun Modifier.fluidTouchEffect(enabled: Boolean = true): Modifier = composed {
    if (!enabled) {
        return@composed this
    }

    val particles = remember { mutableStateListOf<FluidTouchParticle>() }
    val random = remember { Random(SystemClock.elapsedRealtimeNanos()) }
    var nextParticleId by remember { mutableIntStateOf(0) }
    var frameTimeMillis by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    var activeTouch by remember { mutableStateOf<ActiveFluidTouch?>(null) }

    fun emit(position: Offset, movement: Offset, nowMillis: Long, amount: Int) {
        if (!position.x.isFinite() || !position.y.isFinite()) return

        val movementLength = movement.getDistance()
        val direction = if (movementLength > 0.5f) {
            Offset(movement.x / movementLength, movement.y / movementLength)
        } else {
            val angle = random.nextFloat() * (PI.toFloat() * 2f)
            Offset(cos(angle), sin(angle))
        }
        val oppositePull = Offset(-direction.x, -direction.y)
        val perpendicular = Offset(-direction.y, direction.x)

        repeat(amount) {
            val angle = random.nextFloat() * (PI.toFloat() * 2f)
            val spread = Offset(cos(angle), sin(angle))
            val lateral = perpendicular * ((random.nextFloat() - 0.5f) * 58f)
            val drift = oppositePull * (28f + movementLength.coerceAtMost(120f) * 0.34f)
            val rise = Offset(0f, -(24f + random.nextFloat() * 42f))
            val sideways = spread * (12f + random.nextFloat() * 22f)
            val radius = 44f + random.nextFloat() * 62f + movementLength.coerceAtMost(100f) * 0.28f
            val alpha = 0.075f + random.nextFloat() * 0.14f
            val velocity = drift + rise + sideways + lateral
            val tailOffset = direction * (8f + random.nextFloat() * 24f)
            val origin = position - tailOffset + perpendicular * ((random.nextFloat() - 0.5f) * 28f)

            particles += FluidTouchParticle(
                id = nextParticleId++,
                origin = origin,
                velocity = velocity,
                color = fluidPalette[random.nextInt(fluidPalette.size)],
                radius = radius,
                alpha = alpha,
                birthMillis = nowMillis,
                lifetimeMillis = DefaultParticleLifetimeMillis + random.nextLong(240L),
                wobble = random.nextFloat() * PI.toFloat() * 2f,
                angle = atan2(velocity.y, velocity.x)
            )
        }

        while (particles.size > MaxParticles) {
            particles.removeAt(0)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (particles.isEmpty()) {
                delay(IdleFrameDelayMillis)
            } else {
                withFrameMillis { now ->
                    activeTouch?.let { touch ->
                        emit(touch.position, touch.movement, now, amount = 2)
                    }
                    particles.removeAll { particle ->
                        now - particle.birthMillis > particle.lifetimeMillis
                    }
                    frameTimeMillis = now
                }
            }
        }
    }

    this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                var lastPosition: Offset? = null
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val nowMillis = SystemClock.uptimeMillis()
                    val pressedChanges = event.changes.filter { it.pressed }

                    if (pressedChanges.isEmpty()) {
                        lastPosition = null
                        activeTouch = null
                        continue
                    }

                    pressedChanges.forEach { change ->
                        val previous = lastPosition ?: change.previousPosition
                        val movement = change.position - previous
                        val movementAmount = movement.getDistance()
                        val amount = if (movementAmount > 12f) 7 else 4
                        emit(change.position, movement, nowMillis, amount)
                        activeTouch = ActiveFluidTouch(change.position, movement)
                        lastPosition = change.position
                    }
                }
            }
        }
        .drawWithContent {
            drawContent()
            drawFluidTouchParticles(particles, frameTimeMillis)
        }
}

private data class ActiveFluidTouch(
    val position: Offset,
    val movement: Offset
)

private data class FluidTouchParticle(
    val id: Int,
    val origin: Offset,
    val velocity: Offset,
    val color: Color,
    val radius: Float,
    val alpha: Float,
    val birthMillis: Long,
    val lifetimeMillis: Long,
    val wobble: Float,
    val angle: Float
)

private fun ContentDrawScope.drawFluidTouchParticles(
    particles: List<FluidTouchParticle>,
    nowMillis: Long
) {
    particles.forEach { particle ->
        val rawProgress = ((nowMillis - particle.birthMillis).toFloat() / particle.lifetimeMillis)
            .coerceIn(0f, 1f)
        val fade = (1f - rawProgress)
        val eased = 1f - fade * fade
        val wobbleX = sin(particle.wobble + rawProgress * 7.2f) * 34f * rawProgress
        val wobbleY = cos(particle.wobble + rawProgress * 5.4f) * 16f * rawProgress
        val center = particle.origin + particle.velocity * eased + Offset(wobbleX, wobbleY)
        val radius = particle.radius * (0.72f + rawProgress * 1.45f)
        val alpha = particle.alpha * fade * fade

        if (alpha <= 0.01f || radius <= 0f) return@forEach

        val flow = Offset(cos(particle.angle), sin(particle.angle))
        val cross = Offset(-flow.y, flow.x)
        val tailStart = particle.origin - flow * radius * 0.7f
        val tailControl = (particle.origin + center) * 0.5f +
            cross * sin(particle.wobble + rawProgress * 4.6f) * radius * 0.18f

        drawLine(
            color = Color(0xFFFF6B00).copy(alpha = alpha * 0.13f),
            start = tailStart,
            end = tailControl,
            strokeWidth = radius * 0.42f,
            cap = StrokeCap.Round
        )

        drawLine(
            color = Color(0xFFD93BD2).copy(alpha = alpha * 0.07f),
            start = tailControl,
            end = center,
            strokeWidth = radius * 0.48f,
            cap = StrokeCap.Round
        )

        repeat(5) { index ->
            val t = index / 4f
            val curve = sin(particle.wobble + rawProgress * 5.2f + t * PI.toFloat())
            val lobeCenter = particle.origin * (1f - t) + center * t +
                cross * curve * radius * (0.15f + 0.06f * t)
            val lobeRadius = radius * (1.18f - t * 0.22f)
            val lobeAlpha = alpha * (0.34f - t * 0.045f)
            drawFluidLobe(
                center = lobeCenter,
                radius = lobeRadius,
                color = particle.color,
                alpha = lobeAlpha,
                magentaMix = 0.08f + t * 0.12f
            )
        }

        drawFluidLobe(
            center = particle.origin,
            radius = radius * 0.52f,
            color = Color(0xFFFF7A14),
            alpha = alpha * 0.30f,
            magentaMix = 0.02f
        )

        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color(0xFFFFB238).copy(alpha = alpha * 0.08f),
                    0.34f to Color(0xFFFF6B00).copy(alpha = alpha * 0.07f),
                    1f to Color.Transparent
                ),
                center = particle.origin,
                radius = radius * 0.36f
            ),
            radius = radius * 0.36f,
            center = particle.origin
        )
    }
}

private fun ContentDrawScope.drawFluidLobe(
    center: Offset,
    radius: Float,
    color: Color,
    alpha: Float,
    magentaMix: Float
) {
    if (alpha <= 0f || radius <= 0f) return

    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color(0xFFFFC266).copy(alpha = alpha * 0.025f),
                0.16f to color.copy(alpha = alpha * 0.58f),
                0.52f to Color(0xFFB93816).copy(alpha = alpha * 0.24f),
                0.82f to Color(0xFFD93BD2).copy(alpha = alpha * magentaMix * 0.72f),
                1f to Color.Transparent
            ),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}
