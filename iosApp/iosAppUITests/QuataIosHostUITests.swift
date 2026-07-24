import XCTest

final class QuataIosHostUITests: XCTestCase {
    func testLaunchesUIKitCompositionRootWithComposeSurface() {
        let app = XCUIApplication()
        app.launch()

        let migrationSurface = app.descendants(matching: .any)["quata-ios-compose-root"]
        XCTAssertTrue(
            migrationSurface.waitForExistence(timeout: 10),
            "The UIKit composition root must present the Compose UIViewController exported by QuataFeed.",
        )
    }
}
