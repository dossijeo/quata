import AVFoundation
import Foundation
import QuataShared

/// Production iOS playback edge for the shared Chat audio contract.
///
/// Kotlin owns the portable AudioPlayerService, Flow and generation/session identity. This
/// engine owns only the AVAudioSession activation that is not exposed through the current
/// Kotlin/Native SDK and reports playback from AVAudioPlayer itself, never from requested intent.
final class IosAvAudioPlayerEngine: NSObject, IosNativeAudioPlaybackEngine, AVAudioPlayerDelegate {
    private static let dataBackedPlayerMaxBytes: Int64 = 16 * 1024 * 1024

    private var listener: (any IosNativeAudioPlaybackEngineListener)?
    private var player: AVAudioPlayer?
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
        let url = URL(fileURLWithPath: path)
        guard FileManager.default.fileExists(atPath: url.path) else {
            return state(errorReason: "audio_file_missing")
        }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default)
            try session.setActive(true)
            let preparedPlayer = try makePlayer(url: url, sizeBytes: sizeBytes)
            guard preparedPlayer.prepareToPlay() else {
                lastErrorReason = "audio_player_prepare_failed"
                return state(errorReason: "audio_player_prepare_failed")
            }
            preparedPlayer.numberOfLoops = 0
            preparedPlayer.delegate = self
            player = preparedPlayer
            loaded = true
            durationMillis = Int64(max(0.0, preparedPlayer.duration) * 1_000)
            lastErrorReason = nil
            listener?.playbackStateChanged()
            return state()
        } catch {
            let reason = (error as NSError).localizedDescription.isEmpty
                ? "audio_player_prepare_failed"
                : (error as NSError).localizedDescription
            lastErrorReason = reason
            return state(errorReason: reason)
        }
    }

    func startPlayback() -> IosNativeAudioPlaybackEngineState {
        guard let activePlayer = player else { return state(errorReason: "audio_player_not_loaded") }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default)
            try session.setActive(true)
        } catch {
            lastErrorReason = "audio_session_activation_failed"
            return state(errorReason: "audio_session_activation_failed")
        }
        guard activePlayer.play() else {
            lastErrorReason = "audio_player_play_failed"
            return state(errorReason: "audio_player_play_failed")
        }
        lastErrorReason = nil
        listener?.playbackStateChanged()
        return state()
    }

    func pausePlayback() -> IosNativeAudioPlaybackEngineState {
        guard let activePlayer = player else { return state(errorReason: "audio_player_not_loaded") }
        activePlayer.pause()
        listener?.playbackStateChanged()
        return state()
    }

    func seekPlaybackTo(positionMillis: Int64) -> IosNativeAudioPlaybackEngineState {
        guard let activePlayer = player else { return state(errorReason: "audio_player_not_loaded") }
        let boundedMillis = durationMillis > 0
            ? min(max(positionMillis, 0), durationMillis)
            : max(positionMillis, 0)
        let wasPlaying = activePlayer.isPlaying
        activePlayer.currentTime = Double(boundedMillis) / 1_000.0
        if wasPlaying, !activePlayer.play() {
            lastErrorReason = "audio_player_play_failed"
            return state(errorReason: "audio_player_play_failed")
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

    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        guard player === self.player else { return }
        if flag {
            listener?.playbackEnded()
        } else {
            lastErrorReason = "audio_player_finish_failed"
            listener?.playbackFailed(reason: "audio_player_finish_failed")
        }
    }

    func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: Error?) {
        guard player === self.player else { return }
        let reason = (error as NSError?)?.localizedDescription ?? "audio_player_decode_failed"
        lastErrorReason = reason
        listener?.playbackFailed(reason: reason)
    }

    private func state(errorReason: String? = nil) -> IosNativeAudioPlaybackEngineState {
        IosNativeAudioPlaybackEngineState(
            isLoaded: loaded,
            isPlaying: player?.isPlaying ?? false,
            positionMillis: Int64(max(0.0, player?.currentTime ?? 0) * 1_000),
            durationMillis: durationMillis,
            errorReason: errorReason ?? lastErrorReason
        )
    }

    private func makePlayer(url: URL, sizeBytes: Int64) throws -> AVAudioPlayer {
        do {
            return try AVAudioPlayer(contentsOf: url)
        } catch {
            guard sizeBytes > 0, sizeBytes <= Self.dataBackedPlayerMaxBytes else { throw error }
            let data = try Data(contentsOf: url)
            return try AVAudioPlayer(data: data)
        }
    }

    private func clearPlayer(deactivateSession: Bool) {
        generation += 1
        player?.stop()
        player?.delegate = nil
        player = nil
        loaded = false
        durationMillis = 0
        lastErrorReason = nil
        if deactivateSession {
            try? AVAudioSession.sharedInstance().setActive(false, options: [])
        }
    }
}
