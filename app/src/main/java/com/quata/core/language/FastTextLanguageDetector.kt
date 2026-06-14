package com.quata.core.language

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import kotlin.math.exp

enum class QuataDetectedLanguage(val code: String) {
    Spanish("es"),
    French("fr"),
    English("en"),
    Fang("fan"),
    Unknown("und");

    companion object {
        fun fromCode(code: String): QuataDetectedLanguage = when (code.lowercase()) {
            "es" -> Spanish
            "fr" -> French
            "en" -> English
            "fan" -> Fang
            else -> Unknown
        }
    }
}

data class QuataLanguageScore(
    val language: QuataDetectedLanguage,
    val code: String,
    val confidence: Float
)

data class QuataLanguageDetection(
    val language: QuataDetectedLanguage,
    val code: String,
    val confidence: Float,
    val scores: List<QuataLanguageScore>
)

class FastTextLanguageDetector private constructor(
    private val args: FastTextArgs,
    private val dictionary: FastTextDictionary,
    private val input: FastTextMatrix,
    private val output: FastTextMatrix
) {
    fun detect(text: String): QuataLanguageDetection {
        val scores = rank(text)
        val top = scores.firstOrNull()
            ?: QuataLanguageScore(QuataDetectedLanguage.Unknown, QuataDetectedLanguage.Unknown.code, 0f)
        return QuataLanguageDetection(
            language = top.language,
            code = top.code,
            confidence = top.confidence,
            scores = scores
        )
    }

    fun detectCode(text: String): String = detect(text).code

    fun rank(text: String): List<QuataLanguageScore> {
        val line = text.toFastTextLine()
        if (line.isEmpty()) return emptyList()

        val hidden = FloatArray(args.dim)
        line.forEach { featureId ->
            if (featureId in 0 until input.rows) {
                input.addRowTo(hidden, featureId, 1f)
            }
        }
        val scale = 1f / line.size
        hidden.indices.forEach { index -> hidden[index] *= scale }

        val logits = FloatArray(output.rows)
        for (labelIndex in 0 until output.rows) {
            logits[labelIndex] = output.dotRow(labelIndex, hidden)
        }
        return softmax(logits)
            .mapIndexed { index, probability ->
                val code = dictionary.labels.getOrNull(index).orEmpty()
                QuataLanguageScore(
                    language = QuataDetectedLanguage.fromCode(code),
                    code = code.ifBlank { QuataDetectedLanguage.Unknown.code },
                    confidence = probability
                )
            }
            .sortedByDescending { it.confidence }
    }

    private fun String.toFastTextLine(): IntArray {
        val tokens = trim()
            .split(TokenSeparators)
            .filter { it.isNotBlank() && !it.startsWith(FastTextLabelPrefix) }
        if (tokens.isEmpty()) return IntArray(0)

        val features = ArrayList<Int>(tokens.size * 4)
        val tokenHashes = ArrayList<Long>(tokens.size)
        tokens.forEach { token ->
            val tokenHash = fastTextHash(token)
            tokenHashes += tokenHash
            val wordId = dictionary.wordId(token)
            if (wordId >= 0) {
                features += wordId
                if (args.maxn > 0 && token != FastTextEos) {
                    computeSubwords(token, features)
                }
            } else {
                computeSubwords(token, features)
            }
        }
        addWordNgrams(features, tokenHashes)
        return features.toIntArray()
    }

    private fun computeSubwords(token: String, features: MutableList<Int>) {
        if (args.maxn <= 0 || args.bucket <= 0) return
        val bytes = "<$token>".toByteArray(StandardCharsets.UTF_8)
        for (start in bytes.indices) {
            if (bytes[start].isUtf8ContinuationByte()) continue
            var end = start
            var ngramLength = 1
            while (end < bytes.size && ngramLength <= args.maxn) {
                end++
                while (end < bytes.size && bytes[end].isUtf8ContinuationByte()) {
                    end++
                }
                if (
                    ngramLength >= args.minn &&
                    !(ngramLength == 1 && (start == 0 || end == bytes.size))
                ) {
                    dictionary.pushHash(features, fastTextHash(bytes, start, end - start))
                }
                ngramLength++
            }
        }
    }

    private fun addWordNgrams(features: MutableList<Int>, tokenHashes: List<Long>) {
        for (start in tokenHashes.indices) {
            var hash = tokenHashes[start]
            val endExclusive = minOf(tokenHashes.size, start + args.wordNgrams)
            for (index in start + 1 until endExclusive) {
                hash = hash * FastTextWordNgramSeed + tokenHashes[index]
                dictionary.pushHash(features, hash % args.bucket)
            }
        }
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.maxOrNull() ?: return FloatArray(0)
        var total = 0.0
        val exponents = DoubleArray(logits.size) { index ->
            exp((logits[index] - max).toDouble()).also { total += it }
        }
        return FloatArray(logits.size) { index -> (exponents[index] / total).toFloat() }
    }

    companion object {
        const val ModelAssetName = "lang_id_fasttext.bin"

        fun loadFromAssets(context: Context): FastTextLanguageDetector =
            context.assets.open(ModelAssetName).use { input ->
                fromByteArray(input.readBytes())
            }

        fun fromByteArray(bytes: ByteArray): FastTextLanguageDetector =
            FastTextModelReader(bytes).read(::FastTextLanguageDetector)
    }
}

object QuataLanguageIdentifier {
    @Volatile
    private var detector: FastTextLanguageDetector? = null

    suspend fun detector(context: Context): FastTextLanguageDetector =
        detector ?: withContext(Dispatchers.IO) {
            detector ?: FastTextLanguageDetector.loadFromAssets(context.applicationContext)
                .also { detector = it }
        }

    suspend fun detect(context: Context, text: String): QuataLanguageDetection =
        detector(context).detect(text)

    suspend fun detectCode(context: Context, text: String): String =
        detector(context).detectCode(text)
}

private class FastTextModelReader(private val bytes: ByteArray) {
    private val reader = LittleEndianReader(bytes)

    fun read(
        createDetector: (
            FastTextArgs,
            FastTextDictionary,
            FastTextMatrix,
            FastTextMatrix
        ) -> FastTextLanguageDetector
    ): FastTextLanguageDetector {
        val magic = reader.readInt()
        check(magic == FastTextMagic) { "Unsupported FastText model magic: $magic" }
        reader.readInt()
        val args = FastTextArgs(
            dim = reader.readInt(),
            ws = reader.readInt(),
            epoch = reader.readInt(),
            minCount = reader.readInt(),
            neg = reader.readInt(),
            wordNgrams = reader.readInt(),
            loss = reader.readInt(),
            model = reader.readInt(),
            bucket = reader.readInt(),
            minn = reader.readInt(),
            maxn = reader.readInt(),
            lrUpdateRate = reader.readInt(),
            samplingThreshold = reader.readDouble()
        )
        check(args.model == FastTextSupervisedModel) { "Only supervised FastText models are supported" }
        check(args.loss == FastTextSoftmaxLoss) { "Only softmax FastText classifiers are supported" }

        val dictionary = readDictionary(args.bucket)
        val input = if (reader.readBoolean()) readQuantMatrix() else readDenseMatrix()
        val output = if (reader.readBoolean()) readQuantMatrix() else readDenseMatrix()
        check(output.rows == dictionary.labels.size) {
            "FastText output labels mismatch: ${output.rows} rows, ${dictionary.labels.size} labels"
        }
        return createDetector(args, dictionary, input, output)
    }

    private fun readDictionary(bucket: Int): FastTextDictionary {
        val size = reader.readInt()
        val nwords = reader.readInt()
        val nlabels = reader.readInt()
        reader.readLong()
        val pruneSize = reader.readLong()
        val words = HashMap<String, Int>(nwords * 2)
        val labels = ArrayList<String>(nlabels)
        repeat(size) { index ->
            val word = reader.readCString()
            reader.readLong()
            val type = reader.readByte().toInt()
            if (type == FastTextEntryTypeWord) {
                words[word] = index
            } else if (type == FastTextEntryTypeLabel) {
                labels += word.removePrefix(FastTextLabelPrefix)
            }
        }
        val pruneMap = IntArray(bucket) { FastTextMissingPruneIndex }
        if (pruneSize > 0) {
            repeat(pruneSize.toInt()) {
                val key = reader.readInt()
                val value = reader.readInt()
                if (key in pruneMap.indices) {
                    pruneMap[key] = value
                }
            }
        }
        return FastTextDictionary(
            nwords = nwords,
            labels = labels,
            wordIds = words,
            bucket = bucket,
            pruneSize = pruneSize,
            pruneMap = pruneMap
        )
    }

    private fun readDenseMatrix(): DenseFastTextMatrix {
        val rows = reader.readLong().toInt()
        val columns = reader.readLong().toInt()
        val values = FloatArray(rows * columns) { reader.readFloat() }
        return DenseFastTextMatrix(rows, columns, values)
    }

    private fun readQuantMatrix(): QuantFastTextMatrix {
        val qnorm = reader.readBoolean()
        val rows = reader.readLong().toInt()
        val columns = reader.readLong().toInt()
        val codes = reader.readBytes(reader.readInt())
        val productQuantizer = readProductQuantizer()
        val normCodes: ByteArray?
        val normQuantizer: ProductQuantizer?
        if (qnorm) {
            normCodes = reader.readBytes(rows)
            normQuantizer = readProductQuantizer()
        } else {
            normCodes = null
            normQuantizer = null
        }
        return QuantFastTextMatrix(
            rows = rows,
            columns = columns,
            qnorm = qnorm,
            codes = codes,
            productQuantizer = productQuantizer,
            normCodes = normCodes,
            normQuantizer = normQuantizer
        )
    }

    private fun readProductQuantizer(): ProductQuantizer {
        val dim = reader.readInt()
        val nsubq = reader.readInt()
        val dsub = reader.readInt()
        val lastdsub = reader.readInt()
        val centroids = FloatArray(dim * FastTextProductQuantizerKsub) { reader.readFloat() }
        return ProductQuantizer(dim, nsubq, dsub, lastdsub, centroids)
    }
}

private data class FastTextArgs(
    val dim: Int,
    val ws: Int,
    val epoch: Int,
    val minCount: Int,
    val neg: Int,
    val wordNgrams: Int,
    val loss: Int,
    val model: Int,
    val bucket: Int,
    val minn: Int,
    val maxn: Int,
    val lrUpdateRate: Int,
    val samplingThreshold: Double
)

private class FastTextDictionary(
    val nwords: Int,
    val labels: List<String>,
    private val wordIds: Map<String, Int>,
    private val bucket: Int,
    private val pruneSize: Long,
    private val pruneMap: IntArray
) {
    fun wordId(token: String): Int = wordIds[token] ?: FastTextUnknownWord

    fun pushHash(features: MutableList<Int>, hash: Long) {
        if (bucket <= 0 || pruneSize == 0L) return
        val bucketIndex = floorMod(hash, bucket)
        val rowOffset = if (pruneSize > 0) {
            pruneMap.getOrElse(bucketIndex) { FastTextMissingPruneIndex }
        } else {
            bucketIndex
        }
        if (rowOffset != FastTextMissingPruneIndex) {
            features += nwords + rowOffset
        }
    }
}

private interface FastTextMatrix {
    val rows: Int
    val columns: Int
    fun addRowTo(target: FloatArray, row: Int, alpha: Float)
    fun dotRow(row: Int, vector: FloatArray): Float
}

private class DenseFastTextMatrix(
    override val rows: Int,
    override val columns: Int,
    private val values: FloatArray
) : FastTextMatrix {
    override fun addRowTo(target: FloatArray, row: Int, alpha: Float) {
        val offset = row * columns
        for (index in 0 until columns) {
            target[index] += alpha * values[offset + index]
        }
    }

    override fun dotRow(row: Int, vector: FloatArray): Float {
        val offset = row * columns
        var result = 0f
        for (index in 0 until columns) {
            result += values[offset + index] * vector[index]
        }
        return result
    }
}

private class QuantFastTextMatrix(
    override val rows: Int,
    override val columns: Int,
    private val qnorm: Boolean,
    private val codes: ByteArray,
    private val productQuantizer: ProductQuantizer,
    private val normCodes: ByteArray?,
    private val normQuantizer: ProductQuantizer?
) : FastTextMatrix {
    override fun addRowTo(target: FloatArray, row: Int, alpha: Float) {
        val norm = rowNorm(row)
        productQuantizer.addCode(target, codes, row, alpha * norm)
    }

    override fun dotRow(row: Int, vector: FloatArray): Float =
        productQuantizer.mulCode(vector, codes, row, rowNorm(row))

    private fun rowNorm(row: Int): Float {
        if (!qnorm) return 1f
        val codes = normCodes ?: return 1f
        val quantizer = normQuantizer ?: return 1f
        return quantizer.centroidValue(0, codes[row].unsignedByte(), 0)
    }
}

private class ProductQuantizer(
    private val dim: Int,
    private val nsubq: Int,
    private val dsub: Int,
    private val lastdsub: Int,
    private val centroids: FloatArray
) {
    fun addCode(target: FloatArray, codes: ByteArray, row: Int, alpha: Float) {
        val codeOffset = row * nsubq
        for (subquantizer in 0 until nsubq) {
            val code = codes[codeOffset + subquantizer].unsignedByte()
            val dimensionOffset = subquantizer * dsub
            val centroidOffset = centroidOffset(subquantizer, code)
            val centroidSize = centroidSize(subquantizer)
            for (index in 0 until centroidSize) {
                target[dimensionOffset + index] += alpha * centroids[centroidOffset + index]
            }
        }
    }

    fun mulCode(vector: FloatArray, codes: ByteArray, row: Int, alpha: Float): Float {
        var result = 0f
        val codeOffset = row * nsubq
        for (subquantizer in 0 until nsubq) {
            val code = codes[codeOffset + subquantizer].unsignedByte()
            val dimensionOffset = subquantizer * dsub
            val centroidOffset = centroidOffset(subquantizer, code)
            val centroidSize = centroidSize(subquantizer)
            for (index in 0 until centroidSize) {
                result += vector[dimensionOffset + index] * centroids[centroidOffset + index]
            }
        }
        return result * alpha
    }

    fun centroidValue(subquantizer: Int, code: Int, index: Int): Float =
        centroids[centroidOffset(subquantizer, code) + index]

    private fun centroidOffset(subquantizer: Int, code: Int): Int =
        if (subquantizer == nsubq - 1) {
            subquantizer * FastTextProductQuantizerKsub * dsub + code * lastdsub
        } else {
            (subquantizer * FastTextProductQuantizerKsub + code) * dsub
        }

    private fun centroidSize(subquantizer: Int): Int =
        if (subquantizer == nsubq - 1) lastdsub else dsub
}

private class LittleEndianReader(private val bytes: ByteArray) {
    private var offset = 0

    fun readByte(): Byte = bytes[offset++]

    fun readBoolean(): Boolean = readByte().toInt() != 0

    fun readInt(): Int {
        val value = (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
        offset += 4
        return value
    }

    fun readLong(): Long {
        var value = 0L
        for (shift in 0 until 8) {
            value = value or ((bytes[offset + shift].toLong() and 0xFFL) shl (shift * 8))
        }
        offset += 8
        return value
    }

    fun readFloat(): Float = Float.fromBits(readInt())

    fun readDouble(): Double = Double.fromBits(readLong())

    fun readCString(): String {
        val start = offset
        while (bytes[offset].toInt() != 0) {
            offset++
        }
        val value = String(bytes, start, offset - start, StandardCharsets.UTF_8)
        offset++
        return value
    }

    fun readBytes(length: Int): ByteArray =
        bytes.copyOfRange(offset, offset + length).also { offset += length }
}

private fun fastTextHash(text: String): Long {
    val bytes = text.toByteArray(StandardCharsets.UTF_8)
    return fastTextHash(bytes, 0, bytes.size)
}

private fun fastTextHash(bytes: ByteArray, start: Int, length: Int): Long {
    var hash = FastTextHashSeed
    for (index in start until start + length) {
        hash = hash xor bytes[index].toInt()
        hash *= FastTextHashPrime
    }
    return hash.toLong() and UIntMask
}

private fun Byte.isUtf8ContinuationByte(): Boolean = (toInt() and 0xC0) == 0x80

private fun Byte.unsignedByte(): Int = toInt() and 0xFF

private fun floorMod(value: Long, divisor: Int): Int {
    val remainder = value % divisor
    return (if (remainder < 0) remainder + divisor else remainder).toInt()
}

private val TokenSeparators = Regex("[\\s\\u0000]+")
private const val FastTextMagic = 793712314
private const val FastTextSupervisedModel = 3
private const val FastTextSoftmaxLoss = 3
private const val FastTextEntryTypeWord = 0
private const val FastTextEntryTypeLabel = 1
private const val FastTextLabelPrefix = "__label__"
private const val FastTextEos = "</s>"
private const val FastTextUnknownWord = -1
private const val FastTextMissingPruneIndex = -1
private const val FastTextProductQuantizerKsub = 256
private const val FastTextWordNgramSeed = 116049371L
private const val UIntMask = 0xFFFF_FFFFL
private const val FastTextHashSeed = -2128831035
private const val FastTextHashPrime = 16777619
