@echo off
chcp 65001 >nul
echo =========================================================
echo   KHOI DONG GIAO DIEN DO HOA FTP SERVER (JAVA SWING UI)
echo   NHOM 11 - TRAN NHAT TIEN (MSSV: 2380602243)
echo =========================================================
cd /d "%~dp0\.."

if not exist "bin\server\ServerApp.class" (
    echo [THONG BAO] Dang bien dich ma nguon...
    javac -encoding UTF-8 -d bin datachannel\*.java server\*.java client\*.java
)

echo Dang mo cua so FTP Server GUI...
java -Dfile.encoding=UTF-8 -cp bin server.ServerApp 2121 "server\ftp_root"
