import AVFoundation
import CoreImage
import Foundation
import UIKit
import QuataShared

/// Native export boundary for the Official editor. Compose owns editor state; this object only
/// materialises an edited, app-owned file using Apple's media frameworks.
final class IosOfficialMediaEditorBridge: NSObject, IosOfficialNativeMediaEditor {
    static let shared = IosOfficialMediaEditorBridge()
    private let context = CIContext()

    func editAndExport(
        sourceReference: String,
        isVideo: Bool,
        onSuccess: @escaping (String, String, String) -> Void,
        onFailure: @escaping (String) -> Void
    ) -> any IosOfficialNativeMediaEditOperation {
        let operation = OfficialMediaExportOperation()
        guard let sourceURL = URL(string: sourceReference), sourceURL.isFileURL else {
            onFailure("ios_official_editor_source_invalid")
            return operation
        }
        if isVideo {
            exportVideo(sourceURL, operation: operation, onSuccess: onSuccess, onFailure: onFailure)
        } else {
            exportImage(sourceURL, operation: operation, onSuccess: onSuccess, onFailure: onFailure)
        }
        return operation
    }

    private func exportImage(_ source: URL, operation: OfficialMediaExportOperation, onSuccess: @escaping (String, String, String) -> Void, onFailure: @escaping (String) -> Void) {
        DispatchQueue.global(qos: .userInitiated).async { [context] in
            guard !operation.cancelled, let input = CIImage(contentsOf: source) else {
                if !operation.cancelled { onFailure("ios_official_image_decode_failed") }; return
            }
            // A real Core Image render creates an export rather than reusing the picker source.
            let filter = CIFilter(name: "CIColorControls")!
            filter.setValue(input, forKey: kCIInputImageKey)
            filter.setValue(1.04, forKey: kCIInputSaturationKey)
            guard let output = filter.outputImage else { onFailure("ios_official_image_filter_failed"); return }
            let destination = Self.temporaryURL(extension: "jpg")
            do {
                try context.writeJPEGRepresentation(of: output, to: destination, colorSpace: CGColorSpace(name: CGColorSpace.sRGB)!, options: [:])
                guard !operation.cancelled else { try? FileManager.default.removeItem(at: destination); return }
                onSuccess(destination.absoluteString, destination.lastPathComponent, "image/jpeg")
            } catch { onFailure("ios_official_image_export_failed:\(error.localizedDescription)") }
        }
    }

    private func exportVideo(_ source: URL, operation: OfficialMediaExportOperation, onSuccess: @escaping (String, String, String) -> Void, onFailure: @escaping (String) -> Void) {
        let asset = AVURLAsset(url: source)
        guard let session = AVAssetExportSession(asset: asset, presetName: AVAssetExportPresetHighestQuality) else {
            onFailure("ios_official_video_exporter_unavailable"); return
        }
        let destination = Self.temporaryURL(extension: "mp4")
        operation.exportSession = session
        session.outputURL = destination
        session.outputFileType = .mp4
        session.exportAsynchronously {
            if operation.cancelled { try? FileManager.default.removeItem(at: destination); return }
            guard session.status == .completed else {
                onFailure("ios_official_video_export_failed:\(session.error?.localizedDescription ?? "unknown")")
                return
            }
            onSuccess(destination.absoluteString, destination.lastPathComponent, "video/mp4")
        }
    }

    private static func temporaryURL(extension suffix: String) -> URL {
        FileManager.default.temporaryDirectory
            .appendingPathComponent("quata_official_edit_\(UUID().uuidString)")
            .appendingPathExtension(suffix)
    }
}

private final class OfficialMediaExportOperation: NSObject, IosOfficialNativeMediaEditOperation {
    var exportSession: AVAssetExportSession?
    private(set) var cancelled = false
    func cancel() { cancelled = true; exportSession?.cancelExport() }
}
