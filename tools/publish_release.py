#!/usr/bin/env python3
"""Publish preverified signed assets using temporary annotated-tag input.

No signing key is uploaded, no APK is rebuilt, and no model is called.
Only this explicitly triggered job receives repository contents-write access.
"""
import hashlib
from html.parser import HTMLParser
import json
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT).decode().strip()


def download_asset(url):
    def fetch(link):
        return subprocess.check_output([
            'curl', '--silent', '--show-error', '--fail', '--location',
            '--proto', '=https', '--proto-redir', '=https', '--retry', '2',
            '--max-time', '60', '--max-filesize', str(12 * 1024 * 1024), link,
        ])
    parts = urllib.parse.urlsplit(url)
    if parts.hostname == 'tmpfiles.org' and not parts.path.startswith('/dl/'):
        class DownloadLink(HTMLParser):
            link = None
            def handle_starttag(self, tag, attrs):
                attrs = dict(attrs)
                if tag == 'a' and attrs.get('class') == 'download':
                    self.link = attrs.get('href')
        parser = DownloadLink()
        parser.feed(fetch(url).decode())
        if not parser.link or not parser.link.startswith('https://tmpfiles.org/dl/'):
            raise RuntimeError('Temporary host did not provide a public download link')
        url = parser.link
    return fetch(url)


def api(method, path, body=None, content_type='application/json'):
    url = path if path.startswith('https://uploads.github.com/') else 'https://api.github.com' + path
    request = urllib.request.Request(url, data=body, method=method, headers={
        'Authorization': 'Bearer ' + os.environ['GH_TOKEN'],
        'Accept': 'application/vnd.github+json', 'Content-Type': content_type,
        'X-GitHub-Api-Version': '2022-11-28',
    })
    try:
        with urllib.request.urlopen(request, timeout=90) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        raise RuntimeError(f'GitHub {method} failed with HTTP {error.code}') from None


def wait_for_verification(repo, commit):
    deadline = time.monotonic() + 20 * 60
    while time.monotonic() < deadline:
        runs = api('GET', f'/repos/{repo}/actions/runs?head_sha={commit}&per_page=100')['workflow_runs']
        checks = [r for r in runs if r.get('path') == '.github/workflows/verify.yml'
                  and r.get('head_sha') == commit]
        if any(r.get('conclusion') == 'success' for r in checks):
            return
        if checks and all(r.get('status') == 'completed' for r in checks):
            raise RuntimeError('Source commit has no successful full verification')
        print('Waiting for full verification of the release source commit', flush=True)
        time.sleep(15)
    raise RuntimeError('Full verification did not succeed within 20 minutes')


def main():
    ref = os.environ['GITHUB_REF_NAME']
    if not re.fullmatch(r'publish-release-[A-Za-z0-9-]+', ref):
        raise RuntimeError('An explicit temporary publish tag is required')
    repo = os.environ['GITHUB_REPOSITORY']
    # Checkout can replace an annotated tag ref with its peeled commit locally.
    tag_ref = api('GET', f'/repos/{repo}/git/ref/tags/{ref}')['object']
    if tag_ref['type'] != 'tag':
        raise RuntimeError('Publish input must be an annotated tag')
    tag_input = api('GET', f'/repos/{repo}/git/tags/' + tag_ref['sha'])
    manifest = json.loads(tag_input['message'])
    head = git('rev-parse', 'HEAD')
    if manifest['source_commit'] != head or tag_input['object']['sha'] != head:
        raise RuntimeError('Publish input does not match checked-out source')
    build = (ROOT / 'app/build.gradle').read_text()
    version = re.search(r"versionName\s+'([^']+)'", build).group(1)
    tag = 'v' + version
    if manifest['tag'] != tag or git('rev-parse', tag + '^{commit}') != head:
        raise RuntimeError('Release version tag does not match the source commit')
    expected_apk = f'RemoteGPT-{version}-Android-universal-debug.apk'
    expected_zip = f'RemoteGPT-{version}-Android-universal-debug-with-license.zip'
    required = {expected_apk, expected_apk + '.sha256', expected_zip,
                expected_zip + '.sha256', 'LICENSE.txt'}
    assets = manifest['assets']
    if len(assets) != len(required) or {a['name'] for a in assets} != required:
        raise RuntimeError('Unexpected or incomplete release asset set')
    folder = ROOT / 'release-assets'
    folder.mkdir()
    for asset in assets:
        url = urllib.parse.urlsplit(asset['url'])
        if url.scheme != 'https' or not url.hostname or url.username or url.password:
            raise RuntimeError('Asset input must be a public HTTPS URL without credentials')
        if not re.fullmatch(r'[0-9a-f]{64}', asset['sha256']):
            raise RuntimeError('Each asset requires an explicit SHA-256')
        data = download_asset(asset['url'])
        if len(data) > 12 * 1024 * 1024 or hashlib.sha256(data).hexdigest() != asset['sha256']:
            raise RuntimeError('Asset size or checksum validation failed: ' + asset['name'])
        (folder / asset['name']).write_bytes(data)
    license_data = (ROOT / 'LICENSE').read_bytes()
    if (folder / 'LICENSE.txt').read_bytes() != license_data:
        raise RuntimeError('Release license does not match source')
    apk_digest = hashlib.sha256((folder / expected_apk).read_bytes()).hexdigest()
    for filename in [expected_apk, expected_zip]:
        checksum = (folder / (filename + '.sha256')).read_text().split()
        if checksum != [hashlib.sha256((folder / filename).read_bytes()).hexdigest(), filename]:
            raise RuntimeError('Checksum sidecar does not match asset')
    with zipfile.ZipFile(folder / expected_zip) as archive:
        if archive.testzip() or archive.read('LICENSE') != license_data:
            raise RuntimeError('Install bundle is invalid')
        if hashlib.sha256(archive.read(expected_apk)).hexdigest() != apk_digest:
            raise RuntimeError('Install bundle contains a different APK')
    notes = (ROOT / f'docs/releases/{version}.md').read_text()
    if apk_digest not in notes:
        raise RuntimeError('Release notes do not identify the verified APK checksum')
    wait_for_verification(repo, head)
    existing = [r for r in api('GET', f'/repos/{repo}/releases') if r['tag_name'] == tag]
    if existing and not existing[0]['draft']:
        raise RuntimeError('Release is already public; refusing to overwrite it')
    release = existing[0] if existing else api('POST', f'/repos/{repo}/releases', json.dumps({
        'tag_name': tag, 'target_commitish': head, 'name': 'RemoteGPT ' + version,
        'body': notes, 'draft': True, 'prerelease': False,
    }).encode())
    release_id = release['id']
    present = {a['name']: a for a in api('GET', f'/repos/{repo}/releases/{release_id}/assets')}
    upload_url = release['upload_url'].split('{')[0]
    for asset in assets:
        name = asset['name']
        data = (folder / name).read_bytes()
        if name in present:
            if present[name].get('digest') != 'sha256:' + asset['sha256']:
                raise RuntimeError('Existing draft asset differs; refusing to overwrite: ' + name)
            continue
        result = api('POST', upload_url + '?name=' + urllib.parse.quote(name), data,
                     'application/vnd.android.package-archive' if name.endswith('.apk')
                     else 'application/zip' if name.endswith('.zip') else 'text/plain')
        if result['size'] != len(data) or result.get('digest') != 'sha256:' + asset['sha256']:
            raise RuntimeError('GitHub uploaded asset verification failed')
    published = api('PATCH', f'/repos/{repo}/releases/{release_id}', json.dumps({
        'body': notes, 'draft': False, 'make_latest': 'true',
    }).encode())
    print('Published verified APK: ' + published['html_url'])


if __name__ == '__main__':
    main()
