#!/usr/bin/env python3
"""Private, SSH-only app-server host. Detaching a client never closes server stdin.

One connection owns a profile at a time. Reattachment replaces that connection,
remaps request IDs, and replays a thread's baseline and ordered event journal.
Only the daemon lifetime is durable; machine/daemon failure still ends the task.
"""
import fcntl
import hashlib
import json
import os
import signal
import socket
import sqlite3
import subprocess
import sys
import threading
import tempfile
import time
from pathlib import Path

LIMIT = 32 * 1024 * 1024


def encode(value):
    return (json.dumps(value, ensure_ascii=False, separators=(',', ':')) + '\n').encode()


class Peer:
    def __init__(self, sock):
        self.sock = sock
        self.out = tempfile.TemporaryFile()
        self.condition = threading.Condition()
        self.written = 0
        self.read_at = 0
        self.alive = True
        self.resuming = None
        self.replay_since = 0
        threading.Thread(target=self.write, daemon=True).start()

    def send(self, value):
        data = encode(value)
        with self.condition:
            if not self.alive:
                return
            self.out.seek(self.written)
            self.out.write(data)
            self.written += len(data)
            self.condition.notify()

    def write(self):
        try:
            while True:
                with self.condition:
                    while self.alive and self.read_at == self.written:
                        self.condition.wait()
                    if not self.alive:
                        break
                    self.out.seek(self.read_at)
                    data = self.out.read(min(65536, self.written - self.read_at))
                    self.read_at += len(data)
                    if self.read_at == self.written:
                        self.out.seek(0)
                        self.out.truncate()
                        self.read_at = self.written = 0
                self.sock.sendall(data)
        except OSError:
            pass
        finally:
            self.close()
            self.out.close()

    def close(self):
        with self.condition:
            self.alive = False
            self.condition.notify()
        try:
            self.sock.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        self.sock.close()


class Host:
    def __init__(self, folder, command):
        self.lock = threading.RLock()
        self.peer = None
        self.pending = {}
        self.approvals = {}
        self.sequence = 0
        self.initialized = False
        self.initialization = None
        self.initializing = False
        self.init_waiters = []
        self.baselines = {}
        self.last_thread = ''
        # Old journal cannot represent a new app-server process. Its real history
        # remains in Codex; only this daemon's live events belong in this journal.
        self.db = sqlite3.connect(folder / 'events.sqlite', check_same_thread=False)
        self.db.execute('PRAGMA journal_mode=WAL')
        self.db.execute('PRAGMA synchronous=NORMAL')
        self.db.execute('DROP TABLE IF EXISTS events')
        self.db.execute('CREATE TABLE events (seq INTEGER PRIMARY KEY, thread TEXT, body TEXT)')
        self.db.execute('CREATE INDEX by_thread ON events(thread, seq)')
        self.db.commit()
        self.process = subprocess.Popen(command, shell=True, executable='/bin/sh',
                                        stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                        stderr=sys.stderr, start_new_session=True)
        (folder / 'server.pid').write_text(str(self.process.pid))
        threading.Thread(target=self.read, daemon=True).start()

    def upstream(self, value):
        self.process.stdin.write(encode(value))
        self.process.stdin.flush()

    def receive(self, peer, value):
        with self.lock:
            if peer is not self.peer:
                return
            method = value.get('method')
            ident = value.get('id')
            params = value.get('params') or {}
            if method == 'initialize' and self.initialization is not None:
                peer.send(dict(id=ident, result=self.initialization))
                return
            if method == 'initialize':
                if self.initializing:
                    self.init_waiters.append((peer, ident))
                    return
                self.initializing = True
            if method == 'initialized':
                if not self.initialized:
                    self.upstream(value)
                    self.initialized = True
                return
            if method == 'remote/session':
                peer.send(dict(id=ident, result={'lastThread': self.last_thread}))
                return
            thread = params.get('threadId', '')
            if method == 'thread/resume' and thread in self.baselines:
                baseline, since = self.baselines[thread]
                self.last_thread = thread
                peer.resuming = thread
                peer.replay_since = since
                peer.send(dict(id=ident, result=dict(baseline, remoteReplay=True)))
                return
            if method == 'remote/replay':
                if peer.resuming != thread:
                    return
                since = peer.replay_since
                peer.send(dict(method='remote/replayStarted', params={}))
                for (body,) in self.db.execute('SELECT body FROM events WHERE thread=? AND seq>? ORDER BY seq', (thread, since)):
                    event = json.loads(body)
                    # Approval requests are replayed only if still awaiting an answer.
                    if 'id' not in event:
                        peer.send(event)
                for request in self.approvals.values():
                    peer.send(request)
                peer.send(dict(method='remote/replayCompleted', params={}))
                peer.resuming = None
                return
            if method == 'thread/unsubscribe' and thread in self.baselines:
                # Keep the host's subscription so detached turns remain observable.
                peer.send(dict(id=ident, result={}))
                return
            if method and ident is not None:
                self.sequence += 1
                self.pending[self.sequence] = (peer, ident, method)
                value['id'] = self.sequence
            elif not method and ident is not None:
                if str(ident) not in self.approvals:
                    return  # Stale or duplicate approval must never reach Codex.
                self.approvals.pop(str(ident), None)
            self.upstream(value)

    def read(self):
        try:
            while True:
                line = self.process.stdout.readline(LIMIT + 1)
                if not line:
                    break
                if len(line) > LIMIT:
                    raise ValueError('app-server message exceeds 32 MiB')
                value = json.loads(line)
                with self.lock:
                    method = value.get('method')
                    ident = value.get('id')
                    if not method:
                        pending = self.pending.pop(ident, None)
                        if pending is None:
                            continue
                        peer, original, request = pending
                        result = value.get('result')
                        if request == 'initialize':
                            self.initializing = False
                            for waiting_peer, waiting_id in self.init_waiters:
                                if waiting_peer is self.peer:
                                    waiting_peer.send(dict(value, id=waiting_id))
                            self.init_waiters.clear()
                        if result is not None:
                            if request == 'initialize':
                                self.initialization = result
                            if request in ('thread/start', 'thread/resume'):
                                thread = result.get('thread', {}).get('id')
                                if thread:
                                    seq = self.db.execute('SELECT COALESCE(MAX(seq),0) FROM events').fetchone()[0]
                                    self.baselines[thread] = (result, seq)
                                    self.last_thread = thread
                        value['id'] = original
                        if peer is self.peer:
                            peer.send(value)
                        continue
                    params = value.get('params') or {}
                    thread = params.get('threadId', '')
                    if ident is not None:
                        self.approvals[str(ident)] = value
                    if method == 'serverRequest/resolved':
                        self.approvals.pop(str(params.get('requestId')), None)
                    if method == 'turn/completed':
                        self.approvals = {k: v for k, v in self.approvals.items()
                                          if v.get('params', {}).get('threadId') != thread}
                    if method == 'thread/closed':
                        self.baselines.pop(thread, None)
                    self.db.execute('INSERT INTO events(thread,body) VALUES (?,?)', (thread, json.dumps(value)))
                    self.db.commit()
                    if self.peer and self.peer.resuming is None:
                        self.peer.send(value)
        finally:
            if self.peer:
                self.peer.close()
            # Never silently replace a failed backend during an active attachment.
            os._exit(1)

    def attach(self, sock):
        peer = Peer(sock)
        with self.lock:
            if self.peer:
                self.peer.close()
            self.peer = peer
        try:
            with sock.makefile('rb') as reader:
                while True:
                    line = reader.readline(LIMIT + 1)
                    if not line:
                        break
                    if len(line) > LIMIT:
                        break
                    self.receive(peer, json.loads(line))
        except (OSError, ValueError):
            pass
        finally:
            with self.lock:
                if self.peer is peer:
                    self.peer = None
            peer.close()


def daemon(folder, command):
    lock = open(folder / 'daemon.lock', 'a')
    try:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        return
    path = folder / 'socket'
    path.unlink(missing_ok=True)
    listener = socket.socket(socket.AF_UNIX)
    listener.bind(str(path))
    listener.listen(4)
    (folder / 'daemon.pid').write_text(str(os.getpid()))
    host = Host(folder, command)
    while True:
        sock, _ = listener.accept()
        threading.Thread(target=host.attach, args=(sock,), daemon=True).start()


def attach(folder, command):
    # Serialize check/start; concurrent reconnects cannot start duplicate servers.
    with open(folder / 'launch.lock', 'a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        sock = socket.socket(socket.AF_UNIX)
        try:
            sock.connect(str(folder / 'socket'))
        except OSError:
            sock.close()
            with open(folder / 'daemon.log', 'ab', buffering=0) as log:
                subprocess.Popen([sys.executable, __file__, '--daemon', str(folder), command],
                                 stdin=subprocess.DEVNULL, stdout=log, stderr=log,
                                 start_new_session=True, close_fds=True)
            for _ in range(100):
                sock = socket.socket(socket.AF_UNIX)
                try:
                    sock.connect(str(folder / 'socket'))
                    break
                except OSError:
                    sock.close()
                    time.sleep(.1)
            else:
                raise RuntimeError('远端会话启动失败，请查看 ' + str(folder / 'daemon.log'))
    def receive():
        try:
            while True:
                data = sock.recv(65536)
                if not data:
                    break
                sys.stdout.buffer.write(data)
                sys.stdout.buffer.flush()
        finally:
            os._exit(0)
    threading.Thread(target=receive, daemon=True).start()
    try:
        while True:
            data = os.read(sys.stdin.fileno(), 65536)
            if not data:
                break
            sock.sendall(data)
    finally:
        sock.close()


if __name__ == '__main__':
    os.umask(0o077)
    if sys.argv[1] == '--daemon':
        daemon(Path(sys.argv[2]), sys.argv[3])
    else:
        profile, command = sys.argv[1:3]
        key = hashlib.sha256((profile + '\0' + command).encode()).hexdigest()[:24]
        folder = Path.home() / '.remote-gpt' / key
        folder.mkdir(mode=0o700, parents=True, exist_ok=True)
        attach(folder, command)
