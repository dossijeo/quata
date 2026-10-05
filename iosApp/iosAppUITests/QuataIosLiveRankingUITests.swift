import XCTest

final class QuataIosLiveRankingUITests: XCTestCase {
    func testFeedRemoteRankingFailsClosedRetriesAndOpensExactTarget() {
        runRemoteRankingScenario(
            fixture: "live-ranking-feed",
            rootIdentifier: "feed.root",
            initialMarker: "Feed initial pager remains intact",
            errorIdentifier: "feed-ranking-error",
            retryIdentifier: "feed-ranking-retry",
            targetId: "feed-ranking-remote-target",
            targetText: "Feed remote ranking target loaded exactly",
            screenshotPrefix: "ios-feed-live-ranking"
        )
    }

    func testOfficialRemoteRankingFailsClosedRetriesAndOpensExactTarget() {
        runRemoteRankingScenario(
            fixture: "live-ranking-official",
            rootIdentifier: "official-feed-common-root",
            initialMarker: "Official initial pager remains intact",
            errorIdentifier: "official-ranking-error",
            retryIdentifier: "official-ranking-retry",
            targetId: "official-ranking-remote-target",
            targetText: "Official remote ranking target loaded exactly",
            screenshotPrefix: "ios-official-live-ranking"
        )
    }

    private func runRemoteRankingScenario(
        fixture: String,
        rootIdentifier: String,
        initialMarker: String,
        errorIdentifier: String,
        retryIdentifier: String,
        targetId: String,
        targetText: String,
        screenshotPrefix: String
    ) {
        let app = XCUIApplication()
        app.launchArguments += [
            "-AppleLanguages", "(es)",
            "-AppleLocale", "es_ES",
            "-quata-ui-test-fixture", fixture,
            "-quata-live-ranking-fail-first-page",
        ]
        app.launch()

        let root = element(rootIdentifier, in: app)
        XCTAssertTrue(root.waitForExistence(timeout: 20), app.debugDescription)
        XCTAssertTrue(app.staticTexts[initialMarker].waitForExistence(timeout: 20), app.debugDescription)
        attachScreenshot(app, name: "\(screenshotPrefix)-initial-pager")

        let liveButtons = app.buttons
            .matching(NSPredicate(format: "label BEGINSWITH %@", "LIVE"))
        XCTAssertTrue(liveButtons.firstMatch.waitForExistence(timeout: 10), app.debugDescription)
        revealAndTapFirstHittable(liveButtons, in: app)

        let error = element(errorIdentifier, in: app)
        XCTAssertTrue(error.waitForExistence(timeout: 10), app.debugDescription)
        let targetOpen = element("live.ranking.open.\(targetId)", in: app)
        XCTAssertFalse(targetOpen.exists, "A partial ranking must never be exposed as complete.")
        attachScreenshot(app, name: "\(screenshotPrefix)-closed-error")

        let retry = element(retryIdentifier, in: app)
        XCTAssertTrue(retry.waitForExistence(timeout: 5), app.debugDescription)
        retry.tap()

        XCTAssertTrue(targetOpen.waitForExistence(timeout: 15), app.debugDescription)
        attachScreenshot(app, name: "\(screenshotPrefix)-remote-row")
        targetOpen.tap()

        XCTAssertTrue(app.staticTexts[targetText].waitForExistence(timeout: 15), app.debugDescription)
        XCTAssertTrue(root.exists, "The product host must remain mounted after exact-target navigation.")
        XCTAssertFalse(error.exists, "The recovered ranking error must be cleared.")
        attachScreenshot(app, name: "\(screenshotPrefix)-exact-target")
    }

    private func element(_ identifier: String, in app: XCUIApplication) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: identifier).firstMatch
    }

    private func revealAndTapFirstHittable(_ query: XCUIElementQuery, in app: XCUIApplication) {
        for _ in 0..<4 {
            if let element = query.allElementsBoundByIndex.first(where: \.isHittable) {
                element.tap()
                return
            }
            app.swipeUp()
        }
        XCTFail("No visible LIVE button after bounded scrolling.\n\(app.debugDescription)")
    }

    private func attachScreenshot(_ app: XCUIApplication, name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
