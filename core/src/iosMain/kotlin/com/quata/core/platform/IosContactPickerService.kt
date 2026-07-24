package com.quata.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Contacts.CNContact
import platform.Contacts.CNPhoneNumber
import platform.ContactsUI.CNContactPickerDelegateProtocol
import platform.ContactsUI.CNContactPickerViewController
import platform.Foundation.NSString
import platform.darwin.NSObject
import kotlin.concurrent.Volatile
import kotlin.coroutines.resume

/**
 * UIKit/SwiftUI boundary for the system contact picker.
 *
 * ContactsUI only reveals contacts explicitly selected by the user. It is intentionally separate
 * from address-book authorization: the picker does not enumerate contacts through CNContactStore
 * and therefore does not require `NSContactsUsageDescription`.
 */
fun interface IosContactPickerHost {
    suspend fun pickContacts(): PlatformResult<List<PlatformContact>>
}

/**
 * Real iOS [ContactPickerService] with an explicitly injected UIKit host.
 *
 * The mutex ensures one visible system picker at a time. Without an active presenter it returns
 * [PlatformResult.Unsupported], rather than attempting to retain a stale UIViewController.
 */
class IosContactPickerService : ContactPickerService {
    private val requests = Mutex()

    @Volatile
    private var host: IosContactPickerHost? = null

    fun attachHost(host: IosContactPickerHost) {
        this.host = host
    }

    fun detachHost(host: IosContactPickerHost) {
        if (this.host === host) this.host = null
    }

    /** Wires ContactsUI to the launcher-owned visible UIViewController provider. */
    fun attachSystemPicker(presenterProvider: IosViewControllerProvider): IosContactsUIKitPickerHost =
        IosContactsUIKitPickerHost(presenterProvider).also(::attachHost)

    override suspend fun pickContacts(): PlatformResult<List<PlatformContact>> = requests.withLock {
        host?.pickContacts() ?: PlatformResult.Unsupported
    }
}

/**
 * ContactsUI adapter. The delegate is held strongly for the duration of presentation because
 * CNContactPickerViewController keeps its delegate weakly.
 */
@OptIn(ExperimentalForeignApi::class)
class IosContactsUIKitPickerHost(
    private val presenterProvider: IosViewControllerProvider,
) : IosContactPickerHost {
    private var activeDelegate: IosContactPickerDelegate? = null

    override suspend fun pickContacts(): PlatformResult<List<PlatformContact>> {
        if (activeDelegate != null) return PlatformResult.Failure("contact_picker_in_progress")
        val presenter = presenterProvider.activeViewController() ?: return PlatformResult.Unsupported
        return suspendCancellableCoroutine { continuation ->
            val picker = CNContactPickerViewController()
            lateinit var delegate: IosContactPickerDelegate
            delegate = IosContactPickerDelegate { result ->
                if (activeDelegate === delegate) activeDelegate = null
                if (continuation.isActive) continuation.resume(result)
            }
            activeDelegate = delegate
            picker.delegate = delegate
            continuation.invokeOnCancellation {
                if (activeDelegate === delegate) {
                    activeDelegate = null
                    picker.dismissViewControllerAnimated(flag = true, completion = null)
                }
            }
            presenter.presentViewController(picker, animated = true, completion = null)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosContactPickerDelegate(
    private val complete: (PlatformResult<List<PlatformContact>>) -> Unit,
) : NSObject(), CNContactPickerDelegateProtocol {
    private var completed = false

    override fun contactPicker(
        picker: CNContactPickerViewController,
        didSelectContact: CNContact,
    ) = finish(PlatformResult.Success(listOf(didSelectContact.toPlatformContact())))

    /** Implementing this delegate callback enables ContactsUI's native multiple selection mode. */
    override fun contactPicker(
        picker: CNContactPickerViewController,
        didSelectContacts: List<*>,
    ) {
        val contacts = didSelectContacts.filterIsInstance<CNContact>().map(CNContact::toPlatformContact)
        finish(if (contacts.isEmpty()) PlatformResult.Cancelled else PlatformResult.Success(contacts))
    }

    override fun contactPickerDidCancel(picker: CNContactPickerViewController) {
        finish(PlatformResult.Cancelled)
    }

    private fun finish(result: PlatformResult<List<PlatformContact>>) {
        if (completed) return
        completed = true
        complete(result)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun CNContact.toPlatformContact(): PlatformContact {
    val displayName = listOf(givenName, middleName, familyName)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { organizationName.takeIf { it.isNotBlank() } }
    val phones = phoneNumbers.mapNotNull { item ->
        (item.value as? CNPhoneNumber)?.stringValue?.takeIf(String::isNotBlank)
    }
    val emails = emailAddresses.mapNotNull { item ->
        (item.value as? NSString)?.toString()?.takeIf(String::isNotBlank)
    }
    return PlatformContact(
        displayName = displayName,
        phones = phones.distinct(),
        emails = emails.distinct(),
    )
}
