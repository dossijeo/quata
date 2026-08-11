import XCTest

/// Opt-in, production-host gate for `CHAT-TRANSLATION` / `FLOW-TRANSLATOR`.
/// The runner seeds the normal Keychain session and supplies disposable backend fixture IDs.
@available(iOS 16.4, *)
final class QuataIosAuthenticatedChatTranslationUITests: XCTestCase {
    func testRealChatMessageTranslatesAndReturnsToTheConversation() throws {
        let environment = ProcessInfo.processInfo.environment
        guard environment["QUATA_IOS_AUTH_UI_E2E"] == "1" else {
            throw XCTSkip("Authenticated Chat translation UI gate is opt-in.")
        }
        guard let conversationId = nonEmpty(environment["QUATA_IOS_CHAT_E2E_CONVERSATION_ID"]),
              let messageId = nonEmpty(environment["QUATA_IOS_CHAT_E2E_MESSAGE_ID"]),
              let markerProbe = nonEmpty(environment["QUATA_IOS_CHAT_E2E_MARKER_PROBE"]) else {
            throw XCTSkip("Disposable Chat fixture IDs are not configured.")
        }

        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(es)", "-AppleLocale", "es_ES"]
        app.launch()

        let feed = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-feed-host")
            .firstMatch
        XCTAssertTrue(feed.waitForExistence(timeout: 20), "The seeded normal launch must restore Feed.")

        openDeepLink("quata://egquata.com/#chat-\(encodedFragment(conversationId))?message=\(encodedQuery(messageId))", in: app)
        let chat = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-chat-host")
            .firstMatch
        XCTAssertTrue(chat.waitForExistence(timeout: 20), "The authenticated navigation must mount Chat.")
        XCTAssertEqual(chat.value as? String, "chat:\(conversationId)?message=\(messageId)")

        XCTAssertTrue(app.staticTexts[markerProbe].firstMatch.waitForExistence(timeout: 20), app.debugDescription)
        attachScreenshot(app, name: "chat-translation-before")

        let translator = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label BEGINSWITH %@", "Traductor Fang"))
            .firstMatch
        XCTAssertTrue(translator.waitForExistence(timeout: 10), "Chat must expose its shared translator trigger.")
        translator.tap()

        let instruction = app.staticTexts["Toca cualquier mensaje para traducirlo"].firstMatch
        XCTAssertTrue(instruction.waitForExistence(timeout: 10), "The shared translator overlay must be visible.")
        XCTAssertTrue(app.staticTexts["Modo traductor activo"].waitForExistence(timeout: 5))
        attachScreenshot(app, name: "chat-translation-overlay")

        let overlayCandidates = app.buttons.matching(
            NSPredicate(format: "label CONTAINS %@", markerProbe),
        )
        XCTAssertGreaterThanOrEqual(overlayCandidates.count, 2)
        var overlayMessage = overlayCandidates.element(boundBy: 0)
        if overlayCandidates.count > 1 {
            for index in 1..<overlayCandidates.count {
                let candidate = overlayCandidates.element(boundBy: index)
                if candidate.frame.minY > overlayMessage.frame.minY {
                    overlayMessage = candidate
                }
            }
        }
        XCTAssertTrue(overlayMessage.isHittable, "The registered message surface must be actionable.")
        overlayMessage.tap()

        let translatedDirection = app.staticTexts.matching(
            NSPredicate(format: "label CONTAINS %@ AND label CONTAINS %@", "FAN", "ES"),
        ).firstMatch
        XCTAssertTrue(
            translatedDirection.waitForExistence(timeout: 35),
            "A real Fang response must expose its direction label instead of a callback-only success.",
        )
        XCTAssertTrue(app.staticTexts["pan de trigo"].waitForExistence(timeout: 5))
        attachScreenshot(app, name: "chat-translation-result")

        let close = app.buttons
            .matching(NSPredicate(format: "label CONTAINS %@", "Salir"))
            .firstMatch
        XCTAssertTrue(close.waitForExistence(timeout: 5))
        close.tap()
        XCTAssertTrue(chat.waitForExistence(timeout: 5), "Closing the translator must preserve Chat.")
        XCTAssertFalse(instruction.exists, "The translator overlay must leave the composition.")
        XCTAssertTrue(app.staticTexts[markerProbe].firstMatch.exists, "The original conversation must be restored.")
        attachScreenshot(app, name: "chat-translation-return")
    }

    private func nonEmpty(_ value: String?) -> String? {
        guard let value, !value.isEmpty else { return nil }
        return value
    }

    private func openDeepLink(_ url: String, in app: XCUIApplication) {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        app.open(URL(string: url)!)
        let openButton = springboard.buttons["Open"].firstMatch
        if openButton.waitForExistence(timeout: 3) {
            openButton.tap()
        }
    }

    private func encodedFragment(_ value: String) -> String {
        value.addingPercentEncoding(withAllowedCharacters: .urlFragmentAllowed) ?? value
    }

    private func encodedQuery(_ value: String) -> String {
        value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? value
    }

    private func attachScreenshot(_ app: XCUIApplication, name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
