package doan_nhom13_x2_ltmmt;

import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nền tảng Máy Chủ Web Toàn Diện (Full Web Platform Engine - RFC 6455):
 * 1. Đóng vai trò Backend cung cấp toàn bộ giao diện Web trên http://localhost:8080:
 *    - Menu Điều Hướng (Launcher)
 *    - Bảng Điều Khiển Máy Chủ (Server Dashboard trên Web)
 *    - Bảng Kết Nối Máy Khách (Client Dashboard trên Web)
 *    - Màn Hình Điều Khiển (Remote Viewer Canvas)
 * 2. Cung cấp REST API cho Web Frontend (/api/server-info, /api/server-genpass, /api/scan-lan, /api/logs).
 * 3. Quản lý luồng WebSocket (/ws) truyền nhị phân màn hình và nhận lệnh tương tác.
 * 4. Tích hợp Cloud Relay Gateway (Device ID 9 số) và cơ chế chống Brute-Force mật khẩu.
 */
public class WebRemoteServer implements Runnable, ServerLogger {

    private final int port;
    private volatile String password;
    private final ServerLogger externalLogger;
    private volatile boolean running = false;
    private volatile boolean isSharingEnabled = true;

    private ServerSocket serverSocket;
    private DatagramSocket discoverySocket;
    private Thread discoveryThread;

    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private final List<String> memoryLogs = new CopyOnWriteArrayList<>();
    private String localIp = "127.0.0.1";
    private final AtomicInteger activeClients = new AtomicInteger(0);

    // Cloud Relay Gateway & Bảo mật Anti Brute-Force
    private final String deviceId;
    private GatewayConnector gatewayConnector;
    private final Map<String, Integer> failedAttempts = new ConcurrentHashMap<>();
    private final Map<String, Long> lockoutExpiry = new ConcurrentHashMap<>();

    // Cầu nối Internet & Cloudflare Tunnel tích hợp 1-Click
    private Process tunnelProcess;
    private Process gatewayProcess;
    private volatile boolean isTunnelActive = false;
    private volatile String currentTunnelUrl = "";

    public WebRemoteServer(int port, String password, ServerLogger externalLogger) {
        this.port = port;
        this.password = (password != null && !password.trim().isEmpty()) ? password.trim().toLowerCase() : generateRandomPassword();
        this.externalLogger = externalLogger;
        this.localIp = detectLocalIP();
        this.deviceId = generateRandomDeviceId();
        
        // Đảm bảo Cloud Relay Gateway luôn chạy ngầm trên cổng 3000
        ensureGatewayRunning();

        // Tự động kết nối tới Cloud Relay Gateway ở chế độ nền
        String gwUrl = System.getenv("GATEWAY_URL");
        if (gwUrl == null || gwUrl.trim().isEmpty()) {
            gwUrl = System.getProperty("gateway.url", "ws://127.0.0.1:3000");
        }
        this.gatewayConnector = new GatewayConnector(gwUrl, deviceId, () -> this.password, this);
        this.gatewayConnector.start();

        appendLog("Khởi tạo Web Remote Platform trên cổng " + port + ". IP: " + localIp);
        appendLog("Mã Thiết Bị Internet (Device ID): " + GatewayConnector.formatDeviceId(deviceId));
    }

    public static String generateRandomDeviceId() {
        Random rnd = new Random();
        int p1 = 100 + rnd.nextInt(900);
        int p2 = 100 + rnd.nextInt(900);
        int p3 = 100 + rnd.nextInt(900);
        return "" + p1 + p2 + p3;
    }

    public static String generateRandomPassword() {
        String chars = "abcdefghjklmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder();
        Random rnd = new Random();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String detectLocalIP() {
        try {
            String bestIp = null;
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface nif = interfaces.nextElement();
                if (nif.isLoopback() || !nif.isUp()) continue;
                String dName = nif.getDisplayName().toLowerCase();
                String name = nif.getName().toLowerCase();
                if (dName.contains("virtual") || dName.contains("vmware") || dName.contains("vbox")
                        || dName.contains("wsl") || dName.contains("docker") || name.contains("veth")) {
                    continue;
                }
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address) {
                        String ipStr = addr.getHostAddress();
                        if (ipStr.startsWith("192.168.") || ipStr.startsWith("10.") || ipStr.startsWith("172.")) {
                            bestIp = ipStr;
                            break;
                        } else if (bestIp == null) {
                            bestIp = ipStr;
                        }
                    }
                }
                if (bestIp != null && (bestIp.startsWith("192.168.") || bestIp.startsWith("10."))) {
                    break;
                }
            }
            if (bestIp != null) return bestIp;
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    public synchronized void startInternetTunnel() {
        if (isTunnelActive) return;
        isTunnelActive = true;
        currentTunnelUrl = "";
        appendLog("CloudTunnel: Đang kết nối mạng đám mây Cloudflare Argo...");

        new Thread(() -> {
            try {
                ensureGatewayRunning();

                // Đảm bảo GatewayConnector được kết nối và đăng ký Device ID ngay lập tức
                if (gatewayConnector != null) {
                    gatewayConnector.reconnectNow();
                }

                // Dọn dẹp bất kỳ tiến trình cloudflared cũ nào trước khi khởi chạy
                try {
                    new ProcessBuilder("taskkill", "/F", "/IM", "cloudflared.exe").start().waitFor();
                    Thread.sleep(400);
                } catch (Exception ignored) {}

                File cfExe = new File("cloudflared.exe");
                if (!cfExe.exists()) {
                    cfExe = new File("..", "cloudflared.exe");
                }
                if (!cfExe.exists()) {
                    appendLog("CloudTunnel: Không tìm thấy file cloudflared.exe!");
                    isTunnelActive = false;
                    return;
                }

                ProcessBuilder pb = new ProcessBuilder(cfExe.getAbsolutePath(), "tunnel", "--url", "http://localhost:3000");
                pb.redirectErrorStream(true);
                tunnelProcess = pb.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(tunnelProcess.getInputStream()));
                String line;
                Pattern urlPattern = Pattern.compile("https://[a-zA-Z0-9-]+\\.trycloudflare\\.com");
                while ((line = reader.readLine()) != null) {
                    Matcher m = urlPattern.matcher(line);
                    if (m.find()) {
                        currentTunnelUrl = m.group(0);
                        appendLog("CloudTunnel: ĐÃ KÍCH HOẠT! Link Internet 4G: " + currentTunnelUrl);
                        // Đảm bảo host đăng ký chắc chắn trên Gateway
                        if (gatewayConnector != null) {
                            gatewayConnector.reconnectNow();
                        }
                        break;
                    }
                }

                // Tiếp tục đọc log ngầm để tránh đầy buffer ống dẫn (pipe buffer) làm treo cloudflared
                while (isTunnelActive && tunnelProcess != null && tunnelProcess.isAlive()) {
                    if (reader.readLine() == null) break;
                }
            } catch (Exception e) {
                appendLog("CloudTunnel lỗi: " + e.getMessage());
                isTunnelActive = false;
            }
        }, "CloudTunnel-Thread").start();
    }

    public synchronized void stopInternetTunnel() {
        isTunnelActive = false;
        currentTunnelUrl = "";
        if (tunnelProcess != null) {
            try {
                tunnelProcess.destroyForcibly();
                tunnelProcess = null;
            } catch (Exception ignored) {}
        }
        try {
            new ProcessBuilder("taskkill", "/F", "/IM", "cloudflared.exe").start();
        } catch (Exception ignored) {}
        appendLog("CloudTunnel: Đã tắt cầu nối Internet 4G.");
    }

    private synchronized void ensureGatewayRunning() {
        try {
            Socket test = new Socket("127.0.0.1", 3000);
            test.close();
        } catch (Exception notRunning) {
            try {
                File gwDir = new File("gateway");
                if (!gwDir.exists()) gwDir = new File("..", "gateway");
                ProcessBuilder pb = new ProcessBuilder("node", "server.js");
                pb.directory(gwDir);
                pb.redirectErrorStream(true);
                gatewayProcess = pb.start();
                // Đọc ngầm để không bị nghẽn buffer ống dẫn
                new Thread(() -> {
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(gatewayProcess.getInputStream()))) {
                        String l;
                        while ((l = r.readLine()) != null) {}
                    } catch (Exception ignored) {}
                }, "Gateway-Log-Drainer").start();
                appendLog("WebRemoteServer: Tự động khởi động Cloud Gateway trên cổng 3000.");
                Thread.sleep(1500);
            } catch (Exception e) {
                appendLog("Lỗi khởi chạy Gateway: " + e.getMessage());
            }
        }
    }

    @Override
    public void appendLog(String message) {
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String logEntry = "[" + time + "] " + message;
        memoryLogs.add(logEntry);
        // Giới hạn 100 dòng log — dùng subList().clear() để an toàn hơn với CopyOnWriteArrayList
        while (memoryLogs.size() > 100) {
            memoryLogs.remove(0);
        }
        if (externalLogger != null) {
            externalLogger.appendLog(message);
        }
    }

    @Override
    public void run() {
        running = true;
        startDiscoveryResponder();

        try {
            serverSocket = new ServerSocket(port);
            appendLog("Máy chủ Web Platform đã bắt đầu lắng nghe tại: http://" + localIp + ":" + port);

            while (running && !serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.submit(() -> handleClient(clientSocket));
                } catch (IOException e) {
                    if (running) {
                        appendLog("WebRemoteServer accept error: " + e.getMessage());
                    }
                    break;
                }
            }
        } catch (IOException e) {
            appendLog("Không thể mở cổng Web " + port + ": " + e.getMessage());
        } finally {
            stop();
        }
    }

    private void handleClient(Socket socket) {
        try {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            Map<String, String> headers = WebSocketHelper.readHttpHeaders(in);
            if (headers == null) {
                socket.close();
                return;
            }

            String requestLine = headers.get("request-line");
            if (requestLine == null) {
                socket.close();
                return;
            }

            String upgrade = headers.get("upgrade");
            boolean isWebSocket = upgrade != null && upgrade.equalsIgnoreCase("websocket");

            if (isWebSocket) {
                String wsKey = headers.get("sec-websocket-key");
                if (wsKey != null && WebSocketHelper.sendHandshake(out, wsKey)) {
                    appendLog("WebSocket kết nối thành công từ: " + socket.getInetAddress().getHostAddress());
                    runWebSocketSession(socket, in, out);
                } else {
                    socket.close();
                }
            } else {
                handleHttpRequest(requestLine, headers, in, out);
                socket.close();
            }
        } catch (Exception e) {
            try {
                socket.close();
            } catch (Exception ignored) {}
        }
    }

    private void handleHttpRequest(String requestLine, Map<String, String> headers, InputStream in, OutputStream out) throws IOException {
        String[] parts = requestLine.split(" ");
        String method = parts.length > 0 ? parts[0].toUpperCase() : "GET";
        String uri = parts.length > 1 ? parts[1] : "/";

        // 1. API: Thông tin máy chủ
        if (uri.startsWith("/api/server-info")) {
            String json = "{\"ip\":\"" + localIp + "\",\"port\":" + port 
                    + ",\"password\":\"" + password + "\",\"isSharingEnabled\":" + isSharingEnabled 
                    + ",\"activeClients\":" + activeClients.get()
                    + ",\"deviceId\":\"" + deviceId + "\",\"formattedDeviceId\":\"" + GatewayConnector.formatDeviceId(deviceId) + "\""
                    + ",\"isTunnelActive\":" + isTunnelActive
                    + ",\"tunnelUrl\":\"" + (currentTunnelUrl != null ? currentTunnelUrl : "") + "\"}";
            sendJsonResponse(out, json);
            return;
        }

        // 2. API: Bật/Tắt Cầu Nối Internet 4G (Cloudflare Tunnel Tích Hợp)
        if (uri.startsWith("/api/tunnel-toggle")) {
            if (isTunnelActive) {
                stopInternetTunnel();
            } else {
                startInternetTunnel();
            }
            sendJsonResponse(out, "{\"success\":true,\"isTunnelActive\":" + isTunnelActive + ",\"tunnelUrl\":\"" + (currentTunnelUrl != null ? currentTunnelUrl : "") + "\"}");
            return;
        }

        // 3. API: Sinh mật khẩu mới
        if (uri.startsWith("/api/server-genpass")) {
            this.password = generateRandomPassword();
            appendLog("Đã đổi mật khẩu máy chủ mới: " + this.password);
            sendJsonResponse(out, "{\"success\":true,\"password\":\"" + this.password + "\"}");
            return;
        }

        // 4. API: Bật/Tắt chia sẻ màn hình
        if (uri.startsWith("/api/server-toggle")) {
            this.isSharingEnabled = !this.isSharingEnabled;
            appendLog("Chế độ chia sẻ màn hình đã " + (isSharingEnabled ? "BẬT" : "TẮT"));
            sendJsonResponse(out, "{\"success\":true,\"isSharingEnabled\":" + isSharingEnabled + "}");
            return;
        }

        // 4. API: Lấy danh sách Logs
        if (uri.startsWith("/api/server-logs")) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < memoryLogs.size(); i++) {
                sb.append("\"").append(escapeJson(memoryLogs.get(i))).append("\"");
                if (i < memoryLogs.size() - 1) sb.append(",");
            }
            sb.append("]");
            sendJsonResponse(out, sb.toString());
            return;
        }

        // 5. API: Xóa nhật ký máy chủ
        if (uri.startsWith("/api/server-clearlogs")) {
            memoryLogs.clear();
            appendLog("Đã xóa toàn bộ nhật ký máy chủ.");
            sendJsonResponse(out, "{\"success\":true}");
            return;
        }

        // 6. API: Quét tìm máy chủ trong mạng LAN qua UDP Broadcast
        if (uri.startsWith("/api/scan-lan")) {
            String scannedJson = scanLanServers();
            sendJsonResponse(out, scannedJson);
            return;
        }

        // Phục vụ tệp tĩnh Web (HTML, CSS, JS)
        serveHttpStatic(uri, out);
    }

    private void sendJsonResponse(OutputStream out, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json; charset=UTF-8\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Cache-Control: no-cache\r\n"
                + "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }

    /**
     * Tìm thư mục web/ theo thứ tự ưu tiên:
     * 1. Bên cạnh file JAR nếu đóng gói
     * 2. Bên cạnh thư mục build/classes nếu chạy từ NetBeans/IDE
     * 3. Thư mục hiện tại (fallback)
     */
    private static Path resolveWebDir() {
        try {
            Path codeLocation = Paths.get(
                WebRemoteServer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            // Đi lên đến 3 cấp thư mục cha để tìm web/
            Path candidate = codeLocation;
            for (int i = 0; i < 4; i++) {
                if (candidate == null) break;
                Path webCandidate = candidate.resolve("web");
                if (Files.isDirectory(webCandidate)) return webCandidate;
                candidate = candidate.getParent();
            }
        } catch (Exception ignored) {}
        // Fallback: thư mục hiện tại
        return Paths.get("web");
    }

    private void serveHttpStatic(String uri, OutputStream out) throws IOException {
        if (uri.equals("/") || uri.startsWith("/?")) {
            uri = "/index.html";
        }

        // Resolve đường dẫn web/ theo vị trí thực tế của file class/JAR
        // để tránh lỗi 404 khi chạy từ thư mục khác
        Path webDir = resolveWebDir();
        String relativePath = uri.startsWith("/") ? uri.substring(1) : uri;
        int queryIdx = relativePath.indexOf('?');
        if (queryIdx != -1) {
            relativePath = relativePath.substring(0, queryIdx);
        }

        Path targetFile = webDir.resolve(relativePath).normalize();

        if (Files.exists(targetFile) && !Files.isDirectory(targetFile)) {
            byte[] fileBytes = Files.readAllBytes(targetFile);
            String contentType = getContentType(targetFile.toString());

            String header = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: " + contentType + "\r\n"
                    + "Content-Length: " + fileBytes.length + "\r\n"
                    + "Cache-Control: no-cache\r\n"
                    + "Connection: close\r\n\r\n";

            out.write(header.getBytes(StandardCharsets.UTF_8));
            out.write(fileBytes);
            out.flush();
        } else {
            String notFound = "HTTP/1.1 404 Not Found\r\nContent-Type: text/plain\r\nContent-Length: 9\r\n\r\nNot Found";
            out.write(notFound.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    private String getContentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=UTF-8";
        if (path.endsWith(".css")) return "text/css; charset=UTF-8";
        if (path.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    /**
     * Quét các Server trong mạng LAN bằng gói UDP Broadcast
     */
    private String scanLanServers() {
        List<String> foundList = new ArrayList<>();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(1200);

            byte[] sendData = "DISCOVER_SERVER".getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, 
                    InetAddress.getByName("255.255.255.255"), 8888);
            socket.send(sendPacket);

            byte[] recvBuf = new byte[1024];
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 1200) {
                try {
                    DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);
                    socket.receive(recvPacket);
                    String resp = new String(recvPacket.getData(), 0, recvPacket.getLength()).trim();
                    if (resp.startsWith("SERVER_FOUND|")) {
                        String[] p = resp.split("\\|");
                        String name = p.length > 1 ? p[1] : "Máy Chủ";
                        String ip = p.length > 2 ? p[2] : recvPacket.getAddress().getHostAddress();
                        String portStr = p.length > 3 ? p[3] : "8080";
                        foundList.add("{\"name\":\"" + escapeJson(name) + "\",\"ip\":\"" + ip + "\",\"port\":" + portStr + "}");
                    }
                } catch (SocketTimeoutException e) {
                    break;
                }
            }
        } catch (Exception ignored) {}

        // Luôn bổ sung thêm máy hiện tại nếu đang bật
        if (foundList.isEmpty()) {
            foundList.add("{\"name\":\"Máy này (Localhost)\",\"ip\":\"127.0.0.1\",\"port\":" + port + "}");
            if (!"127.0.0.1".equals(localIp)) {
                foundList.add("{\"name\":\"Máy này (Mạng LAN)\",\"ip\":\"" + localIp + "\",\"port\":" + port + "}");
            }
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < foundList.size(); i++) {
            sb.append(foundList.get(i));
            if (i < foundList.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    private void startDiscoveryResponder() {
        try {
            discoverySocket = new DatagramSocket(8888);
            discoveryThread = new Thread(() -> {
                byte[] buffer = new byte[1024];
                while (running && discoverySocket != null && !discoverySocket.isClosed()) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        discoverySocket.receive(packet);
                        String msg = new String(packet.getData(), 0, packet.getLength()).trim();
                        if ("DISCOVER_SERVER".equals(msg)) {
                            String hostName = InetAddress.getLocalHost().getHostName();
                            String response = "SERVER_FOUND|" + hostName + "|" + localIp + "|" + port;
                            byte[] respBytes = response.getBytes();
                            DatagramPacket respPacket = new DatagramPacket(respBytes, respBytes.length, packet.getAddress(), packet.getPort());
                            discoverySocket.send(respPacket);
                        }
                    } catch (IOException e) {
                        break;
                    }
                }
            }, "Web-LAN-Discovery-Responder");
            discoveryThread.setDaemon(true);
            discoveryThread.start();
        } catch (Exception ex) {
            appendLog("Kênh UDP 8888 đã được ứng dụng khác sử dụng hoặc không khả dụng: " + ex.getMessage());
        }
    }

    private void runWebSocketSession(Socket socket, InputStream in, OutputStream out) {
        // Đặt SO_TIMEOUT để readFrame() không block vô hạn khi client mất kết nối.
        // Nếu không có dữ liệu trong 15 giây, SocketTimeoutException sẽ được ném
        // và ta kiểm tra xem screenCaster còn chạy không trước khi tiếp tục.
        try {
            socket.setSoTimeout(15000);
        } catch (Exception ignored) {}

        final boolean[] authenticated = {false};

        // Giữ ref để dùng trong anonymous inner classes
        final WebScreenCaster[] screenCasterRef = {null};
        final WebInputHandler[] inputHandlerRef = {null};

        try {
            while (running && !socket.isClosed()) {
                WebSocketHelper.Frame frame;
                try {
                    frame = WebSocketHelper.readFrame(in);
                } catch (java.net.SocketTimeoutException ste) {
                    // Timeout bình thường - kiểm tra xem screenCaster còn sống không
                    if (authenticated[0] && screenCasterRef[0] != null && !screenCasterRef[0].isRunning()) {
                        break; // ScreenCaster đã chết → đóng phiên
                    }
                    continue; // Chưa auth hoặc caster vẫn chạy → tiếp tục chờ
                } catch (Exception e) {
                    break; // Kết nối đóng hoặc lỗi thực sự
                }

                if (frame == null || frame.opcode == WebSocketHelper.OPCODE_CLOSE) {
                    break;
                }

                // Xử lý PING từ client → phản hồi PONG
                if (frame.opcode == WebSocketHelper.OPCODE_PING) {
                    try {
                        WebSocketHelper.sendPongFrame(out, frame.data);
                    } catch (Exception ignored) {}
                    continue;
                }

                if (frame.opcode != WebSocketHelper.OPCODE_TEXT) {
                    continue; // Bỏ qua các opcode không phải TEXT
                }

                String json = frame.asText();
                Map<String, String> data = WebInputHandler.parseSimpleJson(json);
                String type = data.get("type");

                if (!authenticated[0]) { // chưa xác thực
                    if ("auth".equals(type)) {
                        String clientIp = socket.getInetAddress().getHostAddress();

                        // Kiểm tra trạng thái khóa IP do brute-force
                        Long expiry = lockoutExpiry.get(clientIp);
                        if (expiry != null) {
                            if (System.currentTimeMillis() < expiry) {
                                long remainingSec = (expiry - System.currentTimeMillis()) / 1000;
                                String resp = "{\"type\":\"auth_result\",\"success\":false,\"message\":\"[BẢO MẬT] Địa chỉ IP này bị khóa tạm " + remainingSec + " giây do nhập sai mật khẩu quá 5 lần!\"}";
                                WebSocketHelper.sendTextFrame(out, resp);
                                appendLog("[CẢNH BÁO BẢO MẬT] Chặn IP " + clientIp + " (Đang bị khóa do nghi vấn Brute-Force)");
                                break;
                            } else {
                                lockoutExpiry.remove(clientIp);
                                failedAttempts.remove(clientIp);
                            }
                        }

                        if (!isSharingEnabled) {
                            String resp = "{\"type\":\"auth_result\",\"success\":false,\"message\":\"Máy chủ hiện đang TẮT chế độ chia sẻ màn hình!\"}";
                            WebSocketHelper.sendTextFrame(out, resp);
                            appendLog("Từ chối kết nối từ " + clientIp + " (Chia sẻ đang tắt)");
                            break;
                        }

                        String clientPass = data.get("password");
                        if (password != null && password.equalsIgnoreCase(clientPass != null ? clientPass.trim() : "")) {
                            // Xóa lịch sử sai mật khẩu
                            failedAttempts.remove(clientIp);
                            lockoutExpiry.remove(clientIp);

                            authenticated[0] = true;
                            activeClients.incrementAndGet();
                            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                            String resp = "{\"type\":\"auth_result\",\"success\":true,\"width\":"
                                    + screenSize.width + ",\"height\":" + screenSize.height + "}";
                            WebSocketHelper.sendTextFrame(out, resp);
                            appendLog("Web Client (" + clientIp + ") xác thực THÀNH CÔNG! Đang truyền màn hình...");

                            // Khởi động ScreenCaster trên luồng riêng
                            screenCasterRef[0] = new WebScreenCaster(out, this);
                            new Thread(screenCasterRef[0], "WebScreenCaster-Thread").start();
                            inputHandlerRef[0] = new WebInputHandler(out, this, screenCasterRef[0]);
                        } else {
                            int fails = failedAttempts.getOrDefault(clientIp, 0) + 1;
                            failedAttempts.put(clientIp, fails);

                            if (fails >= 5) {
                                lockoutExpiry.put(clientIp, System.currentTimeMillis() + 5 * 60 * 1000);
                                appendLog("[CẢNH BÁO BẢO MẬT] ĐÃ KHÓA TẠM IP " + clientIp + " trong 5 phút do nhập sai mật khẩu liên tiếp " + fails + " lần!");
                                String resp = "{\"type\":\"auth_result\",\"success\":false,\"message\":\"[BẢO MẬT] Bạn đã nhập sai quá 5 lần! IP của bạn bị khóa tạm thời trong 5 phút.\"}";
                                WebSocketHelper.sendTextFrame(out, resp);
                            } else {
                                String resp = "{\"type\":\"auth_result\",\"success\":false,\"message\":\"Mật khẩu không chính xác! (Còn " + (5 - fails) + " lần thử)\"}";
                                WebSocketHelper.sendTextFrame(out, resp);
                                appendLog("Từ chối kết nối từ " + clientIp + " (Sai mật khẩu lần " + fails + "/5)");
                            }
                            break;
                        }
                    }
                } else {
                    if (inputHandlerRef[0] != null) {
                        inputHandlerRef[0].handleMessage(json);
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (authenticated[0]) {
                activeClients.decrementAndGet();
            }
            if (screenCasterRef[0] != null) {
                screenCasterRef[0].stop();
            }
            try {
                socket.close();
            } catch (Exception ignored2) {}
            appendLog("Đã đóng phiên kết nối Web Client (" + socket.getInetAddress().getHostAddress() + ")");
        }
    }

    public void stop() {
        running = false;
        stopInternetTunnel();
        if (gatewayConnector != null) {
            gatewayConnector.stop();
        }
        if (gatewayProcess != null) {
            try {
                gatewayProcess.destroyForcibly();
                gatewayProcess = null;
            } catch (Exception ignored) {}
        }
        try {
            if (discoverySocket != null && !discoverySocket.isClosed()) {
                discoverySocket.close();
                discoverySocket = null;
            }
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception ignored) {}
        threadPool.shutdownNow();
    }

    public boolean isRunning() {
        return running;
    }

    public String getPassword() {
        return password;
    }

    public String getLocalIp() {
        return localIp;
    }

    public int getPort() {
        return port;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }

    /**
     * Điểm khởi chạy độc lập thuần Web Platform (Không cần giao diện Swing Desktop)
     */
    public static void main(String[] args) {
        WebRemoteServer server = new WebRemoteServer(8080, null, null);
        new Thread(server, "Main-WebRemoteServer").start();

        System.out.println("==================================================================");
        System.out.println("  HE THONG DIEU KHIEN MAY TINH TU XA NEN TANG WEB - NHOM 13");
        System.out.println("  Dia chi Web Platform: http://localhost:8080");
        System.out.println("  Mat khau mac dinh:     " + server.getPassword());
        System.out.println("==================================================================");

        try {
            Desktop.getDesktop().browse(new URI("http://localhost:8080"));
        } catch (Exception ignored) {}
    }
}
