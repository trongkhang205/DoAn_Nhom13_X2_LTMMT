@echo off
title Khoi Chay He Thong Ket Noi 4G Internet - Nhom 13
cd /d "%~dp0"
echo ======================================================================
echo   KHOI CHAY KET NOI XUYEN INTERNET (4G / KHAC MANG KHONG CAN MO PORT)
echo   Do An Lap Trinh Mang May Tinh - Nhom 13 (X2)
echo ======================================================================
echo.

echo [*] 1. Dang khoi dong Cloud Relay Gateway tren cong 3000...
start "Cloud Relay Gateway (Port 3000)" cmd /k "cd gateway && node server.js"
timeout /t 2 /nobreak >nul

:: Kiem tra neu WebRemoteServer chua chay tren cong 8080 thi tu dong bat
netstat -ano | findstr ":8080" | findstr "LISTENING" >nul
if errorlevel 1 (
    echo [*] 2. May Chu Java (Port 8080) chua chay. Dang khoi dong WebRemoteServer...
    start "Web Remote Server (Port 8080)" javaw -cp "build\classes;RemoteDesktop.jar" doan_nhom13_x2_ltmmt.WebRemoteServer
    timeout /t 2 /nobreak >nul
) else (
    echo [*] 2. May Chu Java da san sang tren cong 8080 [OK].
)

echo [*] 3. Dang ket noi toi Cloudflare Edge de tao duong truyen Internet...
echo [*] Vui long doi 3-5 giay, duong link HTTPS ket thuc bang .trycloudflare.com se xuat hien ben duoi!
echo.
echo ======================================================================
echo   COPY DUONG LINK HTTPS .trycloudflare.com VA MO TREN THIET BI KHAC (4G):
echo ======================================================================
echo.

.\cloudflared.exe tunnel --no-autoupdate --no-prechecks --edge-ip-version 4 --url http://localhost:3000
pause
