import AVFoundation
import Foundation
import QuartzCore
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
    let gradientLayer = CAGradientLayer()
    var playerLayer: AVPlayerLayer?

    override init(frame: CGRect) {
        super.init(frame: frame)
        gradientLayer.startPoint = CGPoint(x: 0, y: 0)
        gradientLayer.endPoint = CGPoint(x: 1, y: 1)
        layer.addSublayer(gradientLayer)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        gradientLayer.frame = bounds
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
        root.isOpaque = false
        root.backgroundColor = .clear
        imageView.frame = root.bounds
        imageView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        imageView.contentMode = .scaleAspectFill
        imageView.isOpaque = false
        imageView.backgroundColor = .clear
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
        root.isOpaque = false
        root.backgroundColor = .clear
        guard let videoURL else { reportedError = "feed_video_url_invalid"; return }
        let player = AVPlayer(url: videoURL)
        player.actionAtItemEnd = .none
        let layer = AVPlayerLayer(player: player)
        layer.frame = root.bounds
        layer.videoGravity = .resizeAspectFill
        layer.isOpaque = false
        layer.backgroundColor = UIColor.clear.cgColor
        // AVPlayerLayer can paint its own light placeholder before the first decoded frame.
        // Keep it transparent until AVFoundation confirms there is real media to display so
        // the URL-derived Compose/native gradient remains visible while loading or on failure.
        layer.opacity = 0
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

    func configureBackground(startArgb: Int32, endArgb: Int32) {
        root.gradientLayer.colors = [uiColor(argb: startArgb).cgColor, uiColor(argb: endArgb).cgColor]
    }

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
        player?.seek(
            to: CMTime(value: CMTimeValue(max(0, positionMs)), timescale: 1_000),
            toleranceBefore: .zero,
            toleranceAfter: .zero,
        )
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
        playerLayer?.opacity = playerLayer?.isReadyForDisplay == true ? 1 : 0
        let positionSeconds = CMTimeGetSeconds(player.currentTime())
        let position = positionSeconds.isFinite && positionSeconds > 0
            ? Int64(positionSeconds * 1_000)
            : 0
        let duration = feedDurationMs(for: player.currentItem)
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

    private func feedDurationMs(for item: AVPlayerItem?) -> Int64 {
        guard let item else { return 0 }
        if let duration = milliseconds(from: item.duration), duration > 0 {
            return duration
        }
        if let duration = milliseconds(from: item.asset.duration), duration > 0 {
            return duration
        }
        return item.seekableTimeRanges
            .map(\.timeRangeValue)
            .map { range in CMTimeAdd(range.start, range.duration) }
            .compactMap { milliseconds(from: $0) }
            .max() ?? 0
    }

    private func milliseconds(from time: CMTime) -> Int64? {
        guard time.isValid, !time.isIndefinite, !time.isNegativeInfinity, !time.isPositiveInfinity else {
            return nil
        }
        let seconds = CMTimeGetSeconds(time)
        return seconds.isFinite && seconds > 0 ? Int64(seconds * 1_000) : nil
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

    private func uiColor(argb: Int32) -> UIColor {
        let value = UInt32(bitPattern: argb)
        return UIColor(
            red: CGFloat((value >> 16) & 0xFF) / 255,
            green: CGFloat((value >> 8) & 0xFF) / 255,
            blue: CGFloat(value & 0xFF) / 255,
            alpha: CGFloat((value >> 24) & 0xFF) / 255
        )
    }
}
