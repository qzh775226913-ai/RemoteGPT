"""Regression checks for acceptance gates: partial/failed evidence must not pass as full."""
from pathlib import Path
import contextlib
import io
import os
import socket
import tempfile
import unittest
from unittest.mock import patch

import check_project
import verify


class VerificationGateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="remote-verification-test-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.results = self.root / "app/build/test-results/testDebugUnitTest"
        self.results.mkdir(parents=True)
        self.lint = self.root / "app/build/reports/lint-results-debug.xml"
        self.lint.parent.mkdir(parents=True)
        self.lint.write_text('<issues><issue severity="Warning"/></issues>')
        self.patch = patch.object(verify, "ROOT", self.root)
        self.patch.start()
        self.addCleanup(self.patch.stop)

    def report(self, skipped=0, failures=0):
        (self.results / "TEST-example.xml").write_text(
            f'<testsuite tests="3" failures="{failures}" errors="0" skipped="{skipped}"/>')

    def test_skipped_ssh_tests_cannot_qualify_as_full_acceptance(self):
        self.report(skipped=1)
        with self.assertRaisesRegex(RuntimeError, "incomplete"):
            verify.android_report(full=True)
        self.assertEqual(1, verify.android_report(full=False)["android"]["skipped"])

    def test_failures_are_rejected_in_both_scopes(self):
        self.report(failures=1)
        for full in (False, True):
            with self.subTest(full=full), self.assertRaises(RuntimeError):
                verify.android_report(full)

    def test_missing_evidence_and_blocking_lint_are_rejected(self):
        with self.assertRaisesRegex(RuntimeError, "test results"):
            verify.android_report(True)
        self.report()
        self.lint.unlink()
        with self.assertRaisesRegex(RuntimeError, "Lint report"):
            verify.android_report(True)
        self.lint.write_text('<issues><issue severity="Error"/></issues>')
        with self.assertRaisesRegex(RuntimeError, "blocking"):
            verify.android_report(True)

    def test_success_preserves_warning_count_in_report(self):
        self.report()
        result = verify.android_report(True)
        self.assertEqual(3, result["android"]["tests"])
        self.assertEqual({"Warning": 1}, result["lint"])
        self.assertTrue(result["ssh_fixture"])

    def test_preflight_failure_removes_previous_success_report(self):
        summary = self.root / "app/build/verification/summary.json"
        summary.parent.mkdir(parents=True)
        summary.write_text('{"full_acceptance": true}')
        with patch("sys.argv", ["verify.py", "--unit"]), \
                patch.dict(os.environ, {"JAVA_HOME": str(self.root / "missing-jdk")}), \
                patch.object(verify.shutil, "which", return_value=None), \
                contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit):
                verify.main()
        self.assertFalse(summary.exists())

    def test_port_guard_rejects_live_listener_but_allows_recently_closed_connection(self):
        with socket.socket() as listener:
            listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            listener.bind(("127.0.0.1", 0))
            listener.listen(1)
            port = listener.getsockname()[1]
            with self.assertRaisesRegex(RuntimeError, "occupied"):
                verify.check_fixture_port(port)
            with socket.create_connection(("127.0.0.1", port)) as client:
                connection, _ = listener.accept()
                connection.close()
                self.assertEqual(b"", client.recv(1))
        verify.check_fixture_port(port)

    def test_current_docs_detect_broken_local_links(self):
        doc = self.root / "README.md"
        doc.write_text('[missing](docs/REQUIREMENTS.md)\n[external](https://example.com)\n')
        self.assertEqual(1, len(check_project.document_links(doc, self.root)))
        (self.root / "docs").mkdir()
        (self.root / "docs/REQUIREMENTS.md").write_text("requirements")
        self.assertEqual([], check_project.document_links(doc, self.root))

    def test_link_examples_in_code_do_not_require_fake_files(self):
        doc = self.root / "README.md"
        doc.write_text('```text\n[example](not-a-real-file.md)\n```\n')
        self.assertEqual([], check_project.document_links(doc, self.root))


if __name__ == "__main__":
    unittest.main()
