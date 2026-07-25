import CoreLocation
import UIKit
import UserNotifications
import QuataShared

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
        notificationTapDelegate.setChatDestination { [weak self] target in
            self?.compositionRoot.openChat(
                conversationId: target.conversationId,
                messageId: target.messageId,
            )
        }
        compositionRoot.start()
        return true
    }

    func application(
        _ app: UIApplication,
        open url: URL,
        options: [UIApplication.OpenURLOptionsKey: Any] = [:],
    ) -> Bool {
        compositionRoot.handleDeepLink(url)
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
    private lazy var authenticatedHost = IosAuthenticatedHostRouter(platformServices: platformServices)
    private lazy var authenticatedRouteDispatcher = IosAuthenticatedRouteDispatcher(host: authenticatedHost)
    private let deepLinkDispatcher = IosDeepLinkDispatcher()
    private lazy var runtimeConfiguration: IosFeedRuntimeConfiguration? =
        IosPublicRuntimeConfiguration.feedConfiguration()
    private lazy var runtimeBootstrap: IosFeedRuntimeBootstrap? = {
        guard let configuration = runtimeConfiguration else { return nil }
        return IosFeedRuntimeBootstrapKt.createIosFeedRuntimeBootstrap(configuration: configuration)
    }()
    // Chat receives precisely the Keychain-backed session retained by Feed/Auth. It is created
    // only after a restored or newly logged-in session has installed the authenticated Feed host.
    private lazy var chatRuntimeBootstrap: IosChatRuntimeBootstrap? = {
        guard let configuration = runtimeConfiguration, let runtimeBootstrap else { return nil }
        return IosChatRuntimeBootstrapKt.createIosChatRuntimeBootstrap(
            configuration: IosChatRuntimeConfiguration(
                supabaseUrl: configuration.supabaseUrl,
                supabasePublishableKey: configuration.supabasePublishableKey,
            ),
            authSession: runtimeBootstrap.authSessionForInteractiveLogin(),
        )
    }()
    /// Official is composed only after the Keychain-backed authenticated session used by
    /// Auth/Feed has been restored. Its repository is a real read-only PostgREST adapter.
    private lazy var officialRuntimeBootstrap: IosOfficialRuntimeBootstrap? = {
        guard let configuration = runtimeConfiguration, let runtimeBootstrap else { return nil }
        return IosOfficialRuntimeBootstrap(
            configuration: IosOfficialRuntimeConfiguration(
                supabaseUrl: configuration.supabaseUrl,
                supabasePublishableKey: configuration.supabasePublishableKey,
            ),
            authSession: runtimeBootstrap.authSessionForInteractiveLogin(),
        )
    }()

    func start() {
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = authenticatedHost
        window.makeKeyAndVisible()
        self.window = window
        deepLinkDispatcher.attachHost(host: authenticatedRouteDispatcher)
        if !installRestoredFeedSessionIfAvailable() {
            installAuthenticationIfConfigured()
        }
    }

    func handleDeepLink(_ url: URL) -> Bool {
        _ = deepLinkDispatcher.handleUrl(url: url.absoluteString)
        return true
    }

    func openChat(conversationId: String, messageId: String?) {
        authenticatedHost.showChat(conversationId: conversationId, messageId: messageId)
    }

    /// Called by the iOS authenticated bootstrap once it has a real `FeedRepository` wrapped in
    /// the Kotlin dependency object. No Android repository, URL or token is created by Swift.
    func installAuthenticatedFeed(_ dependencies: IosFeedHostDependencies) {
        authenticatedHost.installAuthenticatedFeed(dependencies)
    }

    /// Installs the shared notification inbox only after a real authenticated repository has
    /// been composed by Kotlin. APNs delivery and the existing tap delegate stay independent of
    /// this screen factory, so the launcher never fabricates notification data for navigation.
    func installAuthenticatedNotifications(_ dependencies: IosNotificationsHostDependencies) {
        authenticatedHost.installNotificationsFactory {
            IosNotificationsHostKt.QuataNotificationsViewController(dependencies: dependencies)
        }
    }

    @discardableResult
    private func installRestoredFeedSessionIfAvailable() -> Bool {
        guard let dependencies = runtimeBootstrap?.restoredDependencies(
            navigationMessage: "Quata para iOS",
            onOpenChats: { [weak self] in
                self?.authenticatedHost.openChatList()
            },
        ) else { return false }
        installAuthenticatedFeed(dependencies)
        installAuthenticatedChatIfAvailable()
        installAuthenticatedOfficialIfAvailable()
        installAuthenticatedNotificationsIfAvailable()
        return true
    }

    private func installAuthenticatedChatIfAvailable() {
        guard let chatRuntimeBootstrap else { return }
        authenticatedHost.installAuthenticatedChat(chatRuntimeBootstrap)
    }

    private func installAuthenticatedOfficialIfAvailable() {
        guard let officialRuntimeBootstrap else { return }
        authenticatedHost.installOfficialFactory { postId in
            QuataOfficialViewControllerKt.QuataOfficialViewController(
                dependencies: QuataOfficialViewControllerKt.createIosOfficialHostDependencies(
                    repository: officialRuntimeBootstrap.repository,
                    officialPostId: postId,
                    navigationMessage: "Quata para iOS",
                ),
            )
        }
    }

    private func installAuthenticatedNotificationsIfAvailable() {
        guard let chatRuntimeBootstrap else { return }
        let notificationsBootstrap = IosNotificationsRuntimeBootstrapKt
            .createIosNotificationsRuntimeBootstrap(chatRepository: chatRuntimeBootstrap.repository())
        installAuthenticatedNotifications(
            IosNotificationsHostKt.createIosNotificationsHostDependencies(
                repository: notificationsBootstrap.repository(),
                timestampNowMillis: Int64(Date().timeIntervalSince1970 * 1_000),
                onBack: { [weak self] in self?.authenticatedHost.showFeed(postId: nil) },
                onOpenConversation: { [weak self] conversationId in
                    self?.authenticatedHost.showChat(conversationId: conversationId, messageId: nil)
                },
                onRequestNotificationPermission: {
                    // Permission is a UIKit/system concern. This invokes the real iOS prompt;
                    // APNs token registration remains the existing AppDelegate bridge.
                    UNUserNotificationCenter.current().requestAuthorization(
                        options: [.alert, .badge, .sound]
                    ) { _, _ in }
                },
                // Conversation navigation above is the real host action. This common callback
                // is observability only and must not manufacture a URL or a route.
                onHandleDeepLink: { _ in },
            ),
        )
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
        authenticatedHost.installAuthentication(dependencies)
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

/// Authenticated UIKit router for shared Compose feature hosts.
///
/// It contains no Swift screen and creates no feature repository. Factories arrive only when the
/// launcher has real dependencies; a deep link received earlier remains pending.
final class IosAuthenticatedHostRouter: UIViewController, IosAuthenticatedRouteHost {
    private let platformServices: IosPlatformServiceComposition
    private var displayedController: UIViewController?
    private var feedFactory: ((String?) -> UIViewController)?
    private var chatFactory: ((String?, String?) -> UIViewController)?
    private var officialFactory: ((String?) -> UIViewController)?
    private var notificationsFactory: (() -> UIViewController)?
    private var pendingRoute: PendingRoute?

    private enum PendingRoute {
        case feed(postId: String?)
        case chat(conversationId: String?, messageId: String?)
        case official(postId: String?)
        case notifications
    }

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
        feedFactory = { _ in QuataFeedViewControllerKt.QuataFeedViewController(dependencies: dependencies) }
        renderPendingRouteIfPossible()
        if pendingRoute == nil {
            showFeed(postId: nil)
        } else if let feedController = feedFactory?(nil) {
            // A Chat/Official route can legitimately wait for its own real repository. Keep that
            // pending identifier while returning the authenticated user to the real Feed surface.
            showRouteController(feedController, route: .feed(postId: nil))
        }
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

    /// These factories are injected by a future real iOS repository composition. They activate
    /// exported KMP UIViewControllers without adding a Swift replacement screen.
    func installChatFactory(_ factory: @escaping (String?, String?) -> UIViewController) {
        chatFactory = factory
        renderPendingRouteIfPossible()
    }

    /// Installs the real KMP Chat host after Auth/Feed restored the Keychain-backed session.
    /// `messageId` intentionally remains a navigation hint: the common Chat host currently
    /// supports opening a conversation, but not scrolling to a particular message identifier.
    func installAuthenticatedChat(_ bootstrap: IosChatRuntimeBootstrap) {
        let services = platformServices.services
        installChatFactory { [weak self] conversationId, _ in
            let dependencies = bootstrap.hostDependencies(
                audioPlayer: services.audioPlayer,
                audioRecorder: services.audioRecorder,
                filePicker: services.filePicker,
                conversationId: conversationId,
                onOpenConversation: { [weak self] conversationId in
                    self?.showChat(conversationId: conversationId, messageId: nil)
                },
                onBackToList: { [weak self] in
                    self?.openChatList()
                },
                // The shared Chat surface already owns picker, recording and playback through
                // the injected adapters. Existing remote attachments need a sandboxed download
                // policy before Quick Look can receive a local URL, so surface that boundary
                // explicitly rather than silently swallowing the user's action.
                onOpenAttachment: { [weak self] _ in
                    self?.presentRemoteAttachmentUnavailableNotice()
                },
            )
            return QuataChatViewControllerKt.QuataChatViewController(dependencies: dependencies)
        }
    }

    private func presentRemoteAttachmentUnavailableNotice() {
        let alert = UIAlertController(
            title: NSLocalizedString("ios_chat_attachment_pending_title", value: "Adjunto", comment: ""),
            message: NSLocalizedString(
                "ios_chat_attachment_pending_message",
                value: "La descarga segura de adjuntos remotos estará disponible próximamente.",
                comment: "",
            ),
            preferredStyle: .alert,
        )
        alert.addAction(UIAlertAction(title: NSLocalizedString("common_close", value: "Cerrar", comment: ""), style: .default))
        present(alert, animated: true)
    }

    func installOfficialFactory(_ factory: @escaping (String?) -> UIViewController) {
        officialFactory = factory
        renderPendingRouteIfPossible()
    }

    func installNotificationsFactory(_ factory: @escaping () -> UIViewController) {
        notificationsFactory = factory
        renderPendingRouteIfPossible()
    }

    func showFeed(postId: String?) { route(.feed(postId: postId)) }

    func showChat(conversationId: String, messageId: String?) {
        route(.chat(conversationId: conversationId, messageId: messageId))
    }

    func showOfficial(postId: String?) { route(.official(postId: postId)) }

    func showNotifications() { route(.notifications) }

    func openChatList() { route(.chat(conversationId: nil, messageId: nil)) }

    private func showMigrationStatus() {
        show(
            QuataFeedViewControllerKt.QuataIosMigrationStatusViewController(),
            accessibilityIdentifier: "quata-ios-compose-root",
            accessibilityLabel: "Quata iOS requires an authenticated Feed session",
        )
    }

    private func route(_ route: PendingRoute) {
        guard let controller = controller(for: route) else {
            pendingRoute = route
            return
        }
        pendingRoute = nil
        showRouteController(controller, route: route)
    }

    private func renderPendingRouteIfPossible() {
        guard let pendingRoute, let controller = controller(for: pendingRoute) else { return }
        self.pendingRoute = nil
        showRouteController(controller, route: pendingRoute)
    }

    private func controller(for route: PendingRoute) -> UIViewController? {
        switch route {
        case let .feed(postId):
            return feedFactory?(postId)
        case let .chat(conversationId, messageId):
            return chatFactory?(conversationId, messageId)
        case let .official(postId):
            return officialFactory?(postId)
        case .notifications:
            return notificationsFactory?()
        }
    }

    private func showRouteController(_ controller: UIViewController, route: PendingRoute) {
        let presentation: (identifier: String, label: String)
        switch route {
        case .feed:
            presentation = ("quata-ios-feed-host", "Quata iOS Feed")
        case .chat:
            presentation = ("quata-ios-chat-host", "Quata iOS Chat")
        case .official:
            presentation = ("quata-ios-official-host", "Quata iOS Official")
        case .notifications:
            presentation = ("quata-ios-notifications-host", "Quata iOS Notifications")
        }
        show(controller, accessibilityIdentifier: presentation.identifier, accessibilityLabel: presentation.label)
    }

    /// Replaces the visible shared Compose controller atomically.
    ///
    /// Internal for the host XCTest target: Auth, Feed and deferred authenticated routes use the
    /// same transition, so containment remains testable without credentials or backend calls.
    func show(
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

/// Kept as an internal source-compatible name for the UIKit host boundary tests while the
/// implementation evolves from Feed-only containment into authenticated route containment.
typealias IosFeedHostContainerViewController = IosAuthenticatedHostRouter
