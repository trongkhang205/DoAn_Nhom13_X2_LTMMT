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

:: Hoi nguoi dung muon chay nen tang Web hay Giao dien Desktop
echo [1] Chay giao dien Desktop (PlayViewConnect - chon may chu / may khach)
echo [2] Chay thuan Web (chi khoi dong Web Server tren cong 8080, mo trinh duyet)
echo.
set /p CHOICE="Chon che do (1 hoac 2): "

if "%CHOICE%"=="2" goto WEB_ONLY

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

:END
exit
