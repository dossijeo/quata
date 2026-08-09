import XCTest

final class QuataIosHostUITests: XCTestCase {
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
    ) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-quata-ui-test-fixture", fixture]
        if let deepLink { app.launchArguments += ["-quata-ui-test-deep-link", deepLink] }
        if let inAppRoute { app.launchArguments += ["-quata-ui-test-in-app-route", inAppRoute] }
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
}
