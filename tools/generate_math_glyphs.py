#!/usr/bin/env python3
"""Extract original U+00AD outlines omitted by Android text shaping; fonts stay unchanged."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import zipfile
import urllib.request
import tempfile
from fontTools.ttLib import TTFont
from fontTools.pens.basePen import BasePen
ROOT = Path(__file__).resolve().parents[1]
AAR_SHA = 'b16824328d45ed86892f7f8aa74a542fc361b8a94004de8ca5ae86113b78a89f'

class OutlinePen(BasePen):
    def __init__(self, glyphs):
        super().__init__(glyphs)
        self.value = []
    def _moveTo(self, p): self.value.append(['M', *p])
    def _lineTo(self, p): self.value.append(['L', *p])
    def _qCurveToOne(self, p, q): self.value.append(['Q', *p, *q])
    def _curveToOne(self, p, q, r): self.value.append(['C', *p, *q, *r])
    def _closePath(self): self.value.append(['Z'])
    def _endPath(self): pass

def generate(aar):
    data = aar.read_bytes()
    if hashlib.sha256(data).hexdigest() != AAR_SHA:
        raise ValueError('Expected unmodified jlatexmath-android 0.2.0 AAR')
    result = {'source': 'ru.noties:jlatexmath-android:0.2.0', 'sha256': AAR_SHA, 'glyphs': []}
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for name in sorted(archive.namelist()):
            if not name.startswith('assets/') or not name.endswith('.ttf'):
                continue
            font = TTFont(io.BytesIO(archive.read(name)))
            glyph = font.getBestCmap().get(173)
            if not glyph:
                continue
            pen = OutlinePen(font.getGlyphSet())
            font.getGlyphSet()[glyph].draw(pen)
            if pen.value:
                result['glyphs'].append({'font': name[7:], 'units': font['head'].unitsPerEm,
                    'advance': font['hmtx'][glyph][0], 'path': pen.value})
    return json.dumps(result, ensure_ascii=True, separators=(',', ':')) + '\n'

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--aar', type=Path)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle'))
    aar = args.aar or next(cache.glob('caches/modules-2/files-2.1/ru.noties/jlatexmath-android/0.2.0/*/*.aar'), None)
    if aar is None:
        # CI may not have resolved Gradle dependencies yet. Only this hash-pinned artifact is accepted.
        url = 'https://repo.maven.apache.org/maven2/ru/noties/jlatexmath-android/0.2.0/jlatexmath-android-0.2.0.aar'
        with tempfile.TemporaryDirectory(prefix='math-glyph-source-') as temp:
            aar = Path(temp) / 'source.aar'
            with urllib.request.urlopen(url, timeout=60) as response:
                aar.write_bytes(response.read())
            expected = generate(aar)
    else:
        expected = generate(aar)
    output = ROOT / 'app/src/main/assets/math_glyphs.json'
    if args.check:
        if not output.exists() or output.read_text() != expected:
            parser.error('Math glyph outlines differ; regenerate with this script')
        print('Math glyph outlines match pinned upstream fonts')
    else:
        output.write_text(expected)

if __name__ == '__main__':
    main()
