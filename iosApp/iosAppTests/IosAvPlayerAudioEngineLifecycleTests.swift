import AVFoundation
import Foundation
import UIKit
import XCTest
@testable import QuataIos

final class IosAvPlayerAudioEngineLifecycleTests: XCTestCase {
    private var fixtureURL: URL!

    override func setUpWithError() throws {
        fixtureURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("quata-audio-lifecycle-\(UUID().uuidString).wav")
        try makeSilentWav(seconds: 3).write(to: fixtureURL, options: .atomic)
    }

    override func tearDownWithError() throws {
        if let fixtureURL {
            try? FileManager.default.removeItem(at: fixtureURL)
        }
    }

    func testInterruptionBeganPausesWithoutAutomaticResume() throws {
        let engine = try playingEngine()

        NotificationCenter.default.post(
            name: AVAudioSession.interruptionNotification,
            object: AVAudioSession.sharedInstance(),
            userInfo: [AVAudioSessionInterruptionTypeKey: NSNumber(value: AVAudioSession.InterruptionType.began.rawValue)]
        )
        drainMainQueue()

        XCTAssertFalse(engine.state().isPlaying)
        NotificationCenter.default.post(
            name: AVAudioSession.interruptionNotification,
            object: AVAudioSession.sharedInstance(),
            userInfo: [AVAudioSessionInterruptionTypeKey: NSNumber(value: AVAudioSession.InterruptionType.ended.rawValue)]
        )
        drainMainQueue()
        XCTAssertFalse(engine.state().isPlaying, "interruption end must not restart Chat audio without user intent")
    }

    func testOldRouteUnavailablePausesPlayback() throws {
        let engine = try playingEngine()

        NotificationCenter.default.post(
            name: AVAudioSession.routeChangeNotification,
            object: AVAudioSession.sharedInstance(),
            userInfo: [AVAudioSessionRouteChangeReasonKey: NSNumber(value: AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue)]
        )
        drainMainQueue()

        XCTAssertFalse(engine.state().isPlaying)
    }

    func testResigningActivePausesPlayback() throws {
        let engine = try playingEngine()

        NotificationCenter.default.post(name: UIApplication.willResignActiveNotification, object: nil)
        drainMainQueue()

        XCTAssertFalse(engine.state().isPlaying)
    }

    private func playingEngine() throws -> IosAvPlayerAudioEngine {
        let engine = IosAvPlayerAudioEngine()
        let attributes = try FileManager.default.attributesOfItem(atPath: fixtureURL.path)
        let size = (attributes[.size] as? NSNumber)?.int64Value ?? 0
        let loaded = engine.load(
            path: fixtureURL.path,
            displayName: fixtureURL.lastPathComponent,
            mimeType: "audio/wav",
            sizeBytes: size
        )
        XCTAssertTrue(loaded.isLoaded)
        XCTAssertNil(loaded.errorReason)
        let playing = engine.startPlayback()
        XCTAssertNil(playing.errorReason)
        XCTAssertTrue(playing.isPlaying)
        return engine
    }

    private func drainMainQueue() {
        RunLoop.main.run(until: Date().addingTimeInterval(0.05))
    }

    private func makeSilentWav(seconds: Int) -> Data {
        let sampleRate: UInt32 = 8_000
        let channels: UInt16 = 1
        let bitsPerSample: UInt16 = 16
        let sampleCount = Int(sampleRate) * seconds
        let dataSize = UInt32(sampleCount * Int(bitsPerSample / 8))
        let byteRate = sampleRate * UInt32(channels) * UInt32(bitsPerSample / 8)
        let blockAlign = channels * (bitsPerSample / 8)
        var data = Data()
        data.append(contentsOf: Array("RIFF".utf8))
        data.appendLittleEndian(UInt32(36) + dataSize)
        data.append(contentsOf: Array("WAVEfmt ".utf8))
        data.appendLittleEndian(UInt32(16))
        data.appendLittleEndian(UInt16(1))
        data.appendLittleEndian(channels)
        data.appendLittleEndian(sampleRate)
        data.appendLittleEndian(byteRate)
        data.appendLittleEndian(blockAlign)
        data.appendLittleEndian(bitsPerSample)
        data.append(contentsOf: Array("data".utf8))
        data.appendLittleEndian(dataSize)
        data.append(Data(repeating: 0, count: Int(dataSize)))
        return data
    }
}

private extension Data {
    mutating func appendLittleEndian<T: FixedWidthInteger>(_ value: T) {
        var littleEndian = value.littleEndian
        Swift.withUnsafeBytes(of: &littleEndian) { append(contentsOf: $0) }
    }
}
