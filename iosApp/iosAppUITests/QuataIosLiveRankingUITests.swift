import XCTest

final class QuataIosLiveRankingUITests: XCTestCase {
    func testFeedRemoteRankingFailsClosedRetriesAndOpensExactTarget() {
        runRemoteRankingScenario(
            fixture: "live-ranking-feed",
            rootIdentifier: "feed.root",
            initialMarker: "Feed initial pager remains intact",
            liveActionIdentifier: "feed.action.live.feed-ranking-fixture-0",
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
            liveActionIdentifier: "official.action.live.official-ranking-fixture-0",
            errorIdentifier: "official-ranking-error",
            retryIdentifier: "official-ranking-retry",
            targetId: "official-ranking-remote-target",
            targetText: "Official remote ranking target loaded exactly",
            screenshotPrefix: "ios-official-live-ranking"
        )
    }

    func testOfficialNativePagerPreservesFirstPageRetriesAndReachesDeepTarget() {
        executionTimeAllowance = 180
        let app = XCUIApplication()
        app.launchEnvironment["QUATA_IOS_AUTH_UI_E2E"] = "1"
        app.launchArguments += [
            "-AppleLanguages", "(es)",
            "-AppleLocale", "es_ES",
            "-quata-ui-test-fixture", "live-ranking-official",
            "-quata-live-ranking-fail-first-page",
        ]
        app.launch()

        let root = element("official-feed-common-root", in: app)
        XCTAssertTrue(root.waitForExistence(timeout: 20), app.debugDescription)
        XCTAssertTrue(element("official-feed-common-state.created.none.count.50", in: app).waitForExistence(timeout: 20), app.debugDescription)
        XCTAssertTrue(app.staticTexts["Official initial pager remains intact"].exists, app.debugDescription)
        let pager = app.scrollViews.firstMatch
        XCTAssertTrue(pager.waitForExistence(timeout: 5), app.debugDescription)

        let olderError = element("official-older-posts-error", in: app)
        for _ in 0..<48 {
            if olderError.exists { break }
            advancePager(pager)
        }
        XCTAssertTrue(olderError.waitForExistence(timeout: 10), app.debugDescription)
        XCTAssertTrue(element("official-feed-common-state.created.none.count.50", in: app).exists, app.debugDescription)
        XCTAssertFalse(app.staticTexts["Official remote ranking target loaded exactly"].exists)
        attachScreenshot(app, name: "ios-official-pagination-preserved-error")

        let retry = element("official-older-posts-retry", in: app)
        XCTAssertTrue(retry.waitForExistence(timeout: 5), app.debugDescription)
        retry.tap()
        XCTAssertTrue(element("official-feed-common-state.created.none.count.100", in: app).waitForExistence(timeout: 15), app.debugDescription)
        XCTAssertFalse(olderError.exists)

        let target = app.staticTexts["Official remote ranking target loaded exactly"]
        for _ in 0..<15 {
            if target.exists { break }
            advancePager(pager)
        }
        XCTAssertTrue(target.waitForExistence(timeout: 10), app.debugDescription)
        XCTAssertTrue(target.isHittable, app.debugDescription)
        XCTAssertTrue(root.exists)
        attachScreenshot(app, name: "ios-official-pagination-deep-target")
    }

    private func runRemoteRankingScenario(
        fixture: String,
        rootIdentifier: String,
        initialMarker: String,
        liveActionIdentifier: String,
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

        let liveAction = element(liveActionIdentifier, in: app)
        XCTAssertTrue(liveAction.waitForExistence(timeout: 10), app.debugDescription)
        XCTAssertTrue(liveAction.isHittable, app.debugDescription)
        liveAction.tap()

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

    private func advancePager(_ pager: XCUIElement) {
        let start = pager.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.86))
        let end = pager.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.12))
        start.press(
            forDuration: 0.05,
            thenDragTo: end,
            withVelocity: .fast,
            thenHoldForDuration: 0
        )
    }

    private func attachScreenshot(_ app: XCUIApplication, name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
