package com.quata.feature.profile.domain

const val ProfileInvalidDisplayName = "profile_invalid_display_name"
const val ProfileInvalidNeighborhood = "profile_invalid_neighborhood"
const val ProfileInvalidCountryCode = "profile_invalid_country_code"
const val ProfileInvalidPhone = "profile_invalid_phone"
const val ProfilePhoneCollision = "profile_phone_collision"

sealed interface ProfileDetailsValidation {
    data class Valid(val update: ProfileUpdate) : ProfileDetailsValidation
    data class Invalid(val code: String) : ProfileDetailsValidation
}

/** Mirrors the registration contract before any profile upload or remote mutation. */
fun validateProfileDetails(update: ProfileUpdate): ProfileDetailsValidation {
    val displayName = normalizedProfileText(update.displayName)
    if (!displayName.isValidProfileText(minimum = 2, maximum = 80)) {
        return ProfileDetailsValidation.Invalid(ProfileInvalidDisplayName)
    }

    val neighborhood = normalizedProfileText(update.neighborhood)
    if (!neighborhood.isValidProfileText(minimum = 2, maximum = 100)) {
        return ProfileDetailsValidation.Invalid(ProfileInvalidNeighborhood)
    }

    val countryCode = update.countryCode.asciiDigits()
    if (!CountryCodePattern.matches(countryCode)) {
        return ProfileDetailsValidation.Invalid(ProfileInvalidCountryCode)
    }

    val phone = update.phone.asciiDigits()
    if (!PhonePattern.matches(phone) || countryCode.length + phone.length > 15) {
        return ProfileDetailsValidation.Invalid(ProfileInvalidPhone)
    }

    return ProfileDetailsValidation.Valid(
        update.copy(
            displayName = displayName,
            neighborhood = neighborhood,
            countryCode = countryCode,
            phone = phone,
        ),
    )
}

internal expect fun normalizeProfileCompatibility(value: String): String

private fun normalizedProfileText(value: String): String =
    normalizeProfileCompatibility(value).trim().replace(ProfileWhitespace, " ")

private fun String.isValidProfileText(minimum: Int, maximum: Int): Boolean =
    length in minimum..maximum && none { it.category == CharCategory.CONTROL }

private fun String.asciiDigits(): String = filter { it in '0'..'9' }

private val ProfileWhitespace = Regex("\\s+")
private val CountryCodePattern = Regex("[1-9][0-9]{0,2}")
private val PhonePattern = Regex("[0-9]{6,14}")
