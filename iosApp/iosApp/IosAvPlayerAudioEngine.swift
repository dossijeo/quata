import AVFoundation
import Foundation
import QuataShared

/// Production iOS playback edge for the shared Chat audio contract.
///
/// Kotlin owns the portable AudioPlayerService, Flow and generation/session identity. This
/// engine owns only the AVAudioSession activation that is not exposed through the current
/// Kotlin/Native SDK and reports playback from AVPlayer itself, never from requested intent.
final class IosAvPlayerAudioEngine: NSObject, IosNativeAudioPlaybackEngine {
    private var listener: (any IosNativeAudioPlaybackEngineListener)?
    private var player: AVPlayer?
    private var item: AVPlayerItem?
    private var endObserver: NSObjectProtocol?
    private var periodicTimeObserver: Any?
    private var statusObservation: NSKeyValueObservation?
    private var timeControlObservation: NSKeyValueObservation?
    private var playbackStartWatchdog: DispatchWorkItem?
    private var generation: Int64 = 0
    private var loaded = false
    private var durationMillis: Int64 = 0
    private var lastErrorReason: String?

    deinit {
        clearPlayer(deactivateSession: false)
    }

    func installListener(listener: (any IosNativeAudioPlaybackEngineListener)?) {
        self.listener = listener
    }

    func load(path: String, displayName: String?, mimeType: String?, sizeBytes: Int64) -> IosNativeAudioPlaybackEngineState {
        clearPlayer(deactivateSession: false)
        generation += 1
        let requestGeneration = generation
        let url = URL(fileURLWithPath: path)
        guard FileManager.default.fileExists(atPath: url.path) else {
            return state(errorReason: "audio_file_missing")
        }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default)
            try session.setActive(true)
        } catch {
            lastErrorReason = "audio_session_activation_failed"
            return state(errorReason: lastErrorReason)
        }

        let nextItem = AVPlayerItem(url: url)
        let nextPlayer = AVPlayer(playerItem: nextItem)
        nextPlayer.actionAtItemEnd = .pause

        item = nextItem
        player = nextPlayer
        loaded = true
        durationMillis = durationMillisFor(url: url, item: nextItem)
        lastErrorReason = nil
        installObservers(for: nextPlayer, item: nextItem, generation: requestGeneration)
        listener?.playbackStateChanged()
        return state()
    }

    func startPlayback() -> IosNativeAudioPlaybackEngineState {
        guard let activePlayer = player, let activeItem = item else {
            return state(errorReason: "audio_player_not_loaded")
        }
        if activeItem.status == .failed {
            lastErrorReason = errorReason(activeItem.error, fallback: "audio_player_prepare_failed")
            return state(errorReason: lastErrorReason)
        }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default)
            try session.setActive(true)
        } catch {
            lastErrorReason = "audio_session_activation_failed"
            return state(errorReason: lastErrorReason)
        }
        activePlayer.play()
        installPlaybackStartWatchdog(for: activePlayer, item: activeItem, generation: generation)
        listener?.playbackStateChanged()
        return state()
    }

    func pausePlayback() -> IosNativeAudioPlaybackEngineState {
        guard let activePlayer = player else { return state(errorReason: "audio_player_not_loaded") }
        activePlayer.pause()
        playbackStartWatchdog?.cancel()
        playbackStartWatchdog = nil
        listener?.playbackStateChanged()
        return state()
    }

    func seekPlaybackTo(positionMillis: Int64) -> IosNativeAudioPlaybackEngineState {
        guard let activePlayer = player, let activeItem = item else { return state(errorReason: "audio_player_not_loaded") }
        let boundedMillis = durationMillis > 0
            ? min(max(positionMillis, 0), durationMillis)
            : max(positionMillis, 0)
        let wasPlaying = isPlaying(activePlayer)
        let requestGeneration = generation
        activePlayer.seek(
            to: CMTime(seconds: Double(boundedMillis) / 1_000.0, preferredTimescale: 600),
            toleranceBefore: .zero,
            toleranceAfter: .zero
        ) { [weak self, weak activePlayer, weak activeItem] finished in
            guard finished,
                  let self,
                  requestGeneration == self.generation,
                  let activePlayer,
                  let activeItem,
                  activePlayer === self.player,
                  activeItem === self.item else { return }
            self.listener?.playbackStateChanged()
        }
        if wasPlaying {
            activePlayer.play()
            installPlaybackStartWatchdog(for: activePlayer, item: activeItem, generation: requestGeneration)
        }
        listener?.playbackStateChanged()
        return state()
    }

    func stopPlayback() -> IosNativeAudioPlaybackEngineState {
        clearPlayer(deactivateSession: true)
        return state()
    }

    func state() -> IosNativeAudioPlaybackEngineState {
        state(errorReason: nil)
    }

    private func installObservers(for player: AVPlayer, item: AVPlayerItem, generation requestGeneration: Int64) {
        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: item,
            queue: .main
        ) { [weak self, weak item] _ in
            guard let self,
                  requestGeneration == self.generation,
                  let item,
                  item === self.item else { return }
            self.playbackStartWatchdog?.cancel()
            self.playbackStartWatchdog = nil
            self.listener?.playbackEnded()
        }

        statusObservation = item.observe(\.status, options: [.new]) { [weak self, weak item] observedItem, _ in
            guard let self,
                  requestGeneration == self.generation,
                  let item,
                  item === self.item,
                  observedItem === item else { return }
            switch observedItem.status {
            case .failed:
                self.playbackStartWatchdog?.cancel()
                self.playbackStartWatchdog = nil
                let reason = self.errorReason(observedItem.error, fallback: "audio_player_prepare_failed")
                self.lastErrorReason = reason
                self.listener?.playbackFailed(reason: reason)
            case .readyToPlay:
                self.durationMillis = self.durationMillisFor(item: observedItem)
                self.listener?.playbackStateChanged()
            default:
                break
            }
        }

        timeControlObservation = player.observe(\.timeControlStatus, options: [.new]) { [weak self, weak player] observedPlayer, _ in
            guard let self,
                  requestGeneration == self.generation,
                  let player,
                  player === self.player,
                  observedPlayer === player else { return }
            if self.isPlaying(observedPlayer) {
                self.playbackStartWatchdog?.cancel()
                self.playbackStartWatchdog = nil
            }
            self.listener?.playbackStateChanged()
        }

        periodicTimeObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.25, preferredTimescale: 600),
            queue: .main
        ) { [weak self, weak player] _ in
            guard let self,
                  requestGeneration == self.generation,
                  let player,
                  player === self.player else { return }
            if self.isPlaying(player), self.positionMillis(for: player) > 50 {
                self.playbackStartWatchdog?.cancel()
                self.playbackStartWatchdog = nil
            }
            self.listener?.playbackStateChanged()
        }
    }

    private func installPlaybackStartWatchdog(
        for activePlayer: AVPlayer,
        item activeItem: AVPlayerItem,
        generation requestGeneration: Int64
    ) {
        playbackStartWatchdog?.cancel()
        let workItem = DispatchWorkItem { [weak self, weak activePlayer, weak activeItem] in
            guard let self,
                  requestGeneration == self.generation,
                  let activePlayer,
                  let activeItem,
                  activePlayer === self.player,
                  activeItem === self.item,
                  !self.isPlaying(activePlayer),
                  self.positionMillis(for: activePlayer) <= 50 else { return }
            guard activeItem.status == .failed || activeItem.error != nil || activePlayer.error != nil else {
                self.playbackStartWatchdog = nil
                self.listener?.playbackStateChanged()
                return
            }
            let reason = self.errorReason(activeItem.error ?? activePlayer.error, fallback: "audio_player_play_failed")
            self.lastErrorReason = reason
            self.listener?.playbackFailed(reason: reason)
        }
        playbackStartWatchdog = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + 5.0, execute: workItem)
    }

    private func state(errorReason: String? = nil) -> IosNativeAudioPlaybackEngineState {
        let activePlayer = player
        return IosNativeAudioPlaybackEngineState(
            isLoaded: loaded,
            isPlaying: activePlayer.map(isPlaying) ?? false,
            positionMillis: activePlayer.map(positionMillis) ?? 0,
            durationMillis: durationMillis,
            errorReason: errorReason ?? lastErrorReason
        )
    }

    private func isPlaying(_ player: AVPlayer) -> Bool {
        player.timeControlStatus == .playing
    }

    private func errorReason(_ error: Error?, fallback: String) -> String {
        guard let nsError = error as NSError? else { return fallback }
        let message = nsError.localizedDescription.isEmpty ? fallback : nsError.localizedDescription
        let primary = "\(message) (\(nsError.domain)#\(nsError.code))"
        guard let underlying = nsError.userInfo[NSUnderlyingErrorKey] as? NSError else { return primary }
        let underlyingMessage = underlying.localizedDescription.isEmpty
            ? "underlying_error"
            : underlying.localizedDescription
        return "\(primary); \(underlyingMessage) (\(underlying.domain)#\(underlying.code))"
    }

    private func positionMillis(for player: AVPlayer) -> Int64 {
        let seconds = CMTimeGetSeconds(player.currentTime())
        guard seconds.isFinite, seconds > 0 else { return 0 }
        return Int64(seconds * 1_000)
    }

    private func durationMillisFor(url: URL, item: AVPlayerItem) -> Int64 {
        let itemDuration = durationMillisFor(item: item)
        if itemDuration > 0 { return itemDuration }
        let seconds = CMTimeGetSeconds(AVURLAsset(url: url).duration)
        guard seconds.isFinite, seconds > 0 else { return 0 }
        return Int64(seconds * 1_000)
    }

    private func durationMillisFor(item: AVPlayerItem) -> Int64 {
        let seconds = CMTimeGetSeconds(item.duration)
        guard seconds.isFinite, seconds > 0 else { return 0 }
        return Int64(seconds * 1_000)
    }

    private func clearPlayer(deactivateSession: Bool) {
        generation += 1
        playbackStartWatchdog?.cancel()
        playbackStartWatchdog = nil
        statusObservation = nil
        timeControlObservation = nil
        if let endObserver {
            NotificationCenter.default.removeObserver(endObserver)
            self.endObserver = nil
        }
        if let periodicTimeObserver, let player {
            player.removeTimeObserver(periodicTimeObserver)
            self.periodicTimeObserver = nil
        } else {
            periodicTimeObserver = nil
        }
        player?.pause()
        player?.replaceCurrentItem(with: nil)
        player = nil
        item = nil
        loaded = false
        durationMillis = 0
        lastErrorReason = nil
        if deactivateSession {
            try? AVAudioSession.sharedInstance().setActive(false, options: [])
        }
    }
}
