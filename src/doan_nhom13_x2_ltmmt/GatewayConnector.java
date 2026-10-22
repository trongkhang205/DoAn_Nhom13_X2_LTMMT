package doan_nhom13_x2_ltmmt;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Cầu nối WebSocket giữa Máy chủ (Java Host) và Cloud Relay Gateway.
 * - Tự động thiết lập kết nối ra Internet qua cổng thông thường (80/443).
 * - Đăng ký Device ID 9 chữ số ngẫu nhiên với Gateway.
 * - Xuyên qua Tường lửa và NAT mà không cần mở port modem.
 */
public class GatewayConnector implements WebSocket.Listener {

    private final String gatewayUrl;
    private final String deviceId;
    private final java.util.function.Supplier<String> passwordSupplier;
    private final ServerLogger logger;
    private WebSocket webSocket;
    private volatile boolean connected = false;
    private volatile boolean running = true;

    private WebScreenCaster screenCaster;
    private WebAudioStreamer audioStreamer;
    private WebInputHandler inputHandler;
    private final StringBuilder textBuffer = new StringBuilder();

    // Bảo mật chống Brute-force qua Internet Relay Gateway
    private int relayFailedAttempts = 0;
    private long relayLockoutExpiry = 0;

    private Thread connectorThread;
    private final Object sendLock = new Object();

    public GatewayConnector(String gatewayUrl, String deviceId, java.util.function.Supplier<String> passwordSupplier, ServerLogger logger) {
        this.gatewayUrl = gatewayUrl != null ? gatewayUrl : "ws://127.0.0.1:3000";
        this.deviceId = deviceId;
        this.passwordSupplier = passwordSupplier;
        this.logger = logger;
    }

    public GatewayConnector(String gatewayUrl, String deviceId, String password, ServerLogger logger) {
        this(gatewayUrl, deviceId, () -> password, logger);
    }

    public void start() {
        connectorThread = new Thread(this::connectLoop, "GatewayConnector-Thread");
        connectorThread.setDaemon(true);
        connectorThread.start();
    }

    public synchronized void stop() {
        this.running = false;
        this.connected = false;
        if (webSocket != null) {
            try {
                webSocket.abort();
            } catch (Exception ignored) {}
            webSocket = null;
        }
        if (connectorThread != null) {
            connectorThread.interrupt();
        }
        stopStreaming();
    }

    public synchronized void reconnectNow() {
        this.connected = false;
        if (webSocket != null) {
            try {
                webSocket.abort();
            } catch (Exception ignored) {}
            webSocket = null;
        }
        if (connectorThread == null || !connectorThread.isAlive()) {
            connectorThread = new Thread(this::connectLoop, "GatewayConnector-Thread");
            connectorThread.setDaemon(true);
            connectorThread.start();
        } else {
            connectorThread.interrupt();
        }
    }

    public void safeSendText(String text) {
        synchronized (sendLock) {
            if (webSocket != null && !webSocket.isOutputClosed()) {
                try {
                    webSocket.sendText(text, true).get(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception e) {
                    if (logger != null) {
                        logger.appendLog("GatewayConnector safeSendText lỗi: " + e.getMessage());
                    }
                }
            }
        }
    }

    private volatile boolean screenSendBusy = false;

    public boolean isScreenSendBusy() {
        return screenSendBusy;
    }

    public boolean isBinaryBusy() {
        return screenSendBusy;
    }

    /**
     * Gửi khung hình nhị phân (Screen Frame) đồng bộ, tuyệt đối tránh lỗi race condition
     * IllegalStateException trên Java 11 WebSocket khi nhiều luồng cùng gửi qua một socket.
     */
    public void safeSendScreenFrame(byte[] payload) throws IOException {
        synchronized (sendLock) {
            if (webSocket != null && !webSocket.isOutputClosed()) {
                screenSendBusy = true;
                try {
                    webSocket.sendBinary(ByteBuffer.wrap(payload), true).get(10, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IOException("WebSocket sendScreenFrame lỗi: " + e.getMessage(), e);
                } finally {
                    screenSendBusy = false;
                }
            }
        }
    }

    /**
     * Gửi gói tin âm thanh nhị phân (Audio Frame) đồng bộ qua cùng WebSocket.
     */
    public void safeSendAudioFrame(byte[] payload) throws IOException {
        synchronized (sendLock) {
            if (webSocket != null && !webSocket.isOutputClosed()) {
                try {
                    webSocket.sendBinary(ByteBuffer.wrap(payload), true).get(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IOException("WebSocket sendAudioFrame lỗi: " + e.getMessage(), e);
                }
            }
        }
    }

    public void safeSendBinary(byte[] payload) throws IOException {
        safeSendScreenFrame(payload);
    }

    public void safeSendPing() {
        synchronized (sendLock) {
            if (webSocket != null && !webSocket.isOutputClosed()) {
                try {
                    // Gui ping bat dong bo de duy tri ket noi relay, khong cho block de tranh nghen khi dang stream
                    webSocket.sendPing(ByteBuffer.wrap(new byte[]{1}));
                } catch (Exception ignored) {}
            }
        }
    }

    private void connectLoop() {
        while (running) {
            Thread.interrupted(); // Xóa cờ ngắt nếu có để đảm bảo luồng tiếp tục chạy
            try {
                if (logger != null) {
                    logger.appendLog("GatewayConnector: Đang kết nối tới Cloud Relay Gateway (" + gatewayUrl + ")...");
                }

                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();

                this.webSocket = client.newWebSocketBuilder()
                        .header("User-Agent", "RemoteDesktop-JavaHost/2.0")
                        .buildAsync(URI.create(gatewayUrl), this)
                        .get(5, java.util.concurrent.TimeUnit.SECONDS);

                this.connected = true;

                // Đăng ký Device ID với Gateway và chờ hoàn tất
                String registerMsg = "{\"type\":\"register_host\",\"id\":\"" + deviceId + "\",\"name\":\"Máy tính nhóm 13\"}";
                safeSendText(registerMsg);

                if (logger != null) {
                    logger.appendLog("GatewayConnector: ĐÃ KẾT NỐI CLOUD RELAY! Mã thiết bị Internet (Device ID): [" + formatDeviceId(deviceId) + "]");
                }

                // Giữ kết nối và gửi Heartbeat định kỳ
                while (running && connected && webSocket != null && !webSocket.isInputClosed() && !webSocket.isOutputClosed()) {
                    try {
                        Thread.sleep(4000);
                        if (running && connected && webSocket != null) {
                            safeSendPing();
                        }
                    } catch (InterruptedException ie) {
                        break; // Thoat vong lap ngu de ket noi lai ngay lap tuc
                    } catch (Exception e) {
                        // Loi gui ping tam thoi khong lam ngat ket noi
                    }
                }
            } catch (Exception e) {
                this.connected = false;
                stopStreaming();
                if (logger != null && running) {
                    logger.appendLog("GatewayConnector: Kênh Cloud Relay chưa sẵn sàng (" + e.getMessage() + "), sẽ thử lại sau 3s...");
                }
                if (running) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ignored) {}
                }
            }
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        this.webSocket = webSocket;
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        textBuffer.append(data);
        if (last) {
            String fullMessage = textBuffer.toString();
            textBuffer.setLength(0);
            handleGatewayMessage(fullMessage);
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        this.connected = false;
        if (logger != null) {
            logger.appendLog("GatewayConnector: Kênh Cloud Relay đã đóng (" + statusCode + "). Đang kết nối lại...");
        }
        stopStreaming();
        if (connectorThread != null) connectorThread.interrupt();
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        this.connected = false;
        if (logger != null) {
            logger.appendLog("GatewayConnector: Lỗi kênh Cloud Relay (" + (error != null ? error.getMessage() : "unknown") + "). Đang kết nối lại...");
        }
        stopStreaming();
        if (connectorThread != null) connectorThread.interrupt();
    }

    private void handleGatewayMessage(String json) {
        Map<String, String> data = WebInputHandler.parseSimpleJson(json);
        String type = data.get("type");
        if (type == null) return;

        if ("auth".equals(type)) {
            // Kiểm tra trạng thái khóa tạm thời do đoán sai quá nhiều lần
            if (System.currentTimeMillis() < relayLockoutExpiry) {
                long remainingSec = (relayLockoutExpiry - System.currentTimeMillis()) / 1000;
                String resp = "{\"type\":\"auth_result\",\"success\":false,\"message\":\"[BẢO MẬT] Đang tạm khóa kết nối từ xa " + remainingSec + " giây do nhập sai mật khẩu quá 5 lần!\"}";
                safeSendText(resp);
                if (logger != null) {
                    logger.appendLog("GatewayConnector: [CẢNH BÁO BẢO MẬT] Chặn yêu cầu Internet (Đang bị khóa do nghi vấn Brute-force)");
                }
                return;
            }

            String clientPass = data.get("password");
            String expectedPass = passwordSupplier != null ? passwordSupplier.get() : "";
            String normalizedClient = clientPass != null ? clientPass.trim().toLowerCase() : "";
            String normalizedExpected = expectedPass != null ? expectedPass.trim().toLowerCase() : "";

            if (!normalizedExpected.isEmpty() && normalizedExpected.equals(normalizedClient)) {
                relayFailedAttempts = 0;
                relayLockoutExpiry = 0;
                if (logger != null) {
                    logger.appendLog("GatewayConnector: Khách kết nối qua Internet xác thực THÀNH CÔNG!");
                }

                // Dẫn xuất khóa bí mật E2EE (AES-256-GCM) TRƯỚC KHI gửi auth_result
                boolean clientWantsE2ee = !"false".equalsIgnoreCase(data.get("e2ee"));
                javax.crypto.SecretKey e2eeKey = null;
                if (clientWantsE2ee) {
                    try {
                        e2eeKey = CryptoHelper.deriveKey(normalizedExpected);
                    } catch (Exception ignored) {}
                }

                java.awt.Dimension screenSize = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
                String resp = "{\"type\":\"auth_result\",\"success\":true,\"width\":"
                        + screenSize.width + ",\"height\":" + screenSize.height + ",\"e2ee\":" + (e2eeKey != null) + "}";
                safeSendText(resp);

                startStreaming(e2eeKey);
            } else {
                relayFailedAttempts++;
                if (relayFailedAttempts >= 5) {
                    relayLockoutExpiry = System.currentTimeMillis() + 5 * 60 * 1000;
                    if (logger != null) {
                        logger.appendLog("GatewayConnector: [CẢNH BÁO BẢO MẬT] ĐÃ KHÓA TẠM KẾT NỐI INTERNET trong 5 phút do nhập sai mật khẩu liên tiếp " + relayFailedAttempts + " lần!");
                    }
                    String resp = "{\"type\":\"auth_result\",\"success\":false,\"message\":\"[BẢO MẬT] Bạn đã nhập sai quá 5 lần! Kết nối bị khóa tạm thời trong 5 phút.\"}";
                    safeSendText(resp);
                } else {
                    if (logger != null) {
                        logger.appendLog("GatewayConnector: Từ chối kết nối qua Internet (Mật khẩu không khớp: Sai lần " + relayFailedAttempts + "/5).");
                    }
                    String resp = "{\"type\":\"auth_result\",\"success\":false,\"message\":\"Mật khẩu không chính xác! (Còn " + (5 - relayFailedAttempts) + " lần thử)\"}";
                    safeSendText(resp);
                }
            }
        } else if (inputHandler != null) {
            inputHandler.handleMessage(json);
        }
    }

    private synchronized void startStreaming(javax.crypto.SecretKey e2eeKey) {
        stopStreaming();

        // Gửi khung nhị phân ảnh màn hình qua WebSocket của Gateway (thread-safe, có await tránh collision)
        WebScreenCaster.BinaryFrameSender frameSender = this::safeSendScreenFrame;

        // Gửi văn bản JSON qua WebSocket của Gateway (thread-safe)
        WebInputHandler.TextMessageSender textSender = this::safeSendText;

        this.screenCaster = new WebScreenCaster(frameSender, logger);
        this.screenCaster.setBusyChecker(this::isScreenSendBusy);
        this.screenCaster.setQuality(0.55f);
        this.screenCaster.setTargetFps(25);
        if (e2eeKey != null) {
            this.screenCaster.setE2eeKey(e2eeKey);
        }
        this.screenCaster.setUacStatusListener((active) -> {
            safeSendText("{\"type\":\"uac_status\",\"active\":" + active + "}");
        });

        this.audioStreamer = new WebAudioStreamer(this::safeSendAudioFrame, logger);
        new Thread(audioStreamer, "Gateway-AudioStreamer-Thread").start();

        this.inputHandler = new WebInputHandler(textSender, logger, screenCaster, audioStreamer);
        this.inputHandler.setPasswordSupplier(this.passwordSupplier);
        new Thread(screenCaster, "Gateway-ScreenCaster-Thread").start();
    }

    private synchronized void stopStreaming() {
        if (screenCaster != null) {
            screenCaster.stop();
            screenCaster = null;
        }
        if (audioStreamer != null) {
            audioStreamer.stop();
            audioStreamer = null;
        }
        inputHandler = null;
    }

    public static String formatDeviceId(String id) {
        if (id == null || id.length() != 9) return id != null ? id : "123 456 789";
        return id.substring(0, 3) + " " + id.substring(3, 6) + " " + id.substring(6, 9);
    }

    public boolean isConnected() {
        return connected;
    }

    public String getDeviceId() {
        return deviceId;
    }
}
