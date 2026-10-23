package doan_nhom13_x2_ltmmt;

import java.awt.AWTException;
import java.awt.Robot;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Giai đoạn 5: EventHandler - Phía Server (Máy bị điều khiển)
 * Chạy trên một Thread riêng biệt.
 * Nhiệm vụ:
 * 1. Liên tục đọc các chuỗi lệnh từ BufferedReader của luồng Socket Client (Port điều khiển).
 * 2. Phân tích chuỗi lệnh nhận được (split theo dấu phẩy).
 * 3. Dùng java.awt.Robot để thực thi các lệnh tương ứng:
 *    - mouseMove(x, y)
 *    - mousePress(buttons)
 *    - mouseRelease(buttons)
 *    - mouseWheel(amount)
 *    - keyPress(keyCode)
 *    - keyRelease(keyCode)
 */
public class EventHandler implements Runnable {

    private final InputStream inputStream;
    private final ServerLogger dashboard;
    private volatile boolean running = true;
    private Robot robot;

    private static final java.io.File RECEIVED_DIR = new java.io.File(System.getProperty("user.home"), "Downloads" + java.io.File.separator + "RemoteDesktop_Received");
    private java.io.FileOutputStream currentUploadFos;
    private String currentUploadName;
    private long currentUploadExpectedSize;
    private long currentUploadReceivedBytes;
    private long currentUploadStartTime;
    private long lastMousePressTime = 0;

    public EventHandler(InputStream inputStream, ServerLogger dashboard) {
        this.inputStream = inputStream;
        this.dashboard = dashboard;
        try {
            this.robot = new Robot();
        } catch (AWTException e) {
            dashboard.appendLog("EventHandler: Lỗi khởi tạo Robot điều khiển: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        dashboard.appendLog("EventHandler: Bắt đầu lắng nghe lệnh điều khiển từ Client...");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while (running && (line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                processCommand(line.trim());
            }
        } catch (Exception e) {
            if (running) {
                dashboard.appendLog("EventHandler: Kênh điều khiển Client đã ngắt (" + e.getMessage() + ")");
            }
        }

        stop();
        dashboard.appendLog("EventHandler: Đã dừng luồng xử lý lệnh điều khiển.");
    }

    /**
     * Phân tích và thực thi lệnh điều khiển từ Client
     */
    private void processCommand(String command) {
        try {
            String[] parts = command.split(",");
            String action = parts[0];

            switch (action) {
                case "MOUSE_MOVE":
                    if (robot != null && parts.length >= 3) {
                        int x = Integer.parseInt(parts[1]);
                        int y = Integer.parseInt(parts[2]);
                        robot.mouseMove(x, y);
                    }
                    break;
                case "MOUSE_PRESS":
                    if (robot != null && parts.length >= 2) {
                        int buttonMask = Integer.parseInt(parts[1]);
                        robot.mousePress(buttonMask);
                        lastMousePressTime = System.currentTimeMillis();
                    }
                    break;

                case "MOUSE_RELEASE":
                    if (robot != null && parts.length >= 2) {
                        int buttonMask = Integer.parseInt(parts[1]);
                        long elapsed = System.currentTimeMillis() - lastMousePressTime;
                        if (elapsed < 35) {
                            try { Thread.sleep(35 - elapsed); } catch (Exception ignored) {}
                        }
                        robot.mouseRelease(buttonMask);
                    }
                    break;

                case "MOUSE_DBLCLICK":
                    if (robot != null && parts.length >= 2) {
                        int buttonMask = Integer.parseInt(parts[1]);
                        robot.mousePress(buttonMask);
                        try { Thread.sleep(35); } catch (Exception ignored) {}
                        robot.mouseRelease(buttonMask);
                        try { Thread.sleep(60); } catch (Exception ignored) {}
                        robot.mousePress(buttonMask);
                        try { Thread.sleep(35); } catch (Exception ignored) {}
                        robot.mouseRelease(buttonMask);
                    }
                    break;

                case "MOUSE_WHEEL":
                    if (robot != null && parts.length >= 2) {
                        int wheelAmt = Integer.parseInt(parts[1]);
                        robot.mouseWheel(wheelAmt);
                    }
                    break;

                case "KEY_PRESS":
                    if (robot != null && parts.length >= 2) {
                        int keyCode = Integer.parseInt(parts[1]);
                        try {
                            robot.keyPress(keyCode);
                        } catch (IllegalArgumentException ignored) {
                            // Bỏ qua nếu phím không hỗ trợ trên hệ điều hành này
                        }
                    }
                    break;

                case "KEY_RELEASE":
                    if (robot != null && parts.length >= 2) {
                        int keyCode = Integer.parseInt(parts[1]);
                        try {
                            robot.keyRelease(keyCode);
                        } catch (IllegalArgumentException ignored) {
                            // Bỏ qua nếu phím không hỗ trợ trên hệ điều hành này
                        }
                    }
                    break;

                // ===== TRUYỀN TỆP TIN TỪ DESKTOP CLIENT =====
                case "FILE_START":
                    if (parts.length >= 2) {
                        String rawParam = parts[1];
                        String rawName;
                        try {
                            byte[] decoded = java.util.Base64.getDecoder().decode(rawParam);
                            rawName = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
                        } catch (Exception ex) {
                            rawName = rawParam;
                        }
                        long size = 0;
                        if (parts.length >= 3) {
                            try {
                                size = Long.parseLong(parts[parts.length - 1]);
                            } catch (Exception ignored) {}
                        }
                        handleFileStart(rawName, size);
                    }
                    break;

                case "FILE_CHUNK":
                    if (parts.length >= 2) {
                        handleFileChunk(parts[1]);
                    }
                    break;

                case "FILE_END":
                    handleFileEnd();
                    break;

                default:
                    // Bỏ qua lệnh không xác định
                    break;
            }
        } catch (Exception ex) {
            // Đảm bảo không làm crash luồng lắng nghe khi có lệnh lỗi
        }
    }

    private void handleFileStart(String rawName, long size) {
        try {
            WebInputHandler.ensureVisible(RECEIVED_DIR);
            String safeName = new java.io.File(rawName).getName().replaceAll("[\\\\/:*?\"<>|]", "_");
            if (safeName.trim().isEmpty() || "..".equals(safeName)) {
                safeName = "desktop_file_" + System.currentTimeMillis();
            }
            java.io.File targetFile = new java.io.File(RECEIVED_DIR, safeName).getCanonicalFile();
            if (!targetFile.toPath().startsWith(RECEIVED_DIR.getCanonicalFile().toPath())) {
                throw new SecurityException("Phát hiện hành vi Path Traversal bất hợp pháp!");
            }
            if (currentUploadFos != null) {
                try { currentUploadFos.close(); } catch (Exception ignored) {}
            }
            currentUploadFos = new java.io.FileOutputStream(targetFile);
            currentUploadName = safeName;
            currentUploadExpectedSize = size;
            currentUploadReceivedBytes = 0;
            currentUploadStartTime = System.currentTimeMillis();
            dashboard.appendLog("EventHandler: Bắt đầu nhận tệp từ Desktop Client [" + safeName + "] (" + formatFileSize(size) + ")...");
        } catch (Exception e) {
            dashboard.appendLog("EventHandler: Lỗi khởi tạo nhận tệp: " + e.getMessage());
        }
    }

    private void handleFileChunk(String b64) {
        if (currentUploadFos == null) return;
        try {
            byte[] bytes = java.util.Base64.getDecoder().decode(b64);
            currentUploadFos.write(bytes);
            currentUploadReceivedBytes += bytes.length;
        } catch (Exception e) {
            dashboard.appendLog("EventHandler: Lỗi ghi chunk tệp: " + e.getMessage());
        }
    }

    private void handleFileEnd() {
        if (currentUploadFos == null) return;
        try {
            currentUploadFos.flush();
            currentUploadFos.close();
            long took = Math.max(1, System.currentTimeMillis() - currentUploadStartTime);
            double mb = currentUploadReceivedBytes / (1024.0 * 1024.0);
            double speed = (mb / (took / 1000.0));
            String speedStr = String.format(java.util.Locale.US, "%.1f MB/s", speed);
            java.io.File savedFile = new java.io.File(RECEIVED_DIR, currentUploadName);
            dashboard.appendLog("✅ EventHandler: Đã nhận trọn vẹn tệp [" + currentUploadName + "] (" 
                    + formatFileSize(currentUploadReceivedBytes) + ") với tốc độ " + speedStr 
                    + " -> Lưu tại: " + savedFile.getAbsolutePath());

            // Tự động mở Windows Explorer chọn ngay tệp vừa nhận trên máy chủ
            try {
                if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                    new ProcessBuilder("explorer.exe", "/select,", savedFile.getAbsolutePath()).start();
                } else if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().open(RECEIVED_DIR);
                }
            } catch (Exception ignored) {}
        } catch (Exception e) {
            dashboard.appendLog("EventHandler: Lỗi đóng tệp nhận: " + e.getMessage());
        } finally {
            currentUploadFos = null;
            currentUploadName = null;
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[] { "B", "KB", "MB", "GB" };
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format(java.util.Locale.US, "%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    public void stop() {
        running = false;
        if (currentUploadFos != null) {
            try { currentUploadFos.close(); } catch (Exception ignored) {}
            currentUploadFos = null;
        }
    }
}
