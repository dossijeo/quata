import AVFoundation
import Foundation
import QuataShared

/// Production iOS playback edge for the shared Chat audio contract.
///
/// Kotlin owns the portable AudioPlayerService, Flow and generation/session identity. This
/// engine owns only the AVPlayer calls that are not exposed reliably through Kotlin/Native on
/// the current toolchain, and reports state from the native player rather than from requested
/// playback intent.
final class IosAvPlayerAudioEngine: NSObject, IosNativeAudioPlaybackEngine {
    private var listener: (any IosNativeAudioPlaybackEngineListener)?
    private var player: AVPlayer?
    private var item: AVPlayerItem?
    private var endObserver: NSObjectProtocol?
    private var failedObserver: NSObjectProtocol?
    private var loaded = false
    private var durationMillis: Int64 = 0
    private var lastErrorReason: String?

    deinit {
        clearPlayer()
    }

    func installListener(listener: (any IosNativeAudioPlaybackEngineListener)?) {
        self.listener = listener
    }

    func load(path: String, displayName: String?, mimeType: String?, sizeBytes: Int64) -> IosNativeAudioPlaybackEngineState {
        clearPlayer()
        let url = URL(fileURLWithPath: path)
        guard FileManager.default.fileExists(atPath: url.path) else {
            return state(errorReason: "audio_file_missing")
        }
        let asset = AVURLAsset(url: url)
        let durationSeconds = CMTimeGetSeconds(asset.duration)
        durationMillis = durationSeconds.isFinite && durationSeconds > 0 ? Int64(durationSeconds * 1_000) : 0
        let item = AVPlayerItem(asset: asset)
        let player = AVPlayer(playerItem: item)
        player.actionAtItemEnd = .pause
        self.item = item
        self.player = player
        loaded = true
        lastErrorReason = nil
        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: item,
            queue: .main,
        ) { [weak self] _ in
            guard let self else { return }
            self.player?.pause()
            self.listener?.playbackEnded()
        }
        failedObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemFailedToPlayToEndTime,
            object: item,
            queue: .main,
        ) { [weak self] notification in
            guard let self else { return }
            let error = notification.userInfo?[AVPlayerItemFailedToPlayToEndTimeErrorKey] as? NSError
            let reason = error?.localizedDescription ?? "audio_player_failed_to_end"
            self.lastErrorReason = reason
            self.listener?.playbackFailed(reason: reason)
        }
        return state()
    }

    func startPlayback() -> IosNativeAudioPlaybackEngineState {
        guard let player else { return state(errorReason: "audio_player_not_loaded") }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default)
            try session.setActive(true)
        } catch {
            return state(errorReason: "audio_session_activation_failed")
        }
        player.play()
        let deadline = Date().addingTimeInterval(1.0)
        while Date() < deadline && player.rate <= 0 && player.timeControlStatus != .playing {
            RunLoop.current.run(until: Date().addingTimeInterval(0.05))
        }
        guard player.rate > 0 || player.timeControlStatus == .playing else {
            let itemError = player.currentItem?.error?.localizedDescription
            let status = String(describing: player.timeControlStatus)
            let reason = itemError ?? lastErrorReason ?? "ios_avplayer_play_not_started_\(status)"
            lastErrorReason = reason
            return state(errorReason: reason)
        }
        lastErrorReason = nil
        return state()
    }

    func pausePlayback() -> IosNativeAudioPlaybackEngineState {
        guard let player else { return state(errorReason: "audio_player_not_loaded") }
        player.pause()
        return state()
    }

    func seekPlaybackTo(positionMillis: Int64) -> IosNativeAudioPlaybackEngineState {
        guard let player else { return state(errorReason: "audio_player_not_loaded") }
        let boundedMillis = durationMillis > 0
            ? min(max(positionMillis, 0), durationMillis)
            : max(positionMillis, 0)
        let wasPlaying = player.rate > 0
        player.seek(to: CMTime(seconds: Double(boundedMillis) / 1_000.0, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
        if wasPlaying {
            player.play()
        }
        return state()
    }

    func stopPlayback() -> IosNativeAudioPlaybackEngineState {
        clearPlayer()
        return state()
    }

    func state() -> IosNativeAudioPlaybackEngineState {
        state(errorReason: nil)
    }

    private func state(errorReason: String? = nil) -> IosNativeAudioPlaybackEngineState {
        let currentSeconds = player.map { CMTimeGetSeconds($0.currentTime()) } ?? 0
        let positionMillis = currentSeconds.isFinite && currentSeconds > 0 ? Int64(currentSeconds * 1_000) : 0
        return IosNativeAudioPlaybackEngineState(
            isLoaded: loaded,
            isPlaying: player.map { $0.rate > 0 || $0.timeControlStatus == .playing } ?? false,
            positionMillis: positionMillis,
            durationMillis: durationMillis,
            errorReason: errorReason ?? lastErrorReason
        )
    }

    private func clearPlayer() {
        player?.pause()
        if let endObserver {
            NotificationCenter.default.removeObserver(endObserver)
        }
        if let failedObserver {
            NotificationCenter.default.removeObserver(failedObserver)
        }
        endObserver = nil
        failedObserver = nil
        player = nil
        item = nil
        loaded = false
        durationMillis = 0
        lastErrorReason = nil
    }
}
