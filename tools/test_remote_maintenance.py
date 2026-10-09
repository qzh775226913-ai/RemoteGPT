"""Updater tests never change the real Codex installation or access credentials."""
import hashlib
import importlib.util
import io
import os
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import time
import unittest
from unittest.mock import call, patch

SOURCE = Path(__file__).resolve().parents[1] / 'app/src/main/assets/remote_maintenance.py'
spec = importlib.util.spec_from_file_location('maintenance', SOURCE)
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class MaintenanceTest(unittest.TestCase):
    def test_parses_actual_version_not_random_output(self):
        self.assertEqual('0.156.1', m.version('codex-cli 0.156.1'))
        self.assertEqual('0.157.0-alpha.2', m.version('codex 0.157.0-alpha.2'))
        with self.assertRaises(RuntimeError):
            m.version('node 22.0.0')

    def test_custom_shell_is_not_executed(self):
        with patch.object(m, 'run') as run:
            for command in ['sh -c "codex app-server"', 'other app-server', 'PATH=$PATH codex app-server']:
                with self.assertRaises(RuntimeError):
                    m.resolve(command)
            run.assert_not_called()

    def test_detects_npm_and_the_exact_prefix(self):
        with tempfile.TemporaryDirectory() as folder:
            prefix = Path(folder)
            real = prefix / 'lib/node_modules/@openai/codex/bin/codex.js'
            real.parent.mkdir(parents=True); real.write_text('#!/usr/bin/env node\n')
            link = prefix / 'bin/codex'; link.parent.mkdir(); link.symlink_to(real)
            with patch.object(m.shutil, 'which', side_effect=lambda x, **kw: str(link) if x == 'codex' else '/bin/npm'), patch.object(m, 'run', return_value='codex-cli 0.100.0'):
                info, env = m.installation('codex app-server')
            self.assertEqual('npm', info['manager'])
            self.assertEqual(str(prefix), info['prefix'])
            self.assertEqual(str(real), info['path'])

    def test_check_network_failure_keeps_installed_version(self):
        with patch.object(m, 'installation', return_value=({'installed': '0.100.0', 'manager': 'npm'}, {})), patch.object(m, 'latest', side_effect=OSError('offline')):
            info = m.check('codex app-server')
        self.assertEqual('0.100.0', info['installed'])
        self.assertIn('checkError', info)
        self.assertNotIn('latest', info)

    def npm_info(self):
        return dict(installed='0.100.0', manager='npm', path='/test/codex.js', executable='/test/codex', tool='/test/npm', prefix='/test')

    def test_update_installs_only_confirmed_version_and_prefix(self):
        with patch.object(m, 'installation', return_value=(self.npm_info(), {})), patch.object(m, 'latest', return_value=('0.101.0', None)), patch.object(m, 'run', side_effect=['installed', 'codex-cli 0.101.0']) as run:
            result = m.update('codex app-server', '0.101.0', '/test/codex.js')
        self.assertEqual('0.101.0', result['installed'])
        self.assertEqual(['/test/npm', 'install', '--global', '--prefix', '/test', '--registry=https://registry.npmjs.org', '@openai/codex@0.101.0'], run.call_args_list[0].args[0])

    def test_changed_installation_cannot_be_updated_by_old_confirmation(self):
        with patch.object(m, 'installation', return_value=(self.npm_info(), {})), patch.object(m, 'run') as run:
            with self.assertRaises(RuntimeError):
                m.update('codex app-server', '0.101.0', '/other/codex')
            run.assert_not_called()

    def test_changed_release_requires_confirmation_again(self):
        with patch.object(m, 'installation', return_value=(self.npm_info(), {})), patch.object(m, 'latest', return_value=('0.102.0', None)), patch.object(m, 'run') as run:
            with self.assertRaises(RuntimeError):
                m.update('codex app-server', '0.101.0', '/test/codex.js')
            run.assert_not_called()

    def test_changed_installed_version_cannot_be_downgraded_by_stale_prompt(self):
        info = self.npm_info(); info['installed'] = '0.102.0'
        with patch.object(m, 'installation', return_value=(info, {})), patch.object(m, 'run') as run:
            with self.assertRaises(RuntimeError):
                m.update('codex app-server', '0.101.0', '/test/codex.js', '0.100.0')
            run.assert_not_called()

    def test_install_failure_never_claims_success(self):
        with patch.object(m, 'installation', return_value=(self.npm_info(), {})), patch.object(m, 'latest', return_value=('0.101.0', None)), patch.object(m, 'run', side_effect=RuntimeError('permission denied')):
            with self.assertRaises(RuntimeError):
                m.update('codex app-server', '0.101.0', '/test/codex.js')

    def test_installed_version_is_verified(self):
        with patch.object(m, 'installation', return_value=(self.npm_info(), {})), patch.object(m, 'latest', return_value=('0.101.0', None)), patch.object(m, 'run', side_effect=['done', 'codex-cli 0.100.0']):
            with self.assertRaises(RuntimeError):
                m.update('codex app-server', '0.101.0', '/test/codex.js')

    def test_binary_digest_failure_preserves_old_file(self):
        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder) / 'codex'; target.write_bytes(b'old')
            release = {'assets': [{'name': 'codex-x86_64-unknown-linux-musl.tar.gz',
                'digest': 'sha256:' + '0' * 64, 'browser_download_url': 'https://github.com/openai/codex/releases/download/test/asset'}]}
            with patch.object(m.platform, 'machine', return_value='x86_64'), patch.object(m.platform, 'system', return_value='Linux'), patch.object(m, 'fetch', return_value=b'wrong'):
                with self.assertRaises(RuntimeError):
                    m.replace_binary({'path': str(target)}, release, '0.101.0')
            self.assertEqual(b'old', target.read_bytes())

    def test_binary_replacement_is_verified_and_atomic(self):
        buffer = io.BytesIO()
        name = 'codex-x86_64-unknown-linux-musl'
        with tarfile.open(fileobj=buffer, mode='w:gz') as archive:
            entry = tarfile.TarInfo(name); entry.size = 3
            archive.addfile(entry, io.BytesIO(b'new'))
        data = buffer.getvalue()
        release = {'assets': [{'name': name + '.tar.gz', 'digest': 'sha256:' + hashlib.sha256(data).hexdigest(),
            'browser_download_url': 'https://github.com/openai/codex/releases/download/test/asset'}]}
        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder) / 'codex'; target.write_bytes(b'old')
            with patch.object(m.platform, 'machine', return_value='x86_64'), patch.object(m.platform, 'system', return_value='Linux'), patch.object(m, 'fetch', return_value=data), patch.object(m, 'run', return_value='codex-cli 0.101.0'):
                m.replace_binary({'path': str(target)}, release, '0.101.0')
            self.assertEqual(b'new', target.read_bytes())

    def test_named_live_session_folder_is_selected(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(m.Path, 'home', return_value=Path(folder)):
            session = Path(folder) / '.remote-gpt' / ('a' * 24)
            session.mkdir(parents=True)
            with patch.object(m, 'live_host', side_effect=lambda value: value == session):
                self.assertEqual(session, m.hosted_folder('codex app-server', ['profile'], 'a' * 24))

    def test_invalid_session_folder_is_never_used(self):
        with patch.object(m, 'live_host') as live:
            with self.assertRaises(RuntimeError):
                m.hosted_folder('codex app-server', ['profile'], '../../other')
            live.assert_not_called()

    def test_force_restart_requires_backend_owned_by_daemon(self):
        with tempfile.TemporaryDirectory() as folder:
            session = Path(folder); (session / 'daemon.pid').write_text('101')
            (session / 'server.pid').write_text('202')
            with patch.object(m, 'hosted_folder', return_value=session), \
                    patch.object(m, 'parent_pid', return_value=999), \
                    patch.object(m, 'stop_group') as stop:
                with self.assertRaises(RuntimeError):
                    m.force_restart('codex app-server', ['profile'])
                stop.assert_not_called()

    def test_confirmed_restart_stops_backend_and_daemon(self):
        with tempfile.TemporaryDirectory() as folder:
            session = Path(folder); (session / 'daemon.pid').write_text('101')
            (session / 'server.pid').write_text('202')
            with patch.object(m, 'hosted_folder', return_value=session), \
                    patch.object(m, 'parent_pid', return_value=101), \
                    patch.object(m, 'live_host', return_value=False), \
                    patch.object(m, 'stop_group') as stop:
                result = m.force_restart('codex app-server', ['profile'])
            self.assertTrue(result['stopped'])
            self.assertEqual([call(202, m.signal.SIGTERM), call(101, m.signal.SIGTERM)], stop.call_args_list)

    def test_confirmed_restart_terminates_isolated_real_process_groups(self):
        with tempfile.TemporaryDirectory() as folder:
            session = Path(folder)
            daemon_code = (
                "import os,pathlib,subprocess,sys,time; "
                "child=subprocess.Popen([sys.executable,'-c','import time; time.sleep(60)'],start_new_session=True); "
                "pathlib.Path(sys.argv[-1],'server.pid').write_text(str(child.pid)); "
                "pathlib.Path(sys.argv[-1],'daemon.pid').write_text(str(os.getpid())); "
                "pathlib.Path(sys.argv[-1],'socket').touch(); time.sleep(60)"
            )
            daemon = subprocess.Popen([sys.executable, '-c', daemon_code, '--daemon', str(session)],
                                      start_new_session=True)
            try:
                deadline = time.monotonic() + 5
                while not (session / 'server.pid').exists() and time.monotonic() < deadline:
                    time.sleep(.02)
                self.assertTrue(m.live_host(session))
                with patch.object(m, 'hosted_folder', return_value=session):
                    self.assertTrue(m.force_restart('codex app-server', ['profile'])['stopped'])
                daemon.wait(timeout=5)
            finally:
                if daemon.poll() is None:
                    os.killpg(daemon.pid, m.signal.SIGKILL)
                    daemon.wait(timeout=5)


if __name__ == '__main__':
    unittest.main()
