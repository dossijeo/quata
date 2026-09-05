import XCTest

/// Opt-in, production-host UI gate for the authenticated Official editor.
/// The companion runner seeds the normal app Keychain first; this test launches the real app
/// with no fixture arguments and opens the editor through the shared Official surface.
final class QuataIosAuthenticatedOfficialEditorUITests: XCTestCase {
    private static let realPublishOptIn = "I_ACCEPT_REVERSIBLE_OFFICIAL_POST_MUTATION"

    func testAuthenticatedSessionOpensRealOfficialEditor() throws {
        guard ProcessInfo.processInfo.environment["QUATA_IOS_AUTH_UI_E2E"] == "1" else {
            throw XCTSkip("Authenticated Official editor UI gate is opt-in.")
        }

        let app = openOfficialEditor()
        assertSharedEditorSurface(in: app)
        QuataIosHostUITestSupport.attachRenderedSurface(named: "authenticated-official-editor-real-host")
    }

    func testAuthenticatedSessionCannotOpenOfficialEditorWhenIneligible() throws {
        guard ProcessInfo.processInfo.environment["QUATA_IOS_AUTH_UI_E2E"] == "1" else {
            throw XCTSkip("Authenticated Official editor UI gate is opt-in.")
        }
        guard ProcessInfo.processInfo.environment["QUATA_IOS_OFFICIAL_EDITOR_EXPECT_INELIGIBLE"] == "1" else {
            throw XCTSkip("Official editor permission denial evidence is opt-in.")
        }

        let app = openOfficialSurface()
        let createNotice = officialCreateNotice(in: app)
        XCTAssertFalse(
            createNotice.waitForExistence(timeout: 8),
            "A non-official authenticated iOS session must not expose Crear comunicado.",
        )
        let editor = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-official-editor-host")
            .firstMatch
        XCTAssertFalse(
            editor.waitForExistence(timeout: 2),
            "A non-official authenticated iOS session must not mount the Official editor host.",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "authenticated-official-editor-ineligible-blocked")
    }

    func testAuthenticatedSessionPublishesRealOfficialPost() throws {
        let environment = ProcessInfo.processInfo.environment
        guard environment["QUATA_IOS_AUTH_UI_E2E"] == "1" else {
            throw XCTSkip("Authenticated Official editor UI gate is opt-in.")
        }
        guard environment["QUATA_IOS_OFFICIAL_EDITOR_REAL_PUBLISH_OPT_IN"] == Self.realPublishOptIn else {
            throw XCTSkip("Real Official editor publish is opt-in because it mutates authorized backend data.")
        }
        guard let marker = environment["QUATA_IOS_OFFICIAL_EDITOR_MARKER"], !marker.isEmpty else {
            throw XCTSkip("Real Official editor publish requires QUATA_IOS_OFFICIAL_EDITOR_MARKER.")
        }

        var app = openOfficialEditor()
        assertSharedEditorSurface(in: app)

        tapPublish(in: app)
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "official-editor-feedback")
                .firstMatch
                .waitForExistence(timeout: 10),
            "An empty iOS Official editor publish must surface the shared validation feedback before any mutation.",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "authenticated-official-editor-real-validation")

        app.terminate()
        app = openOfficialEditor()
        assertSharedEditorSurface(in: app)

        let titleText = "QADATA iOS \(marker)"
        let summaryText = "Publicacion reversible desde iOS \(marker)"
        typeRichTextBody("Contenido reversible iOS \(marker)", in: app)
        switchToAdvancedMode(in: app)
        typeText(titleText, into: "official-editor-advanced-title", in: app)
        typeText(summaryText, into: "official-editor-advanced-summary", in: app)
        try selectMediaIfRequested(in: app)
        dismissKeyboardIfPresent(in: app)
        QuataIosHostUITestSupport.attachRenderedSurface(named: "authenticated-official-editor-real-filled")

        tapPublish(in: app)
        tapTranslationSkipIfShown(in: app)
        waitForPublishedPost(in: app, marker: marker)
        QuataIosHostUITestSupport.attachRenderedSurface(named: "authenticated-official-editor-real-after-publish")
    }

    private func openOfficialEditor() -> XCUIApplication {
        let app = openOfficialSurface()
        let createNotice = officialCreateNotice(in: app)
        XCTAssertTrue(createNotice.waitForExistence(timeout: 10), "The Official surface must expose Crear comunicado.")
        createNotice.tap()

        let editor = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-official-editor-host")
            .firstMatch
        XCTAssertTrue(editor.waitForExistence(timeout: 20), "Crear comunicado must mount the real Official editor host.")
        return app
    }

    private func openOfficialSurface() -> XCUIApplication {
        let app = XCUIApplication()
        let environment = ProcessInfo.processInfo.environment
        for key in [
            "QUATA_IOS_OFFICIAL_EDITOR_MEDIA_FIXTURE_OPT_IN",
            "QUATA_IOS_OFFICIAL_EDITOR_MEDIA_FIXTURE_TYPE",
            "QUATA_IOS_OFFICIAL_EDITOR_MEDIA_FIXTURE_PATH",
        ] {
            if let value = environment[key] {
                app.launchEnvironment[key] = value
            }
        }
        app.launch()

        let feed = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-feed-host")
            .firstMatch
        XCTAssertTrue(feed.waitForExistence(timeout: 20), "A normal launch must restore Feed from the seeded Keychain session.")

        let officialTab = app.buttons["navigation.primary.official"]
        XCTAssertTrue(officialTab.waitForExistence(timeout: 15), "The shared primary navigation must expose Oficial.")
        officialTab.tap()

        let official = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-official-host")
            .firstMatch
        XCTAssertTrue(official.waitForExistence(timeout: 20), "The real Official host must open from the shared primary navigation.")
        return app
    }

    private func officialCreateNotice(in app: XCUIApplication) -> XCUIElement {
        app.buttons.matching(
            NSPredicate(format: "label CONTAINS[c] %@ OR identifier CONTAINS[c] %@", "Crear comunicado", "Crear comunicado")
        ).firstMatch
    }

    private func assertSharedEditorSurface(in app: XCUIApplication) {
        XCTAssertTrue(
            app.descendants(matching: .any).matching(identifier: "quata-ios-authenticated-primary-navigation").firstMatch.exists,
            "The shared primary navigation must remain visible on the Official editor.",
        )
        let bodyAction = app.descendants(matching: .any)
            .matching(identifier: "official-editor-body-action")
            .firstMatch
        XCTAssertTrue(bodyAction.waitForExistence(timeout: 10), "iOS must expose the shared Official editor body slot.")
        bodyAction.tap()
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "official-editor-long-body")
                .firstMatch
                .waitForExistence(timeout: 10),
            "iOS must open the shared full-screen rich-text editor shell.",
        )
        let richTextField = app.descendants(matching: .any)
            .matching(identifier: "quata-portable-rich-text-field")
            .firstMatch
        XCTAssertTrue(richTextField.waitForExistence(timeout: 10), "iOS must mount the common portable rich-text field.")
        let save = app.descendants(matching: .any)
            .matching(identifier: "official-editor-long-save")
            .firstMatch
        XCTAssertTrue(save.waitForExistence(timeout: 5), "The shared long editor must expose a semantic save action.")
        save.tap()
        let preview = app.descendants(matching: .any)
            .matching(identifier: "official-editor-preview")
            .firstMatch
        let previewHeading = app.staticTexts["Vista previa"]
        XCTAssertTrue(
            preview.waitForExistence(timeout: 5) || previewHeading.waitForExistence(timeout: 10),
            "The initial editor viewport must expose the shared preview region.",
        )
    }

    private func selectMediaIfRequested(in app: XCUIApplication) throws {
        let mediaType = ProcessInfo.processInfo.environment["QUATA_IOS_OFFICIAL_EDITOR_MEDIA_FIXTURE_TYPE"]
        guard mediaType == "image" || mediaType == "video" else {
            return
        }
        dismissKeyboardIfPresent(in: app)
        let pickerIdentifier = mediaType == "video" ? "official-editor-pick-video" : "official-editor-pick-image"
        let attachmentName = mediaType == "video" ? "authenticated-official-editor-real-video-preview" : "authenticated-official-editor-real-image-preview"
        let picker = app.descendants(matching: .any)
            .matching(identifier: pickerIdentifier)
            .firstMatch
        let mediaPreview = app.descendants(matching: .any)
            .matching(identifier: "official-editor-media-preview")
            .firstMatch
        var tappedPicker = false
        for attempt in 0..<10 {
            if picker.waitForExistence(timeout: 1), picker.isHittable {
                picker.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
                tappedPicker = true
                if mediaPreview.waitForExistence(timeout: 4) {
                    QuataIosHostUITestSupport.attachRenderedSurface(named: attachmentName)
                    return
                }
            }
            if attempt < 5 {
                app.swipeDown()
            } else {
                app.swipeUp()
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.4))
        }
        guard tappedPicker else {
            throw OfficialEditorMediaEvidenceError.pickerNotTapped
        }
        guard mediaPreview.waitForExistence(timeout: 10) else {
            throw OfficialEditorMediaEvidenceError.previewMissing
        }
        QuataIosHostUITestSupport.attachRenderedSurface(named: attachmentName)
    }

    private func dismissKeyboardIfPresent(in app: XCUIApplication) {
        guard app.keyboards.count > 0 else {
            return
        }
        let returnLabels = ["return", "Return", "Intro", "Retorno", "Done", "Hecho"]
        for label in returnLabels {
            let key = app.keyboards.buttons[label].firstMatch
            if key.exists {
                key.tap()
                RunLoop.current.run(until: Date().addingTimeInterval(0.3))
                if app.keyboards.count == 0 {
                    return
                }
            }
        }
        let focused = app.descendants(matching: .any)
            .matching(NSPredicate(format: "hasKeyboardFocus == 1"))
            .firstMatch
        if focused.exists {
            focused.typeText("\n")
            RunLoop.current.run(until: Date().addingTimeInterval(0.3))
            if app.keyboards.count == 0 {
                return
            }
        }
        for _ in 0..<4 {
            app.swipeDown()
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.06)).tap()
            RunLoop.current.run(until: Date().addingTimeInterval(0.4))
            if app.keyboards.count == 0 {
                return
            }
        }
    }

    private func tapPublish(in app: XCUIApplication) {
        dismissKeyboardIfPresent(in: app)
        let publish = app.descendants(matching: .any)
            .matching(identifier: "official-editor-publish")
            .firstMatch
        for _ in 0..<8 {
            if publish.waitForExistence(timeout: 2), publish.isHittable {
                publish.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
                return
            }
            app.swipeUp()
            RunLoop.current.run(until: Date().addingTimeInterval(0.3))
        }
        XCTAssertTrue(publish.exists, "Expected the shared Official editor publish action to exist.")
        publish.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
    }

    private func switchToAdvancedMode(in app: XCUIApplication) {
        let modeSwitch = app.descendants(matching: .any)
            .matching(identifier: "official-editor-mode-switch")
            .firstMatch
        let advancedTitle = app.descendants(matching: .any)
            .matching(identifier: "official-editor-advanced-title")
            .firstMatch
        XCTAssertTrue(modeSwitch.waitForExistence(timeout: 10), "The common Official editor mode switch must exist.")
        for _ in 0..<8 {
            if modeSwitch.isHittable {
                break
            }
            app.swipeDown()
            RunLoop.current.run(until: Date().addingTimeInterval(0.3))
        }
        XCTAssertTrue(modeSwitch.isHittable, "The common Official editor mode switch must be reachable.")
        for _ in 0..<3 {
            if advancedTitle.waitForExistence(timeout: 1) {
                return
            }
            modeSwitch.tap()
            if advancedTitle.waitForExistence(timeout: 3) {
                return
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.3))
        }
        XCTAssertTrue(advancedTitle.exists, "The common Official editor advanced fields must appear after enabling advanced mode.")
    }

    private func typeText(_ value: String, into identifier: String, in app: XCUIApplication) {
        let field = app.descendants(matching: .any)
            .matching(identifier: identifier)
            .firstMatch
        for attempt in 0..<12 {
            if field.waitForExistence(timeout: 1), field.isHittable {
                field.tap()
                if app.keyboards.count > 0 {
                    typeIntoFocusedElement(value, fallback: field, in: app)
                    return
                }
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

    private func typeRichTextBody(_ value: String, in app: XCUIApplication) {
        dismissKeyboardIfPresent(in: app)
        let bodyAction = app.descendants(matching: .any)
            .matching(identifier: "official-editor-body-action")
            .firstMatch
        XCTAssertTrue(bodyAction.waitForExistence(timeout: 10), "Expected shared body action before rich-text edit.")
        var tappedBodyAction = false
        for attempt in 0..<12 {
            if bodyAction.isHittable {
                bodyAction.tap()
                tappedBodyAction = true
                break
            }
            if attempt < 8 {
                app.swipeDown()
            } else {
                app.swipeUp()
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.3))
        }
        XCTAssertTrue(tappedBodyAction, "Expected shared body action to be reachable before rich-text edit.")
        let richTextField = app.descendants(matching: .any)
            .matching(identifier: "quata-portable-rich-text-field")
            .firstMatch
        XCTAssertTrue(richTextField.waitForExistence(timeout: 10), "Expected common portable rich-text field.")
        richTextField.tap()
        typeIntoFocusedElement(value, fallback: richTextField, in: app)
        dismissKeyboardIfPresent(in: app)
        let save = app.descendants(matching: .any)
            .matching(identifier: "official-editor-long-save")
            .firstMatch
        XCTAssertTrue(save.waitForExistence(timeout: 5), "Expected shared long-editor save action.")
        save.tap()
    }

    private func typeIntoFocusedElement(_ value: String, fallback: XCUIElement, in app: XCUIApplication) {
        if app.keyboards.count > 0 {
            app.typeText(value)
            return
        }
        let focused = app.descendants(matching: .any)
            .matching(NSPredicate(format: "hasKeyboardFocus == 1"))
            .firstMatch
        if focused.waitForExistence(timeout: 2) {
            focused.typeText(value)
        } else {
            fallback.typeText(value)
        }
    }

    private func tapTranslationSkipIfShown(in app: XCUIApplication) {
        let skipByTag = app.descendants(matching: .any)
            .matching(identifier: "official-editor-translation-skip")
            .firstMatch
        if skipByTag.waitForExistence(timeout: 12) {
            skipByTag.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
            return
        }
        let skipPredicates = [
            "Publicar solo este idioma",
            "Publicar así",
            "Publish only this language",
            "Publish as is",
            "Publier seulement cette langue",
            "Publier ainsi",
        ]
        let deadline = Date().addingTimeInterval(12)
        while Date() < deadline {
            for label in skipPredicates {
                let button = app.buttons.matching(NSPredicate(format: "label CONTAINS[c] %@", label)).firstMatch
                if button.exists {
                    button.tap()
                    return
                }
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.3))
        }
    }

    private func waitForPublishedPost(in app: XCUIApplication, marker: String) {
        let suffix = String(marker.suffix(8))
        let postPredicate = NSPredicate(
            format: "label CONTAINS[c] %@ OR label CONTAINS[c] %@ OR identifier CONTAINS[c] %@ OR identifier CONTAINS[c] %@",
            marker,
            suffix,
            marker,
            suffix
        )
        let editor = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-official-editor-host")
            .firstMatch
        let official = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-official-host")
            .firstMatch
        let publishedPost = app.descendants(matching: .any)
            .matching(postPredicate)
            .firstMatch
        let deadline = Date().addingTimeInterval(90)
        while Date() < deadline {
            if official.exists && !editor.exists && publishedPost.exists {
                return
            }
            if !official.exists && editor.exists == false {
                let officialTab = app.buttons["Oficial, Oficial"]
                if officialTab.exists {
                    officialTab.tap()
                }
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.75))
        }
        QuataIosHostUITestSupport.attachRenderedSurface(named: "authenticated-official-editor-real-publish-missing")
        XCTFail("The real Official editor did not show the reversible post marker after publish.")
    }
}

private enum OfficialEditorMediaEvidenceError: Error {
    case pickerNotTapped
    case previewMissing
}
