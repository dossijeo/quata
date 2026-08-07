import XCTest

/// Opt-in, production-host gate for `CHAT-FAVORITES` / `CHAT-FOCUSED-MESSAGE`.
///
/// The runner seeds the normal Keychain session and creates a disposable backend favorite before
/// this test opens the same shared Chat deep links used by Android and Web.
@available(iOS 16.4, *)
final class QuataIosAuthenticatedChatFavoritesFocusedUITests: XCTestCase {
    func testFavoriteRouteOpensSourceAndFocusedDeepLinkHighlightsMessage() throws {
        let environment = ProcessInfo.processInfo.environment
        guard environment["QUATA_IOS_CHAT_FAVORITES_FOCUSED_UI_E2E"] == "1" else {
            throw XCTSkip("Authenticated Chat favorites/focused UI gate is opt-in.")
        }
        guard let conversationId = nonEmpty(environment["QUATA_IOS_CHAT_E2E_CONVERSATION_ID"]),
              let messageId = nonEmpty(environment["QUATA_IOS_CHAT_E2E_MESSAGE_ID"]),
              let markerProbe = nonEmpty(environment["QUATA_IOS_CHAT_E2E_MARKER_PROBE"]) else {
            throw XCTSkip("Disposable Chat favorites/focused fixture is not configured.")
        }

        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(es)", "-AppleLocale", "es_ES"]
        app.launch()

        let feed = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-feed-host")
            .firstMatch
        XCTAssertTrue(feed.waitForExistence(timeout: 20), "The seeded normal launch must restore Feed.")

        openDeepLink("quata://egquata.com/#chat-__favorite_messages__", in: app)
        let favoriteHost = chatHost(in: app, context: "favorite messages")
        XCTAssertTrue(messageText(markerProbe, in: app).waitForExistence(timeout: 45), app.debugDescription)
        attachScreenshot(app, name: "ios-favorites-list")

        let favoriteMessage = actionableMessage(markerProbe, in: app)
        XCTAssertTrue(favoriteMessage.waitForExistence(timeout: 10), "The favorite message must be actionable.")
        favoriteMessage.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        XCTAssertTrue(favoriteHost.waitForExistence(timeout: 10), "Opening a favorite must keep the Chat host mounted.")
        XCTAssertTrue(messageText(markerProbe, in: app).waitForExistence(timeout: 20), app.debugDescription)
        attachScreenshot(app, name: "ios-favorites-open-source")

        openDeepLink("quata://egquata.com/#chat-\(encodedFragment(conversationId))?message=\(encodedQuery(messageId))", in: app)
        _ = chatHost(in: app, context: "focused message")
        XCTAssertTrue(messageText(markerProbe, in: app).waitForExistence(timeout: 45), app.debugDescription)

        let focusedIdentifier = app.descendants(matching: .any)
            .matching(identifier: "chat.message.\(messageId).selected")
            .firstMatch
        XCTAssertTrue(
            focusedIdentifier.waitForExistence(timeout: 10),
            "The shared message bubble semantics must expose the focused message id as selected.",
        )
        attachScreenshot(app, name: "ios-focused-message")
    }

    private func chatHost(in app: XCUIApplication, context: String) -> XCUIElement {
        let chat = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-chat-host")
            .firstMatch
        XCTAssertTrue(chat.waitForExistence(timeout: 20), "Chat host did not mount for \(context).")
        return chat
    }

    private func messageText(_ markerProbe: String, in app: XCUIApplication) -> XCUIElement {
        app.staticTexts
            .matching(NSPredicate(format: "label CONTAINS %@", markerProbe))
            .firstMatch
    }

    private func actionableMessage(_ markerProbe: String, in app: XCUIApplication) -> XCUIElement {
        app.buttons
            .matching(NSPredicate(format: "label CONTAINS %@", markerProbe))
            .firstMatch
    }

    private func openDeepLink(_ value: String, in app: XCUIApplication) {
        guard let url = URL(string: value) else {
            XCTFail("Invalid deep link: \(value)")
            return
        }
        app.open(url)
    }

    private func encodedFragment(_ value: String) -> String {
        value.addingPercentEncoding(withAllowedCharacters: .urlFragmentAllowed) ?? value
    }

    private func encodedQuery(_ value: String) -> String {
        value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? value
    }

    private func nonEmpty(_ value: String?) -> String? {
        guard let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else {
            return nil
        }
        return trimmed
    }

    private func attachScreenshot(_ app: XCUIApplication, name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
