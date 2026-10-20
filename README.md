# HỆ THỐNG ĐIỀU KHIỂN MÁY TÍNH TỪ XA (REMOTE DESKTOP PLATFORM)
### Đồ Án Môn Học: Lập Trình Mạng Máy Tính • Nhóm 13 (X2)

---

## 1. Giới thiệu tổng quan
Dự án xây dựng giải pháp **Điều khiển máy tính từ xa (Remote Desktop)** hỗ trợ đa nền tảng, kết hợp giữa ứng dụng máy tính truyền thống (Desktop Swing Client) và nền tảng Web hiện đại (Web Browser Client thông qua WebSocket RFC 6455).

* **Phía Máy Chủ (Server / Host)**: Chụp màn hình máy trạm, nén ảnh JPEG, truyền tải luồng hình ảnh thời gian thực và nhận tín hiệu điều khiển chuột, bàn phím để tương tác trực tiếp lên hệ điều hành thông qua Java Robot API.
* **Phía Máy Khách Desktop (Client)**: Kết nối Socket TCP, giải mã và hiển thị hình ảnh màn hình máy chủ, bắt trọn sự kiện chuột và phím gửi về máy chủ.
* **Phía Máy Khách Web (Web Platform)**: Điều khiển máy chủ trực tiếp qua trình duyệt (Chrome, Edge, Safari) trên máy tính hoặc điện thoại mà không cần cài đặt thêm phần mềm.
* **Cloud Relay Gateway (Tùy chọn)**: Node.js Relay trung gian giúp kết nối qua Internet bằng Device ID 9 chữ số.

---

## 2. Kiến trúc hệ thống

```
DoAn_Nhom13_X2_LTMMT/
│
├── src/doan_nhom13_x2_ltmmt/      <-- Mã nguồn Java
│   ├── DoAn_Nhom13_X2_LTMMT.java  <-- Menu khởi chạy tổng hợp
│   ├── ServerDashboard.java       <-- Giao diện quản trị Server (Swing)
│   ├── ClientDashboard.java       <-- Giao diện kết nối Client (Swing)
│   ├── PlayViewConnect.java       <-- Giao diện tích hợp AnyDesk Style (Swing Form)
│   ├── RemoteViewerFrame.java     <-- Màn hình xem & điều khiển máy chủ (Swing)
│   ├── ScreenCaster.java          <-- Luồng chụp màn hình & gửi qua Socket TCP
│   ├── ScreenReceiver.java        <-- Luồng nhận màn hình & render phía Client
│   ├── EventHandler.java          <-- Mô phỏng phím/chuột qua Robot API
│   ├── WebRemoteServer.java       <-- Server HTTP & WebSocket RFC 6455 thuần Java
│   ├── WebScreenCaster.java       <-- Luồng stream màn hình nhị phân qua WebSocket
│   ├── WebInputHandler.java       <-- Xử lý lệnh điều khiển từ trình duyệt Web
│   └── KeyMapper.java             <-- Ánh xạ mã phím W3C sang Java KeyEvent
│
├── web/                           <-- Giao diện Web Client
│   ├── index.html                 <-- Giao diện Web Viewer Canvas & Dashboard
│   ├── style.css                  <-- Giao diện Dark Mode Glassmorphism
│   └── app.js                     <-- Giải mã ảnh GPU và bắt sự kiện chuột/phím
│
├── gateway/                       <-- Cloud Relay Gateway (Node.js)
│   ├── server.js                  <-- Server trung chuyển tín hiệu qua Internet
│   └── package.json               <-- Cấu hình Node.js WebSocket
│
└── Start_Web_Platform.bat         <-- Script khởi động nhanh 1-Click
```

---

## 3. Hướng dẫn khởi chạy

### Yêu cầu môi trường:
* **Java Development Kit (JDK)**: Phiên bản 17 hoặc mới hơn.
* **Trình duyệt web**: Google Chrome, Edge, Safari hoặc Firefox hỗ trợ HTML5 & WebSocket.
* **Node.js** (Tùy chọn khi chạy Gateway): Node 16+.

### Khởi động nhanh (1-Click):
1. Nhấp đúp vào file `Start_Web_Platform.bat`.
2. Chọn `[1]` để mở giao diện Desktop hoặc `[2]` để khởi chạy Web Server trên cổng `8080`.
3. Mở trình duyệt tại địa chỉ: `http://localhost:8080`.

---

## 4. Bảng ma trận đóng góp của thành viên (Quy định bắt buộc)

> *Bảng thống kê sự đóng góp của từng thành viên trong nhóm phục vụ đối chiếu và đánh giá bảo vệ đồ án.*

| MSSV | Họ và Tên | Vai trò chính | Công việc đã thực hiện | Merge Requests (PR) |
| :---: | :--- | :--- | :--- | :---: |
| **2380600980** | **Lê Trọng Khang** *(Nhóm trưởng - 23DTHA5)* | Core Network & Screen Stream | • Thiết kế giao thức truyền tải nhị phân qua Socket TCP<br>• Lập trình module chụp màn hình `ScreenCaster`<br>• Xử lý sự kiện điều khiển bàn phím, chuột `EventHandler`<br>• Tối ưu truyền tải vi sai Delta Frame & Adaptive Quality | !4, !7 |
| **2380602196** | **Phạm Minh Thư** *(23DTHA5)* | Desktop GUI & Client Controls | • Thiết kế giao diện Swing `ServerDashboard` & `ClientDashboard`<br>• Xây dựng màn hình điều khiển `RemoteViewerFrame`<br>• Tích hợp tính năng truyền tệp 2 chiều (Upload/Download)<br>• Hỗ trợ điều khiển cảm ứng Mobile Touch & Bàn phím ảo | !5, !9 |
| **2380600898** | **Trần Quốc Huy** *(23DTHA5)* | Web Platform & Cloud Gateway | • Phát triển máy chủ HTTP & WebSocket RFC 6455 thuần Java (`WebRemoteServer`)<br>• Xây dựng giao diện Web Client Canvas HTML5/JS/CSS giải mã GPU (`web/`)<br>• Xây dựng Cloud Relay Gateway trung chuyển qua Internet (`gateway/`) | !3, !6 |
