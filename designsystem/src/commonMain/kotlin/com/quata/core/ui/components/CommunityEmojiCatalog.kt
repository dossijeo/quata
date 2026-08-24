package com.quata.core.ui.components

/** Localized labels are injected, while the product's exact catalog is shared by Android, Web and iOS. */
data class CommunityEmojiLabels(
    val recent: String = "Recientes", val frequent: String = "Frecuentes", val gestures: String = "Gestos",
    val people: String = "Personas", val animalsNature: String = "Animales y naturaleza",
    val foodDrink: String = "Comida y bebida", val objectsSymbols: String = "Objetos y símbolos", val flags: String = "Banderas",
    val empty: String = "No hay emojis disponibles.",
    val retry: String = "Reintentar",
)

sealed interface CommunityEmojiCatalogState {
    data class Available(val sections: List<QuataEmojiSection>) : CommunityEmojiCatalogState
    data class Unavailable(val message: String, val onRetry: (() -> Unit)? = null) : CommunityEmojiCatalogState
}

fun communityEmojiCatalogState(
    labels: CommunityEmojiLabels = CommunityEmojiLabels(),
    onRetry: (() -> Unit)? = null,
    atlasCellCountResolver: (String) -> Int = { communityEmojiAtlas(it).emojiCount },
): CommunityEmojiCatalogState = try {
    val sections = communityEmojiSections(labels)
    sections.forEach { section ->
        val atlasCellCount = atlasCellCountResolver(section.key)
        require(atlasCellCount >= section.emojis.size) {
            "Emoji atlas ${section.key} exposes $atlasCellCount cells for ${section.emojis.size} emojis"
        }
    }
    CommunityEmojiCatalogState.Available(sections)
} catch (_: Throwable) {
    CommunityEmojiCatalogState.Unavailable(labels.empty, onRetry)
}

fun communityEmojiSections(labels: CommunityEmojiLabels = CommunityEmojiLabels()): List<QuataEmojiSection> = listOf(
    QuataEmojiSection("recent", labels.recent, frequentEmojis.take(24)),
    QuataEmojiSection("frequent", labels.frequent, frequentEmojis),
    QuataEmojiSection("gestures", labels.gestures, listOf("👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤌", "🤏", "✌️", "🤞", "🫶", "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "☝️", "👍", "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲", "🙏", "🤝", "💪", "🦾", "🫵")),
    QuataEmojiSection("people", labels.people, listOf("👶", "🧒", "👦", "👧", "🧑", "👨", "👩", "👱", "👴", "👵", "🧔", "👮", "🕵️", "💂", "👷", "🤴", "👸", "🧕", "👨‍⚕️", "👩‍⚕️", "👨‍🍳", "👩‍🍳", "👨‍🎓", "👩‍🎓", "👨‍🏫", "👩‍🏫", "👨‍💻", "👩‍💻", "👨‍🎤", "👩‍🎤", "🧘", "🏃", "🚶", "🧍")),
    QuataEmojiSection("animals_nature", labels.animalsNature, listOf("🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐻‍❄️", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🙈", "🙉", "🙊", "🐔", "🐧", "🐦", "🐤", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗", "🐴", "🦄", "🐝", "🪲", "🦋", "🐢", "🐍", "🦎", "🦂", "🦀", "🐙", "🐠", "🐬", "🦭", "🌵", "🌴", "🌲", "🌳", "🌸", "🌼", "🌻", "🌞", "🌙", "⭐", "⚡", "☔", "🌈", "🔥", "❄️")),
    QuataEmojiSection("food_drink", labels.foodDrink, listOf("🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐", "🍒", "🥭", "🍍", "🥥", "🥑", "🍅", "🍆", "🥔", "🥕", "🌽", "🌶️", "🥒", "🥬", "🥦", "🧄", "🧅", "🍄", "🥜", "🍞", "🥐", "🥖", "🧀", "🍳", "🥓", "🍔", "🍟", "🍕", "🌭", "🥪", "🌮", "🌯", "🥗", "🍝", "🍜", "🍣", "🍤", "🍩", "🍪", "🎂", "🍫", "☕", "🍵", "🧃", "🥤", "🍺", "🍷", "🍾")),
    QuataEmojiSection("objects_symbols", labels.objectsSymbols, listOf("📱", "💻", "⌚", "📷", "🎥", "📺", "🎮", "🎧", "🧠", "🫀", "💡", "🔦", "📚", "✏️", "📌", "📎", "✂️", "🔒", "🔑", "🪙", "💸", "💰", "🧾", "💎", "⚙️", "🧲", "🧪", "🧬", "🚬", "⚰️", "🛒", "🧳", "🎁", "🎈", "🎉", "🏆", "⚽", "🏀", "🎯", "🚗", "✈️", "🚀", "🛸", "⏰", "📍", "✅", "❌", "⚠️", "❓", "💬", "🗯️")),
    QuataEmojiSection("flags", labels.flags, listOf("ES", "US", "GB", "FR", "DE", "IT", "PT", "BR", "AR", "CO", "MX", "EC", "PE", "CL", "UY", "PY", "BO", "VE", "DO", "CU", "MA", "DZ", "EG", "NG", "ZA", "CM", "GA", "GQ", "JP", "KR", "CN", "IN", "AU", "CA").map(::flagEmoji)),
)

private val frequentEmojis = listOf("😀", "😁", "😂", "🤣", "😊", "😍", "🥰", "😘", "😎", "🤩", "😇", "🙂", "😉", "😌", "🤗", "😴", "🤔", "😅", "😢", "😭", "😤", "😡", "🤯", "🥳", "🤝", "👏", "🙌", "👍", "👎", "🙏", "💪", "🔥", "✨", "⭐", "💯", "❤️", "💙", "💚", "💜", "🖤", "🤍", "🤎", "💔", "❤️‍🔥", "❤️‍🩹")

private fun flagEmoji(countryCode: String): String = countryCode.map { (0x1F1E6 + it.code - 'A'.code).toCharString() }.joinToString("")
private fun Int.toCharString(): String = if (this <= 0xFFFF) toChar().toString() else charArrayOf(((this - 0x10000) / 0x400 + 0xD800).toChar(), ((this - 0x10000) % 0x400 + 0xDC00).toChar()).concatToString()
