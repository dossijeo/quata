import AVFoundation
import Foundation
import QuartzCore
import UIKit
import QuataShared

/// Native-only AVFoundation edge for the shared post video editor.
/// Compose/Kotlin owns the editor state, timeline, crop/caption controls and export spec.
final class IosPostVideoEditorNativeDriverBridge: NSObject, IosPostVideoEditorNativeDriver {
    static let shared = IosPostVideoEditorNativeDriverBridge()

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

    func export(
        source: PlatformFile,
        request: IosPostVideoEditorExportRequest,
        callback: any IosPostVideoEditorExportCallback
    ) {
        guard let sourceUrl = URL(string: source.reference), sourceUrl.isFileURL else {
            callback.onFailure(reason: "ios_post_video_editor_source_invalid")
            return
        }
        let exporter = IosPostVideoEditorExportOperation(sourceUrl: sourceUrl, request: request, callback: callback)
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

    init(sourceUrl: URL, request: IosPostVideoEditorExportRequest, callback: any IosPostVideoEditorExportCallback) {
        self.sourceUrl = sourceUrl
        self.request = request
        self.callback = callback
    }

    func start() {
        let asset = AVURLAsset(url: sourceUrl)
        guard let videoTrack = asset.tracks(withMediaType: .video).first else {
            callback.onFailure(reason: "ios_post_video_editor_video_track_missing")
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
            callback.onFailure(reason: "ios_post_video_editor_composition_video_track_failed")
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
            callback.onFailure(reason: "ios_post_video_editor_video_insert_failed")
            return
        }
        compositionVideo.preferredTransform = videoTrack.preferredTransform
        compositionBackground?.preferredTransform = videoTrack.preferredTransform
        if !request.removeAudio, let audioTrack = asset.tracks(withMediaType: .audio).first,
           let compositionAudio = composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid) {
            try? compositionAudio.insertTimeRange(range, of: audioTrack, at: .zero)
        }
        if let captionStyle = request.captionStyle, !captionStyle.isEmpty,
           request.captionText?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty != false {
            callback.onFailure(reason: "ios_post_video_editor_caption_transcript_missing")
            return
        }

        let outputUrl = temporaryOutputUrl()
        try? FileManager.default.removeItem(at: outputUrl)
        guard let exportSession = AVAssetExportSession(asset: composition, presetName: AVAssetExportPresetHighestQuality) else {
            callback.onFailure(reason: "ios_post_video_editor_export_session_unavailable")
            return
        }
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
        exportSession.exportAsynchronously { [callback] in
            DispatchQueue.main.async {
                switch exportSession.status {
                case .completed:
                    let size = (try? FileManager.default.attributesOfItem(atPath: outputUrl.path)[.size] as? NSNumber)?.int64Value
                    callback.onSuccess(file: PlatformFile(
                        reference: outputUrl.absoluteString,
                        displayName: outputUrl.lastPathComponent,
                        mimeType: "video/mp4",
                        sizeBytes: size.map { KotlinLong(value: $0) }
                    ))
                case .failed, .cancelled:
                    callback.onFailure(reason: exportSession.error?.localizedDescription ?? "ios_post_video_editor_export_failed")
                default:
                    callback.onFailure(reason: "ios_post_video_editor_export_incomplete")
                }
            }
        }
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
            guard let captionText = request.captionText?.trimmingCharacters(in: .whitespacesAndNewlines),
                  !captionText.isEmpty else {
                return composition
            }
            composition.animationTool = captionAnimationTool(
                outputSize: outputSize,
                captionStyle: captionStyle,
                captionText: captionText
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
        captionStyle: String,
        captionText: String
    ) -> AVVideoCompositionCoreAnimationTool {
        let parentLayer = CALayer()
        let videoLayer = CALayer()
        parentLayer.frame = CGRect(origin: .zero, size: outputSize)
        videoLayer.frame = parentLayer.frame
        parentLayer.addSublayer(videoLayer)

        let background = CALayer()
        background.backgroundColor = UIColor.black.withAlphaComponent(0.72).cgColor
        background.cornerRadius = outputSize.width * 0.018
        background.frame = CGRect(
            x: outputSize.width * 0.08,
            y: outputSize.height * 0.72,
            width: outputSize.width * 0.84,
            height: outputSize.height * 0.095
        )
        parentLayer.addSublayer(background)

        let text = CATextLayer()
        text.string = captionText.uppercased()
        text.alignmentMode = .center
        text.foregroundColor = UIColor.white.cgColor
        text.contentsScale = UIScreen.main.scale
        text.fontSize = outputSize.width * 0.056
        text.frame = background.frame.insetBy(dx: 12, dy: outputSize.height * 0.018)
        parentLayer.addSublayer(text)
        return AVVideoCompositionCoreAnimationTool(postProcessingAsVideoLayer: videoLayer, in: parentLayer)
    }

    private func temporaryOutputUrl() -> URL {
        URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("quata-post-video-editor-\(UUID().uuidString)")
            .appendingPathExtension("mp4")
    }
}

private enum VideoLayerScaleMode {
    case fit
    case fill
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
