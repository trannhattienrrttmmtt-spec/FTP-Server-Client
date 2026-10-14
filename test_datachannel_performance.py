import socket
import re
import time
import os
import hashlib
import sys

SERVER_HOST = "127.0.0.1"
SERVER_PORT = 2121
USER = "trong"
PASS = "123456"

def calculate_sha256(data_bytes):
    h = hashlib.sha256()
    h.update(data_bytes)
    return h.hexdigest()

def test_performance():
    print("=" * 80)
    print("  PYTHON AUTOMATED TEST: FTP DATA CHANNEL & FILE TRANSFER (NHOM 11)")
    print("  Phu trach: Phan Thanh Trong (MSSV: 2380602351)")
    print("=" * 80)

    # 1. Connect Control Socket
    print(f"\n[1] Dang ket noi den Control Channel {SERVER_HOST}:{SERVER_PORT}...")
    s = socket.create_connection((SERVER_HOST, SERVER_PORT), timeout=10)
    f = s.makefile('r', encoding='utf-8')

    def send_cmd(cmd):
        print(f">> [CLIENT] {cmd}")
        s.sendall((cmd + "\r\n").encode('utf-8'))
        resp = f.readline().strip()
        print(f"<< [SERVER] {resp}")
        return resp

    banner = f.readline().strip()
    print(f"<< [SERVER] {banner}")
    assert banner.startswith("220")

    # Login
    r = send_cmd(f"USER {USER}")
    assert r.startswith("331")
    r = send_cmd(f"PASS {PASS}")
    assert r.startswith("230")

    def open_passive_socket():
        r = send_cmd("PASV")
        assert r.startswith("227")
        m = re.search(r'\((\d+),(\d+),(\d+),(\d+),(\d+),(\d+)\)', r)
        assert m, "Khong the parse chuoi phan hoi PASV"
        ip = f"{m.group(1)}.{m.group(2)}.{m.group(3)}.{m.group(4)}"
        port = int(m.group(5)) * 256 + int(m.group(6))
        print(f" [PASSIVE] Ket noi toi Data Channel tai {ip}:{port}")
        return socket.create_connection((ip, port), timeout=10)

    results = []

    # Test file sizes: 10 KB, 2 MB, 10 MB
    test_cases = [
        ("file_py_10KB.bin", 10 * 1024),
        ("file_py_2MB.bin", 2 * 1024 * 1024),
        ("file_py_10MB.bin", 10 * 1024 * 1024)
    ]

    for fname, size in test_cases:
        print("\n" + "-" * 80)
        print(f">>> [KIEM THU TRUYEN FILE]: {fname} ({size / 1024:.2f} KB)")
        print("-" * 80)

        # Generate sample content
        content = os.urandom(size)
        original_hash = calculate_sha256(content)
        print(f"  SHA-256 goc: {original_hash}")

        # STOR (Upload)
        data_sock = open_passive_socket()
        r = send_cmd(f"STOR {fname}")
        assert r.startswith("150")

        t0 = time.time()
        data_sock.sendall(content)
        data_sock.close()
        t1 = time.time()
        elapsed_up = max(0.001, t1 - t0)
        speed_up_mb = (size / elapsed_up) / (1024 * 1024)

        r = f.readline().strip()
        print(f"<< [SERVER] {r}")
        assert r.startswith("226")
        print(f"  [STOR HOAN TAT] Thoi gian: {elapsed_up*1000:.1f} ms | Toc do: {speed_up_mb:.2f} MB/s")
        results.append(("UPLOAD", fname, size, elapsed_up * 1000, speed_up_mb, "PASS"))

        # RETR (Download)
        data_sock = open_passive_socket()
        r = send_cmd(f"RETR {fname}")
        assert r.startswith("150")

        t0 = time.time()
        downloaded = bytearray()
        while True:
            chunk = data_sock.recv(65536)
            if not chunk:
                break
            downloaded.extend(chunk)
        data_sock.close()
        t1 = time.time()
        elapsed_down = max(0.001, t1 - t0)
        speed_down_mb = (size / elapsed_down) / (1024 * 1024)

        r = f.readline().strip()
        print(f"<< [SERVER] {r}")
        assert r.startswith("226")
        print(f"  [RETR HOAN TAT] Thoi gian: {elapsed_down*1000:.1f} ms | Toc do: {speed_down_mb:.2f} MB/s")

        # Verify integrity
        download_hash = calculate_sha256(bytes(downloaded))
        print(f"  SHA-256 tai ve: {download_hash}")
        assert original_hash == download_hash, "Loi toan ven: Checksum khong khop!"
        print("  ===> XAC THUC TOAN VEN: KHOP 100%!")
        results.append(("DOWNLOAD", fname, size, elapsed_down * 1000, speed_down_mb, "PASS"))

        # Cleanup file on server
        send_cmd(f"DELE {fname}")

    # Test error cases
    print("\n" + "-" * 80)
    print(">>> [KIEM THU XU LY LOI]")
    print("-" * 80)

    # Error 1: RETR non-existent file
    open_passive_socket() # open pasv
    r = send_cmd("RETR file_khong_ton_tai_9999.txt")
    print(f"  Ket qua RETR file khong ton tai: {r}")
    assert r.startswith("550"), "Server phai tra ve ma 550"
    print("  [PASS] Bat loi 550 thanh cong!")

    # Error 2: STOR outside sandbox
    open_passive_socket()
    r = send_cmd("STOR ../../../hack_exploit.exe")
    print(f"  Ket qua Path Traversal: {r}")
    assert r.startswith("550"), "Server phai ngan chan Path Traversal voi ma 550"
    print("  [PASS] Chan Path Traversal thanh cong!")

    # Error 3: Broken socket midway (TCP Reset / Hard Abort)
    import struct
    dsock = open_passive_socket()
    r = send_cmd("STOR midway_broken.bin")
    assert r.startswith("150")
    dsock.sendall(b"Partial data chunk " * 100)
    # Gui TCP RST packet de mo phong mat mang / socket bi crash dot ngot
    dsock.setsockopt(socket.SOL_SOCKET, socket.SO_LINGER, struct.pack('ii', 1, 0))
    dsock.close() # Sends RST
    r = f.readline().strip()
    print(f"  Ket qua ngat ket noi giua chung: {r}")
    assert r.startswith("426") or r.startswith("425"), "Server phai bao loi ngat ket noi (426)"
    print("  [PASS] Bat loi ngat ket noi giua chung thanh cong!")

    # Summary table
    print("\n" + "=" * 80)
    print("           BANG TONG KET HIEU NANG TRUYEN FILE (PYTHON BENCHMARK)               ")
    print("=" * 80)
    print(f"{'Thao tac':<10} | {'Ten File':<20} | {'Dung luong':<12} | {'Thoi gian':<10} | {'Toc do':<12} | {'Trang thai'}")
    print("-" * 11 + "+" + "-" * 22 + "+" + "-" * 14 + "+" + "-" * 12 + "+" + "-" * 14 + "+" + "-" * 10)
    for op, name, sz, ms, spd, st in results:
        sz_str = f"{sz / 1024:.2f} KB" if sz < 1024*1024 else f"{sz / (1024*1024):.2f} MB"
        print(f"{op:<10} | {name:<20} | {sz_str:<12} | {ms:6.1f} ms  | {spd:6.2f} MB/s | {st}")
    print("=" * 80)
    print(">> TAT CA CAC BAI TEST PYTHON DATA CHANNEL DA VUOT QUA 100%!")

    send_cmd("QUIT")
    s.close()

if __name__ == "__main__":
    test_performance()
