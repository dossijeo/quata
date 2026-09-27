import Foundation
import XCTest
import QuataShared

final class QuataIosApnsRemoteLogoutEvidenceTests: XCTestCase {
    func testProductionTransportRegistersAndUnregistersOwnedSyntheticToken() throws {
        let environment = ProcessInfo.processInfo.environment
        guard environment["QUATA_IOS_APNS_REMOTE_EVIDENCE"] == "1" else {
            throw XCTSkip("Requires the private APNs logout evidence coordinator.")
        }
        let backendUrl = try required(environment, "QUATA_IOS_APNS_BACKEND_URL")
        let publishableKey = try required(environment, "QUATA_IOS_APNS_PUBLISHABLE_KEY")
        let profileId = try required(environment, "QUATA_IOS_APNS_PROFILE_ID")
        let authUserId = try required(environment, "QUATA_IOS_APNS_AUTH_USER_ID")
        let accessToken = try required(environment, "QUATA_IOS_APNS_ACCESS_TOKEN")
        let deviceToken = try required(environment, "QUATA_IOS_APNS_DEVICE_TOKEN")
        XCTAssertNotNil(deviceToken.range(of: "^[0-9a-f]{64}$", options: .regularExpression))

        let session = AuthSession(
            token: accessToken,
            userId: profileId,
            email: "",
            displayName: "QADATA",
            authUserId: authUserId,
            accessToken: accessToken,
            refreshToken: nil,
            expiresAt: nil,
            isOfficial: false
        )
        let registration = ApnsRegistration(
            session: session,
            token: deviceToken,
            environment: .sandbox
        )
        let transport = IosApnsRegistrationTransport(
            configuration: IosSupabaseAuthRuntimeConfiguration(
                supabaseUrl: backendUrl,
                supabasePublishableKey: publishableKey
            )
        )

        try awaitTransport("register") { completion in
            transport.register(registration: registration, completionHandler: completion)
        }
        try awaitTransport("unregister") { completion in
            transport.unregister(registration: registration, completionHandler: completion)
        }
    }

    private func required(_ environment: [String: String], _ name: String) throws -> String {
        guard let value = environment[name], !value.isEmpty else {
            throw ApnsRemoteEvidenceError.missingEnvironment(name)
        }
        return value
    }

    private func awaitTransport(
        _ operation: String,
        invoke: (@escaping (KotlinBoolean?, Error?) -> Void) -> Void
    ) throws {
        let completed = expectation(description: operation)
        var accepted = false
        var failure: Error?
        invoke { result, error in
            accepted = result?.boolValue == true
            failure = error
            completed.fulfill()
        }
        wait(for: [completed], timeout: 20)
        if let failure { throw failure }
        XCTAssertTrue(accepted, "APNs \(operation) was not accepted by the production transport.")
    }
}

private enum ApnsRemoteEvidenceError: Error {
    case missingEnvironment(String)
}
