package com.quata.feature.chat.presentation.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.sin

// --- AJUSTES DE PARÁMETROS GLOBALES ---
// Menos intensidad general para dejar más espacio oscuro
private const val BackgroundIntensity = 0.85f 
// Reducido drásticamente para que sea suave/fluido en lugar de fibroso
private const val BackgroundThreadiness = 0.45f 
// Aumentado ligeramente para definir mejor los límites contra el fondo negro
private const val BackgroundContrast = 1.85f 
// Aumentado un poco para dar un efecto de brillo difuso y suave
private const val BackgroundBloom = 0.90f 
private const val ProceduralChatBackgroundTag = "ChatBackground"

internal object ProceduralChatBackground {
    fun cachedBitmap(context: Context, conversationName: String, width: Int, height: Int): ImageBitmap? {
        val file = cacheFile(context, conversationName).takeIf { it.exists() }
            ?: legacyCacheFile(context, conversationName).takeIf { it.exists() }
            ?: return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth != width || bounds.outHeight != height) return null

        return BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
    }

    fun generateIfNeeded(context: Context, conversationName: String, width: Int, height: Int): ImageBitmap? {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val cached = cachedBitmap(context, conversationName, safeWidth, safeHeight)
        if (cached != null) return cached

        val bitmap = ProceduralChatBackgroundRenderer.render(
            seedText = conversationName.ifBlank { "quata" },
            width = safeWidth,
            height = safeHeight
        ) ?: return null

        val file = cacheFile(context, conversationName)
        file.parentFile?.mkdirs()
        file.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.WEBP, 82, output)
        }
        return bitmap.asImageBitmap()
    }

    private fun cacheFile(context: Context, conversationName: String): File {
        val hash = fnv1a(conversationName.ifBlank { "quata" })
        return File(File(context.filesDir, "chat_backgrounds"), "chat_bg_$hash.webp")
    }

    private fun legacyCacheFile(context: Context, conversationName: String): File {
        val hash = fnv1a(conversationName.ifBlank { "quata" })
        return File(File(context.filesDir, "chat_backgrounds"), "chat_bg_$hash.png")
    }

    private fun fnv1a(value: String): Long {
        var hash = 0x811c9dc5L
        value.forEach { char ->
            hash = hash xor char.code.toLong()
            hash = (hash + (hash shl 1) + (hash shl 4) + (hash shl 7) + (hash shl 8) + (hash shl 24)) and 0xffffffffL
        }
        return hash
    }
}

private object ProceduralChatBackgroundRenderer {
    private val quadVertices: FloatBuffer = ByteBuffer
        .allocateDirect(8 * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            position(0)
        }

    fun render(seedText: String, width: Int, height: Int): Bitmap? {
        val egl = EglSession.create(width, height) ?: run {
            Log.w(ProceduralChatBackgroundTag, "Could not create EGL session for ${width}x$height")
            return null
        }
        return try {
            val seed = fnv1a(seedText).toFloat()
            val palette = ChatPalette.values[(seed.toLong() and 0xffffffffL).toInt().floorMod(ChatPalette.values.size)]
            val proceduralProgram = createProgram(FullScreenVertexShader, ProceduralFragmentShader)
            val compositeProgram = createProgram(FullScreenVertexShader, CompositeFragmentShader)
            if (proceduralProgram == 0 || compositeProgram == 0) {
                Log.w(ProceduralChatBackgroundTag, "Could not create GL programs")
                return null
            }

            val baseTarget = RenderTarget.create(width, height) ?: run {
                Log.w(ProceduralChatBackgroundTag, "Could not create base render target")
                return null
            }
            val glowTarget = RenderTarget.create(width, height) ?: run {
                Log.w(ProceduralChatBackgroundTag, "Could not create glow render target")
                return null
            }

            renderProceduralLayer(proceduralProgram, baseTarget, width, height, seed, palette, mode = 0)
            renderProceduralLayer(proceduralProgram, glowTarget, width, height, seed, palette, mode = 1)
            renderComposite(compositeProgram, baseTarget.texture, glowTarget.texture, width, height)

            readBitmap(width, height).also {
                baseTarget.release()
                glowTarget.release()
                GLES20.glDeleteProgram(proceduralProgram)
                GLES20.glDeleteProgram(compositeProgram)
            }
        } finally {
            egl.release()
        }
    }

    private fun renderProceduralLayer(
        program: Int,
        target: RenderTarget,
        width: Int,
        height: Int,
        seed: Float,
        palette: ChatPalette,
        mode: Int
    ) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, target.framebuffer)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(program)
        bindQuad(program)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uResolution"), width.toFloat(), height.toFloat())
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uSeed"), seed)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uIntensity"), BackgroundIntensity)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uThreadiness"), BackgroundThreadiness)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uContrast"), BackgroundContrast)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uBloom"), BackgroundBloom)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uMode"), mode)
        GLES20.glUniform3f(GLES20.glGetUniformLocation(program, "uBase"), palette.base[0], palette.base[1], palette.base[2])
        GLES20.glUniform3f(GLES20.glGetUniformLocation(program, "uColorA"), palette.a[0], palette.a[1], palette.a[2])
        GLES20.glUniform3f(GLES20.glGetUniformLocation(program, "uColorB"), palette.b[0], palette.b[1], palette.b[2])
        GLES20.glUniform3f(GLES20.glGetUniformLocation(program, "uColorC"), palette.c[0], palette.c[1], palette.c[2])
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun renderComposite(program: Int, baseTexture: Int, glowTexture: Int, width: Int, height: Int) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(program)
        bindQuad(program)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uResolution"), width.toFloat(), height.toFloat())
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uBloom"), BackgroundBloom)
        bindTexture(program, "uBaseTexture", baseTexture, 0)
        bindTexture(program, "uGlowTexture", glowTexture, 1)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun bindTexture(program: Int, name: String, texture: Int, unit: Int) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, name), unit)
    }

    private fun bindQuad(program: Int) {
        val position = GLES20.glGetAttribLocation(program, "aPosition")
        quadVertices.position(0)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, quadVertices)
    }

    private fun readBitmap(width: Int, height: Int): Bitmap {
        val buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
        val pixels = IntArray(width * height)
        buffer.position(0)
        for (y in 0 until height) {
            val targetY = height - 1 - y
            for (x in 0 until width) {
                val r = buffer.get().toInt() and 0xff
                val g = buffer.get().toInt() and 0xff
                val b = buffer.get().toInt() and 0xff
                val a = buffer.get().toInt() and 0xff
                pixels[targetY * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (vertexShader == 0 || fragmentShader == 0) return 0

        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return if (linkStatus[0] == GLES20.GL_TRUE) {
            program
        } else {
            Log.w(ProceduralChatBackgroundTag, "Program link failed: ${GLES20.glGetProgramInfoLog(program)}")
            GLES20.glDeleteProgram(program)
            0
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        return if (compileStatus[0] == GLES20.GL_TRUE) {
            shader
        } else {
            Log.w(ProceduralChatBackgroundTag, "Shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            0
        }
    }

    private fun fnv1a(value: String): Long {
        var hash = 0x811c9dc5L
        value.forEach { char ->
            hash = hash xor char.code.toLong()
            hash = (hash + (hash shl 1) + (hash shl 4) + (hash shl 7) + (hash shl 8) + (hash shl 24)) and 0xffffffffL
        }
        return hash
    }

    private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor
}

private class EglSession private constructor(
    private val display: EGLDisplay,
    private val surface: EGLSurface,
    private val context: EGLContext
) {
    fun release() {
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, surface)
        EGL14.eglDestroyContext(display, context)
        EGL14.eglTerminate(display)
    }

    companion object {
        fun create(width: Int, height: Int): EglSession? {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return null

            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null

            val attributes = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val configCount = IntArray(1)
            if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, configCount, 0)) return null
            val config = configs[0] ?: return null

            val contextAttributes = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            val context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttributes, 0)
            if (context == EGL14.EGL_NO_CONTEXT) return null

            val surfaceAttributes = intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE)
            val surface = EGL14.eglCreatePbufferSurface(display, config, surfaceAttributes, 0)
            if (surface == EGL14.EGL_NO_SURFACE) return null

            return if (EGL14.eglMakeCurrent(display, surface, surface, context)) {
                EglSession(display, surface, context)
            } else {
                null
            }
        }
    }
}

private data class RenderTarget(val texture: Int, val framebuffer: Int) {
    fun release() {
        GLES20.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
        GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
    }

    companion object {
        fun create(width: Int, height: Int): RenderTarget? {
            val textures = IntArray(1)
            val framebuffers = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                width,
                height,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                null
            )

            GLES20.glGenFramebuffers(1, framebuffers, 0)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffers[0])
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D,
                textures[0],
                0
            )

            return if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE) {
                RenderTarget(textures[0], framebuffers[0])
            } else {
                null
            }
        }
    }
}

// --- AJUSTE DE PALETAS ---
// Reemplazadas para dominar tonos fríos (Azules, Púrpuras, Cianes) y dorados suaves. 
// El naranja ahora actúa como un acento minoritario. Fondos base ultra oscuros.
private data class ChatPalette(
    val base: FloatArray,
    val a: FloatArray,
    val b: FloatArray,
    val c: FloatArray
) {
    companion object {
        val values = listOf(
            palette("#030408", "#2f8cff", "#7c3cff", "#ff8a1f"), // Azul/Púrpura dominante con acento cálido
            palette("#020508", "#00b3ff", "#18c6a3", "#ffb12b"), // Cian/Esmeralda con acento dorado
            palette("#040308", "#815bff", "#b84cff", "#ff4c1a"), // Púrpuras profundos
            palette("#030507", "#3a86ff", "#ff006e", "#ffbe0b"), // Vibrante pero balanceado
            palette("#020609", "#00b3ff", "#1aa7ff", "#815bff"), // Totalmente frío/etéreo
            palette("#040506", "#ffb238", "#815bff", "#1aa7ff"), // Oro suave y azul
            palette("#020406", "#18c6a3", "#2f8cff", "#7c3cff"), // Nebulosa oceánica
            palette("#040306", "#b84cff", "#ff315f", "#ff8a1f")  // Tonos magenta/cobre
        )

        private fun palette(base: String, a: String, b: String, c: String): ChatPalette =
            ChatPalette(hex(base), hex(a), hex(b), hex(c))

        private fun hex(value: String): FloatArray {
            val clean = value.removePrefix("#")
            return floatArrayOf(
                clean.substring(0, 2).toInt(16) / 255f,
                clean.substring(2, 4).toInt(16) / 255f,
                clean.substring(4, 6).toInt(16) / 255f
            )
        }
    }
}

private const val FullScreenVertexShader = """
attribute vec2 aPosition;
varying vec2 vUv;

void main() {
    vUv = aPosition * 0.5 + 0.5;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
"""

private const val ProceduralFragmentShader = """
precision highp float;

varying vec2 vUv;
uniform vec2 uResolution;
uniform float uSeed;
uniform float uIntensity;
uniform float uThreadiness;
uniform float uContrast;
uniform float uBloom;
uniform int uMode;
uniform vec3 uBase;
uniform vec3 uColorA;
uniform vec3 uColorB;
uniform vec3 uColorC;

float clamp01(float x) { return clamp(x, 0.0, 1.0); }
float qSmoothStep(float a, float b, float x) {
    float t = clamp01((x - a) / (b - a));
    return t * t * (3.0 - 2.0 * t);
}
float seeded(float seed, float salt) {
    return fract(sin(seed * 0.00013 + salt * 12.9898) * 43758.5453);
}
float rand(vec2 p, float s) {
    return fract(sin(p.x * 127.1 + p.y * 311.7 + s * 0.001) * 43758.5453123);
}
float noise(vec2 p, float s) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = rand(i, s);
    float b = rand(i + vec2(1.0, 0.0), s);
    float c = rand(i + vec2(0.0, 1.0), s);
    float d = rand(i + vec2(1.0, 1.0), s);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}
float fbm(vec2 p, float s) {
    float v = 0.0;
    float a = 0.5;
    float f = 1.0;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p * f, s + float(i) * 917.0);
        f *= 2.03;
        a *= 0.52;
    }
    return v;
}
vec2 domainWarp(vec2 p, float s, float warpStrength) {
    float qx = fbm(p, s + 101.0);
    float qy = fbm(p, s + 5000.0);
    float rx = fbm(p + vec2(qx, qy) * warpStrength, s + 9000.0);
    float ry = fbm(p + vec2(qx, qy) * warpStrength, s + 12000.0);
    return vec2(
        fbm(p + vec2(rx, ry) * warpStrength * 1.9, s + 16000.0),
        fbm(p + vec2(qx, qy) * warpStrength * 1.1, s + 22000.0)
    );
}

void main() {
    vec2 uv = vUv;
    vec2 pixel = uv * uResolution;
    float aspect = uResolution.x / uResolution.y;
    vec2 p = vec2((uv.x - 0.5) * aspect, uv.y - 0.5);

    float angle = seeded(uSeed, 7.0) * 6.2831853;
    float cs = cos(angle);
    float sn = sin(angle);
    vec2 rp = vec2(p.x * cs - p.y * sn, p.x * sn + p.y * cs);

    // Escala reducida para formas más grandes y líquidas
    float scale = 2.0 + seeded(uSeed, 5.0) * 1.8; 
    float warpStrength = 2.0 + seeded(uSeed, 6.0) * 1.8;
    vec2 dw = domainWarp(rp * scale, uSeed, warpStrength);
    float main = dw.x;
    float aux = dw.y;

    // Umbrales aumentados para generar más áreas oscuras (menos "lleno")
    float mist = qSmoothStep(0.40, 0.90, main); 
    
    // Suavizado de hilos (transiciones más amplias)
    float bands = abs(main - 0.5) * 2.0;
    float threads = 1.0 - qSmoothStep(0.12, 0.45, bands);
    threads = pow(threads, 1.18 / uContrast);

    float bands2 = abs(aux - 0.52) * 2.0;
    float thin = 1.0 - qSmoothStep(0.06, 0.28, bands2);
    thin = pow(thin, 1.35 / uContrast);

    float cut = pow(qSmoothStep(0.46, 0.88, abs(aux - 0.5) * 2.0), 1.4);
    vec2 glow1Point = vec2(seeded(uSeed, 1.0), seeded(uSeed, 2.0));
    vec2 glow2Point = vec2(seeded(uSeed, 3.0), seeded(uSeed, 4.0));
    vec2 glow3Point = vec2(seeded(uSeed, 11.0), seeded(uSeed, 12.0));
    float glow1 = exp(-distance(uv, glow1Point) * (4.8 + seeded(uSeed, 8.0) * 3.0));
    float glow2 = exp(-distance(uv, glow2Point) * (7.0 + seeded(uSeed, 9.0) * 4.0));
    float glow3 = exp(-distance(uv, glow3Point) * (9.5 + seeded(uSeed, 10.0) * 5.0));

    // Menor peso general a la estructura para que predomine el vacío
    float structure = mist * 0.22
        + threads * 0.45 * uThreadiness
        + thin * 0.25 * uThreadiness
        + glow1 * 0.45
        + glow2 * 0.25
        + glow3 * 0.15;
    structure *= mix(0.72, 1.10, cut);
    float energy = structure * (0.35 + uIntensity * 0.45);

    float t1 = clamp01(main * 0.88 + glow1 * 0.48 + thin * 0.12);
    float t2 = clamp01(aux * 0.82 + glow2 * 0.58 + threads * 0.10);
    vec3 dye = mix(uColorA, uColorB, t1);
    dye = mix(dye, uColorC, t2 * 0.25);
    dye = mix(dye, uColorA, 0.30);

    float grain = (rand(pixel, uSeed) - 0.5) * 2.0 / 255.0;
    
    // Eliminado el sesgo multiplicador que forzaba tonos rojizos/naranjas. Ahora respeta los colores puros.
    vec3 baseColor = clamp(uBase + dye * energy * 0.38 + grain * vec3(1.2), 0.0, 1.0);

    float luminous = clamp01((energy - 0.46) * 1.9);
    float filamentBoost = clamp01((threads + thin * 0.8 - 0.55) * 1.7);
    float glowPower = clamp01((luminous * 0.72 + filamentBoost * 0.45 + glow1 * 0.18 + glow2 * 0.10) * uBloom);
    
    // El resplandor ya no fuerza un naranja brillante puro, sino un tono más neutro/etéreo
    vec3 softGlow = mix(dye, vec3(0.9, 0.95, 1.0), 0.25);
    vec3 glowColor = clamp(softGlow * glowPower * 1.25, 0.0, 1.0);

    if (uMode == 0) {
        gl_FragColor = vec4(baseColor, 1.0);
    } else {
        gl_FragColor = vec4(glowColor, glowPower);
    }
}
"""

private const val CompositeFragmentShader = """
precision mediump float;

varying vec2 vUv;
uniform vec2 uResolution;
uniform float uBloom;
uniform sampler2D uBaseTexture;
uniform sampler2D uGlowTexture;

vec3 screen(vec3 base, vec3 blend) {
    return 1.0 - (1.0 - base) * (1.0 - blend);
}

vec3 blurGlow(float radius) {
    vec2 t = vec2(radius) / uResolution;
    vec3 c = texture2D(uGlowTexture, vUv).rgb * 0.20;
    c += texture2D(uGlowTexture, vUv + vec2(t.x, 0.0)).rgb * 0.12;
    c += texture2D(uGlowTexture, vUv - vec2(t.x, 0.0)).rgb * 0.12;
    c += texture2D(uGlowTexture, vUv + vec2(0.0, t.y)).rgb * 0.12;
    c += texture2D(uGlowTexture, vUv - vec2(0.0, t.y)).rgb * 0.12;
    c += texture2D(uGlowTexture, vUv + vec2(t.x, t.y)).rgb * 0.08;
    c += texture2D(uGlowTexture, vUv + vec2(-t.x, t.y)).rgb * 0.08;
    c += texture2D(uGlowTexture, vUv + vec2(t.x, -t.y)).rgb * 0.08;
    c += texture2D(uGlowTexture, vUv + vec2(-t.x, -t.y)).rgb * 0.08;
    return c;
}

void main() {
    vec3 base = texture2D(uBaseTexture, vUv).rgb;
    vec3 glow = texture2D(uGlowTexture, vUv).rgb * (0.08 + uBloom * 0.12);
    vec3 small = blurGlow(12.0) * (0.42 + uBloom * 0.28);
    vec3 medium = blurGlow(32.0) * (0.26 + uBloom * 0.34);
    vec3 large = blurGlow(80.0) * (0.10 + uBloom * 0.28); // Blur más grande para mayor suavidad
    vec3 color = screen(base, glow + small + medium + large);
    gl_FragColor = vec4(color, 1.0);
}
"""
