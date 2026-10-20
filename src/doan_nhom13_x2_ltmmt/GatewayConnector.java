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
    private final String password;
    private final ServerLogger logger;
    private WebSocket webSocket;
    private volatile boolean connected = false;

    private WebScreenCaster screenCaster;
    private WebInputHandler inputHandler;
    private final StringBuilder textBuffer = new StringBuilder();

    public GatewayConnector(String gatewayUrl, String deviceId, String password, ServerLogger logger) {
        this.gatewayUrl = gatewayUrl != null ? gatewayUrl : "ws://localhost:3000";
        this.deviceId = deviceId;
        this.password = password;
        this.logger = logger;
    }

    public void start() {
        Thread connectorThread = new Thread(this::connectLoop, "GatewayConnector-Thread");
        connectorThread.setDaemon(true);
        connectorThread.start();
    }

    private void connectLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (logger != null) {
                    logger.appendLog("GatewayConnector: Đang kết nối tới Cloud Relay Gateway (" + gatewayUrl + ")...");
                }

                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();

                this.webSocket = client.newWebSocketBuilder()
                        .header("User-Agent", "RemoteDesktop-JavaHost/2.0")
                        .buildAsync(URI.create(gatewayUrl), this)
                        .join();

                // Đăng ký Device ID với Gateway
                String registerMsg = "{\"type\":\"register_host\",\"id\":\"" + deviceId + "\",\"name\":\"Máy tính nhóm 13\"}";
                webSocket.sendText(registerMsg, true);

                if (logger != null) {
                    logger.appendLog("GatewayConnector: ĐÃ KẾT NỐI CLOUD RELAY! Mã thiết bị Internet (Device ID): [" + formatDeviceId(deviceId) + "]");
                }

                this.connected = true;

                // Giữ kết nối và gửi Heartbeat định kỳ
                while (connected && webSocket != null) {
                    Thread.sleep(15000);
                    if (webSocket != null && connected) {
                        webSocket.sendPing(ByteBuffer.wrap(new byte[]{1}));
                    }
                }
            } catch (Exception e) {
                this.connected = false;
                if (logger != null) {
                    logger.appendLog("GatewayConnector: Chưa thấy Gateway trên mạng (" + e.getMessage() + "), sẽ thử lại sau 10s...");
                }
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
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
            logger.appendLog("GatewayConnector: Mất kết nối tới Cloud Relay Gateway.");
        }
        stopStreaming();
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        this.connected = false;
        stopStreaming();
    }

    private void handleGatewayMessage(String json) {
        Map<String, String> data = WebInputHandler.parseSimpleJson(json);
        String type = data.get("type");
        if (type == null) return;

        if ("auth".equals(type)) {
            String clientPass = data.get("password");
            if (password != null && password.equalsIgnoreCase(clientPass != null ? clientPass.trim() : "")) {
                if (logger != null) {
                    logger.appendLog("GatewayConnector: Khách kết nối qua Internet xác thực THÀNH CÔNG!");
                }

                java.awt.Dimension screenSize = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
                String resp = "{\"type\":\"auth_result\",\"success\":true,\"width\":"
                        + screenSize.width + ",\"height\":" + screenSize.height + "}";
                webSocket.sendText(resp, true);

                startStreaming();
            } else {
                if (logger != null) {
                    logger.appendLog("GatewayConnector: Từ chối kết nối qua Internet (Sai mật khẩu).");
                }
                String resp = "{\"type\":\"auth_result\",\"success\":false,\"message\":\"Mật khẩu không chính xác!\"}";
                webSocket.sendText(resp, true);
            }
        } else if (inputHandler != null) {
            inputHandler.handleMessage(json);
        }
    }

    private synchronized void startStreaming() {
        stopStreaming();

        // Gửi khung nhị phân ảnh màn hình qua WebSocket của Gateway
        WebScreenCaster.BinaryFrameSender frameSender = (payload) -> {
            if (webSocket != null && connected) {
                webSocket.sendBinary(ByteBuffer.wrap(payload), true);
            }
        };

        // Gửi văn bản JSON qua WebSocket của Gateway
        WebInputHandler.TextMessageSender textSender = (text) -> {
            if (webSocket != null && connected) {
                webSocket.sendText(text, true);
            }
        };

        this.screenCaster = new WebScreenCaster(frameSender, logger);
        this.inputHandler = new WebInputHandler(textSender, logger, screenCaster);
        new Thread(screenCaster, "Gateway-ScreenCaster-Thread").start();
    }

    private synchronized void stopStreaming() {
        if (screenCaster != null) {
            screenCaster.stop();
            screenCaster = null;
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
