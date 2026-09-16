package com.quata.core.platform

/** Main-dispatcher gate for account-bound notification taps during cold session restoration. */
class NotificationRecipientGate {
    var generation: Int = 0
        private set
    private var validationPending = true
    private var profileId: String? = null
    private var pending: Pair<String, () -> Unit>? = null

    fun receive(recipientProfileId: String?, open: () -> Unit) {
        // Older payloads have no recipient binding; preserve their existing route contract.
        if (recipientProfileId == null) { open(); return }
        if (recipientProfileId.isBlank()) return
        if (validationPending) {
            pending = recipientProfileId to open
        } else if (recipientProfileId == profileId) {
            open()
        }
    }

    fun completeValidation(profileId: String?) {
        generation++
        this.profileId = profileId?.takeIf { it.isNotBlank() }
        validationPending = false
        val target = pending
        pending = null
        if (target != null && target.first == this.profileId) target.second()
    }

    fun sessionEnded() {
        generation++
        pending = null
        profileId = null
        validationPending = false
    }

    fun completeValidationIfCurrent(expectedGeneration: Int, profileId: String?) {
        if (expectedGeneration == generation) completeValidation(profileId)
    }
}
