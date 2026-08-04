import XCTest

/// Opt-in, production-host gate for `CHAT-TRANSLATION` / `FLOW-TRANSLATOR`.
/// The runner seeds the normal Keychain session and supplies disposable backend fixture IDs.
final class QuataIosAuthenticatedChatTranslationUITests: XCTestCase {
    func testRealChatMessageTranslatesAndReturnsToTheConversation() throws {
        let environment = ProcessInfo.processInfo.environment
        guard environment["QUATA_IOS_AUTH_UI_E2E"] == "1" else {
            throw XCTSkip("Authenticated Chat translation UI gate is opt-in.")
        }
        guard let threadID = environment["QUATA_IOS_CHAT_E2E_THREAD_ID"],
              let messageID = environment["QUATA_IOS_CHAT_E2E_MESSAGE_ID"],
              !threadID.isEmpty,
              !messageID.isEmpty else {
            throw XCTSkip("Disposable Chat fixture IDs are not configured.")
        }

        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(es)", "-AppleLocale", "es_ES"]
        app.launch()

        let feed = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-feed-host")
            .firstMatch
        XCTAssertTrue(feed.waitForExistence(timeout: 20), "The seeded normal launch must restore Feed.")

        let encodedConversation = "sb:\(threadID)"
            .addingPercentEncoding(withAllowedCharacters: .alphanumerics)!
        let deepLink = URL(
            string: "quata://egquata.com/#chat-\(encodedConversation)?message=\(messageID)",
        )!
        guard #available(iOS 16.4, *) else {
            throw XCTSkip("Opening a deep link from XCUITest requires iOS 16.4 or newer.")
        }
        app.open(deepLink)

        let chat = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-chat-host")
            .firstMatch
        XCTAssertTrue(chat.waitForExistence(timeout: 20), "The real deep link must mount Chat.")
        XCTAssertTrue(app.staticTexts["Mbolo"].firstMatch.waitForExistence(timeout: 20), app.debugDescription)
        attachScreenshot(app, name: "chat-translation-before")

        let translator = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", "Traductor Fang"))
            .firstMatch
        XCTAssertTrue(translator.waitForExistence(timeout: 10), "Chat must expose its shared translator trigger.")
        translator.tap()

        let instruction = app.staticTexts["Toca un mensaje para traducirlo."].firstMatch
        XCTAssertTrue(instruction.waitForExistence(timeout: 10), "The shared translator overlay must be visible.")
        attachScreenshot(app, name: "chat-translation-overlay")

        let overlayMessage = app.staticTexts["Mbolo"].firstMatch
        XCTAssertTrue(overlayMessage.isHittable, "The registered message surface must be actionable.")
        overlayMessage.tap()

        let translatedDirection = app.staticTexts.matching(
            NSPredicate(format: "label CONTAINS %@ AND label CONTAINS %@", "FAN", "ES"),
        ).firstMatch
        XCTAssertTrue(
            translatedDirection.waitForExistence(timeout: 35),
            "A real Fang response must expose its direction label instead of a callback-only success.",
        )
        attachScreenshot(app, name: "chat-translation-result")

        let close = app.buttons["Cerrar"].firstMatch
        XCTAssertTrue(close.waitForExistence(timeout: 5))
        close.tap()
        XCTAssertTrue(chat.waitForExistence(timeout: 5), "Closing the translator must preserve Chat.")
        XCTAssertFalse(instruction.exists, "The translator overlay must leave the composition.")
        XCTAssertTrue(app.staticTexts["Mbolo"].firstMatch.exists, "The original conversation must be restored.")
        attachScreenshot(app, name: "chat-translation-return")
    }

    private func attachScreenshot(_ app: XCUIApplication, name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
