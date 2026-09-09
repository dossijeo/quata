import XCTest
import QuataShared
@testable import QuataIos

/// One coordinator-owned step. A timeout retains the private files for reconciliation.
final class QuataIosRecoverySecretSessionTests: XCTestCase {
    func testRecoverySessionStep() throws {
        let environment = ProcessInfo.processInfo.environment
        guard environment["QUATA_IOS_RECOVERY_SECRET_E2E"] == "1",
              let path = environment["QUATA_IOS_RECOVERY_SECRET_STEP_DIRECTORY"] else {
            throw XCTSkip("The focal recovery session step is opt-in.")
        }
        let files = try RecoverySecretPrivateFiles(path: path)
        let input = files.input
        guard let configuration = IosPublicRuntimeConfiguration.feedConfiguration() else {
            throw RecoverySecretStepError.invalidInput
        }
        let bootstrap = IosFeedRuntimeBootstrapKt.createIosFeedRuntimeBootstrap(configuration: configuration)
        let session = bootstrap.authSessionForInteractiveLogin()
        let repository = IosAuthRepositoryKt.createIosAuthRepository(
            configuration: IosPublicRuntimeConfiguration.authConfiguration(from: configuration), session: session)
        try files.claimOperation()

        if input.stage == "empty" {
            guard session.restoredSession() == nil else { throw RecoverySecretStepError.operationUnverified }
            try files.writeReceipt(["sessionEmpty": true])
            return
        }
        if input.stage == "identity" {
            guard let current = session.restoredSession(), current.userId == input.profileId,
                  current.authUserId == input.authUserId else { throw RecoverySecretStepError.operationUnverified }
            try files.writeReceipt(["storedIdentityMatched": true])
            return
        }
        if input.stage == "clear-owned" {
            // Backend revocation is a separate coordinator check. Never clear another actor.
            if let current = session.restoredSession() {
                guard current.userId == input.profileId, current.authUserId == input.authUserId else {
                    throw RecoverySecretStepError.operationUnverified
                }
                session.clear()
            }
            guard session.restoredSession() == nil else { throw RecoverySecretStepError.operationUnverified }
            try files.writeReceipt(["sessionEmpty": true])
            return
        }
        let completed = expectation(description: "focal session operation completed")
        var verified = false
        var calls = 0
        switch input.stage {
        case "login":
            guard session.restoredSession() == nil, input.countryCode == "240",
                  let phone = input.phone, !phone.isEmpty, phone.allSatisfy(\.isNumber),
                  let password = input.password, !password.isEmpty,
                  let ticketId = input.ticketId, UUID(uuidString: ticketId) != nil else {
                throw RecoverySecretStepError.invalidInput
            }
            repository.login(countryCode: "240", phone: phone, password: password) { result, error in
                calls += 1
                defer { completed.fulfill() }
                // Kotlin Result<AuthSession> is exported as Any. Inspect the concrete
                // session saved by the real repository, then have the coordinator
                // validate its bearer; a non-nil completion alone is not success.
                guard error == nil, result != nil, let current = session.restoredSession(),
                      current.userId == input.profileId, current.authUserId == input.authUserId,
                      let bearer = current.accessToken, !bearer.isEmpty else { return }
                do {
                    // Private receipt: the coordinator validates the bearer and journals the exact session.
                    try files.writeReceipt(["ticketId": ticketId, "accessToken": bearer, "sessionRestored": true])
                    verified = true
                } catch { /* Keep generic failure and private state; never print the error object. */ }
            }
        case "logout":
            guard let current = session.restoredSession(), current.userId == input.profileId,
                  current.authUserId == input.authUserId else { throw RecoverySecretStepError.operationUnverified }
            let remoteCompleted = expectation(description: "focal remote logout completed")
            remoteCompleted.assertForOverFulfill = true
            var remoteCalls = 0
            var remoteVerified = false
            let authConfiguration = IosPublicRuntimeConfiguration.authConfiguration(from: configuration)
            let logoutEndpoint = authConfiguration.supabaseUrl.trimmingCharacters(in: .whitespacesAndNewlines)
                .trimmingCharacters(in: CharacterSet(charactersIn: "/")) + "/auth/v1/logout"
            let transport = RecoveryLogoutObservingTransport(endpoint: logoutEndpoint) { succeeded in
                remoteCalls += 1
                remoteVerified = succeeded
                remoteCompleted.fulfill()
            }
            let observedRepository = IosAuthRepositoryKt.iosAuthRepository(dependencies:
                IosAuthRepositoryDependencies(configuration: authConfiguration, session: session, transport: transport))
            observedRepository.logout { error in
                calls += 1
                defer { completed.fulfill() }
                verified = error == nil && session.restoredSession() == nil
            }
            // Keep the real HTTP request alive after the local logout callback.
            // The coordinator still verifies exact Auth-session revocation afterward.
            withExtendedLifetime(observedRepository) { wait(for: [completed, remoteCompleted], timeout: 45) }
            guard calls == 1, verified, remoteCalls == 1, remoteVerified,
                  session.restoredSession() == nil else { throw RecoverySecretStepError.operationUnverified }
            try files.writeReceipt(["sessionEmpty": true])
            return
        default: throw RecoverySecretStepError.invalidInput
        }
        wait(for: [completed], timeout: 45)
        XCTAssertTrue(calls == 1 && verified, "The focal session operation was not verified.")
    }
}

/// Observes completion without changing requests or responses of the production transport.
private final class RecoveryLogoutObservingTransport: NSObject, IosAuthHttpTransport {
    private let delegate = IosUrlSessionAuthHttpTransport()
    private let endpoint: String
    private let completed: (Bool) -> Void

    init(endpoint: String, completed: @escaping (Bool) -> Void) {
        self.endpoint = endpoint
        self.completed = completed
    }

    func get(endpoint: String, headers: [String: String], completionHandler: @escaping (IosAuthHttpResponse?, Error?) -> Void) {
        delegate.get(endpoint: endpoint, headers: headers, completionHandler: completionHandler)
    }

    func post(endpoint: String, headers: [String: String], body: String,
              completionHandler: @escaping (IosAuthHttpResponse?, Error?) -> Void) {
        delegate.post(endpoint: endpoint, headers: headers, body: body) { response, error in
            completionHandler(response, error)
            guard endpoint == self.endpoint else { return }
            let succeeded = error == nil && response.map { (200..<300).contains(Int($0.statusCode)) } == true
            DispatchQueue.main.async { self.completed(succeeded) }
        }
    }
}
