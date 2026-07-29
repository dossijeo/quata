import UIKit

/// UIKit-only containment shell for the deterministic Auth UI fixture.
///
/// The child is the real Kotlin/Compose Auth controller, supplied by a local-only factory. This
/// shell deliberately owns no app bootstrap state and can replace its child cleanly in unit tests.
final class IosAuthLaunchFixtureContainerViewController: UIViewController {
    private let makeAuthController: () -> UIViewController
    private var displayedController: UIViewController?
    private let readinessView = UIView()

    init(makeAuthController: @escaping () -> UIViewController) {
        self.makeAuthController = makeAuthController
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        return nil
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        view.accessibilityIdentifier = "quata-ios-auth-launch-host"
        view.accessibilityLabel = "Quata iOS Auth launch fixture"
        view.isAccessibilityElement = false

        readinessView.frame = CGRect(x: 0, y: 0, width: 1, height: 1)
        readinessView.backgroundColor = .clear
        readinessView.alpha = 0.01
        readinessView.isAccessibilityElement = true
        readinessView.accessibilityIdentifier = "quata-ios-auth-launch-ready"
        readinessView.accessibilityLabel = "Quata iOS Auth fixture ready"
        view.addSubview(readinessView)

        replaceComposeSurface(with: makeAuthController())
    }

    /// Replaces the hosted Compose controller using the complete UIKit containment lifecycle.
    func replaceComposeSurface(with controller: UIViewController) {
        let previous = displayedController
        addChild(controller)
        controller.view.frame = view.bounds
        controller.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        controller.view.isAccessibilityElement = false
        view.insertSubview(controller.view, belowSubview: readinessView)
        controller.didMove(toParent: self)

        previous?.willMove(toParent: nil)
        previous?.view.removeFromSuperview()
        previous?.removeFromParent()
        displayedController = controller
    }
}
