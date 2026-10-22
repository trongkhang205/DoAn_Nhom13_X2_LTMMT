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
    private WebAudioStreamer audioStreamer;
    private java.util.function.Supplier<String> passwordSupplier = null;
    private Robot robot;

    // Thư mục lưu trữ tệp tin truyền nhận
    private static final File RECEIVED_DIR = new File(System.getProperty("user.home"), "Downloads" + File.separator + "RemoteDesktop_Received");
    private FileOutputStream currentUploadFos;
    private String currentUploadName;

    public static class FileUploadContext {
        public String fileId;
        public String fileName;
        public long expectedSize;
        public long receivedBytes;
        public FileOutputStream fos;
        public File targetFile;
        public long startTime;
    }
    private final Map<String, FileUploadContext> activeUploads = new java.util.concurrent.ConcurrentHashMap<>();

    @FunctionalInterface
    public interface TextMessageSender {
        void send(String text) throws IOException;
    }
    private final TextMessageSender textSender;

    public WebInputHandler(OutputStream out, ServerLogger logger, WebScreenCaster screenCaster) {
        this((text) -> WebSocketHelper.sendTextFrame(out, text), logger, screenCaster, null);
    }

    public WebInputHandler(OutputStream out, ServerLogger logger, WebScreenCaster screenCaster, WebAudioStreamer audioStreamer) {
        this((text) -> WebSocketHelper.sendTextFrame(out, text), logger, screenCaster, audioStreamer);
    }

    public WebInputHandler(TextMessageSender textSender, ServerLogger logger, WebScreenCaster screenCaster) {
        this(textSender, logger, screenCaster, null);
    }

    public WebInputHandler(TextMessageSender textSender, ServerLogger logger, WebScreenCaster screenCaster, WebAudioStreamer audioStreamer) {
        this.out = null;
        this.textSender = textSender;
        this.logger = logger;
        this.screenCaster = screenCaster;
        this.audioStreamer = audioStreamer;
        try {
            this.robot = new Robot();
            this.robot.setAutoDelay(2);
        } catch (AWTException e) {
            if (logger != null) {
                logger.appendLog("WebInputHandler: Không thể khởi tạo Robot: " + e.getMessage());
            }
        }
    }

    public void setAudioStreamer(WebAudioStreamer audioStreamer) {
        this.audioStreamer = audioStreamer;
    }

    public void setPasswordSupplier(java.util.function.Supplier<String> supplier) {
        this.passwordSupplier = supplier;
    }

    private void sendText(String text) {
        try {
            if (textSender != null) {
                textSender.send(text);
            } else if (out != null) {
                WebSocketHelper.sendTextFrame(out, text);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Phân tích và thực thi lệnh JSON từ trình duyệt
     */
    public void handleMessage(String json) {
        if (json == null || json.trim().isEmpty()) return;

        // Xử lý Siêu Tốc (Fast Path) cho chunk tệp tin không qua Regex
        if (json.startsWith("{\"type\":\"file_upload_chunk\"")) {
            handleFastFileUploadChunk(json);
            return;
        }

        if (robot == null) return;

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

                case "force_keyframe":
                    if (screenCaster != null) {
                        screenCaster.forceKeyframe();
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
                            sendText(resp);
                        }
                    } catch (Exception ignored) {}
                    break;

                case "ping":
                    String timestamp = data.get("t");
                    String pong = "{\"type\":\"pong\",\"t\":" + (timestamp != null ? timestamp : "0") + "}";
                    sendText(pong);
                    break;

                case "set_e2ee":
                    boolean enableE2ee = "true".equalsIgnoreCase(data.get("enabled"));
                    if (screenCaster != null) {
                        if (enableE2ee) {
                            try {
                                String expPass = (passwordSupplier != null) ? passwordSupplier.get() : "";
                                if (expPass != null && !expPass.isEmpty()) {
                                    screenCaster.setE2eeKey(CryptoHelper.deriveKey(expPass.trim().toLowerCase()));
                                }
                            } catch (Exception ignored) {}
                        } else {
                            screenCaster.setE2eeKey(null);
                        }
                    }
                    sendText("{\"type\":\"e2ee_status\",\"enabled\":" + (screenCaster != null && screenCaster.getE2eeKey() != null) + "}");
                    break;

                // ===== TRUYỀN TỆP TIN HAI CHIỀU =====
                case "file_upload_start":
                    handleFileUploadStart(data);
                    break;

                case "file_upload_chunk":
                    handleFileUploadChunk(data);
                    break;

                case "file_upload_end":
                    handleFileUploadEnd(data);
                    break;

                case "file_list":
                    handleFileList();
                    break;

                case "file_download_req":
                    handleFileDownload(data);
                    break;

                // ===== ĐA MÀN HÌNH (MULTI-MONITOR) =====
                case "get_screens":
                    sendText("{\"type\":\"screen_list\",\"screens\":" + WebScreenCaster.getScreensJson() + ",\"current\":" + (screenCaster != null ? screenCaster.getCurrentScreenIndex() : 0) + "}");
                    break;

                case "switch_screen":
                    if (data.containsKey("index") && screenCaster != null) {
                        int targetIdx = Integer.parseInt(data.get("index"));
                        boolean ok = screenCaster.switchScreen(targetIdx);
                        Rectangle b = screenCaster.getScreenBounds();
                        sendText("{\"type\":\"screen_switched\",\"success\":" + ok + ",\"index\":" + targetIdx + ",\"width\":" + (b != null ? b.width : 0) + ",\"height\":" + (b != null ? b.height : 0) + "}");
                    }
                    break;

                // ===== ÂM THANH MÁY TÍNH TỪ XA (REMOTE AUDIO) =====
                case "audio_toggle":
                    if (data.containsKey("enabled") && audioStreamer != null) {
                        boolean en = Boolean.parseBoolean(data.get("enabled"));
                        audioStreamer.setEnabled(en);
                        sendText("{\"type\":\"audio_status\",\"enabled\":" + en + "}");
                    }
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
                    Rectangle bounds = screenCaster != null ? screenCaster.getScreenBounds() : null;
                    int offsetX = bounds != null ? bounds.x : 0;
                    int offsetY = bounds != null ? bounds.y : 0;
                    robot.mouseMove(offsetX + x, offsetY + y);
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
                    int notches = (int) Math.round(delta);
                    if (notches != 0) {
                        // Gioi han toi da 5 notches moi goi tin de tranh giat cuon dot ngot
                        notches = Math.max(-5, Math.min(5, notches));
                        robot.mouseWheel(notches);
                    }
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
        String fileId = data.get("fileId");
        if (fileId == null) fileId = "file_" + System.currentTimeMillis();
        long size = 0;
        try {
            if (data.containsKey("size")) {
                size = Long.parseLong(data.get("size"));
            }
        } catch (Exception ignored) {}

        String safeName = new File(rawName).getName();
        try {
            if (!RECEIVED_DIR.exists()) {
                RECEIVED_DIR.mkdirs();
            }
            File targetFile = new File(RECEIVED_DIR, safeName);
            FileOutputStream fos = new FileOutputStream(targetFile);

            FileUploadContext ctx = new FileUploadContext();
            ctx.fileId = fileId;
            ctx.fileName = safeName;
            ctx.expectedSize = size;
            ctx.receivedBytes = 0;
            ctx.fos = fos;
            ctx.targetFile = targetFile;
            ctx.startTime = System.currentTimeMillis();

            activeUploads.put(fileId, ctx);
            currentUploadFos = fos;
            currentUploadName = safeName;

            if (logger != null) {
                logger.appendLog("WebInputHandler: Bắt đầu nhận tệp [" + safeName + "] (" + formatFileSize(size) + ") [ID: " + fileId + "]...");
            }

            String resp = "{\"type\":\"file_upload_ready\",\"fileId\":\"" + escapeJson(fileId) + "\",\"name\":\"" + escapeJson(safeName) + "\"}";
            sendText(resp);
        } catch (Exception e) {
            if (logger != null) logger.appendLog("Lỗi mở tệp lưu: " + e.getMessage());
            String err = "{\"type\":\"file_upload_error\",\"fileId\":\"" + escapeJson(fileId) + "\",\"message\":\"Lỗi mở tệp lưu: " + escapeJson(e.getMessage()) + "\"}";
            sendText(err);
        }
    }

    private void handleFastFileUploadChunk(String json) {
        try {
            int dataIdx = json.indexOf("\"data\":");
            if (dataIdx == -1) return;
            int startQuote = json.indexOf('"', dataIdx + 7);
            if (startQuote == -1) return;
            int endQuote = json.lastIndexOf('"');
            if (endQuote <= startQuote) return;
            String b64 = json.substring(startQuote + 1, endQuote);

            String fileId = null;
            int idIdx = json.indexOf("\"fileId\":");
            if (idIdx != -1) {
                int q1 = json.indexOf('"', idIdx + 9);
                if (q1 != -1) {
                    int q2 = json.indexOf('"', q1 + 1);
                    if (q2 != -1) {
                        fileId = json.substring(q1 + 1, q2);
                    }
                }
            }

            FileUploadContext ctx = null;
            if (fileId != null) {
                ctx = activeUploads.get(fileId);
            }
            if (ctx == null && !activeUploads.isEmpty()) {
                ctx = activeUploads.values().iterator().next();
            }

            if (ctx != null && ctx.fos != null) {
                byte[] bytes = Base64.getDecoder().decode(b64);
                ctx.fos.write(bytes);
                ctx.receivedBytes += bytes.length;
            } else if (currentUploadFos != null) {
                byte[] bytes = Base64.getDecoder().decode(b64);
                currentUploadFos.write(bytes);
            }
        } catch (Exception e) {
            if (logger != null) logger.appendLog("Lỗi fast chunk: " + e.getMessage());
        }
    }

    private void handleFileUploadChunk(Map<String, String> data) {
        String fileId = data.get("fileId");
        String b64 = data.get("data");
        if (b64 == null) return;

        try {
            FileUploadContext ctx = fileId != null ? activeUploads.get(fileId) : null;
            if (ctx == null && !activeUploads.isEmpty()) {
                ctx = activeUploads.values().iterator().next();
            }

            if (ctx != null && ctx.fos != null) {
                byte[] bytes = Base64.getDecoder().decode(b64);
                ctx.fos.write(bytes);
                ctx.receivedBytes += bytes.length;
            } else if (currentUploadFos != null) {
                byte[] bytes = Base64.getDecoder().decode(b64);
                currentUploadFos.write(bytes);
            }
        } catch (Exception e) {
            if (logger != null) logger.appendLog("Lỗi ghi chunk: " + e.getMessage());
        }
    }

    private void handleFileUploadEnd(Map<String, String> data) {
        String fileId = data != null ? data.get("fileId") : null;
        FileUploadContext ctx = null;
        if (fileId != null) {
            ctx = activeUploads.remove(fileId);
        } else if (!activeUploads.isEmpty()) {
            ctx = activeUploads.values().iterator().next();
            activeUploads.remove(ctx.fileId);
        }

        if (ctx != null) {
            try {
                if (ctx.fos != null) {
                    ctx.fos.flush();
                    ctx.fos.close();
                }
                long took = Math.max(1, System.currentTimeMillis() - ctx.startTime);
                double mb = ctx.receivedBytes / (1024.0 * 1024.0);
                double speed = (mb / (took / 1000.0));
                String speedStr = String.format(Locale.US, "%.1f MB/s", speed);

                if (logger != null) {
                    logger.appendLog("WebInputHandler: Đã nhận trọn vẹn tệp [" + ctx.fileName + "] (" + formatFileSize(ctx.receivedBytes) + ") với tốc độ " + speedStr + " -> Lưu tại: " + ctx.targetFile.getAbsolutePath());
                }

                String resp = "{\"type\":\"file_upload_success\",\"fileId\":\"" + escapeJson(ctx.fileId) + "\",\"name\":\"" + escapeJson(ctx.fileName) + "\",\"size\":" + ctx.receivedBytes + ",\"speed\":\"" + speedStr + "\"}";
                sendText(resp);
            } catch (Exception e) {
                if (logger != null) logger.appendLog("Lỗi đóng tệp: " + e.getMessage());
            } finally {
                if (currentUploadFos == ctx.fos) {
                    currentUploadFos = null;
                    currentUploadName = null;
                }
            }
        } else if (currentUploadFos != null) {
            try {
                currentUploadFos.flush();
                currentUploadFos.close();
                if (logger != null) {
                    logger.appendLog("WebInputHandler: Đã nhận trọn vẹn tệp [" + currentUploadName + "] lưu tại: " + RECEIVED_DIR.getAbsolutePath());
                }
                String resp = "{\"type\":\"file_upload_success\",\"name\":\"" + escapeJson(currentUploadName) + "\"}";
                sendText(resp);
            } catch (Exception ignored) {}
            currentUploadFos = null;
            currentUploadName = null;
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
            sendText(sb.toString());
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
            sendText("{\"type\":\"file_download_error\",\"name\":\"" + escapeJson(safeName) + "\",\"message\":\"Không tìm thấy tệp trên máy chủ!\"}");
            return;
        }

        new Thread(() -> {
            try (FileInputStream fis = new FileInputStream(f)) {
                // Tăng bộ đệm lên 128KB để giảm 50% số lượng gói tin và nhường băng thông cho video
                byte[] buffer = new byte[128 * 1024];
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
                    sendText(msg);
                    chunkIndex++;
                    Thread.sleep(5);
                }

                sendText("{\"type\":\"file_download_complete\",\"name\":\"" + escapeJson(safeName) + "\"}");
                if (logger != null) {
                    logger.appendLog("WebInputHandler: Đã truyền xong tệp [" + safeName + "] (" + formatFileSize(totalSize) + ") về Client.");
                }
            } catch (Exception e) {
                if (logger != null) logger.appendLog("Lỗi truyền tải tệp: " + e.getMessage());
            }
        }, "FileDownload-Thread-" + safeName).start();
    }

    public static String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[] { "B", "KB", "MB", "GB" };
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
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
