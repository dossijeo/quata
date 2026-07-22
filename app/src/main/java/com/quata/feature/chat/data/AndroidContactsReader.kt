package com.quata.feature.chat.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat
import com.quata.feature.chat.domain.ChatInviteContact
import com.quata.feature.chat.domain.normalizeContactPhoneKey
import java.util.Locale

class AndroidContactsReader(context: Context) {
    private val appContext = context.applicationContext

    fun readContacts(): List<ChatInviteContact> {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        val contacts = linkedMapOf<String, MutableInviteContact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER
        )

        try {
            appContext.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC"
            )?.use { cursor ->
                val contactIdColumn = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val lookupKeyColumn = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
                val nameColumn = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val numberColumn = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val normalizedColumn = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER)

                while (cursor.moveToNext()) {
                    val contactId = cursor.getLong(contactIdColumn)
                    val lookupKey = cursor.getString(lookupKeyColumn).orEmpty()
                    val id = lookupKey.ifBlank { contactId.toString() }
                    val displayName = cursor.getString(nameColumn)?.trim().orEmpty().ifBlank { cursor.getString(numberColumn).orEmpty() }
                    val displayPhone = cursor.getString(numberColumn)?.trim().orEmpty()
                    val providerNormalizedPhone = cursor.getString(normalizedColumn).orEmpty()
                    val internationalPhone = providerNormalizedPhone.takeIf { it.startsWith("+") }
                        ?: PhoneNumberUtils.formatNumberToE164(displayPhone, Locale.getDefault().country)
                    val phoneKeys = sequenceOf(displayPhone, providerNormalizedPhone, internationalPhone.orEmpty())
                        .map(::normalizeContactPhoneKey)
                        .filter { it.length in 6..20 }
                        .toSet()
                    if (phoneKeys.isEmpty()) continue

                    val contact = contacts.getOrPut(id) {
                        MutableInviteContact(
                            id = id,
                            displayName = displayName,
                            phone = displayPhone,
                            internationalPhone = internationalPhone
                        )
                    }
                    contact.phoneKeys += phoneKeys
                    if (contact.phone.isBlank()) contact.phone = displayPhone
                    if (contact.internationalPhone == null) contact.internationalPhone = internationalPhone
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        }

        return contacts.values.map { contact ->
            ChatInviteContact(
                id = contact.id,
                displayName = contact.displayName,
                phone = contact.phone,
                phoneKeys = contact.phoneKeys,
                internationalPhone = contact.internationalPhone
            )
        }
    }

    private data class MutableInviteContact(
        val id: String,
        val displayName: String,
        var phone: String,
        var internationalPhone: String?,
        val phoneKeys: MutableSet<String> = linkedSetOf()
    )
}
