import XCTest
import UIKit

final class QuataIosHostUITests: XCTestCase {
    private static let realRecoveryOptIn = "I_ACCEPT_IOS_PASSWORD_RESET_ROUNDTRIP"
    private static let realRegistrationOptIn = "I_ACCEPT_IOS_REGISTRATION_PRODUCT_TRIAL"
    private var privateKeyboardFrameCache: [PrivateKeyboardMode: [String: CGRect]] = [:]

    func testAnonymousFixtureLaunchesWithoutCreatingASessionOrComposeSurface() {
        let app = fixtureApp("anonymous")
        app.launch()

        let anonymousSurface = QuataIosHostUITestSupport.fixtureRoot(
            in: app,
            identifier: "quata-ios-test-anonymous-host",
        )
        XCTAssertEqual(
            anonymousSurface.label, "Quata iOS anonymous fixture",
        )
        XCTAssertFalse(app.descendants(matching: .any).matching(identifier: "quata-ios-compose-root").firstMatch.exists)

    }

    func testAuthLaunchFixtureColdStartsTwiceWithStableHostAndComposeReadiness() {
        let app = fixtureApp("auth-launch")

        for launchNumber in 1...2 {
            app.launch()
            let host = QuataIosHostUITestSupport.fixtureRoot(
                in: app,
                identifier: "quata-ios-auth-launch-host",
            )
            XCTAssertEqual(host.label, "Quata iOS Auth launch fixture")

            let containmentMarker = app.descendants(matching: .any)
                .matching(identifier: "quata-ios-auth-launch-ready")
                .firstMatch
            XCTAssertTrue(
                containmentMarker.exists,
                "The UIKit shell must retain its containment marker.",
            )
            XCTAssertEqual(containmentMarker.label, "Quata iOS Auth fixture ready")

            // `auth.submit` is emitted by the shared Compose LoginForm semantics. Waiting for
            // this descendant proves that the actual Auth content, rather than the UIKit shell,
            // has completed composition. Do not replace this with a native overlay or credential
            // entry: this fixture is intentionally not an authenticated E2E flow.
            let composeSubmit = app.descendants(matching: .any)
                .matching(identifier: "auth.submit")
                .firstMatch
            XCTAssertTrue(
                composeSubmit.waitForExistence(timeout: 10),
                "The real Compose Auth submit semantic must be available on every cold launch.",
            )
            XCTAssertEqual(composeSubmit.label, "Sign in")
            QuataIosHostUITestSupport.attachRenderedSurface(named: "auth-launch-cold-start-\(launchNumber)")
            app.terminate()
        }
    }

    func testAuthLaunchKeepsPhoneDraftAndKeyboardSafeAcrossRotation() {
        let device = XCUIDevice.shared
        device.orientation = .portrait
        addTeardownBlock { device.orientation = .portrait }

        let app = fixtureApp("auth-launch")
        app.launch()
        let window = app.windows.firstMatch
        XCTAssertTrue(window.waitForExistence(timeout: 10), "The Auth fixture must expose an app window.")

        let phone = app.descendants(matching: .any)
            .matching(identifier: "auth.phone.input")
            .firstMatch
        XCTAssertTrue(phone.waitForExistence(timeout: 10), "The real shared Auth phone input must exist.")
        let marker = "612345678"
        phone.tap()
        phone.typeText(marker)
        assertFocusedInput(phone, containsDigits: marker, aboveKeyboardIn: app, context: "Auth portrait")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-auth-keyboard-portrait")

        device.orientation = .landscapeLeft
        waitForWindow(window, toBeLandscape: true, context: "Auth landscape")
        assertFocusedInput(phone, containsDigits: marker, aboveKeyboardIn: app, context: "Auth landscape")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-auth-keyboard-landscape")

        device.orientation = .portrait
        waitForWindow(window, toBeLandscape: false, context: "Auth restored portrait")
        assertFocusedInput(phone, containsDigits: marker, aboveKeyboardIn: app, context: "Auth restored portrait")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-auth-keyboard-restored-portrait")
    }

    func testAuthLaunchFixtureCanColdStartSharedRecoverySurface() {
        let app = fixtureApp("auth-launch", authDestination: "recovery")
        app.launch()

        let host = QuataIosHostUITestSupport.fixtureRoot(
            in: app,
            identifier: "quata-ios-auth-launch-host",
        )
        XCTAssertEqual(host.label, "Quata iOS Auth launch fixture")

        for identifier in [
            "auth.recovery.root",
            "auth.recovery.country-prefix",
            "auth.recovery.phone",
            "auth.recovery.question",
            "auth.recovery.secret-answer",
            "auth.recovery.new-password",
            "auth.recovery.submit",
            "auth.recovery.back",
        ] {
            XCTAssertTrue(
                app.descendants(matching: .any)
                    .matching(identifier: identifier)
                    .firstMatch
                    .waitForExistence(timeout: 10),
                "The shared recovery semantic \(identifier) must be available in the iOS fixture.",
            )
        }
        QuataIosHostUITestSupport.attachRenderedSurface(named: "auth-launch-recovery")
    }

    func testAuthLaunchFixtureCanColdStartSharedRegisterLegalLinks() {
        let app = fixtureApp("auth-launch", authDestination: "register", spanishLocale: true)
        app.launch()

        let host = QuataIosHostUITestSupport.fixtureRoot(
            in: app,
            identifier: "quata-ios-auth-launch-host",
        )
        XCTAssertEqual(host.label, "Quata iOS Auth launch fixture")

        for identifier in [
            "legal-document-link-privacy",
            "legal-document-link-childsafety",
        ] {
            XCTAssertTrue(
                app.descendants(matching: .any)
                    .matching(identifier: identifier)
                    .firstMatch
                    .waitForExistence(timeout: 10),
                "The shared register legal semantic \(identifier) must be available in the iOS fixture.",
            )
        }
        QuataIosHostUITestSupport.attachRenderedSurface(named: "auth-launch-register-legal")

        app.descendants(matching: .any)
            .matching(identifier: "legal-document-link-privacy")
            .firstMatch
            .tap()
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "legal-document-opened-privacy_es.docx")
                .firstMatch
                .waitForExistence(timeout: 10),
            "The shared register Privacy link must resolve to the packaged Spanish DOCX.",
        )
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "document-viewer-status-root")
                .firstMatch
                .waitForExistence(timeout: 10),
            "The shared register legal links must render the common document viewer status chrome.",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "auth-launch-register-document-viewer-status")
        app.descendants(matching: .any)
            .matching(identifier: "document-viewer-status-close")
            .firstMatch
            .tap()
        app.descendants(matching: .any)
            .matching(identifier: "legal-document-link-childsafety")
            .firstMatch
            .tap()
        XCTAssertTrue(
            app.descendants(matching: .any)
            .matching(identifier: "legal-document-opened-child_safety_es.docx")
            .firstMatch
            .waitForExistence(timeout: 10),
            "The shared register Child Safety link must resolve to the packaged Spanish DOCX.",
        )
    }

    func testUgcTermsFixtureRendersCommonGateLegalLinksAndAccepts() {
        let app = fixtureApp("ugc-terms", spanishLocale: true)
        app.launch()

        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "quata-ugc-terms-dialog")
                .firstMatch
                .waitForExistence(timeout: 10),
            "The iOS UGC fixture must mount the shared blocking terms dialog.",
        )
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "quata-ugc-terms-body")
                .firstMatch
                .waitForExistence(timeout: 10),
            "The shared UGC terms body must be visible before acceptance.",
        )
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "quata-ugc-terms-logout")
                .firstMatch
                .waitForExistence(timeout: 10),
            "The shared UGC terms logout action must remain available while blocked.",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-ugc-terms-required")

        app.descendants(matching: .any)
            .matching(identifier: "legal-document-link-childsafety")
            .firstMatch
            .tap()
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "ugc-terms-legal-document-opened-child_safety_es.docx")
                .firstMatch
                .waitForExistence(timeout: 10),
            "The UGC Child Safety link must resolve to the packaged Spanish DOCX.",
        )
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "document-viewer-status-root")
                .firstMatch
                .waitForExistence(timeout: 10),
            "The UGC legal link must render the common document viewer status chrome.",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-ugc-terms-child-safety")
        app.descendants(matching: .any)
            .matching(identifier: "document-viewer-status-close")
            .firstMatch
            .tap()

        app.descendants(matching: .any)
            .matching(identifier: "legal-document-link-privacy")
            .firstMatch
            .tap()
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "ugc-terms-legal-document-opened-privacy_es.docx")
                .firstMatch
                .waitForExistence(timeout: 10),
            "The UGC Privacy link must resolve to the packaged Spanish DOCX.",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-ugc-terms-privacy")
        app.descendants(matching: .any)
            .matching(identifier: "document-viewer-status-close")
            .firstMatch
            .tap()

        let accept = app.descendants(matching: .any)
            .matching(identifier: "quata-ugc-terms-accept")
            .firstMatch
        XCTAssertTrue(accept.waitForExistence(timeout: 10), "The shared UGC accept action must be exposed.")
        accept.tap()
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "quata-ios-ugc-terms-accepted")
                .firstMatch
                .waitForExistence(timeout: 10),
            "Accepting UGC terms must invoke the common gateway callback.",
        )
        XCTAssertFalse(
            app.descendants(matching: .any)
                .matching(identifier: "quata-ios-ugc-terms-logout")
                .firstMatch
                .exists,
            "Accepting the dialog must not invoke the logout callback.",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-ugc-terms-accepted")
    }

    func testRealAuthRecoveryFixtureRoundTripsPasswordAndKeepsEvidence() throws {
        guard ProcessInfo.processInfo.environment["QUATA_IOS_AUTH_RECOVERY_REAL_OPT_IN"] == Self.realRecoveryOptIn else {
            throw XCTSkip("Real iOS recovery is opt-in because it mutates an authorized account password.")
        }
        guard let configurationFile = ProcessInfo.processInfo.environment["QUATA_IOS_AUTH_RECOVERY_E2E_FILE"],
              !configurationFile.isEmpty else {
            throw XCTSkip("QUATA_IOS_AUTH_RECOVERY_E2E_FILE is not configured.")
        }
        let credentials = try AuthRecoveryUiCredentials.load(from: configurationFile)

        let missingApp = fixtureApp("auth-recovery-real", spanishLocale: true)
        missingApp.launch()
        XCTAssertTrue(
            missingApp.descendants(matching: .any)
                .matching(identifier: "auth.recovery.root")
                .firstMatch
                .waitForExistence(timeout: 10),
            "The real iOS recovery fixture must mount the shared recovery root.",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "auth-recovery-real-mounted")
        enterText(credentials.missingLocalPhone, into: "auth.recovery.phone", in: missingApp)
        XCTAssertTrue(
            missingApp.descendants(matching: .any)
                .matching(identifier: "auth.recovery.error")
                .firstMatch
                .waitForExistence(timeout: 20),
            "A missing account must surface the shared recovery error in product UI.",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "auth-recovery-real-missing-account")
        missingApp.terminate()

        let app = fixtureApp("auth-recovery-real", spanishLocale: true)
        app.launch()
        try performRecoveryReset(
            in: app,
            phone: credentials.localPhone,
            secretAnswer: credentials.secretAnswer,
            newPassword: credentials.temporaryPassword,
            expectedQuestion: credentials.expectedQuestion,
            evidencePrefix: "auth-recovery-real-temporary",
        )
        openRecoveryFromLogin(in: app)
        try performRecoveryReset(
            in: app,
            phone: credentials.localPhone,
            secretAnswer: credentials.secretAnswer,
            newPassword: credentials.restorePassword,
            expectedQuestion: credentials.expectedQuestion,
            evidencePrefix: "auth-recovery-real-restored",
        )
    }

    func testRealAuthRegistrationSubmitsOnceAndRestoresAuthenticatedFeed() throws {
        let environment = ProcessInfo.processInfo.environment
        guard environment["QUATA_IOS_AUTH_REGISTER_REAL_OPT_IN"] == Self.realRegistrationOptIn else {
            throw XCTSkip("Real iOS registration is an explicitly guarded product trial.")
        }
        guard let inputFile = environment["QUATA_IOS_AUTH_REGISTER_E2E_FILE"], !inputFile.isEmpty else {
            throw XCTSkip("QUATA_IOS_AUTH_REGISTER_E2E_FILE is not configured.")
        }
        let input = try IosRegistrationUiInput.load(from: inputFile)
        let app = fixtureApp("auth-register-real", spanishLocale: true)
        app.launch()

        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "auth.register.display-name")
                .firstMatch
                .waitForExistence(timeout: 15),
            "The guarded fixture must mount the real shared registration form.",
        )
        for identifier in [
            "auth.register.display-name", "auth.register.neighborhood", "auth.register.country-prefix",
            "auth.register.phone.input", "auth.register.password", "auth.register.secret-question",
            "auth.register.secret-answer", "auth.register.submit",
        ] {
            XCTAssertTrue(
                app.descendants(matching: .any).matching(identifier: identifier).firstMatch.exists,
                "Expected shared registration anchor \(identifier).",
            )
        }
        QuataIosHostUITestSupport.attachRenderedSurface(named: "auth-register-real-mounted")

        typePrivateText(input.displayName, into: "auth.register.display-name", in: app)
        typePrivateText(input.neighborhood, into: "auth.register.neighborhood", in: app)
        tapAfterDismissingKeyboard("auth.register.country-prefix", in: app)
        enterText(input.countryCode, into: "auth.register.country-prefix.search", in: app)
        dismissKeyboardWithReturn(from: "auth.register.country-prefix.search", in: app)
        tapVisibleElement("auth.register.country-prefix.option.\(input.countryCode)", in: app)
        typePrivatePhone(input.phone, into: "auth.register.phone.input", in: app)
        typePrivateText(input.password, into: "auth.register.password", in: app)
        tapAfterDismissingKeyboard("auth.register.secret-question", in: app)
        tapAfterDismissingKeyboard("auth.register.secret-question.option.\(input.secretQuestion)", in: app)
        typePrivateText(input.secretAnswer, into: "auth.register.secret-answer", in: app)

        tapAfterDismissingKeyboard("auth.register.submit", in: app)
        // Xcode 26's injected XCTAutomationSupport crashes inside its runtime-issue logger when
        // it snapshots the hierarchy continuously while the full-screen WKWebView is presented.
        // Let the product's bounded 90-second challenge finish before querying the app again.
        RunLoop.current.run(until: Date().addingTimeInterval(95))
        let authenticated = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-auth-register-success")
            .firstMatch
            .waitForExistence(timeout: 55)
        if !authenticated {
            QuataIosHostUITestSupport.attachRenderedSurface(named: "auth-register-real-after-challenge")
            XCTFail(
                "One product submit must complete native Turnstile, register, login and invoke the authenticated callback."
            )
            return
        }
        QuataIosHostUITestSupport.attachRenderedSurface(named: "auth-register-real-authenticated")
        app.terminate()

        let relaunched = XCUIApplication()
        relaunched.launchArguments += ["-AppleLanguages", "(es)", "-AppleLocale", "es_ES"]
        relaunched.launch()
        let authenticatedChrome = relaunched.descendants(matching: .any)
            .matching(identifier: "quata-ios-authenticated-top-chrome")
            .firstMatch
        let feedNavigation = relaunched.descendants(matching: .any)
            .matching(identifier: "navigation.primary.feed")
            .firstMatch
        guard authenticatedChrome.waitForExistence(timeout: 30), feedNavigation.exists else {
            QuataIosHostUITestSupport.attachRenderedSurface(named: "auth-register-real-relaunch-failed")
            XCTFail("A normal relaunch must restore the authenticated shell with Feed selected.")
            return
        }
        QuataIosHostUITestSupport.attachRenderedSurface(named: "auth-register-real-relaunched-feed")
        print("IOS_AUTH_REGISTER_REAL_UI_GATE_PASSED")
    }

    func testMalformedAuthLaunchFixtureArgumentsFailClosedWithoutCompose() {
        let scenarios: [[String]] = [
            ["-quata-ui-test-fixture"],
            ["-quata-ui-test-fixture", "not-a-fixture"],
        ]

        for arguments in scenarios {
            let app = XCUIApplication()
            app.launchArguments = arguments
            app.launch()
            XCTAssertEqual(
                QuataIosHostUITestSupport.fixtureRoot(
                    in: app,
                    identifier: "quata-ios-test-invalid-fixture",
                ).label,
                "Quata iOS invalid fixture",
            )
            XCTAssertFalse(
                app.descendants(matching: .any).matching(identifier: "auth.submit").firstMatch.exists,
                "A malformed fixture argument must not create the real Auth Compose surface.",
            )
            app.terminate()
        }
    }

    func testNormalLaunchExposesTheUnconfiguredComposeMigrationSemantics() {
        let app = XCUIApplication()
        app.launch()

        _ = QuataIosHostUITestSupport.composeRoot(in: app)
        assertUnconfiguredMigrationSemantics(in: app)
        QuataIosHostUITestSupport.attachRenderedSurface(named: "compose-migration-unconfigured")
    }

    func testNormalLaunchShowsSharedStartupSplashAndThenMigrationSurface() {
        let app = XCUIApplication()
        app.launch()

        let splash = app.descendants(matching: .any)
            .matching(identifier: "quata-splash-root")
            .firstMatch
        XCTAssertTrue(
            splash.waitForExistence(timeout: 5),
            "The normal iOS launcher must mount the shared Compose startup splash.",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "startup-splash-ios")
        XCTAssertTrue(
            splash.waitForNonExistence(timeout: 8),
            "The shared splash must dismiss through its common onFinished callback.",
        )
        _ = QuataIosHostUITestSupport.composeRoot(in: app, timeout: 10, context: "after shared splash")
        assertUnconfiguredMigrationSemantics(in: app)
        QuataIosHostUITestSupport.attachRenderedSurface(named: "startup-splash-ios-complete")
    }

    func testColdRelaunchAndWarmForegroundKeepStartupPolicyStable() {
        let app = XCUIApplication()
        app.launch()
        let splash = app.descendants(matching: .any)
            .matching(identifier: "quata-splash-root")
            .firstMatch
        XCTAssertTrue(splash.waitForExistence(timeout: 5), "Cold start must mount the shared splash.")
        XCTAssertTrue(splash.waitForNonExistence(timeout: 8), "Cold-start splash must finish.")
        _ = QuataIosHostUITestSupport.composeRoot(in: app, context: "cold start complete")

        XCUIDevice.shared.press(.home)
        app.activate()
        _ = QuataIosHostUITestSupport.composeRoot(in: app, context: "warm foreground")
        XCTAssertFalse(splash.exists, "Warm foreground must not restart the splash.")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "startup-splash-ios-warm-foreground")

        app.terminate()
        app.launch()
        XCTAssertTrue(splash.waitForExistence(timeout: 5), "Cold relaunch must mount the shared splash again.")
        XCTAssertTrue(splash.waitForNonExistence(timeout: 8), "Cold-relaunch splash must finish.")
        _ = QuataIosHostUITestSupport.composeRoot(in: app, context: "cold relaunch complete")
        assertUnconfiguredMigrationSemantics(in: app)
        QuataIosHostUITestSupport.attachRenderedSurface(named: "startup-splash-ios-cold-relaunch")
    }

    func testColdRelaunchRestoresOneComposeMigrationSurface() {
        let app = XCUIApplication()
        app.launch()
        _ = QuataIosHostUITestSupport.composeRoot(in: app, context: "initial launch")

        app.terminate()
        app.launch()

        _ = QuataIosHostUITestSupport.composeRoot(in: app, context: "cold relaunch")
        assertUnconfiguredMigrationSemantics(in: app)
        QuataIosHostUITestSupport.attachRenderedSurface(named: "compose-migration-cold-relaunch")
    }

    func testAuthenticatedFixtureColdStartsChatDeepLinkThroughTheSharedRouter() {
        let app = fixtureApp(
            "authenticated",
            deepLink: "https://egquata.com/#chat-conversation-7?message=message-4",
        )
        app.launch()

        let chatSurface = QuataIosHostUITestSupport.fixtureRoot(
            in: app,
            identifier: "quata-ios-chat-host",
        )
        XCTAssertEqual(chatSurface.label, "Quata iOS Chat")
        XCTAssertFalse(app.descendants(matching: .any).matching(identifier: "quata-ios-compose-root").firstMatch.exists)
    }

    func testAuthenticatedFixtureColdStartsFeedAndOfficialDeepLinksThroughTheSharedRouter() {
        let feedApp = fixtureApp(
            "authenticated",
            deepLink: "https://egquata.com/#post-feed-9",
        )
        feedApp.launch()

        XCTAssertEqual(
            QuataIosHostUITestSupport.fixtureRoot(
                in: feedApp,
                identifier: "quata-ios-feed-host",
            ).label,
            "Quata iOS Feed",
        )
        XCTAssertFalse(feedApp.descendants(matching: .any).matching(identifier: "quata-ios-compose-root").firstMatch.exists)

        feedApp.terminate()

        let officialApp = fixtureApp(
            "authenticated",
            deepLink: "https://egquata.com/#official-public-7",
        )
        officialApp.launch()

        XCTAssertEqual(
            QuataIosHostUITestSupport.fixtureRoot(
                in: officialApp,
                identifier: "quata-ios-official-host",
            ).label,
            "Quata iOS Official",
        )
        XCTAssertFalse(officialApp.descendants(matching: .any).matching(identifier: "quata-ios-compose-root").firstMatch.exists)
    }

    func testAuthenticatedFeedShellKeepsSafeViewportAcrossRotation() {
        let device = XCUIDevice.shared
        device.orientation = .portrait
        addTeardownBlock { device.orientation = .portrait }

        let app = fixtureApp("shell-layout")
        app.launch()

        let window = app.windows.firstMatch
        XCTAssertTrue(window.waitForExistence(timeout: 10), "The authenticated fixture must expose one app window.")
        let feed = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-shell-layout-content-frame")
            .firstMatch
        let topChrome = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-authenticated-top-chrome-layout-frame")
            .firstMatch
        let primaryNavigation = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-authenticated-primary-navigation-layout-frame")
            .firstMatch
        XCTAssertTrue(topChrome.waitForExistence(timeout: 10), "The authenticated top chrome must be mounted.")
        XCTAssertTrue(primaryNavigation.waitForExistence(timeout: 10), "The primary navigation must be mounted.")
        XCTAssertTrue(feed.waitForExistence(timeout: 10), "The inert Feed content frame must be mounted by the real shell.")

        assertAuthenticatedViewport(
            window: window,
            content: feed,
            topChrome: topChrome,
            primaryNavigation: primaryNavigation,
            context: "portrait",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-shell-layout-portrait")

        device.orientation = .landscapeLeft
        waitForWindow(window, toBeLandscape: true, context: "landscape")
        assertAuthenticatedViewport(
            window: window,
            content: feed,
            topChrome: topChrome,
            primaryNavigation: primaryNavigation,
            context: "landscape",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-shell-layout-landscape")

        device.orientation = .portrait
        waitForWindow(window, toBeLandscape: false, context: "restored portrait")
        assertAuthenticatedViewport(
            window: window,
            content: feed,
            topChrome: topChrome,
            primaryNavigation: primaryNavigation,
            context: "restored portrait",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-shell-layout-restored-portrait")
    }

    func testAuthenticatedFeedShellOfflineBannerReservesAndRestoresViewport() {
        let device = XCUIDevice.shared
        device.orientation = .portrait
        addTeardownBlock { device.orientation = .portrait }

        let app = fixtureApp("shell-layout", shellOffline: true)
        app.launch()

        let window = app.windows.firstMatch
        let content = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-shell-layout-content-frame")
            .firstMatch
        let topChrome = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-authenticated-top-chrome-layout-frame")
            .firstMatch
        let primaryNavigation = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-authenticated-primary-navigation-layout-frame")
            .firstMatch
        let reconnect = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-shell-layout-reconnect")
            .firstMatch
        let offlineBanner = app.staticTexts["Sin conexión"]

        XCTAssertTrue(window.waitForExistence(timeout: 10))
        XCTAssertTrue(content.waitForExistence(timeout: 10))
        XCTAssertTrue(topChrome.waitForExistence(timeout: 10))
        XCTAssertTrue(primaryNavigation.waitForExistence(timeout: 10))
        XCTAssertTrue(reconnect.waitForExistence(timeout: 10))
        XCTAssertTrue(offlineBanner.waitForExistence(timeout: 10), "The shared offline banner must be visible.")

        assertAuthenticatedViewport(
            window: window,
            content: content,
            topChrome: topChrome,
            primaryNavigation: primaryNavigation,
            context: "offline portrait",
        )
        let offlineTopFrame = topChrome.frame
        let offlineContentFrame = content.frame
        let offlineNavigationFrame = primaryNavigation.frame
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-shell-layout-offline")

        XCTAssertTrue(reconnect.isHittable, "The deterministic reconnect control must be tappable.")
        reconnect.tap()
        XCTAssertTrue(
            offlineBanner.waitForNonExistence(timeout: 90),
            "Reconnect must hide the shared offline banner.",
        )
        XCTAssertEqual(
            topChrome.frame.height,
            offlineTopFrame.height - 28,
            accuracy: 1,
            "Reconnect must return the shared 28-point reservation from top chrome.",
        )
        XCTAssertEqual(
            content.frame.minY,
            offlineContentFrame.minY - 28,
            accuracy: 1,
            "Reconnect must return the shared 28-point reservation to content.",
        )
        assertAuthenticatedViewport(
            window: window,
            content: content,
            topChrome: topChrome,
            primaryNavigation: primaryNavigation,
            context: "restored online portrait",
        )
        XCTAssertEqual(primaryNavigation.frame, offlineNavigationFrame, "Reconnect must not move primary navigation.")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-shell-layout-restored-online")
    }

    func testAuthenticatedShellContainsPrimaryRouteLayoutVariants() {
        assertAuthenticatedShellContainsRouteLayoutVariants([
            ("feed", "quata-ios-feed-host", true),
            ("chat", "quata-ios-chat-host", false),
            ("official", "quata-ios-official-host", true),
            ("official-editor", "quata-ios-official-editor-host", true),
            ("notifications", "quata-ios-notifications-host", true),
            ("profile-sos", "quata-ios-profile-sos-host", true),
        ])
    }

    func testPrimaryRouteSurvivesApplicationTerminationAndRelaunchWithoutRouteReplay() {
        let firstLaunch = fixtureApp("shell-layout", shellRoute: "communities")
        firstLaunch.launch()
        let firstCommunities = firstLaunch.descendants(matching: .any)
            .matching(identifier: "quata-ios-communities-host")
            .firstMatch
        XCTAssertTrue(
            firstCommunities.waitForExistence(timeout: 10),
            "The first process must display Communities before termination.",
        )
        firstLaunch.terminate()

        let relaunched = fixtureApp("shell-layout", resetPrimaryRoute: false)
        relaunched.launch()
        let restoredCommunities = relaunched.descendants(matching: .any)
            .matching(identifier: "quata-ios-communities-host")
            .firstMatch
        XCTAssertTrue(
            restoredCommunities.waitForExistence(timeout: 10),
            "A new process without a route argument must restore the last primary root.",
        )
        XCTAssertFalse(
            relaunched.descendants(matching: .any)
                .matching(identifier: "quata-ios-feed-host")
                .firstMatch.exists,
            "The temporary Feed fallback must be replaced when the restored factory is installed.",
        )
    }

    func testExactFeedAndOfficialPostRoutesSurviveApplicationTerminationAndRelaunch() {
        for scenario in [
            (route: "feed-post:feed/team 9/á?tab=media", expected: "feed-post:feed/team 9/á?tab=media"),
            (route: "official-post:official/team 4/ñ?source=push", expected: "official-post:official/team 4/ñ?source=push"),
        ] {
            let firstLaunch = fixtureApp("shell-layout", shellRoute: scenario.route)
            firstLaunch.launch()
            let firstMarker = firstLaunch.descendants(matching: .any)
                .matching(identifier: "quata-ios-shell-layout-content-frame")
                .firstMatch
            XCTAssertTrue(firstMarker.waitForExistence(timeout: 10))
            XCTAssertEqual(firstMarker.value as? String, scenario.expected)
            firstLaunch.terminate()

            let relaunched = fixtureApp("shell-layout", resetPrimaryRoute: false)
            relaunched.launch()
            let restoredMarker = relaunched.descendants(matching: .any)
                .matching(identifier: "quata-ios-shell-layout-content-frame")
                .firstMatch
            XCTAssertTrue(restoredMarker.waitForExistence(timeout: 10))
            XCTAssertEqual(
                restoredMarker.value as? String,
                scenario.expected,
                "A new process without route injection must restore the exact focused post.",
            )
            relaunched.terminate()
        }
    }

    func testSafeSecondaryRouteSurvivesApplicationTerminationAndRelaunchWithoutRouteReplay() {
        let firstLaunch = fixtureApp("shell-layout", shellRoute: "settings")
        firstLaunch.launch()
        XCTAssertTrue(
            firstLaunch.descendants(matching: .any)
                .matching(identifier: "quata-ios-settings-host")
                .firstMatch.waitForExistence(timeout: 10),
            "The first process must display Settings before termination.",
        )
        firstLaunch.terminate()

        let relaunched = fixtureApp("shell-layout", resetPrimaryRoute: false)
        relaunched.launch()
        XCTAssertTrue(
            relaunched.descendants(matching: .any)
                .matching(identifier: "quata-ios-settings-host")
                .firstMatch.waitForExistence(timeout: 10),
            "A new process without a route argument must restore the safe secondary route.",
        )
        XCTAssertFalse(
            relaunched.descendants(matching: .any)
                .matching(identifier: "quata-ios-feed-host")
                .firstMatch.exists,
            "The temporary Feed fallback must be replaced when the restored secondary factory is installed.",
        )
    }

    func testWhatsNewComposerAndOfficialEditorSurviveApplicationTerminationWithoutRouteReplay() {
        for scenario in [
            (route: "whats-new", host: "quata-ios-whats-new-host"),
            (route: "composer", host: "quata-ios-composer-host"),
            (route: "official-editor", host: "quata-ios-official-editor-host"),
        ] {
            let firstLaunch = fixtureApp("shell-layout", shellRoute: scenario.route)
            firstLaunch.launch()
            XCTAssertTrue(
                firstLaunch.descendants(matching: .any)
                    .matching(identifier: scenario.host)
                    .firstMatch.waitForExistence(timeout: 10),
                "The first process must display \(scenario.route) before termination."
            )
            firstLaunch.terminate()

            let relaunched = fixtureApp("shell-layout", resetPrimaryRoute: false)
            relaunched.launch()
            XCTAssertTrue(
                relaunched.descendants(matching: .any)
                    .matching(identifier: scenario.host)
                    .firstMatch.waitForExistence(timeout: 10),
                "A new process must restore \(scenario.route) without route injection."
            )
            relaunched.terminate()
        }
    }

    func testAuthenticatedShellContainsSecondaryRouteLayoutVariants() {
        assertAuthenticatedShellContainsRouteLayoutVariants([
            ("communities", "quata-ios-communities-host", true),
            ("composer", "quata-ios-composer-host", true),
            ("settings", "quata-ios-settings-host", true),
            ("whats-new", "quata-ios-whats-new-host", true),
            ("about", "quata-ios-about-host", true),
            ("release-history", "quata-ios-release-history-host", true),
        ])
    }

    private func assertAuthenticatedShellContainsRouteLayoutVariants(
        _ scenarios: [(route: String, host: String, hasPrimaryNavigation: Bool)],
    ) {
        let device = XCUIDevice.shared
        device.orientation = .portrait
        addTeardownBlock { device.orientation = .portrait }

        for scenario in scenarios {
            let app = fixtureApp("shell-layout", shellRoute: scenario.route)
            app.launch()

            let window = app.windows.firstMatch
            let host = app.descendants(matching: .any).matching(identifier: scenario.host).firstMatch
            let content = app.descendants(matching: .any)
                .matching(identifier: "quata-ios-shell-layout-content-frame")
                .firstMatch
            let topChrome = app.descendants(matching: .any)
                .matching(identifier: "quata-ios-authenticated-top-chrome-layout-frame")
                .firstMatch
            let primaryNavigation = app.descendants(matching: .any)
                .matching(identifier: "quata-ios-authenticated-primary-navigation-layout-frame")
                .firstMatch

            XCTAssertTrue(window.waitForExistence(timeout: 10), "[\(scenario.route)] The app window must exist.")
            XCTAssertTrue(host.waitForExistence(timeout: 10), "[\(scenario.route)] The real router must expose the selected route host.")
            XCTAssertTrue(content.waitForExistence(timeout: 10), "[\(scenario.route)] The selected route content frame must exist.")
            XCTAssertEqual(content.value as? String, scenario.route)
            XCTAssertTrue(topChrome.waitForExistence(timeout: 10), "[\(scenario.route)] Top chrome must remain mounted.")

            if scenario.hasPrimaryNavigation {
                XCTAssertTrue(
                    primaryNavigation.waitForExistence(timeout: 10),
                    "[\(scenario.route)] Primary navigation must remain mounted.",
                )
                assertAuthenticatedViewport(
                    window: window,
                    content: content,
                    topChrome: topChrome,
                    primaryNavigation: primaryNavigation,
                    context: scenario.route,
                )
            } else {
                XCTAssertFalse(
                    primaryNavigation.exists,
                    "[\(scenario.route)] Chat must preserve its product rule that hides primary navigation.",
                )
                assertAuthenticatedViewportWithoutPrimaryNavigation(
                    window: window,
                    content: content,
                    topChrome: topChrome,
                    context: scenario.route,
                )
            }
            QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-shell-layout-route-\(scenario.route)")
            app.terminate()
        }
    }

    func testAuthenticatedFixtureRendersEverySupportedPublicDeepLinkRouteWithStableAccessibility() {
        let scenarios: [(deepLink: String, identifier: String, label: String, evidence: String)] = [
            ("https://egquata.com/#post-feed-9", "quata-ios-feed-host", "Quata iOS Feed", "fixture-feed"),
            ("https://egquata.com/#chat-conversation-7?message=message-4", "quata-ios-chat-host", "Quata iOS Chat", "fixture-chat"),
            ("https://egquata.com/#official-public-7", "quata-ios-official-host", "Quata iOS Official", "fixture-official"),
            // RichTextEditorQa intentionally resolves to Official(nil) at the production iOS
            // adapter boundary until that diagnostic surface has its own native destination.
            ("https://egquata.com/#editor-qa", "quata-ios-official-host", "Quata iOS Official", "fixture-editor-qa-via-official"),
            ("https://egquata.com/#whats-new", "quata-ios-whats-new-host", "Quata iOS What's New", "fixture-whats-new"),
            ("https://egquata.com/#about", "quata-ios-about-host", "Quata iOS About", "fixture-about"),
            ("https://egquata.com/#release-history", "quata-ios-release-history-host", "Quata iOS Release History", "fixture-release-history"),
        ]

        for scenario in scenarios {
            let app = fixtureApp("authenticated", deepLink: scenario.deepLink)
            app.launch()
            QuataIosHostUITestSupport.assertFixtureRoute(
                in: app,
                identifier: scenario.identifier,
                label: scenario.label,
                screenshotName: scenario.evidence,
            )
            app.terminate()
        }
    }

    func testAboutReleaseHistoryFixtureRendersRealSharedComposeSurfaces() {
        let app = fixtureApp("about-release-history", spanishLocale: true)
        app.launch()

        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "about-common-root")
                .firstMatch
                .waitForExistence(timeout: 15),
            "The iOS About evidence fixture must mount the real shared Compose About dialog.",
        )
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "about-release-history")
                .firstMatch
                .waitForExistence(timeout: 10),
            "The shared About action must be exposed before opening Release History.",
        )
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "legal-document-link-privacy")
                .firstMatch
                .waitForExistence(timeout: 10),
            "The shared privacy legal document action must be exposed on iOS.",
        )
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "legal-document-link-childsafety")
                .firstMatch
                .waitForExistence(timeout: 10),
            "The shared child safety legal document action must be exposed on iOS.",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "about-release-history-real-about")

        app.descendants(matching: .any)
            .matching(identifier: "legal-document-link-privacy")
            .firstMatch
            .tap()
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "legal-document-opened-privacy_es.docx")
                .firstMatch
                .waitForExistence(timeout: 10),
            "The iOS About fixture must resolve Privacy to the packaged Spanish DOCX.",
        )
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "document-viewer-status-root")
                .firstMatch
                .waitForExistence(timeout: 10),
            "The iOS About legal fixture must render the common document viewer status chrome.",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "about-legal-document-viewer-status")
        app.descendants(matching: .any)
            .matching(identifier: "document-viewer-status-close")
            .firstMatch
            .tap()
        app.descendants(matching: .any)
            .matching(identifier: "legal-document-link-childsafety")
            .firstMatch
            .tap()
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "legal-document-opened-child_safety_es.docx")
                .firstMatch
                .waitForExistence(timeout: 10),
            "The iOS About fixture must resolve Child Safety to the packaged Spanish DOCX.",
        )
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "document-viewer-status-root")
                .firstMatch
                .waitForExistence(timeout: 10),
            "The iOS About child-safety document must keep the common viewer status chrome visible.",
        )
        app.descendants(matching: .any)
            .matching(identifier: "document-viewer-status-close")
            .firstMatch
            .tap()

        app.descendants(matching: .any)
            .matching(identifier: "about-release-history")
            .firstMatch
            .tap()
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "release-history-common-root")
                .firstMatch
                .waitForExistence(timeout: 15),
            "The iOS About action must navigate to the real shared Release History Compose surface.",
        )
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "release-history-page-0")
                .firstMatch
                .waitForExistence(timeout: 10),
            "Release History must expose a real common page for visual evidence.",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "about-release-history-real-release-history")
    }

    func testProfileLegalFixtureRendersSharedAccountLegalLinks() {
        let app = fixtureApp("profile-legal", spanishLocale: true)
        app.launch()

        for identifier in [
            "legal-document-link-privacy",
            "legal-document-link-childsafety",
        ] {
            XCTAssertTrue(
                app.descendants(matching: .any)
                    .matching(identifier: identifier)
                    .firstMatch
                    .waitForExistence(timeout: 15),
                "The shared Cuenta legal semantic \(identifier) must be available in the iOS profile fixture.",
            )
        }
        QuataIosHostUITestSupport.attachRenderedSurface(named: "profile-legal-account")

        app.descendants(matching: .any)
            .matching(identifier: "legal-document-link-privacy")
            .firstMatch
            .tap()
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "legal-document-opened-privacy_es.docx")
                .firstMatch
                .waitForExistence(timeout: 10),
            "The iOS Cuenta fixture must resolve Privacy to the packaged Spanish DOCX.",
        )
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "document-viewer-status-root")
                .firstMatch
                .waitForExistence(timeout: 10),
            "The iOS Cuenta legal fixture must render the shared document viewer status chrome.",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "profile-legal-document-viewer-status")
        app.descendants(matching: .any)
            .matching(identifier: "document-viewer-status-close")
            .firstMatch
            .tap()
        app.descendants(matching: .any)
            .matching(identifier: "legal-document-link-childsafety")
            .firstMatch
            .tap()
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "legal-document-opened-child_safety_es.docx")
                .firstMatch
                .waitForExistence(timeout: 10),
            "The iOS Cuenta fixture must resolve Child Safety to the packaged Spanish DOCX.",
        )
    }

    func testProfileSosFixtureRendersSharedContactsEditorAnchors() {
        let app = fixtureApp("profile-legal", spanishLocale: true)
        app.launch()

        let openSos = app.descendants(matching: .any)
            .matching(identifier: "profile.sos.open")
            .firstMatch
        XCTAssertTrue(
            openSos.waitForExistence(timeout: 15),
            "The real shared Cuenta fixture must expose the SOS settings action.",
        )
        XCTAssertTrue(openSos.isHittable, "The shared SOS action must be tappable.")
        openSos.tap()

        for identifier in [
            "profile.sos.root",
            "profile.sos.tab.contacts",
            "profile.sos.tab.message",
            "profile.sos.contacts.list",
            "profile.sos.search",
            "profile.sos.contact.sos-fixture-1",
            "profile.sos.contact.toggle.sos-fixture-1",
            "profile.sos.contact.toggle.sos-fixture-6",
        ] {
            XCTAssertTrue(
                app.descendants(matching: .any)
                    .matching(identifier: identifier)
                    .firstMatch
                    .waitForExistence(timeout: 10),
                "The shared SOS semantic \(identifier) must be available in the iOS Cuenta fixture.",
            )
        }
        QuataIosHostUITestSupport.attachRenderedSurface(named: "profile-sos-contacts")

        app.descendants(matching: .any)
            .matching(identifier: "profile.sos.tab.message")
            .firstMatch
            .coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
            .tap()
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "profile.sos.message.input")
                .firstMatch
                .waitForExistence(timeout: 10),
            "The shared SOS message input must be exposed after switching tabs.",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "profile-sos-message")
    }

    func testAuthenticatedProfileSosKeepsRealComposeDraftAboveGlobalKeyboardAcrossRotation() {
        let device = XCUIDevice.shared
        device.orientation = .portrait
        addTeardownBlock { device.orientation = .portrait }

        let app = fixtureApp(
            "shell-layout",
            spanishLocale: true,
            shellRoute: "profile-sos",
            exposeKeyboardBackdrop: true,
        )
        app.launch()

        let window = app.windows.firstMatch
        let content = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-shell-layout-content-frame")
            .firstMatch
        let topChrome = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-authenticated-top-chrome-layout-frame")
            .firstMatch
        let primaryNavigation = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-authenticated-primary-navigation-layout-frame")
            .firstMatch
        XCTAssertTrue(window.waitForExistence(timeout: 10))
        XCTAssertTrue(content.waitForExistence(timeout: 10))
        XCTAssertTrue(topChrome.waitForExistence(timeout: 10))
        XCTAssertTrue(primaryNavigation.waitForExistence(timeout: 10))

        let openSos = app.descendants(matching: .any)
            .matching(identifier: "profile.sos.open")
            .firstMatch
        XCTAssertTrue(
            openSos.waitForExistence(timeout: 15),
            "The production authenticated router must contain the real shared Profile surface.",
        )
        XCTAssertTrue(openSos.isHittable, "The real Profile SOS entry action must receive input through the evidence marker.")
        openSos.tap()

        let search = app.descendants(matching: .any)
            .matching(identifier: "profile.sos.search")
            .firstMatch
        XCTAssertTrue(search.waitForExistence(timeout: 10))
        let searchMarker = "qa"
        search.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        let focusedSearch = app.descendants(matching: .any)
            .matching(identifier: search.identifier)
            .matching(NSPredicate(format: "hasKeyboardFocus == 1"))
            .firstMatch
        XCTAssertTrue(
            focusedSearch.waitForExistence(timeout: 2),
            "The real Profile SOS search field must receive focus at its observed center.",
        )
        focusedSearch.typeText(searchMarker)
        assertAuthenticatedProfileSearch(
            search,
            contains: searchMarker,
            ownsKeyboardIn: app,
            context: "Profile SOS search portrait",
        )

        device.orientation = .landscapeLeft
        waitForWindow(window, toBeLandscape: true, context: "Profile SOS search landscape")
        assertAuthenticatedProfileSearch(
            search,
            contains: searchMarker,
            ownsKeyboardIn: app,
            context: "Profile SOS search landscape",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-profile-sos-search-keyboard-landscape")

        device.orientation = .portrait
        waitForWindow(window, toBeLandscape: false, context: "Profile SOS search restored portrait")
        assertAuthenticatedProfileSearch(
            search,
            contains: searchMarker,
            ownsKeyboardIn: app,
            context: "Profile SOS search restored portrait",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-profile-sos-search-keyboard-restored-portrait")

        let searchDone = app.keyboards.buttons["Return"]
        XCTAssertTrue(
            searchDone.waitForExistence(timeout: 2),
            "The restored SOS search keyboard must expose Done before switching tabs.",
        )
        searchDone.tap()
        let searchKeyboardDismissed = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "exists == false"),
            object: app.keyboards.firstMatch,
        )
        XCTAssertEqual(
            XCTWaiter.wait(for: [searchKeyboardDismissed], timeout: 5),
            .completed,
            "Done must release SOS search focus and restore the portrait header.",
        )

        let messageTab = app.descendants(matching: .any)
            .matching(identifier: "profile.sos.tab.message")
            .firstMatch
        XCTAssertTrue(messageTab.waitForExistence(timeout: 10))
        messageTab.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()

        let input = app.descendants(matching: .any)
            .matching(identifier: "profile.sos.message.input")
            .firstMatch
        XCTAssertTrue(input.waitForExistence(timeout: 10))
        let marker = " teclado global"
        input.tap()
        input.typeText(marker)
        assertAuthenticatedProfileInput(
            input,
            contains: marker,
            aboveGlobalKeyboardIn: app,
            context: "Profile SOS portrait",
        )
        assertAuthenticatedViewport(
            window: window,
            content: content,
            topChrome: topChrome,
            primaryNavigation: primaryNavigation,
            context: "profile-sos keyboard portrait",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-profile-sos-global-keyboard-portrait")

        device.orientation = .landscapeLeft
        waitForWindow(window, toBeLandscape: true, context: "Profile SOS keyboard landscape")
        assertAuthenticatedProfileInput(
            input,
            contains: marker,
            aboveGlobalKeyboardIn: app,
            context: "Profile SOS landscape",
        )
        assertAuthenticatedViewport(
            window: window,
            content: content,
            topChrome: topChrome,
            primaryNavigation: primaryNavigation,
            context: "profile-sos keyboard landscape",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-profile-sos-global-keyboard-landscape")

        device.orientation = .portrait
        waitForWindow(window, toBeLandscape: false, context: "Profile SOS keyboard restored portrait")
        assertAuthenticatedProfileInput(
            input,
            contains: marker,
            aboveGlobalKeyboardIn: app,
            context: "Profile SOS restored portrait",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-profile-sos-global-keyboard-restored-portrait")
    }

    func testProfileSosSaveFailureKeepsSharedErrorInDialog() {
        let app = fixtureApp("profile-sos-retry", spanishLocale: true)
        app.launch()

        let openSos = app.descendants(matching: .any)
            .matching(identifier: "profile.sos.open")
            .firstMatch
        XCTAssertTrue(openSos.waitForExistence(timeout: 15))
        openSos.tap()

        let editor = app.descendants(matching: .any)
            .matching(identifier: "profile.sos.root")
            .firstMatch
        XCTAssertTrue(editor.waitForExistence(timeout: 10))
        let firstContact = visibleSosContactToggle(index: 1, in: app)
        firstContact.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        let firstContactSelected = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "value == %@", "Remove"),
            object: firstContact,
        )
        XCTAssertEqual(
            XCTWaiter.wait(for: [firstContactSelected], timeout: 10),
            .completed,
            "The first SOS contact must be selected before the save gesture.",
        )
        XCTAssertTrue(editor.exists, "Selecting a contact must not consume the forced save failure.")
        RunLoop.current.run(until: Date().addingTimeInterval(0.4))
        let save = app.descendants(matching: .any)
            .matching(identifier: "profile.sos.save")
            .firstMatch
        XCTAssertTrue(save.waitForExistence(timeout: 10))
        save.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()

        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "profile.sos.error")
                .firstMatch
                .waitForExistence(timeout: 10),
            "A failed SOS save must expose the shared error anchor inside the dialog.",
        )
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "profile.sos.root")
                .firstMatch
                .exists,
            "A failed SOS save must keep the shared editor open for retry.",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "profile-sos-save-error")
    }

    func testProfileSosRejectsSixthContactAndRetriesExactEditedSettings() {
        let device = XCUIDevice.shared
        device.orientation = .portrait
        addTeardownBlock { device.orientation = .portrait }
        let app = fixtureApp("profile-sos-retry", spanishLocale: true)
        app.launch()
        let window = app.windows.firstMatch
        XCTAssertTrue(window.waitForExistence(timeout: 10))

        let openSos = app.descendants(matching: .any)
            .matching(identifier: "profile.sos.open")
            .firstMatch
        XCTAssertTrue(openSos.waitForExistence(timeout: 15))
        openSos.tap()

        let contactsList = app.descendants(matching: .any)
            .matching(identifier: "profile.sos.contacts.list")
            .firstMatch
        XCTAssertTrue(contactsList.waitForExistence(timeout: 10))
        if app.keyboards.firstMatch.exists {
            let done = app.keyboards.buttons["Return"]
            XCTAssertTrue(done.exists, "The focused SOS search field must expose its Done action before contact selection.")
            done.tap()
            XCTAssertFalse(
                app.keyboards.firstMatch.waitForExistence(timeout: 2),
                "The SOS search Done action must release focus before selecting contacts.",
            )
        }
        for index in 1...5 {
            let toggle = visibleSosContactToggle(index: index, in: app)
            toggle.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
            let selectedToggle = app.buttons
                .matching(identifier: "profile.sos.contact.toggle.sos-fixture-\(index)")
                .firstMatch
            let selected = XCTNSPredicateExpectation(
                predicate: NSPredicate(format: "value == %@", "Remove"),
                object: selectedToggle,
            )
            XCTAssertEqual(
                XCTWaiter.wait(for: [selected], timeout: 10),
                .completed,
                "SOS contact \(index) must expose its selected state before the next interaction.",
            )
        }
        let sixth = visibleSosContactToggle(index: 6, in: app)
        sixth.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        let rejectedSixth = app.buttons
            .matching(identifier: "profile.sos.contact.toggle.sos-fixture-6")
            .firstMatch
        XCTAssertEqual(
            rejectedSixth.value as? String,
            "Add",
            "The sixth SOS contact must remain visibly unselected when the shared limit is reached.",
        )
        for index in 1...5 {
            XCTAssertEqual(
                app.buttons
                    .matching(identifier: "profile.sos.contact.toggle.sos-fixture-\(index)")
                    .firstMatch
                    .value as? String,
                "Remove",
                "The first five SOS contacts must remain visibly selected before save.",
            )
        }

        let currentContactsList = app.descendants(matching: .any)
            .matching(identifier: "profile.sos.contacts.list")
            .firstMatch
        currentContactsList.swipeDown()
        currentContactsList.swipeDown()
        if app.keyboards.firstMatch.exists {
            let done = app.keyboards.buttons["Return"]
            XCTAssertTrue(done.exists, "The focused SOS search field must expose its Done action.")
            done.tap()
            XCTAssertFalse(
                app.keyboards.firstMatch.waitForExistence(timeout: 2),
                "The SOS search Done action must release focus and restore the portrait header.",
            )
        }
        let messageTab = app.descendants(matching: .any)
            .matching(identifier: "profile.sos.tab.message")
            .firstMatch
        XCTAssertTrue(messageTab.waitForExistence(timeout: 10))
        messageTab.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()

        let message = app.descendants(matching: .any)
            .matching(identifier: "profile.sos.message.input")
            .firstMatch
        XCTAssertTrue(message.waitForExistence(timeout: 10))
        let suffix = " [iOS retry exacto]"
        message.tap()
        message.typeText(suffix)
        let editedMessageVisible = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "value CONTAINS %@", suffix),
            object: message,
        )
        XCTAssertEqual(
            XCTWaiter.wait(for: [editedMessageVisible], timeout: 10),
            .completed,
            "The edited SOS message must be visible before saving.",
        )

        let save = app.descendants(matching: .any)
            .matching(identifier: "profile.sos.save")
            .firstMatch
        XCTAssertTrue(save.waitForExistence(timeout: 10))
        let messageKeyboard = app.keyboards.firstMatch
        XCTAssertTrue(messageKeyboard.exists, "Editing the exact SOS message must own the software keyboard before save.")
        XCTAssertTrue(save.isHittable, "The shared SOS save action must remain hittable above the message keyboard.")
        XCTAssertLessThanOrEqual(
            save.frame.maxY,
            messageKeyboard.frame.minY,
            "The shared portrait save action must be laid out above the software keyboard.",
        )
        save.tap()
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "profile.sos.error")
                .firstMatch
                .waitForExistence(timeout: 10),
            "The first save must fail visibly without closing the shared editor.",
        )
        XCTAssertFalse(
            app.descendants(matching: .any)
                .matching(identifier: "profile-sos-save-success")
                .firstMatch
                .exists,
            "A failed first attempt must not manufacture a successful save marker.",
        )

        save.tap()
        let success = app.descendants(matching: .any)
            .matching(identifier: "profile-sos-save-success")
            .firstMatch
        XCTAssertTrue(success.waitForExistence(timeout: 10), "Retry must reach the same repository through the shared save action.")
        let expectedPayload = [
            "sos-fixture-1,sos-fixture-2,sos-fixture-3,sos-fixture-4,sos-fixture-5",
            "Avisar a mis contactos de emergencia.\(suffix)",
            "false",
        ].joined(separator: "\u{001F}")
        XCTAssertEqual(success.label, expectedPayload, "Retry must preserve the five selected contacts and exact edited message.")
        XCTAssertFalse(
            app.descendants(matching: .any)
                .matching(identifier: "profile.sos.root")
                .firstMatch
                .exists,
            "A successful retry must close the shared SOS editor.",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "profile-sos-save-retry-success")
    }

    func testWhatsNewFixtureRendersMarksSeenAndDoesNotRepeat() {
        let app = fixtureApp("whats-new-real", spanishLocale: true, resetWhatsNew: true)
        app.launch()

        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "whats-new-common-root")
                .firstMatch
                .waitForExistence(timeout: 15),
            "The iOS What's New evidence fixture must mount the real shared Compose surface.",
        )
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "whats-new-page-0")
                .firstMatch
                .waitForExistence(timeout: 10),
            "What's New must expose a real common page for visual evidence.",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "whats-new-real-page-0")

        app.descendants(matching: .any)
            .matching(identifier: "whats-new-next")
            .firstMatch
            .tap()
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "quata-ios-whats-new-closed")
                .firstMatch
                .waitForExistence(timeout: 15),
            "Completing What's New must close the real shared surface.",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "whats-new-real-closed")
        app.terminate()

        let repeatedApp = fixtureApp("whats-new-real", spanishLocale: true)
        repeatedApp.launch()
        XCTAssertTrue(
            repeatedApp.descendants(matching: .any)
                .matching(identifier: "quata-ios-whats-new-closed")
                .firstMatch
                .waitForExistence(timeout: 15),
            "A release already marked as seen must not render again on iOS.",
        )
        XCTAssertFalse(
            repeatedApp.descendants(matching: .any)
                .matching(identifier: "whats-new-common-root")
                .firstMatch
                .waitForExistence(timeout: 2),
            "The second iOS launch must close without showing the shared What's New root.",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "whats-new-real-not-repeated")
    }

    func testAuthenticatedFixtureRendersInAppOnlyRoutesThroughTheSharedRouterAdapter() {
        // These destinations deliberately do not have public URL contracts. The fixture reaches
        // them through IosAuthenticatedRouteDispatcher's real in-app methods, which prevents a
        // Swift test double from masking a broken Kotlin-to-UIKit route boundary.
        let scenarios: [(route: String, identifier: String, label: String)] = [
            ("notifications", "quata-ios-notifications-host", "Quata iOS Notifications"),
            ("profile-sos", "quata-ios-profile-sos-host", "Quata iOS Profile and SOS"),
            ("communities", "quata-ios-communities-host", "Quata iOS Communities"),
            ("composer", "quata-ios-composer-host", "Quata iOS Composer"),
            ("settings", "quata-ios-settings-host", "Quata iOS Settings"),
        ]

        for scenario in scenarios {
            let app = fixtureApp("authenticated", inAppRoute: scenario.route)
            app.launch()
            QuataIosHostUITestSupport.assertFixtureRoute(
                in: app,
                identifier: scenario.identifier,
                label: scenario.label,
                screenshotName: "fixture-\(scenario.route)",
            )
            app.terminate()
        }
    }

    func testNotificationsRealFixtureOpensExactConversationFromSharedContent() {
        let app = fixtureApp("notifications-real", spanishLocale: true)
        app.launch()

        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "notifications.root")
                .firstMatch
                .waitForExistence(timeout: 15),
            "The real iOS Notifications fixture must mount the shared Notifications root.",
        )
        let row = app.descendants(matching: .any)
            .matching(identifier: "notifications.item.conversation-ios")
            .firstMatch
        XCTAssertTrue(
            row.waitForExistence(timeout: 10),
            "The shared notification row must be available through common semantics.",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "notifications-real-list")
        row.tap()

        let opened = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-notifications-opened-chat")
            .firstMatch
        XCTAssertTrue(
            opened.waitForExistence(timeout: 10),
            "Tapping a notification must dispatch the exact conversation destination.",
        )
        XCTAssertEqual(opened.label, "Quata iOS Notifications opened conversation-ios")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "notifications-real-opened-chat")
    }

    func testUnknownInAppFixtureRouteFailsClosedWithoutRenderingAProtectedSurface() {
        let app = fixtureApp("authenticated", inAppRoute: "not-a-quata-route")
        app.launch()

        XCTAssertEqual(
            QuataIosHostUITestSupport.fixtureRoot(
                in: app,
                identifier: "quata-ios-test-invalid-route",
            ).label,
            "Quata iOS invalid fixture route",
        )
        XCTAssertFalse(
            app.descendants(matching: .any).matching(identifier: "quata-ios-feed-host").firstMatch.exists,
            "An unknown fixture route must not silently fall back to Feed.",
        )
        XCTAssertFalse(
            app.descendants(matching: .any).matching(identifier: "quata-ios-chat-host").firstMatch.exists,
            "An unknown fixture route must not expose a protected destination.",
        )
        XCTAssertFalse(
            app.descendants(matching: .any).matching(identifier: "quata-ios-compose-root").firstMatch.exists,
            "An unknown fixture route must not construct Compose or a session-backed host.",
        )
    }

    private func fixtureApp(
        _ fixture: String,
        deepLink: String? = nil,
        inAppRoute: String? = nil,
        authDestination: String? = nil,
        spanishLocale: Bool = false,
        resetWhatsNew: Bool = false,
        profileSosSaveError: Bool = false,
        shellOffline: Bool = false,
        shellRoute: String? = nil,
        exposeKeyboardBackdrop: Bool = false,
        resetPrimaryRoute: Bool = true,
    ) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-quata-ui-test-fixture", fixture]
        if resetPrimaryRoute { app.launchArguments += ["-quata-ui-test-reset-primary-route"] }
        if spanishLocale {
            app.launchArguments += [
                "-AppleLanguages", "(es)",
                "-AppleLocale", "es_ES",
                "-quata-ui-test-language", "es",
            ]
        }
        if let deepLink { app.launchArguments += ["-quata-ui-test-deep-link", deepLink] }
        if let inAppRoute { app.launchArguments += ["-quata-ui-test-in-app-route", inAppRoute] }
        if let authDestination { app.launchArguments += ["-quata-auth-destination", authDestination] }
        if resetWhatsNew { app.launchArguments += ["-quata-ui-test-reset-whats-new"] }
        if profileSosSaveError {
            app.launchArguments += ["-quata-ui-test-profile-sos-save-error"]
            app.launchEnvironment["QUATA_UI_TEST_PROFILE_SOS_SAVE_ERROR"] = "1"
        }
        if shellOffline { app.launchArguments += ["-quata-ui-test-shell-offline"] }
        if let shellRoute { app.launchArguments += ["-quata-ui-test-shell-route", shellRoute] }
        if exposeKeyboardBackdrop { app.launchArguments += ["-quata-ui-test-expose-keyboard-backdrop"] }
        return app
    }

    private func visibleSosContactToggle(index: Int, in app: XCUIApplication) -> XCUIElement {
        let query = app.buttons
            .matching(identifier: "profile.sos.contact.toggle.sos-fixture-\(index)")
        for attempt in 0..<8 {
            let toggle = query.firstMatch
            let contactsList = app.descendants(matching: .any)
                .matching(identifier: "profile.sos.contacts.list")
                .firstMatch
            XCTAssertTrue(contactsList.exists)
            let visibleHeight = contactsList.frame.intersection(toggle.frame).height
            if toggle.exists && !toggle.frame.isEmpty && visibleHeight >= toggle.frame.height * 0.6 {
                return toggle
            }
            if attempt < 7 {
                if toggle.exists && toggle.frame.midY < contactsList.frame.midY {
                    contactsList.swipeDown()
                } else {
                    contactsList.swipeUp()
                }
                RunLoop.current.run(until: Date().addingTimeInterval(0.4))
            }
        }
        XCTFail("SOS contact \(index) must enter the visible shared-list viewport after bounded scrolling.")
        return query.firstMatch
    }

    private func waitForWindow(
        _ window: XCUIElement,
        toBeLandscape: Bool,
        context: String,
        timeout: TimeInterval = 10,
    ) {
        let expectation = XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in
                let frame = window.frame
                return toBeLandscape ? frame.width > frame.height : frame.height > frame.width
            },
            object: window,
        )
        XCTAssertEqual(
            XCTWaiter.wait(for: [expectation], timeout: timeout),
            .completed,
            "The app window must reach \(context) before checking its safe viewport.",
        )
    }

    private func assertFocusedInput(
        _ input: XCUIElement,
        containsDigits expectedDigits: String,
        aboveKeyboardIn app: XCUIApplication,
        context: String,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) {
        guard input.waitForExistence(timeout: 10) else {
            QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-auth-keyboard-missing-\(context.replacingOccurrences(of: " ", with: "-").lowercased())")
            XCTFail("The focused input must remain mounted in \(context).", file: file, line: line)
            return
        }
        let value = ((input.value as? String) ?? input.label).filter(\.isNumber)
        XCTAssertTrue(value.contains(expectedDigits), "The exact Auth phone draft must survive \(context); value=\(value).", file: file, line: line)
        let visibleInputFrame = input.frame
        XCTAssertFalse(visibleInputFrame.isEmpty, "The Auth input must have visible bounds before restoring its keyboard in \(context).", file: file, line: line)
        let keyboard = app.keyboards.firstMatch
        if !keyboard.exists {
            input.tap()
        }
        guard keyboard.waitForExistence(timeout: 10) else {
            XCTFail("The software keyboard must be restorable in \(context).", file: file, line: line)
            return
        }
        let focusedInput = app.descendants(matching: .any)
            .matching(identifier: input.identifier)
            .matching(NSPredicate(format: "hasKeyboardFocus == 1"))
            .firstMatch
        XCTAssertTrue(focusedInput.waitForExistence(timeout: 2), "The exact Auth input must own keyboard focus in \(context).", file: file, line: line)
        let observedInputFrame = focusedInput.frame
        XCTAssertFalse(observedInputFrame.isEmpty, "The focused input must have layout bounds in \(context).", file: file, line: line)
        XCTAssertLessThanOrEqual(
            observedInputFrame.maxY,
            keyboard.frame.minY + 1,
            "The focused Auth input must stay above the software keyboard in \(context).",
            file: file,
            line: line,
        )
    }

    private func assertAuthenticatedProfileInput(
        _ input: XCUIElement,
        contains marker: String,
        aboveGlobalKeyboardIn app: XCUIApplication,
        context: String,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) {
        XCTAssertTrue(input.waitForExistence(timeout: 10), "The shared Profile input must remain mounted in \(context).", file: file, line: line)
        let value = (input.value as? String) ?? input.label
        XCTAssertTrue(value.contains(marker), "The exact Profile SOS draft must survive \(context); value=\(value).", file: file, line: line)

        let keyboard = app.keyboards.firstMatch
        XCTAssertTrue(keyboard.waitForExistence(timeout: 10), "The software keyboard must remain visible in \(context).", file: file, line: line)
        let focusedInput = app.descendants(matching: .any)
            .matching(identifier: input.identifier)
            .matching(NSPredicate(format: "hasKeyboardFocus == 1"))
            .firstMatch
        XCTAssertTrue(focusedInput.waitForExistence(timeout: 2), "The exact Profile input must own keyboard focus in \(context).", file: file, line: line)
        XCTAssertLessThanOrEqual(
            focusedInput.frame.maxY,
            keyboard.frame.minY + 1,
            "The real shared Profile input must stay above the software keyboard in \(context).",
            file: file,
            line: line,
        )

        let backdrop = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-keyboard-opaque-backdrop")
            .firstMatch
        XCTAssertTrue(backdrop.waitForExistence(timeout: 5), "The production router backdrop must be exposed while the keyboard is visible in \(context).", file: file, line: line)
        let measuredFrame = keyboardBackdropFrame(from: backdrop, context: context, file: file, line: line)
        let keyboardFrame = keyboard.frame
        let tolerance: CGFloat = 1
        XCTAssertLessThanOrEqual(
            measuredFrame.minX,
            keyboardFrame.minX + tolerance,
            "The opaque backdrop must begin at or before the software keyboard in \(context).",
            file: file,
            line: line,
        )
        XCTAssertLessThanOrEqual(
            measuredFrame.minY,
            keyboardFrame.minY + tolerance,
            "The opaque backdrop must begin at or above the software keyboard in \(context).",
            file: file,
            line: line,
        )
        XCTAssertGreaterThanOrEqual(
            measuredFrame.maxX,
            keyboardFrame.maxX - tolerance,
            "The opaque backdrop must cover the software keyboard's trailing edge in \(context).",
            file: file,
            line: line,
        )
        XCTAssertGreaterThanOrEqual(
            measuredFrame.maxY,
            keyboardFrame.maxY - tolerance,
            "The opaque backdrop must cover the software keyboard's bottom edge in \(context).",
            file: file,
            line: line,
        )
    }

    private func assertAuthenticatedProfileSearch(
        _ search: XCUIElement,
        contains marker: String,
        ownsKeyboardIn app: XCUIApplication,
        context: String,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) {
        XCTAssertTrue(search.waitForExistence(timeout: 10), "The Profile SOS search must remain mounted in \(context).", file: file, line: line)
        let value = (search.value as? String) ?? search.label
        XCTAssertTrue(value.contains(marker), "The exact Profile SOS search draft must survive \(context); value=\(value).", file: file, line: line)
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 10), "The search keyboard must remain visible in \(context).", file: file, line: line)

        let focusedSearch = app.descendants(matching: .any)
            .matching(identifier: search.identifier)
            .matching(NSPredicate(format: "hasKeyboardFocus == 1"))
            .firstMatch
        XCTAssertTrue(focusedSearch.waitForExistence(timeout: 2), "The search input must retain keyboard focus in \(context).", file: file, line: line)

        let focusedMessage = app.descendants(matching: .any)
            .matching(identifier: "profile.sos.message.input")
            .matching(NSPredicate(format: "hasKeyboardFocus == 1"))
            .firstMatch
        XCTAssertFalse(focusedMessage.exists, "Rotating a focused search must not transfer focus to the SOS message in \(context).", file: file, line: line)
    }

    private func keyboardBackdropFrame(
        from backdrop: XCUIElement,
        context: String,
        file: StaticString,
        line: UInt,
    ) -> CGRect {
        let components = ((backdrop.value as? String) ?? "")
            .split(separator: ",")
            .compactMap { Double($0) }
        guard components.count == 4 else {
            XCTFail("The production keyboard backdrop must report its unclipped frame in \(context).", file: file, line: line)
            return .zero
        }
        return CGRect(x: components[0], y: components[1], width: components[2], height: components[3])
    }

    private func assertAuthenticatedViewport(
        window: XCUIElement,
        content: XCUIElement,
        topChrome: XCUIElement,
        primaryNavigation: XCUIElement,
        context: String,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) {
        let windowFrame = window.frame
        let contentFrame = content.frame
        let topFrame = topChrome.frame
        let navigationFrame = primaryNavigation.frame
        let tolerance: CGFloat = 1

        for (name, frame) in [
            ("window", windowFrame),
            ("content", contentFrame),
            ("top chrome", topFrame),
            ("primary navigation", navigationFrame),
        ] {
            XCTAssertGreaterThan(frame.width, 0, "[\(context)] \(name) must have positive width.", file: file, line: line)
            XCTAssertGreaterThan(frame.height, 0, "[\(context)] \(name) must have positive height.", file: file, line: line)
        }

        XCTAssertLessThanOrEqual(
            topFrame.maxY,
            contentFrame.minY + tolerance,
            "[\(context)] Feed content must begin below the authenticated top chrome.",
            file: file,
            line: line,
        )
        XCTAssertLessThanOrEqual(
            contentFrame.maxY,
            navigationFrame.minY + tolerance,
            "[\(context)] Feed content must end above primary navigation.",
            file: file,
            line: line,
        )
        for (name, frame) in [("content", contentFrame), ("top chrome", topFrame), ("primary navigation", navigationFrame)] {
            XCTAssertGreaterThanOrEqual(
                frame.minX,
                windowFrame.minX - tolerance,
                "[\(context)] \(name) must stay inside the window's leading edge.",
                file: file,
                line: line,
            )
            XCTAssertLessThanOrEqual(
                frame.maxX,
                windowFrame.maxX + tolerance,
                "[\(context)] \(name) must stay inside the window's trailing edge.",
                file: file,
                line: line,
            )
            XCTAssertGreaterThanOrEqual(
                frame.minY,
                windowFrame.minY - tolerance,
                "[\(context)] \(name) must stay inside the window's top edge.",
                file: file,
                line: line,
            )
            XCTAssertLessThanOrEqual(
                frame.maxY,
                windowFrame.maxY + tolerance,
                "[\(context)] \(name) must stay inside the window's bottom edge.",
                file: file,
                line: line,
            )
        }
    }

    private func assertAuthenticatedViewportWithoutPrimaryNavigation(
        window: XCUIElement,
        content: XCUIElement,
        topChrome: XCUIElement,
        context: String,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) {
        let windowFrame = window.frame
        let contentFrame = content.frame
        let topFrame = topChrome.frame
        let tolerance: CGFloat = 1

        for (name, frame) in [("window", windowFrame), ("content", contentFrame), ("top chrome", topFrame)] {
            XCTAssertGreaterThan(frame.width, 0, "[\(context)] \(name) must have positive width.", file: file, line: line)
            XCTAssertGreaterThan(frame.height, 0, "[\(context)] \(name) must have positive height.", file: file, line: line)
        }
        XCTAssertLessThanOrEqual(
            topFrame.maxY,
            contentFrame.minY + tolerance,
            "[\(context)] Route content must begin below the authenticated top chrome.",
            file: file,
            line: line,
        )
        for (name, frame) in [("content", contentFrame), ("top chrome", topFrame)] {
            XCTAssertGreaterThanOrEqual(frame.minX, windowFrame.minX - tolerance, "[\(context)] \(name) must stay inside the leading edge.", file: file, line: line)
            XCTAssertLessThanOrEqual(frame.maxX, windowFrame.maxX + tolerance, "[\(context)] \(name) must stay inside the trailing edge.", file: file, line: line)
            XCTAssertGreaterThanOrEqual(frame.minY, windowFrame.minY - tolerance, "[\(context)] \(name) must stay inside the top edge.", file: file, line: line)
            XCTAssertLessThanOrEqual(frame.maxY, windowFrame.maxY + tolerance, "[\(context)] \(name) must stay inside the bottom edge.", file: file, line: line)
        }
    }

    private func assertUnconfiguredMigrationSemantics(in app: XCUIApplication) {
        let message = app.staticTexts[
            "Quata para iOS necesita una configuración pública válida para iniciar."
        ]
        XCTAssertTrue(
            message.waitForExistence(timeout: 10),
            "The real unconfigured Compose migration text must be exposed through accessibility.",
        )

        let acknowledge = app.buttons["Entendido"]
        XCTAssertTrue(
            acknowledge.waitForExistence(timeout: 10),
            "The real unconfigured Compose action must be exposed through accessibility.",
        )
        XCTAssertTrue(
            acknowledge.isHittable,
            "The visible Compose migration action must be hittable on the normal launcher surface.",
        )
        acknowledge.tap()

        let updatedMessage = app.staticTexts[
            "La configuración pública sigue sin estar disponible."
        ]
        XCTAssertTrue(
            updatedMessage.waitForExistence(timeout: 10),
            "Tapping the real Compose action must update the visible status semantics.",
        )
        let retry = app.buttons["Comprobar de nuevo"]
        XCTAssertTrue(
            retry.waitForExistence(timeout: 10),
            "Tapping the real Compose action must update its accessible label.",
        )
        XCTAssertTrue(
            retry.isHittable,
            "The updated real Compose action must remain hittable.",
        )
    }

    private func performRecoveryReset(
        in app: XCUIApplication,
        phone: String,
        secretAnswer: String,
        newPassword: String,
        expectedQuestion: String?,
        evidencePrefix: String,
    ) throws {
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "auth.recovery.root")
                .firstMatch
                .waitForExistence(timeout: 10),
            "The shared recovery root must be visible before entering account data.",
        )
        enterText(phone, into: "auth.recovery.phone", in: app)
        let question = app.descendants(matching: .any)
            .matching(identifier: "auth.recovery.question")
            .firstMatch
        if let expectedQuestion {
            XCTAssertTrue(
                question.waitForLabelOrValue(containing: expectedQuestion, timeout: 25),
                "The real recovery question must be read through the iOS Auth repository.",
            )
        } else {
            XCTAssertTrue(question.waitForNonPlaceholderLabel(timeout: 25))
        }
        QuataIosHostUITestSupport.attachRenderedSurface(named: "\(evidencePrefix)-question")

        enterText(secretAnswer, into: "auth.recovery.secret-answer", in: app)
        enterText(newPassword, into: "auth.recovery.new-password", in: app)
        tapAfterDismissingKeyboard("auth.recovery.submit", in: app)
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "auth.submit")
                .firstMatch
                .waitForExistence(timeout: 25),
            "A successful password reset must return to the shared Login surface.",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "\(evidencePrefix)-login-return")
    }

    private func openRecoveryFromLogin(in app: XCUIApplication) {
        let forgotPassword = app.descendants(matching: .any)
            .matching(identifier: "auth.forgot-password")
            .firstMatch
        XCTAssertTrue(forgotPassword.waitForExistence(timeout: 10))
        forgotPassword.tap()
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "auth.recovery.root")
                .firstMatch
                .waitForExistence(timeout: 10),
        )
    }

    private func enterText(_ text: String, into identifier: String, in app: XCUIApplication) {
        let field = app.descendants(matching: .any)
            .matching(identifier: identifier)
            .firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 10), "Expected input \(identifier) to exist.")
        field.tap()
        field.typeText(text)
    }

    private func typePrivateText(
        _ text: String,
        into identifier: String,
        in app: XCUIApplication
    ) {
        let field = app.descendants(matching: .any)
            .matching(identifier: identifier)
            .firstMatch
        guard field.waitForExistence(timeout: 10) else {
            XCTFail("Expected private input \(identifier) to exist.")
            return
        }
        tapAfterDismissingKeyboard(identifier, in: app)
        let keyboard = app.keyboards.firstMatch
        guard keyboard.waitForExistence(timeout: 5) else {
            XCTFail("Expected the software keyboard for \(identifier).")
            return
        }
        guard dismissKeyboardOnboardingIfNeeded(in: app) else { return }
        assertSoftwareKeyboardIsOnScreen(keyboard, in: app, for: identifier)
        privateKeyboardFrameCache.removeAll()
        var keyFrames = privateKeyboardFrames(for: .letters, keyboard: keyboard)
        for character in text {
            let characterLabels: [String]
            if character.isLetter {
                guard ensurePrivateKeyboardMode(.letters, keyFrames: &keyFrames, keyboard: keyboard, app: app) else { return }
                if character.isUppercase {
                    guard tapPrivateKeyboardShift(keyFrames: keyFrames, keyboard: keyboard, app: app) else { return }
                }
                characterLabels = [String(character).lowercased()]
            } else if character.wholeNumberValue != nil {
                guard ensurePrivateKeyboardMode(.numbers, keyFrames: &keyFrames, keyboard: keyboard, app: app) else { return }
                characterLabels = [String(character)]
            } else if character == "-" {
                guard ensurePrivateKeyboardMode(.numbers, keyFrames: &keyFrames, keyboard: keyboard, app: app) else { return }
                characterLabels = ["-"]
            } else if character == "_" {
                guard ensurePrivateKeyboardMode(.symbols, keyFrames: &keyFrames, keyboard: keyboard, app: app) else { return }
                characterLabels = ["_"]
            } else if character == " " {
                guard ensurePrivateKeyboardMode(.letters, keyFrames: &keyFrames, keyboard: keyboard, app: app) else { return }
                characterLabels = [" ", "space", "espacio"]
            } else {
                XCTFail("Private input contains an unsupported keyboard character.")
                return
            }
            guard tapPrivateKeyboardKey(labels: characterLabels, keyFrames: keyFrames, app: app) else { return }
        }
    }

    private func typePrivatePhone(_ text: String, into identifier: String, in app: XCUIApplication) {
        let field = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        guard field.waitForExistence(timeout: 10) else {
            XCTFail("Expected private phone input to exist.")
            return
        }
        tapAfterDismissingKeyboard(identifier, in: app)
        let keyboard = app.keyboards.firstMatch
        guard keyboard.waitForExistence(timeout: 5) else {
            XCTFail("Expected the private phone keypad.")
            return
        }
        guard dismissKeyboardOnboardingIfNeeded(in: app) else { return }
        assertSoftwareKeyboardIsOnScreen(keyboard, in: app, for: identifier)
        // The preceding field can retain both focus and an alphabetic keyboard after the country
        // picker recomposes. Re-snapshot the keyboard selected by the phone field, then switch it
        // to numbers if UIKit did not select a numeric keypad directly.
        privateKeyboardFrameCache.removeAll()
        var keyFrames = privateKeyboardFrames(for: .letters, keyboard: keyboard)
        guard ensurePrivateKeyboardMode(.numbers, keyFrames: &keyFrames, keyboard: keyboard, app: app) else { return }
        guard (0...9).allSatisfy({ keyFrames[String($0)] != nil }) else {
            XCTFail("Expected every digit on the visible private phone keypad.")
            return
        }
        for character in text {
            guard character.wholeNumberValue != nil else {
                XCTFail("Private phone input contains a non-digit character.")
                return
            }
            guard tapPrivateKeyboardKey(labels: [String(character)], keyFrames: keyFrames, app: app) else { return }
        }
    }

    private enum PrivateKeyboardMode: Hashable { case letters, numbers, symbols }

    private func dismissKeyboardOnboardingIfNeeded(in app: XCUIApplication) -> Bool {
        let predicate = NSPredicate(format: "label IN %@", ["Continue", "Continuar"])
        let button = app.descendants(matching: .button).matching(predicate).firstMatch
        if button.waitForExistence(timeout: 2) {
            button.tap()
            let remaining = app.descendants(matching: .button).matching(predicate).firstMatch
            if remaining.waitForExistence(timeout: 1) {
                app.typeKey(.return, modifierFlags: [])
            }
            let stillVisible = app.descendants(matching: .button).matching(predicate).firstMatch
            guard !stillVisible.waitForExistence(timeout: 3) else {
                XCTFail("Expected the keyboard onboarding to close.")
                return false
            }
        }
        return true
    }

    private func ensurePrivateKeyboardMode(
        _ mode: PrivateKeyboardMode,
        keyFrames: inout [String: CGRect],
        keyboard: XCUIElement,
        app: XCUIApplication
    ) -> Bool {
        switch mode {
        case .letters:
            if keyFrames["q"] != nil { return true }
            guard tapPrivateKeyboardKey(labels: ["letters", "abc", "letras"], keyFrames: keyFrames, app: app) else { return false }
        case .numbers:
            if keyFrames["1"] != nil && keyFrames["_"] == nil { return true }
            guard tapPrivateKeyboardKey(labels: ["more", "123", "numbers", "números"], keyFrames: keyFrames, app: app) else { return false }
        case .symbols:
            if keyFrames["_"] != nil { return true }
            guard ensurePrivateKeyboardMode(.numbers, keyFrames: &keyFrames, keyboard: keyboard, app: app),
                  tapPrivateKeyboardKey(labels: ["more", "#+=", "symbols", "símbolos"], keyFrames: keyFrames, app: app)
            else { return false }
        }
        keyFrames = privateKeyboardFrames(for: mode, keyboard: keyboard)
        return true
    }

    private func privateKeyboardFrames(
        for mode: PrivateKeyboardMode,
        keyboard: XCUIElement
    ) -> [String: CGRect] {
        if let cached = privateKeyboardFrameCache[mode] { return cached }
        if mode != .letters,
           let letters = privateKeyboardFrameCache[.letters],
           let derived = derivedPrivateKeyboardFrames(for: mode, letters: letters)
        {
            privateKeyboardFrameCache[mode] = derived
            return derived
        }
        let elements = keyboard.keys.allElementsBoundByIndex + keyboard.buttons.allElementsBoundByIndex
        let frames = Dictionary(
            elements.flatMap { element in
                [element.label, element.identifier]
                    .map { $0.lowercased() }
                    .filter { !$0.isEmpty }
                    .map { ($0, element.frame) }
            },
            uniquingKeysWith: { first, _ in first }
        )
        privateKeyboardFrameCache[mode] = frames
        return frames
    }

    private func derivedPrivateKeyboardFrames(
        for mode: PrivateKeyboardMode,
        letters: [String: CGRect]
    ) -> [String: CGRect]? {
        guard mode != .letters,
              let hyphenOrUnderscore = letters["a"],
              let modeToggle = letters["shift"],
              let lettersToggle = letters["more"]
        else { return nil }
        var frames = [
            "more": modeToggle,
            "123": modeToggle,
            "numbers": modeToggle,
            "números": modeToggle,
            "#+=": modeToggle,
            "symbols": modeToggle,
            "símbolos": modeToggle,
            "letters": lettersToggle,
            "abc": lettersToggle,
            "letras": lettersToggle,
        ]
        if mode == .numbers {
            for (digit, letter) in zip("1234567890", "qwertyuiop") {
                guard let frame = letters[String(letter)] else { return nil }
                frames[String(digit)] = frame
            }
            frames["-"] = hyphenOrUnderscore
        } else {
            frames["_"] = hyphenOrUnderscore
        }
        return frames
    }

    private func tapPrivateKeyboardKey(
        labels: [String],
        keyFrames: [String: CGRect],
        app: XCUIApplication
    ) -> Bool {
        guard let frame = labels.lazy.compactMap({ keyFrames[$0.lowercased()] }).first else {
            XCTFail("Expected a keyboard key required by the private input mode.")
            return false
        }
        let appFrame = app.frame
        app.coordinate(withNormalizedOffset: CGVector(
            dx: (frame.midX - appFrame.minX) / appFrame.width,
            dy: (frame.midY - appFrame.minY) / appFrame.height
        )).tap()
        RunLoop.current.run(until: Date().addingTimeInterval(0.1))
        return true
    }

    private func tapPrivateKeyboardShift(
        keyFrames: [String: CGRect],
        keyboard: XCUIElement,
        app: XCUIApplication
    ) -> Bool {
        guard let firstLetterOnRow = keyFrames["z"] else {
            XCTFail("Expected the lower letter row required to derive Shift.")
            return false
        }
        let keyboardFrame = keyboard.frame
        let shiftCenter = CGPoint(
            x: keyboardFrame.minX + (firstLetterOnRow.minX - keyboardFrame.minX) / 2,
            y: firstLetterOnRow.midY
        )
        let appFrame = app.frame
        app.coordinate(withNormalizedOffset: CGVector(
            dx: (shiftCenter.x - appFrame.minX) / appFrame.width,
            dy: (shiftCenter.y - appFrame.minY) / appFrame.height
        )).tap()
        RunLoop.current.run(until: Date().addingTimeInterval(0.1))
        return true
    }

    private func dismissKeyboardWithReturn(from identifier: String, in app: XCUIApplication) {
        guard app.keyboards.count > 0 else { return }
        let field = app.descendants(matching: .any)
            .matching(identifier: identifier)
            .firstMatch
        XCTAssertTrue(field.exists, "Expected focused input \(identifier) before dismissing its keyboard.")
        let keyboard = app.keyboards.firstMatch
        assertSoftwareKeyboardIsOnScreen(keyboard, in: app, for: identifier)
        let keyFrames = privateKeyboardFrames(for: .letters, keyboard: keyboard)
        guard tapPrivateKeyboardKey(
            labels: ["return", "intro", "search", "buscar", "done", "listo"],
            keyFrames: keyFrames,
            app: app
        ) else { return }
        RunLoop.current.run(until: Date().addingTimeInterval(0.5))
    }

    private func assertSoftwareKeyboardIsOnScreen(
        _ keyboard: XCUIElement,
        in app: XCUIApplication,
        for identifier: String
    ) {
        let keyboardFrame = keyboard.frame
        let appFrame = app.frame
        XCTAssertTrue(
            keyboardFrame.intersects(appFrame) && keyboardFrame.minY < appFrame.maxY,
            "Expected the software keyboard for \(identifier) to be visible on screen."
        )
    }

    private func tapAfterDismissingKeyboard(_ identifier: String, in app: XCUIApplication) {
        let element = app.descendants(matching: .any)
            .matching(identifier: identifier)
            .firstMatch
        XCTAssertTrue(element.waitForExistence(timeout: 10), "Expected \(identifier) to exist before tapping.")
        if app.keyboards.count > 0 {
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.08)).tap()
        }
        for _ in 0..<6 {
            if element.isHittable {
                element.tap()
                return
            }
            app.swipeUp()
            RunLoop.current.run(until: Date().addingTimeInterval(0.3))
        }
        XCTAssertTrue(element.isHittable, "Expected \(identifier) to become hittable after dismissing keyboard.")
    }

    private func tapVisibleElement(_ identifier: String, in app: XCUIApplication) {
        let element = app.descendants(matching: .any)
            .matching(identifier: identifier)
            .firstMatch
        XCTAssertTrue(element.waitForExistence(timeout: 10), "Expected \(identifier) to exist before tapping.")
        XCTAssertTrue(element.isHittable, "Expected \(identifier) to be hittable without dismissing its overlay.")
        element.tap()
    }

}

private struct AuthRecoveryUiCredentials: Decodable {
    let phone: String
    let missingPhone: String
    let secretAnswer: String
    let temporaryPassword: String
    let restorePassword: String
    let expectedQuestion: String?
    let countryCode: String
    let localPhone: String
    let missingLocalPhone: String

    enum CodingKeys: String, CodingKey {
        case phone
        case missingPhone = "missing_phone"
        case secretAnswer = "secret_answer"
        case temporaryPassword = "temporary_password"
        case restorePassword = "restore_password"
        case expectedQuestion = "expected_question"
        case countryCode = "country_code"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        phone = try container.decode(String.self, forKey: .phone)
        missingPhone = try container.decode(String.self, forKey: .missingPhone)
        secretAnswer = try container.decode(String.self, forKey: .secretAnswer)
        temporaryPassword = try container.decode(String.self, forKey: .temporaryPassword)
        restorePassword = try container.decode(String.self, forKey: .restorePassword)
        expectedQuestion = try container.decodeIfPresent(String.self, forKey: .expectedQuestion)
        let configuredCountry = try container.decodeIfPresent(String.self, forKey: .countryCode)?
            .trimmingCharacters(in: CharacterSet(charactersIn: "+ "))
        let phoneDigits = Self.digits(phone)
        let missingDigits = Self.digits(missingPhone)
        let selectedCountry = configuredCountry ?? (phoneDigits.hasPrefix("240") ? "240" : "")
        guard selectedCountry == "240",
              phoneDigits.hasPrefix(selectedCountry),
              missingDigits.hasPrefix(selectedCountry),
              phoneDigits.count > selectedCountry.count,
              missingDigits.count > selectedCountry.count,
              temporaryPassword.count >= 6,
              restorePassword.count >= 6,
              temporaryPassword != restorePassword,
              !secretAnswer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else {
            throw AuthRecoveryUiConfigurationError.invalidShape
        }
        countryCode = selectedCountry
        localPhone = String(phoneDigits.dropFirst(selectedCountry.count))
        missingLocalPhone = String(missingDigits.dropFirst(selectedCountry.count))
    }

    static func load(from path: String) throws -> AuthRecoveryUiCredentials {
        let data = try Data(contentsOf: URL(fileURLWithPath: path))
        return try JSONDecoder().decode(AuthRecoveryUiCredentials.self, from: data)
    }

    private static func digits(_ value: String) -> String {
        value.filter(\.isNumber)
    }
}

private struct IosRegistrationUiInput: Decodable {
    let displayName: String
    let neighborhood: String
    let countryCode: String
    let phone: String
    let password: String
    let secretQuestion: String
    let secretAnswer: String
    let clientInstanceId: String
    let idempotencyKey: String

    static func load(from path: String) throws -> Self {
        let data = try Data(contentsOf: URL(fileURLWithPath: path))
        return try JSONDecoder().decode(Self.self, from: data)
    }
}

private enum AuthRecoveryUiConfigurationError: LocalizedError {
    case invalidShape

    var errorDescription: String? {
        "The iOS recovery E2E file has an invalid credential shape."
    }
}

private extension XCUIElement {
    func waitForLabelOrValue(containing expected: String, timeout: TimeInterval) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        repeat {
            let accessibleValue = (value as? String) ?? ""
            if label.contains(expected) || accessibleValue.contains(expected) {
                return true
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        } while Date() < deadline
        return false
    }

    func waitForLabel(containing expected: String, timeout: TimeInterval) -> Bool {
        let predicate = NSPredicate(format: "label CONTAINS %@", expected)
        let expectation = XCTNSPredicateExpectation(predicate: predicate, object: self)
        return XCTWaiter().wait(for: [expectation], timeout: timeout) == .completed
    }

    func waitForNonPlaceholderLabel(timeout: TimeInterval) -> Bool {
        let predicate = NSPredicate(format: "label.length > 0 AND NOT label CONTAINS[c] 'Enter' AND NOT label CONTAINS[c] 'Loading' AND NOT label CONTAINS[c] 'Introduce' AND NOT label CONTAINS[c] 'Cargando'")
        let expectation = XCTNSPredicateExpectation(predicate: predicate, object: self)
        return XCTWaiter().wait(for: [expectation], timeout: timeout) == .completed
    }
}
