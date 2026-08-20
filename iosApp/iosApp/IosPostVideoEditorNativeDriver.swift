import AVFoundation
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
        exporter.start()
    }
}

private final class IosPostVideoEditorPreviewView: UIView {
    var playerLayer: AVPlayerLayer?
    override func layoutSubviews() {
        super.layoutSubviews()
        playerLayer?.frame = bounds
    }
}

private final class IosPostVideoEditorPreviewSurfaceImpl: NSObject, IosPostVideoEditorPreviewSurface {
    private let root = IosPostVideoEditorPreviewView()
    private var player: AVPlayer?
    private var playerLayer: AVPlayerLayer?

    init(url: URL?) {
        super.init()
        root.clipsToBounds = true
        root.isOpaque = false
        root.backgroundColor = .clear
        guard let url else { return }
        let player = AVPlayer(url: url)
        player.actionAtItemEnd = .pause
        let layer = AVPlayerLayer(player: player)
        layer.videoGravity = .resizeAspect
        layer.isOpaque = false
        layer.backgroundColor = UIColor.clear.cgColor
        root.layer.addSublayer(layer)
        root.playerLayer = layer
        self.player = player
        self.playerLayer = layer
    }

    func nativeView() -> UIView { root }

    func configure(isPlaying: Bool, isMuted: Bool, positionMs: Int64) {
        guard let player else { return }
        player.isMuted = isMuted
        player.seek(
            to: CMTime(value: CMTimeValue(max(0, positionMs)), timescale: 1_000),
            toleranceBefore: .zero,
            toleranceAfter: .zero,
        )
        if isPlaying { player.play() } else { player.pause() }
    }

    func dispose() {
        player?.pause()
        playerLayer?.removeFromSuperlayer()
        root.playerLayer = nil
        playerLayer = nil
        player = nil
    }
}

private final class IosPostVideoEditorExportOperation {
    private let sourceUrl: URL
    private let request: IosPostVideoEditorExportRequest
    private let callback: any IosPostVideoEditorExportCallback
    private let onFinished: () -> Void
    private var exportSession: AVAssetExportSession?

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
        IosPostVideoEditorNativeDriverBridge.writeEvidenceEvent("export_start")
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
        if let captionStyle = request.captionStyle, !captionStyle.isEmpty,
           request.captionDocumentWire?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty != false {
            IosPostVideoEditorNativeDriverBridge.writeEvidenceEvent("export_caption_transcript_missing")
            finishFailure(reason: "ios_post_video_editor_caption_transcript_missing")
            return
        }

        let outputUrl = temporaryOutputUrl()
        try? FileManager.default.removeItem(at: outputUrl)
        guard let exportSession = AVAssetExportSession(asset: composition, presetName: AVAssetExportPresetHighestQuality) else {
            IosPostVideoEditorNativeDriverBridge.writeEvidenceEvent("export_session_unavailable")
            finishFailure(reason: "ios_post_video_editor_export_session_unavailable")
            return
        }
        self.exportSession = exportSession
        exportSession.outputURL = outputUrl
        exportSession.outputFileType = .mp4
        exportSession.shouldOptimizeForNetworkUse = true
        exportSession.timeRange = CMTimeRange(start: .zero, duration: range.duration)
        exportSession.videoComposition = makeVideoComposition(
            track: videoTrack,
            foregroundTrack: compositionVideo,
            backgroundTrack: compositionBackground,
            duration: range.duration
        )
        IosPostVideoEditorNativeDriverBridge.writeEvidenceEvent("export_session_started", details: [
            "durationMs": "\(Int64(CMTimeGetSeconds(range.duration) * 1_000))",
            "captionDocument": request.captionDocumentWire?.isEmpty == false ? "true" : "false",
        ])
        exportSession.exportAsynchronously { [callback] in
            DispatchQueue.main.async {
                defer { self.onFinished() }
                switch exportSession.status {
                case .completed:
                    let size = (try? FileManager.default.attributesOfItem(atPath: outputUrl.path)[.size] as? NSNumber)?.int64Value
                    IosPostVideoEditorNativeDriverBridge.writeEvidenceEvent("export_completed", details: ["sizeBytes": "\(size ?? 0)"])
                    self.writeExportDiagnostics(outputUrl: outputUrl, sizeBytes: size)
                    callback.onSuccess(file: PlatformFile(
                        reference: outputUrl.absoluteString,
                        displayName: outputUrl.lastPathComponent,
                        mimeType: "video/mp4",
                        sizeBytes: size.map { KotlinLong(value: $0) }
                    ))
                case .failed, .cancelled:
                    IosPostVideoEditorNativeDriverBridge.writeEvidenceEvent("export_failed", details: ["reason": exportSession.error?.localizedDescription ?? "ios_post_video_editor_export_failed"])
                    callback.onFailure(reason: exportSession.error?.localizedDescription ?? "ios_post_video_editor_export_failed")
                default:
                    IosPostVideoEditorNativeDriverBridge.writeEvidenceEvent("export_incomplete", details: ["status": "\(exportSession.status.rawValue)"])
                    callback.onFailure(reason: "ios_post_video_editor_export_incomplete")
                }
            }
        }
    }

    private func finishFailure(reason: String) {
        callback.onFailure(reason: reason)
        onFinished()
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
        composition.frameDuration = CMTime(value: 1, timescale: 30)
        composition.instructions = [instruction]
        if let captionStyle = request.captionStyle, !captionStyle.isEmpty {
            guard let captionDocument = CaptionDocumentWire.parse(request.captionDocumentWire),
                  !captionDocument.segments.isEmpty else {
                return composition
            }
            composition.animationTool = captionAnimationTool(
                outputSize: outputSize,
                duration: duration,
                captionStyle: captionStyle,
                captionDocument: captionDocument
            )
        }
        return composition
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

    private func captionAnimationTool(
        outputSize: CGSize,
        duration: CMTime,
        captionStyle: String,
        captionDocument: CaptionDocumentWire
    ) -> AVVideoCompositionCoreAnimationTool {
        let parentLayer = CALayer()
        let videoLayer = CALayer()
        parentLayer.frame = CGRect(origin: .zero, size: outputSize)
        videoLayer.frame = parentLayer.frame
        parentLayer.addSublayer(videoLayer)

        for segment in captionDocument.segments {
            let background = CALayer()
            background.backgroundColor = UIColor.black.withAlphaComponent(0.72).cgColor
            background.cornerRadius = outputSize.width * 0.018
            background.frame = CGRect(
                x: outputSize.width * 0.08,
                y: outputSize.height * 0.72,
                width: outputSize.width * 0.84,
                height: outputSize.height * 0.095
            )
            background.opacity = 0
            addVisibilityAnimation(to: background, startMs: segment.startMs, endMs: segment.endMs, duration: duration)
            parentLayer.addSublayer(background)

            let text = CATextLayer()
            text.string = segment.text.uppercased()
            text.alignmentMode = .center
            text.foregroundColor = UIColor.white.cgColor
            text.contentsScale = UIScreen.main.scale
            text.fontSize = outputSize.width * (captionStyle == "PopWord" ? 0.072 : 0.056)
            text.frame = background.frame.insetBy(dx: 12, dy: outputSize.height * 0.018)
            text.opacity = 0
            addVisibilityAnimation(to: text, startMs: segment.startMs, endMs: segment.endMs, duration: duration)
            parentLayer.addSublayer(text)
        }
        return AVVideoCompositionCoreAnimationTool(postProcessingAsVideoLayer: videoLayer, in: parentLayer)
    }

    private func addVisibilityAnimation(to layer: CALayer, startMs: Int64, endMs: Int64, duration: CMTime) {
        let totalMs = max(1, Int64(CMTimeGetSeconds(duration) * 1_000))
        let epsilon = 0.001
        let start = max(epsilon, min(1 - epsilon * 2, Double(startMs) / Double(totalMs)))
        let end = max(start + epsilon, min(1 - epsilon, Double(endMs) / Double(totalMs)))
        let animation = CAKeyframeAnimation(keyPath: "opacity")
        animation.calculationMode = .linear
        animation.values = [0, 1, 1, 0]
        animation.keyTimes = [
            0,
            NSNumber(value: start),
            NSNumber(value: end),
            1,
        ]
        animation.duration = CMTimeGetSeconds(duration)
        animation.beginTime = AVCoreAnimationBeginTimeAtZero
        animation.isRemovedOnCompletion = false
        animation.fillMode = .both
        layer.add(animation, forKey: "caption-visibility")
    }

    private func temporaryOutputUrl() -> URL {
        URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("quata-post-video-editor-\(UUID().uuidString)")
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
            "trimStartMs": request.trimStartMs,
            "trimEndMs": request.trimEndMs,
            "removeAudio": request.removeAudio,
            "captionStyle": request.captionStyle ?? "",
            "captionDocumentWire": request.captionDocumentWire ?? "",
            "captionSegmentCount": captionDocument?.segments.count ?? 0,
            "captionWordCount": captionDocument?.segments.reduce(0) { $0 + $1.words.count } ?? 0,
            "captionText": captionDocument?.segments.map(\.text).joined(separator: " ") ?? "",
            "hasBackgroundCrop": request.hasBackgroundCrop,
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
