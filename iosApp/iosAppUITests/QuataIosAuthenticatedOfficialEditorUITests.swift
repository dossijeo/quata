import XCTest

/// Opt-in, production-host UI gate for the authenticated Official editor.
/// The companion runner seeds the normal app Keychain first; this test launches the real app
/// with no fixture arguments and opens the editor through the shared Official surface.
final class QuataIosAuthenticatedOfficialEditorUITests: XCTestCase {
    func testAuthenticatedSessionOpensRealOfficialEditor() throws {
        guard ProcessInfo.processInfo.environment["QUATA_IOS_AUTH_UI_E2E"] == "1" else {
            throw XCTSkip("Authenticated Official editor UI gate is opt-in.")
        }

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
        XCTAssertTrue(
            app.descendants(matching: .any).matching(identifier: "quata-ios-authenticated-primary-navigation").firstMatch.exists,
            "The shared primary navigation must remain visible on the Official editor.",
        )
        XCTAssertTrue(app.buttons["Editar descripción"].waitForExistence(timeout: 10), "iOS must expose the compact shared body editor action.")
        XCTAssertTrue(app.staticTexts["Vista previa"].waitForExistence(timeout: 10), "The initial editor viewport must expose the shared preview heading.")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "authenticated-official-editor-real-host")
    }
}
