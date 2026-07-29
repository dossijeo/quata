import AppKit
import Foundation

enum Fixture: String {
    case passWhiteOnBlack = "pass-white-on-black"
    case failLightOnLight = "fail-light-on-light"
    case failDarkOnDark = "fail-dark-on-dark"
    case failMirroredBrightRegion = "fail-mirrored-bright-region"
    case failMarkerAbsent = "fail-marker-absent"
}

guard CommandLine.arguments.count == 3,
      let fixture = Fixture(rawValue: CommandLine.arguments[1]) else {
    FileHandle.standardError.write(
        Data("Usage: fixture-generator FIXTURE OUTPUT.png\n".utf8)
    )
    exit(2)
}

let output = URL(fileURLWithPath: CommandLine.arguments[2])
let canvasSize = NSSize(width: 900, height: 1500)
let image = NSImage(size: canvasSize)
image.lockFocus()

NSColor.white.setFill()
NSBezierPath(rect: NSRect(origin: .zero, size: canvasSize)).fill()

let headerAttributes: [NSAttributedString.Key: Any] = [
    .font: NSFont.systemFont(ofSize: 42, weight: .semibold),
    .foregroundColor: NSColor.black,
]
("Explora Quata" as NSString).draw(at: NSPoint(x: 55, y: 1310), withAttributes: headerAttributes)
("Actualizar" as NSString).draw(at: NSPoint(x: 55, y: 1215), withAttributes: headerAttributes)
("Conversaciones" as NSString).draw(at: NSPoint(x: 55, y: 1120), withAttributes: headerAttributes)

let mediaSurface = NSRect(x: 35, y: 250, width: 830, height: 600)
// Keep this well away from the vertical axis: its mirrored decoy must be a
// distinct region, never an overdraw of the real low-contrast marker.
let textRect = NSRect(x: 65, y: 450, width: 770, height: 90)
let background: NSColor
let foreground: NSColor
switch fixture {
case .passWhiteOnBlack:
    background = .black
    foreground = .white
case .failLightOnLight:
    background = .white
    foreground = NSColor(calibratedWhite: 0.55, alpha: 1.0)
case .failDarkOnDark, .failMirroredBrightRegion:
    background = .black
    foreground = NSColor(calibratedWhite: 0.35, alpha: 1.0)
case .failMarkerAbsent:
    background = .black
    foreground = .white
}
background.setFill()
NSBezierPath(roundedRect: mediaSurface, xRadius: 24, yRadius: 24).fill()

if fixture != .failMarkerAbsent {
    let mediaAttributes: [NSAttributedString.Key: Any] = [
        .font: NSFont.systemFont(ofSize: 34, weight: .medium),
        .foregroundColor: foreground,
    ]
    ("El contenido multimedia aún no está disponible." as NSString)
        .draw(in: textRect, withAttributes: mediaAttributes)
}

if fixture == .failMirroredBrightRegion {
    // A classifier that samples both the real Vision rect and its mirror can
    // incorrectly select this unrelated high-contrast region.
    let mirroredDecoy = NSRect(
        x: textRect.minX,
        y: canvasSize.height - textRect.maxY,
        width: textRect.width,
        height: textRect.height
    )
    NSColor.black.setFill()
    NSBezierPath(rect: mirroredDecoy.insetBy(dx: -12, dy: -12)).fill()
    NSColor.white.setFill()
    NSBezierPath(rect: mirroredDecoy.insetBy(dx: 8, dy: 22)).fill()
}
image.unlockFocus()

guard
    let tiff = image.tiffRepresentation,
    let bitmap = NSBitmapImageRep(data: tiff),
    let png = bitmap.representation(using: .png, properties: [:])
else {
    FileHandle.standardError.write(Data("Unable to encode fixture PNG.\n".utf8))
    exit(2)
}
try png.write(to: output)
