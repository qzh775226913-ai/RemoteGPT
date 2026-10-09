"""Integration tests using a fake app-server, never real Codex credentials."""
import json
import hashlib
import importlib.util
import os
from pathlib import Path
import queue
import shlex
import signal
import subprocess
import sys
import tempfile
import threading
import time
import unittest

HOST = Path(__file__).resolve().parents[1] / 'app/src/main/assets/remote_session.py'
FAKE = r'''
import json, os, sys, threading, time
lock = threading.Lock()
def send(x):
    with lock:
        print(json.dumps(x), flush=True)
def event(method, **params):
    send(dict(method=method, params=dict(threadId='thread-1', **params)))
def work():
    event('turn/started', turn=dict(id='turn-1', status='inProgress'))
    event('item/started', item=dict(id='message', type='agentMessage', text=''))
    time.sleep(.3)
    for i in range(3000):
        event('item/agentMessage/delta', itemId='message', delta='字')
    open(os.path.join(os.environ['HOME'], 'continued'), 'w').write('yes')
    send(dict(id='approval-1', method='item/commandExecution/requestApproval',
              params=dict(threadId='thread-1', turnId='turn-1', command='test command', itemId='cmd')))
for line in sys.stdin:
    r = json.loads(line)
    m = r.get('method')
    if not m:
        open(os.path.join(os.environ['HOME'], 'answers'), 'a').write(json.dumps(r)+'\n')
        send(dict(method='serverRequest/resolved', params=dict(requestId=r['id'])))
        event('turn/completed', turn=dict(id='turn-1', status='completed'))
    elif m == 'initialize':
        send(dict(id=r['id'], result=dict(server='fake')))
    elif m == 'thread/start':
        send(dict(id=r['id'], result=dict(model='fake', thread=dict(id=r.get('params', {}).get('threadId', 'thread-1'), status=dict(type='idle'), turns=[]))))
    elif m == 'thread/unsubscribe' and r.get('params', {}).get('threadId') == 'rejected':
        send(dict(id=r['id'], error=dict(code=-32601, message='unsubscribe unsupported')))
    elif m == 'test/crash':
        print('{broken json', flush=True)
        time.sleep(30)
    elif m == 'turn/start':
        if r.get('params', {}).get('serviceTier') == 'rejected':
            send(dict(id=r['id'], error=dict(code=-32602, message='unsupported tier')))
            continue
        send(dict(id=r['id'], result=dict(turn=dict(id='turn-1'))))
        threading.Thread(target=work).start()
    elif m == 'test/events':
        for value in r['params']['events']:
            send(value)
        send(dict(id=r['id'], result={}))
    elif m == 'echo':
        time.sleep(r['params'].get('delay', 0))
        send(dict(id=r['id'], result=r['params']))
    elif m == 'initialized':
        pass
    else:
        send(dict(id=r['id'], result={}))
'''


class Client:
    def __init__(self, env, command, identities='profile-1'):
        self.proc = subprocess.Popen([sys.executable, str(HOST), identities, command],
                                     stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=env)
        self.events = queue.Queue()
        def read():
            for line in self.proc.stdout:
                self.events.put(json.loads(line))
        threading.Thread(target=read, daemon=True).start()

    def send(self, value):
        self.proc.stdin.write((json.dumps(value)+'\n').encode())
        self.proc.stdin.flush()

    def next(self):
        return self.events.get(timeout=15)

    def request(self, ident, method, **params):
        self.send(dict(id=ident, method=method, params=params))
        while True:
            value = self.next()
            if value.get('id') == ident and 'method' not in value:
                if value.get('result', {}).get('remoteReplay'):
                    self.send(dict(method='remote/replay', params={'threadId': params['threadId']}))
                return value

    def kill(self):
        self.proc.kill()
        self.proc.wait(timeout=5)
        for stream in (self.proc.stdin, self.proc.stdout, self.proc.stderr):
            stream.close()


class PersistentSessionTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='remote-gpt-test-')
        self.home = Path(self.tmp.name)
        fake = self.home / 'fake.py'
        fake.write_text(FAKE)
        self.env = dict(os.environ, HOME=str(self.home))
        self.command = shlex.quote(sys.executable) + ' -u ' + shlex.quote(str(fake))
        self.clients = []

    def client(self, identities='profile-1'):
        c = Client(self.env, self.command, identities)
        self.clients.append(c)
        c.request(1, 'initialize')
        c.send(dict(method='initialized', params={}))
        return c

    def tearDown(self):
        for c in self.clients:
            if c.proc.poll() is None:
                c.kill()
        for path in self.home.glob('.remote-gpt/*/*.pid'):
            try:
                os.killpg(int(path.read_text()), signal.SIGKILL)
            except ProcessLookupError:
                pass
        self.tmp.cleanup()

    def test_safe_restart_stops_only_idle_host(self):
        c = self.client()
        self.assertTrue(c.request(2, 'remote/session')['result']['safeRestart'])
        result = c.request(3, 'remote/shutdownIfIdle')
        self.assertTrue(result['result']['shutdown'])
        c.proc.wait(timeout=8)
        for stream in (c.proc.stdin, c.proc.stdout, c.proc.stderr):
            stream.close()
        replacement = self.client()
        self.assertTrue(replacement.request(4, 'remote/session')['result']['safeRestart'])

    def test_safe_restart_refuses_running_or_approval_waiting_tasks(self):
        c = self.client()
        c.request(2, 'thread/start')
        c.request(3, 'turn/start', threadId='thread-1')
        self.assertIn('error', c.request(4, 'remote/shutdownIfIdle'))
        while True:
            event = c.next()
            if event.get('id') == 'approval-1':
                break
        self.assertIn('error', c.request(5, 'remote/shutdownIfIdle'))
        self.assertIsNone(c.proc.poll())

    def test_idle_unsubscribe_removes_hosted_baseline(self):
        c = self.client()
        c.request(2, 'thread/start')
        self.assertIn('thread-1', c.request(3, 'remote/session')['result']['threads'])
        c.request(4, 'thread/unsubscribe', threadId='thread-1')
        self.assertNotIn('thread-1', c.request(5, 'remote/session')['result']['threads'])

    def test_successful_turn_overrides_update_reconnect_configuration(self):
        c = self.client()
        c.request(2, 'thread/start')
        c.request(3, 'turn/start', threadId='thread-1', serviceTier='priority', model='new-model', effort='high')
        self.assertIn('error', c.request(7, 'turn/start', threadId='thread-1', serviceTier='rejected'))
        c.kill()
        c = self.client()
        result = c.request(4, 'thread/resume', threadId='thread-1')['result']
        self.assertTrue(result['remoteConfigCurrent'])
        self.assertEqual('priority', result['serviceTier'])
        self.assertEqual('new-model', result['model'])
        self.assertEqual('high', result['reasoningEffort'])
        c.request(5, 'turn/start', threadId='thread-1', serviceTier='default')
        c.kill()
        c = self.client()
        self.assertEqual('default', c.request(6, 'thread/resume', threadId='thread-1')['result']['serviceTier'])

    def test_network_alias_profiles_share_the_same_daemon(self):
        wifi = self.client(json.dumps(['wifi-profile', 'tailscale-profile']))
        wifi.request(2, 'thread/start', threadId='same-thread')

        tailscale = self.client(json.dumps(['tailscale-profile', 'wifi-profile']))
        session = tailscale.request(2, 'remote/session')['result']
        self.assertIn('same-thread', session['threads'])
        resumed = tailscale.request(3, 'thread/resume', threadId='same-thread')
        self.assertTrue(resumed['result']['remoteReplay'])

    def test_live_legacy_daemon_wins_over_failed_shared_migration(self):
        spec = importlib.util.spec_from_file_location('remote_session_under_test', HOST)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        identity = 'old-wifi-profile'
        legacy_key = hashlib.sha256((identity + '\0' + self.command).encode()).hexdigest()[:24]
        shared_key = hashlib.sha256(('shared-v2\0' + self.command).encode()).hexdigest()[:24]
        root = self.home / '.remote-gpt'
        for key in (legacy_key, shared_key):
            folder = root / key
            folder.mkdir(parents=True)
            (folder / 'daemon.pid').write_text(str(os.getpid()))
            (folder / 'socket').touch()
        old_home = os.environ.get('HOME')
        os.environ['HOME'] = str(self.home)
        try:
            selected = module.session_folder(json.dumps([identity]), self.command)
        finally:
            if old_home is None:
                os.environ.pop('HOME', None)
            else:
                os.environ['HOME'] = old_home
            for key in (legacy_key, shared_key):
                folder = root / key
                for child in folder.iterdir():
                    child.unlink()
                folder.rmdir()
        self.assertEqual(root / legacy_key, selected)

    def test_rejected_unsubscribe_keeps_writer_recoverable_after_reconnect(self):
        c = self.client()
        c.request(2, 'thread/start', threadId='rejected')
        response = c.request(3, 'thread/unsubscribe', threadId='rejected')
        self.assertEqual(-32601, response['error']['code'])
        self.assertIn('rejected', c.request(4, 'remote/session')['result']['threads'])
        c.kill()

        c = self.client()
        resumed = c.request(2, 'thread/resume', threadId='rejected')
        self.assertTrue(resumed['result']['remoteReplay'])
        self.assertEqual('rejected', resumed['result']['thread']['id'])

    def test_killed_phone_replays_stream_and_pending_approval(self):
        c = self.client()
        c.request(2, 'thread/start')
        c.request(3, 'turn/start')
        c.kill()
        deadline = time.monotonic() + 15
        while not (self.home / 'continued').exists() and time.monotonic() < deadline:
            time.sleep(.02)
        self.assertTrue((self.home / 'continued').exists(), 'Remote must advance with no phone process')
        c = self.client()
        self.assertEqual('thread-1', c.request(2, 'remote/session')['result']['lastThread'])
        self.assertEqual('thread-1', c.request(3, 'thread/resume', threadId='thread-1')['result']['thread']['id'])
        events = []
        while True:
            event = c.next()
            events.append(event)
            if (any(e.get('method') == 'remote/replayCompleted' for e in events)
                    and any(e.get('id') == 'approval-1' for e in events)):
                break
        self.assertEqual(3000, sum(e.get('method') == 'item/agentMessage/delta' for e in events))
        self.assertEqual(1, sum(e.get('id') == 'approval-1' for e in events))
        self.assertFalse((self.home / 'answers').exists(), 'Detaching must never approve a request')
        c.send(dict(id='approval-1', result=dict(decision='accept')))
        c.send(dict(id='approval-1', result=dict(decision='accept')))
        while c.next().get('method') != 'turn/completed':
            pass
        self.assertEqual(1, len((self.home / 'answers').read_text().splitlines()))
        c.kill()
        c = self.client()
        c.request(2, 'thread/resume', threadId='thread-1')
        restored = []
        while True:
            event = c.next()
            restored.append(event)
            if event.get('method') == 'remote/replayCompleted':
                break
        self.assertFalse(any(e.get('id') == 'approval-1' for e in restored))
        self.assertTrue(any(e.get('method') == 'turn/completed' for e in restored))

    def test_two_threads_recover_and_other_thread_events_survive_replay_barrier(self):
        c = self.client()
        c.request(2, 'thread/start', threadId='A')
        c.request(3, 'thread/start', threadId='B')
        def delta(thread, text):
            return dict(method='item/agentMessage/delta', params=dict(threadId=thread, itemId='same', delta=text))
        c.request(4, 'test/events', events=[delta('A', 'a1'), delta('B', 'b1')])
        c.kill()
        c = self.client()
        session = c.request(2, 'remote/session')['result']
        self.assertEqual({'A', 'B'}, set(session['threads']))
        # Deliberately hold the baseline acknowledgement while B continues.
        c.send(dict(id=3, method='thread/resume', params=dict(threadId='A')))
        baseline = c.next()
        self.assertTrue(baseline['result']['remoteReplay'])
        c.request(4, 'test/events', events=[delta('A', 'a2'), delta('B', 'b2')])
        c.send(dict(method='remote/replay', params=dict(threadId='A')))
        events = []
        while True:
            value = c.next()
            if value.get('method') == 'remote/replayCompleted': break
            events.append(value)
        self.assertEqual(['a1', 'a2', 'b2'], [e['params']['delta'] for e in events if e.get('method') == 'item/agentMessage/delta'])
        c.request(5, 'thread/resume', threadId='B')
        replay = []
        while True:
            value = c.next()
            if value.get('method') == 'remote/replayCompleted': break
            replay.append(value)
        self.assertEqual(['b1', 'b2'], [e['params']['delta'] for e in replay if e.get('method') == 'item/agentMessage/delta'])

    def test_request_ids_from_dead_connection_cannot_resolve_new_request(self):
        c = self.client()
        c.send(dict(id=2, method='echo', params=dict(delay=.5, value='old')))
        time.sleep(.1)
        c.kill()
        c = self.client()
        result = c.request(2, 'echo', value='new')
        self.assertEqual('new', result['result']['value'])

    def test_broken_backend_output_does_not_leave_writer_process_group(self):
        c = self.client()
        server_pid = int(next(self.home.glob('.remote-gpt/*/server.pid')).read_text())
        c.send(dict(id=2, method='test/crash', params={}))
        deadline = time.monotonic() + 10
        while c.proc.poll() is None and time.monotonic() < deadline:
            time.sleep(.02)
        self.assertIsNotNone(c.proc.poll(), 'attachment must close after backend protocol failure')
        c.kill()
        deadline = time.monotonic() + 5
        while time.monotonic() < deadline:
            try:
                os.killpg(server_pid, 0)
            except ProcessLookupError:
                break
            time.sleep(.02)
        else:
            self.fail('backend process group survived its host')


if __name__ == '__main__':
    unittest.main()
