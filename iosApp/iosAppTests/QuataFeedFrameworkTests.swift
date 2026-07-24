import XCTest
import QuataFeed

final class QuataFeedFrameworkTests: XCTestCase {
    func testExportsMigrationStatusViewController() {
        let controller = QuataFeedViewControllerKt.QuataIosMigrationStatusViewController()

        XCTAssertNotNil(controller.view)
    }
}
