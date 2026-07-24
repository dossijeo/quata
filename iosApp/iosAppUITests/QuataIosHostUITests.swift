import XCTest

final class QuataIosHostUITests: XCTestCase {
    func testLaunchesExportedComposeMigrationSurface() {
        let app = XCUIApplication()
        app.launch()

        let migrationSurface = app.descendants(matching: .any)["quata-ios-migration-status"]
        XCTAssertTrue(
            migrationSurface.waitForExistence(timeout: 10),
            "The Swift host must present the Compose UIViewController exported by QuataFeed.",
        )
    }
}
