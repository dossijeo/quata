import Foundation
import UserNotifications
import XCTest
@testable import QuataIos

/// Runs only after the private coordinator verifies the exact persisted Reply.
/// This queries the app's actual delivered notifications, not SpringBoard visibility.
final class QuataIosNotificationReplyOutcomeTests: XCTestCase {
    /// Recovery only: never submits a Reply and never credits successful delivery.
    /// Category metadata describes this host launch, not the failed UI's past state.
    func testReconcileOnlyTheOwnedFailedNotification() throws {
        guard let path = ProcessInfo.processInfo.environment["QUATA_IOS_REPLY_RECOVERY_DIRECTORY"] else {
            throw XCTSkip("Notification Reply recovery is opt-in.")
        }
        continueAfterFailure = false
        let directory = URL(fileURLWithPath: path, isDirectory: true).standardizedFileURL
        XCTAssertTrue(directory.lastPathComponent.hasPrefix("quata-ios-reply-recovery-"))
        let input = try JSONDecoder().decode(RecoveryInput.self, from: Data(contentsOf: directory.appendingPathComponent("input.json")))
        for id in [input.runId, input.stepId, input.profileId, input.failedStepId] {
            XCTAssertNotNil(UUID(uuidString: id))
        }
        let thread = try XCTUnwrap(Int64(input.threadId))
        XCTAssertGreaterThan(thread, 0)
        XCTAssertEqual(String(thread), input.threadId)
        let receipt = directory.appendingPathComponent("recovery-receipt.json")
        XCTAssertFalse(FileManager.default.fileExists(atPath: receipt.path))
        let center = UNUserNotificationCenter.current()
        let marker = "qadata-reply-alert-\(input.failedStepId)"
        func ownsRoute(_ content: UNNotificationContent) -> Bool {
            content.userInfo["conversation_id"] as? String == "sb:\(input.threadId)"
                && content.userInfo["recipient_profile_id"] as? String == input.profileId
        }
        var delivered: [UNNotification] = []
        var pending: [UNNotificationRequest] = []
        var categories: Set<UNNotificationCategory> = []
        let inspected = expectation(description: "Inspect only owned notification recovery")
        inspected.expectedFulfillmentCount = 3
        center.getDeliveredNotifications { delivered = $0; inspected.fulfill() }
        center.getPendingNotificationRequests { pending = $0; inspected.fulfill() }
        center.getNotificationCategories { categories = $0; inspected.fulfill() }
        wait(for: [inspected], timeout: 5)
        let owned = delivered.filter { ownsRoute($0.request.content) }
        XCTAssertEqual(owned.count, 1, "Recovery expects the single injected fixture notification.")
        let notification = try XCTUnwrap(owned.first)
        XCTAssertEqual(notification.request.content.body, marker)
        XCTAssertEqual(notification.request.content.title, "QADATA Reply")
        XCTAssertTrue(pending.filter { ownsRoute($0.content) }.isEmpty)
        let registered = categories.filter { $0.identifier == IosNotificationReplyAction.category }
        let actions = registered.flatMap { $0.actions }.map { action -> [String: Any] in
            ["identifier": action.identifier, "title": action.title,
             "textInput": action is UNTextInputNotificationAction,
             "authenticationRequired": action.options.contains(.authenticationRequired)]
        }
        center.removeDeliveredNotifications(withIdentifiers: [notification.request.identifier])
        var remaining = -1
        let deadline = Date().addingTimeInterval(10)
        repeat {
            let checked = expectation(description: "Verify owned notification removed")
            center.getDeliveredNotifications { notifications in
                remaining = notifications.filter { ownsRoute($0.request.content) }.count
                checked.fulfill()
            }
            wait(for: [checked], timeout: 5)
            if remaining == 0 { break }
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        } while Date() < deadline
        XCTAssertEqual(remaining, 0)
        let data = try JSONSerialization.data(withJSONObject: [
            "runId": input.runId, "stepId": input.stepId, "notificationRemoved": true,
            "replySubmitted": false, "categoryAfterHostLaunch": notification.request.content.categoryIdentifier,
            "registeredCategoryCountAfterHostLaunch": registered.count,
            "registeredActionsAfterHostLaunch": actions,
            "preferredLanguageAfterHostLaunch": Locale.preferredLanguages.first ?? "",
        ])
        try data.write(to: receipt, options: .atomic)
    }

    func testSuccessfulReplyLeavesNoDeliveredNotificationForTheFixture() throws {
        guard let path = ProcessInfo.processInfo.environment["QUATA_IOS_REPLY_OUTCOME_DIRECTORY"] else {
            throw XCTSkip("Notification Reply outcome evidence is opt-in.")
        }
        continueAfterFailure = false
        let directory = URL(fileURLWithPath: path, isDirectory: true).standardizedFileURL
        XCTAssertTrue(directory.lastPathComponent.hasPrefix("quata-ios-reply-outcome-"))
        let input = try JSONDecoder().decode(Input.self, from: Data(contentsOf: directory.appendingPathComponent("input.json")))
        XCTAssertNotNil(UUID(uuidString: input.runId))
        XCTAssertNotNil(UUID(uuidString: input.stepId))
        XCTAssertNotNil(UUID(uuidString: input.profileId))
        let thread = try XCTUnwrap(Int64(input.threadId))
        XCTAssertGreaterThan(thread, 0)
        XCTAssertEqual(String(thread), input.threadId)
        let receipt = directory.appendingPathComponent("outcome-receipt.json")
        XCTAssertFalse(FileManager.default.fileExists(atPath: receipt.path))
        let center = UNUserNotificationCenter.current()
        var remaining = -1
        let deadline = Date().addingTimeInterval(20)
        repeat {
            let completed = expectation(description: "Read fixture notification outcome")
            center.getDeliveredNotifications { notifications in
                remaining = notifications.filter { notification in
                    let payload = notification.request.content.userInfo
                    return payload["conversation_id"] as? String == "sb:\(input.threadId)"
                        && payload["recipient_profile_id"] as? String == input.profileId
                }.count
                completed.fulfill()
            }
            wait(for: [completed], timeout: 5)
            if remaining == 0 { break }
            RunLoop.current.run(until: Date().addingTimeInterval(0.5))
        } while Date() < deadline
        XCTAssertEqual(remaining, 0, "The original or a replacement failure notification remains delivered.")
        let data = try JSONSerialization.data(withJSONObject: [
            "runId": input.runId, "stepId": input.stepId, "notificationRemoved": true,
        ])
        try data.write(to: receipt, options: .atomic)
    }

    private struct Input: Decodable {
        let runId: String
        let stepId: String
        let profileId: String
        let threadId: String
    }

    private struct RecoveryInput: Decodable {
        let runId: String
        let stepId: String
        let profileId: String
        let threadId: String
        let failedStepId: String
    }
}
