import XCTest

final class QuataIosHostUITests: XCTestCase {
    func testLaunchesUIKitCompositionRootWithComposeSurface() {
        let app = XCUIApplication()
        app.launch()

        let migrationSurfaces = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-compose-root")
        let migrationSurface = migrationSurfaces.firstMatch
        XCTAssertTrue(
            migrationSurface.waitForExistence(timeout: 10),
            "The UIKit composition root must present the Compose UIViewController exported by QuataShared.",
        )
        XCTAssertEqual(migrationSurfaces.count, 1)
        XCTAssertEqual(
            migrationSurface.label,
            "Quata iOS requires an authenticated Feed session",
        )
    }

    func testRelaunchRestoresTheSingleComposeMigrationSurface() {
        let app = XCUIApplication()
        app.launch()

        let firstSurface = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-compose-root")
            .firstMatch
        XCTAssertTrue(firstSurface.waitForExistence(timeout: 10))

        app.terminate()
        app.launch()

        let relaunchedSurfaces = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-compose-root")
        let relaunchedSurface = relaunchedSurfaces.firstMatch
        XCTAssertTrue(
            relaunchedSurface.waitForExistence(timeout: 10),
            "AppDelegate must recreate the UIKit composition root after a cold relaunch.",
        )
        XCTAssertEqual(relaunchedSurfaces.count, 1)
    }
}
