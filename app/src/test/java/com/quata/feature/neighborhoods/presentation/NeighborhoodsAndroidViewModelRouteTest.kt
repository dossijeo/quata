package com.quata.feature.neighborhoods.presentation

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NeighborhoodsAndroidViewModelRouteTest {
    @Test
    fun restoresOnlyForTheExactActorAndOrigin() {
        val state = SavedStateHandle(
            mapOf(
                "quata.profile.route.ids" to arrayListOf("a", "b"),
                "quata.profile.route.actor" to "actor-a",
                "quata.profile.route.origin" to "feed",
            ),
        )

        assertEquals(
            listOf("a", "b"),
            NeighborhoodsAndroidViewModel.restoredProfileRoute(state, "actor-a", "feed"),
        )
    }

    @Test
    fun rejectsAndConsumesARouteFromAnotherOrigin() {
        val state = SavedStateHandle(
            mapOf(
                "quata.profile.route.ids" to arrayListOf("a", "b"),
                "quata.profile.route.actor" to "actor-a",
                "quata.profile.route.origin" to "feed",
            ),
        )

        assertEquals(
            emptyList<String>(),
            NeighborhoodsAndroidViewModel.restoredProfileRoute(state, "actor-a", "official"),
        )
        assertNull(state.get<ArrayList<String>>("quata.profile.route.ids"))
        assertEquals("official", state.get<String>("quata.profile.route.origin"))
    }

    @Test
    fun rejectsAndConsumesARouteFromAnotherConversation() {
        val chatA = "chat/{conversationId}:6:chat-a"
        val chatB = "chat/{conversationId}:6:chat-b"
        val state = SavedStateHandle(
            mapOf(
                "quata.profile.route.ids" to arrayListOf("a", "b"),
                "quata.profile.route.actor" to "actor-a",
                "quata.profile.route.origin" to chatA,
            ),
        )

        assertEquals(
            emptyList<String>(),
            NeighborhoodsAndroidViewModel.restoredProfileRoute(state, "actor-a", chatB),
        )
        assertNull(state.get<ArrayList<String>>("quata.profile.route.ids"))
        assertEquals(chatB, state.get<String>("quata.profile.route.origin"))
    }
}
