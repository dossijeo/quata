import XCTest
import UIKit

/// Opt-in, production-host gate for `CHAT-COMPOSER` / selected message actions.
/// The companion runner seeds the Keychain session and disposable backend conversation first.
@available(iOS 16.4, *)
final class QuataIosAuthenticatedChatActionsNotificationsUITests: XCTestCase {
    func testComposerReplyEditAndSelectedActionsUseSharedChatSurface() throws {
        let environment = ProcessInfo.processInfo.environment
        guard environment["QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_UI_E2E"] == "1" else {
            throw XCTSkip("Authenticated Chat composer/action UI gate is opt-in.")
        }
        guard let conversationId = nonEmpty(environment["QUATA_IOS_CHAT_E2E_CONVERSATION_ID"]),
              let seedMessageId = nonEmpty(environment["QUATA_IOS_CHAT_E2E_MESSAGE_ID"]),
              let seedMarkerProbe = nonEmpty(environment["QUATA_IOS_CHAT_E2E_MARKER_PROBE"]),
              let editableMessageId = nonEmpty(environment["QUATA_IOS_CHAT_E2E_EDITABLE_MESSAGE_ID"]),
              let editableMarker = nonEmpty(environment["QUATA_IOS_CHAT_E2E_EDITABLE_MARKER"]),
              let composerMarker = nonEmpty(environment["QUATA_IOS_CHAT_E2E_COMPOSER_MARKER"]),
              let replyMarker = nonEmpty(environment["QUATA_IOS_CHAT_E2E_REPLY_MARKER"]),
              let editMarker = nonEmpty(environment["QUATA_IOS_CHAT_E2E_EDIT_MARKER"]) else {
            throw XCTSkip("Disposable Chat composer/action fixture is not configured.")
        }

        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(es)", "-AppleLocale", "es_ES"]
        app.launch()

        let feed = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-feed-host")
            .firstMatch
        XCTAssertTrue(feed.waitForExistence(timeout: 20), "The seeded normal launch must restore Feed.")

        openDeepLink("quata://egquata.com/#chat-\(encodedFragment(conversationId))?message=\(encodedQuery(seedMessageId))", in: app)
        _ = chatHost(in: app, context: "composer/action conversation")
        assertChatRoute(conversationId, messageId: seedMessageId, in: app, context: "composer/action conversation")
        XCTAssertTrue(messageText(seedMarkerProbe, in: app).waitForExistence(timeout: 45), app.debugDescription)
        XCTAssertTrue(messageText(editableMarker, in: app).waitForExistence(timeout: 45), app.debugDescription)
        assertAuthenticatedChrome(in: app, context: "composer/action conversation")
        assertPrimaryNavigationHidden(in: app, context: "composer/action conversation")
        attachScreenshot(app, name: "ios-chat-actions-thread-initial")

        typeText(composerMarker, into: "chat.composer.input", in: app)
        assertConversationHeaderVisibleWithKeyboard(in: app)
        tapTaggedButton("chat.composer.send", in: app, context: "send composer message")
        XCTAssertTrue(messageText(composerMarker, in: app).waitForExistence(timeout: 45), app.debugDescription)
        attachScreenshot(app, name: "ios-chat-composer-sent")
        dismissKeyboardIfPresent(in: app)

        selectMessage(seedMarkerProbe, expectedMessageId: seedMessageId, in: app, context: "seed reply selection")
        tapTaggedButton("chat.action.favorite", in: app, context: "favorite seed message")
        selectMessage(seedMarkerProbe, expectedMessageId: seedMessageId, in: app, context: "seed reply selection after favorite")
        tapTaggedButton("chat.action.reply", in: app, context: "start reply")
        typeText(replyMarker, into: "chat.composer.input", in: app)
        tapTaggedButton("chat.composer.send", in: app, context: "send reply")
        XCTAssertTrue(messageText(replyMarker, in: app).waitForExistence(timeout: 45), app.debugDescription)
        attachScreenshot(app, name: "ios-chat-composer-reply-sent")
        dismissKeyboardIfPresent(in: app)

        openDeepLink("quata://egquata.com/#chat-\(encodedFragment(conversationId))?message=\(encodedQuery(editableMessageId))", in: app)
        _ = chatHost(in: app, context: "editable message conversation")
        XCTAssertTrue(messageText(editableMarker, in: app).waitForExistence(timeout: 45), app.debugDescription)
        attachScreenshot(app, name: "ios-chat-actions-editable-focused")
        waitForFocusedMessageHighlightToClear(editableMessageId, in: app)

        selectMessage(editableMarker, expectedMessageId: editableMessageId, in: app, context: "own message edit selection")
        assertActionBarOwnMessage(in: app)
        startEditingMessage(
            marker: editableMarker,
            messageId: editableMessageId,
            in: app,
            context: "start edit",
        )
        clearAndTypeText(editMarker, into: "chat.composer.input", in: app)
        tapTaggedButton("chat.composer.send", in: app, context: "submit edit")
        XCTAssertTrue(
            messageWithId(editableMessageId, containing: editMarker, in: app).waitForExistence(timeout: 45),
            "Editing must update the selected message id, not create a separate message.\n\(app.debugDescription)",
        )
        waitForMessagePendingToClear(editableMessageId, in: app, context: "edited message backend sync")
        RunLoop.current.run(until: Date().addingTimeInterval(12))
        XCTAssertTrue(
            messageWithId(editableMessageId, containing: editMarker, in: app).exists,
            "The edited message must remain visible after the backend mutation has had time to settle.",
        )
        XCTAssertFalse(messageText(editableMarker, in: app).exists, "Editing must replace the original message text instead of appending to it.")
        attachScreenshot(app, name: "ios-chat-composer-edit-sent")
        dismissKeyboardIfPresent(in: app)
    }

    private func chatHost(in app: XCUIApplication, context: String) -> XCUIElement {
        let chat = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-chat-host")
            .firstMatch
        XCTAssertTrue(chat.waitForExistence(timeout: 20), "Chat host did not mount for \(context).")
        return chat
    }

    private func assertChatRoute(_ conversationId: String, messageId: String? = nil, in app: XCUIApplication, context: String) {
        let chat = chatHost(in: app, context: context)
        var expected = "chat:\(conversationId)"
        if let messageId {
            expected += "?message=\(messageId)"
        }
        XCTAssertEqual(chat.value as? String, expected, "Chat host must expose the exact route for \(context).")
    }

    private func messageText(_ markerProbe: String, in app: XCUIApplication) -> XCUIElement {
        app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", markerProbe))
            .firstMatch
    }

    private func messageWithId(_ messageId: String, containing markerProbe: String, in app: XCUIApplication) -> XCUIElement {
        app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier == %@ AND label CONTAINS %@", "chat.message.\(messageId)", markerProbe))
            .firstMatch
    }

    private func selectMessage(_ markerProbe: String, expectedMessageId: String?, in app: XCUIApplication, context: String) {
        let candidates = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", markerProbe))
        var target: XCUIElement
        if candidates.count > 0 {
            target = candidates.element(boundBy: 0)
            for index in 1..<candidates.count {
                let candidate = candidates.element(boundBy: index)
                if candidate.frame.minY > target.frame.minY {
                    target = candidate
                }
            }
        } else if let expectedMessageId {
            let selectedById = app.descendants(matching: .any)
                .matching(identifier: "chat.message.\(expectedMessageId).selected")
                .firstMatch
            target = selectedById.exists ? selectedById : app.descendants(matching: .any)
                .matching(identifier: "chat.message.\(expectedMessageId)")
                .firstMatch
        } else {
            XCTFail("Expected an actionable message for \(context).")
            return
        }
        XCTAssertTrue(target.waitForExistence(timeout: 10), "Expected actionable message for \(context).")
        target.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        if let expectedMessageId {
            let selected = app.descendants(matching: .any)
                .matching(identifier: "chat.message.\(expectedMessageId).selected")
                .firstMatch
            if !selected.waitForExistence(timeout: 4) {
                target.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
            }
            XCTAssertTrue(selected.waitForExistence(timeout: 10), "Expected selected semantics for \(context).")
        }
    }

    private func assertActionBarOwnMessage(in app: XCUIApplication) {
        for identifier in ["chat.action.copy", "chat.action.reply", "chat.action.forward", "chat.action.edit", "chat.action.favorite", "chat.action.delete"] {
            XCTAssertTrue(
                app.descendants(matching: .any).matching(identifier: identifier).firstMatch.waitForExistence(timeout: 10),
                "Selected own message action \(identifier) must be exposed by the shared action bar.",
            )
        }
    }

    private func assertConversationHeaderVisibleWithKeyboard(in app: XCUIApplication) {
        let titleBar = app.descendants(matching: .any)
            .matching(identifier: "chat.conversation.titlebar")
            .firstMatch
        XCTAssertTrue(titleBar.waitForExistence(timeout: 10), "The shared Chat header must stay mounted while the keyboard is open.")
        XCTAssertGreaterThanOrEqual(titleBar.frame.minY, 0, "The shared Chat header must not be pushed above the viewport by the iOS keyboard.")
        XCTAssertLessThan(titleBar.frame.minY, 220, "The shared Chat header must remain in the upper viewport while the keyboard is open.")
    }

    private func startEditingMessage(marker: String, messageId: String, in app: XCUIApplication, context: String) {
        for attempt in 0..<3 {
            tapTaggedButton("chat.action.edit", in: app, context: context)
            if waitForComposerValue(containing: marker, in: app, timeout: 8) {
                return
            }
            dismissKeyboardIfPresent(in: app)
            selectMessage(marker, expectedMessageId: messageId, in: app, context: "\(context) retry \(attempt + 1)")
            assertActionBarOwnMessage(in: app)
        }
        XCTFail("Expected shared composer to enter edit mode for \(context).")
    }

    private func waitForComposerValue(containing expected: String, in app: XCUIApplication, timeout: TimeInterval) -> Bool {
        let field = app.descendants(matching: .any).matching(identifier: "chat.composer.input").firstMatch
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if field.waitForExistence(timeout: 1),
               let value = field.value as? String,
               value.contains(expected) {
                return true
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.3))
        }
        return false
    }

    private func waitForFocusedMessageHighlightToClear(_ messageId: String, in app: XCUIApplication) {
        let focused = app.descendants(matching: .any)
            .matching(identifier: "chat.message.\(messageId).selected")
            .firstMatch
        let deadline = Date().addingTimeInterval(12)
        while focused.exists && Date() < deadline {
            RunLoop.current.run(until: Date().addingTimeInterval(0.5))
        }
        XCTAssertFalse(focused.exists, "The deep-link focus highlight must clear before selecting the message for actions.")
    }

    private func waitForMessagePendingToClear(_ messageId: String, in app: XCUIApplication, context: String) {
        let pending = app.descendants(matching: .any)
            .matching(identifier: "chat.message.\(messageId).pending")
            .firstMatch
        let deadline = Date().addingTimeInterval(45)
        while pending.exists && Date() < deadline {
            RunLoop.current.run(until: Date().addingTimeInterval(0.5))
        }
        XCTAssertFalse(pending.exists, "Expected \(context) to finish before ending the UI gate.")
    }

    private func tapTaggedButton(_ identifier: String, in app: XCUIApplication, context: String) {
        let button = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        for _ in 0..<8 {
            if button.waitForExistence(timeout: 1), button.isHittable {
                button.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
                return
            }
            app.swipeUp()
            RunLoop.current.run(until: Date().addingTimeInterval(0.3))
        }
        XCTAssertTrue(button.exists, "Expected \(identifier) for \(context).")
        button.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
    }

    private func typeText(_ value: String, into identifier: String, in app: XCUIApplication) {
        let field = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        for attempt in 0..<12 {
            if field.waitForExistence(timeout: 1), field.isHittable {
                field.tap()
                if app.keyboards.count > 0 {
                    pasteText(value, into: field, in: app)
                    return
                }
            }
            if attempt < 6 {
                app.swipeDown()
            } else {
                app.swipeUp()
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.3))
        }
        XCTAssertTrue(field.exists, "Expected editable field \(identifier) to exist.")
        pasteText(value, into: field, in: app)
    }

    private func clearAndTypeText(_ value: String, into identifier: String, in app: XCUIApplication) {
        let field = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 10), "Expected editable field \(identifier) to exist.")
        field.coordinate(withNormalizedOffset: CGVector(dx: 0.22, dy: 0.5)).tap()
        RunLoop.current.run(until: Date().addingTimeInterval(0.3))
        field.press(forDuration: 0.7)
        let selectAll = app.menuItems.matching(NSPredicate(format: "label CONTAINS[c] %@ OR label CONTAINS[c] %@", "Select All", "Seleccionar todo")).firstMatch
        if selectAll.waitForExistence(timeout: 2) {
            selectAll.tap()
            typeIntoFocusedElement(value, fallback: field, in: app)
            return
        }
        typeIntoFocusedElement(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 180), fallback: field, in: app)
        typeIntoFocusedElement(value, fallback: field, in: app)
    }

    private func pasteText(_ value: String, into field: XCUIElement, in app: XCUIApplication) {
        UIPasteboard.general.string = value
        field.coordinate(withNormalizedOffset: CGVector(dx: 0.22, dy: 0.5)).tap()
        RunLoop.current.run(until: Date().addingTimeInterval(0.3))
        field.press(forDuration: 0.7)
        let paste = app.menuItems.matching(NSPredicate(format: "label CONTAINS[c] %@ OR label CONTAINS[c] %@", "Paste", "Pegar")).firstMatch
        if paste.waitForExistence(timeout: 3) {
            paste.tap()
            RunLoop.current.run(until: Date().addingTimeInterval(0.5))
            return
        }
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
            if key.exists, key.isHittable {
                key.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
                RunLoop.current.run(until: Date().addingTimeInterval(0.3))
                if app.keyboards.count == 0 {
                    return
                }
            }
        }
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.12)).tap()
        RunLoop.current.run(until: Date().addingTimeInterval(0.3))
        if app.keyboards.count > 0 {
            app.swipeDown()
        }
    }

    private func assertAuthenticatedChrome(in app: XCUIApplication, context: String) {
        let topChrome = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-authenticated-top-chrome")
            .firstMatch
        XCTAssertTrue(topChrome.exists, "Authenticated top chrome must remain mounted for \(context).")
        let sos = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "SOS")).firstMatch
        XCTAssertTrue(sos.exists, "SOS action must remain visible in the top chrome for \(context).")
    }

    private func assertPrimaryNavigationHidden(in app: XCUIApplication, context: String) {
        let primaryNavigation = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-authenticated-primary-navigation")
            .firstMatch
        XCTAssertFalse(primaryNavigation.exists, "Chat must hide the app primary navigation for \(context).")
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
