@echo off
chcp 65001 >nul
echo ===================================================
echo   BIEN DICH FTP SERVER (NHOM 11 - TRAN NHAT TIEN)
echo ===================================================
cd /d "%~dp0\.."

if not exist "bin" mkdir bin

echo Dang bien dich cac file nguon Java trong thu muc server...
javac -encoding UTF-8 -d bin server\*.java

if %ERRORLEVEL% EQU 0 (
    echo.
    echo [THANH CONG] Da bien dich xong toan bo ma nguon Server!
    echo Cac file .class da duoc luu vao thu muc: bin\server
) else (
    echo.
    echo [THAT BAI] Co loi xay ra trong qua trinh bien dich.
)

pause
