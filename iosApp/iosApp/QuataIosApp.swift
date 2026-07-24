import UIKit
import QuataFeed

/// UIKit launcher and composition boundary for the iOS application.
///
/// Swift owns the window, lifecycle and authenticated dependency hand-off. The shared Feed
/// screen is always created by `QuataFeedViewController`; this target deliberately has no Swift
/// Feed view or sample repository.
@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    private let compositionRoot = IosAppCompositionRoot()

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil,
    ) -> Bool {
        compositionRoot.start()
        return true
    }
}

/// Keeps UIKit-only state at the platform edge and exposes one injection point for the future
/// authenticated session/repository bootstrap. Until that bootstrap exists, the already-exported
/// Compose status surface remains visible instead of constructing fake Feed data.
private final class IosAppCompositionRoot {
    private var window: UIWindow?
    private let platformServices = IosPlatformServiceComposition()
    private lazy var feedHost = IosFeedHostContainerViewController(platformServices: platformServices)

    func start() {
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = feedHost
        window.makeKeyAndVisible()
        self.window = window
    }

    /// Called by the iOS authenticated bootstrap once it has a real `FeedRepository` wrapped in
    /// the Kotlin dependency object. No Android repository, URL or token is created by Swift.
    func installAuthenticatedFeed(_ dependencies: IosFeedHostDependencies) {
        feedHost.installAuthenticatedFeed(dependencies)
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
