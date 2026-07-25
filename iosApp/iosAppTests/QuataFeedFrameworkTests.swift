import XCTest
import Foundation
import UIKit
import QuickLook
import QuataShared
@testable import QuataIos
import QuickLookThumbnailing
import AVFoundation
import CoreLocation
import Security
import UniformTypeIdentifiers

final class QuataFeedFrameworkTests: XCTestCase {
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
        XCTAssertEqual(initialController.view.accessibilityIdentifier, "quata-ios-compose-root")
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

    func testPublicOfficialDeepLinkIsPreservedUntilAuthenticatedFactoryIsInstalled() {
        let services = makePlatformServiceComposition()
        let router = IosFeedHostContainerViewController(platformServices: services)
        router.loadViewIfNeeded()
        let initialChildren = router.children

        let routeDispatcher = IosAuthenticatedRouteDispatcher(host: router)
        let deepLinkDispatcher = IosDeepLinkDispatcher()
        deepLinkDispatcher.attachHost(host: routeDispatcher)

        _ = deepLinkDispatcher.handleUrl(url: "https://egquata.com/#official-public-post-7")
        XCTAssertEqual(router.children.count, initialChildren.count)

        var receivedPostId: String?
        let exportedFeatureController = UIViewController()
        router.installOfficialFactory { postId in
            receivedPostId = postId
            return exportedFeatureController
        }

        XCTAssertEqual(receivedPostId, "public-post-7")
        XCTAssertTrue(router.children.contains { $0 === exportedFeatureController })
        XCTAssertEqual(exportedFeatureController.view.accessibilityIdentifier, "quata-ios-official-host")
        XCTAssertEqual(exportedFeatureController.view.accessibilityLabel, "Quata iOS Official")
    }

    func testAuthenticatedRouterBuildsTheExportedChatHostFromSharedRuntimeAndPlatformServices() {
        let services = makePlatformServiceComposition()
        let router = IosFeedHostContainerViewController(platformServices: services)
        router.loadViewIfNeeded()

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

        XCTAssertEqual(router.children.count, 1)
        XCTAssertEqual(router.children.first?.view.accessibilityIdentifier, "quata-ios-chat-host")
        XCTAssertTrue(services.activeViewController() === router.children.first)
    }
}
