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

    init(recipientGate: NotificationRecipientGate) {
        self.recipientGate = recipientGate
        super.init()
    }

    func install(on center: UNUserNotificationCenter = .current()) {
        center.delegate = self
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
            let recipient = Self.recipientProfileId(in: response.notification.request.content.userInfo)
            self.recipientGate.receive(recipientProfileId: recipient) { [weak self] in
                _ = self?.bridge.handle(response: response)
            }
            completionHandler()
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
