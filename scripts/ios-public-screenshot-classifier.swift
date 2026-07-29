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
let recognized = (request.results ?? [])
    .compactMap { $0.topCandidates(1).first?.string }
    .joined(separator: "\n")
    .folding(options: [.caseInsensitive, .diacriticInsensitive], locale: Locale(identifier: "es_ES"))
    .lowercased()
let markers = ["explora quata", "actualizar", "conversaciones"]
let markersFound = markers.filter { recognized.contains($0) }

let dimensionsValid = width >= 750 && height >= 1300
let classification: String
if dimensionsValid && meanLuminance >= 0.08 && brightPixelFraction >= 0.12 && markersFound.count == markers.count {
    classification = "pass"
} else if dimensionsValid && meanLuminance >= 0.035 && brightPixelFraction >= 0.05 && markersFound.count >= 2 {
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
    markersFound: markersFound
)
let encoder = JSONEncoder()
encoder.outputFormatting = [.sortedKeys]
FileHandle.standardOutput.write(try encoder.encode(result))
FileHandle.standardOutput.write(Data("\n".utf8))
