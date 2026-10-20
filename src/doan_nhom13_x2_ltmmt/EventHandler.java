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
        if (robot == null) return;

        try {
            String[] parts = command.split(",");
            String action = parts[0];

            switch (action) {
                case "MOUSE_MOVE":
                    if (parts.length >= 3) {
                        int x = Integer.parseInt(parts[1]);
                        int y = Integer.parseInt(parts[2]);
                        robot.mouseMove(x, y);
                    }
                    break;

                case "MOUSE_PRESS":
                    if (parts.length >= 2) {
                        int buttonMask = Integer.parseInt(parts[1]);
                        robot.mousePress(buttonMask);
                    }
                    break;

                case "MOUSE_RELEASE":
                    if (parts.length >= 2) {
                        int buttonMask = Integer.parseInt(parts[1]);
                        robot.mouseRelease(buttonMask);
                    }
                    break;

                case "MOUSE_WHEEL":
                    if (parts.length >= 2) {
                        int wheelAmt = Integer.parseInt(parts[1]);
                        robot.mouseWheel(wheelAmt);
                    }
                    break;

                case "KEY_PRESS":
                    if (parts.length >= 2) {
                        int keyCode = Integer.parseInt(parts[1]);
                        try {
                            robot.keyPress(keyCode);
                        } catch (IllegalArgumentException ignored) {
                            // Bỏ qua nếu phím không hỗ trợ trên hệ điều hành này
                        }
                    }
                    break;

                case "KEY_RELEASE":
                    if (parts.length >= 2) {
                        int keyCode = Integer.parseInt(parts[1]);
                        try {
                            robot.keyRelease(keyCode);
                        } catch (IllegalArgumentException ignored) {
                            // Bỏ qua nếu phím không hỗ trợ trên hệ điều hành này
                        }
                    }
                    break;

                default:
                    // Bỏ qua lệnh không xác định
                    break;
            }
        } catch (Exception ex) {
            // Đảm bảo không làm crash luồng lắng nghe khi có lệnh lỗi
        }
    }

    public void stop() {
        running = false;
    }
}
