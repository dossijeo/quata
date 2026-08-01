package com.quata.feature.chat.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks Web/iOS to the same deployed RPC names and payload fields used by Android. */
class ChatPostgrestMutationParityStaticTest {
    private val root = generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun portableRepositoryImplementsEveryAndroidChatMutationWithoutUnsupportedFallbacks() {
        val portable = source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/data/PostgrestChatRepository.kt")
        val androidApi = source("app/src/main/java/com/quata/data/supabase/SupabaseCommunityApi.kt")
        val androidModels = source("app/src/main/java/com/quata/data/supabase/SupabaseModels.kt")
        val rpcNames = listOf(
            "quata_chat_match_registered_contacts",
            "quata_chat_send_sos",
            "quata_chat_open_community_thread",
            "quata_chat_set_member_invites_enabled",
            "quata_chat_add_participants",
            "quata_chat_promote_moderator",
            "quata_chat_demote_moderator",
            "quata_chat_remove_participant",
            "quata_chat_block_participant",
            "quata_ugc_report",
            "quata_chat_leave_thread",
            "quata_chat_delete_thread",
            "quata_chat_restore_thread",
            "quata_chat_edit_message",
            "quata_chat_delete_messages",
            "quata_chat_set_favorite",
            "quata_chat_forward_message",
            "quata_chat_cleanup_empty_private_thread",
        )
        rpcNames.forEach { rpc ->
            assertTrue("Portable repository missing $rpc", "\"$rpc\"" in portable)
            assertTrue("Android API missing $rpc", "\"$rpc\"" in androidApi)
        }
        listOf(
            "p_actor_profile_id", "p_thread_id", "p_message_id", "p_message_ids",
            "p_participant_profile_ids", "p_profile_id", "p_thread_ids", "p_enabled",
            "p_target_type", "p_target_id", "p_reason", "p_details",
        ).forEach { field ->
            assertTrue("Portable payload missing $field", "\"$field\"" in portable)
            assertTrue("Android payload missing $field", "val $field" in androidModels)
        }
        assertFalse("web_chat_mutation_not_implemented" in portable)
        assertFalse("unsupportedMutation" in portable)
    }

    private fun source(path: String): String = File(root, path).readText()
}
