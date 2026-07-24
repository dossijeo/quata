package com.quata.feature.postcomposer.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.feature.postcomposer.domain.PostComposerRepository
import platform.UIKit.UIViewController

/** iOS composition boundary: media capture, export and navigation remain injected by Swift. */
class IosComposerHostDependencies(
    val repository: PostComposerRepository,
    val onOpenGallery: () -> Unit,
    val onOpenCamera: () -> Unit,
    val onPreview: () -> Unit,
    val onExport: () -> Unit,
    val onClose: () -> Unit,
    val content: @Composable (IosComposerHostDependencies) -> Unit = {
        ComposerEmptyPreviewContent("Crear publicación", "iOS", "El launcher aporta galería, cámara y exportación.")
    },
)

/** Stable Swift-exported UIViewController factory for shared Composer forms/previews. */
fun QuataComposerViewController(dependencies: IosComposerHostDependencies): UIViewController = ComposeUIViewController {
    QuataTheme { dependencies.content(dependencies) }
}
