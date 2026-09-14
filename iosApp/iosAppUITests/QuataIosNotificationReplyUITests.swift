import Foundation
import XCTest

/// The host runner seeds an authorized session and owns the disposable conversation and cleanup.
/// UI submission alone is not a delivery verdict: the runner must verify the exact backend message.
final class QuataIosNotificationReplyUITests: XCTestCase {
    func testReplyThroughTheSystemNotification() throws {
        try performReplyUI(affordanceOnly: false)
    }

    /// Gesture pilot only: no session seeding, text entry, Reply tap or send.
    func testInspectReplyAffordanceWithoutSending() throws {
        try performReplyUI(affordanceOnly: true)
    }

    private func performReplyUI(affordanceOnly: Bool) throws {
        let environment = ProcessInfo.processInfo.environment
        let realTrial = environment["QUATA_IOS_NOTIFICATION_REPLY_UI_E2E"] == "1"
        let pilot = environment["QUATA_IOS_NOTIFICATION_REPLY_UI_PILOT"] == "1"
        guard (affordanceOnly ? (pilot && !realTrial) : (realTrial && !pilot)) else {
            throw XCTSkip("Notification Reply UI evidence is opt-in.")
        }
        continueAfterFailure = false
        let marker = try XCTUnwrap(environment["QUATA_IOS_REPLY_NOTIFICATION_MARKER"])
        let text = try XCTUnwrap(environment["QUATA_IOS_REPLY_TEXT_MARKER"])
        let coordinatorPath = try XCTUnwrap(environment["QUATA_IOS_REPLY_COORDINATOR_DIRECTORY"])
        for value in [marker, text] {
            XCTAssertNotNil(value.range(of: "^qadata-reply-[a-z0-9-]{8,80}$", options: .regularExpression),
                            "Only disposable non-personal fixture markers may enter this test.")
        }
        let directory = URL(fileURLWithPath: coordinatorPath, isDirectory: true).standardizedFileURL
        XCTAssertTrue(directory.lastPathComponent.hasPrefix("quata-ios-reply-"))
        var isDirectory: ObjCBool = false
        XCTAssertTrue(FileManager.default.fileExists(atPath: directory.path, isDirectory: &isDirectory) && isDirectory.boolValue)
        XCTAssertFalse(FileManager.default.fileExists(atPath: directory.appendingPathComponent("ui-phase.json").path),
                       "A previous attempt must be reconciled; never reuse its coordinator directory.")

        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(es)", "-AppleLocale", "es_ES"]
        app.launch()
        XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "quata-ios-feed-host")
            .firstMatch.waitForExistence(timeout: 20), "The normal production host must launch.")

        let alerts = app.buttons.matching(NSPredicate(format: "label == %@ OR label MATCHES %@",
                                                     "Avisos", "^Avisos, [0-9]+$"))
        XCTAssertTrue(alerts.firstMatch.waitForExistence(timeout: 15))
        XCTAssertEqual(alerts.count, 1)
        alerts.firstMatch.tap()
        XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "quata-ios-notifications-host")
            .firstMatch.waitForExistence(timeout: 15))

        let permission = app.buttons["Permitir notificaciones"]
        if permission.waitForExistence(timeout: 3) {
            permission.tap()
            let system = XCUIApplication(bundleIdentifier: "com.apple.springboard")
            let allow = system.alerts.buttons.matching(NSPredicate(format: "label == %@ OR label == %@", "Permitir", "Allow")).firstMatch
            XCTAssertTrue(allow.waitForExistence(timeout: 10), "The real OS permission prompt must be handled.")
            allow.tap()
        }
        XCTAssertFalse(app.buttons["Abrir ajustes"].exists, "Denied notification permission needs explicit fixture reconciliation.")

        XCUIDevice.shared.press(.home)
        let system = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let top = system.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.01))
        top.press(forDuration: 0.1, thenDragTo: system.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.8)))
        try writePhase("ready-for-notification", directory: directory, marker: marker)

        let notifications = system.staticTexts.matching(NSPredicate(format: "label == %@", marker))
        let notification = notifications.firstMatch
        XCTAssertTrue(notification.waitForExistence(timeout: 30), "The injected fixture notification must be visible in the system UI.")
        XCTAssertEqual(notifications.count, 1, "A duplicate fixture notification makes submission ambiguous.")
        if affordanceOnly {
            // Anchor observed in the failed run's SpringBoard accessibility tree.
            // The pilot isolates pressing the notification card from its text child.
            let cards = system.buttons.matching(NSPredicate(format: "identifier == %@ AND label CONTAINS %@", "ListCell", marker))
            XCTAssertEqual(cards.count, 1)
            XCTAssertTrue(cards.firstMatch.isHittable)
            cards.firstMatch.press(forDuration: 1)
        } else {
            notification.press(forDuration: 1)
        }
        let replies = system.buttons.matching(NSPredicate(format: "label == %@", "Responder"))
        let reply = replies.firstMatch
        XCTAssertTrue(reply.waitForExistence(timeout: 10), "The native localized Reply action must be available.")
        XCTAssertEqual(replies.allElementsBoundByIndex.filter { $0.isHittable }.count, 1)
        if affordanceOnly {
            try writePhase("reply-affordance-visible", directory: directory, marker: marker)
            return
        }
        try XCTUnwrap(replies.allElementsBoundByIndex.first { $0.isHittable }).tap()
        let input = system.textViews.firstMatch
        XCTAssertTrue(input.waitForExistence(timeout: 10), "The OS must expose its text reply editor.")
        XCTAssertEqual(system.textViews.allElementsBoundByIndex.filter { $0.isHittable }.count, 1)
        let visibleInput = try XCTUnwrap(system.textViews.allElementsBoundByIndex.first { $0.isHittable })
        visibleInput.tap()
        visibleInput.typeText(text)
        let sends = system.buttons.matching(NSPredicate(format: "label == %@", "Enviar"))
        let send = sends.firstMatch
        XCTAssertTrue(send.waitForExistence(timeout: 5))
        XCTAssertEqual(sends.allElementsBoundByIndex.filter { $0.isHittable }.count, 1)
        try XCTUnwrap(sends.allElementsBoundByIndex.first { $0.isHittable }).tap()
        try writePhase("submitted-by-system-ui", directory: directory, marker: marker)
        // Backend verification, notification outcome and cleanup remain mandatory runner gates.
    }

    private func writePhase(_ phase: String, directory: URL, marker: String) throws {
        let data = try JSONSerialization.data(withJSONObject: ["phase": phase, "marker": marker])
        try data.write(to: directory.appendingPathComponent("ui-phase.json"), options: .atomic)
    }
}
