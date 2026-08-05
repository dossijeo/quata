import XCTest

final class QuataIosFeedPlaybackUITests: XCTestCase {
    func testFeedMuteIconTogglesTheSharedAudioState() {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(es)", "-AppleLocale", "es_ES"]
        app.launch()
        dismissStartupWhatsNewIfPresent(app)

        let mute = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", "Silenciar"))
            .firstMatch
        guard mute.waitForExistence(timeout: 20) else {
            attachScreenshot(app, name: "feed-mute-control-missing")
            XCTFail(app.debugDescription)
            return
        }
        attachScreenshot(app, name: "feed-before-mute")
        mute.tap()

        let unmute = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", "Activar sonido"))
            .firstMatch
        guard unmute.waitForExistence(timeout: 5) else {
            attachScreenshot(app, name: "feed-unmute-control-missing")
            XCTFail(app.debugDescription)
            return
        }
        attachScreenshot(app, name: "feed-after-mute")
        unmute.tap()
        guard mute.waitForExistence(timeout: 5) else {
            attachScreenshot(app, name: "feed-remute-control-missing")
            XCTFail(app.debugDescription)
            return
        }
        attachScreenshot(app, name: "feed-after-unmute")
    }

    private func dismissStartupWhatsNewIfPresent(_ app: XCUIApplication) {
        let host = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-whats-new-host")
            .firstMatch
        guard host.waitForExistence(timeout: 3) else { return }

        let dismiss = app.descendants(matching: .any)
            .matching(identifier: "dismiss_whats_new")
            .firstMatch
        if dismiss.waitForExistence(timeout: 2) {
            dismiss.tap()
            XCTAssertFalse(host.waitForExistence(timeout: 5), app.debugDescription)
            return
        }

        let next = app.descendants(matching: .any)
            .matching(identifier: "next_whats_new")
            .firstMatch
        guard next.waitForExistence(timeout: 2) else {
            attachScreenshot(app, name: "whats-new-dismiss-control-missing")
            XCTFail(app.debugDescription)
            return
        }
        next.tap()
        XCTAssertFalse(host.waitForExistence(timeout: 5), app.debugDescription)
    }

    private func attachScreenshot(_ app: XCUIApplication, name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
