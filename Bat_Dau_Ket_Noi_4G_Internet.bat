@echo off
title Khoi Chay He Thong Ket Noi 4G Internet - Nhom 13
cd /d "%~dp0"
echo ======================================================================
echo   KHOI CHAY KET NOI XUYEN INTERNET (4G / KHAC MANG KHONG CAN MO PORT)
echo   Do An Lap Trinh Mang May Tinh - Nhom 13 (X2)
echo ======================================================================
echo.

:: Xoa file link cu neu co
if exist "tunnel_url.txt" del /f /q "tunnel_url.txt"

echo [*] 1. Dang khoi dong Cloud Relay Gateway tren cong 3000...
start "Cloud Relay Gateway (Port 3000)" cmd /k "cd gateway && node server.js"
timeout /t 2 /nobreak >nul

:: Kiem tra neu WebRemoteServer chua chay tren cong 8080 thi tu dong bat
netstat -ano | findstr ":8080" | findstr "LISTENING" >nul
if errorlevel 1 (
    echo [*] 2. May Chu Java (Port 8080) chua chay. Dang khoi dong WebRemoteServer voi Cau Noi 4G...
    start "Web Remote Server (Port 8080)" javaw -Dauto.tunnel=true -cp "build\classes;RemoteDesktop.jar" doan_nhom13_x2_ltmmt.WebRemoteServer
    timeout /t 2 /nobreak >nul
) else (
    echo [*] 2. May Chu Java da san sang tren cong 8080 [OK].
    :: Kich hoat tunnel qua API neu may chu dang chay
    curl -s -X POST http://localhost:8080/api/tunnel-toggle >nul
)

echo [*] 3. Dang ket noi toi Cloudflare Edge toc do cao (HTTP/2)...
echo [*] Vui long doi 3-5 giay, duong link HTTPS .trycloudflare.com se xuat hien ngay ben duoi!
echo.

set /a count=0
:WAIT_TUNNEL
timeout /t 1 /nobreak >nul
set /a count+=1
if exist "tunnel_url.txt" (
    set /p TUNNEL_URL=<tunnel_url.txt
    goto SHOW_LINK
)
if %count% geq 25 goto TIMEOUT_TUNNEL
goto WAIT_TUNNEL

:SHOW_LINK
echo ======================================================================
echo   COPY DUONG LINK HTTPS .trycloudflare.com VA MO TREN THIET BI KHAC (4G):
echo   %TUNNEL_URL%
echo ======================================================================
echo.
echo [*] Dang mo trinh duyet quan ly may chu (http://localhost:8080)...
start http://localhost:8080
goto FINISH

:TIMEOUT_TUNNEL
echo [THONG BAO] He thong dang tiep tuc ket noi Cloudflare Edge o che do ngam.
echo [*] Dang mo trinh duyet quan ly may chu (http://localhost:8080)...
start http://localhost:8080

:FINISH
pause

