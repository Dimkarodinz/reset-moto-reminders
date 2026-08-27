package dev.resetlight.app

import android.content.Context

object ReleaseConsentPolicy {
    const val CURRENT_VERSION = 1

    fun isAccepted(acceptedVersion: Int): Boolean = acceptedVersion >= CURRENT_VERSION
}

class ReleaseConsentStore(
    context: Context,
    preferencesName: String = PREFERENCES_NAME,
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE,
    )

    fun isAccepted(): Boolean = ReleaseConsentPolicy.isAccepted(
        preferences.getInt(ACCEPTED_VERSION_KEY, 0),
    )

    fun accept() {
        check(
            preferences.edit()
                .putInt(ACCEPTED_VERSION_KEY, ReleaseConsentPolicy.CURRENT_VERSION)
                .commit(),
        ) { "Could not persist the safety acknowledgement" }
    }

    companion object {
        internal const val ACCEPTED_VERSION_KEY = "accepted_notice_version"
        private const val PREFERENCES_NAME = "release-consent"
    }
}
