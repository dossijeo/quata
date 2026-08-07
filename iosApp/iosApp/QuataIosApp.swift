import CoreLocation
import Foundation
import UIKit
import UserNotifications
import QuataShared

enum IosWhatsNewLocale {
    static func sanitizedPreferredLanguageTag(_ preferredLanguages: [String] = Locale.preferredLanguages) -> String? {
        guard let raw = preferredLanguages.first else { return nil }
        let candidate = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard candidate.range(of: "^[A-Za-z]{2,3}([-_][A-Za-z0-9]{2,8})*$", options: .regularExpression) != nil else {
            return nil
        }
        return candidate
    }
}

/// iOS application-open contract for public Quata routes.
///
/// The app registers only the custom scheme `quata`. Its canonical shape is
/// `quata://egquata.com/#post-<id>`; `official-<id>` and `chat-<conversation>`
/// fragments use the shared Kotlin parser as well. HTTPS URLs remain web/share URLs and are
/// intentionally not claimed here: this target has no Associated Domains entitlement and does
/// not represent them as Universal Links.
enum IosDeepLinkUrlContract {
    static let scheme = "quata"
    static let host = "egquata.com"

    static func acceptsApplicationOpenUrl(_ url: URL) -> Bool {
        url.scheme?.caseInsensitiveCompare(scheme) == .orderedSame &&
            url.host?.caseInsensitiveCompare(host) == .orderedSame
    }

    /// Keeps the Info.plist declaration testable without treating XCTest's own bundle as the
    /// application bundle.
    static func isRegistered(in infoDictionary: [String: Any]) -> Bool {
        let urlTypes = infoDictionary["CFBundleURLTypes"] as? [[String: Any]] ?? []
        return urlTypes.contains { urlType in
            let schemes = urlType["CFBundleURLSchemes"] as? [String] ?? []
            return schemes.contains { $0.caseInsensitiveCompare(scheme) == .orderedSame }
        }
    }
}

enum IosPublicRuntimeConfiguration {
    static let wordpressBaseUrl = "https://egquata.com/"

    private static let supabaseUrlKey = "QUATA_SUPABASE_URL"
    private static let supabasePublishableKeyKey = "QUATA_SUPABASE_PUBLISHABLE_KEY"
    private static let iosRegistrationEnabledKey = "QUATA_IOS_REGISTRATION_ENABLED"
    private static let registrationApiKeyKey = "QUATA_IOS_REGISTRATION_API_KEY"
    private static let registrationClientInstanceIdKey = "QUATA_IOS_REGISTRATION_CLIENT_INSTANCE_ID"
    private static let registrationChallengeTokenKey = "QUATA_IOS_REGISTRATION_CHALLENGE_TOKEN"

    /// Values are injected as build settings. The Supabase publishable key is client-safe;
    /// service-role credentials must never be added to an iOS bundle.
    static func feedConfiguration(bundle: Bundle = .main) -> IosFeedRuntimeConfiguration? {
        feedConfiguration(infoDictionary: bundle.infoDictionary ?? [:])
    }

    /// Kept separate from Bundle access so XCTest can validate unconfigured/expanded settings
    /// without a deployment bundle, network request or a client credential.
    static func feedConfiguration(infoDictionary: [String: Any]) -> IosFeedRuntimeConfiguration? {
        guard
            let url = configuredURL(for: supabaseUrlKey, infoDictionary: infoDictionary),
            let publishableKey = configuredValue(for: supabasePublishableKeyKey, infoDictionary: infoDictionary)
        else { return nil }
        return IosFeedRuntimeConfiguration(supabaseUrl: url, supabasePublishableKey: publishableKey)
    }

    /// Registration is opt-in and remains unavailable for malformed or absent build settings.
    static func iosRegistrationEnabled(bundle: Bundle = .main) -> Bool {
        configuredValue(for: iosRegistrationEnabledKey, infoDictionary: bundle.infoDictionary ?? [:]) == "true"
    }

    /// Builds the exported Kotlin configuration with every registration gate explicitly wired.
    /// Missing, empty, or unexpanded inputs remain nil so registration fails closed.
    static func authConfiguration(
        from feedConfiguration: IosFeedRuntimeConfiguration,
        infoDictionary: [String: Any],
    ) -> IosAuthRuntimeConfiguration {
        IosAuthRuntimeConfiguration(
            supabaseUrl: feedConfiguration.supabaseUrl,
            supabasePublishableKey: feedConfiguration.supabasePublishableKey,
            iosRegistrationEnabled: configuredValue(
                for: iosRegistrationEnabledKey,
                infoDictionary: infoDictionary
            ) == "true",
            registrationApiKey: configuredValue(
                for: registrationApiKeyKey,
                infoDictionary: infoDictionary
            ),
            registrationClientInstanceId: configuredValue(
                for: registrationClientInstanceIdKey,
                infoDictionary: infoDictionary
            ),
            registrationChallengeToken: configuredValue(
                for: registrationChallengeTokenKey,
                infoDictionary: infoDictionary
            ),
        )
    }

    static func authConfiguration(
        from feedConfiguration: IosFeedRuntimeConfiguration,
        bundle: Bundle = .main,
    ) -> IosAuthRuntimeConfiguration {
        authConfiguration(from: feedConfiguration, infoDictionary: bundle.infoDictionary ?? [:])
    }

    private static func configuredValue(for key: String, infoDictionary: [String: Any]) -> String? {
        guard let value = infoDictionary[key] as? String else { return nil }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty || trimmed.contains("$(") || trimmed.rangeOfCharacter(from: .newlines) != nil
            ? nil
            : trimmed
    }

    private static func configuredURL(for key: String, infoDictionary: [String: Any]) -> String? {
        guard let value = configuredValue(for: key, infoDictionary: infoDictionary),
              let url = URL(string: value),
              url.scheme?.lowercased() == "https",
              url.host?.isEmpty == false,
              url.user == nil,
              url.password == nil
        else { return nil }
        return value
    }
}

/// Installs the Auth entry points and logout operation after the launch-time session probe.
///
/// A restored session changes which Feed factory is mounted, but it must not skip these
/// lifecycle bindings: authenticated users still need to sign out and anonymous private-route
/// attempts still need the common Auth gate.
enum IosAuthLifecycleBootstrap {
    static func installBindings(
        afterRestoredSessionAttempt restoredSessionInstalled: Bool,
        install: () -> Void,
    ) {
        _ = restoredSessionInstalled
        install()
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
        guard IosDeepLinkUrlContract.acceptsApplicationOpenUrl(url) else { return false }
        return compositionRoot.handleDeepLink(url)
    }
}

/// Device-local appearance state shared by Settings and Cuenta. Android persists the same two
/// controls at its application boundary; iOS keeps them at the UIKit boundary and injects them
/// into Compose hosts instead of allowing feature-level no-op defaults.
final class IosAppearancePreferences {
    private enum Key {
        static let themeMode = "quata_ios_theme_mode"
        static let touchFlowEnabled = "quata_ios_touch_flow_enabled"
    }

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    var touchFlowEnabled: Bool {
        defaults.object(forKey: Key.touchFlowEnabled) as? Bool ?? false
    }

    var themeModeStorageValue: String? {
        defaults.string(forKey: Key.themeMode)
    }

    func setTouchFlowEnabled(_ enabled: Bool) {
        defaults.set(enabled, forKey: Key.touchFlowEnabled)
    }

    func setThemeModeStorageValue(_ value: String) {
        defaults.set(value, forKey: Key.themeMode)
    }

    func applyTheme(to window: UIWindow) {
        switch themeModeStorageValue {
        case "dark-mode": window.overrideUserInterfaceStyle = .dark
        case "light-mode": window.overrideUserInterfaceStyle = .light
        default: window.overrideUserInterfaceStyle = .unspecified
        }
    }
}

/// Keeps UIKit-only state at the platform edge. It selects the shared Auth or Feed Compose
/// controller according to the one Keychain-backed session owned by the Kotlin bootstrap.
private final class IosAppCompositionRoot {
    private let appearancePreferences = IosAppearancePreferences()
    /// A Keychain entry is not an authenticated session until launch validation accepts it.
    /// This flag gates every private factory while the public Feed remains available first.
    private var hasValidatedAuthenticatedSession = false

    private var window: UIWindow?
    // Kotlin default arguments are not exported as a Swift zero-argument initializer. Build the
    // real Core Location host explicitly at the UIKit boundary instead of falling back to a
    // placeholder composition.
    private let platformServices = IosPlatformServiceComposition(
        coreLocationHost: IosCoreLocationHost(manager: CLLocationManager()),
    )
    private lazy var authenticatedHost = IosAuthenticatedHostRouter(platformServices: platformServices)
    private lazy var authenticatedRouteDispatcher = IosAuthenticatedRouteDispatcher(host: authenticatedHost)
    private lazy var whatsNewRuntimeBootstrap: IosWhatsNewRuntimeBootstrap? =
        IosWhatsNewRuntimeBootstrapKt.createDefaultIosWhatsNewRuntimeBootstrap(
            languageTag: IosWhatsNewLocale.sanitizedPreferredLanguageTag(),
        )
    private let deepLinkDispatcher = IosDeepLinkDispatcher()
    private lazy var runtimeConfiguration: IosFeedRuntimeConfiguration? =
        IosPublicRuntimeConfiguration.feedConfiguration()
    private lazy var runtimeBootstrap: IosFeedRuntimeBootstrap? = {
        guard let configuration = runtimeConfiguration else { return nil }
        return IosFeedRuntimeBootstrapKt.createIosFeedRuntimeBootstrap(configuration: configuration)
    }()
    /// One object identity is shared by Feed, Auth, Chat and Cuenta. Keeping this reference at
    /// the launcher boundary prevents Cuenta from opening a second Keychain/refresh pipeline.
    private lazy var renewableAuthSession: IosRenewableAuthSession? =
        runtimeBootstrap?.authSessionForInteractiveLogin()
    // Chat receives precisely the Keychain-backed session retained by Feed/Auth. It is created
    // only after a restored or newly logged-in session has installed the authenticated Feed host.
    private lazy var chatRuntimeBootstrap: IosChatRuntimeBootstrap? = {
        guard let configuration = runtimeConfiguration, let renewableAuthSession else { return nil }
        return IosChatRuntimeBootstrapKt.createIosChatRuntimeBootstrap(
            configuration: IosChatRuntimeConfiguration(
                supabaseUrl: configuration.supabaseUrl,
                supabasePublishableKey: configuration.supabasePublishableKey,
            ),
            authSession: renewableAuthSession,
        )
    }()
    // The inbox and the shared top chrome deliberately retain one notification repository. This
    // prevents the badge from becoming a separate Swift count with different unread semantics.
    private lazy var notificationsRuntimeBootstrap: IosNotificationsRuntimeBootstrap? = {
        guard let chatRuntimeBootstrap else { return nil }
        return IosNotificationsRuntimeBootstrapKt
            .createIosNotificationsRuntimeBootstrap(chatRepository: chatRuntimeBootstrap.repository())
    }()
    private var notificationCountObserver: IosNotificationCountObserver?
    private var notificationCountObservationID = UUID()
    private var notificationsFactoryGeneration = 0
    /// Official is a public, read-only browser.  Unlike the private verticals it is deliberately
    /// independent from Keychain restoration, so a valid public deployment can open a shared
    /// Official link before login and never sends a restored bearer token for that read.
    private lazy var officialRuntimeBootstrap: IosOfficialRuntimeBootstrap? = {
        guard let configuration = runtimeConfiguration else { return nil }
        return IosOfficialRuntimeBootstrap(
            configuration: IosOfficialRuntimeConfiguration(
                supabaseUrl: configuration.supabaseUrl,
                supabasePublishableKey: configuration.supabasePublishableKey,
            ),
            authSession: nil,
            preferredLanguageTag: Locale.preferredLanguages.first
        )
    }()
    /// Profile/SOS reuses the same Keychain-backed identity as Auth, Feed and the other iOS
    /// verticals. Cuenta receives this exact renewable object and resolves it before starting its
    /// parallel PostgREST reads; it never creates an independent Keychain or refresh owner.
    private lazy var profileSosRuntimeBootstrap: IosProfileSosRuntimeBootstrap? = {
        guard let configuration = runtimeConfiguration, let renewableAuthSession else { return nil }
        return IosProfileSosRuntimeBootstrapKt.createIosProfileSosRuntimeBootstrap(
            configuration: IosProfileRuntimeConfiguration(
                supabaseUrl: configuration.supabaseUrl,
                supabasePublishableKey: configuration.supabasePublishableKey,
            ),
            authSession: renewableAuthSession,
            languageTag: Locale.preferredLanguages.first,
        )
    }()
    /// Communities reuses the authenticated Chat repository for actual conversation creation and
    /// obtains directory snapshots through its own URLSession/PostgREST read adapter. No Swift
    /// sample directory or local substitute is created when runtime configuration is absent.
    private lazy var communitiesRuntimeBootstrap: IosNeighborhoodsRuntimeBootstrap? = {
        guard let configuration = runtimeConfiguration, let runtimeBootstrap, let chatRuntimeBootstrap else { return nil }
        return IosNeighborhoodsRuntimeBootstrap(
            configuration: IosNeighborhoodsRuntimeConfiguration(
                supabaseUrl: configuration.supabaseUrl,
                supabasePublishableKey: configuration.supabasePublishableKey,
            ),
            authSession: runtimeBootstrap.authSessionForInteractiveLogin(),
            chatRepository: chatRuntimeBootstrap.repository(),
        )
    }()
    /// Public Communities read path deliberately has no Keychain session.  It uses the same
    /// real PostgREST directory adapter with the publishable key only.
    private lazy var publicCommunitiesRuntimeBootstrap: IosNeighborhoodsRuntimeBootstrap? = {
        guard let configuration = runtimeConfiguration, let chatRuntimeBootstrap else { return nil }
        return IosNeighborhoodsReadRepositoryKt.createIosPublicNeighborhoodsRuntimeBootstrap(
            configuration: IosNeighborhoodsRuntimeConfiguration(
                supabaseUrl: configuration.supabaseUrl,
                supabasePublishableKey: configuration.supabasePublishableKey,
            ),
            chatRepository: chatRuntimeBootstrap.repository(),
        )
    }()
    /// One observable request identity keeps the Android loading treatment across every iOS
    /// source route while Kotlin resolves the real public profile before UIKit navigates.
    private let memberProfileOpeningState = IosMemberProfileOpeningState()
    private var memberProfilePreloader: IosCommunityProfilePreloader?
    /// The extension writes only to the App Group. This authenticated app runtime reuses the
    /// existing Keychain session and real Chat repository when the user opens the handoff URL.
    private lazy var externalShareRuntimeBootstrap: IosExternalShareRuntimeBootstrap? = {
        guard let runtimeBootstrap, let chatRuntimeBootstrap else { return nil }
        return IosExternalShareInboxKt.createIosExternalShareRuntimeBootstrap(
            authSession: runtimeBootstrap.authSessionForInteractiveLogin(),
            chatRepository: chatRuntimeBootstrap.repository()
        )
    }()
    private var externalShareForegroundObserver: NSObjectProtocol?

    func start() {
        let window = UIWindow(frame: UIScreen.main.bounds)
        if let fixtureRootViewController = uiTestFixtureRootViewControllerIfRequested() {
            window.rootViewController = fixtureRootViewController
            window.makeKeyAndVisible()
            self.window = window
            return
        }
        window.rootViewController = authenticatedHost
        appearancePreferences.applyTheme(to: window)
        window.makeKeyAndVisible()
        self.window = window
        externalShareForegroundObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            if let self, self.hasValidatedAuthenticatedSession, let chatRuntimeBootstrap = self.chatRuntimeBootstrap {
                chatRuntimeBootstrap.repository().setAppForeground(isForeground: true)
            }
            self?.authenticatedHost.restoreRouteAfterForeground()
            self?.installNotificationsIfAvailable()
            self?.presentPendingExternalShareIfAvailable()
        }
        NotificationCenter.default.addObserver(
            forName: UIApplication.didEnterBackgroundNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            guard let self, self.hasValidatedAuthenticatedSession, let chatRuntimeBootstrap = self.chatRuntimeBootstrap else { return }
            chatRuntimeBootstrap.repository().setAppForeground(isForeground: false)
        }
        deepLinkDispatcher.attachHost(host: authenticatedRouteDispatcher)
        installSettings()
        installWhatsNewIfAvailable()
        installPublicFeedIfConfigured()
        evaluateWhatsNewStartupIfAvailable()
        installPublicOfficialIfConfigured()
        installNotificationsIfAvailable()
        installCommunitiesIfAvailable()
        IosAuthLifecycleBootstrap.installBindings(
            afterRestoredSessionAttempt: false,
            install: installAuthenticationIfConfigured,
        )
        validateRestoredFeedSessionAsynchronously()
    }

    func handleDeepLink(_ url: URL) -> Bool {
        _ = deepLinkDispatcher.handleUrl(url: url.absoluteString)
        return true
    }

    /// XCTest fixtures are built before `authenticatedHost` is accessed. They deliberately use
    /// an independent UIKit root, so no Keychain session, runtime configuration, repository or
    /// Compose/Metal controller can be created on a fixture launch.
    private func uiTestFixtureRootViewControllerIfRequested() -> UIViewController? {
        let arguments = ProcessInfo.processInfo.arguments
        guard let fixtureIndex = arguments.firstIndex(of: "-quata-ui-test-fixture") else { return nil }

        let fixtureRoot = UIViewController()
        guard arguments.indices.contains(fixtureIndex + 1) else {
            fixtureRoot.view.accessibilityIdentifier = "quata-ios-test-invalid-fixture"
            fixtureRoot.view.accessibilityLabel = "Quata iOS invalid fixture"
            return fixtureRoot
        }
        switch arguments[fixtureIndex + 1] {
        case "anonymous":
            fixtureRoot.view.accessibilityIdentifier = "quata-ios-test-anonymous-host"
            fixtureRoot.view.accessibilityLabel = "Quata iOS anonymous fixture"
        case "auth-launch":
            // The Auth fixture is deliberately constructed before the production composition
            // root. Its Kotlin factory uses a local fail-closed repository, and this UIKit shell
            // provides stable containment/readiness for CI without an account or runtime setup.
            let destinationArgument = arguments.firstIndex(of: "-quata-auth-destination")
                .flatMap { arguments.indices.contains($0 + 1) ? arguments[$0 + 1] : nil }
            return IosAuthLaunchFixtureContainerViewController {
                if let destinationArgument {
                    return IosAuthLaunchFixtureHostKt.QuataAuthLaunchFixtureViewControllerForDestination(
                        destination: destinationArgument,
                    )
                }
                return IosAuthLaunchFixtureHostKt.QuataAuthLaunchFixtureViewController()
            }
        case "feed-playback":
            return IosFeedPlaybackFixtureHostKt.QuataIosFeedPlaybackFixtureViewController(
                mediaFactory: IosFeedNativeMediaFactory.shared
            )
        case "authenticated":
            // This deliberately runs the production Kotlin deep-link parser and the same
            // UIKit route adapter as the authenticated launcher. The destination controllers
            // are still inert UIKit fixtures: a UI-test launch must not restore a Keychain
            // session, construct a repository or imply that a remote feature E2E succeeded.
            let router = IosDeterministicDeepLinkFixtureRouter()
            let dispatcher = IosDeepLinkDispatcher()
            dispatcher.attachHost(host: IosAuthenticatedRouteDispatcher(host: router))
            if let deepLinkIndex = arguments.firstIndex(of: "-quata-ui-test-deep-link"),
               arguments.indices.contains(deepLinkIndex + 1) {
                _ = dispatcher.handleUrl(url: arguments[deepLinkIndex + 1])
            } else if let inAppRouteIndex = arguments.firstIndex(of: "-quata-ui-test-in-app-route"),
                      arguments.indices.contains(inAppRouteIndex + 1) {
                // Some authenticated destinations intentionally have no public URL contract.
                // Exercise those through the production Kotlin route adapter, never by making a
                // Swift-only destination switch. These remain inert UIKit surfaces: this proves
                // route/host accessibility wiring only, not a restored session or feature E2E.
                switch arguments[inAppRouteIndex + 1] {
                case "notifications":
                    IosAuthenticatedRouteDispatcher(host: router).openNotifications()
                case "profile-sos":
                    IosAuthenticatedRouteDispatcher(host: router).openProfileSos()
                case "communities":
                    IosAuthenticatedRouteDispatcher(host: router).openCommunities()
                case "composer":
                    IosAuthenticatedRouteDispatcher(host: router).openComposer()
                case "settings":
                    IosAuthenticatedRouteDispatcher(host: router).openSettings()
                case "whats-new":
                    IosAuthenticatedRouteDispatcher(host: router).openWhatsNew()
                case "release-history":
                    IosAuthenticatedRouteDispatcher(host: router).openReleaseHistory()
                default:
                    // An unknown fixture route must fail closed rather than silently render
                    // Feed and obscure a changed or misspelled route contract. Returning an
                    // inert controller also prevents fixture mode from falling through into the
                    // real composition root, which could restore Keychain state or Compose.
                    fixtureRoot.view.accessibilityIdentifier = "quata-ios-test-invalid-route"
                    fixtureRoot.view.accessibilityLabel = "Quata iOS invalid fixture route"
                    return fixtureRoot
                }
            } else {
                router.showFeed(postId: nil)
            }
            return router
        default:
            // A malformed explicit fixture argument must never fall through into the production
            // composition root. It can otherwise restore local state or turn a typo into an
            // accidental integration test with ambient runtime configuration.
            fixtureRoot.view.accessibilityIdentifier = "quata-ios-test-invalid-fixture"
            fixtureRoot.view.accessibilityLabel = "Quata iOS invalid fixture"
            return fixtureRoot
        }
        return fixtureRoot
    }

    func openChat(conversationId: String, messageId: String?) {
        authenticatedHost.showChat(conversationId: conversationId, messageId: messageId)
    }

    /// Called by the iOS authenticated bootstrap once it has a real `FeedRepository` wrapped in
    /// the Kotlin dependency object. No Android repository, URL or token is created by Swift.
    func installAuthenticatedFeed(_ dependencies: IosFeedHostDependencies) {
        authenticatedHost.installAuthenticatedFeed(dependencies)
    }

    /// A valid public deployment starts on the read-only browser before any Keychain session is
    /// inspected. The dependency is intentionally constructed by Kotlin without a session
    /// provider, so public PostgREST reads cannot acquire an Authorization header.
    private func installPublicFeedIfConfigured() {
        guard let runtimeBootstrap else { return }
        authenticatedHost.installPublicFeed { postId in
            QuataFeedViewControllerKt.QuataFeedViewController(
                dependencies: runtimeBootstrap.publicDependencies(
                    mediaFactory: IosFeedNativeMediaFactory.shared,
                    shareService: self.platformServices.services.share,
                    onOpenUserProfile: { [weak self] profileId in
                        self?.presentAuthenticatedMemberProfile(profileId: profileId)
                    },
                    initialPostId: postId,
                    onAuthRequired: { [weak self] in self?.authenticatedHost.presentAuthRequiredPrompt() },
                    onCreatePost: { [weak self] in self?.authenticatedHost.presentAuthRequiredPrompt() },
                    profileOpeningState: self.memberProfileOpeningState,
                ),
            )
        }
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
        guard let runtimeBootstrap, hasValidatedAuthenticatedSession else { return false }
        // A restoration/login completion can race with didEnterBackground.  Seed the newly
        // composed Chat repository from UIKit's current state before any private factory starts
        // observing it, otherwise a missed background transition leaves polling active.
        if let chatRuntimeBootstrap {
            chatRuntimeBootstrap.repository().setAppForeground(
                isForeground: UIApplication.shared.applicationState == .active
            )
        }
        authenticatedHost.installFeedFactory { postId in
            QuataFeedViewControllerKt.QuataFeedViewController(
                dependencies: runtimeBootstrap.authenticatedDependencies(
                    mediaFactory: IosFeedNativeMediaFactory.shared,
                    shareService: self.platformServices.services.share,
                    initialPostId: postId,
                    onOpenUserProfile: { [weak self] profileId in
                        self?.presentAuthenticatedMemberProfile(profileId: profileId)
                    },
                    onAuthRequired: { [weak self] in self?.authenticatedHost.presentAuthRequiredPrompt() },
                    onCreatePost: { [weak self] in self?.authenticatedHost.showComposer() },
                    profileOpeningState: self.memberProfileOpeningState,
                ),
            )
        }
        installAuthenticatedChatIfAvailable()
        installNotificationsIfAvailable()
        installAuthenticatedProfileSosIfAvailable()
        installCommunitiesIfAvailable()
        installAuthenticatedOfficialEditorIfAvailable()
        installAuthenticatedComposerIfAvailable()
        presentPendingExternalShareIfAvailable()
        return true
    }

    /// Public Feed is installed synchronously. Only a successfully validated/restored token may
    /// replace it with authenticated dependencies; a failed refresh leaves the public route up.
    private func validateRestoredFeedSessionAsynchronously() {
        guard let runtimeBootstrap else { return }
        runtimeBootstrap.validateRestoredSession { [weak self] validated in
            DispatchQueue.main.async {
                guard let self, validated.boolValue else { return }
                self.hasValidatedAuthenticatedSession = true
                _ = self.installRestoredFeedSessionIfAvailable()
            }
        }
    }

    private func presentPendingExternalShareIfAvailable() {
        guard
            let bootstrap = externalShareRuntimeBootstrap,
            authenticatedHost.presentedViewController == nil,
            let claim = bootstrap.claimRestoredAuthenticated(requestedId: nil)
        else { return }
        var completedConversationID: String?
        let dependencies = bootstrap.hostDependencies(
            claim: claim,
            onDismiss: { [weak self] in
                guard let self else { return }
                self.authenticatedHost.dismiss(animated: true) {
                    if let conversationID = completedConversationID {
                        self.authenticatedHost.showChat(conversationId: conversationID, messageId: nil)
                    }
                }
            },
            onOpenConversation: { conversationID in
                completedConversationID = conversationID
            }
        )
        let controller = QuataExternalShareViewControllerKt.QuataExternalShareViewController(
            dependencies: dependencies
        )
        controller.modalPresentationStyle = .fullScreen
        authenticatedHost.present(controller, animated: true)
    }

    private func installAuthenticatedChatIfAvailable() {
        guard let chatRuntimeBootstrap else { return }
        authenticatedHost.installAuthenticatedChat(
            chatRuntimeBootstrap,
            profileOpeningState: memberProfileOpeningState
        ) { [weak self] profileId in
            self?.presentAuthenticatedMemberProfile(profileId: profileId)
        }
    }

    /// Official stays available to anonymous visitors.  It is installed before session
    /// restoration and rebuilt after logout, while its Kotlin repository keeps every PostgREST
    /// read bearer-free and fails closed for writes.
    private func installPublicOfficialIfConfigured() {
        guard let officialRuntimeBootstrap else { return }
        authenticatedHost.installOfficialFactory { postId in
            let shareService = self.platformServices.services.share
            // The common Official surface exposes creation only once iOS has a real editor
            // route. This callback also fails closed after logout removes that factory.
            let onCreateOfficialPost = { [weak self] in
                guard let self, self.authenticatedHost.hasOfficialEditorFactory else { return }
                self.authenticatedHost.showOfficialEditor()
            }
            if let runtimeBootstrap = self.runtimeBootstrap, let configuration = self.runtimeConfiguration, self.hasValidatedAuthenticatedSession {
                return QuataOfficialViewControllerKt.QuataOfficialViewController(
                    dependencies: QuataOfficialViewControllerKt.iosAuthenticatedPostgrestOfficialHostDependencies(
                        configuration: IosOfficialRuntimeConfiguration(
                            supabaseUrl: configuration.supabaseUrl,
                            supabasePublishableKey: configuration.supabasePublishableKey
                        ),
                        authSession: runtimeBootstrap.authSessionForInteractiveLogin(),
                        officialPostId: postId,
                        shareService: shareService,
                        mediaViewerFactory: IosOfficialMediaBridge.shared,
                        currentUserId: nil,
                        onAuthRequired: { [weak self] in self?.authenticatedHost.presentAuthRequiredPrompt() },
                        onOpenUserProfile: { [weak self] id in self?.presentAuthenticatedMemberProfile(profileId: id) },
                        onCreateOfficialPost: onCreateOfficialPost,
                        canCreateOfficialPost: self.authenticatedHost.hasOfficialEditorFactory,
                        preferredLanguageTag: Locale.preferredLanguages.first,
                        profileOpeningState: self.memberProfileOpeningState,
                    )
                )
            }
            return QuataOfficialViewControllerKt.QuataOfficialViewController(
                dependencies: QuataOfficialViewControllerKt.createIosOfficialHostDependencies(
                    repository: officialRuntimeBootstrap.repository,
                    officialPostId: postId,
                    shareService: shareService,
                    mediaViewerFactory: IosOfficialMediaBridge.shared,
                    currentUserId: nil,
                    preferredLanguageTag: Locale.preferredLanguages.first,
                    onAuthRequired: { [weak self] in self?.authenticatedHost.presentAuthRequiredPrompt() },
                    onOpenUserProfile: { [weak self] id in self?.presentAuthenticatedMemberProfile(profileId: id) },
                    onCreateOfficialPost: onCreateOfficialPost,
                    canCreateOfficialPost: self.authenticatedHost.hasOfficialEditorFactory,
                    profileOpeningState: self.memberProfileOpeningState,
                ),
            )
        }
    }

    private func installAuthenticatedOfficialEditorIfAvailable() {
        guard let runtimeBootstrap, let configuration = runtimeConfiguration else { return }
        let services = platformServices.services
        authenticatedHost.installOfficialEditorFactory { [weak self] in
            QuataOfficialViewControllerKt.QuataOfficialEditorViewController(
                dependencies: QuataOfficialViewControllerKt.iosAuthenticatedOfficialEditorDependencies(
                    configuration: IosOfficialRuntimeConfiguration(
                        supabaseUrl: configuration.supabaseUrl,
                        supabasePublishableKey: configuration.supabasePublishableKey
                    ),
                    authSession: runtimeBootstrap.authSessionForInteractiveLogin(),
                    filePicker: services.filePicker,
                    videoThumbnails: services.videoThumbnails,
                    currentUserId: nil,
                    preferredLanguageTag: Locale.preferredLanguages.first,
                    onClose: { [weak self] in self?.authenticatedHost.showOfficial(postId: nil) },
                ),
            )
        }
    }

    /// Android exposes the inbox from the shared header without a session.  Its detail action
    /// delegates to `showChat`, which remains the single private-route gate.
    private func installNotificationsIfAvailable() {
        notificationsFactoryGeneration += 1
        let generation = notificationsFactoryGeneration
        UNUserNotificationCenter.current().getNotificationSettings { [weak self] settings in
            DispatchQueue.main.async {
                guard let self, self.notificationsFactoryGeneration == generation else { return }
                self.installNotifications(authorizationStatus: settings.authorizationStatus)
            }
        }
    }

    private func installNotifications(authorizationStatus: UNAuthorizationStatus) {
        let authenticated = hasValidatedAuthenticatedSession
        let bootstrap = authenticated ? notificationsRuntimeBootstrap : nil
        let repository = bootstrap?.repository()
            ?? IosAnonymousNotificationsRepositoryKt.createIosAnonymousNotificationsRepository()
        installAuthenticatedNotifications(
            IosNotificationsHostKt.createIosNotificationsHostDependencies(
                repository: repository,
                timestampNowMillis: Int64(Date().timeIntervalSince1970 * 1_000),
                notificationPermissionActionLabel: notificationPermissionActionLabel(for: authorizationStatus),
                onBack: { [weak self] in self?.authenticatedHost.showFeed(postId: nil) },
                onOpenConversation: { [weak self] conversationId in
                    self?.authenticatedHost.showChat(conversationId: conversationId, messageId: nil)
                },
                onNotificationPermissionAction: { [weak self] in
                    self?.performNotificationPermissionAction(for: authorizationStatus)
                },
                // Conversation navigation above is the real host action. This common callback
                // is observability only and must not manufacture a URL or a route.
                onHandleDeepLink: { _ in },
                canMutate: authenticated,
                onAuthenticationRequired: { [weak self] item in
                    self?.authenticatedHost.showChat(conversationId: item.conversationId, messageId: nil)
                },
                onDismissAuthenticationRequired: { [weak self] _ in
                    self?.authenticatedHost.presentAuthRequiredPrompt()
                },
            ),
        )
        if let bootstrap { installNotificationCountObserver(bootstrap) }
        if authenticatedHost.isNotificationsVisible {
            authenticatedHost.showNotifications()
        }
    }

    private func performNotificationPermissionAction(for status: UNAuthorizationStatus) {
        switch IosApnsAuthorization.permissionAction(status) {
        case .requestAuthorization:
            UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { [weak self] granted, error in
                if IosApnsAuthorization.shouldRequestRegistrationAfterPrompt(granted: granted, error: error) {
                    IosApnsLifecycleBridge.shared.requestRegistrationIfAuthorized()
                }
                DispatchQueue.main.async {
                    self?.installNotificationsIfAvailable()
                }
            }
        case .openSettings:
            guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
            UIApplication.shared.open(url)
        case .none:
            break
        }
    }

    private func notificationPermissionActionLabel(for status: UNAuthorizationStatus) -> String? {
        switch IosApnsAuthorization.permissionAction(status) {
        case .requestAuthorization: return "Permitir notificaciones"
        case .openSettings: return "Abrir ajustes"
        case .none: return nil
        }
    }

    private func installNotificationCountObserver(_ bootstrap: IosNotificationsRuntimeBootstrap) {
        notificationCountObserver?.close()
        let observer = bootstrap.notificationCountObserver()
        let observationID = UUID()
        notificationCountObservationID = observationID
        notificationCountObserver = observer
        observer.start { [weak self] count in
            // The Kotlin bridge collects on MainScope; dispatching also protects this state if a
            // future repository changes its upstream dispatcher.
            DispatchQueue.main.async {
                guard let self, self.notificationCountObservationID == observationID else { return }
                self.authenticatedHost.updateNotificationCount(Int(count.intValue))
            }
        }
    }

    private func closeNotificationCountObserver() {
        notificationCountObserver?.close()
        notificationCountObserver = nil
        // Ignore a value that was already queued on the main run loop before cancellation.
        notificationCountObservationID = UUID()
    }

    private func installAuthenticatedProfileSosIfAvailable() {
        guard
            let profileSosRuntimeBootstrap,
            let runtimeConfiguration,
            let runtimeBootstrap,
            let authRepository = createAuthRepository(configuration: runtimeConfiguration, bootstrap: runtimeBootstrap)
        else { return }
        let lifecycleHandler = IosAuthHostKt.createIosAuthAccountLifecycleHandler(repository: authRepository)
        let filePicker = platformServices.services.filePicker
        let appearancePreferences = self.appearancePreferences
        authenticatedHost.installProfileSosFactory { [weak self] in
            // Cuenta mounts the complete shared Compose host. SOS remains its in-context dialog;
            // it is not a substitute route for Profile on iOS.
            let dependencies = profileSosRuntimeBootstrap.profileHostDependencies(
                onLogout: { [weak self] in self?.authenticatedHost.performLogout() },
                onDeactivateAccount: { [weak self] in
                    self?.presentAccountLifecyclePrompt(action: "deactivate", handler: lifecycleHandler)
                },
                onDeleteAccountData: { [weak self] in
                    self?.presentAccountLifecyclePrompt(action: "delete", handler: lifecycleHandler)
                },
                filePicker: filePicker,
                touchFlowEnabled: appearancePreferences.touchFlowEnabled,
                themeModeStorageValue: appearancePreferences.themeModeStorageValue,
                onTouchFlowEnabledChange: { enabled in
                    appearancePreferences.setTouchFlowEnabled(enabled.boolValue)
                },
                onThemeModeStorageValueChange: { [weak self] value in
                    appearancePreferences.setThemeModeStorageValue(value)
                    if let window = self?.window {
                        appearancePreferences.applyTheme(to: window)
                    }
                },
            )
            return IosProfileHostKt.QuataProfileViewController(dependencies: dependencies)
        }
    }

    /// Communities mirrors Android's anonymous browser.  The KMP host receives a nullable
    /// current user; its chat/follow/profile actions ask the shared Auth gate when it is nil.
    private func installCommunitiesIfAvailable() {
        guard runtimeBootstrap != nil, profileSosRuntimeBootstrap != nil else { return }
        let authenticated = hasValidatedAuthenticatedSession
        guard let communitiesBootstrap = authenticated ? communitiesRuntimeBootstrap : publicCommunitiesRuntimeBootstrap else { return }
        memberProfilePreloader?.close()
        memberProfilePreloader = IosCommunityProfilePreloader(repository: communitiesBootstrap.repository)
        memberProfileOpeningState.clear()
        authenticatedHost.installCommunitiesFactory { [weak self] in
            IosNeighborhoodsHostKt.QuataNeighborhoodsViewController(
                dependencies: IosNeighborhoodsHostKt.createIosNeighborhoodsHostDependencies(
                    repository: communitiesBootstrap.repository,
                    currentUserId: communitiesBootstrap.restoredCurrentUserId(),
                    languageCode: Locale.current.languageCode ?? "en",
                    onOpenConversation: { [weak self] conversationId in
                        DispatchQueue.main.async {
                            guard let self else { return }
                            if self.hasValidatedAuthenticatedSession {
                                self.authenticatedHost.showChat(conversationId: conversationId, messageId: nil)
                            } else {
                                self.authenticatedHost.presentAuthRequiredPrompt()
                            }
                        }
                    },
                    onNavigateToProfile: { [weak self] profile in
                        DispatchQueue.main.async {
                            self?.presentAuthenticatedMemberProfile(profileId: profile.user.id, initialProfile: profile)
                        }
                    },
                    onAuthRequired: { [weak self] in
                        DispatchQueue.main.async {
                            self?.authenticatedHost.presentAuthRequiredPrompt()
                        }
                    },
                ),
            )
        }
    }

    /// Feed and Communities share the existing authenticated member-profile presentation.
    fileprivate func presentAuthenticatedMemberProfile(
        profileId: String,
        initialProfile: CommunityUserProfile? = nil
    ) {
        if initialProfile == nil {
            guard memberProfileOpeningState.begin(profileId: profileId) else { return }
            guard let memberProfilePreloader else {
                memberProfileOpeningState.finish(profileId: profileId)
                presentMemberProfileLoadFailure(message: nil)
                return
            }
            memberProfilePreloader.load(profileId: profileId) { [weak self] profile, errorMessage in
                DispatchQueue.main.async {
                    guard let self else { return }
                    self.memberProfileOpeningState.finish(profileId: profileId)
                    guard let profile else {
                        self.presentMemberProfileLoadFailure(message: errorMessage)
                        return
                    }
                    self.presentAuthenticatedMemberProfile(profileId: profileId, initialProfile: profile)
                }
            }
            return
        }
        let authenticated = hasValidatedAuthenticatedSession
        guard let communitiesBootstrap = authenticated ? communitiesRuntimeBootstrap : publicCommunitiesRuntimeBootstrap else { return }
        let onClose: () -> Void = { [weak self] in
            guard let self else { return }
            self.authenticatedHost.dismiss(animated: true)
        }
        let dependencies = IosNeighborhoodsHostKt.createIosCommunityProfileHostDependencies(
            repository: communitiesBootstrap.repository,
            profileId: profileId,
            initialProfile: initialProfile,
            currentUserId: communitiesBootstrap.restoredCurrentUserId(),
            languageCode: Locale.current.languageCode ?? "en",
            mediaFactory: IosFeedNativeMediaFactory.shared,
            documentOpener: platformServices.services.documentOpener,
            shareService: platformServices.services.share,
            onClose: onClose,
            onOpenConversation: { [weak self] conversationId in
                guard let self else { return }
                self.authenticatedHost.dismiss(animated: true) {
                    if self.hasValidatedAuthenticatedSession {
                        self.authenticatedHost.showChat(conversationId: conversationId, messageId: nil)
                    } else {
                        self.authenticatedHost.presentAuthRequiredPrompt()
                    }
                }
            },
            onAuthRequired: { [weak self] in self?.authenticatedHost.presentAuthRequiredPrompt() },
        )
        let controller = IosNeighborhoodsHostKt.QuataCommunityProfileViewController(
            dependencies: dependencies
        )
        controller.modalPresentationStyle = .fullScreen
        authenticatedHost.present(controller, animated: true)
    }

    private func presentMemberProfileLoadFailure(message: String?) {
        let alert = UIAlertController(
            title: NSLocalizedString("ios_member_profile_load_title", value: "Perfil", comment: ""),
            message: message ?? NSLocalizedString(
                "ios_member_profile_load_message",
                value: "No se pudo cargar el perfil. Inténtalo de nuevo.",
                comment: ""
            ),
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(
            title: NSLocalizedString("common_close", value: "Cerrar", comment: ""),
            style: .default
        ))
        authenticatedHost.present(alert, animated: true)
    }

    /// Composer is an authenticated in-app route. It receives real UIKit media services and the
    /// same renewable Keychain session used by the other authenticated iOS adapters.
    private func installAuthenticatedComposerIfAvailable() {
        guard let runtimeBootstrap, let configuration = runtimeConfiguration else { return }
        let services = platformServices.services
        let repository = ActorBoundPostComposerRepository(
            transport: IosPostComposerTransport(
                configuration: IosPostComposerRuntimeConfiguration(
                    supabaseUrl: configuration.supabaseUrl,
                    supabasePublishableKey: configuration.supabasePublishableKey,
                    wordpressBaseUrl: IosPublicRuntimeConfiguration.wordpressBaseUrl
                ),
                authSession: runtimeBootstrap.authSessionForInteractiveLogin()
            )
        )
        authenticatedHost.installComposerFactory { [weak self] in
            IosComposerHostKt.QuataComposerViewController(
                dependencies: IosComposerHostKt.createIosComposerHostDependencies(
                    repository: repository,
                    filePicker: services.filePicker,
                    cameraCapture: services.cameraCapture,
                    videoThumbnails: services.videoThumbnails,
                    languageTag: Locale.preferredLanguages.first,
                    onClose: { [weak self] in self?.authenticatedHost.showFeed(postId: nil) },
                ),
            )
        }
    }

    /// Settings owns only device-local appearance preferences. It does not create remote data or
    /// pretend that a server profile setting was saved.
    private func installSettings() {
        let appearancePreferences = self.appearancePreferences
        authenticatedHost.installSettingsFactory { [weak self] in
            IosSettingsHostKt.QuataSettingsViewController(
                dependencies: IosSettingsHostKt.createIosSettingsHostDependencies(
                    touchFlowEnabled: appearancePreferences.touchFlowEnabled,
                    themeModeStorageValue: appearancePreferences.themeModeStorageValue,
                    onTouchFlowEnabledChange: { enabled in
                        appearancePreferences.setTouchFlowEnabled(enabled.boolValue)
                    },
                    onThemeModeStorageValueChange: { [weak self] value in
                        appearancePreferences.setThemeModeStorageValue(value)
                        if let window = self?.window {
                            appearancePreferences.applyTheme(to: window)
                        }
                    },
                ),
            )
        }
    }

    /// What's New is a versioned local catalog. It does not require or manufacture backend state.
    private func installWhatsNewIfAvailable() {
        guard let whatsNewRuntimeBootstrap else { return }
        authenticatedHost.installWhatsNewFactory { [weak self] in
            IosWhatsNewRuntimeBootstrapKt.QuataIosManagedWhatsNewViewController(
                runtime: whatsNewRuntimeBootstrap,
                onClose: {
                    whatsNewRuntimeBootstrap.acknowledgeStartup { [weak self] in
                        self?.authenticatedHost.showFeed(postId: nil)
                    }
                },
            )
        }
        authenticatedHost.installReleaseHistoryFactory { [weak self] in
            IosWhatsNewRuntimeBootstrapKt.QuataIosReleaseHistoryViewController(
                runtime: whatsNewRuntimeBootstrap,
                onClose: { [weak self] in self?.authenticatedHost.showFeed(postId: nil) },
            )
        }
    }

    /// Evaluates the shared version/catalog state only after the public Feed is installed.
    /// The router refuses a late decision if a deep link or user action already left Feed.
    private func evaluateWhatsNewStartupIfAvailable() {
        guard let whatsNewRuntimeBootstrap else { return }
        whatsNewRuntimeBootstrap.evaluateStartup { [weak self] shouldShow in
            guard shouldShow.boolValue else { return }
            self?.authenticatedHost.showWhatsNewIfFeedVisible()
        }
    }

    private func presentProfileSosCapabilityNotice(_ message: String) {
        let alert = UIAlertController(
            title: NSLocalizedString("ios_profile_sos_capability_title", value: "SOS contacts", comment: ""),
            message: message,
            preferredStyle: .alert,
        )
        alert.addAction(UIAlertAction(title: NSLocalizedString("common_close", value: "Close", comment: ""), style: .default))
        authenticatedHost.present(alert, animated: true)
    }

    private func presentAccountLifecyclePrompt(
        action: String,
        handler: IosAuthAccountLifecycleHandler
    ) {
        let deleting = action == "delete"
        let alert = UIAlertController(
            title: deleting
                ? NSLocalizedString("ios_profile_delete_title", value: "Delete account data", comment: "")
                : NSLocalizedString("ios_profile_deactivate_title", value: "Deactivate account", comment: ""),
            message: NSLocalizedString(
                "ios_profile_account_password_message",
                value: "Enter your password to confirm this account operation.",
                comment: "",
            ),
            preferredStyle: .alert,
        )
        alert.addTextField { field in
            field.isSecureTextEntry = true
            field.placeholder = NSLocalizedString("auth_password", value: "Password", comment: "")
        }
        let deleteWord: String
        switch Locale.current.languageCode {
        case "es": deleteWord = "ELIMINAR"
        case "fr": deleteWord = "SUPPRIMER"
        default: deleteWord = "DELETE"
        }
        if deleting {
            alert.addTextField { field in
                field.placeholder = String(format: NSLocalizedString("ios_profile_delete_type", value: "Type %@ to confirm", comment: ""), deleteWord)
                field.autocapitalizationType = .allCharacters
            }
        }
        alert.addAction(UIAlertAction(title: NSLocalizedString("common_cancel", value: "Cancel", comment: ""), style: .cancel))
        alert.addAction(UIAlertAction(
            title: NSLocalizedString("common_continue", value: "Continue", comment: ""),
            style: deleting ? .destructive : .default,
        ) { [weak self, weak alert] _ in
            let password = alert?.textFields?.first?.text ?? ""
            if deleting, alert?.textFields?.dropFirst().first?.text != deleteWord {
                self?.presentProfileSosCapabilityNotice(
                    NSLocalizedString("ios_profile_delete_confirmation_invalid", value: "The deletion confirmation does not match.", comment: "")
                )
                return
            }
            handler.perform(
                action: action,
                password: password,
                onSuccess: { [weak self] in
                    DispatchQueue.main.async { self?.authenticatedHost.performLogout() }
                },
                onFailure: { [weak self] reason in
                    DispatchQueue.main.async {
                        self?.presentProfileSosCapabilityNotice(reason)
                    }
                },
            )
        })
        authenticatedHost.present(alert, animated: true)
    }

    private func presentCommunitiesCapabilityNotice(_ message: String) {
        let alert = UIAlertController(
            title: NSLocalizedString("ios_communities_capability_title", value: "Communities", comment: ""),
            message: message,
            preferredStyle: .alert,
        )
        alert.addAction(UIAlertAction(title: NSLocalizedString("common_close", value: "Close", comment: ""), style: .default))
        authenticatedHost.present(alert, animated: true)
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
        let logoutHandler = IosAuthHostKt.createIosAuthLogoutHandler(repository: repository)
        authenticatedHost.installLogoutAction(
            { completed in logoutHandler.logout(onCompleted: completed) },
            onLoggedOut: { [weak self] in
                // The shared operation has already cleared the Keychain session. Rebuild only
                // the public read-only browsers and login entry point; no private factory is
                // retained as an anonymous destination.
                self?.hasValidatedAuthenticatedSession = false
                self?.closeNotificationCountObserver()
                self?.installPublicFeedIfConfigured()
                self?.installPublicOfficialIfConfigured()
                self?.installNotificationsIfAvailable()
                self?.installAuthenticationIfConfigured()
            },
        )
        let dependencies = IosAuthHostKt.createIosAuthHostDependencies(
            repository: repository,
            languageCode: Locale.current.languageCode ?? "en",
            onLoginSuccess: { [weak self] in
                DispatchQueue.main.async {
                    self?.authenticatedHost.finishAuthentication {
                        self?.hasValidatedAuthenticatedSession = true
                        _ = self?.installRestoredFeedSessionIfAvailable()
                    }
                }
            },
        )
        authenticatedHost.installAuthentication(dependencies)
    }

    private func createAuthRepository(
        configuration: IosFeedRuntimeConfiguration,
        bootstrap _: IosFeedRuntimeBootstrap,
    ) -> AuthRepository? {
        guard let renewableAuthSession else { return nil }
        return IosAuthRepositoryKt.createIosAuthRepository(
            configuration: authRuntimeConfiguration(from: configuration),
            session: renewableAuthSession,
        )
    }

    private func authRuntimeConfiguration(from configuration: IosFeedRuntimeConfiguration) -> IosAuthRuntimeConfiguration {
        IosPublicRuntimeConfiguration.authConfiguration(from: configuration)
    }
}

/// Deterministic UI-test-only destination surface. It does not parse URLs itself: the test
/// launch reaches it exclusively through `IosDeepLinkDispatcher` and
/// `IosAuthenticatedRouteDispatcher`, while the route content remains network-free UIKit.
private final class IosDeterministicDeepLinkFixtureRouter: UIViewController, IosAuthenticatedRouteHost {
    override func loadView() {
        view = UIView()
    }

    func showFeed(postId: String?) {
        show(identifier: "quata-ios-feed-host", label: "Quata iOS Feed")
    }

    func showChat(conversationId: String, messageId: String?) {
        show(identifier: "quata-ios-chat-host", label: "Quata iOS Chat")
    }

    func showOfficial(postId: String?) {
        show(identifier: "quata-ios-official-host", label: "Quata iOS Official")
    }

    func showNotifications() {
        show(identifier: "quata-ios-notifications-host", label: "Quata iOS Notifications")
    }

    func showProfileSos() {
        show(identifier: "quata-ios-profile-sos-host", label: "Quata iOS Profile and SOS")
    }

    func showCommunities() {
        show(identifier: "quata-ios-communities-host", label: "Quata iOS Communities")
    }

    func showComposer() {
        show(identifier: "quata-ios-composer-host", label: "Quata iOS Composer")
    }

    func showSettings() {
        show(identifier: "quata-ios-settings-host", label: "Quata iOS Settings")
    }

    func showWhatsNew() {
        show(identifier: "quata-ios-whats-new-host", label: "Quata iOS What's New")
    }

    func showReleaseHistory() {
        show(identifier: "quata-ios-release-history-host", label: "Quata iOS Release History")
    }

    private func show(identifier: String, label: String) {
        view.accessibilityIdentifier = identifier
        view.accessibilityLabel = label
    }
}

/// Keeps a Compose/Skia dialog transparent after its native render view is mounted.  A one-shot
/// background change is too early because Compose inserts that view on a later layout pass.
final class IosTransparentComposeOverlayController: UIViewController {
    private let content: UIViewController

    init(content: UIViewController) {
        self.content = content
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { nil }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
        view.isOpaque = false
        addChild(content)
        content.view.frame = view.bounds
        content.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(content.view)
        content.didMove(toParent: self)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        content.view.frame = view.bounds
        clearNativeBackgrounds(in: view)
        // Compose may mount its rendering view immediately after this pass.
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.clearNativeBackgrounds(in: self.view)
        }
    }

    private func clearNativeBackgrounds(in candidate: UIView) {
        candidate.backgroundColor = .clear
        candidate.isOpaque = false
        candidate.subviews.forEach { clearNativeBackgrounds(in: $0) }
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
    // Installed only once iOS supplies the complete product editor dependencies. There is no
    // Composer or empty-controller fallback for Official publication.
    private var officialEditorFactory: (() -> UIViewController)?
    private var notificationsFactory: (() -> UIViewController)?
    private var profileSosFactory: (() -> UIViewController)?
    private var communitiesFactory: (() -> UIViewController)?
    private var composerFactory: (() -> UIViewController)?
    private var settingsFactory: (() -> UIViewController)?
    private var whatsNewFactory: (() -> UIViewController)?
    private var releaseHistoryFactory: (() -> UIViewController)?
    enum AuthenticationEntry { case login, registration }
    private var authenticationFactory: ((AuthenticationEntry) -> UIViewController)?
    private var authRequiredPromptFactory: (() -> UIViewController)?
    private var authRequiredPromptVisible = false
    private var authModalTransitionsAnimated = true
    /// Retains the selected Auth entry until UIKit has completed dismissing the common prompt.
    private var pendingAuthenticationEntry: AuthenticationEntry?
    private var nextAuthPromptPresentationCompletionForTesting: (() -> Void)?
    private var nextAuthenticationPresentationCompletionForTesting: (() -> Void)?
    private var logoutAction: ((@escaping () -> Void) -> Void)?
    private var onLoggedOut: (() -> Void)?
    private var isLoggingOut = false
    private var pendingRoute: PendingRoute?
    private var visibleRoute: PendingRoute?
    var isNotificationsVisible: Bool {
        if case .notifications? = visibleRoute { return true }
        return false
    }
    private var hasAuthenticatedSession = false
    private var hasPublicFeed = false
    private lazy var primaryNavigationHost = IosPrimaryNavigationHost(
        initialSelectedRoute: "feed",
        onRouteSelected: { [weak self] route in self?.openPrimaryRoute(route) },
    )
    private lazy var primaryNavigationController = primaryNavigationHost.viewController()
    /// Feed browsing is public, but the shared application shell is not authenticated-only.
    /// Android keeps this chrome visible for anonymous Feed/Official routes too; the callbacks
    /// below decide whether a selected destination must first acquire a session.
    private var isSharedShellInstalled = false
    private lazy var authenticatedTopChromeHost = IosAuthenticatedTopChromeHost(
        // iOS has no About route equivalent yet. Keep this callback explicit instead of mapping
        // the shared Q̈ mark to an unrelated release-history route.
        onLogoClick: {},
        onNotificationsClick: { [weak self] in self?.showNotifications() },
        onSosClick: { [weak self] in self?.showProfileSos() },
    )
    private lazy var authenticatedTopChromeController = authenticatedTopChromeHost.viewController()
    private var isAuthenticatedTopChromeInstalled = false

    /// Keeps the shared Compose chrome as the only owner of authenticated badge UI.
    func updateNotificationCount(_ count: Int) {
        authenticatedTopChromeHost.updateNotificationCount(count: Int32(clamping: count))
    }
    private lazy var routeMenuButton: UIButton = {
        var configuration = UIButton.Configuration.filled()
        configuration.image = UIImage(systemName: "line.3.horizontal")
        configuration.cornerStyle = .capsule
        configuration.baseForegroundColor = .white
        configuration.baseBackgroundColor = .systemOrange
        let button = UIButton(configuration: configuration)
        button.accessibilityIdentifier = "quata-ios-authenticated-route-menu"
        button.accessibilityLabel = NSLocalizedString(
            "ios_authenticated_route_menu",
            value: "Abrir secciones",
            comment: "",
        )
        button.addTarget(self, action: #selector(presentAuthenticatedRouteMenu), for: .touchUpInside)
        button.translatesAutoresizingMaskIntoConstraints = false
        button.isHidden = true
        return button
    }()

    enum PendingRoute {
        case feed(postId: String?)
        case chat(conversationId: String?, messageId: String?)
        case official(postId: String?)
        case officialEditor
        case notifications
        case profileSos
        case communities
        case composer
        case settings
        case whatsNew
        case releaseHistory

        var isAuthenticationRequired: Bool {
            switch self {
            case .feed, .official, .whatsNew, .releaseHistory:
                return false
            // Android opens Communities and Notifications anonymously; individual detail
            // actions retain their own route/mutation gates.
            case .chat, .officialEditor, .profileSos, .composer, .settings:
                return true
            case .communities, .notifications: return false
        }
    }
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
        view.addSubview(routeMenuButton)
        NSLayoutConstraint.activate([
            routeMenuButton.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -16),
            // This is a secondary-route affordance, never part of the shared top chrome. Keep it
            // below safeTop + 68 so it cannot occupy the common SOS position.
            routeMenuButton.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 80),
        ])
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        guard isSharedShellInstalled else {
            displayedController?.view.frame = view.bounds
            return
        }
        let hidesPrimaryNavigation = shouldHidePrimaryNavigationForVisibleRoute()
        let layout = IosAuthenticatedShellLayout.frames(
            bounds: view.bounds,
            safeAreaInsets: view.safeAreaInsets,
            includesBottomNavigation: !hidesPrimaryNavigation
        )
        displayedController?.view.frame = layout.content
        authenticatedTopChromeController.view.frame = layout.topChrome
        primaryNavigationController.view.isHidden = hidesPrimaryNavigation
        if !hidesPrimaryNavigation {
            primaryNavigationController.view.frame = layout.bottomNavigation
        }
    }

    func installAuthenticatedFeed(_ dependencies: IosFeedHostDependencies) {
        installFeedFactory { _ in QuataFeedViewControllerKt.QuataFeedViewController(dependencies: dependencies) }
    }

    /// Public Feed is a real app route, not a full-screen fallback. Keep the common top chrome
    /// and primary navigation installed exactly as Android does; private destinations remain
    /// gated by `PendingRoute.isAuthenticationRequired` in `route(_:)`.
    func installPublicFeed(_ factory: @escaping (String?) -> UIViewController) {
        guard !hasAuthenticatedSession else { return }
        feedFactory = factory
        hasPublicFeed = true
        installSharedShellIfNeeded()
        routeMenuButton.isHidden = true
        renderPendingRouteIfPossible()
        if pendingRoute == nil {
            showFeed(postId: nil)
        } else if pendingRoute?.isAuthenticationRequired == true, let feedController = feedFactory?(nil) {
            // A protected deep link can arrive before the public runtime and Auth factories are
            // composed. Keep that destination pending, but make the public Feed (and its shell)
            // visible rather than leaving the migration placeholder on screen.
            showRouteController(feedController, route: .feed(postId: nil))
        }
    }

    /// Installs the authenticated root route. Kept separate from dependency composition so the
    /// UIKit routing contract can be verified without credentials, network traffic or a backend.
    func installFeedFactory(_ factory: @escaping (String?) -> UIViewController) {
        feedFactory = factory
        hasAuthenticatedSession = true
        hasPublicFeed = false
        if authRequiredPromptVisible {
            dismissAuthRequiredPrompt()
        }
        installSharedShellIfNeeded()
        routeMenuButton.isHidden = false
        let hadPendingRoute = pendingRoute != nil
        renderPendingRouteIfPossible()
        if !hadPendingRoute {
            showFeed(postId: nil)
        } else if pendingRoute != nil, let feedController = feedFactory?(nil) {
            // A Chat/Official route can legitimately wait for its own real repository. Keep that
            // pending identifier while returning the authenticated user to the real Feed surface.
            showRouteController(feedController, route: .feed(postId: nil))
        }
    }

    func installAuthentication(_ dependencies: IosAuthHostDependencies) {
        installAuthenticationEntryFactory { entry in
            switch entry {
            case .login: return IosAuthHostKt.QuataAuthViewController(dependencies: dependencies)
            case .registration: return IosAuthHostKt.QuataRegistrationViewController(dependencies: dependencies)
            }
        }
        authRequiredPromptFactory = { [weak self] in
            IosAuthHostKt.QuataAuthRequiredDialogViewController(
                languageCode: Locale.current.languageCode ?? "en",
                onDismiss: { self?.dismissAuthRequiredPrompt() },
                onCreateAccount: { self?.openRegistrationFromAuthRequiredPrompt() },
                onLogin: { self?.openLoginFromAuthRequiredPrompt() },
            )
        }
    }

    /// The actual sign-out operation stays in the shared Auth module. UIKit only supplies the
    /// confirmation UX and replaces authenticated route factories after its completion.
    func installLogoutAction(
        _ action: @escaping (@escaping () -> Void) -> Void,
        onLoggedOut: @escaping () -> Void,
    ) {
        logoutAction = action
        self.onLoggedOut = onLoggedOut
    }

    /// Installs the authenticated entry point without granting a session. Keeping this UIKit
    /// factory boundary explicit lets a private deep link show login while retaining its route
    /// until the real authenticated Feed/factory composition is available.
    private func installAuthenticationEntryFactory(_ factory: @escaping (AuthenticationEntry) -> UIViewController) {
        authenticationFactory = factory
    }

    /// Compatibility seam for router-only XCTest fixtures. Production uses `installAuthentication`
    /// and therefore the real common prompt and distinct Login/Register entry points.
    func installAuthenticationFactory(_ factory: @escaping () -> UIViewController) {
        installAuthenticationEntryFactory { _ in factory() }
    }

    /// XCTest seam only; the production path above always installs the common Compose dialog.
    func installAuthRequiredPromptFactory(_ factory: @escaping () -> UIViewController) {
        authRequiredPromptFactory = factory
    }

    /// XCTest keeps the real UIWindow/presentation lifecycle but removes CoreAnimation timing.
    /// Production never calls this and retains the normal animated modal transitions.
    func disableAuthModalAnimationsForTesting() {
        authModalTransitionsAnimated = false
    }

    /// XCTest synchronization seam. Waiting for UIKit's actual `present` completion is stable
    /// across simulator architectures; polling transition flags is not on Xcode 26.3.
    func onNextAuthPromptPresentedForTesting(_ completion: @escaping () -> Void) {
        nextAuthPromptPresentationCompletionForTesting = completion
    }

    /// XCTest synchronization seam for the second half of the prompt -> Auth transition.
    /// UIKit invokes this only after the full-screen Login/Register controller is presented.
    func onNextAuthenticationPresentedForTesting(_ completion: @escaping () -> Void) {
        nextAuthenticationPresentationCompletionForTesting = completion
    }

    /// Android's capability gate: keep the public Feed visible and show common Compose copy.
    func presentAuthRequiredPrompt() {
        guard !hasAuthenticatedSession, !authRequiredPromptVisible, let authRequiredPromptFactory else { return }
        authRequiredPromptVisible = true
        let prompt = IosTransparentComposeOverlayController(content: authRequiredPromptFactory())
        prompt.modalPresentationStyle = .overFullScreen
        // ComposeUIViewController owns a Skia child view whose default opaque background would
        // otherwise hide the public route beneath this dialog. Make only the hosting tree
        // transparent; AlertDialog still draws its own scrim/card in common Compose.
        makeComposeOverlayTransparent(prompt.view)
        prompt.view.accessibilityIdentifier = "quata-ios-auth-required-dialog"
        present(prompt, animated: authModalTransitionsAnimated) { [weak self] in
            let completion = self?.nextAuthPromptPresentationCompletionForTesting
            self?.nextAuthPromptPresentationCompletionForTesting = nil
            // Resume XCTest after UIKit has unwound the presentation completion stack. A real
            // user cannot select Login/Register re-entrantly from inside this callback either.
            DispatchQueue.main.async { completion?() }
        }
    }

    /// Common Compose invokes this from AlertDialog.onDismissRequest (scrim/back dismissal).
    /// Internal visibility keeps that callback lifecycle directly testable on the UIKit host.
    func dismissAuthRequiredPrompt(completion: (() -> Void)? = nil) {
        authRequiredPromptVisible = false
        guard presentedViewController?.view.accessibilityIdentifier == "quata-ios-auth-required-dialog" else {
            completion?()
            return
        }
        // UIKit requires the presenting controller to own this transition. Dismissing from the
        // Compose wrapper itself can leave the over-full-screen presentation attached and its
        // completion never advances to Login/Register. The completion (plus one run-loop turn)
        // keeps the following full-screen presentation serialized.
        dismiss(animated: authModalTransitionsAnimated) { [weak self] in
            self?.authRequiredPromptVisible = false
            DispatchQueue.main.async { completion?() }
        }
    }

    private func makeComposeOverlayTransparent(_ view: UIView) {
        view.backgroundColor = .clear
        view.isOpaque = false
        view.subviews.forEach(makeComposeOverlayTransparent)
    }

    func presentLoginIfAvailable() {
        presentAuthRequiredPrompt()
    }

    /// Capability-dialog choices intentionally leave the shared shell before displaying Auth.
    /// Keeping these transitions explicit also makes the real modal lifecycle verifiable without
    /// replacing the common Compose prompt with a UIKit imitation.
    func openLoginFromAuthRequiredPrompt() {
        queueAuthenticationPresentation(.login)
    }

    func openRegistrationFromAuthRequiredPrompt() {
        queueAuthenticationPresentation(.registration)
    }

    private func queueAuthenticationPresentation(_ entry: AuthenticationEntry) {
        guard !hasAuthenticatedSession else { return }
        pendingAuthenticationEntry = entry
        dismissAuthRequiredPrompt { [weak self] in
            DispatchQueue.main.async { self?.drainPendingAuthenticationPresentation() }
        }
    }

    private func drainPendingAuthenticationPresentation() {
        guard !hasAuthenticatedSession, let entry = pendingAuthenticationEntry else { return }
        guard presentedViewController == nil else { return }
        pendingAuthenticationEntry = nil
        presentAuthentication(entry)
    }

    private func presentAuthentication(_ entry: AuthenticationEntry) {
        guard !hasAuthenticatedSession, let authenticationFactory else { return }
        let controller = authenticationFactory(entry)
        switch entry {
        case .login:
            controller.modalPresentationStyle = .fullScreen
        case .registration:
            // Keep the Compose shell attached while Registration covers it. Removing the shell
            // during a full-screen transition can synchronously tear down its Metal surface.
            controller.modalPresentationStyle = .overFullScreen
            controller.view.backgroundColor = .systemBackground
            controller.view.isOpaque = true
        }
        controller.view.accessibilityIdentifier = "quata-ios-auth-host"
        controller.view.accessibilityLabel = "Quata iOS authentication"
        controller.view.isAccessibilityElement = false
        // Full-screen Auth deliberately has no app chrome/rail.  It still needs an explicit
        // iOS back affordance because a full-screen modal cannot be reliably swipe-dismissed.
        let close = UIButton(type: .system)
        close.setImage(UIImage(systemName: "xmark"), for: .normal)
        close.tintColor = .secondaryLabel
        close.accessibilityIdentifier = "quata-ios-auth-close"
        close.accessibilityLabel = NSLocalizedString("common_close", value: "Cerrar", comment: "")
        close.translatesAutoresizingMaskIntoConstraints = false
        close.addTarget(self, action: #selector(cancelAuthentication), for: .touchUpInside)
        controller.view.addSubview(close)
        NSLayoutConstraint.activate([
            close.leadingAnchor.constraint(equalTo: controller.view.safeAreaLayoutGuide.leadingAnchor, constant: 16),
            close.topAnchor.constraint(equalTo: controller.view.safeAreaLayoutGuide.topAnchor, constant: 12),
            close.widthAnchor.constraint(equalToConstant: 44),
            close.heightAnchor.constraint(equalToConstant: 44),
        ])
        present(controller, animated: authModalTransitionsAnimated) { [weak self] in
            let completion = self?.nextAuthenticationPresentationCompletionForTesting
            self?.nextAuthenticationPresentationCompletionForTesting = nil
            DispatchQueue.main.async { completion?() }
        }
    }

    /// Cancelling Auth abandons the protected intent and restores the anonymous Feed shell.
    @objc private func cancelAuthentication() {
        pendingRoute = nil
        dismiss(animated: authModalTransitionsAnimated) { [weak self] in
            guard let self, !self.hasAuthenticatedSession else { return }
            self.showFeed(postId: nil)
        }
    }

    /// The shared Auth host has completed a successful login.  Remove the full-screen Auth
    /// product first, then restore the pending private route in the rebuilt app shell.
    func finishAuthentication(_ completion: @escaping () -> Void) {
        guard presentedViewController?.view.accessibilityIdentifier == "quata-ios-auth-host" else {
            completion()
            return
        }
        dismiss(animated: authModalTransitionsAnimated, completion: completion)
    }

    /// XCTest-only controller factories for deterministic launcher and URL routing checks.
    /// Production callers only install factories backed by the real authenticated KMP runtime.
    func installUiTestRoutes() {
        let fixture: () -> UIViewController = { UIViewController() }
        feedFactory = { _ in fixture() }
        chatFactory = { _, _ in fixture() }
        officialFactory = { _ in fixture() }
        notificationsFactory = fixture
        profileSosFactory = fixture
        communitiesFactory = fixture
        composerFactory = fixture
        settingsFactory = fixture
        whatsNewFactory = fixture
        releaseHistoryFactory = fixture
        hasAuthenticatedSession = true
        routeMenuButton.isHidden = false
        installSharedShellIfNeeded()
    }

    /// The shared Feed can already expose its Conversations affordance, but the authenticated
    /// iOS Chat repository/navigation host is not wired yet. Keep that action explicit rather
    /// than routing to an invented screen or silently swallowing the tap.
    func presentChatsMigrationNotice() {
        let alert = UIAlertController(
            title: NSLocalizedString("ios_chat_host_pending_title", value: "Conversaciones", comment: ""),
            message: NSLocalizedString(
                "ios_chat_host_pending_message",
                value: "Las conversaciones estar\u{00E1}n disponibles cuando se complete el host autenticado de iOS.",
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
    /// The common Chat host receives the optional deep-link target, paging authenticated history
    /// until it resolves or history is exhausted. A missing target keeps the conversation open.
    func installAuthenticatedChat(
        _ bootstrap: IosChatRuntimeBootstrap,
        profileOpeningState: IosMemberProfileOpeningState,
        onOpenProfile: @escaping (String) -> Void
    ) {
        let services = platformServices.services
        let chatAttachmentConfiguration: IosChatRuntimeConfiguration? = IosPublicRuntimeConfiguration
            .feedConfiguration()
            .map {
                IosChatRuntimeConfiguration(
                    supabaseUrl: $0.supabaseUrl,
                    supabasePublishableKey: $0.supabasePublishableKey,
                )
            }
        let chatAttachmentSession = bootstrap.authSessionForInteractiveLogin()
        // Chat remains installable for an already constructed bootstrap even when the host has no
        // public runtime metadata. Only remote preview degrades in that case.
        let attachmentPreviewService: IosChatAttachmentPreviewService? = {
            guard let chatAttachmentConfiguration else {
                return nil
            }
            // Reuse the Keychain-backed session from Chat; Quick Look only receives a local
            // temporary file after the Kotlin boundary validates and downloads the attachment.
            return IosChatAttachmentPreviewService(
                configuration: chatAttachmentConfiguration,
                authSession: chatAttachmentSession,
                documentOpener: services.documentOpener,
                downloader: IosChatAttachmentDownloader(
                    configuration: chatAttachmentConfiguration,
                    authSession: chatAttachmentSession,
                ),
            )
        }()
        installChatFactory { [weak self] conversationId, messageId in
            // AVAudioPlayer accepts local files only. Resolve message-controlled remote audio
            // through the authenticated Chat downloader first, so a URL can never be coerced
            // into a file path or escape the deployment/bucket allow-list.
            let chatAudioPlayer: AudioPlayerService = chatAttachmentConfiguration.map {
                IosChatAttachmentAudioPlayerService(
                    delegate: services.audioPlayer,
                    configuration: $0,
                    authSession: chatAttachmentSession,
                )
            } ?? services.audioPlayer
            let dependencies = bootstrap.hostDependencies(
                audioPlayer: chatAudioPlayer,
                audioRecorder: services.audioRecorder,
                filePicker: services.filePicker,
                cameraCapture: services.cameraCapture,
                mediaViewerFactory: IosChatNativeMediaFactory.shared,
                conversationId: conversationId,
                focusedMessageId: messageId,
                onFocusedMessageHandled: { [weak self] in
                    if let conversationId {
                        self?.showChat(conversationId: conversationId, messageId: nil)
                    } else {
                        self?.openChatList()
                    }
                },
                languageTag: Locale.preferredLanguages.first ?? Locale.current.identifier,
                onOpenConversation: { [weak self] conversationId in
                    self?.showChat(conversationId: conversationId, messageId: nil)
                },
                onOpenMessageConversation: { [weak self] conversationId, messageId in
                    self?.showChat(conversationId: conversationId, messageId: messageId)
                },
                onBackToList: { [weak self] in
                    self?.openChatList()
                },
                onOpenAttachment: { [weak self] attachment in
                    guard let attachmentPreviewService,
                          attachmentPreviewService.supportsQuickLook(attachment: attachment) else {
                        self?.presentRemoteAttachmentPreviewUnsupportedNotice()
                        return
                    }
                    attachmentPreviewService.openRemoteAttachmentOrThrow(attachment: attachment) { error in
                        guard error != nil else { return }
                        DispatchQueue.main.async {
                            self?.presentRemoteAttachmentDownloadFailureNotice()
                        }
                    }
                },
                onOpenExternalLink: { value in
                    guard let url = URL(string: value),
                          ["https", "http"].contains(url.scheme?.lowercased() ?? "") else { return }
                    UIApplication.shared.open(url)
                },
                onOpenAvatar: { profileId in
                    DispatchQueue.main.async { onOpenProfile(profileId) }
                },
                profileOpeningState: profileOpeningState,
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

    private func presentRemoteAttachmentPreviewUnsupportedNotice() {
        let alert = UIAlertController(
            title: NSLocalizedString("ios_chat_attachment_pending_title", value: "Adjunto", comment: ""),
            message: NSLocalizedString(
                "ios_chat_attachment_preview_unsupported_message",
                value: "Este tipo de adjunto todavía no se puede previsualizar en iOS.",
                comment: "",
            ),
            preferredStyle: .alert,
        )
        alert.addAction(UIAlertAction(title: NSLocalizedString("common_close", value: "Cerrar", comment: ""), style: .default))
        present(alert, animated: true)
    }

    private func presentRemoteAttachmentDownloadFailureNotice() {
        let alert = UIAlertController(
            title: NSLocalizedString("ios_chat_attachment_pending_title", value: "Adjunto", comment: ""),
            message: NSLocalizedString(
                "ios_chat_attachment_download_failed_message",
                value: "No se ha podido descargar el adjunto de forma segura. Inténtalo de nuevo.",
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

    /// Registers the real common Official editor host only after its dependencies exist.
    func installOfficialEditorFactory(_ factory: @escaping () -> UIViewController) {
        officialEditorFactory = factory
        renderPendingRouteIfPossible()
    }

    var hasOfficialEditorFactory: Bool {
        officialEditorFactory != nil
    }

    func installNotificationsFactory(_ factory: @escaping () -> UIViewController) {
        notificationsFactory = factory
        renderPendingRouteIfPossible()
    }

    func installProfileSosFactory(_ factory: @escaping () -> UIViewController) {
        profileSosFactory = factory
        renderPendingRouteIfPossible()
    }

    func installCommunitiesFactory(_ factory: @escaping () -> UIViewController) {
        communitiesFactory = factory
        renderPendingRouteIfPossible()
    }

    func installComposerFactory(_ factory: @escaping () -> UIViewController) {
        composerFactory = factory
        renderPendingRouteIfPossible()
    }

    func installSettingsFactory(_ factory: @escaping () -> UIViewController) {
        settingsFactory = factory
        renderPendingRouteIfPossible()
    }

    func installWhatsNewFactory(_ factory: @escaping () -> UIViewController) {
        whatsNewFactory = factory
        renderPendingRouteIfPossible()
    }

    func installReleaseHistoryFactory(_ factory: @escaping () -> UIViewController) {
        releaseHistoryFactory = factory
        renderPendingRouteIfPossible()
    }

    func showFeed(postId: String?) { route(.feed(postId: postId)) }

    func showChat(conversationId: String, messageId: String?) {
        route(.chat(conversationId: conversationId, messageId: messageId))
    }

    func showOfficial(postId: String?) { route(.official(postId: postId)) }

    func showOfficialEditor() { route(.officialEditor) }

    func showNotifications() { route(.notifications) }

    func showProfileSos() { route(.profileSos) }

    func showCommunities() { route(.communities) }

    /// Composer deliberately has no public deep-link contract. The launcher opens it only after
    /// a Keychain-backed authenticated session has supplied its real platform adapters.
    func showComposer() { route(.composer) }

    func showSettings() { route(.settings) }

    func showWhatsNew() { route(.whatsNew) }

    /// Startup evaluation is asynchronous. Never replace a route selected while it was running.
    @discardableResult
    func showWhatsNewIfFeedVisible() -> Bool {
        guard case .feed? = visibleRoute, whatsNewFactory != nil else { return false }
        showWhatsNew()
        return true
    }

    func showReleaseHistory() { route(.releaseHistory) }

    func openChatList() { route(.chat(conversationId: nil, messageId: nil)) }

    private func openPrimaryRoute(_ route: String) {
        switch route {
        case "neighborhoods": showCommunities()
        case "conversations": openChatList()
        case "official": showOfficial(postId: nil)
        case "feed": showFeed(postId: nil)
        case "profile": showProfileSos()
        case "composer": showComposer()
        default: break
        }
    }

    /// Feature back actions always return to the real authenticated Feed root. A route is never
    /// fabricated if the session/root factory is not ready yet.
    func returnToAuthenticatedFeed() {
        guard hasAuthenticatedSession, feedFactory != nil else { return }
        showFeed(postId: nil)
    }

    /// Lifecycle re-entry must retain the current route (or its deferred target). This is an
    /// intentionally idempotent UIKit boundary used after foregrounding, not a data refresh.
    func restoreRouteAfterForeground() {
        renderPendingRouteIfPossible()
    }

    @objc private func presentAuthenticatedRouteMenu() {
        guard hasAuthenticatedSession else { return }
        let sheet = UIAlertController(
            title: NSLocalizedString("ios_authenticated_secondary_actions_title", value: "Acciones", comment: ""),
            message: nil,
            preferredStyle: .actionSheet,
        )
        populateAuthenticatedRouteMenu(sheet)
        present(sheet, animated: true)
    }

    /// Keeps the authenticated menu honest: an item is present only after its real KMP factory
    /// has been installed. Internal visibility lets XCTest verify that local-only destinations
    /// remain discoverable without introducing a Swift replacement screen.
    func populateAuthenticatedRouteMenu(_ sheet: UIAlertController) {
        if notificationsFactory != nil {
            sheet.addAction(UIAlertAction(title: "Notificaciones", style: .default) { [weak self] _ in self?.showNotifications() })
        }
        if composerFactory != nil {
            sheet.addAction(UIAlertAction(title: "Crear publicación", style: .default) { [weak self] _ in self?.showComposer() })
        }
        if officialEditorFactory != nil {
            sheet.addAction(UIAlertAction(title: "Crear comunicado", style: .default) { [weak self] _ in self?.showOfficialEditor() })
        }
        if settingsFactory != nil {
            sheet.addAction(UIAlertAction(title: "Ajustes", style: .default) { [weak self] _ in self?.showSettings() })
        }
        if whatsNewFactory != nil {
            sheet.addAction(UIAlertAction(title: "Novedades", style: .default) { [weak self] _ in self?.showWhatsNew() })
        }
        if releaseHistoryFactory != nil {
            sheet.addAction(UIAlertAction(title: "Acerca de Quata", style: .default) { [weak self] _ in self?.showReleaseHistory() })
        }
        if logoutAction != nil {
            sheet.addAction(UIAlertAction(
                title: NSLocalizedString("ios_logout_action", value: "Cerrar sesión", comment: ""),
                style: .destructive,
            ) { [weak self] _ in
                self?.presentLogoutConfirmation()
            })
        }
        sheet.addAction(UIAlertAction(title: "Cerrar", style: .cancel))
    }

    private func presentLogoutConfirmation() {
        guard logoutAction != nil, !isLoggingOut else { return }
        let alert = UIAlertController(
            title: NSLocalizedString("ios_logout_confirmation_title", value: "Cerrar sesión", comment: ""),
            message: NSLocalizedString(
                "ios_logout_confirmation_message",
                value: "Volverás al modo de exploración pública.",
                comment: "",
            ),
            preferredStyle: .alert,
        )
        alert.addAction(UIAlertAction(
            title: NSLocalizedString("ios_logout_action", value: "Cerrar sesión", comment: ""),
            style: .destructive,
        ) { [weak self] _ in
            self?.performLogout()
        })
        alert.addAction(UIAlertAction(
            title: NSLocalizedString("common_cancel", value: "Cancelar", comment: ""),
            style: .cancel,
        ))
        present(alert, animated: true)
    }

    /// Internal for XCTest: it makes the one-shot state transition testable without invoking
    /// network, Keychain or a confirmation alert.
    func performLogout() {
        guard let logoutAction, !isLoggingOut else { return }
        isLoggingOut = true
        logoutAction { [weak self] in
            DispatchQueue.main.async {
                self?.finishLogout()
            }
        }
    }

    private func finishLogout() {
        guard isLoggingOut else { return }
        isLoggingOut = false
        let completion = onLoggedOut
        // A private route may hold a live Compose controller/repository. Remove every factory
        // before asking the composition root to reinstall the anonymous public Feed.
        hasAuthenticatedSession = false
        hasPublicFeed = false
        feedFactory = nil
        chatFactory = nil
        officialFactory = nil
        officialEditorFactory = nil
        notificationsFactory = nil
        profileSosFactory = nil
        communitiesFactory = nil
        composerFactory = nil
        settingsFactory = nil
        whatsNewFactory = nil
        releaseHistoryFactory = nil
        pendingRoute = nil
        logoutAction = nil
        onLoggedOut = nil
        routeMenuButton.isHidden = true
        // The public application chrome is deliberately kept mounted while the composition
        // rebuilds anonymous factories. Removing it first creates a visible full-screen gap and
        // contradicts Android's shell contract for anonymous Feed browsing.
        authenticatedTopChromeHost.updateNotificationCount(count: 0)
        primaryNavigationHost.updateSelectedRoute(route: "feed")
        showMigrationStatus()
        completion?()
    }

    private func showMigrationStatus() {
        show(
            QuataFeedViewControllerKt.QuataIosMigrationStatusViewController(),
            // The identifier belongs to the real Compose semantics node. Keeping a second UIKit
            // identifier here would make the UI test unable to distinguish content from wrapper.
            accessibilityIdentifier: nil,
            accessibilityLabel: "Quata iOS is preparing the public Feed",
        )
    }

    private func route(_ route: PendingRoute) {
        if !hasAuthenticatedSession, route.isAuthenticationRequired {
            // Retain the target, but follow Android: anonymous browsing remains on Feed while
            // the common capability dialog is presented above the shared shell.
            pendingRoute = route
            // The real public factory creates a fresh Compose controller on each invocation.
            // Rebuilding Feed while it is already visible races the modal presentation and loses
            // scroll/playback state. Only navigate back when the user gated from another route.
            if displayedController?.view.accessibilityIdentifier != "quata-ios-feed-host",
               let feedController = feedFactory?(nil) {
                showRouteController(feedController, route: .feed(postId: nil))
            }
            presentAuthRequiredPrompt()
            return
        }
        guard let controller = controller(for: route) else {
            pendingRoute = route
            return
        }
        pendingRoute = nil
        showRouteController(controller, route: route)
    }

    private func renderPendingRouteIfPossible() {
        guard let pendingRoute else { return }
        guard !pendingRoute.isAuthenticationRequired || hasAuthenticatedSession else { return }
        guard let controller = controller(for: pendingRoute) else { return }
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
        case .officialEditor:
            return officialEditorFactory?()
        case .notifications:
            return notificationsFactory?()
        case .profileSos:
            return profileSosFactory?()
        case .communities:
            return communitiesFactory?()
        case .composer:
            return composerFactory?()
        case .settings:
            return settingsFactory?()
        case .whatsNew:
            return whatsNewFactory?()
        case .releaseHistory:
            return releaseHistoryFactory?()
        }
    }

    private func showRouteController(_ controller: UIViewController, route: PendingRoute) {
        visibleRoute = route
        // Public Official/deep-link routes may be resolved before the Feed factory has been
        // installed. They still belong to the application viewport and therefore get the same
        // shared shell as Feed rather than becoming a full-screen UIKit exception.
        installSharedShellIfNeeded()
        let isComposerRoute: Bool
        if case .composer = route {
            isComposerRoute = true
        } else {
            isComposerRoute = false
        }
        primaryNavigationHost.updateComposerMode(isComposer: isComposerRoute)
        switch route {
        case .communities: primaryNavigationHost.updateSelectedRoute(route: "neighborhoods")
        case .chat: primaryNavigationHost.updateSelectedRoute(route: "conversations")
        case .official: primaryNavigationHost.updateSelectedRoute(route: "official")
        case .feed: primaryNavigationHost.updateSelectedRoute(route: "feed")
        case .profileSos: primaryNavigationHost.updateSelectedRoute(route: "profile")
        case .composer: primaryNavigationHost.updateSelectedRoute(route: "composer")
        default: break
        }
        let presentation: (identifier: String, label: String)
        switch route {
        case .feed:
            presentation = ("quata-ios-feed-host", "Quata iOS Feed")
        case .chat:
            presentation = ("quata-ios-chat-host", "Quata iOS Chat")
        case .official:
            presentation = ("quata-ios-official-host", "Quata iOS Official")
        case .officialEditor:
            presentation = ("quata-ios-official-editor-host", "Quata iOS Official Editor")
        case .notifications:
            presentation = ("quata-ios-notifications-host", "Quata iOS Notifications")
        case .profileSos:
            presentation = ("quata-ios-profile-sos-host", "Quata iOS Profile SOS")
        case .communities:
            presentation = ("quata-ios-communities-host", "Quata iOS Communities")
        case .composer:
            presentation = ("quata-ios-composer-host", "Quata iOS Composer")
        case .settings:
            presentation = ("quata-ios-settings-host", "Quata iOS Settings")
        case .whatsNew:
            presentation = ("quata-ios-whats-new-host", "Quata iOS What's New")
        case .releaseHistory:
            presentation = ("quata-ios-release-history-host", "Quata iOS Release History")
        }
        routeMenuButton.isHidden = !routeUsesSecondaryMenu(route)
        primaryNavigationController.view.isHidden = shouldHidePrimaryNavigation(for: route)
        show(controller, accessibilityIdentifier: presentation.identifier, accessibilityLabel: presentation.label)
    }

    private func shouldHidePrimaryNavigationForVisibleRoute() -> Bool {
        guard let visibleRoute else { return false }
        return shouldHidePrimaryNavigation(for: visibleRoute)
    }

    private func shouldHidePrimaryNavigation(for route: PendingRoute) -> Bool {
        if case .chat = route {
            return true
        }
        return false
    }

    /// The five primary routes are already represented by the common bottom navigation. UIKit's
    /// secondary menu is deliberately unavailable there so it neither duplicates nor overlays
    /// the shared SOS/header chrome.
    func routeUsesSecondaryMenu(_ route: PendingRoute) -> Bool {
        switch route {
        case .feed, .chat, .official, .profileSos, .communities:
            return false
        case .officialEditor, .notifications, .settings, .whatsNew, .releaseHistory:
            return true
        case .composer:
            return false
        }
    }

    /// Replaces the visible shared Compose controller atomically.
    ///
    /// Internal for the host XCTest target: Auth, Feed and deferred authenticated routes use the
    /// same transition, so containment remains testable without credentials or backend calls.
    func show(
        _ controller: UIViewController,
        accessibilityIdentifier: String?,
        accessibilityLabel: String,
    ) {
        // A capability gate requested while the anonymous Feed is already visible must not try
        // to re-parent that same controller. UIKit treats adding an existing child as an invalid
        // containment transition, and the failed transition can make the Feed disappear beneath
        // the modal prompt. Keep the mounted public route stable, exactly as Android does.
        if displayedController === controller {
            controller.view.accessibilityIdentifier = accessibilityIdentifier
            controller.view.accessibilityLabel = accessibilityLabel
            view.setNeedsLayout()
            return
        }
        let previous = displayedController
        addChild(controller)
        controller.view.frame = view.bounds
        controller.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        controller.view.accessibilityIdentifier = accessibilityIdentifier
        // The hosted controller is an accessibility container. Promoting its root view to one
        // element makes UIKit hide the real Compose Text/Button descendants from VoiceOver and
        // XCUITest, even though they remain visibly rendered.
        controller.view.isAccessibilityElement = false
        controller.view.accessibilityLabel = accessibilityLabel
        view.addSubview(controller.view)
        // Feature hosts fill the router bounds. Keep the authenticated route affordance above
        // the newly inserted Compose view; otherwise it remains in the hierarchy but cannot be
        // seen or tapped after the first route transition.
        view.bringSubviewToFront(routeMenuButton)
        if isAuthenticatedTopChromeInstalled { view.bringSubviewToFront(authenticatedTopChromeController.view) }
        if isSharedShellInstalled && !primaryNavigationController.view.isHidden { view.bringSubviewToFront(primaryNavigationController.view) }
        controller.didMove(toParent: self)
        platformServices.attachPresenter(controller: controller)

        previous?.willMove(toParent: nil)
        previous?.view.removeFromSuperview()
        previous?.removeFromParent()
        displayedController = controller
        view.setNeedsLayout()
    }

    /// Installs the common visual shell for both public and authenticated application routes.
    /// Authentication is a navigation capability, not a condition for rendering app chrome.
    private func installSharedShellIfNeeded() {
        guard !isSharedShellInstalled else { return }
        addChild(authenticatedTopChromeController)
        authenticatedTopChromeController.view.autoresizingMask = [.flexibleWidth, .flexibleBottomMargin]
        authenticatedTopChromeController.view.isAccessibilityElement = false
        // Stable legacy automation identifier: this is now the shared public/authenticated
        // shell chrome, but changing the externally observed identifier would break clients.
        authenticatedTopChromeController.view.accessibilityIdentifier = "quata-ios-authenticated-top-chrome"
        view.addSubview(authenticatedTopChromeController.view)
        authenticatedTopChromeController.didMove(toParent: self)
        isAuthenticatedTopChromeInstalled = true
        addChild(primaryNavigationController)
        primaryNavigationController.view.autoresizingMask = [.flexibleWidth, .flexibleTopMargin]
        primaryNavigationController.view.isAccessibilityElement = false
        // Stable legacy automation identifier; see the top-chrome compatibility note above.
        primaryNavigationController.view.accessibilityIdentifier = "quata-ios-authenticated-primary-navigation"
        view.addSubview(primaryNavigationController.view)
        primaryNavigationController.didMove(toParent: self)
        isSharedShellInstalled = true
        view.setNeedsLayout()
    }

}

/// One source of truth for UIKit containment frames. The route host owns exactly the viewport
/// between the shared safe-top chrome and common primary navigation.
struct IosAuthenticatedShellLayout {
    let topChrome: CGRect
    let content: CGRect
    let bottomNavigation: CGRect

    static func frames(
        bounds: CGRect,
        safeAreaInsets: UIEdgeInsets,
        includesBottomNavigation: Bool = true
    ) -> IosAuthenticatedShellLayout {
        let topHeight = safeAreaInsets.top + 68
        let bottomHeight = includesBottomNavigation ? 92 + safeAreaInsets.bottom : safeAreaInsets.bottom
        let contentHeight = max(0, bounds.height - topHeight - bottomHeight)
        let contentWidth = max(0, bounds.width - safeAreaInsets.left - safeAreaInsets.right)
        let content = CGRect(
            x: bounds.minX + safeAreaInsets.left,
            y: bounds.minY + topHeight,
            width: contentWidth,
            height: contentHeight
        )
        return IosAuthenticatedShellLayout(
            topChrome: CGRect(x: bounds.minX, y: bounds.minY, width: bounds.width, height: topHeight),
            content: content,
            bottomNavigation: CGRect(x: bounds.minX, y: content.maxY, width: bounds.width, height: bottomHeight),
        )
    }
}

/// Kept as an internal source-compatible name for the UIKit host boundary tests while the
/// implementation evolves from Feed-only containment into authenticated route containment.
typealias IosFeedHostContainerViewController = IosAuthenticatedHostRouter
