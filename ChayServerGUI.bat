@echo off
chcp 65001 > nul
title FTP Server Admin GUI - Nhom 11 (HUTECH)
cd /d "%~dp0"

echo [INFO] Dang bien dich ma nguon FTP Server + DataChannel + Client...
if not exist bin mkdir bin
javac -encoding UTF-8 -d bin datachannel\*.java server\*.java client\*.java
if %errorlevel% neq 0 (
    echo [ERROR] Bien dich that bai!
    pause
    exit /b 1
)

echo [INFO] Dang khoi dong giao dien FTP Server Admin GUI (Swing)...
java -Dfile.encoding=UTF-8 -cp bin server.ServerApp
pause
