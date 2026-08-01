package com.quata.core.ui.components

import com.quata.core.navigation.AppDestinations
import kotlin.test.Test
import kotlin.test.assertEquals

class QuataPrimaryNavigationContractTest {
    private val labels = QuataPrimaryNavigationLabels("Qüata", "Chats", "Oficial", "Feed", "Cuenta")

    @Test
    fun composerModeReplacesOnlyTheCentralOfficialDestination() {
        val items = labels.items(QuataPrimaryNavigationMode.Composer("composer", "Publicar"))

        assertEquals(listOf("neighborhoods", "conversations", "composer", "feed", "profile"), items.map { it.route })
        assertEquals(listOf("Qüata", "Chats", "Publicar", "Feed", "Cuenta"), items.map { it.label })
    }

    @Test
    fun defaultModePreservesTheCanonicalPrimaryDestinations() {
        assertEquals(
            listOf(
                AppDestinations.Neighborhoods.route,
                AppDestinations.Conversations.route,
                AppDestinations.Official.route,
                AppDestinations.Feed.route,
                AppDestinations.Profile.route,
            ),
            labels.items().map { it.route },
        )
    }
}
