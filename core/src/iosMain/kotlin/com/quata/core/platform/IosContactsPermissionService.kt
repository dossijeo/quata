package com.quata.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Contacts.CNAuthorizationStatusAuthorized
import platform.Contacts.CNAuthorizationStatusDenied
import platform.Contacts.CNAuthorizationStatusNotDetermined
import platform.Contacts.CNAuthorizationStatusRestricted
import platform.Contacts.CNContactStore
import platform.Contacts.CNEntityType
import kotlin.coroutines.resume

/**
 * Real address-book authorization adapter, kept separate from [IosContactPickerService].
 *
 * ContactsUI can return only contacts explicitly picked by the user without this permission. The
 * explicit permission action remains useful to an authenticated host that also needs to resolve
 * a selection through a Contacts-backed integration; it is never requested as a side effect of
 * opening the picker.
 */
@OptIn(ExperimentalForeignApi::class)
class IosContactsPermissionService(
    private val store: CNContactStore = CNContactStore(),
) : PermissionService {
    override suspend fun status(permission: PlatformPermission): PermissionStatus = when (permission) {
        PlatformPermission.Contacts -> CNContactStore
            .authorizationStatusForEntityType(ContactsEntityType)
            .toContactsPermissionStatus()
        else -> PermissionStatus.Unavailable
    }

    override suspend fun request(permission: PlatformPermission): PermissionStatus {
        if (permission != PlatformPermission.Contacts) return PermissionStatus.Unavailable
        val current = status(permission)
        if (current != PermissionStatus.Denied) return current
        return suspendCancellableCoroutine { continuation ->
            store.requestAccessForEntityType(ContactsEntityType) { granted, error ->
                if (!continuation.isActive) return@requestAccessForEntityType
                continuation.resume(
                    when {
                        granted -> PermissionStatus.Granted
                        error != null -> PermissionStatus.Unavailable
                        else -> CNContactStore
                            .authorizationStatusForEntityType(ContactsEntityType)
                            .toContactsPermissionStatus()
                    },
                )
            }
        }
    }
}

/** Contacts exposes one entity kind; use the generated enum entry without relying on its C name. */
private val ContactsEntityType: CNEntityType = CNEntityType.entries.single()

@OptIn(ExperimentalForeignApi::class)
private fun Long.toContactsPermissionStatus(): PermissionStatus = when (this) {
    CNAuthorizationStatusAuthorized -> PermissionStatus.Granted
    CNAuthorizationStatusDenied,
    CNAuthorizationStatusRestricted -> PermissionStatus.PermanentlyDenied
    CNAuthorizationStatusNotDetermined -> PermissionStatus.Denied
    else -> PermissionStatus.Unavailable
}
