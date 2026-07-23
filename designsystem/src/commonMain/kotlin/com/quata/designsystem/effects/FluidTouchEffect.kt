package com.quata.designsystem.effects

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.TimeSource

private const val maxParticles = 112
private const val idleFrameDelayMillis = 48L
private const val defaultParticleLifetimeMillis = 540L
private const val targetFrameMillis = 16L
private const val safeFrameMillis = 20L
private const val badFrameMillis = 28L
private const val minParticleBudget = 0.30f

private val fluidPalette = listOf(
    Color(0xFFFF6B00), Color(0xFFFF7A14), Color(0xFFEA580C), Color(0xFFC2410C),
    Color(0xFFFF3D17), Color(0xFFE0303B), Color(0xFFD93BD2),
)

/** Shared Qüata Touch Flow effect. It needs only Compose pointer events and a common monotonic clock. */
fun Modifier.fluidTouchEffect(enabled: Boolean = true): Modifier = composed {
    if (!enabled) return@composed this

    val particles = remember { mutableStateListOf<FluidTouchParticle>() }
    val clockStart = remember { TimeSource.Monotonic.markNow() }
    val random = remember { Random(clockStart.elapsedNow().inWholeNanoseconds) }
    fun nowMillis(): Long = clockStart.elapsedNow().inWholeMilliseconds

    var nextParticleId by remember { mutableIntStateOf(0) }
    var frameTimeMillis by remember { mutableLongStateOf(nowMillis()) }
    var lastFrameMillis by remember { mutableLongStateOf(nowMillis()) }
    var activeTouch by remember { mutableStateOf<ActiveFluidTouch?>(null) }
    var particleBudget by remember { mutableFloatStateOf(1f) }

    fun emit(position: Offset, movement: Offset, now: Long, amount: Int) {
        if (!position.x.isFinite() || !position.y.isFinite() || amount <= 0) return
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
            val velocity = drift + rise + sideways + lateral
            particles += FluidTouchParticle(
                id = nextParticleId++,
                origin = position - direction * (8f + random.nextFloat() * 24f) + perpendicular * ((random.nextFloat() - 0.5f) * 28f),
                velocity = velocity,
                color = fluidPalette[random.nextInt(fluidPalette.size)],
                radius = 60f + random.nextFloat() * 80f + movementLength.coerceAtMost(100f) * 0.3f,
                alpha = 0.3f + random.nextFloat() * 0.3f,
                birthMillis = now,
                lifetimeMillis = defaultParticleLifetimeMillis + random.nextLong(240L),
                wobble = random.nextFloat() * PI.toFloat() * 2f,
                angle = atan2(velocity.y, velocity.x),
            )
        }
        val adaptiveMax = (maxParticles * particleBudget).toInt().coerceAtLeast(32)
        while (particles.size > adaptiveMax) particles.removeAt(0)
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (particles.isEmpty()) {
                delay(idleFrameDelayMillis)
            } else {
                withFrameMillis { now ->
                    val delta = now - lastFrameMillis
                    lastFrameMillis = now
                    particleBudget = when {
                        delta > badFrameMillis -> (particleBudget * 0.75f).coerceAtLeast(minParticleBudget)
                        delta > safeFrameMillis -> (particleBudget * 0.88f).coerceAtLeast(minParticleBudget)
                        delta < targetFrameMillis -> (particleBudget + 0.04f).coerceAtMost(1f)
                        else -> particleBudget
                    }
                    activeTouch?.let { touch ->
                        val amount = (2 * particleBudget).toInt()
                        if (amount > 0) emit(touch.position, touch.movement, now, amount)
                    }
                    particles.removeAll { now - it.birthMillis > it.lifetimeMillis }
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
                    val changes = event.changes.filter { it.pressed }
                    if (changes.isEmpty()) {
                        lastPosition = null
                        activeTouch = null
                        continue
                    }
                    changes.forEach { change ->
                        val movement = change.position - (lastPosition ?: change.previousPosition)
                        val baseAmount = if (movement.getDistance() > 12f) 7 else 4
                        emit(change.position, movement, nowMillis(), (baseAmount * particleBudget).toInt().coerceAtLeast(2))
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

private data class ActiveFluidTouch(val position: Offset, val movement: Offset)

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
    val angle: Float,
)

private fun ContentDrawScope.drawFluidTouchParticles(particles: List<FluidTouchParticle>, now: Long) {
    particles.forEach { particle ->
        val progress = ((now - particle.birthMillis).toFloat() / particle.lifetimeMillis).coerceIn(0f, 1f)
        val fade = 1f - progress
        val eased = 1f - fade * fade
        val center = particle.origin + particle.velocity * eased + Offset(
            sin(particle.wobble + progress * 7.2f) * 34f * progress,
            cos(particle.wobble + progress * 5.4f) * 16f * progress,
        )
        val radius = particle.radius * (1f + progress * 0.5f)
        val alpha = particle.alpha * fade
        if (alpha <= 0.01f || radius <= 0f) return@forEach
        drawCircle(
            brush = Brush.radialGradient(
                0f to particle.color.copy(alpha = alpha * 0.4f),
                0.2f to particle.color.copy(alpha = alpha * 0.3f),
                0.5f to particle.color.copy(alpha = alpha * 0.15f),
                0.8f to particle.color.copy(alpha = alpha * 0.05f),
                1f to Color.Transparent,
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
            blendMode = BlendMode.Plus,
        )
        if (fade > 0.2f) drawCircle(
            brush = Brush.radialGradient(
                0f to Color.White.copy(alpha = alpha * 0.95f * (fade - 0.2f) / 0.8f),
                0.3f to particle.color.copy(alpha = alpha * 0.6f * (fade - 0.2f) / 0.8f),
                0.7f to particle.color.copy(alpha = alpha * 0.2f * (fade - 0.2f) / 0.8f),
                1f to Color.Transparent,
                center = center,
                radius = radius * 0.25f,
            ),
            radius = radius * 0.25f,
            center = center,
            blendMode = BlendMode.Plus,
        )
        if (fade > 0.4f) drawCircle(
            brush = Brush.radialGradient(
                0f to Color.White.copy(alpha = alpha * (fade - 0.4f) / 0.6f),
                0.5f to Color.White.copy(alpha = alpha * 0.5f * (fade - 0.4f) / 0.6f),
                1f to Color.Transparent,
                center = center,
                radius = radius * 0.08f,
            ),
            radius = radius * 0.08f,
            center = center,
            blendMode = BlendMode.Plus,
        )
    }
}
