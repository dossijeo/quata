import AVFoundation
import XCTest
import QuataShared

final class IosMediaPermissionRuntimeTests: XCTestCase {
    private lazy var permissions: IosCompositePermissionService = {
        let unavailable = FixedPermissionService(status: .unavailable)
        return IosCompositePermissionService(
            location: unavailable,
            camera: IosCameraPermissionService(),
            photos: IosPhotosPermissionService(),
            notifications: unavailable,
            contacts: unavailable,
            microphone: IosMicrophonePermissionService(audioSession: .sharedInstance())
        )
    }()

    func testResetMediaPermissionsExposeNativeUndeterminedStateAndPickerScopedFiles() {
        assertStatus(.denied, for: .camera)
        assertStatus(.denied, for: .microphone)
        assertStatus(.denied, for: .photos)
        assertStatus(.denied, for: .videos)
        assertStatus(.granted, for: .files)
    }

    func testGrantedMicrophoneReflectsSimulatorPrivacyStateAndFilesRemainPickerScoped() {
        assertStatus(.granted, for: .microphone)
        assertStatus(.granted, for: .files)
    }

    func testGrantedPhotoReadWritePermissionReflectsSimulatorPrivacyState() {
        assertStatus(.granted, for: .photos)
        assertStatus(.granted, for: .videos)
    }

    func testRevokedMediaPermissionsReflectSimulatorPrivacyState() {
        assertStatus(.permanentlydenied, for: .microphone)
        assertStatus(.permanentlydenied, for: .photos)
        assertStatus(.permanentlydenied, for: .videos)
        assertStatus(.granted, for: .files)
    }

    private func assertStatus(
        _ expected: PermissionStatus,
        for permission: PlatformPermission,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let completed = expectation(description: "Status for \(permission.name)")
        permissions.status(permission: permission) { status, error in
            XCTAssertNil(error, file: file, line: line)
            XCTAssertTrue(status === expected, "Expected \(expected.name), received \(status?.name ?? "nil")", file: file, line: line)
            completed.fulfill()
        }
        wait(for: [completed], timeout: 5)
    }
}

private final class FixedPermissionService: NSObject, PermissionService {
    private let fixedStatus: PermissionStatus

    init(status: PermissionStatus) {
        fixedStatus = status
    }

    func status(
        permission: PlatformPermission,
        completionHandler: @escaping (PermissionStatus?, Error?) -> Void
    ) {
        completionHandler(fixedStatus, nil)
    }

    func request(
        permission: PlatformPermission,
        completionHandler: @escaping (PermissionStatus?, Error?) -> Void
    ) {
        completionHandler(fixedStatus, nil)
    }
}
