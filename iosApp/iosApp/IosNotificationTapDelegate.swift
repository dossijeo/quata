import Foundation
import UserNotifications
import QuataShared

/// Retain this object strongly from the app delegate: UNUserNotificationCenter keeps its delegate weakly.
/// It parses an already-delivered notification tap but deliberately does not register APNs or request
/// notification permission. Future navigation injects `setChatDestination` after its route host is ready.
final class IosNotificationTapDelegate: NSObject, UNUserNotificationCenterDelegate {
    // Kotlin default constructor arguments are not exported as a zero-argument Swift initializer.
    private let bridge = IosNotificationResponseBridge(adapter: IosNotificationDeepLinkAdapter())
    private var destinationHost: IosNotificationDestinationHost?
    private let recipientGate: NotificationRecipientGate
    private var replyHandler: ((QuataChatDeepLink, String, String, String, @escaping (NotificationReplyOutcome) -> Void) -> Void)?

    init(recipientGate: NotificationRecipientGate) {
        self.recipientGate = recipientGate
        super.init()
    }

    func install(on center: UNUserNotificationCenter = .current()) {
        center.delegate = self
        center.setNotificationCategories([IosNotificationReplyAction.notificationCategory()])
    }

    func setReplyHandler(_ handler: @escaping (QuataChatDeepLink, String, String, String, @escaping (NotificationReplyOutcome) -> Void) -> Void) {
        replyHandler = handler
    }

    func setChatDestination(_ callback: @escaping (QuataChatDeepLink) -> Void) {
        destinationHost.map { bridge.detachHost(host: $0) }
        let host = IosNotificationDestinationHost(callback: callback)
        destinationHost = host
        bridge.attachHost(host: host)
    }

    func clearChatDestination() {
        destinationHost.map { bridge.detachHost(host: $0) }
        destinationHost = nil
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void,
    ) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { completionHandler(); return }
            if response.actionIdentifier == IosNotificationReplyAction.identifier {
                self.handleReply(response, center: center, completion: completionHandler)
                return
            }
            guard response.actionIdentifier == UNNotificationDefaultActionIdentifier else {
                completionHandler(); return
            }
            let recipient = Self.recipientProfileId(in: response.notification.request.content.userInfo)
            self.recipientGate.receive(recipientProfileId: recipient) { [weak self] in
                _ = self?.bridge.handle(response: response)
            }
            completionHandler()
        }
    }

    private func handleReply(_ response: UNNotificationResponse, center: UNUserNotificationCenter, completion: @escaping () -> Void) {
        let userInfo = response.notification.request.content.userInfo
        guard let input = response as? UNTextInputNotificationResponse,
              !input.userText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              let recipient = Self.recipientProfileId(in: userInfo), !recipient.isEmpty,
              let target = IosNotificationDeepLinkAdapter().targetFromApnsPayload(userInfo: userInfo),
              let replyHandler else { completion(); return }
        let requestID = response.notification.request.identifier
        replyHandler(target, recipient, input.userText, "notification-reply-\(UUID().uuidString)") { outcome in
            if outcome == .sent {
                center.removeDeliveredNotifications(withIdentifiers: [requestID])
            } else if outcome == .failed {
                let routing: [AnyHashable: Any] = [
                    "conversation_id": target.conversationId,
                    "recipient_profile_id": recipient,
                ]
                let request = UNNotificationRequest(identifier: requestID,
                    content: IosNotificationReplyAction.failedContent(userInfo: routing), trigger: nil)
                center.add(request) { _ in Self.completeOnMain(completion) }
                return
            }
            completion()
        }
    }

    static func completeOnMain(_ completion: @escaping () -> Void) {
        if Thread.isMainThread {
            completion()
        } else {
            DispatchQueue.main.async(execute: completion)
        }
    }

    /// Match the shared adapter's nested payload precedence; malformed bindings fail closed.
    static func recipientProfileId(in userInfo: [AnyHashable: Any]) -> String? {
        let nested = ["data", "quata", "payload"].compactMap {
            userInfo[$0] as? [AnyHashable: Any]
        }.first ?? [:]
        guard let raw = userInfo["recipient_profile_id"] ?? nested["recipient_profile_id"] else { return nil }
        return raw as? String ?? ""
    }
}

private final class IosNotificationDestinationHost: NSObject, IosNotificationDeepLinkHost {
    private let callback: (QuataChatDeepLink) -> Void

    init(callback: @escaping (QuataChatDeepLink) -> Void) {
        self.callback = callback
    }

    func openChat(target: QuataChatDeepLink) {
        callback(target)
    }
}
