"""Run on macOS/Linux with /bin/bash; mocks never inspect a real keychain."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("preflight-ios-local-development-signing.sh")
IDENTITY = "F8EF844BE964B1CE2B7132BBCDCD6509839B70FC"
PRIVATE_LABEL = "fixture-label-must-not-be-printed"


class SigningPreflightTest(unittest.TestCase):
    def run_preflight(self, identities, security_exit=0, platform="Darwin"):
        with tempfile.TemporaryDirectory(prefix="quata-signing-preflight-") as directory:
            root = Path(directory)
            for name, body in {
                "uname": '#!/bin/sh\nprintf "%s\\n" "$FIXTURE_PLATFORM"\n',
                "security": (
                    '#!/bin/sh\n'
                    '[ "$1" = find-identity ] && [ "$2" = -v ] && '
                    '[ "$3" = -p ] && [ "$4" = codesigning ] && '
                    '[ "$5" = "$HOME/Library/Keychains/login.keychain-db" ] && '
                    '[ "$#" = 5 ] || exit 91\n'
                    'printf "%s\\n" "$FIXTURE_IDENTITIES"\n'
                    'printf "%s\\n" "fixture-private-stderr" >&2\n'
                    'exit "$FIXTURE_SECURITY_EXIT"\n'
                ),
            }.items():
                target = root / name
                target.write_text(body, encoding="utf-8")
                target.chmod(0o700)
            environment = dict(os.environ, PATH=f"{root}:/usr/bin:/bin",
                               HOME=str(root / "unused-home"),
                               FIXTURE_PLATFORM=platform,
                               FIXTURE_IDENTITIES=identities,
                               FIXTURE_SECURITY_EXIT=str(security_exit))
            result = subprocess.run(["/bin/bash", str(SCRIPT)], env=environment,
                                    capture_output=True, text=True, timeout=10)
            self.assertNotIn(PRIVATE_LABEL, result.stdout + result.stderr)
            self.assertNotIn("fixture-private-stderr", result.stdout + result.stderr)
            return result

    def test_expected_valid_identity(self):
        result = self.run_preflight(f'  1) {IDENTITY} "{PRIVATE_LABEL}"\n  1 valid identities found')
        self.assertEqual(result.returncode, 0)
        self.assertIn("identity preflight: PASS", result.stdout)

    def test_missing_identity(self):
        self.assertEqual(self.run_preflight("0 valid identities found").returncode, 1)

    def test_duplicate_identity(self):
        self.assertEqual(self.run_preflight(f'1) {IDENTITY} "{PRIVATE_LABEL}"\n'
                                           f'2) {IDENTITY} "{PRIVATE_LABEL}"').returncode, 1)

    def test_failed_security_command_does_not_accept_output(self):
        self.assertEqual(self.run_preflight(f'1) {IDENTITY} "{PRIVATE_LABEL}"', 1).returncode, 1)

    def test_wrong_platform(self):
        self.assertEqual(self.run_preflight("", platform="Linux").returncode, 2)


if __name__ == "__main__":
    unittest.main()
