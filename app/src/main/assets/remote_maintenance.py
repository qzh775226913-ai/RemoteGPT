"""Version checks and explicit updates over an authenticated SSH exec channel.

No auth files are read. Checks never install anything. Custom shell launchers are
not evaluated for discovery: only a directly identifiable Codex executable is used.
"""
import hashlib
import io
import json
import os
import platform
import re
import shlex
import shutil
import signal
import subprocess
import sys
import tarfile
import tempfile
import time
import urllib.request
from pathlib import Path

VERSION = re.compile(r'\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?')


def run(args, env=None, timeout=20):
    with tempfile.TemporaryFile() as output:
        process = subprocess.Popen(args, stdin=subprocess.DEVNULL, stdout=output,
                                   stderr=subprocess.STDOUT, env=env, start_new_session=True)
        try:
            code = process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait()
            raise RuntimeError('远端命令超时，请重新检查版本和安装状态')
        if code:
            raise RuntimeError('远端命令失败（退出码 %s），请检查网络、安装目录权限或包管理器' % code)
        output.seek(0)
        return output.read(65536).decode('utf-8', 'replace').strip()


def fetch(url, limit=4 * 1024 * 1024):
    request = urllib.request.Request(url, headers={'User-Agent': 'RemoteGPT-version-check', 'Accept': 'application/json'})
    with urllib.request.urlopen(request, timeout=20) as response:
        data = response.read(limit + 1)
    if len(data) > limit:
        raise RuntimeError('远端发布信息超过大小限制')
    return data


def metadata(url):
    return json.loads(fetch(url))


def version(text):
    match = re.search(r'codex(?:-cli)?\s+(' + VERSION.pattern + ')', text)
    if not match:
        raise RuntimeError('无法识别 Codex 版本')
    return match.group(1)


def resolve(command):
    tokens = shlex.split(command)
    env = os.environ.copy()
    while tokens and re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*=.*', tokens[0], re.S):
        key, value = tokens.pop(0).split('=', 1)
        # Shell expansion would change the meaning; leave these launchers manual.
        if '$' in value or '`' in value:
            raise RuntimeError('自定义启动环境无法自动识别，请使用 Codex 绝对路径')
        env[key] = value
    if not tokens or 'app-server' not in tokens or any(t in (';', '&&', '||', '|', '&') for t in tokens):
        raise RuntimeError('自定义启动命令无法自动识别，请使用 codex app-server 或绝对路径')
    executable = os.path.expanduser(tokens[0])
    if Path(executable).name not in ('codex', 'codex.js'):
        raise RuntimeError('此启动命令不是可识别的 Codex 安装，请在远端手动管理')
    located = shutil.which(executable, path=env.get('PATH'))
    if not located:
        raise RuntimeError('找不到启动命令中的 Codex，请检查远端 PATH')
    path = Path(located).resolve(strict=True)
    env['PATH'] = str(Path(located).parent) + os.pathsep + env.get('PATH', '')
    return str(Path(located).absolute()), path, env


def installation(command):
    executable, path, env = resolve(command)
    current = version(run([executable, '--version'], env))
    info = dict(installed=current, executable=executable, path=str(path), manager='', reason='')
    for parent in path.parents:
        if parent.name == 'codex' and parent.parent.name == '@openai' and parent.parent.parent.name == 'node_modules':
            modules = parent.parent.parent
            if modules.parent.name == 'lib':
                prefix = modules.parent.parent
                npm = shutil.which('npm', path=env['PATH'])
                if npm:
                    info.update(manager='npm', prefix=str(prefix), tool=npm)
                    return info, env
            info['reason'] = '此 Codex 不是全局 npm 安装，请在远端手动更新'
            return info, env
    if 'Caskroom' in path.parts or 'Cellar' in path.parts:
        brew = shutil.which('brew', path=env['PATH'])
        if brew:
            info.update(manager='brew', tool=brew)
            return info, env
    with open(path, 'rb') as binary:
        magic = binary.read(4)
    if magic == b'\x7fELF' or magic in (b'\xcf\xfa\xed\xfe', b'\xfe\xed\xfa\xcf', b'\xca\xfe\xba\xbe'):
        info['manager'] = 'binary'
    else:
        info['reason'] = '此 Codex 使用自定义包装脚本，请在远端手动更新'
    return info, env


def latest(info):
    if info['manager'] == 'brew':
        return metadata('https://formulae.brew.sh/api/cask/codex.json')['version'], None
    if info['manager'] == 'binary':
        release = metadata('https://api.github.com/repos/openai/codex/releases/latest')
        tag = release['tag_name']
        if release.get('prerelease') or release.get('draft') or not tag.startswith('rust-v'):
            raise RuntimeError('无法识别最新稳定版')
        return tag[6:], release
    return metadata('https://registry.npmjs.org/@openai%2Fcodex/latest')['version'], None


def check(command):
    info, env = installation(command)
    try:
        newest, _ = latest(info)
        if not VERSION.fullmatch(newest):
            raise RuntimeError('发布版本号格式无效')
        info['latest'] = newest
    except Exception:
        info['checkError'] = '无法获取最新版，请检查远端网络后重试'
    return info


def replace_binary(info, release, expected):
    architecture = {'x86_64': 'x86_64', 'aarch64': 'aarch64', 'arm64': 'aarch64'}.get(platform.machine())
    system = {'Linux': 'unknown-linux-musl', 'Darwin': 'apple-darwin'}.get(platform.system())
    if not architecture or not system:
        raise RuntimeError('此系统架构不支持自动更新')
    name = 'codex-' + architecture + '-' + system
    asset = next((a for a in release['assets'] if a['name'] == name + '.tar.gz'), None)
    if not asset or not re.fullmatch(r'sha256:[0-9a-f]{64}', asset.get('digest', '') or ''):
        raise RuntimeError('发布文件缺少 SHA-256 校验信息，未修改现有安装')
    url = asset['browser_download_url']
    if not url.startswith('https://github.com/openai/codex/releases/download/'):
        raise RuntimeError('发布文件地址无效')
    data = fetch(url, 200 * 1024 * 1024)
    if 'sha256:' + hashlib.sha256(data).hexdigest() != asset['digest']:
        raise RuntimeError('下载文件校验失败，未修改现有安装')
    target = Path(info['path'])
    with tempfile.TemporaryDirectory(prefix='.codex-update-', dir=str(target.parent)) as temp:
        candidate = Path(temp) / 'codex'
        with tarfile.open(fileobj=io.BytesIO(data), mode='r:gz') as archive:
            member = next((m for m in archive.getmembers() if Path(m.name).name == name and m.isfile()), None)
            if not member or member.size > 300 * 1024 * 1024:
                raise RuntimeError('发布文件内容无效')
            with archive.extractfile(member) as source, open(candidate, 'wb') as dest:
                shutil.copyfileobj(source, dest)
        candidate.chmod(0o755)
        if version(run([str(candidate), '--version'])) != expected:
            raise RuntimeError('下载版本与确认版本不符，未修改现有安装')
        os.replace(candidate, target)


def update(command, expected, expected_path, expected_installed=None):
    if not VERSION.fullmatch(expected):
        raise RuntimeError('待更新版本无效，请重新检查')
    info, env = installation(command)
    if info['path'] != expected_path:
        raise RuntimeError('远端安装路径已变化，请重新检查后确认更新')
    if expected_installed is not None and info['installed'] != expected_installed:
        raise RuntimeError('远端已安装版本已变化，请重新检查后确认更新')
    if not info['manager']:
        raise RuntimeError(info['reason'])
    newest, release = latest(info)
    if newest != expected:
        raise RuntimeError('最新版已变化，请重新检查后确认更新')
    if info['manager'] == 'npm':
        run([info['tool'], 'install', '--global', '--prefix', info['prefix'],
             '--registry=https://registry.npmjs.org', '@openai/codex@' + expected], env, timeout=300)
    elif info['manager'] == 'brew':
        run([info['tool'], 'update'], env, timeout=180)
        run([info['tool'], 'upgrade', '--cask', 'codex'], env, timeout=300)
    else:
        replace_binary(info, release, expected)
    installed = version(run([info['executable'], '--version'], env))
    if installed != expected:
        raise RuntimeError('安装后版本为 ' + installed + '，与目标不符，请重新检查')
    info.update(installed=installed, latest=newest)
    return info


def process_command(pid):
    proc = Path('/proc') / str(pid) / 'cmdline'
    if proc.exists():
        return proc.read_bytes().replace(b'\0', b' ').decode('utf-8', 'replace')
    try:
        return subprocess.check_output(['ps', '-o', 'command=', '-p', str(pid)],
                                       timeout=5, text=True).strip()
    except (OSError, subprocess.SubprocessError):
        return ''


def live_host(folder):
    try:
        daemon = int((folder / 'daemon.pid').read_text())
        os.kill(daemon, 0)
        command = process_command(daemon)
        return '--daemon' in command and str(folder) in command and (folder / 'socket').exists()
    except (OSError, ValueError):
        return False


def hosted_folder(command, identities, session_name=''):
    root = (Path.home() / '.remote-gpt').resolve()
    if session_name:
        if not re.fullmatch(r'[0-9a-f]{24}', session_name):
            raise RuntimeError('远端托管标识无效，未执行重启')
        candidate = root / session_name
        if live_host(candidate):
            return candidate
    for identity in identities:
        if not isinstance(identity, str) or not identity:
            continue
        key = hashlib.sha256((identity + '\0' + command).encode()).hexdigest()[:24]
        candidate = root / key
        if live_host(candidate):
            return candidate
    key = hashlib.sha256(('shared-v2\0' + command).encode()).hexdigest()[:24]
    candidate = root / key
    if live_host(candidate):
        return candidate
    raise RuntimeError('没有找到当前远端托管进程，请重新连接后再试')


def parent_pid(pid):
    stat = Path('/proc') / str(pid) / 'stat'
    if stat.exists():
        # The command name may contain spaces and parentheses; fields start after
        # the final closing parenthesis. PPID is the second remaining field.
        tail = stat.read_text().rsplit(')', 1)[1].strip().split()
        return int(tail[1])
    try:
        return int(subprocess.check_output(['ps', '-o', 'ppid=', '-p', str(pid)],
                                           timeout=5, text=True).strip())
    except (OSError, ValueError, subprocess.SubprocessError):
        return -1


def stop_group(pid, sig):
    try:
        os.killpg(os.getpgid(pid), sig)
    except ProcessLookupError:
        pass


def force_restart(command, identities, session_name=''):
    folder = hosted_folder(command, identities, session_name)
    daemon = int((folder / 'daemon.pid').read_text())
    server = int((folder / 'server.pid').read_text())
    if parent_pid(server) != daemon:
        raise RuntimeError('远端进程身份校验失败，未执行重启')
    # The user has explicitly accepted that active conversations will stop.
    # Stop the backend first so it cannot outlive its manager, then the daemon.
    stop_group(server, signal.SIGTERM)
    stop_group(daemon, signal.SIGTERM)
    deadline = time.monotonic() + 4
    while time.monotonic() < deadline:
        if not live_host(folder):
            return {'stopped': True, 'sessionFolder': folder.name}
        time.sleep(.1)
    stop_group(server, signal.SIGKILL)
    stop_group(daemon, signal.SIGKILL)
    return {'stopped': True, 'sessionFolder': folder.name}


def main():
    request = json.loads(sys.argv[1])
    try:
        if request['action'] == 'check':
            result = check(request['command'])
        elif request['action'] == 'update':
            # Serialize updates for this remote account across SSH connections.
            import fcntl
            folder = Path.home() / '.remote-gpt'
            folder.mkdir(mode=0o700, exist_ok=True)
            with open(folder / 'update.lock', 'a') as lock:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                result = update(request['command'], request['target'], request['path'], request['installed'])
        elif request['action'] == 'restart':
            import fcntl
            folder = Path.home() / '.remote-gpt'
            folder.mkdir(mode=0o700, exist_ok=True)
            with open(folder / 'restart.lock', 'a') as lock:
                try:
                    fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                except BlockingIOError:
                    raise RuntimeError('另一项远端重启正在执行，请稍候重连')
                result = force_restart(request['command'], request.get('identities', []),
                                       request.get('sessionFolder', ''))
        else:
            raise RuntimeError('未知操作')
        print(json.dumps(result, ensure_ascii=False))
    except Exception as error:
        message = str(error) if isinstance(error, RuntimeError) else '版本操作失败，请检查远端网络、Python 3 或安装权限后重试'
        print(json.dumps({'error': message}, ensure_ascii=False))


if __name__ == '__main__':
    main()
