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
echo [*] Dang khoi dong Cloud Relay Gateway tren cong 3000...
start "Cloud Relay Gateway (Port 3000)" cmd /k "cd gateway && node server.js"
timeout /t 2 /nobreak >nul

echo [*] Dang khoi chay Web Remote Engine tren cong 8080...
start "" javaw -cp "build\classes" doan_nhom13_x2_ltmmt.WebRemoteServer
timeout /t 2 /nobreak >nul

echo [*] Dang tao duong link Cloudflare Tunnel toc do cao cho dien thoai 4G...
start "Cloudflare Tunnel 4G (Khong Bao Gio Bi Loi 503)" cmd /k "echo ======================================================== && echo   COPY DUONG LINK HTTPS CO DUOI .trycloudflare.com VA MO TREN DIEN THOAI: && echo ======================================================== && .\cloudflared.exe tunnel --url http://localhost:3000"

timeout /t 3 /nobreak >nul
echo [*] Dang mo trinh duyet quan ly may chu (http://localhost:8080)...
start http://localhost:8080
echo [OK] He thong ket noi Internet da san sang!
goto END

:END
exit
