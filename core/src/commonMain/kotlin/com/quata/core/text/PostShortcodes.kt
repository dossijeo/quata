package com.quata.core.text

/** Platform-neutral post metadata codec. */

data class PostShortcodeContent(
    val cleanText: String,
    val documentText: String?
)

data class PostMeta(
    val channel: String = "feed",
    val cleanBody: String,
    val imageLocation: String,
    val mediaTitle: String,
    val textPattern: String,
    val alkaType: String,
    val isAlka: Boolean,
    val isVideoProcessing: Boolean,
    val videoProcessingLabel: String
)

fun String.parsePostShortcodeContent(): PostShortcodeContent {
    val meta = extractPostMeta()
    val documentText = PostShortcodeRegex
        .findAll(this)
        .firstOrNull { match ->
            match.groupValues.getOrNull(1)?.uppercase() !in KnownMetaShortcodes
        }
        ?.groupValues
        ?.getOrNull(2)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    return PostShortcodeContent(cleanText = meta.cleanBody, documentText = documentText)
}

fun String.withoutPostShortcodes(): String = parsePostShortcodeContent().cleanText

fun String.extractPostMeta(): PostMeta {
    val raw = this
    val channelMatch = ChannelShortcodeRegex.find(raw)
    val locationMatch = LocationShortcodeRegex.find(raw)
    val mediaTitleMatch = MediaTitleShortcodeRegex.find(raw)
    val textPatternMatch = TextPatternShortcodeRegex.find(raw)
    val alkaTypeMatch = AlkaTypeShortcodeRegex.find(raw)
    val processingMatch = VideoProcessingShortcodeRegex.find(raw)
    val channel = channelMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()
    val imageLocation = locationMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()
    val mediaTitle = mediaTitleMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()
    val textPattern = textPatternMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()
    val alkaType = alkaTypeMatch?.groupValues?.getOrNull(1)?.trim()?.lowercase().orEmpty()
    val isVideoProcessing = processingMatch != null
    val videoProcessingLabel = processingMatch
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.ifBlank { "Procesando video" }
        .orEmpty()
    val cleanBody = cleanTextCanvasSeedBody()
    return PostMeta(
        channel = channel.ifBlank { "feed" },
        cleanBody = cleanBody,
        imageLocation = imageLocation,
        mediaTitle = mediaTitle,
        textPattern = textPattern,
        alkaType = alkaType,
        isAlka = AlkaShortcodeRegex.containsMatchIn(raw) || alkaType.isNotBlank(),
        isVideoProcessing = isVideoProcessing,
        videoProcessingLabel = videoProcessingLabel
    )
}

fun String.cleanTextCanvasSeedBody(): String =
    replace(ChannelShortcodeRegex, "")
        .replace(LocationShortcodeRegex, "")
        .replace(MediaTitleShortcodeRegex, "")
        .replace(TextPatternShortcodeRegex, "")
        .replace(VideoProcessingShortcodeRegex, "")
        .replace(AlkaTypeShortcodeRegex, "")
        .replace(AlkaShortcodeRegex, "")
        .trim()

fun buildPostBodyWithMeta(
    cleanBody: String = "",
    imageLocation: String? = null,
    mediaTitle: String? = null,
    textPattern: String? = null,
    channel: String? = null
): String =
    buildList {
        channel?.trim()?.takeIf { it.isNotBlank() }?.let { add("[CANAL:${it.escapePostShortcodeValue()}]") }
        imageLocation?.trim()?.takeIf { it.isNotBlank() }?.let { add("[UBICACION:${it.escapePostShortcodeValue()}]") }
        mediaTitle?.trim()?.takeIf { it.isNotBlank() }?.let { add("[MEDIA_TITULO:${it.escapePostShortcodeValue()}]") }
        textPattern?.trim()?.takeIf { it.isNotBlank() }?.let { add("[PATRON_TEXTO:${it.escapePostShortcodeValue()}]") }
        cleanBody.trim().takeIf { it.isNotBlank() }?.let { add(it) }
    }.joinToString("\n").trim()

private fun String.escapePostShortcodeValue(): String =
    replace("]", "")
        .replace("\n", " ")
        .replace("\r", " ")
        .trim()

private val ChannelShortcodeRegex = Regex("""\[CANAL:([^\]]+)]""", RegexOption.IGNORE_CASE)
private val LocationShortcodeRegex = Regex("""\[UBICACION:([^\]]+)]""", RegexOption.IGNORE_CASE)
private val MediaTitleShortcodeRegex = Regex("""\[MEDIA_TITULO:([^\]]+)]""", RegexOption.IGNORE_CASE)
private val TextPatternShortcodeRegex = Regex("""\[PATRON_TEXTO:([^\]]+)]""", RegexOption.IGNORE_CASE)
private val VideoProcessingShortcodeRegex = Regex("""\[VIDEO_PROCESANDO(?::([^\]]+))?]""", RegexOption.IGNORE_CASE)
private val AlkaTypeShortcodeRegex = Regex("""\[ALKA_TIPO:([^\]]+)]""", RegexOption.IGNORE_CASE)
private val AlkaShortcodeRegex = Regex("""\[ALKA]""", RegexOption.IGNORE_CASE)
private val KnownMetaShortcodes = setOf(
    "CANAL",
    "UBICACION",
    "MEDIA_TITULO",
    "PATRON_TEXTO",
    "VIDEO_PROCESANDO",
    "ALKA_TIPO"
)
private val PostShortcodeRegex = Regex("""\[([A-Za-z0-9_]+):([^\]]+)]""")
