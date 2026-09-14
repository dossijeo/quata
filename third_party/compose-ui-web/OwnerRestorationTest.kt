package androidx.compose.ui.platform.a11y

import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.w3c.dom.HTMLElement
import androidx.compose.ui.currentTimeMillis
import kotlinx.browser.window
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.accessibility.ComposeWebSemanticsListener
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.jetbrains.skia.Surface

private suspend fun awaitOwnerStage(stage: String, condition: () -> Boolean) {
    println("OWNER_STAGE $stage")
    val deadline = currentTimeMillis() + 3000
    while (currentTimeMillis() < deadline) {
        if (condition()) return
        suspendCoroutine<Unit> { continuation -> window.requestAnimationFrame { continuation.resume(Unit) } }
    }
    error("Owner stage not reached: $stage")
}

/** Regression for the real browser semantics listener across temporary layers. */
class OwnerRestorationTest : OnCanvasTests {
    @OptIn(InternalComposeUiApi::class)
    @Test
    fun clearsLastOwnerAndAcceptsNewRoot() = runApplicationTest {
        val container = document.createElement("div") as HTMLElement
        document.body!!.appendChild(container)
        val scope = MainScope()
        val listener = ComposeWebSemanticsListener(scope, container)
        val platform = object : PlatformContext by PlatformContext.Empty() {
            override val semanticsOwnerListener = listener
        }
        val surface = Surface.makeRasterN32Premul(100, 100)
        var active: ComposeScene? = null
        try {
            for (id in listOf("first-root", "next-root")) {
                val scene = CanvasLayersComposeScene(size = IntSize(100, 100), platformContext = platform)
                active = scene
                scene.setContent { Box(Modifier.size(40.dp).testTag(id)) }
                scene.render(surface.canvas.asComposeCanvas(), 1)
                awaitOwnerStage("attached_$id") { container.querySelector("#$id") != null }
                scene.close()
                active = null
                awaitOwnerStage("cleared_$id") { container.childElementCount == 0 }
            }
        } finally {
            active?.close()
            scope.cancel()
            surface.close()
            container.remove()
        }
    }

    @Test
    fun restoresRootAndAccessibleActionAfterDialog() = runApplicationTest {
        var showDialog by mutableStateOf(false)
        var clicks = 0
        createComposeWindow {
            Button(onClick = { clicks++ }, modifier = Modifier.testTag("owner-root")) { Text("Root") }
            if (showDialog) Dialog(onDismissRequest = { showDialog = false }) {
                Button(onClick = {}, modifier = Modifier.testTag("owner-dialog")) { Text("Dialog") }
            }
        }
        val container = assertNotNull(getA11YContainer())
        awaitOwnerStage("initial_root") { container.querySelector("#owner-root") != null }
        repeat(2) {
            showDialog = true
            awaitOwnerStage("dialog_visible_$it") { container.querySelector("#owner-dialog") != null }
            assertNotNull(container.querySelector("#owner-dialog"))
            showDialog = false
            awaitOwnerStage("root_restored_$it") { container.querySelector("#owner-root") != null && container.querySelector("#owner-dialog") == null }
            assertNull(container.querySelector("#owner-dialog"))
            val button = assertNotNull(container.querySelector("#owner-root")) as HTMLElement
            button.click()
            assertEquals(it + 1, clicks)
        }
    }

    @Test
    fun removingIntermediateLayerPreservesTopThenRestoresRoot() = runApplicationTest {
        var middle by mutableStateOf(false)
        var top by mutableStateOf(false)
        var middleDisposed = false
        var topText by mutableStateOf("Top0")
        createComposeWindow {
            Text("Root", Modifier.testTag("owner-root"))
            if (middle) Dialog(onDismissRequest = {}) {
                DisposableEffect(Unit) { onDispose { middleDisposed = true } }
                Text("Middle", Modifier.testTag("owner-middle"))
            }
            if (top) Dialog(onDismissRequest = {}) { Text(topText, Modifier.testTag("owner-top")) }
        }
        val container = assertNotNull(getA11YContainer())
        awaitOwnerStage("nested_initial") { container.querySelector("#owner-root") != null }
        middle = true
        awaitOwnerStage("middle_visible") { container.querySelector("#owner-middle") != null }
        assertNotNull(container.querySelector("#owner-middle"))
        top = true
        awaitOwnerStage("top_visible") { container.querySelector("#owner-top") != null }
        assertNotNull(container.querySelector("#owner-top"))
        middle = false
        awaitOwnerStage("middle_disposed") { middleDisposed }
        topText = "Top1"
        awaitOwnerStage("top_still_updates") { container.querySelector("#owner-top")?.textContent == "Top1" }
        assertNotNull(container.querySelector("#owner-top"))
        assertNull(container.querySelector("#owner-middle"))
        top = false
        awaitOwnerStage("nested_root_restored") { container.querySelector("#owner-root") != null && container.querySelector("#owner-top") == null }
        assertNotNull(container.querySelector("#owner-root"))
        assertNull(container.querySelector("#owner-top"))
    }
}
