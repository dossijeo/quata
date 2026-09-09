import XCTest
import UIKit

/// Focal UI steps. The external coordinator verifies backend state and owns restoration.
/// Test results are private until their automatic attachments have been inspected.
final class QuataIosRecoverySecretUITests: XCTestCase {
    func testSyntheticRecoveryPaste() throws {
        guard ProcessInfo.processInfo.environment["QUATA_IOS_RECOVERY_PASTE_PREFLIGHT"] == "1" else {
            throw XCTSkip("Synthetic paste preflight is opt-in.")
        }
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-quata-ui-test-fixture", "auth-launch", "-quata-auth-destination", "recovery",
            "-AppleLanguages", "(es)", "-AppleLocale", "es_ES", "-quata-ui-test-language", "es"]
        app.launch()
        defer { app.terminate() }
        try require(element("auth.recovery.root", app).waitForExistence(timeout: 25))
        // Existing deterministic repository; never submit or supply a real identity.
        try paste("799000000000", into: "auth.recovery.phone", app: app, diagnoseSyntheticFailure: true)
        try require(wait { (self.element("auth.recovery.phone", app).value as? String) == "799000000000" })
        try paste("synthetic-only-answer", into: "auth.recovery.secret-answer", app: app, diagnoseSyntheticFailure: true)
        try require(wait { (self.element("auth.recovery.secret-answer", app).value as? String) == "synthetic-only-answer" })
        try paste("Synthetic-only-password-7!", into: "auth.recovery.new-password", app: app, diagnoseSyntheticFailure: true)
        try require(wait { self.passwordMatches("Synthetic-only-password-7!", app) })
    }

    func testRecoverySecretUIStep() throws {
        let environment = ProcessInfo.processInfo.environment
        guard environment["QUATA_IOS_RECOVERY_SECRET_E2E"] == "1" else {
            throw XCTSkip("Recovery secret UI replay is opt-in.")
        }
        guard let path = environment["QUATA_IOS_RECOVERY_SECRET_STEP_DIRECTORY"] else {
            throw RecoverySecretStepError.invalidInput
        }
        continueAfterFailure = false
        let files = try RecoverySecretPrivateFiles(path: path)
        let input = files.input
        guard ["configure", "read", "recover"].contains(input.stage),
              input.countryCode == "240", let phone = input.phone, !phone.isEmpty,
              phone.allSatisfy(\.isNumber), input.question == "madre",
              let questionLabel = input.questionLabel, !questionLabel.isEmpty else {
            throw RecoverySecretStepError.invalidInput
        }
        // A failed/uncertain step keeps this marker. It must never be replayed automatically.
        try files.claimOperation()
        let app = XCUIApplication()
        let selector = NSSelectorFromString("setWaitForQuiescence:")
        if app.responds(to: selector) { _ = app.perform(selector, with: NSNumber(value: false)) }
        app.launchArguments = ["-AppleLanguages", "(es)", "-AppleLocale", "es_ES", "-quata-ui-test-language", "es"]
        if input.stage == "recover" {
            app.launchArguments += ["-quata-ui-test-fixture", "auth-recovery-real"]
        }
        app.launch()
        defer { app.terminate() }

        if input.stage == "recover" {
            let answer = try nonempty(input.answer)
            let password = try nonempty(input.password)
            try require(element("auth.recovery.root", app).waitForExistence(timeout: 25))
            try paste(phone, into: "auth.recovery.phone", app: app)
            try require(wait { (self.element("auth.recovery.phone", app).value as? String) == phone })
            try require(wait { self.contains(questionLabel, in: self.element("auth.recovery.question", app)) })
            try paste(answer, into: "auth.recovery.secret-answer", app: app)
            try require(wait { (self.element("auth.recovery.secret-answer", app).value as? String) == answer })
            try paste(password, into: "auth.recovery.new-password", app: app)
            try require(wait { self.passwordMatches(password, app) })
            dismissKeyboard(app)
            try tap("auth.recovery.submit", app)
            try require(element("auth.submit", app).waitForExistence(timeout: 45))
            try require(!element("auth.recovery.root", app).exists)
            // No attachment while private input fields are mounted.
            _ = QuataIosHostUITestSupport.attachRenderedSurface(named: "recovery-secret-login-return")
            try files.writeReceipt(["loginReturned": true, "submitCount": 1])
            return
        }

        let displayName = try nonempty(input.displayName)
        try require(element("quata-ios-feed-host", app).waitForExistence(timeout: 25))
        try tap("navigation.primary.profile", app)
        try require(element("quata-ios-profile-sos-host", app).waitForExistence(timeout: 25))
        try tap("profile.details.open", app)
        try require(element("profile.details.root", app).waitForExistence(timeout: 15))
        // The coordinator also checks the exact stored profile/Auth identity before this step.
        try require(wait { (self.element("profile.details.name", app).value as? String) == displayName })
        try require((element("profile.details.phone", app).value as? String)?.filter(\.isNumber) == phone)
        let answerField = try visible("profile.details.secret-answer", app)
        try require((answerField.value as? String ?? "").isEmpty)
        try require(!element("profile.details.secret-answer.clear", app).exists)

        if input.stage == "read" {
            try require(contains(questionLabel, in: element("profile.details.secret-question", app)))
            _ = QuataIosHostUITestSupport.attachRenderedSurface(named: "recovery-secret-account-read-answer-empty")
            try files.writeReceipt(["questionMatched": true, "answerEmpty": true, "accountIdentityMatched": true])
            return
        }

        let answer = try nonempty(input.answer)
        try tap("profile.details.secret-question", app)
        try tap("profile.details.secret-question.option.madre", app)
        try paste(answer, into: "profile.details.secret-answer", app: app)
        try require(wait { (self.element("profile.details.secret-answer", app).value as? String) == answer })
        dismissKeyboard(app)
        try tap("profile.details.save", app)
        // Save may navigate away. The next independent read step proves persisted state.
        try require(wait(timeout: 45) {
            self.element("profile.feedback.success", app).exists || !self.element("profile.details.root", app).exists
        })
        try files.writeReceipt(["saveDispatched": true, "saveCount": 1, "accountIdentityMatched": true])
    }

    private func element(_ identifier: String, _ app: XCUIApplication) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: identifier).firstMatch
    }

    private func require(_ value: Bool) throws {
        guard value else { throw RecoverySecretStepError.operationUnverified }
    }

    private func nonempty(_ value: String?) throws -> String {
        guard let value, !value.isEmpty else { throw RecoverySecretStepError.invalidInput }
        return value
    }

    private func visible(_ identifier: String, _ app: XCUIApplication) throws -> XCUIElement {
        let target = element(identifier, app)
        for _ in 0..<8 {
            if target.waitForExistence(timeout: 1), target.isHittable { return target }
            app.swipeUp()
        }
        throw RecoverySecretStepError.operationUnverified
    }

    private func tap(_ identifier: String, _ app: XCUIApplication) throws {
        let target = try visible(identifier, app)
        try require(target.isEnabled)
        target.tap()
    }

    private func contains(_ text: String, in target: XCUIElement) -> Bool {
        guard target.exists else { return false }
        return target.label.contains(text) || (target.value as? String)?.contains(text) == true ||
            target.staticTexts.allElementsBoundByIndex.contains { $0.label.contains(text) }
    }

    private func passwordMatches(_ text: String, _ app: XCUIApplication) -> Bool {
        let value = element("auth.recovery.new-password", app).value as? String
        return value == text || value == String(repeating: "•", count: text.count)
    }

    private func wait(timeout: TimeInterval = 25, _ predicate: () -> Bool) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        repeat {
            if predicate() { return true }
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        } while Date() < deadline
        return false
    }

    private func dismissKeyboard(_ app: XCUIApplication) {
        for label in ["Done", "OK", "Aceptar", "Listo", "Return", "Intro"] {
            let key = app.keyboards.buttons[label].firstMatch
            if key.exists, key.isHittable { key.tap(); return }
        }
        if app.keyboards.count > 0 { app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.08)).tap() }
    }

    /// Never pass a credential to typeText: XCTest records its argument in the result log.
    private func paste(_ text: String, into identifier: String, app: XCUIApplication, diagnoseSyntheticFailure: Bool = false) throws {
        let target = try visible(identifier, app)
        let board = UIPasteboard.general
        let previous = board.items
        board.setItems([["public.utf8-plain-text": text]], options: [.localOnly: true, .expirationDate: Date().addingTimeInterval(60)])
        let ownedChange = board.changeCount
        defer {
            // Do not overwrite a clipboard change made by a different actor.
            if board.changeCount == ownedChange { board.setItems(previous, options: [.localOnly: true]) }
        }
        // Match the existing iOS gesture without its typeText fallback. Native
        // edit actions can expose different element types; their action label is stable.
        let coordinate = target.coordinate(withNormalizedOffset: CGVector(dx: 0.22, dy: 0.5))
        let paste = app.descendants(matching: .any).matching(NSPredicate(format: "label IN %@", ["Pegar", "Paste"])).firstMatch
        for attempt in 0..<3 {
            try require(board.string == text && board.changeCount == ownedChange)
            // Reopen only the nonmutating edit menu. Paste itself is invoked once.
            if attempt > 0 && paste.exists && paste.isHittable { paste.tap(); return }
            coordinate.tap()
            RunLoop.current.run(until: Date().addingTimeInterval(0.3))
            coordinate.press(forDuration: 0.7)
            if paste.waitForExistence(timeout: 5) && paste.isHittable { paste.tap(); return }
        }
        if diagnoseSyntheticFailure {
            // Only the constant-input preflight sets this argument. Capture before
            // restoring the clipboard, which can itself dismiss the edit menu.
            let attachment = XCTAttachment(screenshot: app.screenshot())
            attachment.name = "synthetic-paste-menu-before-cleanup"
            attachment.lifetime = .keepAlways
            add(attachment)
        }
        throw RecoverySecretStepError.operationUnverified
    }
}
