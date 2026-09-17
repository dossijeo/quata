import XCTest
import QuataShared
@testable import QuataIos

final class IosNotificationRecipientTests: XCTestCase {
    func testColdTapWaitsForTheValidatedRecipientAcrossSwiftBridge() {
        let gate = NotificationRecipientGate()
        var opened = 0
        gate.receive(recipientProfileId: "a") { opened += 1 }
        XCTAssertEqual(opened, 0)
        gate.completeValidation(profileId: "a")
        gate.completeValidation(profileId: "a")
        XCTAssertEqual(opened, 1)
        gate.sessionEnded()
        gate.receive(recipientProfileId: "a") { opened += 1 }
        XCTAssertEqual(opened, 1)
    }

    func testLateRestorationCannotReplaceNewRecipientAcrossSwiftBridge() {
        let gate = NotificationRecipientGate()
        let originalGeneration = gate.generation
        gate.completeValidation(profileId: "b")
        gate.completeValidationIfCurrent(expectedGeneration: originalGeneration, profileId: nil)
        var opened = 0
        gate.receive(recipientProfileId: "a") { opened += 1 }
        gate.receive(recipientProfileId: "b") { opened += 1 }
        XCTAssertEqual(opened, 1)
    }

    func testTopLevelBindingOverridesNestedBinding() {
        XCTAssertEqual(IosNotificationTapDelegate.recipientProfileId(in: [
            "recipient_profile_id": "a", "data": ["recipient_profile_id": "b"],
        ]), "a")
    }

    func testNestedBindingUsesSharedAdapterPrecedence() {
        XCTAssertEqual(IosNotificationTapDelegate.recipientProfileId(in: [
            "data": ["recipient_profile_id": "a"], "payload": ["recipient_profile_id": "b"],
        ]), "a")
    }

    func testMalformedBindingIsRejectedRatherThanTreatedAsLegacy() {
        XCTAssertEqual(IosNotificationTapDelegate.recipientProfileId(in: ["recipient_profile_id": 42]), "")
        XCTAssertEqual(IosNotificationTapDelegate.recipientProfileId(in: [
            "recipient_profile_id": NSNull(), "data": ["recipient_profile_id": "a"],
        ]), "")
        XCTAssertNil(IosNotificationTapDelegate.recipientProfileId(in: ["aps": ["alert": "message"]]))
    }

    func testFailureNotificationCompletionMovesOffMainCallbackToMain() {
        let completed = expectation(description: "completion on main thread")

        DispatchQueue.global().async {
            IosNotificationTapDelegate.completeOnMain {
                XCTAssertTrue(Thread.isMainThread)
                completed.fulfill()
            }
        }

        wait(for: [completed], timeout: 1)
    }
}
