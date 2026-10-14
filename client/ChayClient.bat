@echo off
chcp 65001 >nul
cd /d "%~dp0\.."

if not exist "bin\client\ClientApp.class" (
    echo [THONG BAO] Chua thay file bien dich, dang tien hanh bien dich tu dong...
    call client\BienDichClient.bat nopause
)

echo Dang khoi dong FTP Client (giao dien Swing)...
start "" javaw -Dfile.encoding=UTF-8 -cp bin client.ClientApp %*
