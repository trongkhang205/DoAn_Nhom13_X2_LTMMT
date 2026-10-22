@echo off
chcp 65001 >nul
title Khởi Chạy Remote Desktop Server (Quyền Quản Trị - Elevated Admin)
echo =====================================================================
echo  HỆ THỐNG ĐIỀU KHIỂN MÁY TÍNH TỪ XA - ĐỒ ÁN NHÓM 13 (X2)
echo  Chạy với quyền Administrator (Hạn chế tối đa màn hình đen UAC)
echo =====================================================================

:: Kiểm tra quyền Admin
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo [THÔNG BÁO] Đang yêu cầu quyền Quản Trị Viên (Run as Administrator)...
    powershell -Command "Start-Process '%~dp0Bat_Dau_Ket_Noi_4G_Internet.bat' -Verb RunAs"
    exit /b
)

cd /d "%~dp0"
call Bat_Dau_Ket_Noi_4G_Internet.bat
