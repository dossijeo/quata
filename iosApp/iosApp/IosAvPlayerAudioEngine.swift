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
    private var evidenceDisplayName: String?
    private static var evidenceLogInitialized = false
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
        evidenceDisplayName = displayName
        guard FileManager.default.fileExists(atPath: url.path) else {
            recordEvidenceEvent("failed", reason: "audio_file_missing")
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
            recordEvidenceEvent("loaded")
        } catch {
            let fallback = appendPrepareDiagnostic(to: "audio_player_prepare_failed")
            lastErrorReason = lastErrorReason ?? errorReason(error, fallback: fallback)
            recordEvidenceEvent("failed", reason: lastErrorReason)
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
            recordEvidenceEvent("failed", reason: "audio_player_not_loaded")
            return state(errorReason: "audio_player_not_loaded")
        }
        do {
            try activatePlaybackSession()
        } catch {
            lastErrorReason = errorReason(error, fallback: "audio_session_activation_failed")
            recordEvidenceEvent("failed", reason: lastErrorReason)
            return state(errorReason: lastErrorReason)
        }
        if !activePlayer.play() {
            lastErrorReason = "audio_player_play_failed"
            recordEvidenceEvent("failed", reason: lastErrorReason)
            return state(errorReason: lastErrorReason)
        }
        installPlaybackStartWatchdog(for: activePlayer, generation: generation)
        recordEvidenceEvent(activePlayer.isPlaying ? "playing" : "play_requested")
        listener?.playbackStateChanged()
        return state()
    }

    func pausePlayback() -> IosNativeAudioPlaybackEngineState {
        guard let activePlayer = player else { return state(errorReason: "audio_player_not_loaded") }
        activePlayer.pause()
        playbackStartWatchdog?.cancel()
        playbackStartWatchdog = nil
        recordEvidenceEvent("paused")
        listener?.playbackStateChanged()
        return state()
    }

    func seekPlaybackTo(positionMillis: Int64) -> IosNativeAudioPlaybackEngineState {
        guard let activePlayer = player else { return state(errorReason: "audio_player_not_loaded") }
        let boundedMillis = durationMillis > 0
            ? min(max(positionMillis, 0), durationMillis)
            : max(positionMillis, 0)
        activePlayer.currentTime = Double(boundedMillis) / 1_000.0
        recordEvidenceEvent("seek")
        listener?.playbackStateChanged()
        return state()
    }

    func stopPlayback() -> IosNativeAudioPlaybackEngineState {
        clearPlayer(deactivateSession: true)
        return state()
    }

    func state() -> IosNativeAudioPlaybackEngineState {
        if isEvidenceDiagnosticEnabled(), let activePlayer = player, activePlayer.isPlaying {
            recordEvidenceEvent("progress")
        }
        return state(errorReason: nil)
    }

    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        guard player === self.player else { return }
        playbackStartWatchdog?.cancel()
        playbackStartWatchdog = nil
        if flag {
            recordEvidenceEvent("ended")
            listener?.playbackEnded()
        } else {
            lastErrorReason = "audio_player_finish_failed"
            recordEvidenceEvent("failed", reason: lastErrorReason)
            listener?.playbackFailed(reason: lastErrorReason)
        }
    }

    func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: (any Error)?) {
        guard player === self.player else { return }
        playbackStartWatchdog?.cancel()
        playbackStartWatchdog = nil
        lastErrorReason = errorReason(error, fallback: "audio_player_decode_failed")
        recordEvidenceEvent("failed", reason: lastErrorReason)
        listener?.playbackFailed(reason: lastErrorReason)
    }

    private func installPlaybackStartWatchdog(for activePlayer: AVAudioPlayer, generation requestGeneration: Int64) {
        playbackStartWatchdog?.cancel()
        let workItem = DispatchWorkItem { [weak self, weak activePlayer] in
            guard let self,
                  requestGeneration == self.generation,
                  let activePlayer,
                  activePlayer === self.player else { return }
            if activePlayer.isPlaying {
                self.playbackStartWatchdog = nil
                self.recordEvidenceEvent("playing")
                self.listener?.playbackStateChanged()
                return
            }
            self.lastErrorReason = "audio_player_play_failed"
            self.recordEvidenceEvent("failed", reason: self.lastErrorReason)
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
        if loaded || player != nil {
            recordEvidenceEvent("stopped")
        }
        player?.stop()
        player = nil
        loaded = false
        durationMillis = 0
        lastErrorReason = nil
        lastPrepareDiagnostic = nil
        evidenceDisplayName = nil
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

    private func recordEvidenceEvent(_ event: String, reason: String? = nil) {
        guard isEvidenceDiagnosticEnabled() else { return }
        let activePlayer = player
        let line = [
            "quata-ios-audio-evidence",
            "generation=\(generation)",
            "event=\(event)",
            "name=\(evidenceDisplayName ?? "")",
            "isPlaying=\(activePlayer?.isPlaying == true)",
            "positionMillis=\(activePlayer.map { Int64(max(0, $0.currentTime) * 1_000) } ?? 0)",
            "durationMillis=\(durationMillis)",
            "reason=\(reason ?? "")",
        ].joined(separator: "|")
        guard let url = evidenceLogURL() else { return }
        if !Self.evidenceLogInitialized {
            try? FileManager.default.removeItem(at: url)
            Self.evidenceLogInitialized = true
        }
        let data = Data("\(line)\n".utf8)
        if FileManager.default.fileExists(atPath: url.path),
           let handle = try? FileHandle(forWritingTo: url) {
            defer { try? handle.close() }
            _ = try? handle.seekToEnd()
            try? handle.write(contentsOf: data)
        } else {
            try? data.write(to: url, options: [.atomic])
        }
    }

    private func evidenceLogURL() -> URL? {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)
            .first?
            .appendingPathComponent("quata-ios-audio-evidence.log", isDirectory: false)
    }
}

private struct AudioPlayerPrepareError: Error {}
