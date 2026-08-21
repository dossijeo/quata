import AVFoundation
import CoreImage
import CoreText
import Foundation
import QuartzCore
import Speech
import UIKit
import QuataShared

/// Native-only AVFoundation edge for the shared post video editor.
/// Compose/Kotlin owns the editor state, timeline, crop/caption controls and export spec.
final class IosPostVideoEditorNativeDriverBridge: NSObject, IosPostVideoEditorNativeDriver {
    static let shared = IosPostVideoEditorNativeDriverBridge()
    private var activeSpeechTasks: [UUID: SFSpeechRecognitionTask] = [:]
    private var activeExportOperations: [UUID: IosPostVideoEditorExportOperation] = [:]

    func createPreview(reference: String) -> any IosPostVideoEditorPreviewSurface {
        IosPostVideoEditorPreviewSurfaceImpl(url: URL(string: reference))
    }

    func metadata(source: PlatformFile) -> IosPostVideoEditorMetadata? {
        guard let sourceUrl = URL(string: source.reference), sourceUrl.isFileURL else {
            return nil
        }
        let asset = AVURLAsset(url: sourceUrl)
        guard let videoTrack = asset.tracks(withMediaType: .video).first else {
            return nil
        }
        let transformed = videoTrack.naturalSize.applying(videoTrack.preferredTransform)
        let durationMs = max(1, Int64(CMTimeGetSeconds(asset.duration) * 1_000))
        return IosPostVideoEditorMetadata(
            durationMs: durationMs,
            width: Int32(max(1, Int(abs(transformed.width)))),
            height: Int32(max(1, Int(abs(transformed.height))))
        )
    }

    func recordCaptionStyleChange(styleId: String?) {
        Self.writeEvidenceEvent("caption_style_change", details: ["style": styleId ?? "none"])
    }

    func transcribe(
        source: PlatformFile,
        callback: any IosPostVideoEditorTranscriptCallback
    ) {
        Self.writeEvidenceEvent("transcribe_start")
        guard let sourceUrl = URL(string: source.reference), sourceUrl.isFileURL else {
            Self.writeEvidenceEvent("transcribe_source_invalid")
            callback.onFailure(reason: "ios_post_video_editor_caption_source_invalid")
            return
        }
        let localeIdentifier = Self.transcriptionLocaleIdentifier()
        Self.writeEvidenceEvent("transcribe_locale", details: ["locale": localeIdentifier])
        let recognizer = SFSpeechRecognizer(locale: Locale(identifier: localeIdentifier))
            ?? SFSpeechRecognizer(locale: Locale(identifier: "en_US"))
        guard let recognizer else {
            Self.writeEvidenceEvent("transcribe_recognizer_unavailable")
            callback.onFailure(reason: "ios_post_video_editor_caption_recognizer_unavailable")
            return
        }
        SFSpeechRecognizer.requestAuthorization { [weak self] status in
            Self.writeEvidenceEvent("transcribe_authorization", details: ["status": "\(status.rawValue)"])
            guard status == .authorized else {
                DispatchQueue.main.async {
                    callback.onFailure(reason: "ios_post_video_editor_caption_speech_permission_denied")
                }
                return
            }
            let request = SFSpeechURLRecognitionRequest(url: sourceUrl)
            request.shouldReportPartialResults = true
            if #available(iOS 13.0, *) {
                request.requiresOnDeviceRecognition = false
            }
            let taskId = UUID()
            var task: SFSpeechRecognitionTask?
            var latestWire = ""
            var didFinish = false
            let timeoutSeconds = Self.transcriptionTimeoutSeconds(for: sourceUrl)
            let timeout = DispatchWorkItem { [weak self] in
                guard !didFinish else { return }
                didFinish = true
                task?.cancel()
                self?.activeSpeechTasks.removeValue(forKey: taskId)
                DispatchQueue.main.async {
                    if latestWire.isEmpty {
                        Self.writeEvidenceEvent("transcribe_timeout_failure")
                        callback.onFailure(reason: "ios_post_video_editor_caption_transcript_timeout")
                    } else {
                        Self.writeEvidenceEvent("transcribe_timeout_success", details: ["wireLines": "\(latestWire.components(separatedBy: .newlines).count)"])
                        callback.onSuccess(text: latestWire)
                    }
                }
            }
            let finish: (String?, String?) -> Void = { [weak self] wire, failure in
                guard !didFinish else { return }
                didFinish = true
                timeout.cancel()
                task?.cancel()
                self?.activeSpeechTasks.removeValue(forKey: taskId)
                DispatchQueue.main.async {
                    if let wire, !wire.isEmpty {
                        Self.writeEvidenceEvent("transcribe_success", details: ["wireLines": "\(wire.components(separatedBy: .newlines).count)"])
                        callback.onSuccess(text: wire)
                    } else {
                        Self.writeEvidenceEvent("transcribe_failure", details: ["reason": failure ?? "ios_post_video_editor_caption_transcript_failed"])
                        callback.onFailure(reason: failure ?? "ios_post_video_editor_caption_transcript_failed")
                    }
                }
            }
            task = recognizer.recognitionTask(with: request) { result, error in
                if let result {
                    latestWire = Self.captionWire(from: result.bestTranscription)
                    Self.writeEvidenceEvent("transcribe_result", details: [
                        "isFinal": "\(result.isFinal)",
                        "segments": "\(result.bestTranscription.segments.count)",
                    ])
                    if result.isFinal {
                        finish(
                            latestWire,
                            latestWire.isEmpty ? "ios_post_video_editor_caption_transcript_missing" : nil
                        )
                        return
                    }
                }
                if let error {
                    let reason = error.localizedDescription.isEmpty
                        ? "ios_post_video_editor_caption_transcript_failed"
                        : error.localizedDescription
                    finish(latestWire.isEmpty ? nil : latestWire, reason)
                }
            }
            if let task {
                self?.activeSpeechTasks[taskId] = task
                Self.writeEvidenceEvent("transcribe_task_created", details: ["timeoutSeconds": "\(timeoutSeconds)"])
                DispatchQueue.global(qos: .utility).asyncAfter(
                    deadline: .now() + timeoutSeconds,
                    execute: timeout
                )
            } else {
                timeout.cancel()
                Self.writeEvidenceEvent("transcribe_task_missing")
                DispatchQueue.main.async {
                    callback.onFailure(reason: "ios_post_video_editor_caption_transcript_failed")
                }
            }
        }
    }

    private static func transcriptionLocaleIdentifier() -> String {
        let override = ProcessInfo.processInfo.environment["QUATA_IOS_POST_VIDEO_EDITOR_TRANSCRIPTION_LOCALE"]?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        if let override, !override.isEmpty {
            return override
        }
        return Locale.preferredLanguages.first ?? Locale.current.identifier
    }

    private static func transcriptionTimeoutSeconds(for sourceUrl: URL) -> Double {
        let asset = AVURLAsset(url: sourceUrl)
        let duration = CMTimeGetSeconds(asset.duration)
        guard duration.isFinite, duration > 0 else { return 30 }
        return max(20, min(90, duration * 3 + 10))
    }

    static func writeEvidenceEvent(_ event: String, details: [String: String] = [:]) {
        guard let diagnosticsPath = ProcessInfo.processInfo.environment["QUATA_IOS_POST_VIDEO_EDITOR_EXPORT_DIAGNOSTICS"],
              !diagnosticsPath.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return
        }
        var payload = details
        payload["event"] = event
        payload["timestamp"] = ISO8601DateFormatter().string(from: Date())
        guard let data = try? JSONSerialization.data(withJSONObject: payload, options: [.sortedKeys]),
              let line = String(data: data, encoding: .utf8) else {
            return
        }
        let url = URL(fileURLWithPath: diagnosticsPath + ".events.jsonl")
        do {
            try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
            if FileManager.default.fileExists(atPath: url.path),
               let handle = try? FileHandle(forWritingTo: url) {
                try handle.seekToEnd()
                try handle.write(contentsOf: Data((line + "\n").utf8))
                try handle.close()
            } else {
                try (line + "\n").write(to: url, atomically: true, encoding: .utf8)
            }
        } catch {
            // Evidence-only diagnostics must never alter app behavior.
        }
    }

    private static func captionWire(from transcription: SFTranscription) -> String {
        transcription.segments.compactMap { segment -> String? in
            let text = segment.substring
                .replacingOccurrences(of: "\t", with: " ")
                .replacingOccurrences(of: "\n", with: " ")
                .trimmingCharacters(in: .whitespacesAndNewlines)
            guard !text.isEmpty else { return nil }
            let startMs = max(0, Int64((segment.timestamp * 1_000).rounded()))
            let endMs = max(startMs + 1, Int64(((segment.timestamp + segment.duration) * 1_000).rounded()))
            let confidence = max(0, min(1, segment.confidence))
            return "\(text)\t\(startMs)\t\(endMs)\t\(confidence)"
        }.joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
    }

    func export(
        source: PlatformFile,
        request: IosPostVideoEditorExportRequest,
        callback: any IosPostVideoEditorExportCallback
    ) {
        guard let sourceUrl = URL(string: source.reference), sourceUrl.isFileURL else {
            callback.onFailure(reason: "ios_post_video_editor_source_invalid")
            return
        }
        let operationId = UUID()
        let exporter = IosPostVideoEditorExportOperation(
            sourceUrl: sourceUrl,
            request: request,
            callback: callback,
            onFinished: { [weak self] in
                self?.activeExportOperations.removeValue(forKey: operationId)
            }
        )
        activeExportOperations[operationId] = exporter
        DispatchQueue.main.async {
            exporter.start()
        }
    }

    func cancelExport() {
        let operations = Array(activeExportOperations.values)
        activeExportOperations.removeAll()
        operations.forEach { $0.cancel() }
        let speechTasks = Array(activeSpeechTasks.values)
        activeSpeechTasks.removeAll()
        speechTasks.forEach { $0.cancel() }
        Self.writeEvidenceEvent("export_cancel_requested", details: [
            "operations": "\(operations.count)",
            "speechTasks": "\(speechTasks.count)",
        ])
    }
}

private final class IosPostVideoEditorPreviewView: UIView {
    var backgroundLayer: AVPlayerLayer?
    var foregroundLayer: AVPlayerLayer?
    var onLayout: (() -> Void)?
    let blurView = UIVisualEffectView(effect: UIBlurEffect(style: .dark))
    let veilView = UIView()
    let foregroundHost = UIView()
    let cropOverlayView = UIView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        clipsToBounds = true
        isOpaque = false
        backgroundColor = .black
        blurView.isUserInteractionEnabled = false
        veilView.isUserInteractionEnabled = false
        veilView.backgroundColor = UIColor.black.withAlphaComponent(0.18)
        foregroundHost.isUserInteractionEnabled = false
        foregroundHost.clipsToBounds = true
        foregroundHost.backgroundColor = .clear
        cropOverlayView.isUserInteractionEnabled = false
        cropOverlayView.layer.borderColor = UIColor(red: 1, green: 0.48, blue: 0.09, alpha: 1).cgColor
        cropOverlayView.layer.borderWidth = 3
        cropOverlayView.isHidden = true
        addSubview(blurView)
        addSubview(veilView)
        addSubview(foregroundHost)
        addSubview(cropOverlayView)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        onLayout?()
    }
}

private final class IosPostVideoEditorPreviewSurfaceImpl: NSObject, IosPostVideoEditorPreviewSurface {
    private struct PreviewCropConfiguration {
        let videoAspectRatio: CGFloat
        let left: CGFloat
        let top: CGFloat
        let right: CGFloat
        let bottom: CGFloat
        let visible: Bool
    }

    private let root = IosPostVideoEditorPreviewView()
    private var player: AVPlayer?
    private var backgroundPlayer: AVPlayer?
    private var foregroundLayer: AVPlayerLayer?
    private var backgroundLayer: AVPlayerLayer?
    private var timeObserver: Any?
    private var lastCropConfiguration = PreviewCropConfiguration(
        videoAspectRatio: 9.0 / 16.0,
        left: 0,
        top: 0,
        right: 1,
        bottom: 1,
        visible: false
    )

    init(url: URL?) {
        super.init()
        root.onLayout = { [weak self] in
            self?.applyCropPreview()
        }
        guard let url else { return }
        let player = AVPlayer(url: url)
        let backgroundPlayer = AVPlayer(url: url)
        player.actionAtItemEnd = .pause
        backgroundPlayer.actionAtItemEnd = .pause
        let backgroundLayer = AVPlayerLayer(player: backgroundPlayer)
        backgroundLayer.videoGravity = .resizeAspectFill
        backgroundLayer.opacity = 0.78
        let foregroundLayer = AVPlayerLayer(player: player)
        foregroundLayer.videoGravity = .resizeAspect
        foregroundLayer.isOpaque = false
        foregroundLayer.backgroundColor = UIColor.clear.cgColor
        root.layer.insertSublayer(backgroundLayer, at: 0)
        root.foregroundHost.layer.addSublayer(foregroundLayer)
        root.backgroundLayer = backgroundLayer
        root.foregroundLayer = foregroundLayer
        self.player = player
        self.backgroundPlayer = backgroundPlayer
        self.foregroundLayer = foregroundLayer
        self.backgroundLayer = backgroundLayer
    }

    func nativeView() -> UIView { root }

    func configure(
        isPlaying: Bool,
        isMuted: Bool,
        positionMs: Int64,
        trimStartMs: Int64,
        trimEndMs: Int64,
        durationMs: Int64,
        videoAspectRatio: Float,
        cropLeft: Float,
        cropTop: Float,
        cropRight: Float,
        cropBottom: Float,
        cropVisible: Bool,
        callback: any IosPostVideoEditorPreviewCallback
    ) {
        guard let player else { return }
        let backgroundPlayer = backgroundPlayer
        player.isMuted = isMuted
        backgroundPlayer?.isMuted = true
        let trimStart = max(0, trimStartMs)
        let trimEnd = max(trimStart + 50, trimEndMs > 0 ? trimEndMs : durationMs)
        let currentMs = Int64(max(0, CMTimeGetSeconds(player.currentTime()) * 1_000))
        let outsideTrim = currentMs < trimStart - 50 || currentMs >= trimEnd + 50
        let shouldSeekToState = !isPlaying && abs(currentMs - max(0, positionMs)) > 120
        let shouldSeekToTrimStart = isPlaying && outsideTrim
        if shouldSeekToState || shouldSeekToTrimStart {
            let targetMs = shouldSeekToTrimStart ? trimStart : max(0, positionMs)
            let target = CMTime(value: CMTimeValue(targetMs), timescale: 1_000)
            player.seek(to: target, toleranceBefore: .zero, toleranceAfter: .zero)
            backgroundPlayer?.seek(to: target, toleranceBefore: .zero, toleranceAfter: .zero)
        }
        if let timeObserver {
            player.removeTimeObserver(timeObserver)
            self.timeObserver = nil
        }
        if isPlaying {
            player.play()
            backgroundPlayer?.play()
            let interval = CMTime(value: 120, timescale: 1_000)
            timeObserver = player.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self, weak player, weak backgroundPlayer, callback] time in
                guard let player else { return }
                let current = Int64(max(0, CMTimeGetSeconds(time) * 1_000))
                if current >= trimEnd || current < trimStart - 50 {
                    let target = CMTime(value: CMTimeValue(trimStart), timescale: 1_000)
                    player.seek(to: target, toleranceBefore: .zero, toleranceAfter: .zero)
                    backgroundPlayer?.seek(to: target, toleranceBefore: .zero, toleranceAfter: .zero)
                    player.play()
                    backgroundPlayer?.play()
                    callback.onPositionMs(positionMs: trimStart)
                } else {
                    callback.onPositionMs(positionMs: min(max(0, current), max(1, durationMs)))
                }
                self?.applyCropPreview()
            }
        } else {
            player.pause()
            backgroundPlayer?.pause()
            callback.onPositionMs(positionMs: min(max(0, positionMs), max(1, durationMs)))
        }
        lastCropConfiguration = PreviewCropConfiguration(
            videoAspectRatio: CGFloat(videoAspectRatio),
            left: CGFloat(cropLeft),
            top: CGFloat(cropTop),
            right: CGFloat(cropRight),
            bottom: CGFloat(cropBottom),
            visible: cropVisible
        )
        applyCropPreview()
    }

    private func applyCropPreview() {
        let videoAspectRatio = lastCropConfiguration.videoAspectRatio
        let left = lastCropConfiguration.left
        let top = lastCropConfiguration.top
        let right = lastCropConfiguration.right
        let bottom = lastCropConfiguration.bottom
        let visible = lastCropConfiguration.visible
        let safeLeft = left.clamped(to: 0...0.99)
        let safeTop = top.clamped(to: 0...0.99)
        let safeRight = right.clamped(to: (safeLeft + 0.01)...1)
        let safeBottom = bottom.clamped(to: (safeTop + 0.01)...1)
        let cropWidth = max(0.01, safeRight - safeLeft)
        let cropHeight = max(0.01, safeBottom - safeTop)
        let outputAspect = CGFloat(720.0 / 1280.0)
        let safeVideoAspect = max(0.1, videoAspectRatio)
        let rootSize = CGSize(width: max(1, root.bounds.width), height: max(1, root.bounds.height))
        var viewportSize = CGSize(width: rootSize.height * outputAspect, height: rootSize.height)
        if viewportSize.width > rootSize.width {
            viewportSize = CGSize(width: rootSize.width, height: rootSize.width / outputAspect)
        }
        let viewport = CGRect(
            x: (rootSize.width - viewportSize.width) / 2,
            y: (rootSize.height - viewportSize.height) / 2,
            width: viewportSize.width,
            height: viewportSize.height
        )
        let appliedLeft = visible ? 0 : safeLeft
        let appliedTop = visible ? 0 : safeTop
        let appliedWidth = visible ? 1 : cropWidth
        let appliedHeight = visible ? 1 : cropHeight
        let foregroundAspect = max(0.1, safeVideoAspect * appliedWidth / appliedHeight)
        var foregroundSize = CGSize(width: viewport.height * foregroundAspect, height: viewport.height)
        if foregroundSize.width > viewport.width {
            foregroundSize = CGSize(width: viewport.width, height: viewport.width / foregroundAspect)
        }
        let foregroundFrame = CGRect(
            x: viewport.minX + (viewport.width - foregroundSize.width) / 2,
            y: viewport.minY + (viewport.height - foregroundSize.height) / 2,
            width: foregroundSize.width,
            height: foregroundSize.height
        )
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        backgroundLayer?.frame = viewport.insetBy(dx: -viewport.width * 0.04, dy: -viewport.height * 0.04)
        root.blurView.frame = viewport
        root.veilView.frame = viewport
        root.foregroundHost.frame = foregroundFrame
        foregroundLayer?.frame = CGRect(
            x: -appliedLeft / appliedWidth * foregroundFrame.width,
            y: -appliedTop / appliedHeight * foregroundFrame.height,
            width: foregroundFrame.width / appliedWidth,
            height: foregroundFrame.height / appliedHeight
        )
        root.cropOverlayView.isHidden = !visible
        root.cropOverlayView.frame = CGRect(
            x: foregroundFrame.minX + safeLeft * foregroundFrame.width,
            y: foregroundFrame.minY + safeTop * foregroundFrame.height,
            width: cropWidth * foregroundFrame.width,
            height: cropHeight * foregroundFrame.height
        )
        CATransaction.commit()
    }

    func dispose() {
        if let timeObserver, let player {
            player.removeTimeObserver(timeObserver)
        }
        timeObserver = nil
        player?.pause()
        backgroundPlayer?.pause()
        foregroundLayer?.removeFromSuperlayer()
        backgroundLayer?.removeFromSuperlayer()
        root.foregroundLayer = nil
        root.backgroundLayer = nil
        foregroundLayer = nil
        backgroundLayer = nil
        player = nil
        backgroundPlayer = nil
    }
}

private final class IosPostVideoEditorExportOperation {
    private let sourceUrl: URL
    private let request: IosPostVideoEditorExportRequest
    private let callback: any IosPostVideoEditorExportCallback
    private let onFinished: () -> Void
    private var exportSession: AVAssetExportSession?
    private var captionExportSession: AVAssetExportSession?
    private var progressTimer: Timer?
    private var didFinish = false
    private var didCancel = false

    init(
        sourceUrl: URL,
        request: IosPostVideoEditorExportRequest,
        callback: any IosPostVideoEditorExportCallback,
        onFinished: @escaping () -> Void
    ) {
        self.sourceUrl = sourceUrl
        self.request = request
        self.callback = callback
        self.onFinished = onFinished
    }

    func start() {
        IosPostVideoEditorNativeDriverBridge.writeEvidenceEvent("export_start", details: [
            "mainThread": Thread.isMainThread ? "true" : "false",
        ])
        let asset = AVURLAsset(url: sourceUrl)
        guard let videoTrack = asset.tracks(withMediaType: .video).first else {
            IosPostVideoEditorNativeDriverBridge.writeEvidenceEvent("export_video_track_missing")
            finishFailure(reason: "ios_post_video_editor_video_track_missing")
            return
        }
        let actualDurationMs = max(500, Int64(CMTimeGetSeconds(asset.duration) * 1_000))
        let hintedDurationMs = max(1, request.sourceDurationMs)
        let scale: Double = hintedDurationMs > actualDurationMs * 3 / 2
            ? Double(actualDurationMs) / Double(hintedDurationMs)
            : 1
        let startMs = min(
            max(0, Int64(Double(request.trimStartMs) * scale)),
            max(0, actualDurationMs - 500)
        )
        let requestedEndMs = max(startMs + 500, Int64(Double(request.trimEndMs) * scale))
        let endMs = min(actualDurationMs, requestedEndMs)
        let range = CMTimeRange(
            start: CMTime(value: CMTimeValue(startMs), timescale: 1_000),
            duration: CMTime(value: CMTimeValue(max(500, endMs - startMs)), timescale: 1_000)
        )
        let composition = AVMutableComposition()
        guard let compositionVideo = composition.addMutableTrack(
            withMediaType: .video,
            preferredTrackID: kCMPersistentTrackID_Invalid
        ) else {
            IosPostVideoEditorNativeDriverBridge.writeEvidenceEvent("export_composition_video_track_failed")
            finishFailure(reason: "ios_post_video_editor_composition_video_track_failed")
            return
        }
        let needsBackgroundTrack = request.hasBackgroundCrop
        let compositionBackground = needsBackgroundTrack
            ? composition.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid)
            : nil
        do {
            try compositionVideo.insertTimeRange(range, of: videoTrack, at: .zero)
            if let compositionBackground {
                try compositionBackground.insertTimeRange(range, of: videoTrack, at: .zero)
            }
        } catch {
            IosPostVideoEditorNativeDriverBridge.writeEvidenceEvent("export_video_insert_failed")
            finishFailure(reason: "ios_post_video_editor_video_insert_failed")
            return
        }
        compositionVideo.preferredTransform = videoTrack.preferredTransform
        compositionBackground?.preferredTransform = videoTrack.preferredTransform
        if !request.removeAudio, let audioTrack = asset.tracks(withMediaType: .audio).first,
           let compositionAudio = composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid) {
            try? compositionAudio.insertTimeRange(range, of: audioTrack, at: .zero)
        }
        let captionDocument = CaptionDocumentWire.parse(request.captionDocumentWire)
        if let captionStyle = request.captionStyle, !captionStyle.isEmpty, captionDocument == nil {
            IosPostVideoEditorNativeDriverBridge.writeEvidenceEvent("export_caption_transcript_missing")
            finishFailure(reason: "ios_post_video_editor_caption_transcript_missing")
            return
        }

        let outputUrl = temporaryOutputUrl(suffix: "base")
        let finalOutputUrl = temporaryOutputUrl(suffix: "final")
        try? FileManager.default.removeItem(at: outputUrl)
        try? FileManager.default.removeItem(at: finalOutputUrl)
        let exportPresetName = exportPresetName()
        guard let exportSession = AVAssetExportSession(asset: composition, presetName: exportPresetName) else {
            IosPostVideoEditorNativeDriverBridge.writeEvidenceEvent("export_session_unavailable")
            finishFailure(reason: "ios_post_video_editor_export_session_unavailable")
            return
        }
        self.exportSession = exportSession
        exportSession.outputURL = outputUrl
        exportSession.outputFileType = .mp4
        exportSession.shouldOptimizeForNetworkUse = true
        exportSession.timeRange = CMTimeRange(start: .zero, duration: range.duration)
        exportSession.videoComposition = if needsBackgroundTrack {
            makeBlurredBackgroundVideoComposition(asset: composition, duration: range.duration)
        } else {
            makeVideoComposition(
                track: videoTrack,
                foregroundTrack: compositionVideo,
                backgroundTrack: compositionBackground,
                duration: range.duration
            )
        }
        startProgressTimer(session: exportSession, floor: 0.35, ceiling: captionDocument == nil ? 0.95 : 0.72)
        IosPostVideoEditorNativeDriverBridge.writeEvidenceEvent("export_session_started", details: [
            "durationMs": "\(Int64(CMTimeGetSeconds(range.duration) * 1_000))",
            "captionDocument": request.captionDocumentWire?.isEmpty == false ? "true" : "false",
            "outputWidth": "\(request.outputWidth)",
            "outputHeight": "\(request.outputHeight)",
            "maxFrameRate": "\(request.outputMaxFrameRate)",
            "targetBitrate": "\(request.outputTargetBitrate)",
            "exportPreset": exportPresetName,
        ])
        exportSession.exportAsynchronously { [callback] in
            DispatchQueue.main.async {
                switch exportSession.status {
                case .completed:
                    if self.didCancel {
                        self.finishFailure(reason: "ios_post_video_editor_export_cancelled")
                        return
                    }
                    if let captionStyle = self.request.captionStyle, !captionStyle.isEmpty, let captionDocument {
                        self.burnCaptions(
                            inputUrl: outputUrl,
                            outputUrl: finalOutputUrl,
                            captionStyle: captionStyle,
                            captionDocument: captionDocument,
                            callback: callback
                        )
                    } else {
                        self.finishSuccess(outputUrl: outputUrl, callback: callback)
                    }
                case .failed, .cancelled:
                    let reason = self.didCancel
                        ? "ios_post_video_editor_export_cancelled"
                        : exportSession.error?.localizedDescription ?? "ios_post_video_editor_export_failed"
                    IosPostVideoEditorNativeDriverBridge.writeEvidenceEvent("export_failed", details: ["reason": reason])
                    self.finishFailure(reason: reason)
                default:
                    IosPostVideoEditorNativeDriverBridge.writeEvidenceEvent("export_incomplete", details: ["status": "\(exportSession.status.rawValue)"])
                    self.finishFailure(reason: "ios_post_video_editor_export_incomplete")
                }
            }
        }
    }

    func cancel() {
        guard !didFinish else { return }
        didCancel = true
        exportSession?.cancelExport()
        captionExportSession?.cancelExport()
        finishFailure(reason: "ios_post_video_editor_export_cancelled")
    }

    private func finishFailure(reason: String) {
        guard !didFinish else { return }
        didFinish = true
        progressTimer?.invalidate()
        progressTimer = nil
        callback.onFailure(reason: reason)
        onFinished()
    }

    private func startProgressTimer(session: AVAssetExportSession, floor: Float, ceiling: Float) {
        progressTimer?.invalidate()
        progressTimer = Timer.scheduledTimer(withTimeInterval: 0.2, repeats: true) { [weak self, weak session] _ in
            guard let self, !self.didFinish, let session else { return }
            let progress = floor + max(0, min(1, session.progress)) * (ceiling - floor)
            self.callback.onProgress(progress: progress)
        }
        callback.onProgress(progress: floor)
    }

    private func makeVideoComposition(
        track: AVAssetTrack,
        foregroundTrack: AVCompositionTrack,
        backgroundTrack: AVCompositionTrack?,
        duration: CMTime
    ) -> AVMutableVideoComposition {
        let outputSize = CGSize(width: max(2, Int(request.outputWidth)), height: max(2, Int(request.outputHeight)))
        let instruction = AVMutableVideoCompositionInstruction()
        instruction.timeRange = CMTimeRange(start: .zero, duration: duration)
        let foregroundInstruction = AVMutableVideoCompositionLayerInstruction(assetTrack: foregroundTrack)
        foregroundInstruction.setTransform(
            videoTransform(for: track, outputSize: outputSize, crop: request.foregroundCrop, mode: .fit),
            at: .zero
        )
        if let backgroundTrack {
            let backgroundInstruction = AVMutableVideoCompositionLayerInstruction(assetTrack: backgroundTrack)
            backgroundInstruction.setOpacity(0.78, at: .zero)
            backgroundInstruction.setTransform(
                videoTransform(for: track, outputSize: outputSize, crop: request.backgroundCrop, mode: .fill),
                at: .zero
            )
            instruction.backgroundColor = UIColor.black.cgColor
            instruction.layerInstructions = [foregroundInstruction, backgroundInstruction]
        } else {
            instruction.backgroundColor = UIColor.black.cgColor
            instruction.layerInstructions = [foregroundInstruction]
        }

        let composition = AVMutableVideoComposition()
        composition.renderSize = outputSize
        composition.frameDuration = CMTime(value: 1, timescale: CMTimeScale(max(1, request.outputMaxFrameRate)))
        composition.instructions = [instruction]
        return composition
    }

    private func makeBlurredBackgroundVideoComposition(
        asset: AVAsset,
        duration: CMTime
    ) -> AVMutableVideoComposition {
        let outputSize = CGSize(width: max(2, Int(request.outputWidth)), height: max(2, Int(request.outputHeight)))
        let foregroundCrop = request.foregroundCrop
        let backgroundCrop = request.backgroundCrop
        let videoComposition = AVMutableVideoComposition(asset: asset) { renderRequest in
            let source = renderRequest.sourceImage.clampedToExtent()
            let extent = renderRequest.sourceImage.extent
            let background = Self.drawCrop(
                source: source,
                sourceExtent: extent,
                crop: backgroundCrop,
                outputSize: outputSize,
                mode: .fill
            )
                .applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: 22])
                .cropped(to: CGRect(origin: .zero, size: outputSize))
            let foreground = Self.drawCrop(
                source: renderRequest.sourceImage,
                sourceExtent: extent,
                crop: foregroundCrop,
                outputSize: outputSize,
                mode: .fit
            )
            let veil = CIImage(color: CIColor(red: 0, green: 0, blue: 0, alpha: 0.24))
                .cropped(to: CGRect(origin: .zero, size: outputSize))
            renderRequest.finish(
                with: foreground.composited(over: veil.composited(over: background)),
                context: nil
            )
        }
        videoComposition.renderSize = outputSize
        videoComposition.frameDuration = CMTime(value: 1, timescale: CMTimeScale(max(1, request.outputMaxFrameRate)))
        return videoComposition
    }

    private static func drawCrop(
        source: CIImage,
        sourceExtent: CGRect,
        crop: CGRect,
        outputSize: CGSize,
        mode: VideoLayerScaleMode
    ) -> CIImage {
        let cropRect = CGRect(
            x: sourceExtent.minX + crop.minX * sourceExtent.width,
            y: sourceExtent.minY + (1 - crop.maxY) * sourceExtent.height,
            width: max(1, crop.width * sourceExtent.width),
            height: max(1, crop.height * sourceExtent.height)
        )
        let cropped = source.cropped(to: cropRect)
        let scale = mode == .fill
            ? max(outputSize.width / cropRect.width, outputSize.height / cropRect.height)
            : min(outputSize.width / cropRect.width, outputSize.height / cropRect.height)
        let drawWidth = cropRect.width * scale
        let drawHeight = cropRect.height * scale
        let transform = CGAffineTransform(translationX: -cropRect.minX, y: -cropRect.minY)
            .scaledBy(x: scale, y: scale)
            .translatedBy(
                x: ((outputSize.width - drawWidth) / 2) / scale,
                y: ((outputSize.height - drawHeight) / 2) / scale
            )
        return cropped.transformed(by: transform)
            .cropped(to: CGRect(origin: .zero, size: outputSize))
    }

    private func videoTransform(
        for track: AVAssetTrack,
        outputSize: CGSize,
        crop: CGRect,
        mode: VideoLayerScaleMode
    ) -> CGAffineTransform {
        let displaySize = displaySize(for: track)
        let cropWidth = max(1, crop.width * displaySize.width)
        let cropHeight = max(1, crop.height * displaySize.height)
        let scale = mode == .fill
            ? max(outputSize.width / cropWidth, outputSize.height / cropHeight)
            : min(outputSize.width / cropWidth, outputSize.height / cropHeight)
        let drawWidth = cropWidth * scale
        let drawHeight = cropHeight * scale
        let translateX = (outputSize.width - drawWidth) / 2
        let translateY = (outputSize.height - drawHeight) / 2
        return track.preferredTransform
            .translatedBy(x: -crop.minX * displaySize.width, y: -crop.minY * displaySize.height)
            .scaledBy(x: scale, y: scale)
            .translatedBy(x: translateX / scale, y: translateY / scale)
    }

    private func displaySize(for track: AVAssetTrack) -> CGSize {
        let transformed = track.naturalSize.applying(track.preferredTransform)
        let width = abs(transformed.width)
        let height = abs(transformed.height)
        return CGSize(width: max(1, width), height: max(1, height))
    }

    private func burnCaptions(
        inputUrl: URL,
        outputUrl: URL,
        captionStyle: String,
        captionDocument: CaptionDocumentWire,
        callback: any IosPostVideoEditorExportCallback
    ) {
        IosPostVideoEditorNativeDriverBridge.writeEvidenceEvent("caption_burn_start", details: [
            "segments": "\(captionDocument.segments.count)",
        ])
        let asset = AVURLAsset(url: inputUrl)
        let videoComposition = AVMutableVideoComposition(asset: asset) { request in
            let timeMs = max(0, Int64((CMTimeGetSeconds(request.compositionTime) * 1_000).rounded()))
            guard let segment = captionDocument.segment(at: timeMs) else {
                request.finish(with: request.sourceImage, context: nil)
                return
            }
            let overlay = Self.captionOverlayImage(
                outputSize: request.sourceImage.extent.size,
                segment: segment,
                timeMs: timeMs,
                style: captionStyle
            )
            request.finish(with: overlay.composited(over: request.sourceImage), context: nil)
        }
        videoComposition.renderSize = CGSize(width: max(2, Int(request.outputWidth)), height: max(2, Int(request.outputHeight)))
        videoComposition.frameDuration = CMTime(value: 1, timescale: CMTimeScale(max(1, request.outputMaxFrameRate)))
        guard let captionExportSession = AVAssetExportSession(asset: asset, presetName: exportPresetName()) else {
            IosPostVideoEditorNativeDriverBridge.writeEvidenceEvent("caption_burn_session_unavailable")
            callback.onFailure(reason: "ios_post_video_editor_caption_burn_unavailable")
            onFinished()
            return
        }
        self.captionExportSession = captionExportSession
        captionExportSession.outputURL = outputUrl
        captionExportSession.outputFileType = .mp4
        captionExportSession.shouldOptimizeForNetworkUse = true
        captionExportSession.videoComposition = videoComposition
        startProgressTimer(session: captionExportSession, floor: 0.72, ceiling: 0.95)
        captionExportSession.exportAsynchronously { [callback] in
            DispatchQueue.main.async {
                switch captionExportSession.status {
                case .completed:
                    if self.didCancel {
                        self.finishFailure(reason: "ios_post_video_editor_export_cancelled")
                        return
                    }
                    IosPostVideoEditorNativeDriverBridge.writeEvidenceEvent("caption_burn_completed")
                    self.finishSuccess(outputUrl: outputUrl, callback: callback)
                    try? FileManager.default.removeItem(at: inputUrl)
                case .failed, .cancelled:
                    let reason = self.didCancel
                        ? "ios_post_video_editor_export_cancelled"
                        : captionExportSession.error?.localizedDescription ?? "ios_post_video_editor_caption_burn_failed"
                    IosPostVideoEditorNativeDriverBridge.writeEvidenceEvent("caption_burn_failed", details: [
                        "reason": reason,
                    ])
                    self.finishFailure(reason: reason)
                default:
                    IosPostVideoEditorNativeDriverBridge.writeEvidenceEvent("caption_burn_incomplete", details: [
                        "status": "\(captionExportSession.status.rawValue)",
                    ])
                    self.finishFailure(reason: "ios_post_video_editor_caption_burn_incomplete")
                }
            }
        }
    }

    private struct CaptionRenderSpec {
        let textSizeRatio: CGFloat
        let maxWidthRatio: CGFloat
        let maxLines: Int
        let verticalPosition: CGFloat
        let lineHeightMultiplier: CGFloat
        let uppercase: Bool
        let fontName: String
        let segmentBackground: UIColor?
        let activeBackground: UIColor?
        let activeText: UIColor
        let normalText: UIColor
    }

    private static func captionRenderSpec(style: String) -> CaptionRenderSpec {
        switch style {
        case "PopWord":
            return CaptionRenderSpec(textSizeRatio: 0.092, maxWidthRatio: 0.92, maxLines: 1, verticalPosition: 0.67, lineHeightMultiplier: 1.0, uppercase: true, fontName: "HelveticaNeue-CondensedBlack", segmentBackground: nil, activeBackground: UIColor(red: 1, green: 0.54, blue: 0.10, alpha: 0.92), activeText: .black, normalText: .white)
        case "Hormozi":
            return CaptionRenderSpec(textSizeRatio: 0.066, maxWidthRatio: 0.88, maxLines: 2, verticalPosition: 0.70, lineHeightMultiplier: 1.14, uppercase: true, fontName: "HelveticaNeue-CondensedBlack", segmentBackground: UIColor.black.withAlphaComponent(0.82), activeBackground: UIColor(red: 1, green: 0.90, blue: 0, alpha: 0.94), activeText: .black, normalText: .white)
        case "Typewriter":
            return CaptionRenderSpec(textSizeRatio: 0.060, maxWidthRatio: 0.82, maxLines: 2, verticalPosition: 0.76, lineHeightMultiplier: 1.16, uppercase: false, fontName: "Menlo-Bold", segmentBackground: UIColor(red: 0.15, green: 0.16, blue: 0.20, alpha: 0.80), activeBackground: nil, activeText: .white, normalText: .white)
        default:
            return CaptionRenderSpec(textSizeRatio: 0.058, maxWidthRatio: 0.84, maxLines: 2, verticalPosition: 0.74, lineHeightMultiplier: 1.12, uppercase: true, fontName: "HelveticaNeue-CondensedBlack", segmentBackground: UIColor.black.withAlphaComponent(0.69), activeBackground: nil, activeText: UIColor(red: 1, green: 0.48, blue: 0.09, alpha: 1), normalText: .white)
        }
    }

    private static func captionOverlayImage(outputSize: CGSize, segment: CaptionSegmentWire, timeMs: Int64, style: String) -> CIImage {
        let width = max(2, Int(outputSize.width))
        let height = max(2, Int(outputSize.height))
        let bytesPerRow = width * 4
        var pixels = [UInt8](repeating: 0, count: bytesPerRow * height)
        guard let context = CGContext(
            data: &pixels,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: bytesPerRow,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else {
            return CIImage.empty()
        }
        let spec = captionRenderSpec(style: style)
        let fontSize = outputSize.width * spec.textSizeRatio
        let font = UIFont(name: spec.fontName, size: fontSize) ?? UIFont.boldSystemFont(ofSize: fontSize)
        let words = segment.words
        let displayWords = words.map { spec.uppercase ? $0.text.uppercased() : $0.text }
        let gap = fontSize * 0.34
        let widths = displayWords.map { word -> CGFloat in
            let attributed = NSAttributedString(string: word, attributes: [.font: font])
            let line = CTLineCreateWithAttributedString(attributed)
            return CGFloat(CTLineGetTypographicBounds(line, nil, nil, nil))
        }
        let maxLineWidth = outputSize.width * spec.maxWidthRatio
        var lines: [(items: [Int], width: CGFloat)] = []
        var currentItems: [Int] = []
        var currentWidth: CGFloat = 0
        for index in words.indices {
            let nextWidth = currentItems.isEmpty ? widths[index] : currentWidth + gap + widths[index]
            if !currentItems.isEmpty, nextWidth > maxLineWidth, lines.count < spec.maxLines - 1 {
                lines.append((currentItems, currentWidth))
                currentItems = []
                currentWidth = 0
            }
            currentItems.append(index)
            currentWidth = currentWidth == 0 ? widths[index] : currentWidth + gap + widths[index]
        }
        if !currentItems.isEmpty {
            lines.append((currentItems, currentWidth))
        }
        let visibleLines = Array(lines.prefix(spec.maxLines))
        let lineHeight = fontSize * spec.lineHeightMultiplier
        let top = outputSize.height * spec.verticalPosition - (lineHeight * CGFloat(visibleLines.count)) / 2
        for (lineIndex, lineInfo) in visibleLines.enumerated() {
            let boxWidth = min(maxLineWidth, max(outputSize.width * 0.24, lineInfo.width + outputSize.width * 0.08))
            let boxHeight = max(lineHeight * 1.08, outputSize.height * 0.066)
            let box = CGRect(
                x: (outputSize.width - boxWidth) / 2,
                y: top + lineHeight * CGFloat(lineIndex) - boxHeight * 0.14,
                width: boxWidth,
                height: boxHeight
            )
            if let background = spec.segmentBackground ?? (style == "PopWord" ? UIColor(red: 1, green: 0.54, blue: 0.10, alpha: 0.88) : nil) {
                context.setFillColor(background.cgColor)
                context.addPath(CGPath(roundedRect: box, cornerWidth: outputSize.width * 0.018, cornerHeight: outputSize.width * 0.018, transform: nil))
                context.fillPath()
            }
            var x = (outputSize.width - lineInfo.width) / 2
            let y = top + lineHeight * CGFloat(lineIndex) + fontSize * 0.82
            for index in lineInfo.items {
                let word = words[index]
                let active = timeMs >= word.startMs && timeMs <= word.endMs
                if active, let background = spec.activeBackground {
                    let activeBox = CGRect(x: x - gap * 0.32, y: y - fontSize * 0.88, width: widths[index] + gap * 0.64, height: fontSize * 1.12)
                    context.setFillColor(background.cgColor)
                    context.addPath(CGPath(roundedRect: activeBox, cornerWidth: outputSize.width * 0.018, cornerHeight: outputSize.width * 0.018, transform: nil))
                    context.fillPath()
                }
                let attributes: [NSAttributedString.Key: Any] = [
                    .font: font,
                    .foregroundColor: (active ? spec.activeText : spec.normalText).cgColor,
                ]
                let line = CTLineCreateWithAttributedString(NSAttributedString(string: displayWords[index], attributes: attributes))
                context.textPosition = CGPoint(x: x, y: y)
                CTLineDraw(line, context)
                x += widths[index] + gap
            }
        }
        guard let image = context.makeImage() else { return CIImage.empty() }
        return CIImage(cgImage: image)
    }

    private func finishSuccess(outputUrl: URL, callback: any IosPostVideoEditorExportCallback) {
        guard !didFinish else { return }
        didFinish = true
        progressTimer?.invalidate()
        progressTimer = nil
        let size = (try? FileManager.default.attributesOfItem(atPath: outputUrl.path)[.size] as? NSNumber)?.int64Value
        IosPostVideoEditorNativeDriverBridge.writeEvidenceEvent("export_completed", details: ["sizeBytes": "\(size ?? 0)"])
        writeExportDiagnostics(outputUrl: outputUrl, sizeBytes: size)
        callback.onSuccess(file: PlatformFile(
            reference: outputUrl.absoluteString,
            displayName: outputUrl.lastPathComponent,
            mimeType: "video/mp4",
            sizeBytes: size.map { KotlinLong(value: $0) }
        ))
        onFinished()
    }

    private func temporaryOutputUrl(suffix: String) -> URL {
        URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("quata-post-video-editor-\(suffix)-\(UUID().uuidString)")
            .appendingPathExtension("mp4")
    }

    private func writeExportDiagnostics(outputUrl: URL, sizeBytes: Int64?) {
        guard let path = ProcessInfo.processInfo.environment["QUATA_IOS_POST_VIDEO_EDITOR_EXPORT_DIAGNOSTICS"],
              !path.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return
        }
        let captionDocument = CaptionDocumentWire.parse(request.captionDocumentWire)
        let payload: [String: Any] = [
            "outputPath": outputUrl.path,
            "sizeBytes": sizeBytes ?? 0,
            "outputWidth": request.outputWidth,
            "outputHeight": request.outputHeight,
            "outputMaxFrameRate": request.outputMaxFrameRate,
            "outputTargetBitrate": request.outputTargetBitrate,
            "outputIntermediateBitrate": request.outputIntermediateBitrate,
            "outputExportPreset": exportPresetName(),
            "trimStartMs": request.trimStartMs,
            "trimEndMs": request.trimEndMs,
            "removeAudio": request.removeAudio,
            "captionStyle": request.captionStyle ?? "",
            "captionDocumentWire": request.captionDocumentWire ?? "",
            "captionSegmentCount": captionDocument?.segments.count ?? 0,
            "captionWordCount": captionDocument?.segments.reduce(0) { $0 + $1.words.count } ?? 0,
            "captionText": captionDocument?.segments.map(\.text).joined(separator: " ") ?? "",
            "hasBackgroundCrop": request.hasBackgroundCrop,
            "physicalBackgroundBlur": request.hasBackgroundCrop,
        ]
        do {
            let data = try JSONSerialization.data(withJSONObject: payload, options: [.prettyPrinted, .sortedKeys])
            let url = URL(fileURLWithPath: path)
            try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
            try data.write(to: url, options: [.atomic])
        } catch {
            // Evidence-only diagnostics must never make a successful user export fail.
        }
    }

    private func exportPresetName() -> String {
        if request.outputTargetBitrate <= 1_500_000 || request.outputMaxFrameRate <= 30 {
            return AVAssetExportPresetMediumQuality
        }
        return AVAssetExportPresetHighestQuality
    }
}

private enum VideoLayerScaleMode {
    case fit
    case fill
}

private struct CaptionDocumentWire {
    let segments: [CaptionSegmentWire]

    static func parse(_ value: String?) -> CaptionDocumentWire? {
        let chunks = String(value ?? "")
            .components(separatedBy: "\n\n")
        let segments = chunks.compactMap(CaptionSegmentWire.parse)
        return segments.isEmpty ? nil : CaptionDocumentWire(segments: segments)
    }

    func segment(at timeMs: Int64) -> CaptionSegmentWire? {
        segments.last { segment in
            timeMs >= segment.startMs && timeMs <= segment.endMs
        }
    }
}

private struct CaptionSegmentWire {
    let words: [CaptionWordWire]
    var startMs: Int64 { words.map(\.startMs).min() ?? 0 }
    var endMs: Int64 { words.map(\.endMs).max() ?? startMs }
    var text: String { words.map(\.text).joined(separator: " ") }

    static func parse(_ value: String) -> CaptionSegmentWire? {
        let words = value
            .components(separatedBy: .newlines)
            .compactMap(CaptionWordWire.parse)
        return words.isEmpty ? nil : CaptionSegmentWire(words: words)
    }
}

private struct CaptionWordWire {
    let text: String
    let startMs: Int64
    let endMs: Int64
    let confidence: Float

    static func parse(_ value: String) -> CaptionWordWire? {
        let parts = value.components(separatedBy: "\t")
        guard parts.count >= 3 else { return nil }
        let text = parts[0].trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty,
              let start = Int64(parts[1]),
              let rawEnd = Int64(parts[2]) else { return nil }
        let confidence = parts.count > 3 ? Float(parts[3]) ?? 1 : 1
        return CaptionWordWire(
            text: text,
            startMs: max(0, start),
            endMs: max(start + 1, rawEnd),
            confidence: max(0, min(1, confidence))
        )
    }
}

private extension IosPostVideoEditorExportRequest {
    var foregroundCrop: CGRect {
        normalizedCrop(
            left: CGFloat(cropLeft),
            top: CGFloat(cropTop),
            right: CGFloat(cropRight),
            bottom: CGFloat(cropBottom)
        )
    }

    var backgroundCrop: CGRect {
        normalizedCrop(
            left: CGFloat(backgroundCropLeft),
            top: CGFloat(backgroundCropTop),
            right: CGFloat(backgroundCropRight),
            bottom: CGFloat(backgroundCropBottom)
        )
    }

    var hasBackgroundCrop: Bool {
        abs(backgroundCropLeft) > 0.001 ||
            abs(backgroundCropTop) > 0.001 ||
            abs(backgroundCropRight - 1) > 0.001 ||
            abs(backgroundCropBottom - 1) > 0.001
    }

    private func normalizedCrop(left: CGFloat, top: CGFloat, right: CGFloat, bottom: CGFloat) -> CGRect {
        let safeLeft = left.clamped(to: 0...0.99)
        let safeTop = top.clamped(to: 0...0.99)
        let safeRight = right.clamped(to: (safeLeft + 0.01)...1)
        let safeBottom = bottom.clamped(to: (safeTop + 0.01)...1)
        return CGRect(
            x: safeLeft,
            y: safeTop,
            width: safeRight - safeLeft,
            height: safeBottom - safeTop
        )
    }
}

private extension CGFloat {
    func clamped(to range: ClosedRange<CGFloat>) -> CGFloat {
        Swift.min(Swift.max(self, range.lowerBound), range.upperBound)
    }
}
