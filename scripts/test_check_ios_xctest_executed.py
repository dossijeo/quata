import importlib.util
from pathlib import Path
import unittest


MODULE_PATH = Path(__file__).with_name("check-ios-xctest-executed.py")
SPEC = importlib.util.spec_from_file_location("ios_xctest_execution_check", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)
selected_test_passed = MODULE.selected_test_passed
xcodebuild_test_succeeded = MODULE.xcodebuild_test_succeeded


METHOD = "testSeedAuthenticatedSessionForVisualGates"


class XCTestExecutionContractTests(unittest.TestCase):
    def test_accepts_the_real_xcode_passed_format(self):
        log = (
            "Test Case '-[QuataIosTests.QuataIosAuthenticatedSessionSeederTests testSeedAuthenticatedSessionForVisualGates]' passed (1.667 seconds).\n"
            "** TEST SUCCEEDED **\n"
        )
        self.assertTrue(selected_test_passed(log, METHOD))

    def test_accepts_the_xcode_26_execute_success_marker(self):
        self.assertTrue(xcodebuild_test_succeeded("** TEST EXECUTE SUCCEEDED **\n"))

    def test_accepts_the_legacy_success_marker(self):
        self.assertTrue(xcodebuild_test_succeeded("** TEST SUCCEEDED **\n"))

    def test_rejects_failed_skipped_and_absent_terminal_markers(self):
        for marker in (
            "** TEST FAILED **",
            "** TEST SKIPPED **",
            "xcodebuild exited 0",
            "** TEST EXECUTE SUCCEEDED **\n** TEST FAILED **",
            "** TEST SUCCEEDED **\n** TEST SKIPPED **",
        ):
            with self.subTest(marker=marker):
                self.assertFalse(xcodebuild_test_succeeded(marker + "\n"))

    def test_rejects_skipped_execution(self):
        log = "Test Case '-[QuataIosTests.QuataIosAuthenticatedSessionSeederTests testSeedAuthenticatedSessionForVisualGates]' skipped (0.001 seconds).\n"
        self.assertFalse(selected_test_passed(log, METHOD))

    def test_rejects_failed_execution(self):
        log = "Test Case '-[QuataIosTests.QuataIosAuthenticatedSessionSeederTests testSeedAuthenticatedSessionForVisualGates]' failed (0.001 seconds).\n"
        self.assertFalse(selected_test_passed(log, METHOD))

    def test_rejects_missing_execution(self):
        self.assertFalse(selected_test_passed("** TEST EXECUTE SUCCEEDED **\n", METHOD))
