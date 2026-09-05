import Foundation
import QuataShared
import UIKit

final class IosChatNativeAudioSeekAccessibilityFactory: NSObject, IosChatAudioSeekAccessibilityFactory {
    static let shared = IosChatNativeAudioSeekAccessibilityFactory()

    func create(
        action: any IosChatAudioSeekAccessibilityAction,
        accessibilityIdentifier: String,
        accessibilityLabel: String,
        progress: Float
    ) -> UIView {
        IosChatNativeAudioSeekSlider(
            action: action,
            accessibilityIdentifier: accessibilityIdentifier,
            accessibilityLabel: accessibilityLabel,
            progress: progress
        )
    }

    func update(view: UIView, accessibilityLabel: String, progress: Float) {
        guard let slider = view as? IosChatNativeAudioSeekSlider else { return }
        slider.update(accessibilityLabel: accessibilityLabel, progress: progress)
    }
}

private final class IosChatNativeAudioSeekSlider: UISlider {
    private let action: any IosChatAudioSeekAccessibilityAction

    init(
        action: any IosChatAudioSeekAccessibilityAction,
        accessibilityIdentifier: String,
        accessibilityLabel: String,
        progress: Float
    ) {
        self.action = action
        super.init(frame: .zero)
        minimumValue = 0
        maximumValue = 1
        isContinuous = false
        minimumTrackTintColor = .clear
        maximumTrackTintColor = .clear
        thumbTintColor = .clear
        isAccessibilityElement = true
        self.accessibilityIdentifier = accessibilityIdentifier
        self.accessibilityLabel = accessibilityLabel
        accessibilityTraits.insert(.adjustable)
        update(accessibilityLabel: accessibilityLabel, progress: progress)
        addTarget(self, action: #selector(valueChanged), for: .valueChanged)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func update(accessibilityLabel: String, progress: Float) {
        let bounded = min(max(progress, 0), 1)
        self.accessibilityLabel = accessibilityLabel
        accessibilityValue = "\(Int((bounded * 100).rounded()))%"
        setValue(bounded, animated: false)
    }

    @objc private func valueChanged() {
        action.seekToFraction(fraction: value)
    }
}
