@echo off
title He Thong Web Remote Desktop - Nhom 13
cd /d "%~dp0"

echo ========================================================
echo   HE THONG DIEU KHIEN MAY TINH TU XA NEN TANG WEB
echo   Do An Lap Trinh Mang May Tinh - Nhom 13 (X2)
echo ========================================================
echo.

echo [*] Dang bien dich ma nguon Java...
if not exist "build\classes" mkdir "build\classes"
javac -encoding UTF-8 -d "build\classes" src\doan_nhom13_x2_ltmmt\*.java

if errorlevel 1 (
    echo [LOI] Khong the bien dich ma nguon!
    pause
    exit /b 1
)

echo [*] Bien dich thanh cong!
echo.

:: Hoi nguoi dung muon chay che do nao
echo [1] Chay giao dien Desktop (PlayViewConnect - Java Swing)
echo [2] Chay Web Platform trong mang LAN / Localhost (cong 8080)
echo [3] Chay Tron Goi INTERNET: Web Server (8080) + Cloud Gateway (3000) de ket noi tu 4G
echo.
set /p CHOICE="Chon che do (1, 2 hoac 3): "

if "%CHOICE%"=="1" goto DESKTOP_MODE
if "%CHOICE%"=="2" goto WEB_ONLY
if "%CHOICE%"=="3" goto INTERNET_MODE

:DESKTOP_MODE
echo [*] Dang khoi chay giao dien Desktop...
javaw -cp "build\classes" doan_nhom13_x2_ltmmt.DoAn_Nhom13_X2_LTMMT
goto END

:WEB_ONLY
echo [*] Dang khoi chay Web Remote Engine tren cong 8080...
start "" javaw -cp "build\classes" doan_nhom13_x2_ltmmt.WebRemoteServer
timeout /t 2 /nobreak >nul
echo [*] Dang mo trinh duyet Web (http://localhost:8080)...
start http://localhost:8080
echo [OK] He thong da san sang! Mo trinh duyet de su dung.
goto END

:INTERNET_MODE
if exist "tunnel_url.txt" del /f /q "tunnel_url.txt"

echo [*] Dang khoi dong Cloud Relay Gateway tren cong 3000...
start "Cloud Relay Gateway (Port 3000)" cmd /k "cd gateway && node server.js"
timeout /t 2 /nobreak >nul

echo [*] Dang khoi chay Web Remote Engine tren cong 8080 voi Cau Noi 4G...
start "" javaw -Dauto.tunnel=true -cp "build\classes" doan_nhom13_x2_ltmmt.WebRemoteServer

echo [*] Dang tao duong link Cloudflare Tunnel toc do cao (HTTP/2)...
echo [*] Vui long doi 3-5 giay...

set /a count=0
:WAIT_TUNNEL_MODE
timeout /t 1 /nobreak >nul
set /a count+=1
if exist "tunnel_url.txt" (
    set /p TUNNEL_URL=<tunnel_url.txt
    goto SHOW_LINK_MODE
)
if %count% geq 25 goto TIMEOUT_MODE
goto WAIT_TUNNEL_MODE

:SHOW_LINK_MODE
echo.
echo ========================================================
echo   COPY DUONG LINK HTTPS VA MO TREN DIEN THOAI 4G:
echo   %TUNNEL_URL%
echo ========================================================
echo.
start http://localhost:8080
echo [OK] He thong ket noi Internet da san sang!
pause
goto END

:TIMEOUT_MODE
start http://localhost:8080
echo [OK] Dang mo trinh duyet quan ly may chu...
goto END

:END
exit
