import Foundation
import UserNotifications

enum IosNotificationReplyAction {
    static let category = "QUATA_CHAT_MESSAGE"
    static let identifier = "QUATA_CHAT_REPLY"

    static func notificationCategory() -> UNNotificationCategory {
        let reply = UNTextInputNotificationAction(
            identifier: identifier,
            title: NSLocalizedString("notification_reply", value: "Reply", comment: ""),
            options: [.authenticationRequired],
            textInputButtonTitle: NSLocalizedString("common_send", value: "Send", comment: ""),
            textInputPlaceholder: NSLocalizedString("notification_reply_hint", value: "Message", comment: "")
        )
        return UNNotificationCategory(identifier: category, actions: [reply], intentIdentifiers: [], options: [])
    }

    static func failedContent(userInfo: [AnyHashable: Any]) -> UNMutableNotificationContent {
        let content = UNMutableNotificationContent()
        content.title = NSLocalizedString("notification_reply_failed_title", value: "Reply not sent", comment: "")
        content.body = NSLocalizedString("notification_reply_failed_body", value: "Tap to open the chat and try again.", comment: "")
        // Preserve routing only. Never duplicate the original alert or the user's typed reply.
        content.userInfo = userInfo
        return content
    }
}
