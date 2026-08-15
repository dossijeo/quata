@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.WebElementView
import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLInputElement

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WebNativeInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit = {},
    name: String,
    modifier: Modifier,
    inputType: String = "tel",
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .requiredHeightIn(min = 62.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon?.let {
            Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) { it() }
        }
        WebElementView(
            factory = {
                (document.createElement("input") as HTMLInputElement).apply {
                    type = inputType
                    setAttribute("aria-label", name)
                    style.width = "100%"
                    style.height = "100%"
                    style.boxSizing = "border-box"
                }
            },
            update = { input ->
                if (input.value != value) input.value = value
                input.oninput = { onValueChange(input.value) }
                input.onkeydown = { event ->
                    if (event.key == "Enter") {
                        onSubmit()
                        event.preventDefault()
                    }
                    null
                }
            },
            onRelease = { input ->
                input.oninput = null
                input.onkeydown = null
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(vertical = 3.dp),
        )
        trailingIcon?.let {
            Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) { it() }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WebNativeButton(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier, selected: Boolean = false) {
    WebElementView(
        factory = { (document.createElement("button") as HTMLButtonElement).apply {
            setAttribute("aria-label", label)
            style.width = "100%"; style.height = "100%"; style.setProperty("pointer-events", "auto"); style.position = "relative"; style.zIndex = "1"
        } },
        update = { button -> button.textContent = label; button.setAttribute("aria-label", label); if (selected) button.setAttribute("aria-current", "page") else button.removeAttribute("aria-current"); button.disabled = !enabled; button.setAttribute("aria-disabled", (!enabled).toString()); button.onclick = { onClick(); null } },
        onRelease = { button -> button.onclick = null },
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WebNativeIconButton(label: String, text: String, onClick: () -> Unit, modifier: Modifier) {
    WebElementView(
        factory = {
            (document.createElement("button") as HTMLButtonElement).apply {
                setAttribute("aria-label", label)
                style.width = "100%"
                style.height = "100%"
                style.border = "0"
                style.borderRadius = "999px"
                style.background = "rgba(255, 246, 238, 0.92)"
                style.color = "#1f2933"
                style.font = "700 22px system-ui, sans-serif"
                style.cursor = "pointer"
                style.setProperty("pointer-events", "auto")
                style.position = "relative"
                style.zIndex = "9999"
                onclick = { onClick(); null }
            }
        },
        update = { button ->
            button.textContent = text
            button.setAttribute("aria-label", label)
            button.onclick = { onClick(); null }
        },
        onRelease = { button -> button.onclick = null },
        modifier = modifier,
    )
}
