import XCTest

/// Opt-in, production-host UI gate for the authenticated common post composer.
/// The companion runner seeds the normal app Keychain first, then opens the real composer route.
final class QuataIosAuthenticatedPostPublishUITests: XCTestCase {
    private static let realPublishOptIn = "I_ACCEPT_REVERSIBLE_POST_PUBLISH_MUTATION"

    func testAuthenticatedSessionPublishesRealTextPost() throws {
        let environment = ProcessInfo.processInfo.environment
        guard environment["QUATA_IOS_POST_PUBLISH_UI_E2E"] == "1" else {
            throw XCTSkip("Authenticated post publish UI gate is opt-in.")
        }
        guard environment["QUATA_IOS_POST_PUBLISH_REAL_MUTATION_OPT_IN"] == Self.realPublishOptIn else {
            throw XCTSkip("Real post publish is opt-in because it mutates authorized backend data.")
        }
        guard let marker = environment["QUATA_IOS_POST_PUBLISH_MARKER"], !marker.isEmpty else {
            throw XCTSkip("Real post publish requires QUATA_IOS_POST_PUBLISH_MARKER.")
        }
        guard let destinationWallId = environment["QUATA_IOS_POST_PUBLISH_DESTINATION_WALL_ID"], !destinationWallId.isEmpty else {
            throw XCTSkip("Real post publish requires QUATA_IOS_POST_PUBLISH_DESTINATION_WALL_ID.")
        }

        let app = openComposer()
        assertSharedComposerSurface(in: app)

        tapTextType(in: app)
        selectDestination(destinationWallId, in: app)
        typeText(marker, into: "composer-text-input", in: app)
        dismissKeyboardIfPresent(in: app)
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-publish-composer-filled")

        tapPublish(in: app)
        waitForPublishedFeedbackOrClose(in: app)
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-publish-after-publish")
    }

    private func selectDestination(_ wallId: String, in app: XCUIApplication) {
        let destination = app.descendants(matching: .any)
            .matching(identifier: "composer-destination-option.\(wallId)")
            .firstMatch
        for _ in 0..<10 {
            if destination.waitForExistence(timeout: 1), destination.isHittable {
                destination.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
                QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-publish-destination-selected")
                return
            }
            app.swipeUp()
            RunLoop.current.run(until: Date().addingTimeInterval(0.3))
        }
        XCTAssertTrue(destination.exists, "Expected shared composer destination \(wallId) to exist.")
        destination.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
    }

    private func openComposer() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(es)", "-AppleLocale", "es_ES"]
        app.launch()

        let feed = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-feed-host")
            .firstMatch
        XCTAssertTrue(feed.waitForExistence(timeout: 25), "A normal launch must restore Feed from the seeded Keychain session.")

        let composerTab = app.buttons.matching(
            NSPredicate(format: "label CONTAINS[c] %@ OR identifier CONTAINS[c] %@", "Crear", "composer")
        ).firstMatch
        if composerTab.waitForExistence(timeout: 8), composerTab.isHittable {
            composerTab.tap()
        } else {
            let feedPublish = app.buttons.matching(
                NSPredicate(format: "identifier BEGINSWITH %@ OR label CONTAINS[c] %@", "feed.action.publish.", "Publicar")
            ).firstMatch
            XCTAssertTrue(feedPublish.waitForExistence(timeout: 12), "The Feed publish CTA must expose the real composer route.")
            feedPublish.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        }

        let composer = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-composer-host")
            .firstMatch
        XCTAssertTrue(composer.waitForExistence(timeout: 25), "The real shared composer host must open from authenticated iOS chrome.")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-publish-composer-opened")
        return app
    }

    private func assertSharedComposerSurface(in app: XCUIApplication) {
        XCTAssertTrue(
            app.descendants(matching: .any).matching(identifier: "quata-ios-authenticated-primary-navigation").firstMatch.exists,
            "The shared primary navigation must remain visible on the post composer.",
        )
        XCTAssertTrue(
            app.descendants(matching: .any).matching(identifier: "create-post-common-root").firstMatch.waitForExistence(timeout: 12),
            "iOS must expose the common CreatePostRoot surface.",
        )
    }

    private func tapTextType(in app: XCUIApplication) {
        let textType = app.descendants(matching: .any)
            .matching(identifier: "composer-type-text")
            .firstMatch
        XCTAssertTrue(textType.waitForExistence(timeout: 10), "The common text composer type must be exposed.")
        if textType.isHittable {
            textType.tap()
        } else {
            textType.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        }
    }

    private func typeText(_ value: String, into identifier: String, in app: XCUIApplication) {
        let field = app.descendants(matching: .any)
            .matching(identifier: identifier)
            .firstMatch
        for attempt in 0..<12 {
            if field.waitForExistence(timeout: 1), field.isHittable {
                field.tap()
                typeIntoFocusedElement(value, fallback: field, in: app)
                return
            }
            if attempt < 5 {
                app.swipeDown()
            } else {
                app.swipeUp()
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.4))
        }
        XCTAssertTrue(field.exists, "Expected editable field \(identifier) to exist.")
        typeIntoFocusedElement(value, fallback: field, in: app)
    }

    private func typeIntoFocusedElement(_ value: String, fallback: XCUIElement, in app: XCUIApplication) {
        let focused = app.descendants(matching: .any)
            .matching(NSPredicate(format: "hasKeyboardFocus == 1"))
            .firstMatch
        if focused.waitForExistence(timeout: 2) {
            focused.typeText(value)
        } else {
            fallback.typeText(value)
        }
    }

    private func dismissKeyboardIfPresent(in app: XCUIApplication) {
        guard app.keyboards.count > 0 else {
            return
        }
        for label in ["return", "Return", "Intro", "Retorno", "Done", "Hecho"] {
            let key = app.keyboards.buttons[label].firstMatch
            if key.exists {
                key.tap()
                RunLoop.current.run(until: Date().addingTimeInterval(0.3))
                if app.keyboards.count == 0 {
                    return
                }
            }
        }
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.06)).tap()
        RunLoop.current.run(until: Date().addingTimeInterval(0.5))
    }

    private func tapPublish(in app: XCUIApplication) {
        dismissKeyboardIfPresent(in: app)
        let publish = app.descendants(matching: .any)
            .matching(identifier: "composer-publish")
            .firstMatch
        for _ in 0..<10 {
            if publish.waitForExistence(timeout: 1), publish.isHittable {
                publish.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
                return
            }
            app.swipeUp()
            RunLoop.current.run(until: Date().addingTimeInterval(0.3))
        }
        XCTAssertTrue(publish.exists, "Expected the shared composer publish action to exist.")
        publish.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
    }

    private func waitForPublishedFeedbackOrClose(in app: XCUIApplication) {
        let success = app.descendants(matching: .any)
            .matching(identifier: "composer-feedback-success")
            .firstMatch
        let error = app.descendants(matching: .any)
            .matching(identifier: "composer-feedback-error")
            .firstMatch
        let composer = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-composer-host")
            .firstMatch
        let feed = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-feed-host")
            .firstMatch
        let deadline = Date().addingTimeInterval(60)
        while Date() < deadline {
            if error.exists {
                QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-publish-error")
                XCTFail("The real iOS composer surfaced shared error feedback after publish.")
                return
            }
            if success.exists || feed.exists {
                return
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.5))
        }
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-publish-timeout")
        XCTFail("The real iOS composer did not show publish success or return to Feed after publish.")
    }
}
