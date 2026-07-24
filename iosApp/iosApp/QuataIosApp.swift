import SwiftUI
import UIKit
import QuataFeed

@main
struct QuataIosApp: App {
    var body: some Scene {
        WindowGroup {
            FeedRootView()
                .ignoresSafeArea()
        }
    }
}

private struct FeedRootView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        QuataFeedViewControllerKt.QuataFeedViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
