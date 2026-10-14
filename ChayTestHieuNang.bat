@echo off
chcp 65001 >nul
echo ======================================================================
echo   CHẠY BÀI TEST HIỆU NĂNG DATA CHANNEL & TRUYỀN FILE (PHAN THANH TRỌNG)
echo ======================================================================
cd /d "%~dp0"

echo [1/2] Dang chay bo kiem thu Java DataChannelPerformanceTest...
java "-Dfile.encoding=UTF-8" -cp bin datachannel.DataChannelPerformanceTest

echo.
echo [2/2] Dang chay bo kiem thu Python Automated Test...
python test_datachannel_performance.py

echo.
echo Da hoan thanh kiem thu!
pause
