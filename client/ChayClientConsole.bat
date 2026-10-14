@echo off
chcp 65001 >nul
cd /d "%~dp0\.."

if not exist "bin\client\FtpConsoleClient.class" (
    echo [THONG BAO] Chua thay file bien dich, dang tien hanh bien dich tu dong...
    call client\BienDichClient.bat nopause
)

java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp bin client.FtpConsoleClient %*

pause
