import XCTest

/// Opt-in, production-host UI gate for the authenticated common post composer.
/// The companion runner seeds the normal app Keychain first, then opens the real composer route.
final class QuataIosAuthenticatedPostPublishUITests: XCTestCase {
    private static let realPublishOptIn = "I_ACCEPT_REVERSIBLE_POST_PUBLISH_MUTATION"
    private enum ReplayError: Error {
        case captionStyleSelectionFailed(String)
    }

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
        let mediaType = environment["QUATA_IOS_POST_COMPOSER_PICKER_MEDIA_TYPE"] ?? "image"
        XCTAssertTrue(["gallery", "camera"].contains(source), "Unsupported picker source \(source).")
        XCTAssertTrue(["success", "cancelled", "failure", "unsupported", "permission-denied"].contains(outcome), "Unsupported picker outcome \(outcome).")
        XCTAssertTrue(["image", "video"].contains(mediaType), "Unsupported picker media type \(mediaType).")

        let app = openComposer(mode: mediaType, locationLabel: "")
        assertSharedComposerSurface(in: app)
        if mediaType == "video" {
            tapVideoType(in: app)
        } else {
            tapImageType(in: app)
        }
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-picker-camera-\(mediaType)-form-\(source)-\(outcome)")

        let actionIdentifier = source == "gallery"
            ? "composer-media.pick-\(mediaType)"
            : "composer-media.capture-\(mediaType)"
        tapComposerAction(actionIdentifier, in: app)
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-picker-camera-\(mediaType)-after-tap-\(source)-\(outcome)")

        let selectedPreview = app.descendants(matching: .any)
            .matching(identifier: "composer-media.selected-\(mediaType)-preview")
            .firstMatch
        if outcome == "success" {
            XCTAssertTrue(selectedPreview.waitForExistence(timeout: 12), "A successful \(source) picker replay must select \(mediaType) in common composer state.")
        } else {
            XCTAssertFalse(selectedPreview.waitForExistence(timeout: 2), "A non-success \(source) picker replay must not select \(mediaType).")
            let mediaError = app.descendants(matching: .any)
                .matching(identifier: "composer-media.error")
                .firstMatch
            if outcome == "failure" || outcome == "unsupported" || outcome == "permission-denied" {
                XCTAssertTrue(mediaError.waitForExistence(timeout: 8), "A \(outcome) picker replay must expose the shared media error anchor.")
                if outcome == "permission-denied" {
                    XCTAssertTrue(
                        permissionDeniedMediaCopyExists(in: app),
                        "Permission denied replay must expose the shared media permission copy."
                    )
                }
            } else {
                XCTAssertFalse(mediaError.waitForExistence(timeout: 2), "A cancelled picker replay must stay silent and not expose a media error.")
            }
        }
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-picker-camera-\(mediaType)-after-action-\(source)-\(outcome)")
        print("IOS_POST_PICKER_CAMERA_UI_GATE_PASSED \(mediaType) \(source) \(outcome)")
    }

    private func permissionDeniedMediaCopyExists(in app: XCUIApplication) -> Bool {
        let copyPredicates = [
            NSPredicate(format: "label CONTAINS[c] %@", "Permiso denegado"),
            NSPredicate(format: "label CONTAINS[c] %@", "Permission denied"),
            NSPredicate(format: "label CONTAINS[c] %@", "Autorisation refusée"),
        ]
        return copyPredicates.contains { predicate in
            app.descendants(matching: .any).matching(predicate).firstMatch.waitForExistence(timeout: 2)
        }
    }

    func testAuthenticatedSessionExercisesDestinationStatesFromCommonComposer() throws {
        let environment = ProcessInfo.processInfo.environment
        guard environment["QUATA_IOS_POST_DESTINATION_UI_E2E"] == "1" else {
            throw XCTSkip("Authenticated post destination UI gate is opt-in.")
        }
        let mode = environment["QUATA_IOS_POST_DESTINATION_E2E_MODE"] ?? ""
        XCTAssertTrue(["empty", "failure", "multiple"].contains(mode), "Unsupported destination evidence mode \(mode).")

        let app = openComposer(mode: "text", locationLabel: "")
        assertSharedComposerSurface(in: app)
        tapTextType(in: app)
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-destination-composer-opened-\(mode)")

        switch mode {
        case "failure":
            XCTAssertTrue(commonElement("composer-destination-error", in: app).waitForExistence(timeout: 12), "Destination load failure must expose the shared error anchor.")
            XCTAssertTrue(commonElement("composer-destination-retry", in: app).waitForExistence(timeout: 6), "Destination load failure must expose the shared retry anchor.")
            QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-destination-error")
            assertPublishBlockedByDestinationRequired(in: app)
        case "empty":
            XCTAssertTrue(commonElement("composer-destination-empty", in: app).waitForExistence(timeout: 12), "Empty destinations must expose the shared empty anchor.")
            XCTAssertTrue(commonElement("composer-destination-retry", in: app).waitForExistence(timeout: 6), "Empty destinations must expose the shared retry anchor.")
            QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-destination-empty")
            assertPublishBlockedByDestinationRequired(in: app)
        default:
            selectDestination("e2e-wall-bata", in: app)
            let selected = commonElement("composer-destination-selected", in: app)
            XCTAssertTrue(selected.waitForExistence(timeout: 8), "Selected destination label must remain anchored.")
            XCTAssertTrue(elementText(selected).contains("Bata"), "Selecting the alternate destination must update common selected label.")
            QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-destination-multiple-selected")
        }
        print("IOS_POST_DESTINATION_UI_GATE_PASSED \(mode)")
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
            "post-video-editor.reset",
            "post-video-editor.export",
        ] {
            XCTAssertTrue(app.descendants(matching: .any).matching(identifier: identifier).firstMatch.waitForExistence(timeout: 8), "Missing shared video editor anchor \(identifier).")
        }
        for index in 0..<6 {
            let frame = app.descendants(matching: .any)
                .matching(identifier: "post-video-editor.timeline-frame.\(index)")
                .firstMatch
            XCTAssertTrue(frame.waitForExistence(timeout: 12), "Missing shared video editor timeline frame \(index).")
        }
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-video-editor-opened")
        let editorPreview = app.descendants(matching: .any)
            .matching(identifier: "post-video-editor.preview")
            .firstMatch
        QuataIosHostUITestSupport.assertElementHasNonBlackPixels(
            editorPreview,
            named: "ios-post-video-editor-preview-opened",
            minimumNonBlackRatio: 0.08,
        )
        tapComposerAction("post-video-editor.reset", in: app)
        if ProcessInfo.processInfo.environment["QUATA_IOS_POST_VIDEO_EDITOR_MUTE"] == "1" {
            tapComposerAction("post-video-editor.mute", in: app)
        }
        tapComposerAction("post-video-editor.play-pause", in: app)
        dragVideoTrimEnd(toNormalizedX: 0.64, in: app)
        tapComposerAction("post-video-editor.captions", in: app)
        try tapVideoCaptionStyle("Hormozi", in: app)
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "post-video-editor.caption-preview.Hormozi")
                .firstMatch
                .waitForExistence(timeout: 8),
            "Selecting a caption style must render the common caption preview overlay before export.",
        )
        tapComposerAction("post-video-editor.crop", in: app)
        tapComposerAction("post-video-editor.crop-mode.Square", in: app)
        QuataIosHostUITestSupport.assertElementHasNonBlackPixels(
            editorPreview,
            named: "ios-post-video-editor-preview-after-crop-captions",
            minimumNonBlackRatio: 0.08,
        )
        exerciseVideoExportCancellationIfRequested(in: app, editorRoot: editorRoot)
        if ProcessInfo.processInfo.environment["QUATA_IOS_POST_VIDEO_EDITOR_CANCEL_ONLY"] == "1" {
            print("IOS_POST_VIDEO_EDITOR_UI_GATE_PASSED")
            return
        }
        tapComposerAction("post-video-editor.export", in: app)
        let editorExport = app.descendants(matching: .any)
            .matching(identifier: "post-video-editor.export")
            .firstMatch
        let exportProgress = app.descendants(matching: .any)
            .matching(identifier: "post-video-editor.export-progress")
            .firstMatch
        XCTAssertTrue(exportProgress.waitForExistence(timeout: 8), "Tapping the shared video editor export action must enter the exporting state.")
        let editorError = app.descendants(matching: .any)
            .matching(identifier: "post-video-editor.error")
            .firstMatch
        let exportDeadline = Date().addingTimeInterval(180)
        var returnedToComposer = false
        while Date() < exportDeadline {
            let editorStillVisible = editorRoot.exists || editorPreview.exists || editorExport.exists
            if selectedVideoPreview.exists && !editorStillVisible {
                returnedToComposer = true
                break
            }
            if editorError.exists {
                XCTFail("Saving the iOS video editor surfaced the common editor error: \(elementText(editorError))")
                break
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.5))
        }
        XCTAssertTrue(returnedToComposer, "Saving the iOS video editor must return to the common selected-video preview after the native export finishes.")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-video-editor-after-edit")
        print("IOS_POST_VIDEO_EDITOR_UI_GATE_PASSED")
    }

    private func exerciseVideoExportCancellationIfRequested(in app: XCUIApplication, editorRoot: XCUIElement) {
        guard ProcessInfo.processInfo.environment["QUATA_IOS_POST_VIDEO_EDITOR_EXERCISE_CANCEL"] == "1" else {
            return
        }
        tapComposerAction("post-video-editor.export", in: app)
        let exportProgress = commonElement("post-video-editor.export-progress", in: app)
        XCTAssertTrue(exportProgress.waitForExistence(timeout: 8), "The shared editor must expose export progress before cancellation.")
        tapComposerAction("post-video-editor.cancel-export", in: app)
        let editorError = commonElement("post-video-editor.error", in: app)
        let exportButton = commonElement("post-video-editor.export", in: app)
        XCTAssertTrue(exportButton.waitForExistence(timeout: 8), "Cancelling export must return to the editable video editor state.")
        XCTAssertTrue(editorRoot.exists, "Cancelling export must keep the shared video editor open.")
        XCTAssertFalse(editorError.exists, "Cancelling export must not surface a user-visible error: \(elementText(editorError))")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-video-editor-after-cancel-export")
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
            "QUATA_IOS_POST_VIDEO_EDITOR_EXPORT_DIAGNOSTICS",
            "QUATA_IOS_POST_VIDEO_EDITOR_TRANSCRIPTION_LOCALE",
            "QUATA_IOS_POST_VIDEO_EDITOR_MUTE",
            "QUATA_IOS_POST_PROGRESS_ROLLBACK_FAIL_ONCE",
            "QUATA_IOS_POST_STORAGE_ROLLBACK_FAIL_AFTER_UPLOAD",
            "QUATA_IOS_POST_DESTINATION_E2E_MODE",
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
        let buttonAction = app.buttons.matching(identifier: identifier).firstMatch
        let fallbackAction = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        for _ in 0..<8 {
            let action = buttonAction.waitForExistence(timeout: 1) ? buttonAction : fallbackAction
            if action.waitForExistence(timeout: 1), action.isHittable {
                action.tap()
                return
            }
            app.swipeUp()
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        }
        let action = buttonAction.exists ? buttonAction : fallbackAction
        XCTAssertTrue(action.exists, "Expected common composer action \(identifier) to exist.")
        action.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).press(forDuration: 0.15)
    }

    private func tapVideoCaptionStyle(_ style: String, in app: XCUIApplication) throws {
        let selectedIdentifier = "post-video-editor.caption-style-selected.\(style)"
        let buttonIdentifier = "post-video-editor.caption-style.\(style)"
        var target: XCUIElement?
        for _ in 0..<8 {
            let button = app.buttons.matching(identifier: buttonIdentifier).firstMatch
            let semanticTarget = app.descendants(matching: .any).matching(identifier: buttonIdentifier).firstMatch
            if button.waitForExistence(timeout: 1) {
                target = button
                break
            }
            if semanticTarget.waitForExistence(timeout: 1) {
                target = semanticTarget
                break
            }
            scrollVideoEditorControlsDown(in: app)
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        }
        guard let target = target else {
            XCTFail("Expected common video caption style \(style) to exist.")
            throw ReplayError.captionStyleSelectionFailed(style)
        }
        for _ in 0..<4 {
            if !target.isHittable {
                scrollVideoEditorControlsDown(in: app)
                RunLoop.current.run(until: Date().addingTimeInterval(0.25))
                continue
            }
            target.tap()
            if app.descendants(matching: .any).matching(identifier: selectedIdentifier).firstMatch.waitForExistence(timeout: 1) {
                return
            }
            target.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
            if app.descendants(matching: .any).matching(identifier: selectedIdentifier).firstMatch.waitForExistence(timeout: 1) {
                return
            }
            let visibleText = app.staticTexts.matching(identifier: style).firstMatch
            if visibleText.exists {
                visibleText.tap()
            }
            if app.descendants(matching: .any).matching(identifier: selectedIdentifier).firstMatch.waitForExistence(timeout: 1) {
                return
            }
        }
        XCTFail("Selecting common video caption style \(style) did not update the shared editor state.")
        throw ReplayError.captionStyleSelectionFailed(style)
    }

    private func scrollVideoEditorControlsDown(in app: XCUIApplication) {
        let editorRoot = app.descendants(matching: .any)
            .matching(identifier: "post-video-editor.root")
            .firstMatch
        if editorRoot.exists {
            let start = editorRoot.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.78))
            let end = editorRoot.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.32))
            start.press(forDuration: 0.05, thenDragTo: end)
            return
        }
        let editorPreview = app.descendants(matching: .any)
            .matching(identifier: "post-video-editor.preview")
            .firstMatch
        if editorPreview.exists {
            let start = editorPreview.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.86))
            let end = editorPreview.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.20))
            start.press(forDuration: 0.05, thenDragTo: end)
        }
    }

    private func dragVideoTrimEnd(toNormalizedX targetX: CGFloat, in app: XCUIApplication) {
        let timeline = app.descendants(matching: .any)
            .matching(identifier: "post-video-editor.timeline")
            .firstMatch
        let handle = app.descendants(matching: .any)
            .matching(identifier: "post-video-editor.trim-end")
            .firstMatch
        XCTAssertTrue(timeline.waitForExistence(timeout: 8), "The shared video editor timeline must be exposed before trimming.")
        XCTAssertTrue(handle.waitForExistence(timeout: 8), "The shared video editor trim-end handle must be exposed before trimming.")
        let destination = timeline.coordinate(withNormalizedOffset: CGVector(dx: min(max(targetX, 0.52), 0.95), dy: 0.5))
        handle.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
            .press(forDuration: 0.18, thenDragTo: destination)
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-video-editor-trimmed")
    }

    private func commonElement(_ identifier: String, in app: XCUIApplication) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: identifier).firstMatch
    }

    private func assertPublishBlockedByDestinationRequired(in app: XCUIApplication) {
        tapPublish(in: app)
        XCTAssertTrue(commonElement("composer-feedback-error", in: app).waitForExistence(timeout: 8), "Publishing without a loaded destination must expose the common feedback error.")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-post-destination-publish-blocked")
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
