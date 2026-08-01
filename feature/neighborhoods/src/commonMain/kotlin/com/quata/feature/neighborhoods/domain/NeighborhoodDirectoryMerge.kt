package com.quata.feature.neighborhoods.domain

data class NeighborhoodWallSnapshot(val id: String?, val name: String, val normalizedName: String?, val messageCount: Int, val lastMessageAtMillis: Long?)

fun mergeNeighborhoodDirectory(users: List<NeighborhoodUser>, walls: List<NeighborhoodWallSnapshot>): List<NeighborhoodCommunity> {
    fun String.key() = trim().lowercase().replace(Regex("\\s+"), " ")
    val grouped = users.filter { it.neighborhood.isNotBlank() }.groupBy { it.neighborhood.key() }
    val wallsByKey = walls.associateBy { (it.normalizedName ?: it.name).key() }
    return (grouped.keys + wallsByKey.keys).filter(String::isNotBlank).map { key ->
        val wall = wallsByKey[key]
        val members = grouped[key].orEmpty().distinctBy(NeighborhoodUser::id).sortedBy { it.displayName.lowercase() }
        NeighborhoodCommunity(
            name = wall?.name?.takeIf(String::isNotBlank) ?: members.firstOrNull()?.neighborhood ?: key,
            users = members,
            conversationId = wall?.id?.let { "wall:$it" },
            lastMessagePreview = null,
            lastMessageAtMillis = wall?.lastMessageAtMillis,
            messageCount = wall?.messageCount ?: 0,
        )
    }.sortedWith(compareByDescending<NeighborhoodCommunity> { it.lastMessageAtMillis ?: 0L }.thenBy { it.name.lowercase() })
}
