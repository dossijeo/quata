package com.quata.core.ui.richtext

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuataRichTextRuntimeTest {
    @Test
    fun slashRegistryFindsCommandsByLabelAndKeyword() {
        val registry = QuataSlashCommandRegistry()

        assertEquals(RichTextBlockType.Heading2, registry.filter("h2").first().type)
        assertEquals(RichTextBlockType.Todo, registry.filter("check").first().type)
        assertTrue(registry.filter("").size >= 10)
    }

    @Test
    fun blockSpanStatesSplitAndMergePreserveStyles() {
        val spans = QuataBlockSpanStates()
        spans.getOrCreate(
            blockId = "a",
            initialSpans = listOf(QuataTextSpan(0, 6, QuataSpanStyle.Bold)),
            textLength = 6,
        )

        spans.split(sourceBlockId = "a", newBlockId = "b", position = 3)

        assertEquals(listOf(QuataTextSpan(0, 3, QuataSpanStyle.Bold)), spans.getSpans("a"))
        assertEquals(listOf(QuataTextSpan(0, 3, QuataSpanStyle.Bold)), spans.getSpans("b"))

        spans.mergeInto(sourceId = "b", targetId = "a", targetTextLength = 3, mergedTextLength = 6)

        assertEquals(listOf(QuataTextSpan(0, 6, QuataSpanStyle.Bold)), spans.getSpans("a"))
        assertEquals(emptyList<QuataTextSpan>(), spans.getSpans("b"))
    }

    @Test
    fun linkHitTesterResolvesSmallestLinkAtCursor() {
        val block = QuataRichTextBlock(
            id = "link-block",
            text = "hello quata",
            spans = listOf(QuataTextSpan(6, 11, QuataSpanStyle.Link("https://quata.app"))),
        )

        val target = QuataLinkHitTester.resolve(block, 8)

        assertNotNull(target)
        val resolved = target!!
        assertEquals("link-block", resolved.blockId)
        assertEquals("quata", resolved.text)
        assertEquals("https://quata.app", resolved.url)
        assertNull(QuataLinkHitTester.resolve(block, 1))
    }

    @Test
    fun htmlRoundTripPreservesBlockTypesTodoStateIndentAndInlineStyles() {
        val blocks = listOf(
            QuataRichTextBlock(type = RichTextBlockType.Heading1, text = "Heading 1"),
            QuataRichTextBlock(type = RichTextBlockType.Heading2, text = "Heading 2"),
            QuataRichTextBlock(type = RichTextBlockType.Heading3, text = "Heading 3"),
            QuataRichTextBlock(type = RichTextBlockType.Heading4, text = "Heading 4"),
            QuataRichTextBlock(type = RichTextBlockType.Heading5, text = "Heading 5"),
            QuataRichTextBlock(type = RichTextBlockType.Heading6, text = "Heading 6"),
            QuataRichTextBlock(
                type = RichTextBlockType.Paragraph,
                text = "bold italic underline strike code mark link",
                spans = listOf(
                    QuataTextSpan(0, 4, QuataSpanStyle.Bold),
                    QuataTextSpan(5, 11, QuataSpanStyle.Italic),
                    QuataTextSpan(12, 21, QuataSpanStyle.Underline),
                    QuataTextSpan(22, 28, QuataSpanStyle.Strike),
                    QuataTextSpan(29, 33, QuataSpanStyle.InlineCode),
                    QuataTextSpan(34, 38, QuataSpanStyle.Highlight),
                    QuataTextSpan(39, 43, QuataSpanStyle.Link("https://egquata.com")),
                ),
            ),
            QuataRichTextBlock(type = RichTextBlockType.Bullet, text = "bullet root"),
            QuataRichTextBlock(type = RichTextBlockType.Bullet, text = "bullet child", indent = 1),
            QuataRichTextBlock(type = RichTextBlockType.Numbered, text = "numbered root"),
            QuataRichTextBlock(type = RichTextBlockType.Numbered, text = "numbered child", indent = 2),
            QuataRichTextBlock(type = RichTextBlockType.Todo, text = "todo open", checked = false),
            QuataRichTextBlock(type = RichTextBlockType.Todo, text = "todo done", checked = true, indent = 1),
            QuataRichTextBlock(type = RichTextBlockType.Quote, text = "quoted"),
            QuataRichTextBlock(type = RichTextBlockType.Info, text = "info"),
            QuataRichTextBlock(type = RichTextBlockType.Code, text = "fun main() {\n    println(\"ok\")\n}"),
            QuataRichTextBlock(type = RichTextBlockType.Divider),
        )

        val parsed = parseHtmlToRichTextBlocks(convertBlocksToHtml(blocks))

        assertEquals(blocks.size, parsed.size)
        assertEquals(blocks.map { it.type }, parsed.map { it.type })
        assertEquals(blocks.map { it.text.text }, parsed.map { it.text.text })
        assertEquals(blocks.map { it.indentLevel }, parsed.map { it.indentLevel })
        assertEquals(blocks.map { it.isChecked }, parsed.map { it.isChecked })
        assertEquals(blocks[6].spans, parsed[6].spans)
    }

    @Test
    fun parseHtmlFlattensNestedListsWithoutIndentation() {
        val html = """
            <ul>
                <li>Root bullet
                    <ul>
                        <li>Child bullet</li>
                    </ul>
                </li>
                <li>Second root</li>
            </ul>
            <ol>
                <li>Root number
                    <ol>
                        <li>Child number</li>
                    </ol>
                </li>
            </ol>
        """.trimIndent()

        val parsed = parseHtmlToRichTextBlocks(html)

        assertEquals(
            listOf(
                RichTextBlockType.Bullet,
                RichTextBlockType.Bullet,
                RichTextBlockType.Bullet,
                RichTextBlockType.Numbered,
                RichTextBlockType.Numbered,
            ),
            parsed.map { it.type },
        )
        assertEquals(listOf("Root bullet", "Child bullet", "Second root", "Root number", "Child number"), parsed.map { it.text.text })
        assertEquals(listOf(0, 0, 0, 0, 0), parsed.map { it.indentLevel })
    }

    @Test
    fun parseHtmlSupportsTodoListClassAndCheckedAttributes() {
        val parsed = parseHtmlToRichTextBlocks(
            """
                <ul class="todo">
                    <li>Open task</li>
                    <li checked="true">Done task</li>
                    <li checked="false">Explicit open task</li>
                </ul>
            """.trimIndent(),
        )

        assertEquals(listOf(RichTextBlockType.Todo, RichTextBlockType.Todo, RichTextBlockType.Todo), parsed.map { it.type })
        assertEquals(listOf(false, true, false), parsed.map { it.isChecked })
        assertEquals(listOf("Open task", "Done task", "Explicit open task"), parsed.map { it.text.text })
    }

    @Test
    fun stateIgnoresLegacyIndentAttributesWhenReorderingAndDeleting() {
        val state = QuataRichTextEditorState(
            """
                <p>Intro</p>
                <ul>
                    <li data-quata-indent="0">Parent</li>
                    <li data-quata-indent="1">Child</li>
                    <li data-quata-indent="0">Sibling</li>
                </ul>
                <p>Outro</p>
            """.trimIndent(),
        )

        val parentId = state.blocks[1].id
        val siblingId = state.blocks[3].id
        state.selectBlock(parentId)
        state.moveBlockDown(parentId)

        assertEquals(listOf("Intro", "Child", "Parent", "Sibling", "Outro"), state.blocks.map { it.text.text })
        assertEquals(listOf(0, 0, 0, 0, 0), state.blocks.map { it.indentLevel })

        state.selectBlock(state.blocks[2].id)
        state.selectBlock(state.blocks[4].id, clearSelection = false, useShift = true)
        state.removeSelectedBlocks()

        assertEquals(listOf("Intro", "Child"), state.blocks.map { it.text.text })
    }

    @Test
    fun stateSplitsBlocksAndPreservesInlineStylesInGeneratedHtml() {
        val state = QuataRichTextEditorState("<p>Hello styled</p>")
        val block = state.blocks.first()
        block.text = TextFieldValue("Hello styled", selection = TextRange(0, 5))
        state.toggleBold()
        state.updateBlockText(
            block.id,
            block.text.copy(selection = TextRange(5)),
            TextFieldValue("Hello\nstyled", selection = TextRange(6)),
        )

        assertEquals(listOf("Hello", "styled"), state.blocks.map { it.text.text })
        assertEquals(state.blocks[1].id, state.selectedBlockId.value)
        assertEquals(listOf(state.blocks[1].id), state.selectedBlockIds.toList())
        assertEquals(listOf(QuataTextSpan(0, 5, QuataSpanStyle.Bold)), state.blocks.first().spans)
        assertTrue(state.html.contains("<b>Hello</b>"))
        assertTrue(state.html.contains("<p>styled</p>"))
    }

    @Test
    fun enterAtStartCreatesEmptyBlockBelowWithoutMovingCurrentText() {
        val state = QuataRichTextEditorState("<p>Keep me here</p>")
        val block = state.blocks.first()
        block.text = TextFieldValue("Keep me here", selection = TextRange(0))

        assertTrue(state.splitBlockAtSelection(block.id))

        assertEquals(listOf("Keep me here", ""), state.blocks.map { it.text.text })
        assertEquals(state.blocks[1].id, state.selectedBlockId.value)
        assertEquals(listOf(state.blocks[1].id), state.selectedBlockIds.toList())
    }

    @Test
    fun newlineAtStartFromTextFieldCreatesEmptyBlockBelowWithoutMovingCurrentText() {
        val state = QuataRichTextEditorState("<p>Keep me here</p>")
        val block = state.blocks.first()

        state.updateBlockText(
            block.id,
            TextFieldValue("Keep me here", selection = TextRange(0)),
            TextFieldValue("\nKeep me here", selection = TextRange(1)),
        )

        assertEquals(listOf("Keep me here", ""), state.blocks.map { it.text.text })
        assertEquals(state.blocks[1].id, state.selectedBlockId.value)
        assertEquals(listOf(state.blocks[1].id), state.selectedBlockIds.toList())
    }
}
