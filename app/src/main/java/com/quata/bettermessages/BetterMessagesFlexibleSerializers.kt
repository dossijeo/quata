package com.quata.bettermessages

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

private class FlexibleListSerializer<T>(
    private val itemSerializer: KSerializer<T>
) : KSerializer<List<T>> {
    private val listSerializer = ListSerializer(itemSerializer)

    override val descriptor: SerialDescriptor = listSerializer.descriptor

    override fun deserialize(decoder: Decoder): List<T> {
        val jsonDecoder = decoder as? JsonDecoder ?: return listSerializer.deserialize(decoder)
        return decodeFlexibleList(jsonDecoder.decodeJsonElement(), itemSerializer)
    }

    override fun serialize(encoder: Encoder, value: List<T>) {
        listSerializer.serialize(encoder, value)
    }
}

internal object FlexibleBmThreadListSerializer :
    KSerializer<List<BmThread>> by FlexibleListSerializer(BmThread.serializer())

internal object FlexibleBmUserListSerializer :
    KSerializer<List<BmUser>> by FlexibleListSerializer(BmUser.serializer())

internal object FlexibleBmMessageListSerializer :
    KSerializer<List<BmMessage>> by FlexibleListSerializer(BmMessage.serializer())

internal object FlexibleBmFileListSerializer :
    KSerializer<List<BmFile>> by FlexibleListSerializer(BmFile.serializer())

internal object FlexibleThreadAttachmentFileListSerializer :
    KSerializer<List<BmThreadAttachmentFile>> by FlexibleListSerializer(BmThreadAttachmentFile.serializer())

internal object FlexibleJsonElementListSerializer : KSerializer<List<JsonElement>> {
    private val listSerializer = ListSerializer(JsonElement.serializer())
    override val descriptor: SerialDescriptor = listSerializer.descriptor

    override fun deserialize(decoder: Decoder): List<JsonElement> {
        val jsonDecoder = decoder as? JsonDecoder ?: return listSerializer.deserialize(decoder)
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonArray -> element.toList()
            is JsonObject -> element.values.toList()
            JsonNull -> emptyList()
            is JsonPrimitive -> emptyList()
        }
    }

    override fun serialize(encoder: Encoder, value: List<JsonElement>) {
        listSerializer.serialize(encoder, value)
    }
}

internal object FlexibleIntListSerializer : KSerializer<List<Int>> {
    private val listSerializer = ListSerializer(Int.serializer())
    override val descriptor: SerialDescriptor = listSerializer.descriptor

    override fun deserialize(decoder: Decoder): List<Int> {
        val jsonDecoder = decoder as? JsonDecoder ?: return listSerializer.deserialize(decoder)
        return jsonDecoder.decodeJsonElement()
            .toIntList()
            .distinct()
    }

    override fun serialize(encoder: Encoder, value: List<Int>) {
        listSerializer.serialize(encoder, value)
    }
}

internal object FlexibleNullableBooleanSerializer : KSerializer<Boolean?> {
    private val nullableSerializer = Boolean.serializer().nullable
    override val descriptor: SerialDescriptor = nullableSerializer.descriptor

    override fun deserialize(decoder: Decoder): Boolean? {
        val jsonDecoder = decoder as? JsonDecoder ?: return nullableSerializer.deserialize(decoder)
        return jsonDecoder.decodeJsonElement().toBooleanOrNull()
    }

    override fun serialize(encoder: Encoder, value: Boolean?) {
        nullableSerializer.serialize(encoder, value)
    }
}

internal object FlexibleStringSerializer : KSerializer<String> {
    private val delegate = String.serializer()
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> element.contentOrNull.orEmpty()
            JsonNull -> ""
            else -> element.toString()
        }
    }

    override fun serialize(encoder: Encoder, value: String) {
        delegate.serialize(encoder, value)
    }
}

internal object FlexibleBmMessageMetaSerializer : KSerializer<BmMessageMeta> {
    private val delegate = BmMessageMeta.serializer()
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): BmMessageMeta {
        val jsonDecoder = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        val element = jsonDecoder.decodeJsonElement()
        if (element !is JsonObject) return BmMessageMeta()
        return runCatching { BetterMessagesJson.default.decodeFromJsonElement(delegate, element) }
            .getOrDefault(BmMessageMeta())
    }

    override fun serialize(encoder: Encoder, value: BmMessageMeta) {
        delegate.serialize(encoder, value)
    }
}

internal object FlexibleBmThreadMetaSerializer : KSerializer<BmThreadMeta?> {
    private val delegate = BmThreadMeta.serializer()
    private val nullableSerializer = delegate.nullable
    override val descriptor: SerialDescriptor = nullableSerializer.descriptor

    override fun deserialize(decoder: Decoder): BmThreadMeta? {
        val jsonDecoder = decoder as? JsonDecoder ?: return nullableSerializer.deserialize(decoder)
        val element = jsonDecoder.decodeJsonElement()
        if (element !is JsonObject) return null
        return runCatching { BetterMessagesJson.default.decodeFromJsonElement(delegate, element) }.getOrNull()
    }

    override fun serialize(encoder: Encoder, value: BmThreadMeta?) {
        nullableSerializer.serialize(encoder, value)
    }
}

internal object FlexibleBmPermissionsSerializer : KSerializer<BmPermissions?> {
    private val delegate = BmPermissions.serializer()
    private val nullableSerializer = delegate.nullable
    override val descriptor: SerialDescriptor = nullableSerializer.descriptor

    override fun deserialize(decoder: Decoder): BmPermissions? {
        val jsonDecoder = decoder as? JsonDecoder ?: return nullableSerializer.deserialize(decoder)
        val element = jsonDecoder.decodeJsonElement()
        if (element !is JsonObject) return null
        return runCatching { BetterMessagesJson.default.decodeFromJsonElement(delegate, element) }.getOrNull()
    }

    override fun serialize(encoder: Encoder, value: BmPermissions?) {
        nullableSerializer.serialize(encoder, value)
    }
}

private fun <T> decodeFlexibleList(
    element: JsonElement,
    serializer: KSerializer<T>
): List<T> = when (element) {
    is JsonArray -> element.mapNotNull { decodeOne(it, serializer) }
    is JsonObject -> {
        decodeOne(element, serializer)?.let { listOf(it) }
            ?: element.values.flatMap { decodeFlexibleList(it, serializer) }
    }
    JsonNull -> emptyList()
    is JsonPrimitive -> emptyList()
}

private fun <T> decodeOne(element: JsonElement, serializer: KSerializer<T>): T? =
    runCatching { BetterMessagesJson.default.decodeFromJsonElement(serializer, element) }.getOrNull()

private fun JsonElement.toIntList(): List<Int> = when (this) {
    is JsonArray -> flatMap { it.toIntList() }
    is JsonObject -> keys.mapNotNull { it.toIntOrNull() } + values.flatMap { it.toIntList() }
    JsonNull -> emptyList()
    is JsonPrimitive -> listOfNotNull(toIntOrNull())
}

private fun JsonPrimitive.toIntOrNull(): Int? =
    intOrNull
        ?: longOrNull?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
        ?: contentOrNull?.toIntOrNull()

private fun JsonElement.toBooleanOrNull(): Boolean? {
    val primitive = this as? JsonPrimitive ?: return null
    primitive.booleanOrNull?.let { return it }
    primitive.intOrNull?.let { return it != 0 }
    return when (primitive.contentOrNull?.trim()?.lowercase()) {
        "1", "true", "yes", "y" -> true
        "0", "false", "no", "n", "" -> false
        else -> null
    }
}
