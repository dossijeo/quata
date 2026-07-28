@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.WebElementView
import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLInputElement

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WebNativeInput(value: String, onValueChange: (String) -> Unit, name: String, modifier: Modifier, inputType: String = "tel") {
    WebElementView(
        factory = { (document.createElement("input") as HTMLInputElement).apply {
            type = inputType; setAttribute("aria-label", name)
            style.width = "100%"; style.height = "100%"; style.boxSizing = "border-box"
        } },
        update = { input -> if (input.value != value) input.value = value; input.oninput = { onValueChange(input.value) } },
        onRelease = { input -> input.oninput = null },
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WebNativeButton(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    WebElementView(
        factory = { (document.createElement("button") as HTMLButtonElement).apply { setAttribute("aria-label", label); style.width = "100%"; style.height = "100%" } },
        update = { button -> button.textContent = label; button.setAttribute("aria-label", label); button.disabled = !enabled; button.onclick = { onClick(); null } },
        onRelease = { button -> button.onclick = null },
        modifier = modifier.fillMaxWidth(),
    )
}
