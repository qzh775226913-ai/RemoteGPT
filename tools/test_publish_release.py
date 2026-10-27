"""Distribution regressions use synthetic APKs, never signing keys or GitHub."""
import hashlib
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from publish_release import verify_materials


class DistributionMaterialsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.apk_entries = {}
        self.bundle_entries = {}
        for name in ['LICENSE', 'CONTRIBUTING.md', 'docs/THIRD_PARTY.md']:
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(name.encode())
            self.bundle_entries[name] = path.read_bytes()
        for relative, data in {
            'LICENSE.txt': b'LICENSE', 'CONTRIBUTING.md': b'CONTRIBUTING.md',
            'third_party/component/LICENSE': b'component notice',
        }.items():
            path = self.root / 'app/src/main/assets' / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
            self.apk_entries['assets/' + relative] = data
            self.bundle_entries['app/src/main/assets/' + relative] = data
        self.font = 'assets/org/scilab/forge/jlatexmath/fonts/licences/OFL.txt'
        self.apk_entries[self.font] = self.bundle_entries[self.font] = b'font notice'
        self.bundle_entries['third-party/source.zip'] = b'corresponding source'
        self.sources = {'third-party/source.zip': hashlib.sha256(b'corresponding source').hexdigest()}

    def verify(self):
        apk = self.root / 'test.apk'
        with zipfile.ZipFile(apk, 'w') as archive:
            for name, data in self.apk_entries.items():
                archive.writestr(name, data)
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, 'w') as archive:
            for name, data in self.bundle_entries.items():
                archive.writestr(name, data)
        with patch('publish_release.FORMULA_SOURCES', self.sources), zipfile.ZipFile(buffer) as bundle:
            verify_materials(apk, bundle, self.root)

    def test_complete_materials(self):
        self.verify()

    def test_stale_apk_notice(self):
        self.apk_entries['assets/LICENSE.txt'] = b'old terms'
        with self.assertRaisesRegex(RuntimeError, 'APK notice differs'):
            self.verify()

    def test_stale_bundle_notice(self):
        self.bundle_entries['app/src/main/assets/third_party/component/LICENSE'] = b'old notice'
        with self.assertRaisesRegex(RuntimeError, 'Bundle notice differs'):
            self.verify()

    def test_wrong_corresponding_source(self):
        self.bundle_entries['third-party/source.zip'] = b'wrong source'
        with self.assertRaisesRegex(RuntimeError, 'Corresponding formula source differs'):
            self.verify()

    def test_missing_font_notice(self):
        del self.apk_entries[self.font]
        with self.assertRaisesRegex(RuntimeError, 'Font license notices are missing'):
            self.verify()

    def test_stale_distribution_document(self):
        self.bundle_entries['CONTRIBUTING.md'] = b'old contribution terms'
        with self.assertRaisesRegex(RuntimeError, 'Distribution document differs'):
            self.verify()
