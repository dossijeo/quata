import UIKit
import XCTest

/**
 * Stable assertion boundary for the unauthenticated UIKit/Compose smoke.
 *
 * This deliberately verifies only host containment and accessibility metadata. It does not
 * grant permissions, seed a session, or imply a functional authenticated/E2E journey.
 */
enum QuataIosHostUITestSupport {
    static let composeRootIdentifier = "quata-ios-compose-root"

    static func composeRoot(
        in app: XCUIApplication,
        timeout: TimeInterval = 10,
        context: String = "launch",
        file: StaticString = #filePath,
        line: UInt = #line,
    ) -> XCUIElement {
        let roots = app.descendants(matching: .any).matching(identifier: composeRootIdentifier)
        let root = roots.firstMatch
        XCTAssertTrue(
            root.waitForExistence(timeout: timeout),
            "[\(context)] The UIKit composition root must present exactly one Compose surface with identifier \(composeRootIdentifier).",
            file: file,
            line: line,
        )
        XCTAssertEqual(
            roots.count,
            1,
            "[\(context)] The UIKit composition root must not retain duplicate Compose surfaces.",
            file: file,
            line: line,
        )
        return root
    }

    static func fixtureRoot(
        in app: XCUIApplication,
        identifier: String,
        timeout: TimeInterval = 10,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) -> XCUIElement {
        let roots = app.descendants(matching: .any).matching(identifier: identifier)
        let root = roots.firstMatch
        XCTAssertTrue(
            root.waitForExistence(timeout: timeout),
            "The deterministic UIKit fixture must expose \(identifier).",
            file: file,
            line: line,
        )
        XCTAssertEqual(roots.count, 1, "The UIKit fixture must not retain duplicate route surfaces.", file: file, line: line)
        return root
    }

    static func assertFixtureRoute(
        in app: XCUIApplication,
        identifier: String,
        label: String,
        screenshotName: String,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) {
        let root = fixtureRoot(in: app, identifier: identifier, file: file, line: line)
        XCTAssertEqual(root.label, label, "The deterministic route surface must retain its accessible name.", file: file, line: line)
        XCTAssertFalse(
            app.descendants(matching: .any).matching(identifier: composeRootIdentifier).firstMatch.exists,
            "A deterministic route fixture must not instantiate the production Compose surface.",
            file: file,
            line: line,
        )
        attachRenderedSurface(named: screenshotName)
    }

    static func attachRenderedSurface(named name: String) -> XCTAttachment {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        XCTContext.runActivity(named: name) { $0.add(attachment) }
        return attachment
    }

    static func assertElementHasNonBlackPixels(
        _ element: XCUIElement,
        named name: String,
        minimumNonBlackRatio: Double = 0.08,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) {
        XCTAssertTrue(element.exists, "Expected \(name) to exist before sampling rendered pixels.", file: file, line: line)
        let screenshot = XCUIScreen.main.screenshot()
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = "\(name)-pixel-sample"
        attachment.lifetime = .keepAlways
        XCTContext.runActivity(named: "\(name)-pixel-sample") { $0.add(attachment) }

        guard let image = screenshot.image.cgImage else {
            XCTFail("Unable to read \(name) screenshot pixels.", file: file, line: line)
            return
        }
        let screenBounds = UIScreen.main.bounds
        let clippedFrame = element.frame
            .intersection(screenBounds)
            .insetBy(dx: 3, dy: 3)
        guard clippedFrame.width > 8, clippedFrame.height > 8 else {
            XCTFail("Unable to sample \(name): invalid frame \(element.frame).", file: file, line: line)
            return
        }

        let scaleX = CGFloat(image.width) / max(screenBounds.width, 1)
        let scaleY = CGFloat(image.height) / max(screenBounds.height, 1)
        let cropRect = CGRect(
            x: clippedFrame.minX * scaleX,
            y: clippedFrame.minY * scaleY,
            width: clippedFrame.width * scaleX,
            height: clippedFrame.height * scaleY,
        ).integral.intersection(CGRect(x: 0, y: 0, width: image.width, height: image.height))
        guard let cropped = image.cropping(to: cropRect), cropRect.width > 4, cropRect.height > 4 else {
            XCTFail("Unable to crop \(name) screenshot pixels.", file: file, line: line)
            return
        }

        let sampleWidth = 24
        let sampleHeight = 24
        var pixels = [UInt8](repeating: 0, count: sampleWidth * sampleHeight * 4)
        guard let context = CGContext(
            data: &pixels,
            width: sampleWidth,
            height: sampleHeight,
            bitsPerComponent: 8,
            bytesPerRow: sampleWidth * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue,
        ) else {
            XCTFail("Unable to create \(name) pixel sampler.", file: file, line: line)
            return
        }
        context.interpolationQuality = .low
        context.draw(cropped, in: CGRect(x: 0, y: 0, width: sampleWidth, height: sampleHeight))

        var nonBlack = 0
        for offset in stride(from: 0, to: pixels.count, by: 4) {
            let red = Int(pixels[offset])
            let green = Int(pixels[offset + 1])
            let blue = Int(pixels[offset + 2])
            let alpha = Int(pixels[offset + 3])
            if alpha > 24 && max(red, green, blue) > 34 && red + green + blue > 72 {
                nonBlack += 1
            }
        }
        let ratio = Double(nonBlack) / Double(sampleWidth * sampleHeight)
        XCTAssertGreaterThanOrEqual(
            ratio,
            minimumNonBlackRatio,
            "\(name) rendered too dark: nonBlackRatio=\(ratio), frame=\(element.frame), crop=\(cropRect).",
            file: file,
            line: line,
        )
    }
}
