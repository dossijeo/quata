package com.quata.web

import com.quata.core.ui.components.QuataDocumentViewerStatusStrings

internal fun webDocumentViewerStatusStrings(languageTags: List<String>): QuataDocumentViewerStatusStrings = when {
    languageTags.any { it.startsWith("es", ignoreCase = true) } -> QuataDocumentViewerStatusStrings(
        openingTitle = "Abriendo documento",
        openedTitle = "Documento abierto",
        failedTitle = "Documento no disponible",
        openingMessage = "Abriendo el visor de documentos.",
        openedMessage = "El visor de documentos está abierto.",
        cancelledMessage = "La apertura se ha cancelado.",
        unsupportedFormatMessage = "Este formato de documento aún no está disponible.",
        platformUnsupportedMessage = "El visor de documentos no está disponible en este navegador.",
        openFailedMessage = "No se ha podido abrir el documento.",
        closeLabel = "Cerrar",
    )
    languageTags.any { it.startsWith("fr", ignoreCase = true) } -> QuataDocumentViewerStatusStrings(
        openingTitle = "Ouverture du document",
        openedTitle = "Document ouvert",
        failedTitle = "Document indisponible",
        openingMessage = "Ouverture de la visionneuse de documents.",
        openedMessage = "La visionneuse de documents est ouverte.",
        cancelledMessage = "L'ouverture a été annulée.",
        unsupportedFormatMessage = "Ce format de document n'est pas encore pris en charge.",
        platformUnsupportedMessage = "La visionneuse de documents n'est pas disponible dans ce navigateur.",
        openFailedMessage = "Le document n'a pas pu être ouvert.",
        closeLabel = "Fermer",
    )
    else -> QuataDocumentViewerStatusStrings(
        openingTitle = "Opening document",
        openedTitle = "Document opened",
        failedTitle = "Document unavailable",
        openingMessage = "Opening the document viewer.",
        openedMessage = "The document viewer is open.",
        cancelledMessage = "Opening was cancelled.",
        unsupportedFormatMessage = "This document format is not supported yet.",
        platformUnsupportedMessage = "Document viewing is not available in this browser.",
        openFailedMessage = "The document could not be opened.",
        closeLabel = "Close",
    )
}
