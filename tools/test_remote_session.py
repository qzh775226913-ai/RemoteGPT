"""Integration tests using a fake app-server, never real Codex credentials."""
import json
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
        send(dict(id=r['id'], result=dict(model='fake', thread=dict(id='thread-1', status=dict(type='idle'), turns=[]))))
    elif m == 'turn/start':
        send(dict(id=r['id'], result=dict(turn=dict(id='turn-1'))))
        threading.Thread(target=work).start()
    elif m == 'echo':
        time.sleep(r['params'].get('delay', 0))
        send(dict(id=r['id'], result=r['params']))
    elif m == 'initialized':
        pass
    else:
        send(dict(id=r['id'], result={}))
'''


class Client:
    def __init__(self, env, command):
        self.proc = subprocess.Popen([sys.executable, str(HOST), 'profile-1', command],
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

    def client(self):
        c = Client(self.env, self.command)
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

    def test_request_ids_from_dead_connection_cannot_resolve_new_request(self):
        c = self.client()
        c.send(dict(id=2, method='echo', params=dict(delay=.5, value='old')))
        time.sleep(.1)
        c.kill()
        c = self.client()
        result = c.request(2, 'echo', value='new')
        self.assertEqual('new', result['result']['value'])


if __name__ == '__main__':
    unittest.main()
