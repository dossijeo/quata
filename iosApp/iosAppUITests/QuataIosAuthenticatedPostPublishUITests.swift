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
        let mode = environment["QUATA_IOS_POST_PUBLISH_MODE"] ?? "text"
        XCTAssertTrue(["text", "image-location"].contains(mode), "Unsupported post publish mode \(mode).")
        let locationLabel = environment["QUATA_IOS_POST_PUBLISH_LOCATION_LABEL"] ?? ""
        if mode == "image-location" {
            XCTAssertFalse(locationLabel.isEmpty, "Image-location mode requires QUATA_IOS_POST_PUBLISH_LOCATION_LABEL.")
        }

        let app = openComposer(mode: mode, locationLabel: locationLabel)
        assertSharedComposerSurface(in: app)

        if mode == "text" {
            tapTextType(in: app)
        } else {
            assertImageLocationDraft(locationLabel, in: app)
        }
        selectDestination(destinationWallId, in: app)
        if mode == "text" {
            typeText(marker, into: "composer-text-input", in: app)
            dismissKeyboardIfPresent(in: app)
        } else {
            assertImageLocationDraft(locationLabel, in: app)
        }
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-publish-composer-filled")

        tapPublish(in: app)
        var retriedAfterForcedFailure = false
        if environment["QUATA_IOS_POST_PROGRESS_ROLLBACK_FAIL_ONCE"] == "1" ||
            environment["QUATA_IOS_POST_STORAGE_ROLLBACK_FAIL_AFTER_UPLOAD"] == "1" {
            let screenshotName = environment["QUATA_IOS_POST_STORAGE_ROLLBACK_FAIL_AFTER_UPLOAD"] == "1"
                ? "ios-post-storage-rollback-after-error"
                : "ios-post-progress-rollback-after-error"
            waitForRetryAndTap(in: app, screenshotName: screenshotName)
            retriedAfterForcedFailure = true
        }
        waitForPublishedFeedbackOrClose(in: app, allowExistingRetryError: retriedAfterForcedFailure)
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-publish-after-publish")
    }

    func testAuthenticatedSessionExercisesMediaSourceActionsFromCommonComposer() throws {
        let environment = ProcessInfo.processInfo.environment
        guard environment["QUATA_IOS_POST_PICKER_CAMERA_UI_E2E"] == "1" else {
            throw XCTSkip("Authenticated post picker/camera UI gate is opt-in.")
        }
        guard environment["QUATA_IOS_POST_COMPOSER_PICKER_FIXTURE_OPT_IN"] == "I_ACCEPT_IOS_POST_COMPOSER_PICKER_FIXTURE" else {
            throw XCTSkip("Post picker/camera fixture replay is opt-in.")
        }
        let source = environment["QUATA_IOS_POST_COMPOSER_PICKER_SOURCE"] ?? ""
        let outcome = environment["QUATA_IOS_POST_COMPOSER_PICKER_OUTCOME"] ?? "success"
        XCTAssertTrue(["gallery", "camera"].contains(source), "Unsupported picker source \(source).")
        XCTAssertTrue(["success", "cancelled", "failure", "unsupported"].contains(outcome), "Unsupported picker outcome \(outcome).")

        let app = openComposer(mode: "image", locationLabel: "")
        assertSharedComposerSurface(in: app)
        tapImageType(in: app)
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-picker-camera-image-form-\(source)-\(outcome)")

        let actionIdentifier = source == "gallery" ? "composer-media.pick-image" : "composer-media.capture-image"
        tapComposerAction(actionIdentifier, in: app)
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-picker-camera-after-tap-\(source)-\(outcome)")

        let selectedImagePreview = app.descendants(matching: .any)
            .matching(identifier: "composer-media.selected-image-preview")
            .firstMatch
        if outcome == "success" {
            XCTAssertTrue(selectedImagePreview.waitForExistence(timeout: 12), "A successful \(source) picker replay must select an image in common composer state.")
        } else {
            XCTAssertFalse(selectedImagePreview.waitForExistence(timeout: 2), "A non-success \(source) picker replay must not select an image.")
        }
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-picker-camera-after-action-\(source)-\(outcome)")
        print("IOS_POST_PICKER_CAMERA_UI_GATE_PASSED \(source) \(outcome)")
    }

    func testAuthenticatedSessionExercisesPostImageEditorFromCommonComposer() throws {
        let environment = ProcessInfo.processInfo.environment
        guard environment["QUATA_IOS_POST_IMAGE_EDITOR_UI_E2E"] == "1" else {
            throw XCTSkip("Authenticated post image editor UI gate is opt-in.")
        }
        guard environment["QUATA_IOS_POST_COMPOSER_PICKER_FIXTURE_OPT_IN"] == "I_ACCEPT_IOS_POST_COMPOSER_PICKER_FIXTURE" else {
            throw XCTSkip("Post image editor replay requires the picker fixture.")
        }
        let app = openComposer(mode: "image", locationLabel: "")
        assertSharedComposerSurface(in: app)
        tapImageType(in: app)
        tapComposerAction("composer-media.pick-image", in: app)
        let selectedImagePreview = app.descendants(matching: .any)
            .matching(identifier: "composer-media.selected-image-preview")
            .firstMatch
        XCTAssertTrue(selectedImagePreview.waitForExistence(timeout: 12), "A picker replay must select an image before editing.")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-image-editor-image-selected")

        tapComposerAction("composer-media.edit-image", in: app)
        let editorRoot = app.descendants(matching: .any)
            .matching(identifier: "post-image-editor.root")
            .firstMatch
        XCTAssertTrue(editorRoot.waitForExistence(timeout: 12), "The iOS composer must open the real shared post image editor surface.")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-image-editor-opened")
        tapComposerAction("post-image-editor.cancel", in: app)
        XCTAssertTrue(selectedImagePreview.waitForExistence(timeout: 12), "Cancelling the iOS image editor must preserve the common selected-image preview.")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-image-editor-after-cancel")
        tapComposerAction("composer-media.edit-image", in: app)
        XCTAssertTrue(editorRoot.waitForExistence(timeout: 12), "The iOS composer must reopen the shared post image editor after cancellation.")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-image-editor-reopened")
        tapComposerAction("post-image-editor.rotate", in: app)
        tapComposerAction("post-image-editor.reset", in: app)
        tapComposerAction("post-image-editor.save", in: app)
        XCTAssertTrue(selectedImagePreview.waitForExistence(timeout: 12), "Saving the iOS image editor must return to the common selected-image preview.")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-image-editor-after-edit")
        print("IOS_POST_IMAGE_EDITOR_UI_GATE_PASSED")
    }

    func testAuthenticatedSessionExercisesPostVideoEditorFromCommonComposer() throws {
        let environment = ProcessInfo.processInfo.environment
        guard environment["QUATA_IOS_POST_VIDEO_EDITOR_UI_E2E"] == "1" else {
            throw XCTSkip("Authenticated post video editor UI gate is opt-in.")
        }
        guard environment["QUATA_IOS_POST_COMPOSER_PICKER_FIXTURE_OPT_IN"] == "I_ACCEPT_IOS_POST_COMPOSER_PICKER_FIXTURE" else {
            throw XCTSkip("Post video editor replay requires the picker fixture.")
        }
        let app = openComposer(mode: "video", locationLabel: "")
        assertSharedComposerSurface(in: app)
        tapVideoType(in: app)
        tapComposerAction("composer-media.pick-video", in: app)
        let selectedVideoPreview = app.descendants(matching: .any)
            .matching(identifier: "composer-media.selected-video-preview")
            .firstMatch
        XCTAssertTrue(selectedVideoPreview.waitForExistence(timeout: 12), "A picker replay must select a video before editing.")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-video-editor-video-selected")

        tapComposerAction("composer-media.edit-video", in: app)
        let editorRoot = app.descendants(matching: .any)
            .matching(identifier: "post-video-editor.root")
            .firstMatch
        XCTAssertTrue(editorRoot.waitForExistence(timeout: 12), "The iOS composer must open the shared post video editor.")
        for identifier in [
            "post-video-editor.preview",
            "post-video-editor.mute",
            "post-video-editor.play-pause",
            "post-video-editor.timeline",
            "post-video-editor.crop",
            "post-video-editor.captions",
            "post-video-editor.export",
        ] {
            XCTAssertTrue(app.descendants(matching: .any).matching(identifier: identifier).firstMatch.waitForExistence(timeout: 8), "Missing shared video editor anchor \(identifier).")
        }
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-video-editor-opened")
        tapComposerAction("post-video-editor.mute", in: app)
        tapComposerAction("post-video-editor.play-pause", in: app)
        tapComposerAction("post-video-editor.crop", in: app)
        tapComposerAction("post-video-editor.captions", in: app)
        tapComposerAction("post-video-editor.export", in: app)
        XCTAssertTrue(selectedVideoPreview.waitForExistence(timeout: 12), "Saving the iOS video editor must return to the common selected-video preview.")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-video-editor-after-edit")
        print("IOS_POST_VIDEO_EDITOR_UI_GATE_PASSED")
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

    private func openComposer(mode: String, locationLabel: String) -> XCUIApplication {
        let app = XCUIApplication()
        disableQuiescenceWait(for: app)
        app.launchArguments += ["-AppleLanguages", "(es)", "-AppleLocale", "es_ES"]
        app.launchEnvironment["QUATA_IOS_POST_PUBLISH_MODE"] = mode
        app.launchEnvironment["QUATA_IOS_POST_PUBLISH_REAL_MUTATION_OPT_IN"] = Self.realPublishOptIn
        if !locationLabel.isEmpty {
            app.launchEnvironment["QUATA_IOS_POST_PUBLISH_LOCATION_LABEL"] = locationLabel
        }
        for key in [
            "QUATA_IOS_POST_COMPOSER_PICKER_FIXTURE_OPT_IN",
            "QUATA_IOS_POST_COMPOSER_PICKER_SOURCE",
            "QUATA_IOS_POST_COMPOSER_PICKER_OUTCOME",
            "QUATA_IOS_POST_COMPOSER_PICKER_PATH",
            "QUATA_IOS_POST_COMPOSER_PICKER_NAME",
            "QUATA_IOS_POST_COMPOSER_PICKER_MIME",
            "QUATA_IOS_POST_COMPOSER_IMAGE_EDITOR_FIXTURE_OPT_IN",
            "QUATA_IOS_POST_COMPOSER_IMAGE_EDITOR_PATH",
            "QUATA_IOS_POST_COMPOSER_IMAGE_EDITOR_NAME",
            "QUATA_IOS_POST_COMPOSER_IMAGE_EDITOR_MIME",
            "QUATA_IOS_POST_COMPOSER_VIDEO_EDITOR_FIXTURE_OPT_IN",
            "QUATA_IOS_POST_COMPOSER_VIDEO_EDITOR_PATH",
            "QUATA_IOS_POST_COMPOSER_VIDEO_EDITOR_NAME",
            "QUATA_IOS_POST_COMPOSER_VIDEO_EDITOR_MIME",
            "QUATA_IOS_POST_PROGRESS_ROLLBACK_FAIL_ONCE",
            "QUATA_IOS_POST_STORAGE_ROLLBACK_FAIL_AFTER_UPLOAD",
        ] {
            if let value = ProcessInfo.processInfo.environment[key], !value.isEmpty {
                app.launchEnvironment[key] = value
            }
        }
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

    private func disableQuiescenceWait(for app: XCUIApplication) {
        let selector = NSSelectorFromString("setWaitForQuiescence:")
        guard app.responds(to: selector) else {
            return
        }
        _ = app.perform(selector, with: NSNumber(value: false))
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

    private func tapImageType(in app: XCUIApplication) {
        let imageType = app.descendants(matching: .any)
            .matching(identifier: "composer-type-image")
            .firstMatch
        XCTAssertTrue(imageType.waitForExistence(timeout: 10), "The common image composer type must be exposed.")
        if imageType.isHittable {
            imageType.tap()
        } else {
            imageType.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        }
    }

    private func tapVideoType(in app: XCUIApplication) {
        let videoType = app.descendants(matching: .any)
            .matching(identifier: "composer-type-video")
            .firstMatch
        XCTAssertTrue(videoType.waitForExistence(timeout: 10), "The common video composer type must be exposed.")
        if videoType.isHittable {
            videoType.tap()
        } else {
            videoType.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        }
    }

    private func tapComposerAction(_ identifier: String, in app: XCUIApplication) {
        let action = app.descendants(matching: .any)
            .matching(identifier: identifier)
            .firstMatch
        for _ in 0..<8 {
            if action.waitForExistence(timeout: 1), action.isHittable {
                action.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
                return
            }
            app.swipeUp()
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        }
        XCTAssertTrue(action.exists, "Expected common composer action \(identifier) to exist.")
        action.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
    }

    private func assertImageLocationDraft(_ locationLabel: String, in app: XCUIApplication) {
        let location = app.descendants(matching: .any)
            .matching(identifier: "composer-location-value")
            .firstMatch
        for _ in 0..<10 {
            if location.waitForExistence(timeout: 1), elementText(location).contains(locationLabel) {
                return
            }
            app.swipeUp()
            RunLoop.current.run(until: Date().addingTimeInterval(0.3))
        }
        XCTAssertTrue(location.exists, "The common image composer location value must be exposed.")
        XCTAssertTrue(
            elementText(location).contains(locationLabel),
            "Expected image-location draft to expose \(locationLabel); text=\(elementText(location))",
        )
    }

    private func elementText(_ element: XCUIElement) -> String {
        [element.label, element.value as? String].compactMap { $0 }.joined(separator: " ")
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

    private func waitForRetryAndTap(in app: XCUIApplication, screenshotName: String) {
        let error = app.descendants(matching: .any)
            .matching(identifier: "composer-feedback-error")
            .firstMatch
        let retry = app.descendants(matching: .any)
            .matching(identifier: "composer-feedback-retry")
            .firstMatch
        XCTAssertTrue(error.waitForExistence(timeout: 20), "The forced first publish failure must surface shared error feedback.")
        XCTAssertTrue(retry.waitForExistence(timeout: 10), "The forced first publish failure must expose the shared retry action.")
        QuataIosHostUITestSupport.attachRenderedSurface(named: screenshotName)
        if retry.isHittable {
            retry.tap()
        } else {
            retry.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        }
    }

    private func waitForPublishedFeedbackOrClose(in app: XCUIApplication, allowExistingRetryError: Bool = false) {
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
            if success.exists || feed.exists || !composer.exists {
                return
            }
            if error.exists && !allowExistingRetryError {
                QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-publish-error")
                XCTFail("The real iOS composer surfaced shared error feedback after publish.")
                return
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.5))
        }
        if error.exists {
            QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-publish-error")
            XCTFail("The real iOS composer surfaced shared error feedback after publish.")
            return
        }
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-publish-timeout")
        XCTFail("The real iOS composer did not show publish success or return to Feed after publish.")
    }
}
