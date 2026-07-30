import AVFoundation
import Foundation
import UIKit
import QuataShared

/// The intentionally narrow native driver behind the shared Compose Feed playback chrome.
/// It owns URL loading and AVFoundation only; Feed selection, controls and state remain Kotlin.
final class IosFeedNativeMediaFactory: NSObject, IosFeedMediaFactory {
    static let shared = IosFeedNativeMediaFactory()

    func createImage(url: String) -> any IosFeedMediaSurface {
        IosFeedNativeMediaSurface(imageURL: URL(string: url))
    }

    func createVideo(url: String) -> any IosFeedMediaSurface {
        IosFeedNativeMediaSurface(videoURL: URL(string: url))
    }
}

private final class IosFeedMediaContainerView: UIView {
    var playerLayer: AVPlayerLayer?

    override func layoutSubviews() {
        super.layoutSubviews()
        playerLayer?.frame = bounds
    }
}

private final class IosFeedNativeMediaSurface: NSObject, IosFeedMediaSurface {
    private let root = IosFeedMediaContainerView()
    private let imageView = UIImageView()
    private var imageTask: URLSessionDataTask?
    private var player: AVPlayer?
    private var playerLayer: AVPlayerLayer?
    private var active = false
    private var started = false
    private var reportedError: String?

    init(imageURL: URL?) {
        super.init()
        root.clipsToBounds = true
        imageView.frame = root.bounds
        imageView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        imageView.contentMode = .scaleAspectFill
        root.addSubview(imageView)
        guard let imageURL else { return }
        imageTask = URLSession.shared.dataTask(with: imageURL) { [weak self] data, _, _ in
            guard let image = data.flatMap(UIImage.init(data:)) else { return }
            DispatchQueue.main.async { self?.imageView.image = image }
        }
        imageTask?.resume()
    }

    init(videoURL: URL?) {
        super.init()
        root.clipsToBounds = true
        guard let videoURL else { reportedError = "feed_video_url_invalid"; return }
        let player = AVPlayer(url: videoURL)
        player.actionAtItemEnd = .none
        let layer = AVPlayerLayer(player: player)
        layer.frame = root.bounds
        layer.videoGravity = .resizeAspectFill
        root.layer.addSublayer(layer)
        root.playerLayer = layer
        self.player = player
        self.playerLayer = layer
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(loopVideo),
            name: .AVPlayerItemDidPlayToEndTime,
            object: player.currentItem,
        )
    }

    deinit { dispose() }

    func nativeView() -> UIView { root }

    func configure(isActive: Bool, isMuted: Bool, initialPositionMs: Int64) {
        guard let player else { return }
        player.isMuted = isMuted
        if initialPositionMs > 0, !started {
            player.seek(to: CMTime(value: CMTimeValue(initialPositionMs), timescale: 1_000))
        }
        active = isActive
        if isActive { play() } else { pause() }
    }

    func play() {
        active = true
        player?.play()
        started = true
    }

    func pause() {
        active = false
        player?.pause()
    }

    func seekTo(positionMs: Int64) {
        player?.seek(to: CMTime(value: CMTimeValue(max(0, positionMs)), timescale: 1_000))
    }

    func retry() {
        reportedError = nil
        if active { player?.play() }
    }

    func snapshot() -> IosFeedMediaSnapshot {
        guard let player else {
            return IosFeedMediaSnapshot(
                isPlaying: false, isBuffering: false, positionMs: 0, durationMs: 0,
                hasStartedPlayback: false, isEnded: false, error: reportedError,
            )
        }
        let positionSeconds = CMTimeGetSeconds(player.currentTime())
        let position = positionSeconds.isFinite && positionSeconds > 0
            ? Int64(positionSeconds * 1_000)
            : 0
        let durationSeconds = player.currentItem.map { CMTimeGetSeconds($0.duration) } ?? 0
        let duration = durationSeconds.isFinite && durationSeconds > 0 ? Int64(durationSeconds * 1_000) : 0
        let buffering = active && player.timeControlStatus == .waitingToPlayAtSpecifiedRate
        return IosFeedMediaSnapshot(
            isPlaying: player.timeControlStatus == .playing,
            isBuffering: buffering,
            positionMs: position,
            durationMs: duration,
            hasStartedPlayback: started,
            isEnded: false,
            error: reportedError,
        )
    }

    func dispose() {
        imageTask?.cancel()
        imageTask = nil
        NotificationCenter.default.removeObserver(self)
        player?.pause()
        playerLayer?.removeFromSuperlayer()
        root.playerLayer = nil
        playerLayer = nil
        player = nil
    }

    @objc private func loopVideo() {
        guard let player else { return }
        player.seek(to: .zero)
        if active { player.play() }
    }
}
