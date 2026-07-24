import XCTest
import QuataFeed

final class QuataFeedFrameworkTests: XCTestCase {
    func testExportsComposeMigrationViewController() {
        let controller = QuataFeedViewControllerKt.QuataIosMigrationStatusViewController()

        XCTAssertNotNil(controller.view)
    }
}
