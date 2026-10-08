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
    let playbackAccessibility = UIView()
    let positionAccessibility = UIView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        isAccessibilityElement = false
        playbackAccessibility.isAccessibilityElement = false
        playbackAccessibility.accessibilityIdentifier = "fullscreen-media.video"
        playbackAccessibility.accessibilityLabel = "Official video"
        playbackAccessibility.isUserInteractionEnabled = false
        positionAccessibility.isAccessibilityElement = false
        positionAccessibility.accessibilityIdentifier = "official.video.position"
        positionAccessibility.accessibilityLabel = "Official video position"
        positionAccessibility.isUserInteractionEnabled = false
        addSubview(playbackAccessibility)
        addSubview(positionAccessibility)
    }

    required init?(coder: NSCoder) { nil }

    override func layoutSubviews() {
        super.layoutSubviews()
        playerLayer?.frame = bounds
        playbackAccessibility.frame = bounds
        positionAccessibility.frame = bounds
    }
}

private final class IosOfficialMediaSurface: NSObject, IosOfficialMediaViewerSurface {
    private let root = IosOfficialMediaContainer()
    private let image = UIImageView()
    private let sourceURL: URL?
    private let isVideo: Bool
    private var imageTask: URLSessionDataTask?
    private var player: AVPlayer?
    private var playerLayer: AVPlayerLayer?
    private var timeControlObservation: NSKeyValueObservation?
    private var looping = false
    private var isLoading = true
    private var reportedError: String?
    private var pendingPositionMs: Int64 = 0

    init(url: URL?, video: Bool) {
        sourceURL = url
        isVideo = video
        super.init()
        root.clipsToBounds = true
        guard url != nil else {
            isLoading = false
            reportedError = "official_media_url_invalid"
            return
        }
        if video {
            root.playbackAccessibility.isAccessibilityElement = true
            root.playbackAccessibility.accessibilityValue = "loading"
            startVideo()
        } else {
            image.frame = root.bounds
            image.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            image.contentMode = .scaleAspectFit
            root.addSubview(image)
            startImage()
        }
    }

    deinit { dispose() }
    func nativeView() -> UIView { root }

    func snapshot() -> IosOfficialMediaViewerSnapshot {
        if isVideo,
           player?.error != nil || player?.currentItem?.status == .failed {
            isLoading = false
            reportedError = "official_video_playback_failed"
            root.playbackAccessibility.accessibilityValue = "failed"
        }
        root.positionAccessibility.accessibilityValue = currentPositionMs.description
        return IosOfficialMediaViewerSnapshot(
            isPlaying: player?.timeControlStatus == .playing,
            isLoading: isLoading,
            error: reportedError,
            positionMs: currentPositionMs
        )
    }

    func seekTo(positionMs: Int64) {
        pendingPositionMs = max(0, positionMs)
        root.positionAccessibility.accessibilityValue = pendingPositionMs.description
        guard let player else { return }
        player.seek(to: CMTime(value: pendingPositionMs, timescale: 1_000))
    }

    func setPositionAccessibilityEnabled(enabled: Bool) {
        root.positionAccessibility.isAccessibilityElement = enabled && isVideo
        root.positionAccessibility.accessibilityValue = currentPositionMs.description
    }

    func retry() {
        reportedError = nil
        isLoading = true
        if isVideo { startVideo() } else { startImage() }
    }

    func dispose() {
        imageTask?.cancel()
        imageTask = nil
        tearDownPlayer()
    }

    private func startImage() {
        imageTask?.cancel()
        imageTask = nil
        image.image = nil
        guard let sourceURL else {
            isLoading = false
            reportedError = "official_media_url_invalid"
            return
        }
        isLoading = true
        reportedError = nil
        imageTask = URLSession.shared.dataTask(with: sourceURL) { [weak self] data, response, error in
            let status = (response as? HTTPURLResponse)?.statusCode
            let decoded = error == nil && status.map({ 200..<300 ~= $0 }) != false
                ? data.flatMap(UIImage.init(data:))
                : nil
            DispatchQueue.main.async {
                guard let self else { return }
                self.isLoading = false
                if let decoded {
                    self.image.image = decoded
                    self.reportedError = nil
                } else {
                    self.reportedError = "official_image_load_failed"
                }
            }
        }
        imageTask?.resume()
    }

    private func startVideo() {
        tearDownPlayer()
        guard let sourceURL else {
            isLoading = false
            reportedError = "official_media_url_invalid"
            root.playbackAccessibility.accessibilityValue = "failed"
            return
        }
        isLoading = true
        reportedError = nil
        root.playbackAccessibility.accessibilityValue = "loading"
        let player = AVPlayer(url: sourceURL)
        player.actionAtItemEnd = .none
        let layer = AVPlayerLayer(player: player)
        layer.videoGravity = .resizeAspect
        root.layer.addSublayer(layer)
        root.playerLayer = layer
        self.player = player
        playerLayer = layer
        if pendingPositionMs > 0 {
            player.seek(to: CMTime(value: pendingPositionMs, timescale: 1_000))
        }
        timeControlObservation = player.observe(\.timeControlStatus, options: [.initial, .new]) { [weak self] player, _ in
            DispatchQueue.main.async {
                guard let self else { return }
                if player.error != nil || player.currentItem?.status == .failed {
                    self.isLoading = false
                    self.reportedError = "official_video_playback_failed"
                    self.root.playbackAccessibility.accessibilityValue = "failed"
                    return
                }
                self.isLoading = player.timeControlStatus == .waitingToPlayAtSpecifiedRate
                self.root.playbackAccessibility.accessibilityValue = switch player.timeControlStatus {
                case .playing: "playing"
                case .paused: "paused"
                case .waitingToPlayAtSpecifiedRate: "loading"
                @unknown default: "unknown"
                }
            }
        }
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(loop),
            name: .AVPlayerItemDidPlayToEndTime,
            object: player.currentItem
        )
        player.play()
    }

    private func tearDownPlayer() {
        timeControlObservation?.invalidate()
        timeControlObservation = nil
        NotificationCenter.default.removeObserver(self, name: .AVPlayerItemDidPlayToEndTime, object: nil)
        player?.pause()
        playerLayer?.removeFromSuperlayer()
        root.playerLayer = nil
        playerLayer = nil
        player = nil
    }

    private var currentPositionMs: Int64 {
        guard let player else { return pendingPositionMs }
        let seconds = CMTimeGetSeconds(player.currentTime())
        guard seconds.isFinite, seconds >= 0 else { return pendingPositionMs }
        return Int64(seconds * 1_000)
    }

    @objc private func loop() { guard let player, !looping else { return }; looping = true; player.seek(to: .zero) { [weak self] _ in player.play(); self?.looping = false } }
}
