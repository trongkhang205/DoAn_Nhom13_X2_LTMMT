package doan_nhom13_x2_ltmmt;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Xử lý các lệnh điều khiển nhận từ Web Client (chuột, bàn phím, clipboard, ping/pong).
 */
public class WebInputHandler {

    private final OutputStream out;
    private final ServerLogger logger;
    private final WebScreenCaster screenCaster;
    private Robot robot;

    public WebInputHandler(OutputStream out, ServerLogger logger, WebScreenCaster screenCaster) {
        this.out = out;
        this.logger = logger;
        this.screenCaster = screenCaster;
        try {
            this.robot = new Robot();
            this.robot.setAutoDelay(2);
        } catch (AWTException e) {
            logger.appendLog("WebInputHandler: Không thể khởi tạo Robot: " + e.getMessage());
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
                        logger.appendLog("WebInputHandler: Đã nhận nội dung Clipboard từ Web Client.");
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

                default:
                    break;
            }
        } catch (Exception ex) {
            // Đảm bảo ngoại lệ từ 1 thao tác không làm crash luồng kết nối
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
