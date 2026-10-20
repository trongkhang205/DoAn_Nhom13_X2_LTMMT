# HỆ THỐNG ĐIỀU KHIỂN MÁY TÍNH TỪ XA - NỀN TẢNG WEB (WEB REMOTE PLATFORM)
### Đồ Án Lập Trình Mạng Máy Tính • Nhóm 13 (X2)

---

## 1. Giới thiệu tổng quan
Dự án đã được nâng cấp thành công từ ứng dụng Desktop thuần (Java Swing) lên **Nền tảng Web đa nền tảng (Web-Based Remote Desktop Platform)**. 

Người điều khiển (Client) giờ đây **không cần cài đặt bất kỳ phần mềm hay Java nào**, chỉ cần mở trình duyệt web (Google Chrome, Microsoft Edge, Safari, Firefox) trên Máy tính, Laptop, MacBook, iPad hoặc Điện thoại thông minh là có thể điều khiển máy chủ từ xa mượt mà với độ trễ cực thấp.

### Mô hình tham khảo công nghệ:
Hệ thống được thiết kế dựa trên các giải pháp Web Remote Desktop hàng đầu hiện nay:
* **AnyDesk Web / TeamViewer Web Client**: Giao diện thanh công cụ nổi (Floating Toolbar), đồng bộ clipboard, gửi phím tắt hệ thống, điều chỉnh chất lượng và hiển thị chỉ số mạng theo thời gian thực (FPS, RTT Latency).
* **RustDesk Web / Apache Guacamole**: Tận dụng chuẩn WebSocket (RFC 6455) truyền nhị phân (Binary Frames) kết hợp giải mã phần cứng trình duyệt qua `createImageBitmap` trên thẻ HTML5 `<canvas>`.
* **Chrome Remote Desktop**: Cơ chế ánh xạ tọa độ chuột theo tỉ lệ màn hình thực (Coordinate Mapping) và bắt trọn vẹn sự kiện bàn phím không bị xung đột với phím tắt trình duyệt.

---

## 2. Kiến trúc & Các thành phần mới

```
DoAn_Nhom13_X2_LTMMT/
│
├── src/doan_nhom13_x2_ltmmt/
│   ├── WebRemoteServer.java       <-- Máy chủ HTTP & WebSocket RFC 6455 thuần Java tích hợp
│   ├── WebScreenCaster.java       <-- Luồng chụp màn hình & đóng gói Binary Stream (Header + JPEG)
│   ├── WebInputHandler.java       <-- Bộ xử lý lệnh chuột, phím, clipboard, ping/pong từ Web
│   ├── KeyMapper.java             <-- Ánh xạ mã phím W3C DOM JavaScript sang Java KeyEvent VK Codes
│   ├── WebSocketHelper.java       <-- Bắt tay nâng cấp kết nối và xử lý Framing RFC 6455
│   ├── ServerDashboard.java       <-- Giao diện Server nâng cấp (thêm link Web và nút Mở Trình Duyệt)
│   └── DoAn_Nhom13_X2_LTMMT.java  <-- Menu chính bổ sung mục "Trải nghiệm Nền tảng Web"
│
├── web/                           <-- Giao diện Web Client HTML5 / CSS3 / JavaScript
│   ├── index.html                 <-- Cấu trúc giao diện Web Viewer & Modal đăng nhập
│   ├── style.css                  <-- Giao diện Dark Mode Glassmorphism cao cấp
│   └── app.js                     <-- Engine giải mã ảnh GPU, bắt sự kiện chuột & phím
│
├── gateway/                       <-- Cloud Relay Gateway (Node.js) cho kết nối qua Internet
│   ├── server.js                  <-- Máy chủ trung chuyển Relay & Broker Device ID
│   └── package.json               <-- Cấu hình Node.js WebSocket
│
├── Start_Web_Platform.bat         <-- Khởi động 1-click toàn bộ hệ thống Web
└── RemoteDesktop.bat              <-- Khởi động Menu tổng hợp
```

---

## 3. Các chức năng trên Nền tảng Web (http://localhost:8080)

Khi khởi chạy, toàn bộ giao diện điều hướng và điều khiển hoạt động trực tiếp trên trình duyệt Web với 4 màn hình:

1. **Menu Chính (Launcher)**:
   - Cung cấp 3 thẻ lựa chọn vai trò y hệt như app Desktop ban đầu:
     - **► Máy Chủ (Server - Máy bị điều khiển)**: Vào bảng điều khiển quản lý máy này.
     - **► Máy Khách (Client - Đi điều khiển)**: Vào bảng kết nối tới máy khác.
     - **⇄ Chạy Test Localhost**: Tự động kết nối với chính máy này (Loopback) chỉ với 1 click mà không cần gõ mật khẩu.
2. **Bảng Điều Khiển Máy Chủ (Server Dashboard ngay trên Web)**:
   - Hiển thị IP LAN của máy, cổng Web (8080).
   - Hiển thị Mật khẩu xác thực hiện tại kèm nút **"Đổi Pass"** tức thì.
   - Nút công tắc **Bật / Tắt chia sẻ màn hình** an toàn.
   - Khung **Nhật ký kết nối (Logs)** hiển thị log trực tiếp từ máy chủ theo thời gian thực.
   - Hiển thị đường dẫn trực tiếp để copy gửi sang thiết bị khác trong cùng Wi-Fi.
3. **Bảng Kết Nối Máy Khách (Client Dashboard ngay trên Web)**:
   - Nhập IP, Port và Mật khẩu của máy chủ cần điều khiển.
   - Nút **"Quét tìm trong LAN"**: Tự động quét và liệt kê danh sách các máy chủ đang bật trong mạng, bấm vào là tự điền IP/Port!
   - Nút **"KẾT NỐI NGAY (CONNECT)"**: Bắt đầu kết nối và mở màn hình điều khiển.
4. **Màn Hình Điều Khiển (Remote Viewer Fullscreen Canvas)**:
   - Hiển thị toàn màn hình với thanh công cụ điều khiển thông minh (Floating Toolbar):
     - Chỉ số mạng thời gian thực: FPS, độ trễ RTT (ms), độ phân giải máy chủ.
     - Bật/Tắt chế độ tương tác (Đang Điều Khiển vs Chỉ Xem).
     - Đổi chất lượng hình ảnh (40%, 65%, 85% HD).
     - Phím nhanh: Windows Start, Alt + Tab, Esc, Enter.
     - Đồng bộ Clipboard hai chiều.
     - Bật/Tắt toàn màn hình (Fullscreen) và Co dãn vừa khung (Fit / 1:1).
     - Nút "Rời khỏi màn hình" để ngắt kết nối và quay trở về Menu.

---

## 4. Hướng dẫn khởi chạy & Trải nghiệm

### Cách 1: Khởi động 1-Click thuần Web (Khuyên dùng)
* Nhấp đúp vào file [Start_Web_Platform.bat](file:///p:/Rx00/file_code/codejava/DoAn_Nhom13_X2_LTMMT/Start_Web_Platform.bat).
* Toàn bộ hệ thống sẽ tự động biên dịch và mở trình duyệt tại: **`http://localhost:8080`**.
* Bạn sẽ thấy giao diện **Menu Chính** ngay trên web và có thể chọn:
  - Bấm vào **"Máy Chủ"** để xem IP, mật khẩu, nhật ký log.
  - Bấm vào **"Máy Khách"** để kết nối tới máy khác.
  - Hoặc bấm vào **"Bắt đầu Test Localhost ⚡"** để kiểm thử ngay lập tức!

### Cách 2: Khởi động từ Menu tổng hợp
* Nhấp đúp vào [RemoteDesktop.bat](file:///p:/Rx00/file_code/codejava/DoAn_Nhom13_X2_LTMMT/RemoteDesktop.bat) -> Chọn dòng đầu tiên: **🌐 KHỞI CHẠY NỀN TẢNG WEB TOÀN DIỆN (HTTP://LOCALHOST:8080)**.

### Cách 3: Kết nối từ thiết bị khác trong cùng mạng LAN / Wi-Fi
* Trên máy chủ ServerDashboard, xem ô **"IP Máy này"** (Ví dụ: `192.168.1.15`).
* Mở trình duyệt trên điện thoại hoặc máy tính khác, nhập địa chỉ:
  ```
  http://192.168.1.15:8080
  ```
* Nhập mật khẩu 6 ký tự của máy chủ để bắt đầu điều khiển từ xa ngay trên trình duyệt!

### Cách 4: Chạy Cloud Relay Gateway (Tùy chọn kết nối qua Internet)
Khi cần điều khiển xuyên mạng Internet mà không cần mở port modem:
1. Mở terminal tại thư mục `gateway`:
   ```bash
   cd gateway
   node server.js
   ```
2. Truy cập cổng Gateway tại: `http://localhost:3000`.
