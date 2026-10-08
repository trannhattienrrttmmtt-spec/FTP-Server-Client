@echo off
chcp 65001 >nul
echo ===================================================
echo   KHOI DONG FTP SERVER (NHOM 11 - TRAN NHAT TIEN)
echo ===================================================
cd /d "%~dp0\.."

if not exist "bin\server\FtpServer.class" (
    echo [THONG BAO] Chua thay file bien dich, dang tien hanh bien dich tu dong...
    call server\BienDich.bat
)

echo Dang khoi dong FTP Server tren cong 2121...
java -Dfile.encoding=UTF-8 -cp bin server.FtpServer 2121 "server\ftp_root"

pause
