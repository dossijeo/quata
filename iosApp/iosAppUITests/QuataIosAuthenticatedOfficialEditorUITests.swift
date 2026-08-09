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

        let bodyText = "QADATA official iOS evidence \(marker) Publicacion reversible desde iOS."
        let bodyField = app.descendants(matching: .any)
            .matching(identifier: "quata-portable-rich-text-field")
            .firstMatch
        XCTAssertTrue(bodyField.waitForExistence(timeout: 10), "The common portable rich-text field must be editable.")
        focusRichTextField(bodyField, in: app)
        bodyField.typeText(bodyText)
        if app.keyboards.count > 0 {
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.08)).tap()
        }
        QuataIosHostUITestSupport.attachRenderedSurface(named: "authenticated-official-editor-real-filled")

        tapPublish(in: app)
        tapTranslationSkipIfShown(in: app)
        QuataIosHostUITestSupport.attachRenderedSurface(named: "authenticated-official-editor-real-after-publish")

        let editor = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-official-editor-host")
            .firstMatch
        let official = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-official-host")
            .firstMatch
        let deadline = Date().addingTimeInterval(90)
        while Date() < deadline {
            if !editor.exists || official.exists {
                return
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.5))
        }
        XCTFail("The real Official editor did not close or return to Official after publish.")
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

    private func focusRichTextField(_ field: XCUIElement, in app: XCUIApplication) {
        for _ in 0..<4 {
            if field.exists, field.frame.midY > 120, field.frame.midY < app.frame.maxY - 160 {
                break
            }
            app.swipeDown()
            RunLoop.current.run(until: Date().addingTimeInterval(0.3))
        }
        if field.isHittable {
            field.tap()
        } else {
            let frame = field.frame
            let x = min(max(frame.midX, app.frame.minX + 40), app.frame.maxX - 40)
            let y = min(max(frame.midY, app.frame.minY + 150), app.frame.maxY - 220)
            app.coordinate(withNormalizedOffset: CGVector(dx: 0, dy: 0))
                .withOffset(CGVector(dx: x, dy: y))
                .tap()
        }
        RunLoop.current.run(until: Date().addingTimeInterval(0.5))
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
}
