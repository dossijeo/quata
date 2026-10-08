import XCTest

/// Focal, non-destructive postflight for the authenticated shared Create Post root.
final class QuataIosAuthenticatedCreatePostPostflightUITests: XCTestCase {
    func testAuthenticatedTextDraftRestoresAfterRelaunchAndDiscardsWithoutPublishing() throws {
        let environment = ProcessInfo.processInfo.environment
        guard environment["QUATA_IOS_CREATE_POST_POSTFLIGHT_UI_E2E"] == "1",
              let marker = environment["QUATA_IOS_CREATE_POST_DRAFT_MARKER"],
              !marker.isEmpty else {
            throw XCTSkip("Authenticated Create Post draft restoration is opt-in.")
        }

        let app = launchAuthenticatedApp()
        openTextComposer(in: app)
        let input = app.descendants(matching: .any).matching(identifier: "composer-text-input").firstMatch
        XCTAssertTrue(input.waitForExistence(timeout: 15), "Expected text composer input before relaunch.")
        input.tap()
        input.typeText(marker)
        assertTextInput(input, equals: marker, context: "draft before relaunch")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-create-post-draft-before-relaunch")

        app.terminate()
        let relaunched = launchAuthenticatedApp()
        openTextComposer(in: relaunched)
        let restoredInput = relaunched.descendants(matching: .any).matching(identifier: "composer-text-input").firstMatch
        XCTAssertTrue(restoredInput.waitForExistence(timeout: 15), "Expected restored text composer input after relaunch.")
        assertTextInput(restoredInput, equals: marker, context: "draft after relaunch")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-create-post-draft-after-relaunch")

        tapScrollableIdentifier(
            "composer-back",
            inside: "create-post-common-root",
            in: relaunched,
            context: "discard restored draft"
        )
        assertVisible("quata-ios-feed-host", in: relaunched, context: "Feed after draft discard")
        XCTAssertTrue(
            relaunched.descendants(matching: .any).matching(identifier: "create-post-common-root").firstMatch.waitForNonExistence(timeout: 12),
            "Discarding the restored draft must dismiss the common Create Post root."
        )

        relaunched.terminate()
        let afterDiscard = launchAuthenticatedApp()
        openComposer(in: afterDiscard)
        assertVisible("composer-type-text", in: afterDiscard, context: "empty composer after durable discard")
        XCTAssertFalse(
            afterDiscard.descendants(matching: .any).matching(identifier: "composer-text-input").firstMatch.exists,
            "A discarded draft must not reappear after another app relaunch."
        )
        print("IOS_CREATE_POST_DRAFT_RESTORATION_UI_GATE_PASSED")
    }

    func testAuthenticatedCreatePostRootOpensAndReturnsWithoutPublishing() throws {
        guard ProcessInfo.processInfo.environment["QUATA_IOS_CREATE_POST_POSTFLIGHT_UI_E2E"] == "1" else {
            throw XCTSkip("Authenticated Create Post postflight is opt-in.")
        }

        let app = launchAuthenticatedApp()
        assertVisible("quata-ios-feed-host", in: app, context: "authenticated Feed")
        tapPrefix("feed.action.publish.", in: app, context: "open Create Post from Feed")
        assertVisible("quata-ios-composer-host", in: app, context: "Create Post host")
        assertVisible("create-post-common-root", in: app, context: "common Create Post root")
        assertVisible("navigation.primary.composer", in: app, context: "composer shell destination")
        assertVisible("composer-type-text", in: app, context: "text post type")
        assertVisible("composer-type-image", in: app, context: "image post type")
        assertVisible("composer-type-video", in: app, context: "video post type")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-create-post-postflight-opened")

        tapIdentifier("navigation.primary.feed", in: app, context: "return to Feed without publishing")
        assertVisible("quata-ios-feed-host", in: app, context: "Feed after Create Post return")
        XCTAssertTrue(
            app.descendants(matching: .any).matching(identifier: "create-post-common-root").firstMatch.waitForNonExistence(timeout: 12),
            "Returning to Feed must dismiss the common Create Post root."
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-create-post-postflight-returned")

        app.terminate()
        let relaunched = launchAuthenticatedApp()
        assertVisible("quata-ios-feed-host", in: relaunched, context: "authenticated Feed after relaunch")
        tapIdentifier("navigation.primary.profile", in: relaunched, context: "open authenticated Profile after relaunch")
        assertVisible("quata-ios-profile-sos-host", in: relaunched, context: "restored authenticated Profile after relaunch")
        print("IOS_CREATE_POST_POSTFLIGHT_UI_GATE_PASSED")
    }

    func testAuthenticatedImageDraftRestoresAfterRelaunchAndDiscardsWithoutPublishing() throws {
        guard ProcessInfo.processInfo.environment["QUATA_IOS_CREATE_POST_POSTFLIGHT_UI_E2E"] == "1" else {
            throw XCTSkip("Authenticated Create Post media draft restoration is opt-in.")
        }

        let app = launchAuthenticatedApp(extraEnvironment: [
            "QUATA_IOS_POST_PUBLISH_MODE": "image-location",
            "QUATA_IOS_POST_PUBLISH_REAL_MUTATION_OPT_IN": "I_ACCEPT_REVERSIBLE_POST_PUBLISH_MUTATION",
            "QUATA_IOS_POST_PUBLISH_LOCATION_LABEL": "Media draft fixture",
        ])
        openComposer(in: app)
        assertVisible("composer-media.selected-image-preview", in: app, context: "image draft before relaunch")
        assertVisible("composer-media.selected-image-preview.persisted", in: app, context: "durably persisted image draft before relaunch")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-create-post-media-draft-before-relaunch")

        app.terminate()
        let relaunched = launchAuthenticatedApp()
        openComposer(in: relaunched)
        assertVisible("composer-media.selected-image-preview", in: relaunched, context: "restored image draft after relaunch")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-create-post-media-draft-after-relaunch")

        tapScrollableIdentifier(
            "composer-back",
            inside: "create-post-common-root",
            in: relaunched,
            context: "discard restored image draft"
        )
        assertVisible("quata-ios-feed-host", in: relaunched, context: "Feed after image draft discard")
        XCTAssertTrue(
            relaunched.descendants(matching: .any).matching(identifier: "create-post-common-root").firstMatch.waitForNonExistence(timeout: 12),
            "Discarding the restored image draft must dismiss the common Create Post root."
        )

        relaunched.terminate()
        let afterDiscard = launchAuthenticatedApp()
        openComposer(in: afterDiscard)
        assertVisible("composer-type-image", in: afterDiscard, context: "empty composer after image draft discard")
        XCTAssertFalse(
            afterDiscard.descendants(matching: .any).matching(identifier: "composer-media.selected-image-preview").firstMatch.exists,
            "A discarded image draft must not reappear after another app relaunch."
        )
        print("IOS_CREATE_POST_MEDIA_DRAFT_RESTORATION_UI_GATE_PASSED")
    }

    private func launchAuthenticatedApp(extraEnvironment: [String: String] = [:]) -> XCUIApplication {
        let app = XCUIApplication()
        disableQuiescenceWait(for: app)
        app.launchArguments += ["-AppleLanguages", "(es)", "-AppleLocale", "es_ES"]
        for (key, value) in extraEnvironment {
            app.launchEnvironment[key] = value
        }
        app.launch()
        assertVisible("navigation.primary.profile", in: app, context: "restored authenticated shell", timeout: 25)
        dismissStartupWhatsNewIfPresent(in: app)
        return app
    }

    private func openTextComposer(in app: XCUIApplication) {
        openComposer(in: app)
        let input = app.descendants(matching: .any).matching(identifier: "composer-text-input").firstMatch
        if !input.exists {
            tapIdentifier("composer-type-text", in: app, context: "select text post type")
        }
        assertVisible("composer-text-input", in: app, context: "text composer input")
    }

    private func openComposer(in app: XCUIApplication) {
        if !app.descendants(matching: .any).matching(identifier: "create-post-common-root").firstMatch.exists {
            assertVisible("quata-ios-feed-host", in: app, context: "authenticated Feed")
            tapPrefix("feed.action.publish.", in: app, context: "open Create Post from Feed")
        }
        assertVisible("create-post-common-root", in: app, context: "common Create Post root")
    }

    private func assertTextInput(_ element: XCUIElement, equals expected: String, context: String) {
        let expectation = XCTNSPredicateExpectation(
            predicate: NSPredicate { candidate, _ in
                guard let input = candidate as? XCUIElement else { return false }
                return [input.label, input.value as? String]
                    .compactMap { $0 }
                    .contains(expected)
            },
            object: element
        )
        XCTAssertEqual(
            XCTWaiter.wait(for: [expectation], timeout: 15),
            .completed,
            "Expected exact marker in \(context)."
        )
    }

    private func dismissStartupWhatsNewIfPresent(in app: XCUIApplication) {
        let host = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-whats-new-host")
            .firstMatch
        guard host.waitForExistence(timeout: 3) else { return }

        let deadline = Date().addingTimeInterval(20)
        while host.exists && Date() < deadline {
            let dismiss = ["whats-new-dismiss", "dismiss_whats_new"]
                .map { app.descendants(matching: .any).matching(identifier: $0).firstMatch }
                .first(where: { $0.exists && $0.isHittable })
            let next = ["whats-new-next", "next_whats_new"]
                .map { app.descendants(matching: .any).matching(identifier: $0).firstMatch }
                .first(where: { $0.exists && $0.isHittable })
            guard let control = dismiss ?? next else {
                RunLoop.current.run(until: Date().addingTimeInterval(0.25))
                continue
            }
            control.tap()
            RunLoop.current.run(until: Date().addingTimeInterval(0.5))
        }
        XCTAssertFalse(host.exists, "Startup What's New must close before exercising Create Post.")
    }

    private func assertVisible(
        _ identifier: String,
        in app: XCUIApplication,
        context: String,
        timeout: TimeInterval = 15
    ) {
        let element = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        XCTAssertTrue(element.waitForExistence(timeout: timeout), "Expected \(identifier) for \(context).")
    }

    private func tapIdentifier(_ identifier: String, in app: XCUIApplication, context: String) {
        let element = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        XCTAssertTrue(element.waitForExistence(timeout: 12), "Expected \(identifier) for \(context).")
        if element.isHittable {
            element.tap()
        } else {
            element.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        }
    }

    private func tapScrollableIdentifier(
        _ identifier: String,
        inside containerIdentifier: String,
        in app: XCUIApplication,
        context: String
    ) {
        let element = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        let container = app.descendants(matching: .any).matching(identifier: containerIdentifier).firstMatch
        let composerScroll = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-composer-host")
            .firstMatch
            .scrollViews
            .firstMatch
        XCTAssertTrue(container.waitForExistence(timeout: 12), "Expected \(containerIdentifier) for \(context).")
        XCTAssertTrue(element.waitForExistence(timeout: 12), "Expected \(identifier) for \(context).")

        dismissKeyboardIfPresent(in: app)
        var remainingScrolls = 24
        while !element.isHittable && remainingScrolls > 0 {
            if composerScroll.exists {
                let start = composerScroll.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.85))
                let end = composerScroll.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.15))
                start.press(forDuration: 0.01, thenDragTo: end)
            } else {
                container.swipeUp()
            }
            remainingScrolls -= 1
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        }
        XCTAssertTrue(element.isHittable, "Expected \(identifier) to become hittable after scrolling for \(context).")
        guard element.isHittable else { return }
        element.tap()
    }

    private func dismissKeyboardIfPresent(in app: XCUIApplication) {
        guard app.keyboards.count > 0 else { return }
        for label in ["return", "Return", "Intro", "Retorno", "Done", "Hecho"] {
            let key = app.keyboards.buttons[label].firstMatch
            if key.exists {
                key.tap()
                RunLoop.current.run(until: Date().addingTimeInterval(0.3))
                if app.keyboards.count == 0 { return }
            }
        }
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.06)).tap()
        RunLoop.current.run(until: Date().addingTimeInterval(0.5))
    }

    private func tapPrefix(_ prefix: String, in app: XCUIApplication, context: String) {
        let element = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH %@", prefix))
            .firstMatch
        XCTAssertTrue(element.waitForExistence(timeout: 25), "Expected \(prefix) for \(context).")
        if element.isHittable {
            element.tap()
        } else {
            element.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        }
    }

    private func disableQuiescenceWait(for app: XCUIApplication) {
        let selector = NSSelectorFromString("setWaitForQuiescence:")
        if app.responds(to: selector) {
            _ = app.perform(selector, with: NSNumber(value: false))
        }
    }
}
