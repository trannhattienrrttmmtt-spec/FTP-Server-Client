import socket
import re
import time
import sys

def test_ftp():
    print("[TEST] Dang ket noi den FTP Server tai 127.0.0.1:2121...")
    s = socket.create_connection(("127.0.0.1", 2121), timeout=5)
    f = s.makefile('r', encoding='utf-8')

    def send_cmd(cmd):
        print(f">> [CLIENT] {cmd}")
        s.sendall((cmd + "\r\n").encode('utf-8'))
        resp = f.readline().strip()
        print(f"<< [SERVER] {resp}")
        return resp

    # 1. Check banner
    banner = f.readline().strip()
    print(f"<< [SERVER] {banner}")
    assert banner.startswith("220"), f"Expected 220, got: {banner}"

    # 2. Login
    r = send_cmd("USER tien")
    assert r.startswith("331")
    r = send_cmd("PASS 123456")
    assert r.startswith("230")

    # 3. PWD
    r = send_cmd("PWD")
    assert r.startswith("257")

    # 4. PASV + LIST
    r = send_cmd("PASV")
    assert r.startswith("227")
    m = re.search(r'\((\d+),(\d+),(\d+),(\d+),(\d+),(\d+)\)', r)
    assert m, "Cannot parse PASV response"
    ip = f"{m.group(1)}.{m.group(2)}.{m.group(3)}.{m.group(4)}"
    port = int(m.group(5)) * 256 + int(m.group(6))
    print(f"[TEST] Data channel address: {ip}:{port}")

    # Connect to data socket
    data_sock = socket.create_connection((ip, port), timeout=5)
    r = send_cmd("LIST")
    assert r.startswith("150")

    list_data = b""
    while True:
        chunk = data_sock.recv(4096)
        if not chunk:
            break
        list_data += chunk
    data_sock.close()

    r = f.readline().strip()
    print(f"<< [SERVER] {r}")
    assert r.startswith("226")
    print(f"[TEST] LIST Output:\n{list_data.decode('utf-8')}")

    # 5. PASV + RETR welcome.txt
    r = send_cmd("PASV")
    m = re.search(r'\((\d+),(\d+),(\d+),(\d+),(\d+),(\d+)\)', r)
    port = int(m.group(5)) * 256 + int(m.group(6))
    data_sock = socket.create_connection((ip, port), timeout=5)
    r = send_cmd("RETR welcome.txt")
    assert r.startswith("150")

    retr_data = b""
    while True:
        chunk = data_sock.recv(4096)
        if not chunk:
            break
        retr_data += chunk
    data_sock.close()

    r = f.readline().strip()
    print(f"<< [SERVER] {r}")
    assert r.startswith("226")
    print(f"[TEST] RETR welcome.txt received ({len(retr_data)} bytes).")

    # 6. PASV + STOR upload_test.txt
    r = send_cmd("PASV")
    m = re.search(r'\((\d+),(\d+),(\d+),(\d+),(\d+),(\d+)\)', r)
    port = int(m.group(5)) * 256 + int(m.group(6))
    data_sock = socket.create_connection((ip, port), timeout=5)
    r = send_cmd("STOR upload_test.txt")
    assert r.startswith("150")

    content = b"Du lieu upload thu nghiem tu Client - Nhom 11\n"
    data_sock.sendall(content)
    data_sock.close()

    r = f.readline().strip()
    print(f"<< [SERVER] {r}")
    assert r.startswith("226")
    print("[TEST] Upload thanh cong!")

    # 7. CWD & CDUP
    r = send_cmd("CWD tailieu")
    assert r.startswith("250")
    r = send_cmd("PWD")
    assert "/tailieu" in r
    r = send_cmd("CDUP")
    assert r.startswith("250")
    r = send_cmd("PWD")
    assert r.startswith("257")

    # 8. DELE upload_test.txt
    r = send_cmd("DELE upload_test.txt")
    assert r.startswith("250")

    # 9. QUIT
    r = send_cmd("QUIT")
    assert r.startswith("221")
    s.close()
    print("\n[SUCCESS] TOAN BO CAC BAI TEST FTP SERVER DA VUOT QUA 100%!")

if __name__ == "__main__":
    test_ftp()
