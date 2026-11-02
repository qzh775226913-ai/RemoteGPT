"""Repository hygiene regressions using isolated miniature projects."""
import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import check_project
import generate_icons


class ProjectStructureTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="remote-project-check-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.registry = {
            "default_java_line_budget": 10,
            "line_budgets": {},
            "python_asset_line_budgets": {"host.py": 10},
            "modules": {"application": ["App"]},
        }
        self.write("app/src/main/java/app/remotessh/App.java", "class App {}\n")
        self.write("app/src/main/assets/host.py", "pass\n")
        self.write("app/src/test/java/AppTest.java", "class AppTest {}\n")
        self.write("app/build.gradle", "versionName '1.1.1'\nversionCode 88\n")
        for name in ("README.md", "README.zh-CN.md", "docs/ACCEPTANCE.md"):
            self.write(name, "1.1.1 / versionCode 88\n")
        self.write("docs/REQUIREMENTS.md", "1.1.1 / versionCode 88\n"
                   "| R-01 | behavior | [test](../app/src/test/java/AppTest.java) |\n")

    def write(self, name, text):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)

    def check(self):
        self.write("tools/project_structure.json", json.dumps(self.registry))
        return check_project.check(self.root)

    def test_registered_project_passes(self):
        self.assertEqual([], self.check())

    def test_html_images_archives_and_contribution_templates_cannot_hide_broken_links(self):
        self.write("README.md", '1.1.1 / versionCode 88\n<img src="docs/missing.png">')
        self.write("docs/history/old.md", "[evidence](missing.md)")
        self.write(".github/pull_request_template.md", "[rules](../missing.md)")
        errors = self.check()
        self.assertEqual(3, len(errors), errors)
        self.assertTrue(all("unresolved local link" in error for error in errors))

    def test_each_current_version_entry_rejects_drift(self):
        for name in ("README.md", "README.zh-CN.md", "docs/REQUIREMENTS.md", "docs/ACCEPTANCE.md"):
            path = self.root / name
            original = path.read_text()
            path.write_text(original.replace("versionCode 88", "versionCode 87"))
            with self.subTest(name=name):
                self.assertTrue(any(name + ": current version" in e for e in self.check()))
            path.write_text(original)

    def test_nested_java_requires_registration_and_cannot_duplicate_a_registry_name(self):
        self.write("app/src/main/java/app/remotessh/ui/Other.java", "class Other {}\n")
        self.write("app/src/main/java/app/remotessh/ui/App.java", "class App {}\n")
        errors = self.check()
        self.assertIn("Other: missing module registration", errors)
        self.assertTrue(any("App: duplicate source name" in e for e in errors))

    def test_deleted_source_budgets_and_new_python_assets_are_detected(self):
        self.registry["line_budgets"]["Deleted"] = 100
        self.write("app/src/main/assets/extra.py", "pass\n")
        errors = self.check()
        self.assertIn("Deleted: stale Java line budget", errors)
        self.assertIn("extra.py: missing Python asset line budget", errors)

    def test_acceptance_cannot_reference_a_missing_requirement(self):
        self.write("docs/ACCEPTANCE.md", "1.1.1 / versionCode 88\nR-99")
        self.assertIn("ACCEPTANCE.md: unknown requirement R-99", self.check())


class GeneratedIconTest(unittest.TestCase):
    def test_removed_spec_icon_is_rejected_then_pruned_without_touching_other_drawables(self):
        with tempfile.TemporaryDirectory(prefix="remote-icon-check-") as folder:
            root = Path(folder)
            res = root / "res"
            (res / "drawable").mkdir(parents=True)
            obsolete = res / "drawable/ic_ui_removed.xml"
            custom = res / "drawable/custom.xml"
            obsolete.write_text("obsolete generated icon")
            custom.write_text("unmanaged drawable")
            (root / "tools").mkdir()
            (root / "tools/icon_spec.json").write_text(json.dumps({
                "strokeWidth": 1.8, "background": "#171717",
                "icons": {"brand": [{"path": "M1,1L2,2"}]},
            }))
            with patch.object(generate_icons, "ROOT", root), patch.object(generate_icons, "RES", res), \
                    contextlib.redirect_stdout(io.StringIO()):
                with patch("sys.argv", ["generate_icons.py", "--check"]):
                    with self.assertRaisesRegex(SystemExit, "ic_ui_removed.xml"):
                        generate_icons.main()
                self.assertTrue(obsolete.exists())
                with patch("sys.argv", ["generate_icons.py"]):
                    generate_icons.main()
                self.assertFalse(obsolete.exists())
                self.assertEqual("unmanaged drawable", custom.read_text())
                with patch("sys.argv", ["generate_icons.py", "--check"]):
                    generate_icons.main()


if __name__ == "__main__":
    unittest.main()
