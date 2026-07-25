package com.quata.core.platform

/**
 * Value-only representation of a ContactsUI contact.
 *
 * Keeping this separate from [platform.Contacts.CNContact] makes the normalization rule
 * deterministic and testable without requesting address-book access or presenting UIKit.
 */
data class IosPickedContactFields(
    val givenName: String = "",
    val middleName: String = "",
    val familyName: String = "",
    val organizationName: String = "",
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
)

/** Pure mapping shared by the ContactsUI delegate and the iOS framework smoke tests. */
fun iosPickedContactToPlatformContact(fields: IosPickedContactFields): PlatformContact {
    val displayName = listOf(fields.givenName, fields.middleName, fields.familyName)
        .filter(String::isNotBlank)
        .joinToString(" ")
        .ifBlank { fields.organizationName.takeIf(String::isNotBlank) }
    return PlatformContact(
        displayName = displayName,
        phones = fields.phones.map(String::trim).filter(String::isNotBlank).distinct(),
        emails = fields.emails.map(String::trim).filter(String::isNotBlank).distinct(),
    )
}

/**
 * Testable outcome before it is converted to the common [PlatformResult] contract.
 *
 * It intentionally has no Contacts framework types, so XCTest can cover selection, cancellation
 * and adapter-error semantics without reading the address book or faking a UIKit picker.
 */
data class IosContactPickerOutcome(
    val contacts: List<PlatformContact> = emptyList(),
    val isCancelled: Boolean = false,
    val failureReason: String? = null,
)

/** ContactsUI treats an empty multi-selection as cancellation. */
fun iosPickedContactsOutcome(fields: List<IosPickedContactFields>): IosContactPickerOutcome =
    if (fields.isEmpty()) IosContactPickerOutcome(isCancelled = true)
    else IosContactPickerOutcome(contacts = fields.map(::iosPickedContactToPlatformContact))

/** Explicit normalizer for an adapter failure before it crosses the common service contract. */
fun iosContactPickerFailureOutcome(reason: String? = null): IosContactPickerOutcome =
    IosContactPickerOutcome(failureReason = reason ?: "contact_picker_failed")

internal fun IosContactPickerOutcome.toPlatformResult(): PlatformResult<List<PlatformContact>> = when {
    failureReason != null -> PlatformResult.Failure(failureReason)
    isCancelled -> PlatformResult.Cancelled
    else -> PlatformResult.Success(contacts)
}
