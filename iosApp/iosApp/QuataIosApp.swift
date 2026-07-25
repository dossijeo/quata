import CoreLocation
import UIKit
import QuataFeed

enum IosPublicRuntimeConfiguration {
    private static let supabaseUrlKey = "QUATA_SUPABASE_URL"
    private static let supabasePublishableKeyKey = "QUATA_SUPABASE_PUBLISHABLE_KEY"

    /// Values are injected as build settings. The Supabase publishable key is client-safe;
    /// service-role credentials must never be added to an iOS bundle.
    static func feedConfiguration(bundle: Bundle = .main) -> IosFeedRuntimeConfiguration? {
        feedConfiguration(infoDictionary: bundle.infoDictionary ?? [:])
    }

    /// Kept separate from Bundle access so XCTest can validate unconfigured/expanded settings
    /// without a deployment bundle, network request or a client credential.
    static func feedConfiguration(infoDictionary: [String: Any]) -> IosFeedRuntimeConfiguration? {
        guard
            let url = configuredValue(for: supabaseUrlKey, infoDictionary: infoDictionary),
            let publishableKey = configuredValue(for: supabasePublishableKeyKey, infoDictionary: infoDictionary)
        else { return nil }
        return IosFeedRuntimeConfiguration(supabaseUrl: url, supabasePublishableKey: publishableKey)
    }

    private static func configuredValue(for key: String, infoDictionary: [String: Any]) -> String? {
        guard let value = infoDictionary[key] as? String else { return nil }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty || trimmed.contains("$(") ? nil : trimmed
    }
}

/// UIKit launcher and composition boundary for the iOS application.
///
/// Swift owns the window, lifecycle and authenticated dependency hand-off. The shared Feed
/// screen is always created by `QuataFeedViewController`; this target deliberately has no Swift
/// Feed view or sample repository.
@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    private let compositionRoot = IosAppCompositionRoot()
    // `UNUserNotificationCenter` retains its delegate weakly. Keep the bridge at the UIKit
    // composition boundary so an APNs tap is normalized even before a future authenticated
    // navigation host chooses to attach a destination callback.
    private let notificationTapDelegate = IosNotificationTapDelegate()

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil,
    ) -> Bool {
        notificationTapDelegate.install()
        compositionRoot.start()
        return true
    }
}

/// Keeps UIKit-only state at the platform edge. It selects the shared Auth or Feed Compose
/// controller according to the one Keychain-backed session owned by the Kotlin bootstrap.
private final class IosAppCompositionRoot {
    private var window: UIWindow?
    // Kotlin default arguments are not exported as a Swift zero-argument initializer. Build the
    // real Core Location host explicitly at the UIKit boundary instead of falling back to a
    // placeholder composition.
    private let platformServices = IosPlatformServiceComposition(
        coreLocationHost: IosCoreLocationHost(manager: CLLocationManager()),
    )
    private lazy var feedHost = IosFeedHostContainerViewController(platformServices: platformServices)
    private lazy var runtimeConfiguration: IosFeedRuntimeConfiguration? =
        IosPublicRuntimeConfiguration.feedConfiguration()
    private lazy var runtimeBootstrap: IosFeedRuntimeBootstrap? = {
        guard let configuration = runtimeConfiguration else { return nil }
        return IosFeedRuntimeBootstrapKt.createIosFeedRuntimeBootstrap(configuration: configuration)
    }()

    func start() {
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = feedHost
        window.makeKeyAndVisible()
        self.window = window
        if !installRestoredFeedSessionIfAvailable() {
            installAuthenticationIfConfigured()
        }
    }

    /// Called by the iOS authenticated bootstrap once it has a real `FeedRepository` wrapped in
    /// the Kotlin dependency object. No Android repository, URL or token is created by Swift.
    func installAuthenticatedFeed(_ dependencies: IosFeedHostDependencies) {
        feedHost.installAuthenticatedFeed(dependencies)
    }

    @discardableResult
    private func installRestoredFeedSessionIfAvailable() -> Bool {
        guard let dependencies = runtimeBootstrap?.restoredDependencies(
            navigationMessage: "Quata para iOS",
            onOpenChats: { [weak self] in
                self?.feedHost.presentChatsMigrationNotice()
            },
        ) else { return false }
        installAuthenticatedFeed(dependencies)
        return true
    }

    private func installAuthenticationIfConfigured() {
        guard
            let runtimeConfiguration,
            let runtimeBootstrap,
            let repository = createAuthRepository(
                configuration: runtimeConfiguration,
                bootstrap: runtimeBootstrap,
            )
        else { return }
        let dependencies = IosAuthHostKt.createIosAuthHostDependencies(
            repository: repository,
            languageCode: Locale.current.languageCode ?? "en",
            onLoginSuccess: { [weak self] in
                DispatchQueue.main.async {
                    _ = self?.installRestoredFeedSessionIfAvailable()
                }
            },
        )
        feedHost.installAuthentication(dependencies)
    }

    private func createAuthRepository(
        configuration: IosFeedRuntimeConfiguration,
        bootstrap: IosFeedRuntimeBootstrap,
    ) -> AuthRepository? {
        IosAuthRepositoryKt.createIosAuthRepository(
            configuration: IosAuthRuntimeConfiguration(
                supabaseUrl: configuration.supabaseUrl,
                supabasePublishableKey: configuration.supabasePublishableKey,
            ),
            session: bootstrap.authSessionForInteractiveLogin(),
        )
    }
}

/// A UIKit container rather than a SwiftUI replacement screen. It can atomically replace the
/// explicit unauthenticated Compose status controller with the shared Feed Compose controller.
private final class IosFeedHostContainerViewController: UIViewController {
    private let platformServices: IosPlatformServiceComposition
    private var displayedController: UIViewController?

    init(platformServices: IosPlatformServiceComposition) {
        self.platformServices = platformServices
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        nil
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        showMigrationStatus()
    }

    func installAuthenticatedFeed(_ dependencies: IosFeedHostDependencies) {
        show(
            QuataFeedViewControllerKt.QuataFeedViewController(dependencies: dependencies),
            accessibilityIdentifier: "quata-ios-feed-host",
            accessibilityLabel: "Quata iOS Feed",
        )
    }

    func installAuthentication(_ dependencies: IosAuthHostDependencies) {
        show(
            IosAuthHostKt.QuataAuthViewController(dependencies: dependencies),
            accessibilityIdentifier: "quata-ios-auth-host",
            accessibilityLabel: "Quata iOS authentication",
        )
    }

    /// The shared Feed can already expose its Conversations affordance, but the authenticated
    /// iOS Chat repository/navigation host is not wired yet. Keep that action explicit rather
    /// than routing to an invented screen or silently swallowing the tap.
    func presentChatsMigrationNotice() {
        let alert = UIAlertController(
            title: NSLocalizedString("ios_chat_host_pending_title", value: "Conversaciones", comment: ""),
            message: NSLocalizedString(
                "ios_chat_host_pending_message",
                value: "Las conversaciones estarÃ¡n disponibles cuando se complete el host autenticado de iOS.",
                comment: "",
            ),
            preferredStyle: .alert,
        )
        alert.addAction(UIAlertAction(title: NSLocalizedString("common_close", value: "Cerrar", comment: ""), style: .default))
        present(alert, animated: true)
    }

    private func showMigrationStatus() {
        show(
            QuataFeedViewControllerKt.QuataIosMigrationStatusViewController(),
            accessibilityIdentifier: "quata-ios-compose-root",
            accessibilityLabel: "Quata iOS requires an authenticated Feed session",
        )
    }

    private func show(
        _ controller: UIViewController,
        accessibilityIdentifier: String,
        accessibilityLabel: String,
    ) {
        let previous = displayedController
        addChild(controller)
        controller.view.frame = view.bounds
        controller.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        controller.view.accessibilityIdentifier = accessibilityIdentifier
        controller.view.isAccessibilityElement = true
        controller.view.accessibilityLabel = accessibilityLabel
        view.addSubview(controller.view)
        controller.didMove(toParent: self)
        platformServices.attachPresenter(controller: controller)

        previous?.willMove(toParent: nil)
        previous?.view.removeFromSuperview()
        previous?.removeFromParent()
        displayedController = controller
    }
}
