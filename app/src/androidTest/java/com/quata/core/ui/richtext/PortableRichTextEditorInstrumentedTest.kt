package com.quata.core.ui.richtext

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.designsystem.theme.QuataThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortableRichTextEditorInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun toolbarActionsExposeSelectionAndChangeSerializedBlockType() {
        var latestHtml = ""
        compose.setContent {
            QuataTheme(mode = QuataThemeMode.Light) {
                QuataPortableRichTextEditorBox(
                    initialHtml = "<p>Alpha</p>",
                    placeholder = "Body",
                    onHtmlChange = { latestHtml = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        compose.onNodeWithTag("quata-portable-rich-text-editor", useUnmergedTree = true)
            .assertIsDisplayed()
        compose.onNodeWithTag("$QuataPortableRichTextToolbarTestTagPrefix-bold", useUnmergedTree = true)
            .performScrollTo()
            .assertIsNotSelected()
            .performClick()
            .assertIsSelected()
            .performClick()
            .assertIsNotSelected()

        compose.onNodeWithTag("$QuataPortableRichTextToolbarTestTagPrefix-heading", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        compose.onNodeWithTag("quata-portable-rich-text-heading-dialog", useUnmergedTree = true)
            .assertIsDisplayed()
        compose.onNodeWithTag("quata-portable-rich-text-heading-2", useUnmergedTree = true)
            .performClick()

        compose.waitUntil(5_000) { latestHtml.contains("<h2>Alpha</h2>") }
        assertTrue(latestHtml.contains("<h2>Alpha</h2>"))
        compose.onNodeWithTag("$QuataPortableRichTextToolbarTestTagPrefix-heading", useUnmergedTree = true)
            .assertIsSelected()
    }
}
