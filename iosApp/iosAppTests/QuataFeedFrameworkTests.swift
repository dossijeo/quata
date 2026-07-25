import XCTest
import UIKit
import QuataFeed
@testable import QuataIos
import QuickLookThumbnailing

final class QuataFeedFrameworkTests: XCTestCase {
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
}
