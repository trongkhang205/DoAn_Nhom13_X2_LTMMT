package doan_nhom13_x2_ltmmt;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Xử lý các lệnh điều khiển nhận từ Web Client:
 * - Chuột (Move, Click, Double-click, Right-click, Wheel, Drag)
 * - Cảm ứng điện thoại (Touch mapping)
 * - Bàn phím (W3C codes sang VK Codes)
 * - Đồng bộ Clipboard hai chiều
 * - Truyền tải tệp tin hai chiều (Upload / Download / File List)
 * - Tự động co dãn chất lượng và tốc độ khung hình (Adaptive Quality)
 */
public class WebInputHandler {

    private final OutputStream out;
    private final ServerLogger logger;
    private final WebScreenCaster screenCaster;
    private Robot robot;

    // Thư mục lưu trữ tệp tin truyền nhận
    private static final File RECEIVED_DIR = new File(System.getProperty("user.home"), "Downloads" + File.separator + "RemoteDesktop_Received");
    private FileOutputStream currentUploadFos;
    private String currentUploadName;

    public WebInputHandler(OutputStream out, ServerLogger logger, WebScreenCaster screenCaster) {
        this.out = out;
        this.logger = logger;
        this.screenCaster = screenCaster;
        try {
            this.robot = new Robot();
            this.robot.setAutoDelay(2);
        } catch (AWTException e) {
            if (logger != null) {
                logger.appendLog("WebInputHandler: Không thể khởi tạo Robot: " + e.getMessage());
            }
        }
    }

    /**
     * Phân tích và thực thi lệnh JSON từ trình duyệt
     */
    public void handleMessage(String json) {
        if (json == null || json.trim().isEmpty() || robot == null) return;

        Map<String, String> data = parseSimpleJson(json);
        String type = data.get("type");
        if (type == null) return;

        try {
            switch (type) {
                case "mouse":
                    handleMouse(data);
                    break;

                case "key":
                    handleKey(data);
                    break;

                case "special":
                    handleSpecial(data);
                    break;

                case "quality":
                    if (data.containsKey("value") && screenCaster != null) {
                        float q = Float.parseFloat(data.get("value"));
                        screenCaster.setQuality(q);
                    }
                    break;

                case "target_fps":
                    if (data.containsKey("value") && screenCaster != null) {
                        int fps = Integer.parseInt(data.get("value"));
                        screenCaster.setTargetFps(fps);
                    }
                    break;

                case "clipboard_set":
                    if (data.containsKey("text")) {
                        String text = unescapeJson(data.get("text"));
                        StringSelection sel = new StringSelection(text);
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
                        if (logger != null) {
                            logger.appendLog("WebInputHandler: Đã nhận nội dung Clipboard từ Web Client.");
                        }
                    }
                    break;

                case "clipboard_get":
                    try {
                        String clip = (String) Toolkit.getDefaultToolkit().getSystemClipboard()
                                .getData(DataFlavor.stringFlavor);
                        if (clip != null) {
                            String resp = "{\"type\":\"clipboard_data\",\"text\":\"" + escapeJson(clip) + "\"}";
                            WebSocketHelper.sendTextFrame(out, resp);
                        }
                    } catch (Exception ignored) {}
                    break;

                case "ping":
                    String timestamp = data.get("t");
                    String pong = "{\"type\":\"pong\",\"t\":" + (timestamp != null ? timestamp : "0") + "}";
                    WebSocketHelper.sendTextFrame(out, pong);
                    break;

                // ===== TRUYỀN TỆP TIN HAI CHIỀU =====
                case "file_upload_start":
                    handleFileUploadStart(data);
                    break;

                case "file_upload_chunk":
                    handleFileUploadChunk(data);
                    break;

                case "file_upload_end":
                    handleFileUploadEnd();
                    break;

                case "file_list":
                    handleFileList();
                    break;

                case "file_download_req":
                    handleFileDownload(data);
                    break;

                default:
                    break;
            }
        } catch (Exception ex) {
            // Đảm bảo không crash luồng xử lý
        }
    }

    private void handleMouse(Map<String, String> data) {
        String action = data.get("action");
        if (action == null) return;

        switch (action) {
            case "move":
                if (data.containsKey("x") && data.containsKey("y")) {
                    int x = (int) Math.round(Double.parseDouble(data.get("x")));
                    int y = (int) Math.round(Double.parseDouble(data.get("y")));
                    robot.mouseMove(x, y);
                }
                break;

            case "down":
                if (data.containsKey("button")) {
                    int btn = Integer.parseInt(data.get("button"));
                    int mask = getButtonMask(btn);
                    if (mask != 0) robot.mousePress(mask);
                }
                break;

            case "up":
                if (data.containsKey("button")) {
                    int btn = Integer.parseInt(data.get("button"));
                    int mask = getButtonMask(btn);
                    if (mask != 0) robot.mouseRelease(mask);
                }
                break;

            case "wheel":
                if (data.containsKey("deltaY")) {
                    double delta = Double.parseDouble(data.get("deltaY"));
                    int notches = (int) Math.round(delta > 0 ? 1 : (delta < 0 ? -1 : 0));
                    if (notches != 0) robot.mouseWheel(notches);
                }
                break;
        }
    }

    private void handleKey(Map<String, String> data) {
        String action = data.get("action");
        String code = data.get("code");
        int fallbackKeyCode = data.containsKey("keyCode") ? Integer.parseInt(data.get("keyCode")) : 0;

        int vk = KeyMapper.getVkCode(code, fallbackKeyCode);
        if (vk == KeyEvent.VK_UNDEFINED) return;

        try {
            if ("down".equals(action)) {
                robot.keyPress(vk);
            } else if ("up".equals(action)) {
                robot.keyRelease(vk);
            }
        } catch (IllegalArgumentException ignored) {}
    }

    private void handleSpecial(Map<String, String> data) {
        String command = data.get("command");
        if (command == null) return;

        switch (command) {
            case "win_key":
                try {
                    robot.keyPress(KeyEvent.VK_WINDOWS);
                    robot.keyRelease(KeyEvent.VK_WINDOWS);
                } catch (Exception ignored) {}
                break;

            case "alt_tab":
                try {
                    robot.keyPress(KeyEvent.VK_ALT);
                    robot.keyPress(KeyEvent.VK_TAB);
                    robot.delay(100);
                    robot.keyRelease(KeyEvent.VK_TAB);
                    robot.keyRelease(KeyEvent.VK_ALT);
                } catch (Exception ignored) {}
                break;

            case "escape":
                try {
                    robot.keyPress(KeyEvent.VK_ESCAPE);
                    robot.keyRelease(KeyEvent.VK_ESCAPE);
                } catch (Exception ignored) {}
                break;

            case "enter":
                try {
                    robot.keyPress(KeyEvent.VK_ENTER);
                    robot.keyRelease(KeyEvent.VK_ENTER);
                } catch (Exception ignored) {}
                break;
        }
    }

    // ===== XỬ LÝ TRUYỀN TỆP TIN =====

    private void handleFileUploadStart(Map<String, String> data) {
        String rawName = data.get("name");
        if (rawName == null) return;
        String safeName = new File(rawName).getName();
        try {
            if (currentUploadFos != null) {
                try { currentUploadFos.close(); } catch (Exception ignored) {}
            }
            if (!RECEIVED_DIR.exists()) {
                RECEIVED_DIR.mkdirs();
            }
            File targetFile = new File(RECEIVED_DIR, safeName);
            currentUploadFos = new FileOutputStream(targetFile);
            currentUploadName = safeName;
            if (logger != null) {
                logger.appendLog("WebInputHandler: Bắt đầu tiếp nhận tệp tin [" + safeName + "] từ Client...");
            }
        } catch (Exception e) {
            if (logger != null) logger.appendLog("Lỗi mở tệp lưu: " + e.getMessage());
        }
    }

    private void handleFileUploadChunk(Map<String, String> data) {
        String b64 = data.get("data");
        if (currentUploadFos != null && b64 != null) {
            try {
                byte[] bytes = Base64.getDecoder().decode(b64);
                currentUploadFos.write(bytes);
            } catch (Exception e) {
                if (logger != null) logger.appendLog("Lỗi ghi chunk tệp: " + e.getMessage());
            }
        }
    }

    private void handleFileUploadEnd() {
        if (currentUploadFos != null) {
            try {
                currentUploadFos.flush();
                currentUploadFos.close();
                if (logger != null) {
                    logger.appendLog("WebInputHandler: Đã nhận trọn vẹn tệp [" + currentUploadName + "] lưu tại: " + RECEIVED_DIR.getAbsolutePath());
                }
                String resp = "{\"type\":\"file_upload_success\",\"name\":\"" + escapeJson(currentUploadName) + "\"}";
                WebSocketHelper.sendTextFrame(out, resp);
            } catch (Exception e) {
                if (logger != null) logger.appendLog("Lỗi đóng tệp: " + e.getMessage());
            } finally {
                currentUploadFos = null;
                currentUploadName = null;
            }
        }
    }

    private void handleFileList() {
        try {
            if (!RECEIVED_DIR.exists()) {
                RECEIVED_DIR.mkdirs();
            }
            File[] files = RECEIVED_DIR.listFiles();
            StringBuilder sb = new StringBuilder("{\"type\":\"file_list_resp\",\"files\":[");
            if (files != null) {
                boolean first = true;
                for (File f : files) {
                    if (f.isFile()) {
                        if (!first) sb.append(",");
                        first = false;
                        sb.append("{\"name\":\"").append(escapeJson(f.getName()))
                          .append("\",\"size\":").append(f.length())
                          .append(",\"date\":\"").append(new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(f.lastModified())))
                          .append("\"}");
                    }
                }
            }
            sb.append("]}");
            WebSocketHelper.sendTextFrame(out, sb.toString());
        } catch (Exception e) {
            if (logger != null) logger.appendLog("Lỗi quét danh sách tệp: " + e.getMessage());
        }
    }

    private void handleFileDownload(Map<String, String> data) {
        String rawName = data.get("name");
        if (rawName == null) return;
        String safeName = new File(rawName).getName();
        File f = new File(RECEIVED_DIR, safeName);
        if (!f.exists() || !f.isFile()) {
            try {
                WebSocketHelper.sendTextFrame(out, "{\"type\":\"file_download_error\",\"message\":\"Không tìm thấy tệp trên máy chủ!\"}");
            } catch (Exception ignored) {}
            return;
        }

        new Thread(() -> {
            try (FileInputStream fis = new FileInputStream(f)) {
                byte[] buffer = new byte[32 * 1024];
                long totalSize = f.length();
                int totalChunks = (int) Math.ceil((double) totalSize / buffer.length);
                if (totalChunks == 0) totalChunks = 1;
                int chunkIndex = 0;
                int bytesRead;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    byte[] chunkBytes = buffer;
                    if (bytesRead < buffer.length) {
                        chunkBytes = Arrays.copyOf(buffer, bytesRead);
                    }
                    String b64 = Base64.getEncoder().encodeToString(chunkBytes);
                    String msg = "{\"type\":\"file_download_chunk\",\"name\":\"" + escapeJson(safeName) +
                            "\",\"chunk\":" + chunkIndex + ",\"total\":" + totalChunks +
                            ",\"data\":\"" + b64 + "\"}";
                    WebSocketHelper.sendTextFrame(out, msg);
                    chunkIndex++;
                    Thread.sleep(10);
                }

                WebSocketHelper.sendTextFrame(out, "{\"type\":\"file_download_complete\",\"name\":\"" + escapeJson(safeName) + "\"}");
                if (logger != null) {
                    logger.appendLog("WebInputHandler: Đã truyền xong tệp [" + safeName + "] về Client.");
                }
            } catch (Exception e) {
                if (logger != null) logger.appendLog("Lỗi truyền tải tệp: " + e.getMessage());
            }
        }, "FileDownload-Thread").start();
    }

    private int getButtonMask(int button) {
        switch (button) {
            case 0: return InputEvent.BUTTON1_DOWN_MASK; // Chuột trái
            case 1: return InputEvent.BUTTON2_DOWN_MASK; // Chuột giữa
            case 2: return InputEvent.BUTTON3_DOWN_MASK; // Chuột phải
            default: return 0;
        }
    }

    /**
     * Parser JSON phẳng đơn giản không cần thư viện bên ngoài
     */
    public static Map<String, String> parseSimpleJson(String json) {
        Map<String, String> map = new HashMap<>();
        Pattern pattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(?:\"([^\"]*)\"|([\\w.-]+))");
        Matcher matcher = pattern.matcher(json);
        while (matcher.find()) {
            String key = matcher.group(1);
            String val = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            map.put(key, val);
        }
        return map;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String unescapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }
}
