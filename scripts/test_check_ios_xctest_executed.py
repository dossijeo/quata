import importlib.util
from pathlib import Path
import unittest


MODULE_PATH = Path(__file__).with_name("check-ios-xctest-executed.py")
SPEC = importlib.util.spec_from_file_location("ios_xctest_execution_check", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)
selected_test_passed = MODULE.selected_test_passed


METHOD = "testSeedAuthenticatedSessionForVisualGates"


class XCTestExecutionContractTests(unittest.TestCase):
    def test_accepts_the_real_xcode_passed_format(self):
        log = "Test Case '-[QuataIosTests.QuataIosAuthenticatedSessionSeederTests testSeedAuthenticatedSessionForVisualGates]' passed (1.667 seconds).\n"
        self.assertTrue(selected_test_passed(log, METHOD))

    def test_rejects_skipped_execution(self):
        log = "Test Case '-[QuataIosTests.QuataIosAuthenticatedSessionSeederTests testSeedAuthenticatedSessionForVisualGates]' skipped (0.001 seconds).\n"
        self.assertFalse(selected_test_passed(log, METHOD))

    def test_rejects_failed_execution(self):
        log = "Test Case '-[QuataIosTests.QuataIosAuthenticatedSessionSeederTests testSeedAuthenticatedSessionForVisualGates]' failed (0.001 seconds).\n"
        self.assertFalse(selected_test_passed(log, METHOD))

    def test_rejects_missing_execution(self):
        self.assertFalse(selected_test_passed("** TEST EXECUTE SUCCEEDED **\n", METHOD))
