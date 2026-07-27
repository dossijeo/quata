package com.quata.core.accessibility

/**
 * Platform-neutral copy for the semantics of the controls that can create or discard user work.
 * UI implementations still provide the platform accessibility role; this catalogue keeps the
 * announced name, state, and focus wording consistent between Web and iOS.
 */
data class CriticalControlAccessibilityCopy(
    val name: String,
    val role: String,
    val selected: String,
    val notSelected: String,
    val enabled: String,
    val disabled: String,
    val focused: String,
    val notFocused: String,
) {
    fun state(isSelected: Boolean, isEnabled: Boolean): String = listOf(
        if (isSelected) selected else notSelected,
        if (isEnabled) enabled else disabled,
    ).joinToString(", ")

    fun focus(isFocused: Boolean): String = if (isFocused) focused else notFocused
}

data class CriticalControlsAccessibilityCopy(
    val composerType: CriticalControlAccessibilityCopy,
    val publish: CriticalControlAccessibilityCopy,
    val back: CriticalControlAccessibilityCopy,
    val composer: ComposerLocalizedCopy,
)

data class ComposerLocalizedCopy(
    val title: String,
    val textType: String,
    val imageType: String,
    val videoType: String,
    val contentTitle: String,
    val placeholder: String,
    val characters: (Int) -> String,
    val preview: String,
    val readMore: String,
    val close: String,
    val publish: String,
    val publishing: String,
    val backToFeed: String,
    val chooseImage: String,
    val takePhoto: String,
    val imagePreview: String,
    val selectedImage: (String) -> String,
    val imageUnavailable: String,
    val chooseVideo: String,
    val recordVideoUnavailable: String,
    val videoPreview: String,
    val selectedVideo: (String) -> String,
    val videoUnavailable: String,
)

enum class AccessibilityLocale { Spanish, English }

object CriticalControlsAccessibilityCatalog {
    fun forLocale(locale: AccessibilityLocale): CriticalControlsAccessibilityCopy = when (locale) {
        AccessibilityLocale.Spanish -> SpanishCriticalControlsAccessibility
        AccessibilityLocale.English -> EnglishCriticalControlsAccessibility
    }

    /** Spanish is the established fallback for unknown and absent language tags. */
    fun forLanguageTag(languageTag: String?): CriticalControlsAccessibilityCopy = when (
        languageTag?.trim()?.substringBefore('-')?.lowercase()
    ) {
        "en" -> EnglishCriticalControlsAccessibility
        else -> SpanishCriticalControlsAccessibility
    }
}

val SpanishCriticalControlsAccessibility = CriticalControlsAccessibilityCopy(
    composerType = CriticalControlAccessibilityCopy(
        name = "Tipo de publicación", role = "botón", selected = "seleccionado", notSelected = "no seleccionado",
        enabled = "disponible", disabled = "no disponible", focused = "con foco", notFocused = "sin foco",
    ),
    publish = CriticalControlAccessibilityCopy(
        name = "Publicar", role = "botón", selected = "no seleccionado", notSelected = "no seleccionado",
        enabled = "disponible", disabled = "publicando", focused = "con foco", notFocused = "sin foco",
    ),
    back = CriticalControlAccessibilityCopy(
        name = "Volver", role = "botón", selected = "no seleccionado", notSelected = "no seleccionado",
        enabled = "disponible", disabled = "no disponible", focused = "con foco", notFocused = "sin foco",
    ),
    composer = ComposerLocalizedCopy(
        title = "Crear publicación", textType = "Texto", imageType = "Imagen", videoType = "Vídeo",
        contentTitle = "Tu publicación", placeholder = "Escribe algo…",
        characters = { count -> if (count == 1) "1 carácter" else "$count caracteres" },
        preview = "Vista previa", readMore = "Leer más", close = "Cerrar", publish = "Publicar",
        publishing = "Publicando…", backToFeed = "Volver al feed", chooseImage = "Elegir imagen",
        takePhoto = "Tomar foto", imagePreview = "Vista previa de imagen",
        selectedImage = { "Imagen seleccionada: $it" },
        imageUnavailable = "El renderizado y la edición de bitmap aún no están disponibles en iOS.",
        chooseVideo = "Elegir vídeo", recordVideoUnavailable = "Grabar vídeo no disponible",
        videoPreview = "Vista previa de vídeo", selectedVideo = { "Vídeo seleccionado: $it" },
        videoUnavailable = "La reproducción, edición y exportación de vídeo aún no están disponibles en iOS.",
    ),
)

val EnglishCriticalControlsAccessibility = CriticalControlsAccessibilityCopy(
    composerType = CriticalControlAccessibilityCopy(
        name = "Post type", role = "button", selected = "selected", notSelected = "not selected",
        enabled = "available", disabled = "unavailable", focused = "focused", notFocused = "not focused",
    ),
    publish = CriticalControlAccessibilityCopy(
        name = "Publish", role = "button", selected = "not selected", notSelected = "not selected",
        enabled = "available", disabled = "publishing", focused = "focused", notFocused = "not focused",
    ),
    back = CriticalControlAccessibilityCopy(
        name = "Back", role = "button", selected = "not selected", notSelected = "not selected",
        enabled = "available", disabled = "unavailable", focused = "focused", notFocused = "not focused",
    ),
    composer = ComposerLocalizedCopy(
        title = "Create post", textType = "Text", imageType = "Image", videoType = "Video",
        contentTitle = "Your post", placeholder = "Write something…",
        characters = { count -> if (count == 1) "1 character" else "$count characters" },
        preview = "Preview", readMore = "Read more", close = "Close", publish = "Publish",
        publishing = "Publishing…", backToFeed = "Back to feed", chooseImage = "Choose image",
        takePhoto = "Take photo", imagePreview = "Image preview",
        selectedImage = { "Selected image: $it" },
        imageUnavailable = "Bitmap rendering and editing are not available on iOS yet.",
        chooseVideo = "Choose video", recordVideoUnavailable = "Video recording unavailable",
        videoPreview = "Video preview", selectedVideo = { "Selected video: $it" },
        videoUnavailable = "Video playback, editing, and export are not available on iOS yet.",
    ),
)
