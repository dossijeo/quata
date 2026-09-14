import Foundation
import UserNotifications
import XCTest
@testable import QuataIos

/// Private, opt-in delivery-source comparison. Never installs a session or sends a Reply.
final class QuataIosLocalNotificationComparisonTests: XCTestCase {
    private struct Input: Decodable {
        let runId: String
        let stepId: String
        let profileId: String
    }

    private func context() throws -> (URL, Input, String) {
        guard let path = ProcessInfo.processInfo.environment["QUATA_IOS_LOCAL_NOTIFICATION_COMPARISON_DIRECTORY"] else {
            throw XCTSkip("Local notification comparison is opt-in.")
        }
        continueAfterFailure = false
        let directory = URL(fileURLWithPath: path, isDirectory: true).standardizedFileURL
        XCTAssertTrue(directory.lastPathComponent.hasPrefix("quata-ios-reply-local-"))
        let input = try JSONDecoder().decode(Input.self, from: Data(contentsOf: directory.appendingPathComponent("input.json")))
        for value in [input.runId, input.stepId, input.profileId] {
            XCTAssertEqual(try XCTUnwrap(UUID(uuidString: value)).uuidString.lowercased(), value)
        }
        XCTAssertEqual(Set([input.runId, input.stepId, input.profileId]).count, 3)
        return (directory, input, "qadata-local-reply-\(input.stepId)")
    }

    private func notifications(_ center: UNUserNotificationCenter) -> ([UNNotification], [UNNotificationRequest]) {
        var delivered: [UNNotification] = []
        var pending: [UNNotificationRequest] = []
        let inspected = expectation(description: "Read local comparison requests")
        inspected.expectedFulfillmentCount = 2
        center.getDeliveredNotifications { delivered = $0; inspected.fulfill() }
        center.getPendingNotificationRequests { pending = $0; inspected.fulfill() }
        wait(for: [inspected], timeout: 5)
        return (delivered, pending)
    }

    func testScheduleOwnedLocalComparisonNotification() throws {
        let (directory, input, identifier) = try context()
        let receipt = directory.appendingPathComponent("scheduled.json")
        let intent = directory.appendingPathComponent("schedule-intent.json")
        XCTAssertFalse(FileManager.default.fileExists(atPath: receipt.path))
        XCTAssertFalse(FileManager.default.fileExists(atPath: intent.path))
        let center = UNUserNotificationCenter.current()
        var categories: Set<UNNotificationCategory> = []
        var authorized = false
        let inspected = expectation(description: "Check category and permission before local delivery")
        inspected.expectedFulfillmentCount = 2
        center.getNotificationCategories { categories = $0; inspected.fulfill() }
        center.getNotificationSettings { authorized = $0.authorizationStatus == .authorized; inspected.fulfill() }
        wait(for: [inspected], timeout: 5)
        XCTAssertTrue(authorized)
        let category = try XCTUnwrap(categories.first { $0.identifier == IosNotificationReplyAction.category })
        XCTAssertEqual(category.actions.count, 1)
        let action = try XCTUnwrap(category.actions.first as? UNTextInputNotificationAction)
        XCTAssertEqual(action.identifier, "QUATA_CHAT_REPLY")
        XCTAssertEqual(action.title, "Responder")
        XCTAssertTrue(action.options.contains(.authenticationRequired))
        let (delivered, pending) = notifications(center)
        XCTAssertFalse(delivered.contains { $0.request.identifier == identifier })
        XCTAssertFalse(pending.contains { $0.identifier == identifier })
        let content = UNMutableNotificationContent()
        content.title = "QADATA Reply"
        content.body = "qadata-reply-alert-\(input.stepId)"
        content.categoryIdentifier = category.identifier
        content.userInfo = ["conversation_id": "sb:9223372036854775807", "recipient_profile_id": input.profileId]
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 120, repeats: false)
        let dueAt = try XCTUnwrap(trigger.nextTriggerDate()).timeIntervalSince1970
        let metadata: [String: Any] = ["runId": input.runId, "stepId": input.stepId,
            "requestId": identifier, "dueAt": dueAt, "category": category.identifier,
            "action": action.identifier, "title": action.title, "textInput": true,
            "authenticationRequired": true, "authorized": true, "replySubmitted": false]
        let data = try JSONSerialization.data(withJSONObject: metadata)
        try data.write(to: intent, options: .withoutOverwriting)
        var addError: Error?
        let added = expectation(description: "Schedule exactly one local comparison notification")
        center.add(UNNotificationRequest(identifier: identifier, content: content, trigger: trigger)) {
            addError = $0; added.fulfill()
        }
        wait(for: [added], timeout: 5)
        XCTAssertNil(addError)
        let (_, after) = notifications(center)
        XCTAssertEqual(after.filter { $0.identifier == identifier }.count, 1)
        try data.write(to: receipt, options: .withoutOverwriting)
    }

    func testReconcileOwnedLocalComparisonNotification() throws {
        let (directory, input, identifier) = try context()
        let receipt = directory.appendingPathComponent("local-recovery.json")
        XCTAssertFalse(FileManager.default.fileExists(atPath: receipt.path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: directory.appendingPathComponent("schedule-intent.json").path))
        let center = UNUserNotificationCenter.current()
        let (delivered, pending) = notifications(center)
        let ownedDelivered = delivered.filter { $0.request.identifier == identifier }
        let ownedPending = pending.filter { $0.identifier == identifier }
        XCTAssertLessThanOrEqual(ownedDelivered.count, 1)
        XCTAssertLessThanOrEqual(ownedPending.count, 1)
        for content in ownedDelivered.map({ $0.request.content }) + ownedPending.map({ $0.content }) {
            XCTAssertEqual(content.body, "qadata-reply-alert-\(input.stepId)")
            XCTAssertEqual(content.title, "QADATA Reply")
            XCTAssertEqual(content.categoryIdentifier, IosNotificationReplyAction.category)
            XCTAssertEqual(content.userInfo["conversation_id"] as? String, "sb:9223372036854775807")
            XCTAssertEqual(content.userInfo["recipient_profile_id"] as? String, input.profileId)
        }
        center.removePendingNotificationRequests(withIdentifiers: [identifier])
        center.removeDeliveredNotifications(withIdentifiers: [identifier])
        let deadline = Date().addingTimeInterval(10)
        var remaining = -1
        repeat {
            let (afterDelivered, afterPending) = notifications(center)
            remaining = afterDelivered.filter { $0.request.identifier == identifier }.count
                + afterPending.filter { $0.identifier == identifier }.count
            if remaining == 0 { break }
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        } while Date() < deadline
        XCTAssertEqual(remaining, 0)
        var metadata: [String: Any] = ["runId": input.runId, "stepId": input.stepId,
            "requestId": identifier, "deliveredCount": ownedDelivered.count,
            "pendingCount": ownedPending.count, "notificationRemoved": true, "replySubmitted": false]
        if let notification = ownedDelivered.first {
            metadata["deliveredAt"] = notification.date.timeIntervalSince1970
        }
        try JSONSerialization.data(withJSONObject: metadata).write(to: receipt, options: .withoutOverwriting)
    }
}
