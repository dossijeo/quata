import XCTest

/**
 * Stable assertion boundary for the unauthenticated UIKit/Compose smoke.
 *
 * This deliberately verifies only host containment and accessibility metadata. It does not
 * grant permissions, seed a session, or imply a functional authenticated/E2E journey.
 */
enum QuataIosHostUITestSupport {
    static let composeRootIdentifier = "quata-ios-compose-root"

    static func composeRoot(
        in app: XCUIApplication,
        timeout: TimeInterval = 10,
        context: String = "launch",
        file: StaticString = #filePath,
        line: UInt = #line,
    ) -> XCUIElement {
        let roots = app.descendants(matching: .any).matching(identifier: composeRootIdentifier)
        let root = roots.firstMatch
        XCTAssertTrue(
            root.waitForExistence(timeout: timeout),
            "[\(context)] The UIKit composition root must present exactly one Compose surface with identifier \(composeRootIdentifier).",
            file: file,
            line: line,
        )
        XCTAssertEqual(
            roots.count,
            1,
            "[\(context)] The UIKit composition root must not retain duplicate Compose surfaces.",
            file: file,
            line: line,
        )
        return root
    }
}
