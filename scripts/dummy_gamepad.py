#!/usr/bin/env python3
# Copyright: Iakov Kirilenko
# SPDX-License-Identifier: Apache-2.0

r"""dummy_gamepad.py - a protocol-tracking gamepad client for trik-gamepad.

A tiny interactive client that speaks the DESIGN.md "Gamepad protocol" (TCP or
UDP) to a robot / DummyRobotServer and logs every outbound command and inbound
line with a timestamp. Useful to check a third-party gamepad implementation
against the reference robot behavior, or to drive DummyRobotServer's keepalive
watchdog by hand (announce `keepalive <ms>`, then go silent and watch the
ERROR disconnect).

Commands (one per line, or `;`-separated in `--batch`):
  pad N X Y | btn N [down|up] | wheel <angle> | keepalive <ms>
  custom <message>            # specified in the protocol, robot-side only logs
  wait <ms>                   # roughly waits <ms> (real time), then continues
  quit | exit                 # end the session (EOF / Ctrl+C also end it)

Usage (from repo root, uv venv):
  uv run python scripts/dummy_gamepad.py                          # TCP 127.0.0.1:4444, stdin
  uv run python scripts/dummy_gamepad.py --host 192.168.1.10 --port 4444
  uv run python scripts/dummy_gamepad.py --udp --batch "pad 1 0 0;wait 1000;keepalive 1000"
Exit code 0 on success, 1 when the TCP connect fails.
"""

from __future__ import annotations

import argparse
import datetime
import socket
import sys
import threading
import time


def ts() -> str:
    """Current time as HH:MM:SS.mmm (the same format DummyRobotServer logs)."""
    return datetime.datetime.now().strftime("%H:%M:%S.%f")[:-3]


def run_command(cmd: str, send) -> bool:
    """Executes one command line; returns False when the session should end."""
    line = cmd.strip()
    if not line:
        return True
    if line in ("quit", "exit"):
        return False
    if line.startswith("wait "):
        try:
            delay = float(line[5:].strip()) / 1000.0
        except ValueError:
            print(f"{ts()} ! bad wait value: {line[5:].strip()}", file=sys.stderr)
            return True
        time.sleep(max(delay, 0.0))
        print(f"{ts()} < waited {line[5:].strip()} ms")
        return True
    send(line)
    return True


def tcp_outbound(sock: socket.socket, lock: threading.Lock):
    def send(line: str) -> None:
        with lock:
            sock.sendall((line + "\n").encode("utf-8"))
        print(f"{ts()} > {line}")

    return send


def udp_outbound(sock: socket.socket, target):
    def send(line: str) -> None:
        sock.sendto((line + "\n").encode("utf-8"), target)
        print(f"{ts()} > {line}")

    return send


def tcp_reader(sock: socket.socket):
    def loop() -> None:
        try:
            while True:
                try:
                    data = sock.recv(4096)
                except socket.timeout:
                    continue
                if not data:
                    print(f"{ts()} < [connection closed by robot]")
                    return
                for line in data.decode("utf-8", "replace").splitlines():
                    print(f"{ts()} < {line}")
        except OSError:
            print(f"{ts()} < [read error: connection gone]")

    return loop


def udp_reader(sock: socket.socket):
    def loop() -> None:
        while True:
            try:
                data, addr = sock.recvfrom(4096)
            except socket.timeout:
                continue
            except OSError:
                return
            print(
                f"{ts()} < {addr[0]}:{addr[1]}: "
                f"{data.decode('utf-8', 'replace').rstrip()}"
            )

    return loop


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Protocol-tracking gamepad client (logs every line both ways)."
    )
    parser.add_argument("--host", default="127.0.0.1", help="robot host (default 127.0.0.1)")
    parser.add_argument("--port", type=int, default=4444, help="control port (default 4444)")
    parser.add_argument("--udp", action="store_true", help="use UDP (one command per datagram)")
    parser.add_argument(
        "--batch", help="run ;-separated commands and exit (no quit needed)"
    )
    args = parser.parse_args()

    target = (args.host, args.port)
    if args.udp:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.settimeout(0.5)
        send = udp_outbound(sock, target)
        reader = udp_reader(sock)
    else:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(5.0)
        try:
            sock.connect(target)
        except OSError as exc:
            print(f"error: cannot connect to {args.host}:{args.port}: {exc}", file=sys.stderr)
            return 1
        sock.settimeout(0.5)
        send = tcp_outbound(sock, threading.Lock())
        reader = tcp_reader(sock)

    transport = "udp" if args.udp else "tcp"
    print(f"dummy_gamepad -> {args.host}:{args.port} ({transport})")
    print("commands: pad N X Y | btn N [down|up] | wheel <angle> | keepalive <ms> | "
          "custom <message> | wait <ms> | quit")
    threading.Thread(target=reader, daemon=True).start()

    try:
        if args.batch is not None:
            for cmd in args.batch.split(";"):
                if not run_command(cmd, send):
                    break
        else:
            for line in sys.stdin:
                if not run_command(line, send):
                    break
    except KeyboardInterrupt:
        pass
    finally:
        sock.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
