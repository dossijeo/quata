import Foundation
import UIKit
import UserNotifications
import QuataShared

enum IosNotificationPermissionAction: Equatable {
    case requestAuthorization
    case openSettings
    case none
}

/// Keeps APNs registration at the UIKit boundary.
///
/// Registration is requested only after iOS has granted notification authorization. The bridge
/// forwards tokens to the configured shared session runtime. Its composition root controls
/// activation after authentication; the bridge never owns another authenticated session.
final class IosApnsLifecycleBridge: NSObject, IosApnsRegistrationHost {
    static let shared = IosApnsLifecycleBridge()

    private let adapter = IosApnsRegistrationAdapter()
    private var runtime: IosApnsSessionRuntime?
    private var receivedToken: String?

    func install(runtime: IosApnsSessionRuntime) {
        if let previous = self.runtime { adapter.detachTokenHost(host: previous) }
        self.runtime = runtime
        adapter.attachTokenHost(host: runtime)
        if let receivedToken { _ = adapter.handleDeviceToken(token: receivedToken) }
    }

    private override init() {
        super.init()
        adapter.attachRegistrationHost(host: self)
    }

    func registerForRemoteNotifications() {
        UIApplication.shared.registerForRemoteNotifications()
    }

    func requestRegistrationIfAuthorized(
        center: UNUserNotificationCenter = .current(),
    ) {
        center.getNotificationSettings { [weak self] settings in
            guard IosApnsAuthorization.permitsRegistration(settings.authorizationStatus) else { return }
            DispatchQueue.main.async {
                self?.runtime?.refreshIfAvailable()
                // The adapter owns the presence check and exception boundary. Do not call UIKit
                // directly from permission callbacks, which can run off the main queue.
                _ = self?.adapter.requestRegistration()
            }
        }
    }

    func handleDeviceToken(_ deviceToken: Data) {
        // APNs tokens are binary. The bridge makes the canonical lowercase hex representation
        // expected by the Kotlin validator. Retain only in memory for late host installation.
        receivedToken = IosApnsTokenFormatting.hexString(deviceToken)
        _ = adapter.handleDeviceToken(token: receivedToken!)
    }

    func handleRegistrationFailure(_ error: Error) {
        // Do not forward arbitrary localized errors: they may include environment-specific text.
        // A stable code can be observed only when a future explicit failure host is attached.
        _ = adapter.handleRegistrationFailure(code: IosApnsFailureCode.from(error: error))
    }
}

enum IosApnsAuthorization {
    static func permitsRegistration(_ status: UNAuthorizationStatus) -> Bool {
        switch status {
        case .authorized, .provisional, .ephemeral:
            true
        case .notDetermined, .denied:
            false
        @unknown default:
            false
        }
    }

    static func shouldRequestRegistrationAfterPrompt(granted: Bool, error: Error?) -> Bool {
        granted && error == nil
    }

    static func permissionAction(_ status: UNAuthorizationStatus) -> IosNotificationPermissionAction {
        switch status {
        case .notDetermined:
            .requestAuthorization
        case .denied:
            .openSettings
        case .authorized, .provisional, .ephemeral:
            .none
        @unknown default:
            .openSettings
        }
    }
}

enum IosApnsTokenFormatting {
    static func hexString(_ token: Data) -> String {
        token.map { String(format: "%02x", $0) }.joined()
    }
}

private enum IosApnsFailureCode {
    static func from(error: Error) -> String {
        let nsError = error as NSError
        // Domains can contain arbitrary values; retain only a bounded, public error number.
        return "ios_apns_\(nsError.code)"
    }
}

/// Lifecycle methods intentionally live outside QuataIosApp.swift so the platform transport
/// remains independently reviewable from SwiftUI/Compose composition and app navigation.
extension AppDelegate {
    func applicationDidBecomeActive(_ application: UIApplication) {
        IosApnsLifecycleBridge.shared.requestRegistrationIfAuthorized()
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data,
    ) {
        IosApnsLifecycleBridge.shared.handleDeviceToken(deviceToken)
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error,
    ) {
        IosApnsLifecycleBridge.shared.handleRegistrationFailure(error)
    }
}
