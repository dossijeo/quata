package com.quata.core.ui.effects

import android.os.SystemClock
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

private const val MaxParticles = 112
private const val IdleFrameDelayMillis = 48L
private const val DefaultParticleLifetimeMillis = 540L

// CONTROL ADAPTATIVO
private const val TargetFrameMillis = 16L // 60 FPS
private const val SafeFrameMillis = 20L
private const val BadFrameMillis = 28L
private const val MinParticleBudget = 0.30f

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

    // SISTEMA ADAPTATIVO
    var particleBudget by remember { mutableFloatStateOf(1f) }
    var lastFrameMillis by remember {
        mutableLongStateOf(SystemClock.uptimeMillis())
    }

    fun emit(
        position: Offset,
        movement: Offset,
        nowMillis: Long,
        amount: Int
    ) {
        if (!position.x.isFinite() || !position.y.isFinite()) return
        if (amount <= 0) return

        val movementLength = movement.getDistance()

        val direction = if (movementLength > 0.5f) {
            Offset(
                movement.x / movementLength,
                movement.y / movementLength
            )
        } else {
            val angle = random.nextFloat() * (PI.toFloat() * 2f)
            Offset(cos(angle), sin(angle))
        }

        val oppositePull = Offset(-direction.x, -direction.y)
        val perpendicular = Offset(-direction.y, direction.x)

        repeat(amount) {
            val angle = random.nextFloat() * (PI.toFloat() * 2f)
            val spread = Offset(cos(angle), sin(angle))

            val lateral = perpendicular *
                    ((random.nextFloat() - 0.5f) * 58f)

            val drift = oppositePull *
                    (28f + movementLength.coerceAtMost(120f) * 0.34f)

            val rise = Offset(
                0f,
                -(24f + random.nextFloat() * 42f)
            )

            val sideways = spread *
                    (12f + random.nextFloat() * 22f)

            val radius =
                60f +
                        random.nextFloat() * 80f +
                        movementLength.coerceAtMost(100f) * 0.3f

            val alpha =
                0.3f + random.nextFloat() * 0.3f

            val velocity =
                drift + rise + sideways + lateral

            val tailOffset =
                direction * (8f + random.nextFloat() * 24f)

            val origin =
                position -
                        tailOffset +
                        perpendicular *
                        ((random.nextFloat() - 0.5f) * 28f)

            particles += FluidTouchParticle(
                id = nextParticleId++,
                origin = origin,
                velocity = velocity,
                color = fluidPalette[random.nextInt(fluidPalette.size)],
                radius = radius,
                alpha = alpha,
                birthMillis = nowMillis,
                lifetimeMillis = DefaultParticleLifetimeMillis +
                        random.nextLong(240L),
                wobble = random.nextFloat() *
                        PI.toFloat() * 2f,
                angle = atan2(velocity.y, velocity.x)
            )
        }

        // LIMITE DINÁMICO
        val adaptiveMaxParticles =
            (MaxParticles * particleBudget)
                .toInt()
                .coerceAtLeast(32)

        while (particles.size > adaptiveMaxParticles) {
            particles.removeAt(0)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (particles.isEmpty()) {
                delay(IdleFrameDelayMillis)
            } else {
                withFrameMillis { now ->

                    // MEDICIÓN DE FRAME TIME
                    val delta = now - lastFrameMillis
                    lastFrameMillis = now

                    // AJUSTE ADAPTATIVO
                    particleBudget = when {
                        delta > BadFrameMillis -> {
                            (particleBudget * 0.75f)
                                .coerceAtLeast(MinParticleBudget)
                        }

                        delta > SafeFrameMillis -> {
                            (particleBudget * 0.88f)
                                .coerceAtLeast(MinParticleBudget)
                        }

                        delta < TargetFrameMillis -> {
                            (particleBudget + 0.04f)
                                .coerceAtMost(1f)
                        }

                        else -> particleBudget
                    }

                    // EMISIÓN AUTOMÁTICA
                    activeTouch?.let { touch ->
                        val idleAmount =
                            (2 * particleBudget).toInt()

                        if (idleAmount > 0) {
                            emit(
                                touch.position,
                                touch.movement,
                                now,
                                idleAmount
                            )
                        }
                    }

                    particles.removeAll { particle ->
                        now - particle.birthMillis >
                                particle.lifetimeMillis
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

                    val event =
                        awaitPointerEvent(PointerEventPass.Final)

                    val nowMillis =
                        SystemClock.uptimeMillis()

                    val pressedChanges =
                        event.changes.filter { it.pressed }

                    if (pressedChanges.isEmpty()) {
                        lastPosition = null
                        activeTouch = null
                        continue
                    }

                    pressedChanges.forEach { change ->

                        val previous =
                            lastPosition ?: change.previousPosition

                        val movement =
                            change.position - previous

                        val movementAmount =
                            movement.getDistance()

                        val baseAmount =
                            if (movementAmount > 12f) 7 else 4

                        // ADAPTATIVO
                        val adaptiveAmount =
                            (baseAmount * particleBudget)
                                .toInt()
                                .coerceAtLeast(2)

                        emit(
                            change.position,
                            movement,
                            nowMillis,
                            adaptiveAmount
                        )

                        activeTouch = ActiveFluidTouch(
                            change.position,
                            movement
                        )

                        lastPosition = change.position
                    }
                }
            }
        }
        .drawWithContent {
            drawContent()
            drawFluidTouchParticles(
                particles,
                frameTimeMillis
            )
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

        val rawProgress =
            (
                    (nowMillis - particle.birthMillis).toFloat() /
                            particle.lifetimeMillis
                    )
                .coerceIn(0f, 1f)

        val fade = (1f - rawProgress)
        val eased = 1f - fade * fade

        val wobbleX =
            sin(particle.wobble + rawProgress * 7.2f) *
                    34f * rawProgress

        val wobbleY =
            cos(particle.wobble + rawProgress * 5.4f) *
                    16f * rawProgress

        val center =
            particle.origin +
                    particle.velocity * eased +
                    Offset(wobbleX, wobbleY)

        val currentRadius =
            particle.radius *
                    (1.0f + rawProgress * 0.5f)

        val currentAlpha =
            particle.alpha * fade

        if (
            currentAlpha <= 0.01f ||
            currentRadius <= 0f
        ) {
            return@forEach
        }

        // HALO EXTERIOR
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to particle.color.copy(
                        alpha = currentAlpha * 0.4f
                    ),
                    0.2f to particle.color.copy(
                        alpha = currentAlpha * 0.3f
                    ),
                    0.5f to particle.color.copy(
                        alpha = currentAlpha * 0.15f
                    ),
                    0.8f to particle.color.copy(
                        alpha = currentAlpha * 0.05f
                    ),
                    1.0f to Color.Transparent
                ),
                center = center,
                radius = currentRadius
            ),
            radius = currentRadius,
            center = center,
            blendMode = BlendMode.Plus
        )

        // NÚCLEO
        if (fade > 0.2f) {

            val coreRadius =
                currentRadius * 0.25f

            val coreAlpha =
                currentAlpha *
                        (fade - 0.2f) / 0.8f

            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to Color.White.copy(
                            alpha = coreAlpha * 0.95f
                        ),
                        0.3f to particle.color.copy(
                            alpha = coreAlpha * 0.6f
                        ),
                        0.7f to particle.color.copy(
                            alpha = coreAlpha * 0.2f
                        ),
                        1.0f to Color.Transparent
                    ),
                    center = center,
                    radius = coreRadius
                ),
                radius = coreRadius,
                center = center,
                blendMode = BlendMode.Plus
            )
        }

        // BRILLO CENTRAL
        if (fade > 0.4f) {

            val intenseRadius =
                currentRadius * 0.08f

            val intenseAlpha =
                currentAlpha *
                        (fade - 0.4f) / 0.6f

            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to Color.White.copy(
                            alpha = intenseAlpha
                        ),
                        0.5f to Color.White.copy(
                            alpha = intenseAlpha * 0.5f
                        ),
                        1.0f to Color.Transparent
                    ),
                    center = center,
                    radius = intenseRadius
                ),
                radius = intenseRadius,
                center = center,
                blendMode = BlendMode.Plus
            )
        }
    }
}