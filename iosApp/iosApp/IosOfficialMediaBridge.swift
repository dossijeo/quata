import AVFoundation
import Foundation
import UIKit
import QuataShared

/// Native-only viewer backing the common Official overlay; it owns no pager or product controls.
final class IosOfficialMediaBridge: NSObject, IosOfficialMediaViewerFactory {
    static let shared = IosOfficialMediaBridge()
    func create(url: String, isVideo: Bool) -> any IosOfficialMediaViewerSurface {
        IosOfficialMediaSurface(url: URL(string: url), video: isVideo)
    }
}

private final class IosOfficialMediaContainer: UIView {
    var playerLayer: AVPlayerLayer?
    override func layoutSubviews() { super.layoutSubviews(); playerLayer?.frame = bounds }
}

private final class IosOfficialMediaSurface: NSObject, IosOfficialMediaViewerSurface {
    private let root = IosOfficialMediaContainer()
    private let image = UIImageView()
    private var imageTask: URLSessionDataTask?
    private var player: AVPlayer?
    private var playerLayer: AVPlayerLayer?
    private var looping = false

    init(url: URL?, video: Bool) {
        super.init(); root.clipsToBounds = true
        guard let url else { return }
        if video {
            let player = AVPlayer(url: url); player.actionAtItemEnd = .none
            let layer = AVPlayerLayer(player: player); layer.videoGravity = .resizeAspect
            root.layer.addSublayer(layer); root.playerLayer = layer; self.player = player; playerLayer = layer
            NotificationCenter.default.addObserver(self, selector: #selector(loop), name: .AVPlayerItemDidPlayToEndTime, object: player.currentItem)
            player.play()
        } else {
            image.frame = root.bounds; image.autoresizingMask = [.flexibleWidth, .flexibleHeight]; image.contentMode = .scaleAspectFit; root.addSubview(image)
            imageTask = URLSession.shared.dataTask(with: url) { [weak self] data, _, _ in
                guard let decoded = data.flatMap(UIImage.init(data:)) else { return }
                DispatchQueue.main.async { self?.image.image = decoded }
            }; imageTask?.resume()
        }
    }
    deinit { dispose() }
    func nativeView() -> UIView { root }
    func dispose() { imageTask?.cancel(); imageTask = nil; NotificationCenter.default.removeObserver(self); player?.pause(); playerLayer?.removeFromSuperlayer(); playerLayer = nil; player = nil }
    @objc private func loop() { guard let player, !looping else { return }; looping = true; player.seek(to: .zero) { [weak self] _ in player.play(); self?.looping = false } }
}
