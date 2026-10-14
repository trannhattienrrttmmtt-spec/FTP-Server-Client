@echo off
chcp 65001 >nul
echo ===================================================
echo   BIEN DICH FTP CLIENT (NHOM 11 - NGUYEN ANH VU)
echo ===================================================
cd /d "%~dp0\.."

if not exist "bin" mkdir bin

echo Dang bien dich client\*.java va datachannel\*.java ...
javac -encoding UTF-8 -d bin datachannel\*.java client\*.java

if %ERRORLEVEL% EQU 0 (
    echo.
    echo [THANH CONG] Da bien dich xong FTP Client!
    echo Cac file .class da duoc luu vao thu muc: bin\client
) else (
    echo.
    echo [THAT BAI] Co loi xay ra trong qua trinh bien dich.
)

if not "%1"=="nopause" pause
