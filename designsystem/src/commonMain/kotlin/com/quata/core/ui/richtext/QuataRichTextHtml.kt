package com.quata.core.ui.richtext

private val AllowedHtmlTags = linkedSetOf(
    "p",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "ul",
    "ol",
    "li",
    "blockquote",
    "aside",
    "pre",
    "code",
    "hr",
    "br",
    "a",
    "b",
    "strong",
    "i",
    "em",
    "u",
    "s",
    "del",
    "mark",
)

private const val TODO_UNCHECKED = "\u2610"
private const val TODO_CHECKED = "\u2611"
private const val TODO_MARK = "data-quata-todo"

public fun convertBlocksToHtml(blocks: List<QuataRichTextBlock>): String {
    if (blocks.isEmpty()) return ""

    val html = StringBuilder()
    var index = 0
    while (index < blocks.size) {
        val block = blocks[index]
        when (block.type) {
            RichTextBlockType.Bullet, RichTextBlockType.Numbered, RichTextBlockType.Todo -> {
                val listTag = when (block.type) {
                    RichTextBlockType.Numbered -> "ol"
                    else -> "ul"
                }
                html.append("<").append(listTag).append(">")
                var i = index
                while (i < blocks.size &&
                    isListLikeType(blocks[i].type) &&
                    listType(blocks[i].type) == listTag
                ) {
                    val item = blocks[i]
                    html.append("<li")
                    if (item.type == RichTextBlockType.Todo) {
                        val todoState = if (item.isChecked) "true" else "false"
                        html.append(" ").append(TODO_MARK).append("=\"").append(todoState).append("\"")
                    }
                    html.append(">")
                    if (item.type == RichTextBlockType.Todo) {
                        val marker = if (item.isChecked) TODO_CHECKED else TODO_UNCHECKED
                        html.append(marker).append(" ")
                    }
                    html.append(renderInlineHtml(item.text.text, item.spans))
                    html.append("</li>")
                    i++
                }
                html.append("</").append(listTag).append(">")
                index = i
            }

            RichTextBlockType.Code -> {
                html.append("<pre><code>")
                html.append(encodeHtml(block.text.text).replace("\n", "<br>"))
                html.append("</code></pre>")
                index++
            }

            RichTextBlockType.Quote -> {
                html.append("<blockquote>").append(renderInlineHtml(block.text.text, block.spans)).append("</blockquote>")
                index++
            }

            RichTextBlockType.Info -> {
                html.append("<aside>").append(renderInlineHtml(block.text.text, block.spans)).append("</aside>")
                index++
            }

            RichTextBlockType.Heading1 -> {
                html.append("<h1>").append(renderInlineHtml(block.text.text, block.spans)).append("</h1>")
                index++
            }

            RichTextBlockType.Heading2 -> {
                html.append("<h2>").append(renderInlineHtml(block.text.text, block.spans)).append("</h2>")
                index++
            }

            RichTextBlockType.Heading3 -> {
                html.append("<h3>").append(renderInlineHtml(block.text.text, block.spans)).append("</h3>")
                index++
            }

            RichTextBlockType.Heading4 -> {
                html.append("<h4>").append(renderInlineHtml(block.text.text, block.spans)).append("</h4>")
                index++
            }

            RichTextBlockType.Heading5 -> {
                html.append("<h5>").append(renderInlineHtml(block.text.text, block.spans)).append("</h5>")
                index++
            }

            RichTextBlockType.Heading6 -> {
                html.append("<h6>").append(renderInlineHtml(block.text.text, block.spans)).append("</h6>")
                index++
            }

            RichTextBlockType.Divider -> {
                html.append("<hr>")
                index++
            }

            RichTextBlockType.Paragraph -> {
                if (block.text.text.isBlank()) {
                    html.append("<p><br></p>")
                } else {
                    html.append("<p>").append(renderInlineHtml(block.text.text, block.spans)).append("</p>")
                }
                index++
            }
        }
    }
    return html.toString()
}

public fun parseHtmlToRichTextBlocks(rawHtml: String): List<QuataRichTextBlock> {
    if (rawHtml.isBlank()) return emptyList()

    val sanitized = sanitizeGeneratedHtml(rawHtml)
    val blocks = mutableListOf<QuataRichTextBlock>()

    val htmlBlocks = findHtmlBlocks(sanitized, setOf("h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "pre", "blockquote", "aside", "p"))
    if (htmlBlocks.isEmpty()) {
        val parsed = parseInlineHtml(sanitized).trimmed()
        if (parsed.text.isNotBlank()) {
            blocks.add(QuataRichTextBlock(text = parsed.text, spans = parsed.spans))
        }
        return blocks
    }

    for (htmlBlock in htmlBlocks) {
        val tag = htmlBlock.tag
        val content = htmlBlock.content

        if (tag == "hr") {
            blocks.add(QuataRichTextBlock(type = RichTextBlockType.Divider, text = ""))
            continue
        }

        when (tag) {
            "h1" -> blocks.add(blockFromInline(RichTextBlockType.Heading1, content, trim = true))
            "h2" -> blocks.add(blockFromInline(RichTextBlockType.Heading2, content, trim = true))
            "h3" -> blocks.add(blockFromInline(RichTextBlockType.Heading3, content, trim = true))
            "h4" -> blocks.add(blockFromInline(RichTextBlockType.Heading4, content, trim = true))
            "h5" -> blocks.add(blockFromInline(RichTextBlockType.Heading5, content, trim = true))
            "h6" -> blocks.add(blockFromInline(RichTextBlockType.Heading6, content, trim = true))
            "pre" -> blocks.add(QuataRichTextBlock(type = RichTextBlockType.Code, text = decodeCodeText(content)))
            "blockquote" -> blocks.add(blockFromInline(RichTextBlockType.Quote, content, trim = true))
            "aside" -> blocks.add(blockFromInline(RichTextBlockType.Info, content, trim = true))
            "hr" -> blocks.add(QuataRichTextBlock(type = RichTextBlockType.Divider, text = ""))
            "ul", "ol" -> blocks.addAll(parseListTag(tag, content, forceTodo = isTodoListContainer(htmlBlock.attributes)))
            "p" -> {
                val parsed = parseInlineHtml(content)
                val text = if (parsed.text.isNotBlank()) parsed.text else if (BR_TAG_PATTERN.containsMatchIn(content)) "" else ""
                if (text.isNotBlank() || BR_TAG_PATTERN.containsMatchIn(content)) {
                    blocks.add(QuataRichTextBlock(type = RichTextBlockType.Paragraph, text = text, spans = parsed.spans))
                }
            }
        }
    }

    if (blocks.isEmpty()) {
        val fallback = parseInlineHtml(sanitized).trimmed()
        if (fallback.text.isNotBlank()) {
            blocks.add(QuataRichTextBlock(text = fallback.text, spans = fallback.spans))
        }
    }
    return blocks
}

private fun parseListTag(
    containerTag: String,
    content: String,
    parentIndent: Int = 0,
    forceTodo: Boolean = false,
): List<QuataRichTextBlock> {
    val output = mutableListOf<QuataRichTextBlock>()
    for (item in findListItems(content)) {
        val attrs = item.attributes
        val rawContent = item.content
        val indent = 0
        val todoCheckedFromAttr = parseTodoCheckedFromAttributes(attrs)
        val isTodoAttribute = forceTodo || todoCheckedFromAttr != null || parseTodoFromAttributes(attrs)
        val nestedLists = findHtmlBlocks(rawContent, setOf("ul", "ol"))
        val rawText = replaceRangesWithSpace(rawContent, nestedLists.map { it.range })
        val parsedInline = parseInlineHtml(rawText).trimmed()
        val cleaned = if (parsedInline.text.isBlank() && BR_TAG_PATTERN.containsMatchIn(rawText)) "" else parsedInline.text

        val trimmedForTodo = cleaned.trimStart()
        val hasTodoPrefix = if (isTodoAttribute) {
            true
        } else {
            trimmedForTodo.startsWith(TODO_CHECKED) || trimmedForTodo.startsWith(TODO_UNCHECKED)
        }
        val parsed = parseTodoPrefix(trimmedForTodo)
        val isTodo = isTodoAttribute || hasTodoPrefix
        val visibleText = if (isTodo) parsed.text else cleaned
        val spans = if (isTodo) {
            parsedInline.shiftedAfterTodoPrefix(cleaned.length - visibleText.length)
        } else {
            parsedInline.spans
        }
        val type = when {
            containerTag == "ol" && !isTodo -> RichTextBlockType.Numbered
            isTodo -> RichTextBlockType.Todo
            else -> RichTextBlockType.Bullet
        }
        output.add(
            QuataRichTextBlock(
                type = type,
                text = visibleText,
                checked = if (isTodo) todoCheckedFromAttr ?: parsed.isChecked else false,
                indent = indent,
                spans = spans,
            ),
        )

        for (nestedList in nestedLists) {
            output.addAll(
                parseListTag(
                    containerTag = nestedList.tag,
                    content = nestedList.content,
                    parentIndent = 0,
                    forceTodo = forceTodo || isTodoListContainer(nestedList.attributes),
                ),
            )
        }
    }
    return output
}

private fun findHtmlBlocks(html: String, allowedTags: Set<String>): List<HtmlBlock> {
    if (html.isBlank()) return emptyList()
    val output = mutableListOf<HtmlBlock>()
    val tokenPattern = Regex("<(/?)([a-zA-Z0-9]+)([^>]*)>", RegexOption.IGNORE_CASE)
    var activeTag: String? = null
    var activeAttributes = ""
    var activeStart = -1
    var activeContentStart = -1
    var activeDepth = 0

    for (match in tokenPattern.findAll(html)) {
        val isClosing = match.groupValues[1] == "/"
        val tag = match.groupValues[2].lowercase()
        val attrs = match.groupValues[3]
        val isSelfClosing = attrs.trimEnd().endsWith("/")

        if (activeTag == null) {
            if (!isClosing && tag == "hr") {
                output += HtmlBlock(
                    tag = "hr",
                    attributes = attrs,
                    content = "",
                    range = match.range,
                )
            } else if (!isClosing && tag in allowedTags && !isSelfClosing) {
                activeTag = tag
                activeAttributes = attrs
                activeStart = match.range.first
                activeContentStart = match.range.last + 1
                activeDepth = 1
            }
            continue
        }

        if (tag != activeTag) continue
        if (!isClosing && !isSelfClosing) {
            activeDepth++
            continue
        }
        if (isClosing) {
            activeDepth--
            if (activeDepth == 0) {
                output += HtmlBlock(
                    tag = activeTag,
                    attributes = activeAttributes,
                    content = html.substring(activeContentStart, match.range.first),
                    range = activeStart..match.range.last,
                )
                activeTag = null
                activeAttributes = ""
                activeStart = -1
                activeContentStart = -1
            }
        }
    }
    return output
}

private fun findListItems(html: String): List<HtmlBlock> {
    if (html.isBlank()) return emptyList()
    val output = mutableListOf<HtmlBlock>()
    val tokenPattern = Regex("<(/?)li\\b([^>]*)>", RegexOption.IGNORE_CASE)
    var activeStart = -1
    var activeContentStart = -1
    var activeAttributes = ""
    var depth = 0

    for (match in tokenPattern.findAll(html)) {
        val isClosing = match.groupValues[1] == "/"
        if (!isClosing) {
            if (depth == 0) {
                activeStart = match.range.first
                activeContentStart = match.range.last + 1
                activeAttributes = match.groupValues[2]
            }
            depth++
            continue
        }

        if (depth == 0) continue
        depth--
        if (depth == 0 && activeStart >= 0) {
            output += HtmlBlock(
                tag = "li",
                attributes = activeAttributes,
                content = html.substring(activeContentStart, match.range.first),
                range = activeStart..match.range.last,
            )
            activeStart = -1
            activeContentStart = -1
            activeAttributes = ""
        }
    }
    return output
}

private fun replaceRangesWithSpace(value: String, ranges: List<IntRange>): String {
    if (ranges.isEmpty()) return value
    val output = StringBuilder(value.length)
    var cursor = 0
    for (range in ranges.sortedBy { it.first }) {
        if (range.first < cursor) continue
        output.append(value.substring(cursor, range.first))
        output.append(' ')
        cursor = range.last + 1
    }
    if (cursor < value.length) output.append(value.substring(cursor))
    return output.toString()
}

private fun parseTodoPrefix(text: String): TodoPrefixParseResult {
    var normalized = text
    var checked = false
    when {
        normalized.startsWith(TODO_CHECKED) -> {
            normalized = normalized.substring(TODO_CHECKED.length).trimStart()
            checked = true
        }
        normalized.startsWith(TODO_UNCHECKED) -> {
            normalized = normalized.substring(TODO_UNCHECKED.length).trimStart()
        }
    }
    return TodoPrefixParseResult(
        text = normalized,
        isChecked = checked,
    )
}

private fun isListLikeType(type: RichTextBlockType): Boolean = when (type) {
    RichTextBlockType.Bullet, RichTextBlockType.Numbered, RichTextBlockType.Todo -> true
    else -> false
}

private fun listType(type: RichTextBlockType): String = when (type) {
    RichTextBlockType.Numbered -> "ol"
    else -> "ul"
}

public fun sanitizeGeneratedHtml(raw: String): String {
    if (raw.isBlank()) return ""

    var sanitized = raw
        .replace(Regex("<\\s*script[^>]*>[\\s\\S]*?<\\s*/\\s*script\\s*>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("<\\s*style[^>]*>[\\s\\S]*?<\\s*/\\s*style\\s*>", RegexOption.IGNORE_CASE), "")
        .replace(
            Regex("<\\s*(iframe|object|embed|frameset|frame)[^>]*>[\\s\\S]*?<\\s*/\\s*\\1\\s*>", RegexOption.IGNORE_CASE),
            "",
        )
        .replace(Regex("\\s+on[a-zA-Z0-9_]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+style\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)", RegexOption.IGNORE_CASE), "")

    sanitized = Regex("<a\\b[^>]*>", RegexOption.IGNORE_CASE).replace(sanitized) { match ->
        val href = Regex("href\\s*=\\s*([\"'])(.*?)\\1", RegexOption.IGNORE_CASE)
            .find(match.value)
            ?.groupValues
            ?.getOrNull(2)
        val safeHref = sanitizeHref(href)
        if (safeHref == null) "<a>" else "<a href=\"$safeHref\">"
    }

    val tagPattern = Regex("</?([a-zA-Z0-9]+)(\\s[^>]*)?>", RegexOption.IGNORE_CASE)
    return tagPattern.replace(sanitized) { match ->
        val name = match.groupValues[1].lowercase()
        if (AllowedHtmlTags.contains(name)) match.value else ""
    }
}

public fun sanitizeHref(rawHref: String?): String? {
    val href = rawHref?.trim().orEmpty()
    if (href.isBlank()) return null
    val lower = href.lowercase()
    return when {
        lower.startsWith("http://") -> href
        lower.startsWith("https://") -> href
        lower.startsWith("mailto:") -> href
        lower.startsWith("tel:") -> href
        lower.startsWith("sms:") -> href
        lower.startsWith("#") -> href
        href.startsWith("//") -> "https:$href"
        else -> null
    }
}

private fun parseTodoFromAttributes(rawAttributes: String): Boolean {
    if (Regex("(^|\\s)checked(\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(rawAttributes)) {
        return true
    }
    val value = Regex("$TODO_MARK\\s*=\\s*([\"'])(.*?)\\1", RegexOption.IGNORE_CASE).find(rawAttributes)
        ?.groupValues
        ?.getOrNull(2)
        ?.trim()
        ?: return false
    return value.equals("1", ignoreCase = true) ||
        value.equals("true", ignoreCase = true) ||
        value.equals("checked", ignoreCase = true) ||
        value.equals("yes", ignoreCase = true)
}

private fun parseTodoCheckedFromAttributes(rawAttributes: String): Boolean? {
    val quoted = Regex("(^|\\s)checked\\s*=\\s*([\"'])(.*?)\\2", RegexOption.IGNORE_CASE).find(rawAttributes)
        ?.groupValues
        ?.getOrNull(3)
        ?.trim()
    val unquoted = Regex("(^|\\s)checked\\s*=\\s*([^\\s>]+)", RegexOption.IGNORE_CASE).find(rawAttributes)
        ?.groupValues
        ?.getOrNull(2)
        ?.trim()
    val value = quoted ?: unquoted ?: return if (Regex("(^|\\s)checked(\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(rawAttributes)) true else null
    return value.equals("1", ignoreCase = true) ||
        value.equals("true", ignoreCase = true) ||
        value.equals("checked", ignoreCase = true) ||
        value.equals("yes", ignoreCase = true)
}

private fun isTodoListContainer(rawAttributes: String): Boolean {
    val classValue = Regex("class\\s*=\\s*([\"'])(.*?)\\1", RegexOption.IGNORE_CASE).find(rawAttributes)
        ?.groupValues
        ?.getOrNull(2)
        .orEmpty()
    return classValue.split(Regex("\\s+")).any { it.equals("todo", ignoreCase = true) || it.equals("task-list", ignoreCase = true) }
}

private fun blockFromInline(type: RichTextBlockType, raw: String, trim: Boolean): QuataRichTextBlock {
    val parsed = if (trim) parseInlineHtml(raw).trimmed() else parseInlineHtml(raw)
    return QuataRichTextBlock(type = type, text = parsed.text, spans = parsed.spans)
}

private fun renderInlineHtml(text: String, spans: List<QuataTextSpan>): String {
    if (text.isEmpty()) return ""
    val normalizedSpans = QuataSpanAlgorithms.normalize(spans, text.length)
    if (normalizedSpans.isEmpty()) {
        return encodeHtml(text).replace("\n", "<br>")
    }

    val boundaries = mutableSetOf(0, text.length)
    normalizedSpans.forEach { span ->
        boundaries += span.start
        boundaries += span.end
    }
    val points = boundaries.sorted()
    val html = StringBuilder()
    for (index in 0 until points.lastIndex) {
        val start = points[index]
        val end = points[index + 1]
        if (start >= end) continue
        val active = normalizedSpans
            .filter { it.start <= start && it.end >= end }
            .sortedBy { spanRenderPriority(it.style) }
        var segment = encodeHtml(text.substring(start, end)).replace("\n", "<br>")
        for (span in active.asReversed()) {
            segment = wrapHtmlSegment(segment, span.style)
        }
        html.append(segment)
    }
    return html.toString()
}

private fun spanRenderPriority(style: QuataSpanStyle): Int = when (style) {
    is QuataSpanStyle.Link -> 0
    QuataSpanStyle.Bold -> 1
    QuataSpanStyle.Italic -> 2
    QuataSpanStyle.Underline -> 3
    QuataSpanStyle.Strike -> 4
    QuataSpanStyle.Highlight -> 5
    QuataSpanStyle.InlineCode -> 6
}

private fun wrapHtmlSegment(segment: String, style: QuataSpanStyle): String = when (style) {
    QuataSpanStyle.Bold -> "<b>$segment</b>"
    QuataSpanStyle.Italic -> "<i>$segment</i>"
    QuataSpanStyle.Underline -> "<u>$segment</u>"
    QuataSpanStyle.Strike -> "<del>$segment</del>"
    QuataSpanStyle.InlineCode -> "<code>$segment</code>"
    QuataSpanStyle.Highlight -> "<mark>$segment</mark>"
    is QuataSpanStyle.Link -> "<a href=\"${encodeHtmlAttribute(style.url)}\">$segment</a>"
}

private fun parseInlineHtml(raw: String): QuataInlineParseResult {
    if (raw.isBlank()) return QuataInlineParseResult("", emptyList())
    val text = StringBuilder()
    val spans = mutableListOf<QuataTextSpan>()
    val open = mutableListOf<Pair<QuataSpanStyle, Int>>()
    val tokenPattern = Regex("(?is)<br\\s*/?>|</?([a-zA-Z0-9]+)(\\s[^>]*)?>")
    var cursor = 0

    fun appendDecoded(value: String) {
        if (value.isEmpty()) return
        text.append(decodeHtmlText(value))
    }

    for (match in tokenPattern.findAll(raw)) {
        appendDecoded(raw.substring(cursor, match.range.first))
        val token = match.value
        if (BR_TAG_PATTERN.matches(token)) {
            text.append('\n')
            cursor = match.range.last + 1
            continue
        }
        val tag = match.groupValues.getOrNull(1).orEmpty().lowercase()
        val isClosing = token.startsWith("</")
        val style = styleForTag(tag, match.groupValues.getOrNull(2).orEmpty())
        if (style != null) {
            if (isClosing) {
                val index = open.indexOfLast { openStyle -> sameStyleKind(openStyle.first, style) }
                if (index >= 0) {
                    val (openStyle, start) = open.removeAt(index)
                    if (start < text.length) spans += QuataTextSpan(start, text.length, openStyle)
                }
            } else {
                open += style to text.length
            }
        }
        cursor = match.range.last + 1
    }
    appendDecoded(raw.substring(cursor))
    for ((style, start) in open) {
        if (start < text.length) spans += QuataTextSpan(start, text.length, style)
    }
    val finalText = text.toString().replace("\u00a0", " ")
    return QuataInlineParseResult(finalText, QuataSpanAlgorithms.normalize(spans, finalText.length))
}

private fun styleForTag(tag: String, attributes: String): QuataSpanStyle? = when (tag) {
    "b", "strong" -> QuataSpanStyle.Bold
    "i", "em" -> QuataSpanStyle.Italic
    "u" -> QuataSpanStyle.Underline
    "s", "del" -> QuataSpanStyle.Strike
    "mark" -> QuataSpanStyle.Highlight
    "code" -> QuataSpanStyle.InlineCode
    "a" -> {
        val href = Regex("href\\s*=\\s*([\"'])(.*?)\\1", RegexOption.IGNORE_CASE)
            .find(attributes)
            ?.groupValues
            ?.getOrNull(2)
        sanitizeHref(href)?.let(QuataSpanStyle::Link)
    }
    else -> null
}

private fun sameStyleKind(first: QuataSpanStyle, second: QuataSpanStyle): Boolean {
    return when {
        first is QuataSpanStyle.Link && second is QuataSpanStyle.Link -> true
        else -> first::class == second::class
    }
}

private data class QuataInlineParseResult(
    val text: String,
    val spans: List<QuataTextSpan>,
) {
    fun trimmed(): QuataInlineParseResult {
        val startTrim = text.indexOfFirst { !it.isWhitespace() }
        if (startTrim == -1) return QuataInlineParseResult("", emptyList())
        val endTrimExclusive = text.indexOfLast { !it.isWhitespace() } + 1
        val trimmedText = text.substring(startTrim, endTrimExclusive)
        val shifted = spans.mapNotNull { span ->
            val start = maxOf(span.start, startTrim) - startTrim
            val end = minOf(span.end, endTrimExclusive) - startTrim
            if (start < end) span.copy(start = start, end = end) else null
        }
        return QuataInlineParseResult(trimmedText, QuataSpanAlgorithms.normalize(shifted, trimmedText.length))
    }

    fun shiftedAfterTodoPrefix(prefixLength: Int): List<QuataTextSpan> {
        if (prefixLength <= 0) return spans
        val shifted = spans.mapNotNull { span ->
            val start = maxOf(span.start, prefixLength) - prefixLength
            val end = span.end - prefixLength
            if (start < end) span.copy(start = start, end = end) else null
        }
        return QuataSpanAlgorithms.normalize(shifted, maxOf(0, text.length - prefixLength))
    }
}

private fun inlineMarkupToHtml(text: String): String {
    var safe = encodeHtml(text)
        .replace("\u0000", "")
        .replace("\n", "<br>")
    safe = safe.replace("`([^`]+?)`".toRegex(), "<code>$1</code>")
    safe = safe.replace("\\*\\*(.+?)\\*\\*".toRegex(), "<b>$1</b>")
    safe = safe.replace("\\*(.+?)\\*".toRegex(), "<i>$1</i>")
    safe = safe.replace("__([^_]+?)__".toRegex(), "<u>$1</u>")
    safe = safe.replace("~~(.+?)~~".toRegex(), "<del>$1</del>")
    safe = safe.replace("==(.+?)==".toRegex(), "<mark>$1</mark>")
    safe = safe.replace("\\[([^\\]]+?)\\]\\(([^)]+)\\)".toRegex()) { match ->
        "<a href=\"${sanitizeHref(match.groupValues[2]) ?: "#"}\">${match.groupValues[1]}</a>"
    }
    return safe
}

private fun inlineHtmlToMarkup(raw: String): String {
    if (raw.isBlank()) return ""
    var normalized = BR_TAG_PATTERN.replace(raw, "\n")

    val linkRegex = "(?is)<a\\b[^>]*href\\s*=\\s*([\"'])(.*?)\\1[^>]*>([\\s\\S]*?)</a>".toRegex()
    val result = StringBuilder()
    var last = 0
    for (match in linkRegex.findAll(normalized)) {
        result.append(normalized.substring(last, match.range.first))
        val href = match.groupValues.getOrNull(2).orEmpty()
        val text = match.groupValues.getOrNull(3).orEmpty()
        result.append("[").append(text).append("](").append(href).append(")")
        last = match.range.last + 1
    }
    if (last < normalized.length) {
        result.append(normalized.substring(last))
    }
    normalized = if (result.isNotEmpty()) result.toString() else normalized

    normalized = normalized
        .replace("<b>", "**").replace("</b>", "**")
        .replace("<strong>", "**").replace("</strong>", "**")
        .replace("<i>", "*").replace("</i>", "*")
        .replace("<em>", "*").replace("</em>", "*")
        .replace("<u>", "__").replace("</u>", "__")
        .replace("<del>", "~~").replace("</del>", "~~")
        .replace("<s>", "~~").replace("</s>", "~~")
        .replace("<mark>", "==").replace("</mark>", "==")
        .replace("<code>", "`").replace("</code>", "`")

    return stripTagsAndDecode(normalized)
        .replace("\u00a0", " ")
}

private fun decodeCodeText(raw: String): String {
    return stripTagsAndDecode(BR_TAG_PATTERN.replace(raw, "\n"))
}

private fun encodeHtml(value: String): String {
    if (value.isEmpty()) return ""
    val output = StringBuilder(value.length)
    for (char in value) {
        when (char) {
            '&' -> output.append("&amp;")
            '<' -> output.append("&lt;")
            '>' -> output.append("&gt;")
            '"' -> output.append("&quot;")
            '\'' -> output.append("&#39;")
            else -> output.append(char)
        }
    }
    return output.toString()
}

private fun encodeHtmlAttribute(value: String): String = encodeHtml(value).replace("`", "&#96;")

private fun stripTagsAndDecode(value: String): String {
    return decodeHtmlText(Regex("<[^>]+>").replace(value, ""))
}

private fun decodeHtmlText(value: String): String {
    if (value.isEmpty()) return ""
    val named = value
        .replace("&nbsp;", " ", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&#39;", "'", ignoreCase = true)
        .replace("&apos;", "'", ignoreCase = true)
        .replace("&amp;", "&", ignoreCase = true)

    return Regex("&#(x[0-9a-fA-F]+|\\d+);").replace(named) { match ->
        val token = match.groupValues[1]
        val codePoint = if (token.startsWith("x", ignoreCase = true)) {
            token.drop(1).toIntOrNull(16)
        } else {
            token.toIntOrNull()
        }
        if (codePoint == null || codePoint !in 0..0x10FFFF) {
            match.value
        } else {
            codePoint.toUnicodeString()
        }
    }
}

private fun Int.toUnicodeString(): String = when {
    this <= 0xFFFF -> toChar().toString()
    else -> {
        val adjusted = this - 0x1_0000
        charArrayOf(
            ((adjusted shr 10) + 0xD800).toChar(),
            ((adjusted and 0x3FF) + 0xDC00).toChar(),
        ).concatToString()
    }
}

private data class TodoPrefixParseResult(
    val text: String,
    val isChecked: Boolean,
)

private data class HtmlBlock(
    val tag: String,
    val attributes: String,
    val content: String,
    val range: IntRange,
)

private val BR_TAG_PATTERN = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
