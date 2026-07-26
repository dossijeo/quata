import XCTest

final class QuataIosHostUITests: XCTestCase {
    func testLaunchesUIKitCompositionRootWithComposeSurface() {
        let app = XCUIApplication()
        app.launch()

        let migrationSurface = QuataIosHostUITestSupport.composeRoot(in: app)
        XCTAssertEqual(
            migrationSurface.label,
            "Quata iOS requires an authenticated Feed session",
        )
    }

    func testRelaunchRestoresTheSingleComposeMigrationSurface() {
        let app = XCUIApplication()
        app.launch()

        _ = QuataIosHostUITestSupport.composeRoot(in: app, context: "initial launch")

        app.terminate()
        app.launch()

        _ = QuataIosHostUITestSupport.composeRoot(in: app, context: "cold relaunch")
    }
}
