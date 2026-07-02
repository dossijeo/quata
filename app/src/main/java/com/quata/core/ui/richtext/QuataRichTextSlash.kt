package com.quata.core.ui.richtext

internal data class RichTextBlockCommand(
    val label: String,
    val type: RichTextBlockType,
    val keywords: List<String>,
    val id: String = type.name,
)

internal class QuataSlashCommandRegistry(
    commands: List<RichTextBlockCommand> = defaultCommands,
) {
    private val commands = commands

    fun filter(query: String): List<RichTextBlockCommand> {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return commands
        return commands.filter { command ->
            command.label.lowercase().contains(normalized) ||
                command.id.lowercase().contains(normalized) ||
                command.keywords.any { keyword -> keyword.lowercase().contains(normalized) }
        }
    }

    companion object {
        val defaultCommands = listOf(
            RichTextBlockCommand("Texto", RichTextBlockType.Paragraph, listOf("p", "paragraph", "texto")),
            RichTextBlockCommand("Titulo 1", RichTextBlockType.Heading1, listOf("h1", "heading 1", "title 1")),
            RichTextBlockCommand("Titulo 2", RichTextBlockType.Heading2, listOf("h2", "heading 2", "title 2")),
            RichTextBlockCommand("Titulo 3", RichTextBlockType.Heading3, listOf("h3", "heading 3", "title 3")),
            RichTextBlockCommand("Titulo 4", RichTextBlockType.Heading4, listOf("h4", "heading 4", "title 4")),
            RichTextBlockCommand("Titulo 5", RichTextBlockType.Heading5, listOf("h5", "heading 5", "title 5")),
            RichTextBlockCommand("Titulo 6", RichTextBlockType.Heading6, listOf("h6", "heading 6", "title 6")),
            RichTextBlockCommand("Lista", RichTextBlockType.Bullet, listOf("bullet", "lista")),
            RichTextBlockCommand("Numerada", RichTextBlockType.Numbered, listOf("numbered", "ordered")),
            RichTextBlockCommand("Tarea", RichTextBlockType.Todo, listOf("todo", "check")),
            RichTextBlockCommand("Cita", RichTextBlockType.Quote, listOf("quote", "cita")),
            RichTextBlockCommand("Informacion", RichTextBlockType.Info, listOf("info", "informacion")),
            RichTextBlockCommand("Codigo", RichTextBlockType.Code, listOf("code", "codigo")),
            RichTextBlockCommand("Separador", RichTextBlockType.Divider, listOf("divider", "hr")),
        )
    }
}

internal class QuataSlashCommandExecutor(
    private val state: QuataRichTextEditorState,
) {
    fun execute(blockId: String, command: RichTextBlockCommand, fromSlashSession: Boolean): Boolean {
        return if (fromSlashSession) {
            state.executeSlashCommand(command.type)
        } else {
            state.setBlockTypeFromSlash(blockId, command.type)
            true
        }
    }
}
