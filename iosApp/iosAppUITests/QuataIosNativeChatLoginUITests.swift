import XCTest
import UIKit
import Darwin

/// Native Login on an externally delivered Chat route. Results remain private
/// until the coordinator checks attachments; no credential is passed to typeText.
final class QuataIosNativeChatLoginUITests: XCTestCase {
    private enum Failure: Error {
        case unverified, clipboardChangedBeforeRead, clipboardChangedDuringRead, clipboardValueMismatch
    }
    private enum PastePhase: String {
        case focusCompleted, writeStarted, writeCompleted, readStarted, readCompleted, menuStarted
    }
    private var ownedClipboardChange: Int?

    func testObserveDeliveredNativeLoginGate() throws {
        let env = ProcessInfo.processInfo.environment
        guard env["QUATA_IOS_NATIVE_CHAT_GATE_E2E"] == "1",
              let step = env["QUATA_IOS_EXTERNAL_CHAT_STEP"], UUID(uuidString: step) != nil else {
            throw XCTSkip("Requires the owned external native-login gate coordinator.")
        }
        continueAfterFailure = false
        let app = XCUIApplication(bundleIdentifier: "com.quata.ios")
        let prompt = element("quata-ios-auth-required-dialog", app)
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        print("QUATA_DEEP_LINK_CHAT_OBSERVER_READY:\(step)")
        fflush(stdout)
        let deadline = Date().addingTimeInterval(45)
        var opened = false
        while Date() < deadline && !prompt.waitForExistence(timeout: 0.5) {
            let open = springboard.alerts.buttons.matching(NSPredicate(format: "label IN %@", ["Abrir", "Open"])).firstMatch
            if !opened && open.exists && open.isHittable { opened = true; open.tap() }
        }
        let login = app.buttons.matching(NSPredicate(format: "label IN %@", ["Ya tengo cuenta", "I have an account"])).firstMatch
        // The native presentation container exists before Compose mounts its
        // actionable content. Readiness belongs to the button, not the container.
        try require(login.waitForExistence(timeout: 10) && wait { login.isHittable })
        try require(app.state == .runningForeground && prompt.exists)
        try require(!element("quata-ios-auth-host", app).exists && !element("quata-ios-chat-host", app).exists)
        capture("native-login-delivered-gate", app)
        // Leave the delivered route pending. The private Login test runs only
        // after the coordinator's durable ticket; never launch or activate here.
    }

    func testSyntheticNativeLoginPaste() throws {
        guard ProcessInfo.processInfo.environment["QUATA_IOS_NATIVE_LOGIN_PASTE_PREFLIGHT"] == "1" else {
            throw XCTSkip("Requires the dedicated synthetic paste preflight.")
        }
        continueAfterFailure = false
        let app = XCUIApplication(bundleIdentifier: "com.quata.ios")
        app.launchArguments = ["-quata-ui-test-fixture", "auth-launch", "-quata-auth-destination", "login"]
        app.launch()
        defer { _ = clearOwnedClipboard(); app.terminate() }
        try require(element("auth.phone", app).waitForExistence(timeout: 25))
        try fillLogin(phone: "799000000000", password: "Synthetic-only-password-7!", app: app, synthetic: true)
        // No Submit, real identity, backend mutation or stored native session.
        try require(clearOwnedClipboard())
    }

    func testSyntheticDeliveredNativeLoginPaste() throws {
        guard ProcessInfo.processInfo.environment["QUATA_IOS_NATIVE_DELIVERED_PASTE_PREFLIGHT"] == "1" else {
            throw XCTSkip("Requires the dedicated externally delivered synthetic paste preflight.")
        }
        continueAfterFailure = false
        let app = XCUIApplication(bundleIdentifier: "com.quata.ios")
        defer { _ = clearOwnedClipboard(); app.terminate() }
        // The coordinator delivers the public URL and observes its gate first.
        // Preserve that process and navigation; do not launch a fixture host.
        try require(app.state == .runningForeground)
        try require(element("quata-ios-auth-required-dialog", app).waitForExistence(timeout: 10))
        try require(!element("quata-ios-auth-host", app).exists && !element("quata-ios-chat-host", app).exists)
        let login = app.buttons.matching(NSPredicate(format: "label IN %@", ["Ya tengo cuenta", "I have an account"])).firstMatch
        try require(login.waitForExistence(timeout: 10) && login.isHittable)
        login.tap()
        try require(element("quata-ios-auth-host", app).waitForExistence(timeout: 15))
        try require(element("auth.phone", app).waitForExistence(timeout: 15))
        try fillLogin(phone: "799000000000", password: "Synthetic-only-password-7!", app: app, synthetic: true)
        // Deliberately no Submit: this diagnoses the native edit interaction only.
        try require(clearOwnedClipboard())
    }

    func testResumeDeliveredChatAfterNativeLogin() throws {
        let env = ProcessInfo.processInfo.environment
        guard env["QUATA_IOS_NATIVE_CHAT_LOGIN_E2E"] == "1",
              let path = env["QUATA_IOS_NATIVE_CHAT_LOGIN_DIRECTORY"] else {
            throw XCTSkip("Requires the owned native-login coordinator.")
        }
        continueAfterFailure = false
        defer { _ = clearOwnedClipboard() }
        let files = try RecoverySecretPrivateFiles(path: path)
        let input = files.input
        guard input.stage == "login", input.countryCode == "240",
              let ticket = input.ticketId, UUID(uuidString: ticket) != nil,
              let phone = input.phone, phone.range(of: "^[0-9]{8,15}$", options: .regularExpression) != nil,
              let password = input.password, (12...128).contains(password.count),
              input.question == nil, input.answer == nil, input.questionLabel == nil,
              input.runId == env["QUATA_IOS_EXTERNAL_CHAT_RUN"], input.stepId == env["QUATA_IOS_EXTERNAL_CHAT_STEP"],
              let thread = env["QUATA_IOS_EXTERNAL_CHAT_THREAD"], let message = env["QUATA_IOS_EXTERNAL_CHAT_MESSAGE"],
              [thread, message].allSatisfy({ $0.range(of: "^[1-9][0-9]{0,15}$", options: .regularExpression) != nil }) else {
            throw Failure.unverified
        }
        try files.claimOperation()
        let app = XCUIApplication(bundleIdentifier: "com.quata.ios")
        let prompt = element("quata-ios-auth-required-dialog", app)
        let auth = element("quata-ios-auth-host", app)
        let host = element("quata-ios-chat-host", app)
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        print("QUATA_DEEP_LINK_CHAT_OBSERVER_READY:\(input.stepId)")
        fflush(stdout)
        let deadline = Date().addingTimeInterval(45)
        var opened = false
        while Date() < deadline && !prompt.waitForExistence(timeout: 0.5) {
            let open = springboard.alerts.buttons.matching(NSPredicate(format: "label IN %@", ["Abrir", "Open"])).firstMatch
            if !opened && open.exists && open.isHittable { opened = true; open.tap() }
        }
        try require(prompt.exists && !auth.exists && !host.exists)
        capture("native-login-gate", app)
        let login = app.buttons.matching(NSPredicate(format: "label IN %@", ["Ya tengo cuenta", "I have an account"])).firstMatch
        try require(login.waitForExistence(timeout: 10) && login.isHittable)
        login.tap()
        try require(auth.waitForExistence(timeout: 15))
        try require(element("auth.phone", app).waitForExistence(timeout: 15))
        try fillLogin(phone: phone, password: password, app: app)
        let submit = element("auth.submit", app)
        try require(submit.waitForExistence(timeout: 10) && submit.isHittable && submit.isEnabled)
        submit.tap() // Exactly one Submit; never replay after an uncertain result.
        let body = "Deep link \(input.runId)"
        let selected = app.buttons.matching(NSPredicate(format: "identifier == %@ AND label BEGINSWITH %@",
            "chat.message.\(message).selected", "Deep link fixture: \(body), ")).firstMatch
        try require(selected.waitForExistence(timeout: 45) && selected.isHittable)
        try require(selected.staticTexts.matching(NSPredicate(format: "label == %@", body)).firstMatch.exists)
        try require(host.exists && ["chat:sb:\(thread)", "chat:sb:\(thread)?message=\(message)"].contains(host.value as? String ?? ""))
        try require(!auth.exists && !element("auth.phone", app).exists && !element("auth.password", app).exists)
        try require(clearOwnedClipboard())
        capture("native-login-focused", app)
        let back = element("chat.back", app)
        try require(back.waitForExistence(timeout: 10) && back.isHittable)
        back.tap()
        try require(wait(timeout: 15) { host.exists && (host.value as? String ?? "").isEmpty && !back.exists && !selected.exists && !auth.exists && !prompt.exists })
        let until = Date().addingTimeInterval(2)
        while Date() < until {
            try require(app.state == .runningForeground && host.exists && (host.value as? String ?? "").isEmpty &&
                        !back.exists && !selected.exists && !auth.exists && !prompt.exists)
            Thread.sleep(forTimeInterval: 0.1)
        }
        capture("native-login-back", app)
        try files.writeReceipt(["passed": true, "submitCount": 1, "messageId": message,
                                "clipboardCleared": true, "postExitObservationMs": 2000])
    }

    private func element(_ identifier: String, _ app: XCUIApplication) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: identifier).firstMatch
    }

    private func require(_ value: Bool) throws { if !value { throw Failure.unverified } }

    private func wait(timeout: TimeInterval = 10, _ predicate: () -> Bool) -> Bool {
        let until = Date().addingTimeInterval(timeout)
        repeat {
            if predicate() { return true }
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        } while Date() < until
        return false
    }

    private func fillLogin(phone: String, password: String, app: XCUIApplication, synthetic: Bool = false) throws {
        try require(app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "+240")).firstMatch.exists)
        let phoneField = try phoneEditor(app)
        try paste(phone, into: phoneField, app: app, synthetic: synthetic)
        try require(wait { (phoneField.value as? String) == phone })
        try require(clearOwnedClipboard())
        try paste(password, into: element("auth.password", app), app: app, synthetic: synthetic)
        try require(wait {
            let value = self.element("auth.password", app).value as? String
            return value == password || value == String(repeating: "•", count: password.count)
        })
        try require(clearOwnedClipboard())
        for label in ["Done", "OK", "Aceptar", "Listo", "Return", "Intro"] {
            let key = app.keyboards.buttons[label].firstMatch
            if key.exists && key.isHittable { key.tap(); return }
        }
        if app.keyboards.count > 0 { app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.08)).tap() }
    }

    private func phoneEditor(_ app: XCUIApplication) throws -> XCUIElement {
        // Address the editable field directly; Compose exposes the surrounding
        // row separately and its layout is not an editor identity contract.
        let editor = element("auth.phone.input", app)
        try require(editor.waitForExistence(timeout: 10))
        return editor
    }

    /// Same native edit-menu gesture used by the existing private recovery steps.
    private func paste(_ text: String, into target: XCUIElement, app: XCUIApplication, synthetic: Bool) throws {
        try require(target.waitForExistence(timeout: 10) && target.isHittable)
        // Focus/idle waits can outlast the private clipboard's 60-second lifetime.
        // Complete focus before creating the value; do not extend its lifetime.
        target.tap()
        pastePhase(.focusCompleted)
        let board = UIPasteboard.general
        pastePhase(.writeStarted)
        board.setItems([["public.utf8-plain-text": text]], options: [.localOnly: true, .expirationDate: Date().addingTimeInterval(60)])
        pastePhase(.writeCompleted)
        let change = board.changeCount
        ownedClipboardChange = change
        guard board.changeCount == change else { throw Failure.clipboardChangedBeforeRead }
        pastePhase(.readStarted)
        let matches = board.string == text
        pastePhase(.readCompleted)
        guard board.changeCount == change else { throw Failure.clipboardChangedDuringRead }
        guard matches else { throw Failure.clipboardValueMismatch }
        pastePhase(.menuStarted)
        target.doubleTap()
        let paste = app.descendants(matching: .any).matching(NSPredicate(format: "label IN %@", ["Pegar", "Paste"])).firstMatch
        if paste.waitForExistence(timeout: 5) && paste.isHittable { paste.tap(); return }
        if synthetic { capture("synthetic-native-login-paste-menu", app) }
        throw Failure.unverified
    }

    private func pastePhase(_ phase: PastePhase) {
        // Fixed phase names and monotonic time only; never values or identities.
        print("QUATA_NATIVE_PASTE:\(phase.rawValue):\(ProcessInfo.processInfo.systemUptime)")
        fflush(stdout)
    }

    private func clearOwnedClipboard() -> Bool {
        guard let change = ownedClipboardChange else { return true }
        let board = UIPasteboard.general
        guard board.changeCount == change else { return false }
        board.setItems([], options: [.localOnly: true])
        guard board.items.isEmpty else { return false }
        ownedClipboardChange = nil
        return true
    }

    private func capture(_ name: String, _ app: XCUIApplication) {
        let capture = XCTAttachment(screenshot: app.screenshot())
        capture.name = name
        capture.lifetime = .keepAlways
        add(capture)
    }
}
