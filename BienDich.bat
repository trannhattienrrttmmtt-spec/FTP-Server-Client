@echo off
chcp 65001 >nul
echo ======================================================================
echo   BIÊN DỊCH TOÀN BỘ DỰ ÁN FTP HAI KÊNH (NHÓM 11 - HUTECH)
echo ======================================================================
cd /d "%~dp0"

if not exist "bin" mkdir bin

echo Dang bien dich toan bo ma nguon (datachannel, server, client)...
javac -encoding UTF-8 -d bin datachannel\*.java server\*.java client\*.java

if %ERRORLEVEL% EQU 0 (
    echo.
    echo [THANH CONG] Da bien dich xong tat ca cac package vao thu muc bin!
) else (
    echo.
    echo [THAT BAI] Co loi xay ra trong qua trinh bien dich!
)

pause
