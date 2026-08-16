import AVFoundation
import AVKit
import Foundation
import UIKit
import QuataShared

/// Decoder/playback adapter behind the common Compose Chat media overlay.
final class IosChatNativeMediaFactory: NSObject, IosChatMediaViewerFactory {
    static let shared = IosChatNativeMediaFactory()

    func create(localUrl: String, isVideo: Bool) -> any IosChatMediaViewerSurface {
        IosChatNativeMediaSurface(localURL: URL(string: localUrl), video: isVideo)
    }

    func createCloseButton(
        action: any IosChatMediaOverlayCloseAction,
        accessibilityIdentifier: String
    ) -> UIView {
        let button = IosChatNativeMediaCloseButton(type: .system)
        let target = IosChatNativeMediaCloseTarget(action: action)
        button.closeTarget = target
        button.accessibilityIdentifier = accessibilityIdentifier
        button.accessibilityLabel = accessibilityIdentifier
        button.isAccessibilityElement = true
        button.setTitle("x", for: .normal)
        button.setTitleColor(.white, for: .normal)
        button.backgroundColor = UIColor.black.withAlphaComponent(0.62)
        button.layer.cornerRadius = 22
        button.addTarget(target, action: #selector(IosChatNativeMediaCloseTarget.close), for: .touchUpInside)
        return button
    }
}

private final class IosChatNativeMediaCloseButton: UIButton {
    var closeTarget: IosChatNativeMediaCloseTarget?
}

private final class IosChatNativeMediaCloseTarget: NSObject {
    private let action: any IosChatMediaOverlayCloseAction

    init(action: any IosChatMediaOverlayCloseAction) {
        self.action = action
        super.init()
    }

    @objc func close() {
        action.close()
    }
}

private final class IosChatNativeMediaSurface: NSObject, IosChatMediaViewerSurface {
    private let root = UIView()
    private var imageView: UIImageView?
    private var player: AVPlayer?
    private var playerController: AVPlayerViewController?

    init(localURL: URL?, video: Bool) {
        super.init()
        root.backgroundColor = .black
        root.clipsToBounds = true
        guard let localURL, localURL.isFileURL else { return }
        if video {
            let player = AVPlayer(url: localURL)
            let controller = AVPlayerViewController()
            controller.player = player
            controller.showsPlaybackControls = true
            controller.videoGravity = .resizeAspect
            controller.view.frame = root.bounds
            controller.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            root.addSubview(controller.view)
            self.player = player
            self.playerController = controller
            player.play()
        } else {
            let imageView = UIImageView(frame: root.bounds)
            imageView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            imageView.contentMode = .scaleAspectFit
            imageView.image = UIImage(contentsOfFile: localURL.path)
            root.addSubview(imageView)
            self.imageView = imageView
        }
    }

    deinit { dispose() }

    func nativeView() -> UIView { root }

    func dispose() {
        player?.pause()
        playerController?.player = nil
        playerController?.view.removeFromSuperview()
        playerController = nil
        player = nil
        imageView?.image = nil
        imageView?.removeFromSuperview()
        imageView = nil
    }
}
