import Foundation
import UserNotifications
import QuataFeed

/// Retain this object strongly from the app delegate: UNUserNotificationCenter keeps its delegate weakly.
/// It parses an already-delivered notification tap but deliberately does not register APNs or request
/// notification permission. Future navigation injects `setChatDestination` after its route host is ready.
final class IosNotificationTapDelegate: NSObject, UNUserNotificationCenterDelegate {
    private let bridge = IosNotificationResponseBridge()
    private var destinationHost: IosNotificationDestinationHost?

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
        _ = bridge.handle(response: response)
        completionHandler()
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
