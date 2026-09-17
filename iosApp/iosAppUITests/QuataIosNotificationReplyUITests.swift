import Foundation
import XCTest

/// The host runner seeds an authorized session and owns the disposable conversation and cleanup.
/// UI submission alone is not a delivery verdict: the runner must verify the exact backend message.
final class QuataIosNotificationReplyUITests: XCTestCase {
    /// Positive gesture control only. Does not launch Quata or create a notification.
    func testSystemIconLongPressControl() throws {
        let environment = ProcessInfo.processInfo.environment
        guard environment["QUATA_IOS_SYSTEM_GESTURE_CONTROL"] == "1" else {
            throw XCTSkip("System gesture control is opt-in.")
        }
        continueAfterFailure = false
        XCTAssertNotEqual(environment["QUATA_IOS_NOTIFICATION_REPLY_UI_E2E"], "1")
        XCTAssertNotEqual(environment["QUATA_IOS_NOTIFICATION_REPLY_UI_PILOT"], "1")
        let directory = URL(fileURLWithPath: try XCTUnwrap(environment["QUATA_IOS_REPLY_COORDINATOR_DIRECTORY"]),
                            isDirectory: true).standardizedFileURL
        XCTAssertTrue(directory.lastPathComponent.hasPrefix("quata-ios-reply-"))
        let receipt = directory.appendingPathComponent("gesture-control.json")
        XCTAssertFalse(FileManager.default.fileExists(atPath: receipt.path))
        let system = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        XCUIDevice.shared.press(.home)
        var dismissed = false
        defer { if !dismissed { XCUIDevice.shared.press(.home) } }
        func attach(_ name: String) {
            let screenshot = XCTAttachment(screenshot: system.screenshot())
            screenshot.name = name; screenshot.lifetime = .keepAlways; add(screenshot)
            let tree = XCTAttachment(string: system.debugDescription)
            tree.name = name + " hierarchy"; tree.lifetime = .keepAlways; add(tree)
        }
        let icons = system.icons.matching(NSPredicate(format: "label == %@ OR label == %@", "Ajustes", "Settings"))
        XCTAssertTrue(icons.firstMatch.waitForExistence(timeout: 10))
        attach("Before system icon press")
        let visible = icons.allElementsBoundByIndex.filter { $0.isHittable }
        XCTAssertEqual(visible.count, 1)
        let icon = try XCTUnwrap(visible.first)
        let frame = icon.frame
        let screen = system.frame
        XCTAssertGreaterThan(frame.minX, screen.minX + 10)
        XCTAssertLessThan(frame.maxX, screen.maxX - 10)
        XCTAssertGreaterThan(frame.minY, screen.minY + 100)
        XCTAssertLessThan(frame.maxY, screen.maxY - 120)
        icon.press(forDuration: 1)
        // These two menu items were observed on this simulator. Settings does not
        // expose a Bluetooth shortcut here; absence of that shortcut is not a gesture failure.
        let editHome = system.buttons["com.apple.springboardhome.application-shortcut-item.rearrange-icons"]
        let removeApp = system.buttons["com.apple.springboardhome.application-shortcut-item.remove-app"]
        let menuVisible = editHome.waitForExistence(timeout: 5) && editHome.isHittable
            && removeApp.exists && removeApp.isHittable
        attach("After system icon press")
        XCTAssertTrue(menuVisible, "Expected Settings quick-action menu; icon edit mode is not a positive control.")
        XCUIDevice.shared.press(.home)
        let editRemoved = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: editHome)
        let removeRemoved = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: removeApp)
        XCTAssertEqual(XCTWaiter.wait(for: [editRemoved, removeRemoved], timeout: 5), .completed,
                       "Dismiss the menu without selecting an action.")
        dismissed = true
        try JSONSerialization.data(withJSONObject: ["systemContextMenuVisible": true,
            "menuDismissed": true, "notificationCreated": false, "replySubmitted": false])
            .write(to: receipt, options: .withoutOverwriting)
    }

    func testReplyThroughTheSystemNotification() throws {
        try performReplyUI(affordanceOnly: false)
    }

    /// Editor pilot only: no session seeding, text entry or Send.
    func testInspectReplyAffordanceWithoutSending() throws {
        try performReplyUI(affordanceOnly: true)
    }

    private func performReplyUI(affordanceOnly: Bool) throws {
        let environment = ProcessInfo.processInfo.environment
        let realTrial = environment["QUATA_IOS_NOTIFICATION_REPLY_UI_E2E"] == "1"
        let pilot = environment["QUATA_IOS_NOTIFICATION_REPLY_UI_PILOT"] == "1"
        guard (affordanceOnly ? (pilot && !realTrial) : (realTrial && !pilot)) else {
            throw XCTSkip("Notification Reply UI evidence is opt-in.")
        }
        continueAfterFailure = false
        let marker = try XCTUnwrap(environment["QUATA_IOS_REPLY_NOTIFICATION_MARKER"])
        let text = try XCTUnwrap(environment["QUATA_IOS_REPLY_TEXT_MARKER"])
        let coordinatorPath = try XCTUnwrap(environment["QUATA_IOS_REPLY_COORDINATOR_DIRECTORY"])
        for value in [marker, text] {
            XCTAssertNotNil(value.range(of: "^qadata-reply-[a-z0-9-]{8,80}$", options: .regularExpression),
                            "Only disposable non-personal fixture markers may enter this test.")
        }
        let directory = URL(fileURLWithPath: coordinatorPath, isDirectory: true).standardizedFileURL
        XCTAssertTrue(directory.lastPathComponent.hasPrefix("quata-ios-reply-"))
        var isDirectory: ObjCBool = false
        XCTAssertTrue(FileManager.default.fileExists(atPath: directory.path, isDirectory: &isDirectory) && isDirectory.boolValue)
        XCTAssertFalse(FileManager.default.fileExists(atPath: directory.appendingPathComponent("ui-phase.json").path),
                       "A previous attempt must be reconciled; never reuse its coordinator directory.")

        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(es)", "-AppleLocale", "es_ES"]
        if let localPath = environment["QUATA_IOS_LOCAL_NOTIFICATION_COMPARISON_DIRECTORY"] {
            XCTAssertTrue(affordanceOnly && pilot && !realTrial)
            let localDirectory = URL(fileURLWithPath: localPath, isDirectory: true).standardizedFileURL
            XCTAssertTrue(localDirectory.lastPathComponent.hasPrefix("quata-ios-reply-local-"))
            let scheduled = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf:
                localDirectory.appendingPathComponent("scheduled.json"))) as? [String: Any])
            let step = try XCTUnwrap(scheduled["stepId"] as? String)
            XCTAssertEqual(marker, "qadata-reply-alert-\(step)")
            let dueAt = try XCTUnwrap(scheduled["dueAt"] as? Double)
            XCTAssertTrue(dueAt.isFinite)
            XCTAssertLessThan(dueAt - Date().timeIntervalSince1970, 125)
            XCTAssertEqual(app.state, .notRunning)
            while Date().timeIntervalSince1970 < dueAt + 2 {
                RunLoop.current.run(until: Date().addingTimeInterval(0.2))
                XCTAssertEqual(app.state, .notRunning)
            }
            try JSONSerialization.data(withJSONObject: ["stepId": step,
                "appLaunchRequestedAt": Date().timeIntervalSince1970])
                .write(to: localDirectory.appendingPathComponent("launch-requested.json"), options: .withoutOverwriting)
        }
        app.launch()
        XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "quata-ios-feed-host")
            .firstMatch.waitForExistence(timeout: 20), "The normal production host must launch.")

        let alerts = app.buttons.matching(NSPredicate(format: "label == %@ OR label MATCHES %@",
                                                     "Avisos", "^Avisos, [0-9]+$"))
        XCTAssertTrue(alerts.firstMatch.waitForExistence(timeout: 15))
        XCTAssertEqual(alerts.count, 1)
        alerts.firstMatch.tap()
        XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "quata-ios-notifications-host")
            .firstMatch.waitForExistence(timeout: 15))

        let permission = app.buttons["Permitir notificaciones"]
        if permission.waitForExistence(timeout: 3) {
            permission.tap()
            let system = XCUIApplication(bundleIdentifier: "com.apple.springboard")
            let allow = system.alerts.buttons.matching(NSPredicate(format: "label == %@ OR label == %@", "Permitir", "Allow")).firstMatch
            XCTAssertTrue(allow.waitForExistence(timeout: 10), "The real OS permission prompt must be handled.")
            allow.tap()
        }
        XCTAssertFalse(app.buttons["Abrir ajustes"].exists, "Denied notification permission needs explicit fixture reconciliation.")

        if environment["QUATA_IOS_REPLY_OBSERVER_BEFORE_HOME"] == "1" {
            try awaitObserverBeforeHome(directory: directory, marker: marker)
        }
        let homeDeadline = Date().addingTimeInterval(10)
        XCUIDevice.shared.press(.home)
        let system = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        // XCTest updates state asynchronously; visible Home alone is insufficient.
        let backgroundStateAtEntry = app.state
        let backgroundBudgetAtEntry = max(0, homeDeadline.timeIntervalSinceNow)
        let backgroundEntry = XCTAttachment(string: "Home background-state check entry; remaining budget seconds: \(backgroundBudgetAtEntry); sampled application state raw value: \(backgroundStateAtEntry.rawValue)")
        backgroundEntry.name = "Home background-state check entry"
        backgroundEntry.lifetime = .keepAlways
        add(backgroundEntry)
        let background = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            let state = app.state
            return Date() <= homeDeadline
                && (state == .runningBackground || state == .runningBackgroundSuspended)
        }, object: nil)
        let backgroundResult = XCTWaiter.wait(for: [background], timeout: max(0, homeDeadline.timeIntervalSinceNow))
        let backgroundStateAtExit = app.state
        let backgroundBudgetAtExit = max(0, homeDeadline.timeIntervalSinceNow)
        let backgroundExit = XCTAttachment(string: "Home background-state check exit; remaining budget seconds: \(backgroundBudgetAtExit); sampled application state raw value: \(backgroundStateAtExit.rawValue); waiter result raw value: \(backgroundResult.rawValue)")
        backgroundExit.name = "Home background-state check exit"
        backgroundExit.lifetime = .keepAlways
        add(backgroundExit)
        var homeBackgroundState: [String: Any] = [
            "marker": marker,
            "homeDeadlineEpoch": homeDeadline.timeIntervalSince1970,
            "entryStateRaw": backgroundStateAtEntry.rawValue,
            "entryRemainingBudgetSeconds": backgroundBudgetAtEntry,
            "exitStateRaw": backgroundStateAtExit.rawValue,
            "exitRemainingBudgetSeconds": backgroundBudgetAtExit,
            "waiterResultRaw": backgroundResult.rawValue
        ]
        if let intent = try? JSONSerialization.jsonObject(with: Data(contentsOf:
            directory.appendingPathComponent("intent.json"))) as? [String: Any],
           let run = intent["runId"] as? String,
           let step = intent["stepId"] as? String,
           UUID(uuidString: run) != nil,
           UUID(uuidString: step) != nil,
           marker == "qadata-reply-alert-\(step)" {
            homeBackgroundState["runId"] = run
            homeBackgroundState["stepId"] = step
        }
        try JSONSerialization.data(withJSONObject: homeBackgroundState, options: [.sortedKeys])
            .write(to: directory.appendingPathComponent("home-background-state.json"), options: .withoutOverwriting)
        XCTAssertEqual(backgroundResult,
                       .completed, "The app must reach a running background state before delivery.")
        XCTAssertTrue(system.icons.firstMatch.waitForExistence(timeout: max(0, homeDeadline.timeIntervalSinceNow)),
                      "An unlocked Home screen must precede delivery; do not open Notification Center.")
        XCTAssertTrue(system.icons.allElementsBoundByIndex.contains { $0.isHittable })
        XCTAssertTrue(system.textViews.matching(replyInputPredicate).allElementsBoundByIndex.filter { $0.isHittable }.isEmpty,
                      "No pre-existing reply editor may precede the owned banner gesture.")
        attachSystem(system, "Home before delivery")
        try writePhase("ready-for-notification", directory: directory, marker: marker)
        let visibleInput = try openOwnedBannerEditor(system: system, marker: marker)
        if affordanceOnly {
            // The historical phase name is retained for coordinator compatibility;
            // it now requires the native editor, not merely the Reply button.
            try writePhase("reply-affordance-visible", directory: directory, marker: marker)
            return
        }
        let emptyInputFrame = visibleInput.frame
        let emptySendFrame = try ownedEditorSend(system: system, marker: marker, input: visibleInput).frame
        visibleInput.tap()
        attachSystem(system, "Focused reply editor before typing")
        visibleInput.typeText(text)
        attachSystem(system, "Reply editor after typing before verification")
        // SpringBoard removes placeholderValue after typing. Resolve the filled
        // editor by the exact synthetic value, then recheck its native row.
        let typedPredicate = NSPredicate(format: "value == %@ AND identifier != %@", text, "NotificationBody")
        let typedInputs = system.textViews.matching(typedPredicate).allElementsBoundByIndex.filter { $0.isHittable }
        XCTAssertEqual(typedInputs.count, 1)
        let typedInput = try XCTUnwrap(typedInputs.first)
        XCTAssertEqual(typedInput.value as? String, text, "Verify the exact synthetic text before the single Send.")
        XCTAssertEqual(typedInput.frame.minX, emptyInputFrame.minX, accuracy: 2)
        XCTAssertEqual(typedInput.frame.maxX, emptyInputFrame.maxX, accuracy: 2)
        let visibleSend = try ownedEditorSend(system: system, marker: marker, input: typedInput, predicate: typedPredicate)
        XCTAssertEqual(visibleSend.frame.minX, emptySendFrame.minX, accuracy: 2)
        XCTAssertEqual(visibleSend.frame.maxX, emptySendFrame.maxX, accuracy: 2)
        XCTAssertTrue(visibleSend.isEnabled)
        attachSystem(system, "Exact reply text before single Send")
        visibleSend.tap()
        try writePhase("submitted-by-system-ui", directory: directory, marker: marker)
        // Absence is evidence, so observe for the full window; downstream gates remain mandatory.
        let observationStart = ProcessInfo.processInfo.systemUptime
        var stateTransitions = [(timestamp: TimeInterval, state: XCUIApplication.State)]()
        var previousState: XCUIApplication.State?
        attachSystem(system, "Immediately after single Send")
        repeat {
            let state = app.state
            let timestamp = ProcessInfo.processInfo.systemUptime - observationStart
            if state != previousState {
                stateTransitions.append((timestamp, state))
                previousState = state
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        } while ProcessInfo.processInfo.systemUptime - observationStart < 25
        let elapsed = ProcessInfo.processInfo.systemUptime - observationStart
        let transitions = stateTransitions.map { transition in
            String(format: "%.3f seconds", transition.timestamp)
                + ": \(transition.state) (\(transition.state.rawValue))"
        }.joined(separator: "\n")
        let observation = XCTAttachment(string: "Post-Send application state observation: \(elapsed) seconds; state transitions:\n\(transitions)")
        observation.name = "Reply producer state observation"
        observation.lifetime = .keepAlways
        add(observation)
        attachSystem(system, "After reply producer observation")
        // Backend verification, notification outcome and cleanup remain mandatory runner gates.
    }

    private func attachSystem(_ system: XCUIApplication, _ name: String) {
        let screenshot = XCTAttachment(screenshot: system.screenshot())
        screenshot.name = name; screenshot.lifetime = .keepAlways; add(screenshot)
        let tree = XCTAttachment(string: system.debugDescription)
        tree.name = name + " hierarchy"; tree.lifetime = .keepAlways; add(tree)
    }

    private var replyInputPredicate: NSPredicate {
        NSPredicate(format: "placeholderValue == %@ OR placeholderValue == %@", "Mensaje", "Message")
    }

    /// The observed OS editor is in a separate SpringBoard window. Bind it by
    /// the unique expanded owned alert and its own input/Send sibling container.
    private func ownedEditorSend(system: XCUIApplication, marker: String, input: XCUIElement,
                                 predicate: NSPredicate? = nil) throws -> XCUIElement {
        let inputPredicate = predicate ?? replyInputPredicate
        // This informational container becomes non-hittable while the native
        // input menu is active. It must remain unique and visibly in bounds;
        // the editor and Send themselves must still be hittable below.
        let expanded = system.otherElements.matching(identifier: "notification-expanded-view")
            .allElementsBoundByIndex.filter { element in
                guard element.exists else { return false }
                let frame = element.frame
                return !frame.isEmpty && system.frame.contains(frame)
            }
        XCTAssertEqual(expanded.count, 1)
        let alert = try XCTUnwrap(expanded.first)
        XCTAssertGreaterThan(alert.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", marker)).count, 0)
        let inputs = system.textViews.matching(inputPredicate).allElementsBoundByIndex.filter { $0.isHittable }
        XCTAssertEqual(inputs.count, 1)
        XCTAssertEqual(try XCTUnwrap(inputs.first).frame, input.frame)
        let sends = system.buttons.matching(NSPredicate(format: "label == %@ OR label == %@", "Enviar", "Send"))
            .allElementsBoundByIndex.filter { $0.isHittable }
        XCTAssertEqual(sends.count, 1)
        let send = try XCTUnwrap(sends.first)
        let rows = system.otherElements.containing(inputPredicate).allElementsBoundByIndex.filter { row in
            let rowFrame = row.frame
            let inputFrame = input.frame
            guard rowFrame.contains(inputFrame) else { return false }
            let sendFrame = send.frame
            guard rowFrame.contains(sendFrame) else { return false }
            return rowFrame.height <= max(inputFrame.height, sendFrame.height) + 2
                && row.textViews.matching(inputPredicate).count == 1
                && row.buttons.matching(NSPredicate(format: "label == %@ OR label == %@", "Enviar", "Send")).count == 1
        }
        XCTAssertEqual(rows.count, 1, "Require the observed native input/Send row, not a global button.")
        return send
    }

    /// Shared by the no-send pilot and the authenticated trial. All gesture
    /// coordinates are derived from the observed owned banner, never screen edges.
    private func openOwnedBannerEditor(system: XCUIApplication, marker: String) throws -> XCUIElement {
        let markerPredicate = NSPredicate(format: "label CONTAINS %@", marker)
        let marked = system.descendants(matching: .any).matching(markerPredicate)
        XCTAssertTrue(marked.firstMatch.waitForExistence(timeout: 30), "Owned fresh banner not observed.")
        func owns(_ element: XCUIElement) -> Bool {
            element.label.contains(marker) || element.descendants(matching: .any).matching(markerPredicate).count > 0
        }
        func banner() throws -> XCUIElement {
            let screen = system.frame
            let named = system.descendants(matching: .any).matching(identifier: "NotificationShortLookView")
                .allElementsBoundByIndex.filter { $0.isHittable && owns($0) }
            if named.count == 1 { return named[0] }
            XCTAssertLessThanOrEqual(named.count, 1, "Ambiguous ShortLook banner.")
            let leaves = system.staticTexts.matching(NSPredicate(format: "label == %@", marker))
            XCTAssertEqual(leaves.count, 1, "Fallback requires a unique owned text leaf.")
            let leafPredicate = NSPredicate(format: "elementType == %lu AND label == %@", XCUIElement.ElementType.staticText.rawValue, marker)
            let candidates = system.descendants(matching: .any).containing(leafPredicate)
                .allElementsBoundByIndex.filter { element in
                guard [.other, .button, .cell].contains(element.elementType), element.isHittable else { return false }
                let frame = element.frame
                return frame.width > screen.width * 0.5 && frame.height > 30
                    && frame.height < screen.height * 0.4 && frame.minY < screen.height * 0.25
                    && screen.contains(frame) && owns(element)
            }
            let ordered = candidates.sorted { $0.frame.width * $0.frame.height < $1.frame.width * $1.frame.height }
            let first = try XCTUnwrap(ordered.first, "No observed visible banner container; do not press its text child.")
            // Every candidate is an ancestor of the same unique text leaf.
            // Require nested observed geometry as well, not merely minimum area.
            XCTAssertTrue(ordered.allSatisfy { $0.frame.contains(first.frame) }, "Banner ancestors are not geometrically nested.")
            if ordered.count > 1 {
                XCTAssertNotEqual(first.frame, ordered[1].frame, "Ambiguous equally bounded banner containers.")
            }
            return first
        }
        func editor() -> XCUIElement? {
            let inputs = system.textViews.matching(replyInputPredicate).allElementsBoundByIndex.filter { $0.isHittable }
            guard inputs.count == 1, let input = inputs.first else { return nil }
            return input
        }
        func openActionIfPresent() throws -> XCUIElement? {
            if let input = editor() { return input }
            let replies = system.buttons.matching(NSPredicate(format: "label == %@ OR label == %@", "Responder", "Reply"))
                .allElementsBoundByIndex.filter { $0.isHittable }
            if replies.count == 1 {
                let bound = system.descendants(matching: .any).containing(markerPredicate).allElementsBoundByIndex.contains { element in
                    [.other, .button, .cell].contains(element.elementType)
                        && element.frame.height < system.frame.height * 0.8 && element.frame.contains(replies[0].frame)
                        && element.descendants(matching: .button).matching(NSPredicate(format: "label == %@ OR label == %@", "Responder", "Reply")).count == 1
                }
                XCTAssertTrue(bound, "Reply must belong to the owned expanded notification.")
                replies[0].tap()
                _ = system.textViews.firstMatch.waitForExistence(timeout: 5)
                return try XCTUnwrap(editor(), "Reply was tapped once but its associated editor did not appear; do not retry the action.")
            }
            XCTAssertLessThanOrEqual(replies.count, 1, "Ambiguous Reply action.")
            return nil
        }
        let fresh = try banner()
        attachSystem(system, "Fresh owned banner before press")
        fresh.press(forDuration: 1.5)
        attachSystem(system, "Owned banner after press")
        if let input = try openActionIfPresent() {
            _ = try ownedEditorSend(system: system, marker: marker, input: input)
            attachSystem(system, "Owned reply editor after press")
            return input
        }
        throw NSError(domain: "QuataReplyPilot", code: 1, userInfo: [NSLocalizedDescriptionKey:
            "Expansion/editor not verified; preserve hierarchy before any differential gesture."])
    }

    private func awaitObserverBeforeHome(directory: URL, marker: String) throws {
        let input = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf:
            directory.appendingPathComponent("observer-before-home-input.json"))) as? [String: Any])
        XCTAssertEqual(Set(input.keys), ["runId", "stepId", "marker", "deadlineEpoch"])
        let run = try XCTUnwrap(input["runId"] as? String)
        let step = try XCTUnwrap(input["stepId"] as? String)
        XCTAssertNotNil(UUID(uuidString: run))
        XCTAssertNotNil(UUID(uuidString: step))
        XCTAssertEqual(directory.lastPathComponent, "quata-ios-reply-\(step)")
        XCTAssertEqual(marker, "qadata-reply-alert-\(step)")
        XCTAssertEqual(input["marker"] as? String, marker)
        let deadline = try XCTUnwrap(input["deadlineEpoch"] as? Double)
        XCTAssertTrue(deadline.isFinite && deadline > Date().timeIntervalSince1970)
        XCTAssertLessThanOrEqual(deadline - Date().timeIntervalSince1970, 240)
        let authorization = directory.appendingPathComponent("observer-home-authorized.json")
        XCTAssertFalse(FileManager.default.fileExists(atPath: authorization.path))
        try JSONSerialization.data(withJSONObject: ["phase": "awaiting-observer-before-home",
            "marker": marker, "runId": run, "stepId": step])
            .write(to: directory.appendingPathComponent("ui-phase.json"), options: .atomic)
        while !FileManager.default.fileExists(atPath: authorization.path) {
            guard Date().timeIntervalSince1970 < deadline else {
                XCTFail("Observer authorization did not arrive within the existing coordinator budget.")
                throw NSError(domain: "QuataReplyObserverBarrier", code: 1)
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.1))
        }
        let receipt = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: authorization)) as? [String: Any])
        XCTAssertEqual(Set(receipt.keys), ["runId", "stepId", "marker", "authorized"])
        XCTAssertEqual(receipt["runId"] as? String, run)
        XCTAssertEqual(receipt["stepId"] as? String, step)
        XCTAssertEqual(receipt["marker"] as? String, marker)
        XCTAssertEqual(receipt["authorized"] as? Bool, true)
        XCTAssertLessThan(Date().timeIntervalSince1970, deadline)
    }

    private func writePhase(_ phase: String, directory: URL, marker: String) throws {
        let data = try JSONSerialization.data(withJSONObject: ["phase": phase, "marker": marker])
        try data.write(to: directory.appendingPathComponent("ui-phase.json"), options: .atomic)
    }
}
