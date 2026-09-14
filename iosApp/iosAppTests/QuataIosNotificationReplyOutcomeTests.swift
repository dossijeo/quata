import Foundation
import UserNotifications
import XCTest

/// Runs only after the private coordinator verifies the exact persisted Reply.
/// This queries the app's actual delivered notifications, not SpringBoard visibility.
final class QuataIosNotificationReplyOutcomeTests: XCTestCase {
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
}
