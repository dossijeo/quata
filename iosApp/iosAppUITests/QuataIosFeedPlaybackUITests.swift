import XCTest

final class QuataIosFeedPlaybackUITests: XCTestCase {
    func testFeedMuteIconTogglesTheSharedAudioState() {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(es)", "-AppleLocale", "es_ES"]
        app.launch()

        let mute = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", "Silenciar"))
            .firstMatch
        XCTAssertTrue(mute.waitForExistence(timeout: 20), app.debugDescription)
        attachScreenshot(app, name: "feed-before-mute")
        mute.tap()

        let unmute = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", "Activar sonido"))
            .firstMatch
        XCTAssertTrue(unmute.waitForExistence(timeout: 5), app.debugDescription)
        attachScreenshot(app, name: "feed-after-mute")
        unmute.tap()
        XCTAssertTrue(mute.waitForExistence(timeout: 5), app.debugDescription)
        attachScreenshot(app, name: "feed-after-unmute")
    }

    private func attachScreenshot(_ app: XCUIApplication, name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
