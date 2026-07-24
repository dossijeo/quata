import SwiftUI
import UIKit
import QuataFeed

@main
struct QuataIosApp: App {
    var body: some Scene {
        WindowGroup {
            IosMigrationStatusView()
                .ignoresSafeArea()
        }
    }
}

/// The Feed framework exports `QuataFeedViewController(dependencies:)` for the real host.
/// This launcher intentionally remains a migration status view until iOS has an authenticated
/// FeedRepository; constructing Android's repository or a sample repository here would make
/// the host look functional while bypassing the real backend/session boundary.
private struct IosMigrationStatusView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        QuataFeedViewControllerKt.QuataIosMigrationStatusViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
