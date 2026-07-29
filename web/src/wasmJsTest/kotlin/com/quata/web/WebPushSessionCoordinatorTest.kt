package com.quata.web

import com.quata.core.platform.PreferenceStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WebPushSessionCoordinatorTest {
    private val credentials = WebPushCredentials("access", "session")

    @Test
    fun enableCommitsConsentOnlyAfterBrowserAndServerSuccess() = runTest {
        val preferences = FakePreferences()
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            preferences = preferences,
            events = events,
            created = WebPushSubscriptionResult.Success(SUBSCRIPTION),
        )

        assertIs<WebPushSessionResult.Success>(coordinator.enableFromUserGesture())
        assertEquals(listOf("create:true", "credentials", "server:subscribe"), events)
        assertTrue(WebPushConsent.isEnabled(preferences))
    }

    @Test
    fun deniedDefaultUnsupportedAndBackendFailureRemainOptedOutAndRetryable() = runTest {
        for (browserResult in listOf(
            WebPushSubscriptionResult.PermissionDenied,
            WebPushSubscriptionResult.Unsupported,
            WebPushSubscriptionResult.Failure("default"),
        )) {
            val preferences = FakePreferences()
            val events = mutableListOf<String>()
            val coordinator = coordinator(preferences, events, created = browserResult)

            coordinator.enableFromUserGesture()

            assertFalse(WebPushConsent.isEnabled(preferences))
            assertEquals(listOf("create:true"), events)
        }

        val preferences = FakePreferences()
        val events = mutableListOf<String>()
        val failed = coordinator(
            preferences,
            events,
            created = WebPushSubscriptionResult.Success(SUBSCRIPTION),
            subscribeResult = WebPushRegistrationResult.Failure("backend"),
        )
        assertIs<WebPushSessionResult.Failure>(failed.enableFromUserGesture())
        assertFalse(WebPushConsent.isEnabled(preferences))
        assertEquals(listOf("create:true", "credentials", "server:subscribe"), events)
    }

    @Test
    fun disableIsServerFirstAndPreservesConsentAndEndpointOnServerFailure() = runTest {
        val preferences = FakePreferences(enabled = true)
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            preferences,
            events,
            existing = WebPushSubscriptionResult.Success(SUBSCRIPTION),
            unsubscribeResult = WebPushRegistrationResult.Failure("backend"),
        )

        assertIs<WebPushSessionResult.Failure>(coordinator.disableFromUserGesture())
        assertEquals(listOf("credentials", "browser:existing", "server:unsubscribe"), events)
        assertTrue(WebPushConsent.isEnabled(preferences))
    }

    @Test
    fun disableCommitsOptOutOnlyAfterServerAndBrowserSuccess() = runTest {
        val preferences = FakePreferences(enabled = true)
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            preferences,
            events,
            existing = WebPushSubscriptionResult.Success(SUBSCRIPTION),
        )

        assertIs<WebPushSessionResult.Success>(coordinator.disableFromUserGesture())
        assertEquals(listOf("credentials", "browser:existing", "server:unsubscribe", "browser:unsubscribe"), events)
        assertFalse(WebPushConsent.isEnabled(preferences))
    }

    @Test
    fun preV1ExistingEndpointIsAdoptedAndReconciledWithoutPermissionPrompt() = runTest {
        val preferences = FakePreferences()
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            preferences,
            events,
            existing = WebPushSubscriptionResult.Success(SUBSCRIPTION),
            subscribeResult = WebPushRegistrationResult.Failure("retryable"),
        )

        assertIs<WebPushSessionResult.Failure>(coordinator.reconcileCurrentSession())
        assertEquals(listOf("browser:existing", "credentials", "server:subscribe"), events)
        assertTrue(WebPushConsent.isEnabled(preferences))
    }

    @Test
    fun preV1WithoutEndpointDoesNothingAndNeverPromptsOrPosts() = runTest {
        val preferences = FakePreferences()
        val events = mutableListOf<String>()
        val coordinator = coordinator(preferences, events)

        assertIs<WebPushSessionResult.ConsentDisabled>(coordinator.reconcileCurrentSession())
        assertEquals(listOf("browser:existing"), events)
        assertEquals(WebPushConsentState.Unset, WebPushConsent.state(preferences))
    }

    @Test
    fun lookupFailureDuringDisablePreservesEnabledStateAndNeverClaimsSuccess() = runTest {
        val preferences = FakePreferences(enabled = true)
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            preferences,
            events,
            existing = WebPushSubscriptionResult.Failure("service_worker_unavailable"),
        )

        assertIs<WebPushSessionResult.Failure>(coordinator.disableFromUserGesture())
        assertEquals(listOf("credentials", "browser:existing"), events)
        assertTrue(WebPushConsent.isEnabled(preferences))
    }

    private fun coordinator(
        preferences: FakePreferences,
        events: MutableList<String>,
        existing: WebPushSubscriptionResult = WebPushSubscriptionResult.NoSubscription,
        created: WebPushSubscriptionResult = WebPushSubscriptionResult.Failure("unused"),
        subscribeResult: WebPushRegistrationResult = WebPushRegistrationResult.Success,
        unsubscribeResult: WebPushRegistrationResult = WebPushRegistrationResult.Success,
        browserUnsubscribeResult: Result<Unit> = Result.success(Unit),
    ) = WebPushSessionCoordinator(
        preferences,
        WebPushSessionOperations(
            currentCredentials = {
                events += "credentials"
                credentials
            },
            getExistingSubscription = {
                events += "browser:existing"
                existing
            },
            getOrCreateSubscription = { requestPermission ->
                events += "create:$requestPermission"
                created
            },
            subscribeServer = { _, _ ->
                events += "server:subscribe"
                subscribeResult
            },
            unsubscribeServer = { _, _ ->
                events += "server:unsubscribe"
                unsubscribeResult
            },
            unsubscribeBrowser = {
                events += "browser:unsubscribe"
                browserUnsubscribeResult
            },
            logout = { Result.success(Unit) },
        ),
    )

    private class FakePreferences(enabled: Boolean? = null) : PreferenceStore {
        private val values = mutableMapOf<String, String>()

        init {
            if (enabled != null) values[WebPushConsent.PreferenceKey] = if (enabled) "enabled" else "disabled"
        }

        override suspend fun getString(key: String): String? = values[key]
        override suspend fun putString(key: String, value: String) { values[key] = value }
        override suspend fun remove(key: String) { values.remove(key) }
    }

    private companion object {
        const val SUBSCRIPTION = """{"endpoint":"https://push.example/sub"}"""
    }
}
