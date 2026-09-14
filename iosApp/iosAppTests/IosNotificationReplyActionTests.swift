import XCTest
import UserNotifications
@testable import QuataIos

final class IosNotificationReplyActionTests: XCTestCase {
    func testChatCategoryUsesNativeTextInputAndRequiresUnlockedDevice() {
        let category = IosNotificationReplyAction.notificationCategory()
        XCTAssertEqual(category.identifier, "QUATA_CHAT_MESSAGE")
        XCTAssertEqual(category.actions.count, 1)
        let action = category.actions.first as? UNTextInputNotificationAction
        XCTAssertEqual(action?.identifier, "QUATA_CHAT_REPLY")
        XCTAssertEqual(action?.options.contains(.authenticationRequired), true)
        XCTAssertEqual(action?.options.contains(.foreground), false)
        XCTAssertFalse(action?.textInputButtonTitle.isEmpty ?? true)
    }

    func testFailureNotificationOpensChatWithoutOfferingAnotherInlineSend() {
        let routing: [AnyHashable: Any] = ["conversation_id": "sb:7", "recipient_profile_id": "actor-a"]
        let content = IosNotificationReplyAction.failedContent(userInfo: routing)
        XCTAssertEqual(content.categoryIdentifier, "")
        XCTAssertEqual(content.userInfo["conversation_id"] as? String, "sb:7")
        XCTAssertEqual(content.userInfo["recipient_profile_id"] as? String, "actor-a")
        XCTAssertEqual(content.userInfo.count, 2)
        XCTAssertFalse(content.title.isEmpty)
        XCTAssertFalse(content.body.isEmpty)
    }
}
