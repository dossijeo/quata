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
              let editMarker = nonEmpty(environment["QUATA_IOS_CHAT_E2E_EDIT_MARKER"]),
              let forwardQuery = nonEmpty(environment["QUATA_IOS_CHAT_E2E_FORWARD_QUERY"]) else {
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
        XCTAssertTrue(waitForComposerValue(containing: editMarker, in: app, timeout: 8), "The edit composer must contain the exact replacement marker before submit.")
        tapTaggedButton("chat.composer.send", in: app, context: "submit edit")
        dismissKeyboardIfPresent(in: app)
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

        openDeepLink("quata://egquata.com/#chat-\(encodedFragment(conversationId))?message=\(encodedQuery(editableMessageId))", in: app)
        _ = chatHost(in: app, context: "forward refreshed conversation")
        XCTAssertTrue(messageWithId(editableMessageId, containing: editMarker, in: app).waitForExistence(timeout: 45), app.debugDescription)
        waitForFocusedMessageHighlightToClear(editableMessageId, in: app)
        selectMessage(editMarker, expectedMessageId: editableMessageId, in: app, context: "own message forward selection")
        assertActionBarOwnMessage(in: app)
        tapTaggedButton("chat.action.forward", in: app, context: "start forward")
        XCTAssertTrue(
            app.descendants(matching: .any).matching(identifier: "chat.forward.root").firstMatch.waitForExistence(timeout: 15),
            "The shared forward picker must mount.",
        )
        clearAndTypeText(forwardQuery, into: "chat.forward.search", in: app)
        selectForwardDestination(forwardQuery, in: app)
        attachScreenshot(app, name: "ios-chat-forward-picker-selected")
        tapTaggedButton("chat.forward.send", in: app, context: "send forward")
        RunLoop.current.run(until: Date().addingTimeInterval(2))
    }

    func testProfileEntryFromChatOpensPublicProfileAndReturns() throws {
        let environment = ProcessInfo.processInfo.environment
        guard environment["QUATA_IOS_CHAT_PROFILE_UI_E2E"] == "1" else {
            throw XCTSkip("Authenticated Chat profile UI gate is opt-in.")
        }
        guard let conversationId = nonEmpty(environment["QUATA_IOS_CHAT_E2E_CONVERSATION_ID"]),
              let peerMarkerProbe = nonEmpty(environment["QUATA_IOS_CHAT_PROFILE_E2E_MARKER_PROBE"]),
              let peerProfileId = nonEmpty(environment["QUATA_IOS_CHAT_PROFILE_E2E_PROFILE_ID"]) else {
            throw XCTSkip("Disposable Chat profile fixture is not configured.")
        }

        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(es)", "-AppleLocale", "es_ES"]
        app.launch()

        let feed = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-feed-host")
            .firstMatch
        XCTAssertTrue(feed.waitForExistence(timeout: 20), "The seeded normal launch must restore Feed.")

        openDeepLink("quata://egquata.com/#chat-\(encodedFragment(conversationId))", in: app)
        _ = chatHost(in: app, context: "profile entry conversation")
        assertChatRoute(conversationId, in: app, context: "profile entry conversation")
        XCTAssertTrue(messageText(peerMarkerProbe, in: app).waitForExistence(timeout: 45), app.debugDescription)
        attachScreenshot(app, name: "ios-chat-profile-thread-initial")

        let profile = openPeerPublicProfile(peerProfileId: peerProfileId, in: app)
        for identifier in [
            "public-profile.avatar.\(peerProfileId)",
            "public-profile.name.\(peerProfileId)",
            "public-profile.neighborhood.\(peerProfileId)",
            "public-profile.kpi.posts.\(peerProfileId)",
            "public-profile.kpi.followers.\(peerProfileId)",
            "public-profile.kpi.following.\(peerProfileId)",
        ] {
            let headerField = app.descendants(matching: .any)
                .matching(identifier: identifier)
                .firstMatch
            XCTAssertTrue(headerField.waitForExistence(timeout: 10), "The shared public-profile header field \(identifier) must be visible.")
        }
        attachScreenshot(app, name: "ios-chat-profile-open")

        closePublicProfile(in: app)
        XCTAssertTrue(profile.waitForNonExistence(timeout: 10), "The public profile sheet must close after the dismiss gesture.")
        XCTAssertTrue(messageText(peerMarkerProbe, in: app).waitForExistence(timeout: 20), "Closing the profile must return to the same Chat conversation.")
        attachScreenshot(app, name: "ios-chat-profile-return")
    }

    func testProfileFollowFromChatTogglesSharedPublicProfileAction() throws {
        let environment = ProcessInfo.processInfo.environment
        guard environment["QUATA_IOS_CHAT_PROFILE_FOLLOW_UI_E2E"] == "1" else {
            throw XCTSkip("Authenticated Chat profile follow UI gate is opt-in.")
        }
        guard let conversationId = nonEmpty(environment["QUATA_IOS_CHAT_E2E_CONVERSATION_ID"]),
              let peerMarkerProbe = nonEmpty(environment["QUATA_IOS_CHAT_PROFILE_E2E_MARKER_PROBE"]),
              let peerProfileId = nonEmpty(environment["QUATA_IOS_CHAT_PROFILE_E2E_PROFILE_ID"]) else {
            throw XCTSkip("Disposable Chat profile follow fixture is not configured.")
        }

        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(es)", "-AppleLocale", "es_ES"]
        app.launch()

        let feed = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-feed-host")
            .firstMatch
        XCTAssertTrue(feed.waitForExistence(timeout: 20), "The seeded normal launch must restore Feed.")

        openDeepLink("quata://egquata.com/#chat-\(encodedFragment(conversationId))", in: app)
        _ = chatHost(in: app, context: "profile follow conversation")
        assertChatRoute(conversationId, in: app, context: "profile follow conversation")
        XCTAssertTrue(messageText(peerMarkerProbe, in: app).waitForExistence(timeout: 45), app.debugDescription)
        attachScreenshot(app, name: "ios-chat-profile-follow-thread-initial")

        let profile = openPeerPublicProfile(peerProfileId: peerProfileId, in: app)
        attachScreenshot(app, name: "ios-chat-profile-follow-before")

        let follow = app.descendants(matching: .any)
            .matching(identifier: "public-profile.follow.\(peerProfileId)")
            .firstMatch
        XCTAssertTrue(follow.waitForExistence(timeout: 10), "The shared public profile follow action must be exposed.")
        follow.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()

        let loading = app.descendants(matching: .any)
            .matching(identifier: "public-profile.follow.loading.\(peerProfileId)")
            .firstMatch
        _ = loading.waitForNonExistence(timeout: 20)
        attachScreenshot(app, name: "ios-chat-profile-follow-after")

        closePublicProfile(in: app)
        XCTAssertTrue(profile.waitForNonExistence(timeout: 10), "The public profile sheet must close after toggling follow.")
        XCTAssertTrue(messageText(peerMarkerProbe, in: app).waitForExistence(timeout: 20), "Closing the profile must return to the same Chat conversation.")
        attachScreenshot(app, name: "ios-chat-profile-follow-return")
    }

    func testProfileFollowListsFromChatOpenAndReturn() throws {
        let environment = ProcessInfo.processInfo.environment
        guard environment["QUATA_IOS_CHAT_PROFILE_LISTS_UI_E2E"] == "1" else {
            throw XCTSkip("Authenticated Chat profile follow-lists UI gate is opt-in.")
        }
        guard let conversationId = nonEmpty(environment["QUATA_IOS_CHAT_E2E_CONVERSATION_ID"]),
              let peerMarkerProbe = nonEmpty(environment["QUATA_IOS_CHAT_PROFILE_E2E_MARKER_PROBE"]),
              let peerProfileId = nonEmpty(environment["QUATA_IOS_CHAT_PROFILE_E2E_PROFILE_ID"]) else {
            throw XCTSkip("Disposable Chat profile follow-lists fixture is not configured.")
        }

        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(es)", "-AppleLocale", "es_ES"]
        app.launch()

        let feed = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-feed-host")
            .firstMatch
        XCTAssertTrue(feed.waitForExistence(timeout: 20), "The seeded normal launch must restore Feed.")

        openDeepLink("quata://egquata.com/#chat-\(encodedFragment(conversationId))", in: app)
        _ = chatHost(in: app, context: "profile follow-lists conversation")
        assertChatRoute(conversationId, in: app, context: "profile follow-lists conversation")
        XCTAssertTrue(messageText(peerMarkerProbe, in: app).waitForExistence(timeout: 45), app.debugDescription)
        attachScreenshot(app, name: "ios-chat-profile-lists-thread-initial")

        let avatar = app.descendants(matching: .any)
            .matching(identifier: "chat.profile.message.\(peerProfileId)")
            .firstMatch
        XCTAssertTrue(avatar.waitForExistence(timeout: 20), "The peer message avatar must expose the shared profile-entry tag.")
        avatar.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()

        let profile = app.descendants(matching: .any)
            .matching(identifier: "public-profile.user.\(peerProfileId)")
            .firstMatch
        XCTAssertTrue(profile.waitForExistence(timeout: 30), "Opening the peer avatar must mount the shared public profile.")
        attachScreenshot(app, name: "ios-chat-profile-lists-open")

        openAndAssertProfileList("followers", profileId: peerProfileId, in: app)
        openAndAssertProfileList("following", profileId: peerProfileId, in: app)

        closePublicProfile(profile, in: app)
        XCTAssertTrue(messageText(peerMarkerProbe, in: app).waitForExistence(timeout: 20), "Closing the profile must return to the same Chat conversation.")
        attachScreenshot(app, name: "ios-chat-profile-lists-return")
    }

    func testProfileContentFromChatUsesSharedPublicProfileSurface() throws {
        let environment = ProcessInfo.processInfo.environment
        guard environment["QUATA_IOS_CHAT_PROFILE_CONTENT_UI_E2E"] == "1" else {
            throw XCTSkip("Authenticated Chat profile content UI gate is opt-in.")
        }
        guard let conversationId = nonEmpty(environment["QUATA_IOS_CHAT_E2E_CONVERSATION_ID"]),
              let peerMarkerProbe = nonEmpty(environment["QUATA_IOS_CHAT_PROFILE_E2E_MARKER_PROBE"]),
              let peerProfileId = nonEmpty(environment["QUATA_IOS_CHAT_PROFILE_E2E_PROFILE_ID"]),
              let postId = nonEmpty(environment["QUATA_IOS_CHAT_PROFILE_CONTENT_POST_ID"]),
              let commentId = nonEmpty(environment["QUATA_IOS_CHAT_PROFILE_CONTENT_COMMENT_ID"]),
              let attachmentId = nonEmpty(environment["QUATA_IOS_CHAT_PROFILE_CONTENT_ATTACHMENT_ID"]) else {
            throw XCTSkip("Disposable Chat profile content fixture is not configured.")
        }

        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(es)", "-AppleLocale", "es_ES"]
        app.launch()

        let feed = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-feed-host")
            .firstMatch
        XCTAssertTrue(feed.waitForExistence(timeout: 20), "The seeded normal launch must restore Feed.")

        openDeepLink("quata://egquata.com/#chat-\(encodedFragment(conversationId))", in: app)
        _ = chatHost(in: app, context: "profile content conversation")
        assertChatRoute(conversationId, in: app, context: "profile content conversation")
        XCTAssertTrue(messageText(peerMarkerProbe, in: app).waitForExistence(timeout: 45), app.debugDescription)
        attachScreenshot(app, name: "ios-chat-profile-content-thread-initial")

        let profile = openPeerPublicProfile(peerProfileId: peerProfileId, in: app)
        assertProfileContentStage(profileId: peerProfileId, postId: postId, commentId: commentId, attachmentId: attachmentId, in: app)

        closePublicProfile(in: app)
        XCTAssertTrue(profile.waitForNonExistence(timeout: 10), "The public profile sheet must close after checking content.")
        XCTAssertTrue(messageText(peerMarkerProbe, in: app).waitForExistence(timeout: 20), "Closing the profile content view must return to the same Chat conversation.")
        attachScreenshot(app, name: "ios-chat-profile-content-return")
    }

    private func assertProfileContentStage(profileId: String, postId: String, commentId: String, attachmentId: String, in app: XCUIApplication) {
        let posts = app.descendants(matching: .any)
            .matching(identifier: "public-profile.kpi.posts.\(profileId)")
            .firstMatch
        XCTAssertTrue(posts.waitForExistence(timeout: 10), "The shared profile posts KPI must be visible.")
        posts.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()

        for identifier in [
            "public-profile.gallery.header.\(profileId)",
            "public-profile.gallery.\(profileId)",
            "public-profile.gallery.post.\(postId)",
            "public-profile.post.preview.\(postId)",
            "public-profile.post.action.comments.\(postId)",
            "public-profile.comments.panel",
            "public-profile.comments.list",
            "public-profile.comments.row.\(commentId)",
            "public-profile.comments.input",
            "public-profile.comments.send",
            "public-profile.attachments",
            "public-profile.attachments.item.\(attachmentId)",
        ] {
            let element = app.descendants(matching: .any)
                .matching(identifier: identifier)
                .firstMatch
            XCTAssertTrue(element.waitForExistence(timeout: 10), "The shared public-profile content element \(identifier) must be visible.")
        }
        attachScreenshot(app, name: "ios-chat-profile-content")
    }

    private func chatHost(in app: XCUIApplication, context: String) -> XCUIElement {
        let chat = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-chat-host")
            .firstMatch
        XCTAssertTrue(chat.waitForExistence(timeout: 20), "Chat host did not mount for \(context).")
        return chat
    }

    private func openAndAssertProfileList(_ listKind: String, profileId: String, in app: XCUIApplication) {
        let kpi = app.descendants(matching: .any)
            .matching(identifier: "public-profile.kpi.\(listKind).\(profileId)")
            .firstMatch
        XCTAssertTrue(kpi.waitForExistence(timeout: 10), "The public profile \(listKind) KPI must expose a shared tag.")
        kpi.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()

        let list = app.descendants(matching: .any)
            .matching(identifier: "public-profile.list.\(listKind)")
            .firstMatch
        XCTAssertTrue(list.waitForExistence(timeout: 20), "The shared public-profile \(listKind) list must open.")
        let row = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH %@", "public-profile.list.row.\(listKind)."))
            .firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 10), "The shared public-profile \(listKind) list must expose at least one user row.")
        attachScreenshot(app, name: "ios-chat-profile-list-\(listKind)")

        let back = app.descendants(matching: .any)
            .matching(identifier: "public-profile.list.back.\(listKind)")
            .firstMatch
        XCTAssertTrue(back.waitForExistence(timeout: 10), "The shared public-profile \(listKind) list must expose a back action.")
        back.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()

        let profile = app.descendants(matching: .any)
            .matching(identifier: "public-profile.user.\(profileId)")
            .firstMatch
        XCTAssertTrue(profile.waitForExistence(timeout: 10), "Returning from \(listKind) must restore the parent public profile.")
    }

    private func closePublicProfile(_ profile: XCUIElement, in app: XCUIApplication) {
        let back = app.descendants(matching: .any)
            .matching(identifier: "public-profile.back")
            .firstMatch
        if back.waitForExistence(timeout: 5), back.isHittable {
            back.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        } else {
            let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.12))
            let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.88))
            start.press(forDuration: 0.1, thenDragTo: end)
        }
        XCTAssertTrue(profile.waitForNonExistence(timeout: 10), "The public profile sheet must close after the dismiss gesture.")
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

    private func openPeerPublicProfile(peerProfileId: String, in app: XCUIApplication) -> XCUIElement {
        let avatar = app.descendants(matching: .any)
            .matching(identifier: "chat.profile.message.\(peerProfileId)")
            .firstMatch
        XCTAssertTrue(avatar.waitForExistence(timeout: 20), "The peer message avatar must expose the shared profile-entry tag.")
        avatar.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()

        let profile = app.descendants(matching: .any)
            .matching(identifier: "public-profile.user.\(peerProfileId)")
            .firstMatch
        XCTAssertTrue(profile.waitForExistence(timeout: 30), "Opening the peer avatar must mount the shared public profile.")
        return profile
    }

    private func closePublicProfile(in app: XCUIApplication) {
        let back = app.descendants(matching: .any)
            .matching(identifier: "public-profile.back")
            .firstMatch
        if back.waitForExistence(timeout: 5), back.isHittable {
            back.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        } else {
            let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.12))
            let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.88))
            start.press(forDuration: 0.1, thenDragTo: end)
        }
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

    private func selectForwardDestination(_ query: String, in app: XCUIApplication) {
        let destination = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", query)).firstMatch
        if destination.waitForExistence(timeout: 15) {
            destination.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
            return
        }
        let anyDestination = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", query))
            .firstMatch
        XCTAssertTrue(anyDestination.waitForExistence(timeout: 10), "Expected forward destination containing \(query).")
        anyDestination.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
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
        for _ in 0..<6 {
            let current = fieldValue(field)
            if current.isEmpty || current == "Mensaje" || current == "Message" || !current.starts(with: "chat-actions-ios-") {
                break
            }
            field.coordinate(withNormalizedOffset: CGVector(dx: 0.92, dy: 0.5)).tap()
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
            typeIntoFocusedElement(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 320), fallback: field, in: app)
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        }
        XCTAssertFalse(fieldValue(field).starts(with: "chat-actions-ios-"), "The edit composer still contains the original message text.")
        pasteText(value, into: field, in: app)
    }

    private func fieldValue(_ field: XCUIElement) -> String {
        if let value = field.value as? String {
            return value
        }
        return ""
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
            fallback.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
            RunLoop.current.run(until: Date().addingTimeInterval(0.3))
            let refocused = app.descendants(matching: .any)
                .matching(NSPredicate(format: "hasKeyboardFocus == 1"))
                .firstMatch
            if refocused.waitForExistence(timeout: 2) {
                refocused.typeText(value)
            } else {
                fallback.typeText(value)
            }
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
