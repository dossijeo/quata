import AVFoundation
import Foundation
import QuataShared

/// Production iOS playback edge for the shared Chat audio contract.
///
/// Kotlin owns the portable AudioPlayerService, Flow and generation/session identity. This
/// engine owns only the AVAudioSession activation that is not exposed through the current
/// Kotlin/Native SDK and reports playback from AVFoundation itself, never from requested intent.
final class IosAvPlayerAudioEngine: NSObject, IosNativeAudioPlaybackEngine, AVAudioPlayerDelegate {
    private var listener: (any IosNativeAudioPlaybackEngineListener)?
    private var player: AVAudioPlayer?
    private var playbackStartWatchdog: DispatchWorkItem?
    private var generation: Int64 = 0
    private var loaded = false
    private var durationMillis: Int64 = 0
    private var lastErrorReason: String?
    private var lastPrepareDiagnostic: String?
    private static let dataBackedPlayerMaxBytes: Int64 = 50 * 1024 * 1024

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
            try activatePlaybackSession()
            lastPrepareDiagnostic = prepareDiagnostic(for: url)
            let nextPlayer = try createPreparedAudioPlayer(url: url, sizeBytes: sizeBytes)
            player = nextPlayer
            loaded = true
            durationMillis = Int64(max(0, nextPlayer.duration) * 1_000)
            lastErrorReason = nil
        } catch {
            let fallback = appendPrepareDiagnostic(to: "audio_player_prepare_failed")
            lastErrorReason = lastErrorReason ?? errorReason(error, fallback: fallback)
            return state(errorReason: lastErrorReason)
        }
        listener?.playbackStateChanged()
        return state()
    }

    private func createPreparedAudioPlayer(url: URL, sizeBytes: Int64) throws -> AVAudioPlayer {
        if sizeBytes > 0 && sizeBytes <= Self.dataBackedPlayerMaxBytes {
            do {
                let data = try Data(contentsOf: url, options: [.mappedIfSafe])
                let dataPlayer = try AVAudioPlayer(data: data)
                dataPlayer.delegate = self
                if dataPlayer.prepareToPlay() {
                    return dataPlayer
                }
                lastErrorReason = appendPrepareDiagnostic(to: "audio_player_data_prepare_failed:dataBytes=\(data.count)")
            } catch {
                lastErrorReason = errorReason(error, fallback: appendPrepareDiagnostic(to: "audio_player_data_create_failed"))
            }
        }
        let urlPlayer = try AVAudioPlayer(contentsOf: url)
        urlPlayer.delegate = self
        if urlPlayer.prepareToPlay() {
            return urlPlayer
        }
        lastErrorReason = appendPrepareDiagnostic(to: "audio_player_prepare_failed")
        throw AudioPlayerPrepareError()
    }

    func startPlayback() -> IosNativeAudioPlaybackEngineState {
        guard let activePlayer = player else {
            return state(errorReason: "audio_player_not_loaded")
        }
        do {
            try activatePlaybackSession()
        } catch {
            lastErrorReason = errorReason(error, fallback: "audio_session_activation_failed")
            return state(errorReason: lastErrorReason)
        }
        if !activePlayer.play() {
            lastErrorReason = "audio_player_play_failed"
            return state(errorReason: lastErrorReason)
        }
        installPlaybackStartWatchdog(for: activePlayer, generation: generation)
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
        guard let activePlayer = player else { return state(errorReason: "audio_player_not_loaded") }
        let boundedMillis = durationMillis > 0
            ? min(max(positionMillis, 0), durationMillis)
            : max(positionMillis, 0)
        activePlayer.currentTime = Double(boundedMillis) / 1_000.0
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
        playbackStartWatchdog?.cancel()
        playbackStartWatchdog = nil
        if flag {
            listener?.playbackEnded()
        } else {
            lastErrorReason = "audio_player_finish_failed"
            listener?.playbackFailed(reason: lastErrorReason)
        }
    }

    func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: (any Error)?) {
        guard player === self.player else { return }
        playbackStartWatchdog?.cancel()
        playbackStartWatchdog = nil
        lastErrorReason = errorReason(error, fallback: "audio_player_decode_failed")
        listener?.playbackFailed(reason: lastErrorReason)
    }

    private func installPlaybackStartWatchdog(for activePlayer: AVAudioPlayer, generation requestGeneration: Int64) {
        playbackStartWatchdog?.cancel()
        let workItem = DispatchWorkItem { [weak self, weak activePlayer] in
            guard let self,
                  requestGeneration == self.generation,
                  let activePlayer,
                  activePlayer === self.player else { return }
            if activePlayer.isPlaying || activePlayer.currentTime > 0.05 {
                self.playbackStartWatchdog = nil
                self.listener?.playbackStateChanged()
                return
            }
            self.lastErrorReason = "audio_player_play_failed"
            self.listener?.playbackFailed(reason: self.lastErrorReason)
        }
        playbackStartWatchdog = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + 5.0, execute: workItem)
    }

    private func state(errorReason: String? = nil) -> IosNativeAudioPlaybackEngineState {
        let activePlayer = player
        return IosNativeAudioPlaybackEngineState(
            isLoaded: loaded,
            isPlaying: activePlayer?.isPlaying ?? false,
            positionMillis: activePlayer.map { Int64(max(0, $0.currentTime) * 1_000) } ?? 0,
            durationMillis: durationMillis,
            errorReason: errorReason ?? lastErrorReason
        )
    }

    private func activatePlaybackSession() throws {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playback, mode: .default)
        try session.setActive(true)
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

    private func clearPlayer(deactivateSession: Bool) {
        generation += 1
        playbackStartWatchdog?.cancel()
        playbackStartWatchdog = nil
        player?.delegate = nil
        player?.stop()
        player = nil
        loaded = false
        durationMillis = 0
        lastErrorReason = nil
        lastPrepareDiagnostic = nil
        if deactivateSession {
            try? AVAudioSession.sharedInstance().setActive(false, options: [])
        }
    }

    private func appendPrepareDiagnostic(to reason: String) -> String {
        guard isEvidenceDiagnosticEnabled(), let diagnostic = lastPrepareDiagnostic else { return reason }
        return "\(reason);\(diagnostic)"
    }

    private func prepareDiagnostic(for url: URL) -> String? {
        guard isEvidenceDiagnosticEnabled() else { return nil }
        let attributes = (try? FileManager.default.attributesOfItem(atPath: url.path)) ?? [:]
        let fileSize = (attributes[.size] as? NSNumber)?.int64Value ?? -1
        let head = (try? Data(contentsOf: url, options: [.mappedIfSafe]))
            .map { data in
                data.prefix(16)
                    .map { String(format: "%02x", $0) }
                    .joined()
            } ?? "unreadable"
        return "fileSize=\(fileSize);head=\(head)"
    }

    private func isEvidenceDiagnosticEnabled() -> Bool {
        ProcessInfo.processInfo.environment["QUATA_IOS_CHAT_ATTACHMENTS_AUDIO_UI_E2E"] != nil
    }
}

private struct AudioPlayerPrepareError: Error {}
