"""Isolated SSH fixture, not an AI backend. Binds only to loopback; uses ephemeral keys.

Run: PYTHONPATH=/tmp/remote-ssh-test-libs python3 tools/ssh_fixture.py
Test credentials below are deliberately public and have no system account privileges.
"""
import json
import socket
import threading
import os
import tempfile
import paramiko
import shlex
import subprocess
import sys
import signal
import atexit
from pathlib import Path
from test_remote_session import FAKE

HOST_KEY = paramiko.RSAKey.generate(2048)
CLIENT_KEY = paramiko.RSAKey.generate(2048)
TEST_ROOT = tempfile.TemporaryDirectory(prefix="remote-sftp-fixture-")
os.mkdir(os.path.join(TEST_ROOT.name, "projects"))
Path(TEST_ROOT.name, "fake_server.py").write_text(FAKE)
def cleanup_hosts():
    for pidfile in Path(TEST_ROOT.name).glob('.remote-gpt/*/*.pid'):
        try: os.killpg(int(pidfile.read_text()), signal.SIGKILL)
        except ProcessLookupError: pass
atexit.register(cleanup_hosts)
signal.signal(signal.SIGTERM, lambda *_: sys.exit(0))


class Files(paramiko.SFTPServerInterface):
    def local(self, path):
        resolved = os.path.realpath(os.path.join(TEST_ROOT.name, path.lstrip("/")))
        if os.path.commonpath([resolved, TEST_ROOT.name]) != TEST_ROOT.name:
            raise PermissionError(13, "Outside fixture")
        return resolved

    def canonicalize(self, path):
        return os.path.normpath("/" + path.lstrip("/"))

    def stat(self, path):
        try:
            return paramiko.SFTPAttributes.from_stat(os.stat(self.local(path)))
        except OSError as error:
            return paramiko.SFTPServer.convert_errno(error.errno)

    lstat = stat

    def list_folder(self, path):
        try:
            entries = []
            for name in os.listdir(self.local(path)):
                attr = paramiko.SFTPAttributes.from_stat(os.stat(os.path.join(self.local(path), name)))
                attr.filename = name
                entries.append(attr)
            return entries
        except OSError as error:
            return paramiko.SFTPServer.convert_errno(error.errno)

    def open(self, path, flags, attr):
        try:
            fd = os.open(self.local(path), flags, 0o600)
            mode = "r+b" if flags & os.O_RDWR else "wb" if flags & os.O_WRONLY else "rb"
            stream = os.fdopen(fd, mode)
            handle = paramiko.SFTPHandle(flags)
            handle.readfile = stream
            handle.writefile = stream
            return handle
        except OSError as error:
            return paramiko.SFTPServer.convert_errno(error.errno)

    def chattr(self, path, attr):
        try:
            paramiko.SFTPServer.set_file_attr(self.local(path), attr)
            return paramiko.SFTP_OK
        except OSError as error:
            return paramiko.SFTPServer.convert_errno(error.errno)

    def rename(self, oldpath, newpath):
        try:
            os.rename(self.local(oldpath), self.local(newpath))
            return paramiko.SFTP_OK
        except OSError as error:
            return paramiko.SFTPServer.convert_errno(error.errno)

    def mkdir(self, path, attr):
        try:
            os.mkdir(self.local(path))
            return paramiko.SFTP_OK
        except OSError as error:
            return paramiko.SFTPServer.convert_errno(error.errno)


class Fixture(paramiko.ServerInterface):
    def __init__(self):
        self.ready = threading.Event()

    def check_auth_none(self, username):
        return paramiko.AUTH_SUCCESSFUL if username == "tailnet-fixture" else paramiko.AUTH_FAILED

    def get_allowed_auths(self, username):
        return "publickey,password"

    def check_auth_publickey(self, username, key):
        return paramiko.AUTH_SUCCESSFUL if username == "fixture" and key == CLIENT_KEY else paramiko.AUTH_FAILED

    def check_auth_password(self, username, password):
        return paramiko.AUTH_SUCCESSFUL if (username, password) == ("fixture", "test-only") else paramiko.AUTH_FAILED

    def check_channel_request(self, kind, chanid):
        return paramiko.OPEN_SUCCEEDED if kind == "session" else paramiko.OPEN_FAILED_ADMINISTRATIVELY_PROHIBITED

    def check_channel_exec_request(self, channel, command):
        self.command = command
        self.exec_channel = channel
        self.ready.set()
        if command in (b"fixture-server", b"fixture-exit"): return True
        args = shlex.split(command.decode())
        return (len(args) == 4 and args[0] == 'python3' and args[1].lstrip('/').startswith('.remote-gpt/host-')
                and args[3] == 'python3 "$HOME/fake_server.py"')


def serve(sock):
    transport = paramiko.Transport(sock)
    transport.add_server_key(HOST_KEY)
    transport.set_subsystem_handler("sftp", paramiko.SFTPServer, Files)
    server = Fixture()
    try:
        transport.start_server(server=server)
        channel = transport.accept(10)
        if channel is None or not server.ready.wait(10):
            return
        channel = server.exec_channel
        if server.command.startswith(b'python3 '):
            args = shlex.split(server.command.decode())
            args[0] = sys.executable
            args[1] = Files(server).local(args[1])
            process = subprocess.Popen(args, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                stderr=subprocess.PIPE, env=dict(os.environ, HOME=TEST_ROOT.name))
            def pump(source, sink):
                try:
                    while True:
                        data = source.read1(65536)
                        if not data: break
                        sink(data)
                except (OSError, EOFError): pass
            threading.Thread(target=pump, args=(process.stdout, channel.sendall), daemon=True).start()
            threading.Thread(target=pump, args=(process.stderr, channel.sendall_stderr), daemon=True).start()
            try:
                while True:
                    data = channel.recv(65536)
                    if not data: break
                    process.stdin.write(data); process.stdin.flush()
            finally:
                process.stdin.close()
                try: process.wait(timeout=5)
                except subprocess.TimeoutExpired: process.kill(); process.wait()
            return
        channel.sendall_stderr("(node:31517) [UNDICI-EHPA] Warning: EnvHttpProxyAgent is experimental\n诊断：测试警告，不应阻止握手\n".encode())
        if server.command == b"fixture-exit":
            channel.sendall_stderr(b"fixture: command not found\n")
            channel.send_exit_status(127)
            return
        with channel.makefile("rb") as stream:
            for line in stream:
                message = json.loads(line)
                if "id" not in message:
                    continue
                result = {"echo": message.get("params", {}), "transport": "real-ssh"}
                channel.sendall((json.dumps({"id": message["id"], "result": result}, ensure_ascii=False) + "\n").encode())
    except (EOFError, OSError, paramiko.SSHException):
        pass
    finally:
        transport.close()


if __name__ == "__main__":
    # Only synthetic keys for this loopback test server, never production credentials.
    os.umask(0o077)
    CLIENT_KEY.write_private_key_file("/tmp/remote-ssh-fixture-key.pem", password="fixture-passphrase")
    with socket.socket() as listener:
        listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        listener.bind(("127.0.0.1", 22228))
        listener.listen(8)
        print("SSH test fixture ready on 127.0.0.1:22228", flush=True)
        while True:
            client, _ = listener.accept()
            threading.Thread(target=serve, args=(client,), daemon=True).start()
