package com.quata.core.platform

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Web Contact Picker API adapter. The API only exposes contacts after a user gesture and is not
 * broadly supported, so unsupported browsers receive an explicit [PlatformResult.Unsupported].
 */
class BrowserContactPickerService : ContactPickerService {
    override suspend fun pickContacts(): PlatformResult<List<PlatformContact>> = suspendCoroutine { continuation ->
        browserPickContacts { state, payload ->
            continuation.resume(
                when (state) {
                    "success" -> PlatformResult.Success(payload.orEmpty().toPlatformContacts())
                    "cancelled" -> PlatformResult.Cancelled
                    "unsupported" -> PlatformResult.Unsupported
                    else -> PlatformResult.Failure(payload)
                }
            )
        }
    }
}

private fun String.toPlatformContacts(): List<PlatformContact> = runCatching {
    Json.parseToJsonElement(this).jsonArray.mapNotNull { element ->
        val contact = element.jsonObject
        val displayName = contact["displayName"]?.jsonPrimitive?.contentOrNull
        val phones = contact["phones"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            .orEmpty()
        val emails = contact["emails"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            .orEmpty()
        if (displayName == null && phones.isEmpty() && emails.isEmpty()) null else PlatformContact(displayName, phones, emails)
    }
}.getOrElse { emptyList() }

private fun browserPickContacts(onResult: (String, String?) -> Unit): Unit = js(
    """
    try {
      const picker = globalThis.navigator?.contacts?.select;
      if (typeof picker !== 'function') { onResult('unsupported', null); return; }
      picker.call(globalThis.navigator.contacts, ['name', 'tel', 'email'], { multiple: true })
        .then((contacts) => onResult('success', JSON.stringify((contacts || []).map((contact) => ({
          displayName: Array.isArray(contact.name) ? (contact.name[0] || null) : null,
          phones: Array.isArray(contact.tel) ? contact.tel : [],
          emails: Array.isArray(contact.email) ? contact.email : []
        })))))
        .catch((error) => onResult(error?.name === 'AbortError' ? 'cancelled' : 'failure', error?.message || null));
    } catch (error) {
      onResult('failure', error?.message || null);
    }
    """
)
