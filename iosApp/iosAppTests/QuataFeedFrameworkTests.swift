import XCTest
import Foundation
import UIKit
import QuickLook
import QuataShared
@testable import QuataIos
import QuickLookThumbnailing
import AVFoundation
import CoreLocation
import Metal
import Security
import UniformTypeIdentifiers

final class QuataFeedFrameworkTests: XCTestCase {
    private var mountedWindows: [UIWindow] = []

    override func tearDown() {
        mountedWindows.forEach { $0.isHidden = true }
        mountedWindows.removeAll()
        super.tearDown()
    }

    private func mountRouter() -> MountedRouter {
        let router = IosFeedHostContainerViewController(platformServices: makePlatformServiceComposition())
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = router
        window.makeKeyAndVisible()
        router.disableAuthModalAnimationsForTesting()
        router.loadViewIfNeeded()
        router.view.layoutIfNeeded()
        mountedWindows.append(window)
        return MountedRouter(window: window, router: router)
    }

    func testAppRegistersTheStableQuataCustomUrlScheme() {
        let appBundle = Bundle(for: AppDelegate.self)

        XCTAssertTrue(
            IosDeepLinkUrlContract.isRegistered(in: appBundle.infoDictionary ?? [:]),
            "The app bundle must register the quata URL scheme so iOS can deliver app-open URLs."
        )
    }

    func testCustomUrlContractAcceptsOnlyTheRegisteredQuataHost() {
        XCTAssertTrue(IosDeepLinkUrlContract.acceptsApplicationOpenUrl(URL(string: "quata://egquata.com/#post-video-7")!))
        XCTAssertTrue(IosDeepLinkUrlContract.acceptsApplicationOpenUrl(URL(string: "quata://egquata.com/#official-news-8")!))
        XCTAssertTrue(IosDeepLinkUrlContract.acceptsApplicationOpenUrl(URL(string: "quata://egquata.com/#chat-sb%3A9?message=m-3")!))
        XCTAssertFalse(IosDeepLinkUrlContract.acceptsApplicationOpenUrl(URL(string: "https://egquata.com/#post-video-7")!))
        XCTAssertFalse(IosDeepLinkUrlContract.acceptsApplicationOpenUrl(URL(string: "quata://example.com/#post-video-7")!))
    }

    func testSimulatorExposesMetalDevice() throws {
        let device = try XCTUnwrap(MTLCreateSystemDefaultDevice(), "The iOS Simulator did not expose a Metal device.")
        let attachment = XCTAttachment(string: "Metal device: \(device.name)")
        attachment.name = "simulator-metal-device.txt"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
    func testApnsAuthorizationRegistersOnlyForGrantedSystemStates() {
        XCTAssertFalse(IosApnsAuthorization.permitsRegistration(.notDetermined))
        XCTAssertFalse(IosApnsAuthorization.permitsRegistration(.denied))
        XCTAssertTrue(IosApnsAuthorization.permitsRegistration(.authorized))
        XCTAssertTrue(IosApnsAuthorization.permitsRegistration(.provisional))
        XCTAssertTrue(IosApnsAuthorization.permitsRegistration(.ephemeral))
    }

    func testApnsAuthorizationPromptRequestsRegistrationOnlyAfterCleanGrant() {
        let error = NSError(domain: "test", code: 1)

        XCTAssertTrue(
            IosApnsAuthorization.shouldRequestRegistrationAfterPrompt(granted: true, error: nil)
        )
        XCTAssertFalse(
            IosApnsAuthorization.shouldRequestRegistrationAfterPrompt(granted: false, error: nil)
        )
        XCTAssertFalse(
            IosApnsAuthorization.shouldRequestRegistrationAfterPrompt(granted: true, error: error)
        )
    }

    func testApnsTokenFormattingProducesCanonicalLowercaseHexWithoutPersistence() {
        let token = Data([0x00, 0x0A, 0xF0, 0xFF])

        XCTAssertEqual(IosApnsTokenFormatting.hexString(token), "000af0ff")
        XCTAssertEqual(IosApnsTokenFormatting.hexString(Data()), "")
    }

    func testWhatsNewPreferredLanguageUsesSpanishTag() {
        XCTAssertEqual(IosWhatsNewLocale.sanitizedPreferredLanguageTag(["es-ES", "en-US"]), "es-ES")
    }

    func testWhatsNewPreferredLanguageUsesEnglishTag() {
        XCTAssertEqual(IosWhatsNewLocale.sanitizedPreferredLanguageTag(["en-US", "es-ES"]), "en-US")
    }

    func testWhatsNewPreferredLanguageRejectsInvalidInputForKotlinFallback() {
        XCTAssertNil(IosWhatsNewLocale.sanitizedPreferredLanguageTag(["../../invalid"]))
        XCTAssertNil(IosWhatsNewLocale.sanitizedPreferredLanguageTag([]))
    }

    func testWhatsNewMenuDispatcherForwardsOnlyAfterHostAttachment() {
        let dispatcher = IosWhatsNewRouteDispatcher()
        let host = CapturingWhatsNewRouteHost()

        _ = dispatcher.openReleaseHistory()
        XCTAssertNil(host.route)

        dispatcher.attachHost(host: host)
        _ = dispatcher.openReleaseHistory()
        XCTAssertEqual(host.route?.name, "ReleaseHistory")

        dispatcher.detachHost()
        _ = dispatcher.openPendingReleases()
        XCTAssertEqual(host.route?.name, "ReleaseHistory")
    }

    func testPublicReleaseHistoryDeepLinkWaitsForLocalFactory() {
        let services = makePlatformServiceComposition()
        let router = IosFeedHostContainerViewController(platformServices: services)
        router.loadViewIfNeeded()
        let initialChildren = router.children
        let dispatcher = IosDeepLinkDispatcher()
        dispatcher.attachHost(host: IosAuthenticatedRouteDispatcher(host: router))

        _ = dispatcher.handleUrl(url: "https://egquata.com/#release-history")
        XCTAssertEqual(router.children.count, initialChildren.count)

        let history = UIViewController()
        router.installReleaseHistoryFactory { history }

        XCTAssertTrue(router.children.contains { $0 === history })
        XCTAssertEqual(history.view.accessibilityIdentifier, "quata-ios-release-history-host")
    }

    func testKeychainStorageCanQueryAnIsolatedNamespaceWithoutCrashing() {
        // This covers the Kotlin/Foundation/CoreFoundation bridge used by SecItemCopyMatching.
        // A unique namespace avoids observing or modifying the authenticated app session.
        let storage = IosKeychainSessionStorage(
            service: "com.quata.tests.keychain-bridge.\(UUID().uuidString)",
            account: "missing-session",
        )
        defer { storage.clear() }

        XCTAssertNil(storage.getSession())
        // The CI simulator test bundle has no Keychain entitlement. A real signed host returns
        // errSecItemNotFound for this namespace; both outcomes prove that the Kotlin/CF bridge
        // handed Security a valid CFDictionary instead of dereferencing Kotlin heap memory.
        if let status = storage.lastStatus {
            XCTAssertEqual(OSStatus(status.intValue), errSecMissingEntitlement)
        }
    }

    func testExportsComposeMigrationViewController() {
        let controller = QuataFeedViewControllerKt.QuataIosMigrationStatusViewController()

        controller.loadViewIfNeeded()

        XCTAssertTrue(controller.isViewLoaded)
        XCTAssertNotNil(controller.view)
    }

    func testPublicRuntimeConfigurationRejectsMissingOrUnexpandedBuildSettings() {
        XCTAssertNil(IosPublicRuntimeConfiguration.feedConfiguration(infoDictionary: [:]))
        XCTAssertNil(IosPublicRuntimeConfiguration.feedConfiguration(infoDictionary: [
            "QUATA_SUPABASE_URL": "$(QUATA_SUPABASE_URL)",
            "QUATA_SUPABASE_PUBLISHABLE_KEY": "$(QUATA_SUPABASE_PUBLISHABLE_KEY)",
        ]))
    }

    func testPublicRuntimeConfigurationParsesInjectedClientSettingsWithoutNetwork() {
        let configuration = IosPublicRuntimeConfiguration.feedConfiguration(infoDictionary: [
            "QUATA_SUPABASE_URL": " https://deployment.invalid ",
            "QUATA_SUPABASE_PUBLISHABLE_KEY": " public-build-setting ",
        ])

        XCTAssertEqual(configuration?.supabaseUrl, "https://deployment.invalid")
        XCTAssertEqual(configuration?.supabasePublishableKey, "public-build-setting")
    }

    func testPublicRuntimeConfigurationKeepsRegistrationFailClosedForMissingOrUnexpandedInputs() {
        let feedConfiguration = IosFeedRuntimeConfiguration(
            supabaseUrl: "https://deployment.invalid",
            supabasePublishableKey: "public-build-setting",
        )
        let configuration = IosPublicRuntimeConfiguration.authConfiguration(
            from: feedConfiguration,
            infoDictionary: [
                "QUATA_IOS_REGISTRATION_ENABLED": "true",
                "QUATA_IOS_REGISTRATION_API_KEY": "$(QUATA_IOS_REGISTRATION_API_KEY)",
                "QUATA_IOS_REGISTRATION_CLIENT_INSTANCE_ID": "ios-install",
            ],
        )

        XCTAssertTrue(configuration.iosRegistrationEnabled)
        XCTAssertNil(configuration.registrationApiKey)
        XCTAssertEqual(configuration.registrationClientInstanceId, "ios-install")
        XCTAssertNil(configuration.registrationChallengeToken)
        XCTAssertFalse(IosAuthRepositoryKt.iosRegistrationAvailable(configuration: configuration))
    }

    func testPublicRuntimeConfigurationWiresExplicitRegistrationBuildSettings() {
        let feedConfiguration = IosFeedRuntimeConfiguration(
            supabaseUrl: "https://deployment.invalid",
            supabasePublishableKey: "public-build-setting",
        )
        let configuration = IosPublicRuntimeConfiguration.authConfiguration(
            from: feedConfiguration,
            infoDictionary: [
                "QUATA_IOS_REGISTRATION_ENABLED": "true",
                "QUATA_IOS_REGISTRATION_API_KEY": " public-registration-key ",
                "QUATA_IOS_REGISTRATION_CLIENT_INSTANCE_ID": " ios-install ",
                "QUATA_IOS_REGISTRATION_CHALLENGE_TOKEN": " challenge-token ",
            ],
        )

        XCTAssertTrue(configuration.iosRegistrationEnabled)
        XCTAssertEqual(configuration.registrationApiKey, "public-registration-key")
        XCTAssertEqual(configuration.registrationClientInstanceId, "ios-install")
        XCTAssertEqual(configuration.registrationChallengeToken, "challenge-token")
        XCTAssertTrue(IosAuthRepositoryKt.iosRegistrationAvailable(configuration: configuration))
    }

    func testAnonymousRouterShowsPublicFeedInsideSharedShellAndKeepsPrivateRoutesGated() {
        let mounted = mountRouter()
        let router = mounted.router
        let publicFeed = UIViewController()

        router.installPublicFeed { _ in publicFeed }
        let prompt = UIViewController()
        router.installAuthRequiredPromptFactory { prompt }

        XCTAssertTrue(router.children.contains { $0 === publicFeed })
        XCTAssertEqual(publicFeed.view.accessibilityIdentifier, "quata-ios-feed-host")
        XCTAssertNotNil(router.view.subviews.first {
            $0.accessibilityIdentifier == "quata-ios-authenticated-top-chrome"
        })
        XCTAssertNotNil(router.view.subviews.first {
            $0.accessibilityIdentifier == "quata-ios-authenticated-primary-navigation"
        })
        XCTAssertTrue(router.view.subviews.compactMap { $0 as? UIButton }.allSatisfy(\.isHidden))

        router.showChat(conversationId: "private-chat", messageId: nil)

        XCTAssertTrue(router.children.contains { $0 === publicFeed })
        XCTAssertEqual(router.presentedViewController?.view.accessibilityIdentifier, "quata-ios-auth-required-dialog")
        XCTAssertEqual(router.children.count, 3)
    }

    func testAnonymousFeedShellReservesContentBetweenTopChromeAndPrimaryNavigation() {
        let router = IosFeedHostContainerViewController(platformServices: makePlatformServiceComposition())
        router.loadViewIfNeeded()
        router.view.frame = CGRect(x: 0, y: 0, width: 402, height: 874)
        let publicFeed = UIViewController()

        router.installPublicFeed { _ in publicFeed }
        router.view.setNeedsLayout()
        router.view.layoutIfNeeded()

        let layout = IosAuthenticatedShellLayout.frames(
            bounds: router.view.bounds,
            safeAreaInsets: router.view.safeAreaInsets,
        )
        XCTAssertEqual(publicFeed.view.frame, layout.content)
        XCTAssertEqual(
            router.view.subviews.first {
                $0.accessibilityIdentifier == "quata-ios-authenticated-top-chrome"
            }?.frame,
            layout.topChrome,
        )
        XCTAssertEqual(
            router.view.subviews.first {
                $0.accessibilityIdentifier == "quata-ios-authenticated-primary-navigation"
            }?.frame,
            layout.bottomNavigation,
        )
    }

    func testAnonymousRouterFailsClosedForEveryProtectedRouteEvenWhenItsFactoryExists() {
        // This is a UIKit routing contract only. The factories are deliberately inert: it proves
        // that a private destination cannot be rendered before the real Keychain-backed session
        // exists; it does not claim any backend read or mutation succeeds.
        let protectedRoutes: [(String, (IosFeedHostContainerViewController) -> Void)] = [
            ("quata-ios-chat-host", { $0.showChat(conversationId: "conversation-1", messageId: "message-1") }),
            ("quata-ios-profile-sos-host", { $0.showProfileSos() }),
            ("quata-ios-composer-host", { $0.showComposer() }),
            ("quata-ios-settings-host", { $0.showSettings() }),
        ]

        for (protectedIdentifier, openRoute) in protectedRoutes {
            let mounted = mountRouter()
            let router = mounted.router
            let publicFeed = UIViewController()
            router.installPublicFeed { _ in publicFeed }
            let prompt = UIViewController()
            router.installAuthRequiredPromptFactory { prompt }
            router.installChatFactory { _, _ in UIViewController() }
            router.installNotificationsFactory { UIViewController() }
            router.installProfileSosFactory { UIViewController() }
            router.installCommunitiesFactory { UIViewController() }
            router.installComposerFactory { UIViewController() }
            router.installSettingsFactory { UIViewController() }

            openRoute(router)

            XCTAssertTrue(authenticatedRouteController(in: router) === publicFeed, "Anonymous route replaced public Feed: \(protectedIdentifier)")
            XCTAssertTrue(router.presentedViewController?.children.first === prompt, "Protected route did not open the capability prompt: \(protectedIdentifier)")
            XCTAssertEqual(router.presentedViewController?.view.accessibilityIdentifier, "quata-ios-auth-required-dialog")
            XCTAssertFalse(router.children.contains { $0.view.accessibilityIdentifier == protectedIdentifier })
            XCTAssertEqual(router.children.count, 3)
        }
    }

    func testAnonymousRouterAllowsPublicPostRouteWithoutSession() throws {
        let router = IosFeedHostContainerViewController(platformServices: makePlatformServiceComposition())
        router.loadViewIfNeeded()
        var receivedPostId: String?
        var latestPublicFeed: UIViewController?
        router.installPublicFeed { postId in
            receivedPostId = postId
            let publicFeed = UIViewController()
            latestPublicFeed = publicFeed
            return publicFeed
        }

        router.showFeed(postId: "public-post-9")

        XCTAssertEqual(receivedPostId, "public-post-9")
        let publicFeed = try XCTUnwrap(latestPublicFeed)
        XCTAssertTrue(router.children.contains { $0 === publicFeed })
    }

    func testAnonymousRouterAllowsCommunitiesInsideSharedShell() {
        let router = IosFeedHostContainerViewController(platformServices: makePlatformServiceComposition())
        router.loadViewIfNeeded()
        let feed = UIViewController()
        let communities = UIViewController()
        router.installPublicFeed { _ in feed }
        router.installCommunitiesFactory { communities }

        router.showCommunities()

        XCTAssertTrue(authenticatedRouteController(in: router) === communities)
        XCTAssertEqual(communities.view.accessibilityIdentifier, "quata-ios-communities-host")
        XCTAssertNil(router.presentedViewController)
        XCTAssertNotNil(router.view.subviews.first { $0.accessibilityIdentifier == "quata-ios-authenticated-primary-navigation" })
    }

    func testAnonymousRouterAllowsNotificationsButLeavesConversationGated() {
        let router = IosFeedHostContainerViewController(platformServices: makePlatformServiceComposition())
        router.loadViewIfNeeded()
        let feed = UIViewController()
        let notifications = UIViewController()
        router.installPublicFeed { _ in feed }
        router.installNotificationsFactory { notifications }

        router.showNotifications()

        XCTAssertTrue(authenticatedRouteController(in: router) === notifications)
        XCTAssertEqual(notifications.view.accessibilityIdentifier, "quata-ios-notifications-host")
    }

    func testAnonymousPrivateRouteQueuesShowsLoginAndConsumesAfterAuthenticationAndFactoryInstall() {
        let mounted = mountRouter()
        let router = mounted.router
        let publicFeed = UIViewController()
        router.installPublicFeed { _ in publicFeed }
        let prompt = UIViewController()
        router.installAuthRequiredPromptFactory { prompt }

        router.showChat(conversationId: "private-chat", messageId: "message-4")

        XCTAssertTrue(router.children.contains { $0 === publicFeed })
        XCTAssertTrue(router.presentedViewController?.children.first === prompt)
        XCTAssertFalse(router.children.contains { $0.view.accessibilityIdentifier == "quata-ios-chat-host" })
        XCTAssertNotNil(router.view.subviews.first {
            $0.accessibilityIdentifier == "quata-ios-authenticated-top-chrome"
        })
        XCTAssertNotNil(router.view.subviews.first {
            $0.accessibilityIdentifier == "quata-ios-authenticated-primary-navigation"
        })

        let chat = UIViewController()
        router.installChatFactory { conversationId, messageId in
            XCTAssertEqual(conversationId, "private-chat")
            XCTAssertEqual(messageId, "message-4")
            return chat
        }

        XCTAssertTrue(router.children.contains { $0 === publicFeed })
        XCTAssertFalse(router.children.contains { $0 === chat })

        router.installFeedFactory { _ in UIViewController() }

        XCTAssertTrue(router.children.contains { $0 === chat })
        XCTAssertEqual(chat.view.accessibilityIdentifier, "quata-ios-chat-host")
        XCTAssertFalse(router.children.contains { $0.view.accessibilityIdentifier == "quata-ios-feed-host" })
        XCTAssertEqual(router.children.count, 3)
        XCTAssertTrue(authenticatedRouteController(in: router) === chat)
    }

    func testAuthRequiredPromptDismissKeepsAnonymousFeedAndSharedShell() {
        let mounted = mountRouter()
        let router = mounted.router
        let publicFeed = UIViewController()
        router.installPublicFeed { _ in publicFeed }
        router.installAuthRequiredPromptFactory { UIViewController() }
        let promptPresented = expectation(description: "Auth prompt presentation completed")
        router.onNextAuthPromptPresentedForTesting { promptPresented.fulfill() }

        router.showComposer()
        XCTAssertEqual(router.presentedViewController?.view.accessibilityIdentifier, "quata-ios-auth-required-dialog")
        wait(for: [promptPresented], timeout: 2)

        // This is the exact callback used by common AlertDialog.onDismissRequest when its scrim
        // is tapped. The dialog disappears; the public app route and shell remain mounted.
        router.dismissAuthRequiredPrompt()
        waitUntil { router.presentedViewController == nil }
        XCTAssertTrue(authenticatedRouteController(in: router) === publicFeed)
        XCTAssertNotNil(router.view.subviews.first {
            $0.accessibilityIdentifier == "quata-ios-authenticated-top-chrome"
        })
        XCTAssertNotNil(router.view.subviews.first {
            $0.accessibilityIdentifier == "quata-ios-authenticated-primary-navigation"
        })
    }

    func testAuthRequiredPromptCreateAccountOpensRegistrationFullScreenOutsideShell() {
        let mounted = mountRouter()
        let router = mounted.router
        let registration = UIViewController()
        router.installPublicFeed { _ in UIViewController() }
        router.installAuthRequiredPromptFactory { UIViewController() }
        router.installAuthenticationFactory { registration }
        let promptPresented = expectation(description: "Auth prompt presentation completed")
        router.onNextAuthPromptPresentedForTesting { promptPresented.fulfill() }

        router.showComposer()
        XCTAssertEqual(router.presentedViewController?.view.accessibilityIdentifier, "quata-ios-auth-required-dialog")
        wait(for: [promptPresented], timeout: 2)
        let registrationPresented = expectation(description: "Registration presentation completed")
        router.onNextAuthenticationPresentedForTesting { registrationPresented.fulfill() }
        router.openRegistrationFromAuthRequiredPrompt()
        wait(for: [registrationPresented], timeout: 2)

        XCTAssertTrue(router.presentedViewController === registration)
        XCTAssertEqual(registration.modalPresentationStyle, .overFullScreen)
        XCTAssertEqual(registration.view.backgroundColor, .systemBackground)
        XCTAssertTrue(registration.view.isOpaque)
        XCTAssertEqual(registration.view.accessibilityIdentifier, "quata-ios-auth-host")
        XCTAssertNotNil(router.view.subviews.first {
            $0.accessibilityIdentifier == "quata-ios-authenticated-top-chrome"
        })
        XCTAssertNotNil(router.view.subviews.first {
            $0.accessibilityIdentifier == "quata-ios-authenticated-primary-navigation"
        })
        XCTAssertNil(registration.view.subviews.first {
            $0.accessibilityIdentifier == "quata-ios-authenticated-top-chrome"
        })
        XCTAssertNil(registration.view.subviews.first {
            $0.accessibilityIdentifier == "quata-ios-authenticated-primary-navigation"
        })
    }

    func testPrivateRouteQueuedBeforePublicFactoriesShowsShellThenLoginAndStillResolvesAfterAuthentication() {
        let mounted = mountRouter()
        let router = mounted.router

        router.showChat(conversationId: "deferred-private-chat", messageId: nil)
        let publicFeed = UIViewController()
        router.installPublicFeed { _ in publicFeed }

        XCTAssertTrue(authenticatedRouteController(in: router) === publicFeed)
        XCTAssertNotNil(router.view.subviews.first {
            $0.accessibilityIdentifier == "quata-ios-authenticated-top-chrome"
        })
        XCTAssertNotNil(router.view.subviews.first {
            $0.accessibilityIdentifier == "quata-ios-authenticated-primary-navigation"
        })

        let prompt = UIViewController()
        router.installAuthRequiredPromptFactory { prompt }
        router.showChat(conversationId: "deferred-private-chat", messageId: nil)
        XCTAssertTrue(authenticatedRouteController(in: router) === publicFeed)
        XCTAssertTrue(router.presentedViewController?.children.first === prompt)

        let chat = UIViewController()
        router.installChatFactory { conversationId, messageId in
            XCTAssertEqual(conversationId, "deferred-private-chat")
            XCTAssertNil(messageId)
            return chat
        }
        router.installFeedFactory { _ in UIViewController() }

        XCTAssertTrue(authenticatedRouteController(in: router) === chat)
        XCTAssertEqual(chat.view.accessibilityIdentifier, "quata-ios-chat-host")
    }

    func testAnonymousRouterAllowsLocalWhatsNewAndReleaseHistoryWithoutSession() {
        let router = IosFeedHostContainerViewController(platformServices: makePlatformServiceComposition())
        router.loadViewIfNeeded()
        router.installPublicFeed { _ in UIViewController() }
        let whatsNew = UIViewController()
        let releaseHistory = UIViewController()
        router.installWhatsNewFactory { whatsNew }
        router.installReleaseHistoryFactory { releaseHistory }

        router.showWhatsNew()

        XCTAssertTrue(router.children.contains { $0 === whatsNew })
        XCTAssertEqual(whatsNew.view.accessibilityIdentifier, "quata-ios-whats-new-host")

        router.showReleaseHistory()

        XCTAssertTrue(router.children.contains { $0 === releaseHistory })
        XCTAssertEqual(releaseHistory.view.accessibilityIdentifier, "quata-ios-release-history-host")
        XCTAssertEqual(router.children.count, 3)
    }

    func testPublicRuntimeConfigurationRequiresBothNonEmptyClientSettings() {
        XCTAssertNil(IosPublicRuntimeConfiguration.feedConfiguration(infoDictionary: [
            "QUATA_SUPABASE_URL": "https://deployment.invalid",
            "QUATA_SUPABASE_PUBLISHABLE_KEY": "   ",
        ]))
        XCTAssertNil(IosPublicRuntimeConfiguration.feedConfiguration(infoDictionary: [
            "QUATA_SUPABASE_URL": "   ",
            "QUATA_SUPABASE_PUBLISHABLE_KEY": "client-key",
        ]))
    }

    func testPublicRuntimeConfigurationRejectsMalformedOrMultilineUrls() {
        XCTAssertNil(IosPublicRuntimeConfiguration.feedConfiguration(infoDictionary: [
            "QUATA_SUPABASE_URL": "http://deployment.invalid",
            "QUATA_SUPABASE_PUBLISHABLE_KEY": "client-key",
        ]))
        XCTAssertNil(IosPublicRuntimeConfiguration.feedConfiguration(infoDictionary: [
            "QUATA_SUPABASE_URL": "https://deployment.invalid\r\nhttps://other.invalid",
            "QUATA_SUPABASE_PUBLISHABLE_KEY": "client-key",
        ]))
        XCTAssertNil(IosPublicRuntimeConfiguration.feedConfiguration(infoDictionary: [
            "QUATA_SUPABASE_URL": "https://deployment.invalid",
            "QUATA_SUPABASE_PUBLISHABLE_KEY": "client\r\nkey",
        ]))
    }

    func testDeepLinkDispatcherReturnsUnsupportedBeforeHostAttachmentWithoutReplayingLater() {
        let dispatcher = IosDeepLinkDispatcher()
        let host = CapturingAuthenticatedRouteHost()

        let result = dispatcher.handleUrl(url: "https://egquata.com/#chat-conversation-7?message=message-4")
        XCTAssertTrue(result is PlatformResultUnsupported)
        XCTAssertNil(host.route)
        XCTAssertEqual(host.callCount, 0)

        dispatcher.attachHost(host: IosAuthenticatedRouteDispatcher(host: host))
        XCTAssertNil(host.route)
        XCTAssertEqual(host.callCount, 0)
    }

    func testDeepLinkDispatcherRoutesPublicUrlsWithoutRenderingCompose() {
        let host = CapturingAuthenticatedRouteHost()
        let dispatcher = IosDeepLinkDispatcher()
        dispatcher.attachHost(host: IosAuthenticatedRouteDispatcher(host: host))

        _ = dispatcher.handleUrl(url: "https://egquata.com/#post-feed-9")
        XCTAssertEqual(host.route, .feed(postId: "feed-9"))

        _ = dispatcher.handleUrl(url: "https://egquata.com/#official-public-7")
        XCTAssertEqual(host.route, .official(postId: "public-7"))

        _ = dispatcher.handleUrl(url: "https://egquata.com/#whats-new")
        XCTAssertEqual(host.route, .whatsNew)

        _ = dispatcher.handleUrl(url: "https://egquata.com/#release-history")
        XCTAssertEqual(host.route, .releaseHistory)

        let callsBeforeInvalidUrl = host.callCount
        let invalidResult = dispatcher.handleUrl(url: "https://invalid.example/#chat-ignore")
        XCTAssertTrue(invalidResult is PlatformResultFailure)
        XCTAssertEqual(host.route, .releaseHistory)
        XCTAssertEqual(host.callCount, callsBeforeInvalidUrl)
    }

    func testNotificationPayloadMappingRoutesToChatAtTheHostBoundary() {
        // This only verifies provider-normalized payload mapping. It does not claim an
        // authenticated session or exercise the Keychain-backed launcher policy.
        let host = CapturingAuthenticatedRouteHost()
        let dispatcher = IosDeepLinkDispatcher()
        dispatcher.attachHost(host: IosAuthenticatedRouteDispatcher(host: host))

        _ = dispatcher.handleNotificationPayload(payload: [
            "conversation_id": "conversation-9",
            "message_id": "message-2",
        ])

        XCTAssertEqual(host.route, .chat(conversationId: "conversation-9", messageId: "message-2"))
    }

    func testAuthenticatedRouteDispatcherKeepsNonPublicRoutesExplicit() {
        let host = CapturingAuthenticatedRouteHost()
        let dispatcher = IosAuthenticatedRouteDispatcher(host: host)

        dispatcher.openNotifications()
        XCTAssertEqual(host.route, .notifications)
        dispatcher.openProfileSos()
        XCTAssertEqual(host.route, .profileSos)
        dispatcher.openCommunities()
        XCTAssertEqual(host.route, .communities)
        dispatcher.openComposer()
        XCTAssertEqual(host.route, .composer)
        dispatcher.openSettings()
        XCTAssertEqual(host.route, .settings)
    }

    func testIosContactPickerNormalizesExplicitlySelectedContact() {
        let fields = IosPickedContactFields(
            givenName: "Ada",
            middleName: "",
            familyName: "Lovelace",
            organizationName: "Analytical Engines",
            phones: [" +34 600 123 123 ", "+34 600 123 123", ""],
            emails: [" ada@example.com ", "ada@example.com", ""]
        )

        let contact = IosContactPickerMappingKt.iosPickedContactToPlatformContact(fields: fields)

        XCTAssertEqual(contact.displayName, "Ada Lovelace")
        XCTAssertEqual(contact.phones, ["+34 600 123 123"])
        XCTAssertEqual(contact.emails, ["ada@example.com"])
    }

    func testIosContactPickerExposesCancellationForEmptySelection() {
        let outcome = IosContactPickerMappingKt.iosPickedContactsOutcome(fields: [])

        XCTAssertTrue(outcome.isCancelled)
        XCTAssertTrue(outcome.contacts.isEmpty)
        XCTAssertNil(outcome.failureReason)
    }

    func testIosContactPickerExposesAdapterFailureWithoutContactsAccess() {
        let outcome = IosContactPickerMappingKt.iosContactPickerFailureOutcome(reason: "presentation_failed")

        XCTAssertFalse(outcome.isCancelled)
        XCTAssertTrue(outcome.contacts.isEmpty)
        XCTAssertEqual(outcome.failureReason, "presentation_failed")
    }

    func testQuickLookThumbnailGeneratorIsAvailableOnTheIosHost() {
        // Do not generate an asset in XCTest: Quick Look's decoding varies with simulator files.
        // This still proves that the real system API used by IosDocumentThumbnailService is linked.
        XCTAssertNotNil(QLThumbnailGenerator.shared)
    }

    func testQuickLookPreviewControllerIsAvailableOnTheIosHost() {
        // The Kotlin adapter creates this controller and retains its data source while presented.
        // Constructing it here is deterministic and verifies the host links QuickLook itself.
        XCTAssertNotNil(QLPreviewController())
    }

    func testDocumentPickerMapsPdfRtfAndOfficeMimesToUtiIdentifiers() {
        let identifiers = IosDocumentPickerHostKt.iosDocumentContentTypeIdentifiers(
            acceptedMimeTypes: [
                "application/pdf",
                "text/rtf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            ],
        )

        XCTAssertEqual(identifiers, [
            "com.adobe.pdf",
            "public.rtf",
            "org.openxmlformats.wordprocessingml.document",
            "com.microsoft.excel.xls",
            "org.openxmlformats.presentationml.presentation",
        ])
        // The mapper's output is consumed by UTType.typeWithIdentifier before presentation.
        // Check representative system declarations too, rather than constructing a picker UI.
        XCTAssertEqual(UTType.pdf.identifier, "com.adobe.pdf")
        XCTAssertEqual(UTType.rtf.identifier, "public.rtf")
    }

    func testDocumentAdaptersAdmitOnlySafeLocalFileReferences() {
        XCTAssertEqual(
            IosDocumentLocalReferenceKt.iosDocumentLocalReferenceOrNull(
                reference: " file:///private/var/mobile/Documents/report%20final.pdf ",
            ),
            "file:///private/var/mobile/Documents/report%20final.pdf",
        )
        XCTAssertEqual(
            IosDocumentLocalReferenceKt.iosDocumentLocalReferenceOrNull(
                reference: "/private/var/mobile/Documents/brief.rtf",
            ),
            "file:///private/var/mobile/Documents/brief.rtf",
        )
        XCTAssertEqual(
            IosDocumentLocalReferenceKt.iosDocumentLocalReferenceOrNull(
                reference: "file://localhost/private/var/mobile/Documents/local.pdf",
            ),
            "file://localhost/private/var/mobile/Documents/local.pdf",
        )

        [
            "https://cdn.example.test/report.pdf",
            "content://documents/report.pdf",
            "file://fileserver.example.test/share/report.pdf",
            "file://",
            "relative/report.docx",
        ].forEach { reference in
            XCTAssertNil(IosDocumentLocalReferenceKt.iosDocumentLocalReferenceOrNull(reference: reference))
        }
    }

    func testAvFoundationVideoThumbnailGeneratorIsAvailableOnTheIosHost() {
        // No media fixture is used here: simulator codec support can vary. Constructing the real
        // generator proves the API linked by IosVideoThumbnailService is present in the host.
        let asset = AVURLAsset(url: URL(fileURLWithPath: NSTemporaryDirectory()))
        let generator = AVAssetImageGenerator(asset: asset)

        XCTAssertNotNil(generator)
    }

    func testIosVideoThumbnailAdmissionBuildsBoundedFirstFrameRequestForExistingLocalReference() {
        // This existing directory deliberately is not a video fixture. The test covers only
        // admission and must not claim that AVFoundation decoded a simulator asset.
        let localDirectory = URL(fileURLWithPath: NSTemporaryDirectory()).absoluteString
        let input = IosVideoThumbnailServiceKt.inspectIosVideoThumbnailInput(
            video: PlatformFile(reference: localDirectory, displayName: "capture.mp4", mimeType: "video/mp4", sizeBytes: nil),
            maxWidth: 480,
        )

        XCTAssertEqual(input.status.name, "Ready")
        XCTAssertEqual(input.maxWidth, 480)
        XCTAssertEqual(input.requestedTimeSeconds, 0.0)
        XCTAssertEqual(input.requestedTimeScale, 600)
        XCTAssertEqual(input.sourceUrl, localDirectory)
    }

    func testIosVideoThumbnailAdmissionRejectsInvalidAndRemoteReferencesBeforeAvFoundation() {
        let malformed = IosVideoThumbnailServiceKt.inspectIosVideoThumbnailInput(
            video: PlatformFile(reference: "file://", displayName: "capture.mp4", mimeType: "video/mp4", sizeBytes: nil),
            maxWidth: 320,
        )
        let remote = IosVideoThumbnailServiceKt.inspectIosVideoThumbnailInput(
            video: PlatformFile(reference: "https://cdn.invalid/capture.mp4", displayName: "capture.mp4", mimeType: "video/mp4", sizeBytes: nil),
            maxWidth: 320,
        )

        XCTAssertEqual(malformed.status.name, "UnsafeLocalReference")
        XCTAssertEqual(remote.status.name, "UnsafeLocalReference")
    }

    func testIosVideoThumbnailAdmissionReportsStableFallbackReasonsWithoutDecodingMedia() {
        let missingPath = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("quata-thumbnail-missing-\(UUID().uuidString).mp4")
            .absoluteString
        let missing = IosVideoThumbnailServiceKt.inspectIosVideoThumbnailInput(
            video: PlatformFile(reference: missingPath, displayName: "missing.mp4", mimeType: "video/mp4", sizeBytes: nil),
            maxWidth: 320,
        )
        let invalidWidth = IosVideoThumbnailServiceKt.inspectIosVideoThumbnailInput(
            video: PlatformFile(reference: missingPath, displayName: "missing.mp4", mimeType: "video/mp4", sizeBytes: nil),
            maxWidth: 0,
        )

        XCTAssertEqual(missing.status.name, "SourceMissing")
        XCTAssertEqual(invalidWidth.status.name, "InvalidThumbnailWidth")
    }

    func testExportedComposeControllerSupportsUIKitContainment() {
        let host = UIViewController()
        host.loadViewIfNeeded()
        let composeController = QuataFeedViewControllerKt.QuataIosMigrationStatusViewController()

        host.addChild(composeController)
        composeController.view.frame = host.view.bounds
        host.view.addSubview(composeController.view)
        composeController.didMove(toParent: host)

        XCTAssertTrue(host.children.contains { $0 === composeController })
        XCTAssertTrue(composeController.parent === host)
        XCTAssertTrue(composeController.view.superview === host.view)

        composeController.willMove(toParent: nil)
        composeController.view.removeFromSuperview()
        composeController.removeFromParent()

        XCTAssertFalse(host.children.contains { $0 === composeController })
        XCTAssertNil(composeController.parent)
    }

    func testAuthLaunchFixtureContainerReplacesItsChildWithCleanUIKitContainment() throws {
        let first = UIViewController()
        let host = IosAuthLaunchFixtureContainerViewController { first }
        host.loadViewIfNeeded()

        XCTAssertEqual(host.children.count, 1)
        XCTAssertTrue(host.children.first === first)
        XCTAssertTrue(first.parent === host)
        XCTAssertTrue(first.view.superview === host.view)
        XCTAssertEqual(host.view.accessibilityIdentifier, "quata-ios-auth-launch-host")
        XCTAssertNotNil(host.view.subviews.first { $0.accessibilityIdentifier == "quata-ios-auth-launch-ready" })

        let replacement = UIViewController()
        host.replaceComposeSurface(with: replacement)

        XCTAssertEqual(host.children.count, 1)
        XCTAssertTrue(host.children.first === replacement)
        XCTAssertNil(first.parent)
        XCTAssertNil(first.view.superview)
        XCTAssertTrue(replacement.parent === host)
        XCTAssertTrue(replacement.view.superview === host.view)
    }

    func testPlatformServiceCompositionTracksOnlyItsAttachedPresenter() {
        let composition = makePlatformServiceComposition()
        let first = UIViewController()
        let unrelated = UIViewController()

        composition.attachPresenter(controller: first)
        XCTAssertTrue(composition.activeViewController() === first)

        composition.detachPresenter(controller: unrelated)
        XCTAssertTrue(composition.activeViewController() === first)

        composition.detachPresenter(controller: first)
        // The Kotlin/Objective-C bridge can expose UIKit's fallback controller when no presenter
        // is attached. The host contract is that the released controller is no longer retained.
        XCTAssertFalse(composition.activeViewController() === first)
    }

    func testHostContainerAtomicallyReplacesTheComposeSurface() throws {
        let composition = makePlatformServiceComposition()
        let host = IosFeedHostContainerViewController(platformServices: composition)
        host.loadViewIfNeeded()

        let initialController = try XCTUnwrap(host.children.first)
        XCTAssertEqual(host.children.count, 1)
        XCTAssertNil(initialController.view.accessibilityIdentifier)
        XCTAssertFalse(initialController.view.isAccessibilityElement)
        XCTAssertTrue(composition.activeViewController() === initialController)

        let authSurface = UIViewController()
        host.show(
            authSurface,
            accessibilityIdentifier: "quata-ios-auth-host",
            accessibilityLabel: "Quata iOS authentication",
        )

        XCTAssertEqual(host.children.count, 1)
        XCTAssertTrue(host.children.first === authSurface)
        XCTAssertNil(initialController.parent)
        XCTAssertNil(initialController.view.superview)
        XCTAssertEqual(authSurface.view.accessibilityIdentifier, "quata-ios-auth-host")
        XCTAssertEqual(authSurface.view.accessibilityLabel, "Quata iOS authentication")
        XCTAssertFalse(authSurface.view.isAccessibilityElement)
        XCTAssertTrue(composition.activeViewController() === authSurface)

        let feedSurface = UIViewController()
        host.show(
            feedSurface,
            accessibilityIdentifier: "quata-ios-feed-host",
            accessibilityLabel: "Quata iOS Feed",
        )

        XCTAssertEqual(host.children.count, 1)
        XCTAssertTrue(host.children.first === feedSurface)
        XCTAssertNil(authSurface.parent)
        XCTAssertNil(authSurface.view.superview)
        XCTAssertEqual(feedSurface.view.accessibilityIdentifier, "quata-ios-feed-host")
        XCTAssertEqual(feedSurface.view.accessibilityLabel, "Quata iOS Feed")
        XCTAssertFalse(feedSurface.view.isAccessibilityElement)
        XCTAssertTrue(composition.activeViewController() === feedSurface)
    }

    private func makePlatformServiceComposition() -> IosPlatformServiceComposition {
        IosPlatformServiceComposition(coreLocationHost: IosCoreLocationHost(manager: CLLocationManager()))
    }

    func testAuthenticatedRouterPresentsAQueuedChatOnlyAfterItsRealFactoryIsInstalled() {
        let services = IosPlatformServiceComposition(
            coreLocationHost: IosCoreLocationHost(manager: CLLocationManager()),
        )
        let router = IosFeedHostContainerViewController(platformServices: services)
        router.loadViewIfNeeded()
        router.installFeedFactory { _ in UIViewController() }
        let initialChildren = router.children

        router.showChat(conversationId: "conversation-7", messageId: "message-4")
        XCTAssertEqual(router.children.count, initialChildren.count)

        var receivedConversationId: String?
        var receivedMessageId: String?
        let exportedFeatureController = UIViewController()
        router.installChatFactory { conversationId, messageId in
            receivedConversationId = conversationId
            receivedMessageId = messageId
            return exportedFeatureController
        }

        XCTAssertEqual(receivedConversationId, "conversation-7")
        XCTAssertEqual(receivedMessageId, "message-4")
        XCTAssertTrue(router.children.contains { $0 === exportedFeatureController })
        XCTAssertEqual(exportedFeatureController.view.accessibilityIdentifier, "quata-ios-chat-host")
    }

    func testPublicChatDeepLinkIsPreservedUntilAuthenticatedFactoryIsInstalled() {
        let services = IosPlatformServiceComposition(
            coreLocationHost: IosCoreLocationHost(manager: CLLocationManager()),
        )
        let router = IosFeedHostContainerViewController(platformServices: services)
        router.loadViewIfNeeded()
        let initialChildren = router.children

        let routeDispatcher = IosAuthenticatedRouteDispatcher(host: router)
        let deepLinkDispatcher = IosDeepLinkDispatcher()
        deepLinkDispatcher.attachHost(host: routeDispatcher)

        _ = deepLinkDispatcher.handleUrl(url: "https://egquata.com/#chat-conversation-7?message=message-4")
        XCTAssertEqual(router.children.count, initialChildren.count)

        router.installFeedFactory { _ in UIViewController() }

        var receivedConversationId: String?
        var receivedMessageId: String?
        let exportedFeatureController = UIViewController()
        router.installChatFactory { conversationId, messageId in
            receivedConversationId = conversationId
            receivedMessageId = messageId
            return exportedFeatureController
        }

        XCTAssertEqual(receivedConversationId, "conversation-7")
        XCTAssertEqual(receivedMessageId, "message-4")
        XCTAssertTrue(router.children.contains { $0 === exportedFeatureController })
        XCTAssertEqual(exportedFeatureController.view.accessibilityIdentifier, "quata-ios-chat-host")
    }

    func testAuthenticatedRouterPresentsQueuedNotificationsOnlyAfterItsRealFactoryIsInstalled() {
        let services = IosPlatformServiceComposition(
            coreLocationHost: IosCoreLocationHost(manager: CLLocationManager()),
        )
        let router = IosFeedHostContainerViewController(platformServices: services)
        router.loadViewIfNeeded()
        router.installFeedFactory { _ in UIViewController() }
        let initialChildren = router.children

        router.showNotifications()
        XCTAssertEqual(router.children.count, initialChildren.count)

        let exportedFeatureController = UIViewController()
        router.installNotificationsFactory {
            exportedFeatureController
        }

        XCTAssertTrue(router.children.contains { $0 === exportedFeatureController })
        XCTAssertEqual(exportedFeatureController.view.accessibilityIdentifier, "quata-ios-notifications-host")
        XCTAssertEqual(exportedFeatureController.view.accessibilityLabel, "Quata iOS Notifications")
    }

    func testAuthenticatedRouterPresentsQueuedProfileSosOnlyAfterItsRealFactoryIsInstalled() {
        let services = makePlatformServiceComposition()
        let router = IosFeedHostContainerViewController(platformServices: services)
        router.loadViewIfNeeded()
        router.installFeedFactory { _ in UIViewController() }
        let initialChildren = router.children

        router.showProfileSos()
        XCTAssertEqual(router.children.count, initialChildren.count)

        let exportedFeatureController = UIViewController()
        router.installProfileSosFactory {
            exportedFeatureController
        }

        XCTAssertTrue(router.children.contains { $0 === exportedFeatureController })
        XCTAssertEqual(exportedFeatureController.view.accessibilityIdentifier, "quata-ios-profile-sos-host")
        XCTAssertEqual(exportedFeatureController.view.accessibilityLabel, "Quata iOS Profile SOS")
    }

    func testAuthenticatedRouterPresentsQueuedCommunitiesOnlyAfterItsRealFactoryIsInstalled() {
        let services = makePlatformServiceComposition()
        let router = IosFeedHostContainerViewController(platformServices: services)
        router.loadViewIfNeeded()
        router.installFeedFactory { _ in UIViewController() }
        let initialChildren = router.children

        // Communities has no public URL contract. It remains an authenticated, deferred route
        // until the launcher has real session/configuration-backed dependencies.
        router.showCommunities()
        XCTAssertEqual(router.children.count, initialChildren.count)

        let exportedFeatureController = UIViewController()
        router.installCommunitiesFactory {
            exportedFeatureController
        }

        XCTAssertTrue(router.children.contains { $0 === exportedFeatureController })
        XCTAssertEqual(exportedFeatureController.view.accessibilityIdentifier, "quata-ios-communities-host")
        XCTAssertEqual(exportedFeatureController.view.accessibilityLabel, "Quata iOS Communities")
    }

    func testAuthenticatedRouterPresentsQueuedComposerOnlyAfterItsRealFactoryIsInstalled() {
        let services = makePlatformServiceComposition()
        let router = IosFeedHostContainerViewController(platformServices: services)
        router.loadViewIfNeeded()
        router.installFeedFactory { _ in UIViewController() }
        let initialChildren = router.children

        // Composer has no public URL contract. It remains an internal authenticated route until
        // the launcher supplies the exported KMP factory and actual UIKit platform services.
        IosAuthenticatedRouteDispatcher(host: router).openComposer()
        XCTAssertEqual(router.children.count, initialChildren.count)

        let exportedFeatureController = UIViewController()
        router.installComposerFactory {
            exportedFeatureController
        }

        XCTAssertTrue(router.children.contains { $0 === exportedFeatureController })
        XCTAssertEqual(exportedFeatureController.view.accessibilityIdentifier, "quata-ios-composer-host")
        XCTAssertEqual(exportedFeatureController.view.accessibilityLabel, "Quata iOS Composer")
    }

    func testAuthenticatedRouterPresentsQueuedSettingsOnlyAfterItsLocalFactoryIsInstalled() {
        let services = makePlatformServiceComposition()
        let router = IosFeedHostContainerViewController(platformServices: services)
        router.loadViewIfNeeded()
        router.installFeedFactory { _ in UIViewController() }
        let initialChildren = router.children

        // Settings has no public URL contract. Its factory persists only local iOS preferences.
        router.showSettings()
        XCTAssertEqual(router.children.count, initialChildren.count)

        let exportedFeatureController = UIViewController()
        router.installSettingsFactory {
            exportedFeatureController
        }

        XCTAssertTrue(router.children.contains { $0 === exportedFeatureController })
        XCTAssertEqual(exportedFeatureController.view.accessibilityIdentifier, "quata-ios-settings-host")
        XCTAssertEqual(exportedFeatureController.view.accessibilityLabel, "Quata iOS Settings")
    }

    func testAuthenticatedRouterUsesEveryInstalledFactoryWithItsStableHostSemantics() {
        // The matrix intentionally uses UIKit fixtures. It protects the production composition
        // boundary (factory -> route -> host semantics) without pretending that a remote vertical
        // has completed an authenticated backend E2E flow.
        typealias RouteScenario = (
            identifier: String,
            label: String,
            expectsSharedShell: Bool,
            installAndOpen: (IosFeedHostContainerViewController, UIViewController) -> Void
        )
        let routes: [RouteScenario] = [
            ("quata-ios-feed-host", "Quata iOS Feed", true, { router, controller in
                // Installing the authenticated Feed renders its initial root immediately. Return
                // a distinct controller for the explicit post route: re-parenting one UIKit
                // instance to replace itself is invalid and would only test containment noise.
                var isInitialRequest = true
                router.installFeedFactory { _ in
                    defer { isInitialRequest = false }
                    return isInitialRequest ? UIViewController() : controller
                }
                router.showFeed(postId: "feed-1")
            }),
            ("quata-ios-chat-host", "Quata iOS Chat", true, { router, controller in
                router.installFeedFactory { _ in UIViewController() }
                router.installChatFactory { _, _ in controller }
                router.showChat(conversationId: "conversation-1", messageId: "message-1")
            }),
            ("quata-ios-official-host", "Quata iOS Official", true, { router, controller in
                router.installOfficialFactory { _ in controller }
                router.showOfficial(postId: "official-1")
            }),
            ("quata-ios-official-editor-host", "Quata iOS Official Editor", true, { router, controller in
                router.installFeedFactory { _ in UIViewController() }
                router.installOfficialEditorFactory { controller }
                router.showOfficialEditor()
            }),
            ("quata-ios-notifications-host", "Quata iOS Notifications", true, { router, controller in
                router.installFeedFactory { _ in UIViewController() }
                router.installNotificationsFactory { controller }
                router.showNotifications()
            }),
            ("quata-ios-profile-sos-host", "Quata iOS Profile SOS", true, { router, controller in
                router.installFeedFactory { _ in UIViewController() }
                router.installProfileSosFactory { controller }
                router.showProfileSos()
            }),
            ("quata-ios-communities-host", "Quata iOS Communities", true, { router, controller in
                router.installFeedFactory { _ in UIViewController() }
                router.installCommunitiesFactory { controller }
                router.showCommunities()
            }),
            ("quata-ios-composer-host", "Quata iOS Composer", true, { router, controller in
                router.installFeedFactory { _ in UIViewController() }
                router.installComposerFactory { controller }
                router.showComposer()
            }),
            ("quata-ios-settings-host", "Quata iOS Settings", true, { router, controller in
                router.installFeedFactory { _ in UIViewController() }
                router.installSettingsFactory { controller }
                router.showSettings()
            }),
            ("quata-ios-whats-new-host", "Quata iOS What's New", true, { router, controller in
                router.installFeedFactory { _ in UIViewController() }
                router.installWhatsNewFactory { controller }
                router.showWhatsNew()
            }),
            ("quata-ios-release-history-host", "Quata iOS Release History", true, { router, controller in
                router.installFeedFactory { _ in UIViewController() }
                router.installReleaseHistoryFactory { controller }
                router.showReleaseHistory()
            }),
        ]

        for (identifier, label, expectsSharedShell, installAndOpen) in routes {
            let router = IosFeedHostContainerViewController(platformServices: makePlatformServiceComposition())
            router.loadViewIfNeeded()
            let controller = UIViewController()
            installAndOpen(router, controller)

            if expectsSharedShell {
                XCTAssertTrue(authenticatedRouteController(in: router) === controller, "Installed factory did not render inside the authenticated shell: \(identifier)")
                XCTAssertEqual(router.children.count, 3)
            }
            XCTAssertEqual(controller.view.accessibilityIdentifier, identifier)
            XCTAssertEqual(controller.view.accessibilityLabel, label)
        }
    }

    func testOfficialEditorRouteResolvesOnlyAfterItsRealFactoryIsInstalled() {
        let router = IosFeedHostContainerViewController(platformServices: makePlatformServiceComposition())
        router.loadViewIfNeeded()
        let feed = UIViewController()
        let editor = UIViewController()
        router.installFeedFactory { _ in feed }

        router.showOfficialEditor()
        XCTAssertTrue(authenticatedRouteController(in: router) === feed)

        router.installOfficialEditorFactory { editor }
        XCTAssertTrue(authenticatedRouteController(in: router) === editor)
        XCTAssertEqual(editor.view.accessibilityIdentifier, "quata-ios-official-editor-host")
        XCTAssertEqual(editor.view.accessibilityLabel, "Quata iOS Official Editor")
    }

    func testOfficialEditorRouteDefersUntilAuthenticationCompletes() {
        let mounted = mountRouter()
        let router = mounted.router
        let publicFeed = UIViewController()
        let prompt = UIViewController()
        let login = UIViewController()
        let editor = UIViewController()
        router.installPublicFeed { _ in publicFeed }
        router.installAuthRequiredPromptFactory { prompt }
        router.installAuthenticationFactory { login }
        router.installOfficialEditorFactory { editor }
        let promptPresented = expectation(description: "Auth prompt presentation completed")
        router.onNextAuthPromptPresentedForTesting { promptPresented.fulfill() }

        router.showOfficialEditor()
        XCTAssertTrue(authenticatedRouteController(in: router) === publicFeed)
        XCTAssertTrue(router.presentedViewController?.children.first === prompt)
        XCTAssertEqual(router.presentedViewController?.view.accessibilityIdentifier, "quata-ios-auth-required-dialog")
        wait(for: [promptPresented], timeout: 2)

        let loginPresented = expectation(description: "Login presentation completed")
        router.onNextAuthenticationPresentedForTesting { loginPresented.fulfill() }
        router.openLoginFromAuthRequiredPrompt()
        wait(for: [loginPresented], timeout: 2)
        XCTAssertTrue(router.presentedViewController === login)
        XCTAssertEqual(router.presentedViewController?.modalPresentationStyle, .fullScreen)
        XCTAssertEqual(login.view.accessibilityIdentifier, "quata-ios-auth-host")
        XCTAssertNil(login.view.subviews.first {
            $0.accessibilityIdentifier == "quata-ios-authenticated-primary-navigation"
        })

        let authenticationFinished = expectation(description: "authentication modal dismissed")
        router.finishAuthentication {
            router.installFeedFactory { _ in UIViewController() }
            authenticationFinished.fulfill()
        }
        wait(for: [authenticationFinished], timeout: 1)
        XCTAssertTrue(authenticatedRouteController(in: router) === editor)
        XCTAssertEqual(editor.view.accessibilityIdentifier, "quata-ios-official-editor-host")
    }

    func testOfficialFactoryCallSitesUseTheDedicatedEditorCallbackContract() throws {
        let appSourceURL = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("iosApp/QuataIosApp.swift")
        let source = try String(contentsOf: appSourceURL, encoding: .utf8)

        [
            "onCreateOfficialPost: onCreateOfficialPost",
            "hasOfficialEditorFactory",
            "showOfficialEditor()",
        ].forEach { expectedCallSite in
            XCTAssertTrue(source.contains(expectedCallSite), "Missing Official editor call site: \(expectedCallSite)")
        }
    }

    func testPublicOfficialDeepLinkRendersWithoutAnAuthenticatedFeedSession() {
        let services = makePlatformServiceComposition()
        let router = IosFeedHostContainerViewController(platformServices: services)
        router.loadViewIfNeeded()

        let routeDispatcher = IosAuthenticatedRouteDispatcher(host: router)
        let deepLinkDispatcher = IosDeepLinkDispatcher()
        deepLinkDispatcher.attachHost(host: routeDispatcher)

        var receivedPostId: String?
        let exportedFeatureController = UIViewController()
        router.installOfficialFactory { postId in
            receivedPostId = postId
            return exportedFeatureController
        }

        _ = deepLinkDispatcher.handleUrl(url: "https://egquata.com/#official-public-post-7")

        XCTAssertEqual(receivedPostId, "public-post-7")
        XCTAssertTrue(router.children.contains { $0 === exportedFeatureController })
        XCTAssertEqual(exportedFeatureController.view.accessibilityIdentifier, "quata-ios-official-host")
        XCTAssertEqual(exportedFeatureController.view.accessibilityLabel, "Quata iOS Official")
    }

    func testAuthenticatedRouterBuildsTheExportedChatHostFromSharedRuntimeAndPlatformServices() {
        let services = makePlatformServiceComposition()
        let router = IosFeedHostContainerViewController(platformServices: services)
        router.loadViewIfNeeded()
        router.installFeedFactory { _ in UIViewController() }

        let feedBootstrap = IosFeedRuntimeBootstrapKt.createIosFeedRuntimeBootstrap(
            configuration: IosFeedRuntimeConfiguration(
                supabaseUrl: "https://deployment.invalid",
                supabasePublishableKey: "client-key",
            ),
        )
        let chatBootstrap = IosChatRuntimeBootstrapKt.createIosChatRuntimeBootstrap(
            configuration: IosChatRuntimeConfiguration(
                supabaseUrl: "https://deployment.invalid",
                supabasePublishableKey: "client-key",
            ),
            authSession: feedBootstrap.authSessionForInteractiveLogin(),
        )

        router.installAuthenticatedChat(chatBootstrap)
        router.showChat(conversationId: "conversation-7", messageId: "message-not-yet-positioned")

        XCTAssertEqual(router.children.count, 3)
        XCTAssertEqual(authenticatedRouteController(in: router)?.view.accessibilityIdentifier, "quata-ios-chat-host")
        XCTAssertTrue(services.activeViewController() === authenticatedRouteController(in: router))
    }

    func testAuthenticatedRouterBuildsTheExportedCommunitiesHostFromSharedRuntime() {
        let services = makePlatformServiceComposition()
        let router = IosFeedHostContainerViewController(platformServices: services)
        router.loadViewIfNeeded()
        router.installFeedFactory { _ in UIViewController() }

        let feedBootstrap = IosFeedRuntimeBootstrapKt.createIosFeedRuntimeBootstrap(
            configuration: IosFeedRuntimeConfiguration(
                supabaseUrl: "https://deployment.invalid",
                supabasePublishableKey: "client-key",
            ),
        )
        let chatBootstrap = IosChatRuntimeBootstrapKt.createIosChatRuntimeBootstrap(
            configuration: IosChatRuntimeConfiguration(
                supabaseUrl: "https://deployment.invalid",
                supabasePublishableKey: "client-key",
            ),
            authSession: feedBootstrap.authSessionForInteractiveLogin(),
        )
        let communitiesBootstrap = IosNeighborhoodsRuntimeBootstrap(
            configuration: IosNeighborhoodsRuntimeConfiguration(
                supabaseUrl: "https://deployment.invalid",
                supabasePublishableKey: "client-key",
            ),
            authSession: feedBootstrap.authSessionForInteractiveLogin(),
            chatRepository: chatBootstrap.repository(),
        )

        router.installCommunitiesFactory {
            IosNeighborhoodsHostKt.QuataNeighborhoodsViewController(
                dependencies: IosNeighborhoodsHostKt.createIosNeighborhoodsHostDependencies(
                    repository: communitiesBootstrap.repository,
                    currentUserId: communitiesBootstrap.restoredCurrentUserId(),
                    onOpenConversation: { _ in },
                    onNavigateToProfile: { _ in },
                    onAuthRequired: {},
                ),
            )
        }
        router.showCommunities()

        XCTAssertEqual(router.children.count, 3)
        XCTAssertEqual(authenticatedRouteController(in: router)?.view.accessibilityIdentifier, "quata-ios-communities-host")
        XCTAssertTrue(services.activeViewController() === authenticatedRouteController(in: router))
    }

    func testAuthenticatedRouterBuildsComposerHostWithRealPublicationAndPlatformAdapters() {
        let services = makePlatformServiceComposition()
        let router = IosFeedHostContainerViewController(platformServices: services)
        router.loadViewIfNeeded()
        router.installFeedFactory { _ in UIViewController() }
        let configuration = IosFeedRuntimeConfiguration(
            supabaseUrl: "https://project.supabase.co",
            supabasePublishableKey: "publishable-key"
        )
        let bootstrap = IosFeedRuntimeBootstrapKt.createIosFeedRuntimeBootstrap(configuration: configuration)
        let repository = ActorBoundPostComposerRepository(
            transport: IosPostComposerTransport(
                configuration: IosPostComposerRuntimeConfiguration(
                    supabaseUrl: configuration.supabaseUrl,
                    supabasePublishableKey: configuration.supabasePublishableKey,
                    wordpressBaseUrl: IosPublicRuntimeConfiguration.wordpressBaseUrl
                ),
                authSession: bootstrap.authSessionForInteractiveLogin()
            )
        )

        router.installComposerFactory {
            IosComposerHostKt.QuataComposerViewController(
                dependencies: IosComposerHostKt.createIosComposerHostDependencies(
                    repository: repository,
                    filePicker: services.services.filePicker,
                    cameraCapture: services.services.cameraCapture,
                    videoThumbnails: services.services.videoThumbnails,
                    languageTag: "en-US",
                    onClose: {},
                ),
            )
        }
        router.showComposer()

        XCTAssertEqual(router.children.count, 3)
        XCTAssertEqual(IosPublicRuntimeConfiguration.wordpressBaseUrl, "https://egquata.com/")
        XCTAssertEqual(authenticatedRouteController(in: router)?.view.accessibilityIdentifier, "quata-ios-composer-host")
        XCTAssertTrue(authenticatedRouteController(in: router)?.isViewLoaded == true)
        XCTAssertTrue(services.activeViewController() === authenticatedRouteController(in: router))
    }

    func testPrimaryRoutesHideTheSecondaryMenuAndKeepSharedChromeAboveRouteController() {
        let router = IosFeedHostContainerViewController(platformServices: makePlatformServiceComposition())
        router.loadViewIfNeeded()
        router.installFeedFactory { _ in UIViewController() }
        router.installCommunitiesFactory { UIViewController() }
        router.showCommunities()

        let routeButton = router.view.subviews.compactMap { $0 as? UIButton }.first {
            $0.accessibilityIdentifier == "quata-ios-authenticated-route-menu"
        }
        XCTAssertNotNil(routeButton)
        XCTAssertTrue(routeButton?.isHidden == true)
        let routeControllerView = router.view.subviews.first { $0.accessibilityIdentifier == "quata-ios-communities-host" }
        let topChromeView = router.view.subviews.first { $0.accessibilityIdentifier == "quata-ios-authenticated-top-chrome" }
        let primaryNavigationView = router.view.subviews.first { $0.accessibilityIdentifier == "quata-ios-authenticated-primary-navigation" }
        XCTAssertNotNil(routeControllerView)
        XCTAssertNotNil(topChromeView)
        XCTAssertNotNil(primaryNavigationView)
        XCTAssertLessThan(
            router.view.subviews.firstIndex(of: routeControllerView!)!,
            router.view.subviews.firstIndex(of: topChromeView!)!,
        )
        XCTAssertLessThan(
            router.view.subviews.firstIndex(of: routeControllerView!)!,
            router.view.subviews.firstIndex(of: primaryNavigationView!)!,
        )
    }

    func testAuthenticatedRouteMenuExposesWhatsNewOnlyAfterItsLocalFactoriesAreInstalled() {
        let router = IosFeedHostContainerViewController(platformServices: makePlatformServiceComposition())
        router.loadViewIfNeeded()

        let beforeInstall = UIAlertController(title: nil, message: nil, preferredStyle: .actionSheet)
        router.populateAuthenticatedRouteMenu(beforeInstall)
        XCTAssertFalse(beforeInstall.actions.contains { $0.title == "Novedades" })
        XCTAssertFalse(beforeInstall.actions.contains { $0.title == "Acerca de Quata" })

        router.installWhatsNewFactory { UIViewController() }
        router.installReleaseHistoryFactory { UIViewController() }
        let afterInstall = UIAlertController(title: nil, message: nil, preferredStyle: .actionSheet)
        router.populateAuthenticatedRouteMenu(afterInstall)

        XCTAssertTrue(afterInstall.actions.contains { $0.title == "Novedades" })
        XCTAssertTrue(afterInstall.actions.contains { $0.title == "Acerca de Quata" })
    }

    func testAuthenticatedRouteMenuContainsOnlyInstalledVerticals() {
        let router = IosFeedHostContainerViewController(platformServices: makePlatformServiceComposition())
        router.loadViewIfNeeded()
        router.installFeedFactory { _ in UIViewController() }
        router.installChatFactory { _, _ in UIViewController() }
        router.installOfficialFactory { _ in UIViewController() }
        router.installNotificationsFactory { UIViewController() }
        router.installProfileSosFactory { UIViewController() }
        router.installCommunitiesFactory { UIViewController() }
        router.installComposerFactory { UIViewController() }
        router.installOfficialEditorFactory { UIViewController() }
        router.installSettingsFactory { UIViewController() }

        let menu = UIAlertController(title: nil, message: nil, preferredStyle: .actionSheet)
        router.populateAuthenticatedRouteMenu(menu)

        let titles = menu.actions.compactMap(\.title)
        ["Notificaciones", "Crear publicaci\u{00F3}n", "Crear comunicado", "Ajustes", "Cerrar"].forEach {
            XCTAssertTrue(titles.contains($0), "Missing installed secondary action: \($0)")
        }
        ["Inicio", "Conversaciones", "Oficial", "Perfil y SOS", "Comunidades"].forEach {
            XCTAssertFalse(titles.contains($0), "Primary route must not remain in the UIKit secondary actions menu: \($0)")
        }
        XCTAssertEqual(titles.count, 5)
    }

    func testLogoutActionUsesOneSharedCompletionAndReturnsToPublicFeed() {
        let router = IosFeedHostContainerViewController(platformServices: makePlatformServiceComposition())
        router.loadViewIfNeeded()
        let authenticatedFeed = UIViewController()
        let publicFeed = UIViewController()
        let authenticatedOfficial = UIViewController()
        let publicOfficial = UIViewController()
        let officialEditor = UIViewController()
        router.installFeedFactory { _ in authenticatedFeed }
        router.installOfficialFactory { _ in authenticatedOfficial }
        router.installOfficialEditorFactory { officialEditor }

        var logoutInvocationCount = 0
        var completeSharedLogout: (() -> Void)?
        let returnedToPublicFeed = expectation(description: "returns to anonymous public feed")
        router.installLogoutAction(
            { completed in
                logoutInvocationCount += 1
                completeSharedLogout = completed
            },
            onLoggedOut: {
                router.installPublicFeed { _ in publicFeed }
                router.installOfficialFactory { _ in publicOfficial }
                returnedToPublicFeed.fulfill()
            },
        )

        let beforeLogout = UIAlertController(title: nil, message: nil, preferredStyle: .actionSheet)
        router.populateAuthenticatedRouteMenu(beforeLogout)
        XCTAssertTrue(beforeLogout.actions.contains { $0.title == "Crear comunicado" })
        XCTAssertTrue(beforeLogout.actions.contains {
            $0.title == "Cerrar sesión" && $0.style == .destructive
        })

        router.performLogout()
        router.performLogout()
        XCTAssertEqual(logoutInvocationCount, 1)
        XCTAssertNotNil(completeSharedLogout)

        completeSharedLogout?()
        wait(for: [returnedToPublicFeed], timeout: 3)

        XCTAssertTrue(authenticatedRouteController(in: router) === publicFeed)
        XCTAssertEqual(router.children.count, 3)
        XCTAssertNotNil(router.view.subviews.first {
            $0.accessibilityIdentifier == "quata-ios-authenticated-top-chrome"
        })
        XCTAssertNotNil(router.view.subviews.first {
            $0.accessibilityIdentifier == "quata-ios-authenticated-primary-navigation"
        })
        router.showOfficial(postId: "official-public-after-logout")
        XCTAssertTrue(authenticatedRouteController(in: router) === publicOfficial)
        XCTAssertEqual(publicOfficial.view.accessibilityIdentifier, "quata-ios-official-host")
        XCTAssertEqual(publicOfficial.view.accessibilityLabel, "Quata iOS Official")
        let routeButton = router.view.subviews.compactMap { $0 as? UIButton }.first {
            $0.accessibilityIdentifier == "quata-ios-authenticated-route-menu"
        }
        XCTAssertTrue(routeButton?.isHidden == true)
        let afterLogout = UIAlertController(title: nil, message: nil, preferredStyle: .actionSheet)
        router.populateAuthenticatedRouteMenu(afterLogout)
        XCTAssertFalse(afterLogout.actions.contains { $0.title == "Cerrar sesión" })
    }

    func testColdStartRestoredSessionInstallsLogoutAndReturnsPrivateRoutesToThePublicGate() {
        let mounted = mountRouter()
        let router = mounted.router
        let authenticatedFeed = UIViewController()
        let publicFeed = UIViewController()
        let prompt = UIViewController()
        var bindingsInstallCount = 0
        var logoutInvocationCount = 0
        let returnedToPublicFeed = expectation(description: "restored session logs out to public Feed")

        router.installPublicFeed { _ in publicFeed }
        router.installFeedFactory { _ in authenticatedFeed }
        IosAuthLifecycleBootstrap.installBindings(afterRestoredSessionAttempt: true) {
            bindingsInstallCount += 1
            router.installAuthRequiredPromptFactory { prompt }
            router.installAuthenticationFactory { UIViewController() }
            router.installLogoutAction(
                { completed in
                    logoutInvocationCount += 1
                    completed()
                },
                onLoggedOut: {
                    router.installPublicFeed { _ in publicFeed }
                    returnedToPublicFeed.fulfill()
                },
            )
        }

        XCTAssertEqual(bindingsInstallCount, 1)
        XCTAssertTrue(authenticatedRouteController(in: router) === authenticatedFeed)
        let restoredSessionMenu = UIAlertController(title: nil, message: nil, preferredStyle: .actionSheet)
        router.populateAuthenticatedRouteMenu(restoredSessionMenu)
        XCTAssertTrue(restoredSessionMenu.actions.contains {
            $0.title == "Cerrar sesión" && $0.style == .destructive
        })

        router.performLogout()
        wait(for: [returnedToPublicFeed], timeout: 3)
        XCTAssertEqual(logoutInvocationCount, 1)
        XCTAssertTrue(authenticatedRouteController(in: router) === publicFeed)

        router.showChat(conversationId: "private-after-restored-session-logout", messageId: nil)
        XCTAssertTrue(authenticatedRouteController(in: router) === publicFeed)
        XCTAssertEqual(router.presentedViewController?.view.accessibilityIdentifier, "quata-ios-auth-required-dialog")
    }

    func testOfficialEditorFactoryIsRemovedWithAuthenticatedLogoutState() {
        let router = IosFeedHostContainerViewController(platformServices: makePlatformServiceComposition())
        router.loadViewIfNeeded()
        let publicFeed = UIViewController()
        let loggedOut = expectation(description: "logged out")
        router.installFeedFactory { _ in UIViewController() }
        router.installOfficialEditorFactory { UIViewController() }
        router.installLogoutAction({ completed in completed() }, onLoggedOut: {
            router.installPublicFeed { _ in publicFeed }
            loggedOut.fulfill()
        })

        router.performLogout()
        wait(for: [loggedOut], timeout: 1)
        XCTAssertFalse(router.hasOfficialEditorFactory)
        router.showOfficialEditor()
        XCTAssertTrue(authenticatedRouteController(in: router) === publicFeed)
    }

    func testBackReturnsToInstalledAuthenticatedFeedWithoutCreatingAFallback() {
        let router = IosFeedHostContainerViewController(platformServices: makePlatformServiceComposition())
        router.loadViewIfNeeded()
        let feed = UIViewController()
        let communities = UIViewController()
        router.installFeedFactory { _ in feed }
        router.installCommunitiesFactory { communities }

        router.showCommunities()
        XCTAssertTrue(router.children.contains { $0 === communities })

        router.returnToAuthenticatedFeed()
        XCTAssertTrue(router.children.contains { $0 === feed })
        XCTAssertEqual(feed.view.accessibilityIdentifier, "quata-ios-feed-host")
        XCTAssertFalse(router.children.contains { $0 === communities })
    }

    func testForegroundRestoreKeepsDeferredDeepLinkUntilItsFactoryBecomesAvailable() throws {
        let router = IosFeedHostContainerViewController(platformServices: makePlatformServiceComposition())
        router.loadViewIfNeeded()
        let dispatcher = IosDeepLinkDispatcher()
        dispatcher.attachHost(host: IosAuthenticatedRouteDispatcher(host: router))

        _ = dispatcher.handleUrl(url: "https://egquata.com/#official-public-post-9")
        router.restoreRouteAfterForeground()
        let migrationController = try XCTUnwrap(router.children.first)
        XCTAssertEqual(router.children.count, 1)
        XCTAssertNil(migrationController.view.accessibilityIdentifier)
        XCTAssertFalse(migrationController.view.isAccessibilityElement)

        let official = UIViewController()
        router.installOfficialFactory { _ in official }
        XCTAssertEqual(router.children.count, 3)
        XCTAssertTrue(authenticatedRouteController(in: router) === official)
        XCTAssertNil(migrationController.parent)
        XCTAssertNil(migrationController.view.superview)
        XCTAssertEqual(official.view.accessibilityIdentifier, "quata-ios-official-host")
    }

    func testDeepLinkWithoutHostReportsExplicitUnsupportedCapability() {
        let result = IosDeepLinkDispatcher().handleUrl(url: "https://egquata.com/#chat-sb%3A7")

        XCTAssertTrue(String(describing: result).contains("Unsupported"))
    }

    func testAuthenticatedShellLayoutUsesSafeTopChromeAndBottomSafeAreaExactlyOnce() {
        let layout = IosAuthenticatedShellLayout.frames(
            bounds: CGRect(x: 0, y: 0, width: 402, height: 874),
            safeAreaInsets: UIEdgeInsets(top: 62, left: 0, bottom: 34, right: 0),
        )

        XCTAssertEqual(layout.topChrome, CGRect(x: 0, y: 0, width: 402, height: 130))
        XCTAssertEqual(layout.content, CGRect(x: 0, y: 130, width: 402, height: 618))
        XCTAssertEqual(layout.bottomNavigation, CGRect(x: 0, y: 748, width: 402, height: 126))
    }

    func testAuthenticatedShellLayoutKeepsLandscapeContentBetweenHorizontalSafeAreas() {
        let layout = IosAuthenticatedShellLayout.frames(
            bounds: CGRect(x: 0, y: 0, width: 874, height: 402),
            safeAreaInsets: UIEdgeInsets(top: 0, left: 62, bottom: 21, right: 21),
        )

        XCTAssertEqual(layout.topChrome, CGRect(x: 0, y: 0, width: 874, height: 68))
        XCTAssertEqual(layout.content, CGRect(x: 62, y: 68, width: 791, height: 221))
        XCTAssertEqual(layout.bottomNavigation, CGRect(x: 0, y: 289, width: 874, height: 113))
    }

}

private func authenticatedRouteController(in router: IosFeedHostContainerViewController) -> UIViewController? {
    router.children.first { controller in
        guard let identifier = controller.view.accessibilityIdentifier else { return false }
        return identifier.hasPrefix("quata-ios-") &&
            identifier != "quata-ios-authenticated-top-chrome" &&
            identifier != "quata-ios-authenticated-primary-navigation"
    }
}

/// Retains a real visible presentation context for router tests. UIKit intentionally ignores
/// `present` from a detached controller, so testing modal Auth without a UIWindow would only
/// exercise an impossible lifecycle and produce false negatives on CI.
private struct MountedRouter {
    let window: UIWindow
    let router: IosFeedHostContainerViewController
}

private func waitUntil(
    timeout: TimeInterval = 1,
    condition: () -> Bool,
) {
    let deadline = Date().addingTimeInterval(timeout)
    while !condition(), Date() < deadline {
        RunLoop.main.run(until: Date().addingTimeInterval(0.01))
    }
    XCTAssertTrue(condition(), "Timed out waiting for the UIKit modal transition")
}

private final class CapturingWhatsNewRouteHost: NSObject, IosWhatsNewRouteHost {
    var route: IosWhatsNewRoute?

    func open(route: IosWhatsNewRoute) {
        self.route = route
    }
}

private final class CapturingAuthenticatedRouteHost: NSObject, IosAuthenticatedRouteHost {
    enum Route: Equatable {
        case feed(postId: String?)
        case chat(conversationId: String, messageId: String?)
        case official(postId: String?)
        case notifications
        case profileSos
        case communities
        case composer
        case settings
        case whatsNew
        case releaseHistory
    }

    var route: Route?
    private(set) var callCount = 0

    func showFeed(postId: String?) { record(.feed(postId: postId)) }
    func showChat(conversationId: String, messageId: String?) {
        record(.chat(conversationId: conversationId, messageId: messageId))
    }
    func showOfficial(postId: String?) { record(.official(postId: postId)) }
    func showNotifications() { record(.notifications) }
    func showProfileSos() { record(.profileSos) }
    func showCommunities() { record(.communities) }
    func showComposer() { record(.composer) }
    func showSettings() { record(.settings) }
    func showWhatsNew() { record(.whatsNew) }
    func showReleaseHistory() { record(.releaseHistory) }

    private func record(_ route: Route) {
        self.route = route
        callCount += 1
    }
}
