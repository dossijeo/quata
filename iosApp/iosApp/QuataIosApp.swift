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

enum IosPublicRuntimeConfiguration {
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
    private enum SettingsPreferenceKey {
        static let themeMode = "quata_ios_theme_mode"
        static let touchFlowEnabled = "quata_ios_touch_flow_enabled"
    }

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
            authSession: nil
        )
    }()
    /// Profile/SOS reuses the same Keychain-backed identity as Auth, Feed and the other iOS
    /// verticals. Its adapter deliberately exposes read-only remote state until mutations have
    /// iOS RLS/E2E evidence.
    private lazy var profileSosRuntimeBootstrap: IosProfileSosRuntimeBootstrap? = {
        guard let configuration = runtimeConfiguration, let runtimeBootstrap else { return nil }
        return IosProfileSosRuntimeBootstrapKt.createIosProfileSosRuntimeBootstrap(
            configuration: IosProfileRuntimeConfiguration(
                supabaseUrl: configuration.supabaseUrl,
                supabasePublishableKey: configuration.supabasePublishableKey,
            ),
            authSession: runtimeBootstrap.authSessionForInteractiveLogin(),
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
        window.makeKeyAndVisible()
        self.window = window
        externalShareForegroundObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.authenticatedHost.restoreRouteAfterForeground()
            self?.presentPendingExternalShareIfAvailable()
        }
        deepLinkDispatcher.attachHost(host: authenticatedRouteDispatcher)
        installSettings()
        installWhatsNewIfAvailable()
        installPublicFeedIfConfigured()
        installPublicOfficialIfConfigured()
        if !installRestoredFeedSessionIfAvailable() {
            installAuthenticationIfConfigured()
        }
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
                    navigationMessage: "Explora Quata",
                    onOpenChats: { [weak self] in self?.authenticatedHost.presentLoginIfAvailable() },
                    onBackToFeed: { [weak self] in self?.authenticatedHost.showFeed(postId: nil) },
                    onOpenUserProfile: { [weak self] profileId in
                        self?.presentAuthenticatedMemberProfile(profileId: profileId)
                    },
                    initialPostId: postId,
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
        guard let runtimeBootstrap, runtimeBootstrap.hasRestoredSession() else { return false }
        authenticatedHost.installFeedFactory { postId in
            QuataFeedViewControllerKt.QuataFeedViewController(
                dependencies: runtimeBootstrap.authenticatedDependencies(
                    shareService: platformServices.services.share,
                    initialPostId: postId,
                    onOpenUserProfile: { [weak self] profileId in
                        self?.presentAuthenticatedMemberProfile(profileId: profileId)
                    }
                ),
            )
        }
        installAuthenticatedChatIfAvailable()
        installAuthenticatedNotificationsIfAvailable()
        installAuthenticatedProfileSosIfAvailable()
        installAuthenticatedCommunitiesIfAvailable()
        installAuthenticatedComposerIfAvailable()
        presentPendingExternalShareIfAvailable()
        return true
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
        authenticatedHost.installAuthenticatedChat(chatRuntimeBootstrap)
    }

    /// Official stays available to anonymous visitors.  It is installed before session
    /// restoration and rebuilt after logout, while its Kotlin repository keeps every PostgREST
    /// read bearer-free and fails closed for writes.
    private func installPublicOfficialIfConfigured() {
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
                    // APNs token registration remains the existing AppDelegate bridge. Request
                    // it from this completion as the authorization sheet is not guaranteed to
                    // produce another applicationDidBecomeActive callback.
                    UNUserNotificationCenter.current().requestAuthorization(
                        options: [.alert, .badge, .sound]
                    ) { granted, error in
                        guard IosApnsAuthorization.shouldRequestRegistrationAfterPrompt(
                            granted: granted,
                            error: error
                        ) else { return }
                        IosApnsLifecycleBridge.shared.requestRegistrationIfAuthorized()
                    }
                },
                // Conversation navigation above is the real host action. This common callback
                // is observability only and must not manufacture a URL or a route.
                onHandleDeepLink: { _ in },
            ),
        )
    }

    private func installAuthenticatedProfileSosIfAvailable() {
        guard let profileSosRuntimeBootstrap else { return }
        let services = platformServices.services
        authenticatedHost.installProfileSosFactory { [weak self] in
            let dependencies = profileSosRuntimeBootstrap.hostDependencies(
                contacts: services.contacts,
                permissions: services.permissions,
                // ContactsUI returns real device contacts, but Profile currently accepts Quata
                // profile IDs only. Do not turn phone numbers into persistence identifiers.
                onContactsPicked: { [weak self] _ in
                    self?.presentProfileSosCapabilityNotice(
                        ProfileSosCapabilityCopy.shared.selectedDeviceContactsNotMatched(languageTag: Locale.current.identifier)
                    )
                },
                onContactPickerResult: { [weak self] _ in
                    self?.presentProfileSosCapabilityNotice(
                        ProfileSosCapabilityCopy.shared.contactsPickerUnavailable(languageTag: Locale.current.identifier)
                    )
                },
                onContactsPermissionResult: { [weak self] _ in
                    self?.presentProfileSosCapabilityNotice(
                        ProfileSosCapabilityCopy.shared.contactsPermissionNotGranted(languageTag: Locale.current.identifier)
                    )
                },
                onClose: { [weak self] in
                    self?.authenticatedHost.showFeed(postId: nil)
                },
            )
            return IosProfileSosHostKt.QuataProfileSosViewController(dependencies: dependencies)
        }
    }

    private func installAuthenticatedCommunitiesIfAvailable() {
        guard let communitiesRuntimeBootstrap, let profileSosRuntimeBootstrap else { return }
        authenticatedHost.installCommunitiesFactory { [weak self] in
            IosNeighborhoodsHostKt.QuataNeighborhoodsViewController(
                dependencies: IosNeighborhoodsHostKt.createIosNeighborhoodsHostDependencies(
                    repository: communitiesRuntimeBootstrap.repository,
                    currentUserId: communitiesRuntimeBootstrap.restoredCurrentUserId(),
                    onOpenConversation: { [weak self] conversationId in
                        self?.authenticatedHost.showChat(conversationId: conversationId, messageId: nil)
                    },
                    onNavigateToProfile: { [weak self] profileId in
                        self?.presentAuthenticatedMemberProfile(profileId: profileId)
                    },
                ),
            )
        }
    }

    /// Feed and Communities deliberately share the same authenticated member-profile route.
    /// It uses the real read-only repository from the Profile bootstrap; no Feed-local profile
    /// screen or placeholder navigation is introduced here.
    private func presentAuthenticatedMemberProfile(profileId: String) {
        guard let profileSosRuntimeBootstrap else { return }
        let dependencies = profileSosRuntimeBootstrap.memberProfileHostDependencies(
            profileId: profileId,
            onClose: { [weak self] in self?.authenticatedHost.dismiss(animated: true) },
        )
        let controller = IosMemberProfileHostKt.QuataMemberProfileViewController(
            dependencies: dependencies,
        )
        controller.modalPresentationStyle = .fullScreen
        authenticatedHost.present(controller, animated: true)
    }

    /// Composer is an authenticated in-app route. It receives the real UIKit gallery and still
    /// camera adapters already owned by `IosPlatformServiceComposition`; publication remains an
    /// explicit capability error until the iOS write/storage path has RLS and E2E evidence.
    private func installAuthenticatedComposerIfAvailable() {
        guard runtimeBootstrap != nil else { return }
        let services = platformServices.services
        authenticatedHost.installComposerFactory { [weak self] in
            IosComposerHostKt.QuataComposerViewController(
                dependencies: IosComposerHostKt.createIosComposerHostDependencies(
                    repository: IosComposerHostKt.iosComposerPublicationUnavailableRepository(),
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
        authenticatedHost.installSettingsFactory {
            IosSettingsHostKt.QuataSettingsViewController(
                dependencies: IosSettingsHostKt.createIosSettingsHostDependencies(
                    touchFlowEnabled: UserDefaults.standard.object(
                        forKey: SettingsPreferenceKey.touchFlowEnabled
                    ) as? Bool ?? true,
                    themeModeStorageValue: UserDefaults.standard.string(
                        forKey: SettingsPreferenceKey.themeMode
                    ),
                    onTouchFlowEnabledChange: { enabled in
                        UserDefaults.standard.set(enabled, forKey: SettingsPreferenceKey.touchFlowEnabled)
                    },
                    onThemeModeStorageValueChange: { value in
                        UserDefaults.standard.set(value, forKey: SettingsPreferenceKey.themeMode)
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
                onClose: { [weak self] in self?.authenticatedHost.showFeed(postId: nil) },
            )
        }
        authenticatedHost.installReleaseHistoryFactory { [weak self] in
            IosWhatsNewRuntimeBootstrapKt.QuataIosReleaseHistoryViewController(
                runtime: whatsNewRuntimeBootstrap,
                onClose: { [weak self] in self?.authenticatedHost.showFeed(postId: nil) },
            )
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
                self?.installPublicFeedIfConfigured()
                self?.installPublicOfficialIfConfigured()
                self?.installAuthenticationIfConfigured()
            },
        )
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
            configuration: authRuntimeConfiguration(from: configuration),
            session: bootstrap.authSessionForInteractiveLogin(),
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
    private var profileSosFactory: (() -> UIViewController)?
    private var communitiesFactory: (() -> UIViewController)?
    private var composerFactory: (() -> UIViewController)?
    private var settingsFactory: (() -> UIViewController)?
    private var whatsNewFactory: (() -> UIViewController)?
    private var releaseHistoryFactory: (() -> UIViewController)?
    private var authenticationFactory: (() -> UIViewController)?
    private var logoutAction: ((@escaping () -> Void) -> Void)?
    private var onLoggedOut: (() -> Void)?
    private var isLoggingOut = false
    private var pendingRoute: PendingRoute?
    private var hasAuthenticatedSession = false
    private var hasPublicFeed = false
    private lazy var primaryNavigationHost = IosPrimaryNavigationHost(
        initialSelectedRoute: "feed",
        onRouteSelected: { [weak self] route in self?.openPrimaryRoute(route) },
    )
    private lazy var primaryNavigationController = primaryNavigationHost.viewController()
    private var isPrimaryNavigationInstalled = false
    private lazy var authenticatedTopChromeHost = IosAuthenticatedTopChromeHost(
        // iOS has no About route equivalent yet. Keep this callback explicit instead of mapping
        // the shared Q̈ mark to an unrelated release-history route.
        onLogoClick: {},
        onNotificationsClick: { [weak self] in self?.showNotifications() },
        onSosClick: { [weak self] in self?.showProfileSos() },
    )
    private lazy var authenticatedTopChromeController = authenticatedTopChromeHost.viewController()
    private var isAuthenticatedTopChromeInstalled = false
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

    private enum PendingRoute {
        case feed(postId: String?)
        case chat(conversationId: String?, messageId: String?)
        case official(postId: String?)
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
            case .chat, .notifications, .profileSos, .communities, .composer, .settings:
                return true
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
        guard isPrimaryNavigationInstalled else {
            displayedController?.view.frame = view.bounds
            return
        }
        let layout = IosAuthenticatedShellLayout.frames(bounds: view.bounds, safeAreaInsets: view.safeAreaInsets)
        displayedController?.view.frame = layout.content
        authenticatedTopChromeController.view.frame = layout.topChrome
        primaryNavigationController.view.frame = layout.bottomNavigation
    }

    func installAuthenticatedFeed(_ dependencies: IosFeedHostDependencies) {
        installFeedFactory { _ in QuataFeedViewControllerKt.QuataFeedViewController(dependencies: dependencies) }
    }

    /// Public Feed factory never enables the authenticated menu or interactive verticals.
    func installPublicFeed(_ factory: @escaping (String?) -> UIViewController) {
        guard !hasAuthenticatedSession else { return }
        feedFactory = factory
        hasPublicFeed = true
        routeMenuButton.isHidden = true
        renderPendingRouteIfPossible()
        if pendingRoute == nil { showFeed(postId: nil) }
    }

    /// Installs the authenticated root route. Kept separate from dependency composition so the
    /// UIKit routing contract can be verified without credentials, network traffic or a backend.
    func installFeedFactory(_ factory: @escaping (String?) -> UIViewController) {
        feedFactory = factory
        hasAuthenticatedSession = true
        hasPublicFeed = false
        installPrimaryNavigationIfNeeded()
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
        installAuthenticationFactory {
            IosAuthHostKt.QuataAuthViewController(dependencies: dependencies)
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
    func installAuthenticationFactory(_ factory: @escaping () -> UIViewController) {
        authenticationFactory = factory
        // On an unconfigured deployment this remains the honest initial state. With a valid
        // public Feed it is deferred until an anonymous user explicitly asks to log in.
        if !hasPublicFeed { presentLoginIfAvailable() }
    }

    func presentLoginIfAvailable() {
        guard !hasAuthenticatedSession, let authenticationFactory else { return }
        routeMenuButton.isHidden = true
        show(
            authenticationFactory(),
            accessibilityIdentifier: "quata-ios-auth-host",
            accessibilityLabel: "Quata iOS authentication",
        )
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
        installPrimaryNavigationIfNeeded()
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
    func installAuthenticatedChat(_ bootstrap: IosChatRuntimeBootstrap) {
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
                conversationId: conversationId,
                focusedMessageId: messageId,
                onOpenConversation: { [weak self] conversationId in
                    self?.showChat(conversationId: conversationId, messageId: nil)
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

    func showNotifications() { route(.notifications) }

    func showProfileSos() { route(.profileSos) }

    func showCommunities() { route(.communities) }

    /// Composer deliberately has no public deep-link contract. The launcher opens it only after
    /// a Keychain-backed authenticated session has supplied its real platform adapters.
    func showComposer() { route(.composer) }

    func showSettings() { route(.settings) }

    func showWhatsNew() { route(.whatsNew) }

    func showReleaseHistory() { route(.releaseHistory) }

    func openChatList() { route(.chat(conversationId: nil, messageId: nil)) }

    private func openPrimaryRoute(_ route: String) {
        switch route {
        case "neighborhoods": showCommunities()
        case "conversations": openChatList()
        case "official": showOfficial(postId: nil)
        case "feed": showFeed(postId: nil)
        case "profile": showProfileSos()
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
        removePrimaryNavigation()
        completion?()
    }

    private func showMigrationStatus() {
        show(
            QuataFeedViewControllerKt.QuataIosMigrationStatusViewController(),
            // The identifier belongs to the real Compose semantics node. Keeping a second UIKit
            // identifier here would make the UI test unable to distinguish content from wrapper.
            accessibilityIdentifier: nil,
            accessibilityLabel: "Quata iOS requires an authenticated Feed session",
        )
    }

    private func route(_ route: PendingRoute) {
        if !hasAuthenticatedSession, route.isAuthenticationRequired {
            // Retain a private deep link for the authenticated composition, but never render its
            // controller before the launcher has restored a real session.
            pendingRoute = route
            presentLoginIfAvailable()
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
        switch route {
        case .communities: primaryNavigationHost.updateSelectedRoute(route: "neighborhoods")
        case .chat: primaryNavigationHost.updateSelectedRoute(route: "conversations")
        case .official: primaryNavigationHost.updateSelectedRoute(route: "official")
        case .feed: primaryNavigationHost.updateSelectedRoute(route: "feed")
        case .profileSos: primaryNavigationHost.updateSelectedRoute(route: "profile")
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
        show(controller, accessibilityIdentifier: presentation.identifier, accessibilityLabel: presentation.label)
    }

    /// The five primary routes are already represented by the common bottom navigation. UIKit's
    /// secondary menu is deliberately unavailable there so it neither duplicates nor overlays
    /// the shared SOS/header chrome.
    private func routeUsesSecondaryMenu(_ route: PendingRoute) -> Bool {
        switch route {
        case .feed, .chat, .official, .profileSos, .communities:
            return false
        case .notifications, .composer, .settings, .whatsNew, .releaseHistory:
            return true
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
        if isPrimaryNavigationInstalled { view.bringSubviewToFront(primaryNavigationController.view) }
        controller.didMove(toParent: self)
        platformServices.attachPresenter(controller: controller)

        previous?.willMove(toParent: nil)
        previous?.view.removeFromSuperview()
        previous?.removeFromParent()
        displayedController = controller
        view.setNeedsLayout()
    }

    private func installPrimaryNavigationIfNeeded() {
        guard !isPrimaryNavigationInstalled else { return }
        addChild(authenticatedTopChromeController)
        authenticatedTopChromeController.view.autoresizingMask = [.flexibleWidth, .flexibleBottomMargin]
        authenticatedTopChromeController.view.isAccessibilityElement = false
        authenticatedTopChromeController.view.accessibilityIdentifier = "quata-ios-authenticated-top-chrome"
        view.addSubview(authenticatedTopChromeController.view)
        authenticatedTopChromeController.didMove(toParent: self)
        isAuthenticatedTopChromeInstalled = true
        addChild(primaryNavigationController)
        primaryNavigationController.view.autoresizingMask = [.flexibleWidth, .flexibleTopMargin]
        primaryNavigationController.view.isAccessibilityElement = false
        primaryNavigationController.view.accessibilityIdentifier = "quata-ios-authenticated-primary-navigation"
        view.addSubview(primaryNavigationController.view)
        primaryNavigationController.didMove(toParent: self)
        isPrimaryNavigationInstalled = true
        view.setNeedsLayout()
    }

    private func removePrimaryNavigation() {
        guard isPrimaryNavigationInstalled else { return }
        authenticatedTopChromeController.willMove(toParent: nil)
        authenticatedTopChromeController.view.removeFromSuperview()
        authenticatedTopChromeController.removeFromParent()
        isAuthenticatedTopChromeInstalled = false
        primaryNavigationController.willMove(toParent: nil)
        primaryNavigationController.view.removeFromSuperview()
        primaryNavigationController.removeFromParent()
        isPrimaryNavigationInstalled = false
    }
}

/// One source of truth for UIKit containment frames. The route host owns exactly the viewport
/// between the shared safe-top chrome and common primary navigation.
struct IosAuthenticatedShellLayout {
    let topChrome: CGRect
    let content: CGRect
    let bottomNavigation: CGRect

    static func frames(bounds: CGRect, safeAreaInsets: UIEdgeInsets) -> IosAuthenticatedShellLayout {
        let topHeight = safeAreaInsets.top + 68
        let bottomHeight = 92 + safeAreaInsets.bottom
        let contentHeight = max(0, bounds.height - topHeight - bottomHeight)
        let content = CGRect(x: bounds.minX, y: bounds.minY + topHeight, width: bounds.width, height: contentHeight)
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
