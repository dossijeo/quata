import CoreGraphics
import Foundation
import ImageIO
import Vision

struct VisualResult: Encodable {
    let classification: String
    let width: Int
    let height: Int
    let meanLuminance: Double
    let brightPixelFraction: Double
    let mediaTextContrastRatio: Double
    let markersFound: [String]
}

guard CommandLine.arguments.count == 2 else {
    FileHandle.standardError.write(Data("Usage: classifier SCREENSHOT.png\n".utf8))
    exit(2)
}
let url = URL(fileURLWithPath: CommandLine.arguments[1])
guard
    let source = CGImageSourceCreateWithURL(url as CFURL, nil),
    let image = CGImageSourceCreateImageAtIndex(source, 0, nil)
else {
    FileHandle.standardError.write(Data("Unable to decode screenshot.\n".utf8))
    exit(2)
}

let width = image.width
let height = image.height
let bytesPerPixel = 4
let bytesPerRow = width * bytesPerPixel
var pixels = [UInt8](repeating: 0, count: height * bytesPerRow)
guard let context = CGContext(
    data: &pixels,
    width: width,
    height: height,
    bitsPerComponent: 8,
    bytesPerRow: bytesPerRow,
    space: CGColorSpaceCreateDeviceRGB(),
    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
) else {
    FileHandle.standardError.write(Data("Unable to inspect screenshot pixels.\n".utf8))
    exit(2)
}
context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))

var luminanceTotal = 0.0
var brightPixels = 0
for offset in stride(from: 0, to: pixels.count, by: bytesPerPixel) {
    let luminance =
        0.2126 * Double(pixels[offset]) +
        0.7152 * Double(pixels[offset + 1]) +
        0.0722 * Double(pixels[offset + 2])
    luminanceTotal += luminance / 255.0
    if luminance >= 48.0 { brightPixels += 1 }
}
let pixelCount = max(1, width * height)
let meanLuminance = luminanceTotal / Double(pixelCount)
let brightPixelFraction = Double(brightPixels) / Double(pixelCount)

let request = VNRecognizeTextRequest()
request.recognitionLevel = .accurate
request.usesLanguageCorrection = true
request.recognitionLanguages = ["es-ES", "en-US"]
let handler = VNImageRequestHandler(cgImage: image, options: [:])
do {
    try handler.perform([request])
} catch {
    FileHandle.standardError.write(Data("Vision OCR failed.\n".utf8))
    exit(2)
}
let recognizedLines = (request.results ?? []).compactMap { observation -> (String, CGRect)? in
    guard let candidate = observation.topCandidates(1).first?.string else { return nil }
    let normalized = candidate
        .folding(options: [.caseInsensitive, .diacriticInsensitive], locale: Locale(identifier: "es_ES"))
        .lowercased()
    return (normalized, observation.boundingBox)
}
let recognized = recognizedLines.map { $0.0 }.joined(separator: "\n")
let mediaMarker = "contenido multimedia"
let markers = ["explora quata", "actualizar", "conversaciones", mediaMarker]
let markersFound = markers.filter { recognized.contains($0) }

struct PixelRect {
    let minX: Int
    let maxX: Int
    let minY: Int
    let maxY: Int
}

func pixelRect(fromVisionBoundingBox normalizedRect: CGRect) -> PixelRect {
    PixelRect(
        minX: max(0, Int(floor(normalizedRect.minX * CGFloat(width)))),
        maxX: min(width, Int(ceil(normalizedRect.maxX * CGFloat(width)))),
        minY: max(0, Int(floor((1.0 - normalizedRect.maxY) * CGFloat(height)))),
        maxY: min(height, Int(ceil((1.0 - normalizedRect.minY) * CGFloat(height))))
    )
}

func linearChannel(_ byte: UInt8) -> Double {
    let channel = Double(byte) / 255.0
    return channel <= 0.04045
        ? channel / 12.92
        : pow((channel + 0.055) / 1.055, 2.4)
}

func relativeLuminance(x: Int, y: Int) -> Double {
    let offset = y * bytesPerRow + x * bytesPerPixel
    return
        0.2126 * linearChannel(pixels[offset]) +
        0.7152 * linearChannel(pixels[offset + 1]) +
        0.0722 * linearChannel(pixels[offset + 2])
}

func median(_ values: [Double]) -> Double {
    guard !values.isEmpty else { return 0.0 }
    let sorted = values.sorted()
    let middle = sorted.count / 2
    return sorted.count.isMultiple(of: 2)
        ? (sorted[middle - 1] + sorted[middle]) / 2.0
        : sorted[middle]
}

func textContrastRatio(in visionBoundingBox: CGRect) -> Double {
    let rect = pixelRect(fromVisionBoundingBox: visionBoundingBox)
    guard rect.minX < rect.maxX, rect.minY < rect.maxY else { return 0.0 }

    let padding = max(4, (rect.maxY - rect.minY) / 3)
    let outer = PixelRect(
        minX: max(0, rect.minX - padding),
        maxX: min(width, rect.maxX + padding),
        minY: max(0, rect.minY - padding),
        maxY: min(height, rect.maxY + padding)
    )
    var backgroundSamples: [Double] = []
    for y in outer.minY..<outer.maxY {
        for x in outer.minX..<outer.maxX
        where x < rect.minX || x >= rect.maxX || y < rect.minY || y >= rect.maxY {
            backgroundSamples.append(relativeLuminance(x: x, y: y))
        }
    }
    guard !backgroundSamples.isEmpty else { return 0.0 }
    let background = median(backgroundSamples)

    var glyphCandidates: [(distance: Double, luminance: Double)] = []
    for y in rect.minY..<rect.maxY {
        for x in rect.minX..<rect.maxX {
            let luminance = relativeLuminance(x: x, y: y)
            glyphCandidates.append((abs(luminance - background), luminance))
        }
    }
    glyphCandidates.sort { $0.distance > $1.distance }
    let sampleCount = max(8, glyphCandidates.count / 12)
    let foreground = median(glyphCandidates.prefix(sampleCount).map { $0.luminance })
    let lighter = max(foreground, background)
    let darker = min(foreground, background)
    return (lighter + 0.05) / (darker + 0.05)
}

let mediaTextContrastRatio = recognizedLines
    .filter { $0.0.contains(mediaMarker) }
    .map { textContrastRatio(in: $0.1) }
    .max() ?? 0.0

let dimensionsValid = width >= 750 && height >= 1300
let mediaTextContrastValid = mediaTextContrastRatio >= 4.5
let classification: String
if dimensionsValid && meanLuminance >= 0.08 && brightPixelFraction >= 0.12 &&
    mediaTextContrastValid && markersFound.count == markers.count {
    classification = "pass"
} else if dimensionsValid && meanLuminance >= 0.035 && brightPixelFraction >= 0.05 &&
    mediaTextContrastValid && markersFound.count >= 3 {
    classification = "degraded"
} else {
    classification = "fail"
}
let result = VisualResult(
    classification: classification,
    width: width,
    height: height,
    meanLuminance: meanLuminance,
    brightPixelFraction: brightPixelFraction,
    mediaTextContrastRatio: mediaTextContrastRatio,
    markersFound: markersFound
)
let encoder = JSONEncoder()
encoder.outputFormatting = [.sortedKeys]
FileHandle.standardOutput.write(try encoder.encode(result))
FileHandle.standardOutput.write(Data("\n".utf8))
