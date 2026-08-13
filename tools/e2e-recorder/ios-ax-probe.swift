#!/usr/bin/env swift
import ApplicationServices
import Foundation

struct Bounds: Codable {
    let x: Double
    let y: Double
    let width: Double
    let height: Double
}

struct Node: Codable {
    let accessibilityIdentifier: String?
    let label: String?
    let value: String?
    let title: String?
    let role: String?
    let frame: Bounds?
}

func arg(_ name: String) -> String? {
    let args = CommandLine.arguments
    guard let index = args.firstIndex(of: name), index + 1 < args.count else { return nil }
    return args[index + 1]
}

func stringAttribute(_ element: AXUIElement, _ attribute: CFString) -> String? {
    var value: AnyObject?
    guard AXUIElementCopyAttributeValue(element, attribute, &value) == .success else { return nil }
    return value as? String
}

func frameAttribute(_ element: AXUIElement) -> Bounds? {
    var positionValue: AnyObject?
    var sizeValue: AnyObject?
    guard AXUIElementCopyAttributeValue(element, kAXPositionAttribute as CFString, &positionValue) == .success,
          AXUIElementCopyAttributeValue(element, kAXSizeAttribute as CFString, &sizeValue) == .success,
          CFGetTypeID(positionValue) == AXValueGetTypeID(),
          CFGetTypeID(sizeValue) == AXValueGetTypeID() else { return nil }
    var point = CGPoint.zero
    var size = CGSize.zero
    AXValueGetValue(positionValue as! AXValue, .cgPoint, &point)
    AXValueGetValue(sizeValue as! AXValue, .cgSize, &size)
    return Bounds(x: point.x, y: point.y, width: size.width, height: size.height)
}

guard let rawPoint = arg("--point") else {
    FileHandle.standardError.write(Data("Usage: swift tools/e2e-recorder/ios-ax-probe.swift --point x,y [--pid <pid>]\n".utf8))
    exit(64)
}

let parts = rawPoint.split(separator: ",").compactMap { Double($0.trimmingCharacters(in: .whitespaces)) }
guard parts.count == 2 else {
    FileHandle.standardError.write(Data("--point must be x,y\n".utf8))
    exit(64)
}

let system = AXUIElementCreateSystemWide()
let targetApp: AXUIElement
if let rawPid = arg("--pid"), let parsedPid = Int32(rawPid) {
    targetApp = AXUIElementCreateApplication(pid_t(parsedPid))
} else {
    targetApp = system
}

var hit: AXUIElement?
let error = AXUIElementCopyElementAtPosition(targetApp, Float(parts[0]), Float(parts[1]), &hit)
guard error == .success, let element = hit else {
    FileHandle.standardError.write(Data("ios_ax_probe_failed: \(error.rawValue)\n".utf8))
    exit(2)
}

let node = Node(
    accessibilityIdentifier: stringAttribute(element, "AXIdentifier" as CFString),
    label: stringAttribute(element, kAXDescriptionAttribute as CFString),
    value: stringAttribute(element, kAXValueAttribute as CFString),
    title: stringAttribute(element, kAXTitleAttribute as CFString),
    role: stringAttribute(element, kAXRoleAttribute as CFString),
    frame: frameAttribute(element)
)

let encoder = JSONEncoder()
encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
FileHandle.standardOutput.write(try encoder.encode(["children": [node]]))
FileHandle.standardOutput.write(Data("\n".utf8))
