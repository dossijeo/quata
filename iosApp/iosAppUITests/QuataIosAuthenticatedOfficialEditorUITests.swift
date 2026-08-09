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

        let app = openOfficialEditor()
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

        let titleText = "QADATA iOS \(marker)"
        let summaryText = "Publicacion reversible desde iOS \(marker)"
        switchToAdvancedMode(in: app)
        typeText(titleText, into: "official-editor-advanced-title", in: app)
        typeText(summaryText, into: "official-editor-advanced-summary", in: app)
        if app.keyboards.count > 0 {
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.08)).tap()
        }
        QuataIosHostUITestSupport.attachRenderedSurface(named: "authenticated-official-editor-real-filled")

        tapPublish(in: app)
        tapTranslationSkipIfShown(in: app)
        waitForPublishedPost(in: app, marker: marker)
        QuataIosHostUITestSupport.attachRenderedSurface(named: "authenticated-official-editor-real-after-publish")
    }

    private func openOfficialEditor() -> XCUIApplication {
        let app = XCUIApplication()
        app.launch()

        let feed = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-feed-host")
            .firstMatch
        XCTAssertTrue(feed.waitForExistence(timeout: 20), "A normal launch must restore Feed from the seeded Keychain session.")

        let officialTab = app.buttons["Oficial, Oficial"]
        XCTAssertTrue(officialTab.waitForExistence(timeout: 15), "The shared primary navigation must expose Oficial.")
        officialTab.tap()

        let official = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-official-host")
            .firstMatch
        XCTAssertTrue(official.waitForExistence(timeout: 20), "The real Official host must open from the shared primary navigation.")

        let createNotice = app.buttons.matching(
            NSPredicate(format: "label CONTAINS[c] %@ OR identifier CONTAINS[c] %@", "Crear comunicado", "Crear comunicado")
        ).firstMatch
        XCTAssertTrue(createNotice.waitForExistence(timeout: 10), "The Official surface must expose Crear comunicado.")
        createNotice.tap()

        let editor = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-official-editor-host")
            .firstMatch
        XCTAssertTrue(editor.waitForExistence(timeout: 20), "Crear comunicado must mount the real Official editor host.")
        return app
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
        let richTextField = app.descendants(matching: .any)
            .matching(identifier: "quata-portable-rich-text-field")
            .firstMatch
        XCTAssertTrue(richTextField.waitForExistence(timeout: 10), "iOS must mount the common portable rich-text field.")
        let preview = app.descendants(matching: .any)
            .matching(identifier: "official-editor-preview")
            .firstMatch
        let previewHeading = app.staticTexts["Vista previa"]
        XCTAssertTrue(
            preview.waitForExistence(timeout: 5) || previewHeading.waitForExistence(timeout: 10),
            "The initial editor viewport must expose the shared preview region.",
        )
    }

    private func tapPublish(in app: XCUIApplication) {
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
        XCTAssertTrue(modeSwitch.waitForExistence(timeout: 10), "The common Official editor mode switch must exist.")
        for _ in 0..<8 {
            if modeSwitch.isHittable {
                break
            }
            app.swipeDown()
            RunLoop.current.run(until: Date().addingTimeInterval(0.3))
        }
        XCTAssertTrue(modeSwitch.isHittable, "The common Official editor mode switch must be reachable.")
        if modeSwitch.value as? String != "1" {
            modeSwitch.tap()
        }
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
            if publishedPost.exists {
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
