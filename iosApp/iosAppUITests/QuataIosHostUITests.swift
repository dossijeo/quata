import XCTest

final class QuataIosHostUITests: XCTestCase {
    private static let realRecoveryOptIn = "I_ACCEPT_IOS_PASSWORD_RESET_ROUNDTRIP"

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
    ) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-quata-ui-test-fixture", fixture]
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
        return app
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
