package doan_nhom13_x2_ltmmt;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;

/**
 * Trình hỗ trợ chuẩn RFC 6455 WebSocket thuần Java (Zero-dependency):
 * - Bắt tay Upgrade: websocket với Sec-WebSocket-Key / Sec-WebSocket-Accept
 * - Đọc khung tin nhắn từ Client (Text, Binary, Ping, Close) có Unmask
 * - Gửi khung tin nhắn tới Client (Text, Binary) chuẩn framing
 */
public class WebSocketHelper {

    private static final String WS_MAGIC_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    public static final int OPCODE_CONTINUATION = 0x0;
    public static final int OPCODE_TEXT = 0x1;
    public static final int OPCODE_BINARY = 0x2;
    public static final int OPCODE_CLOSE = 0x8;
    public static final int OPCODE_PING = 0x9;
    public static final int OPCODE_PONG = 0xA;

    public static class Frame {
        public int opcode;
        public byte[] data;

        public Frame(int opcode, byte[] data) {
            this.opcode = opcode;
            this.data = data;
        }

        public String asText() {
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    /**
     * Đọc HTTP Request header từ InputStream byte-by-byte để không tiêu thụ
     * dữ liệu WebSocket frame nằm ngay sau HTTP headers.
     * QUAN TRỌNG: KHÔNG dùng BufferedReader ở đây vì nó có read-ahead buffer
     * sẽ nuốt phần đầu của WebSocket frame đầu tiên, làm hỏng readFrame().
     */
    public static Map<String, String> readHttpHeaders(InputStream in) throws IOException {
        Map<String, String> headers = new HashMap<>();

        // Đọc từng dòng byte-by-byte để tránh BufferedReader đọc vượt qua HTTP headers
        String requestLine = readLineFromStream(in);
        if (requestLine == null) return null;
        headers.put("request-line", requestLine);

        String line;
        while ((line = readLineFromStream(in)) != null && !line.isEmpty()) {
            int idx = line.indexOf(':');
            if (idx != -1) {
                String key = line.substring(0, idx).trim().toLowerCase();
                String val = line.substring(idx + 1).trim();
                headers.put(key, val);
            }
        }
        return headers;
    }

    /**
     * Đọc một dòng từ InputStream byte-by-byte (kết thúc bởi \r\n hoặc \n).
     * Không buffer thêm nên an toàn cho WebSocket upgrade.
     */
    private static String readLineFromStream(InputStream in) throws IOException {
        ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream(256);
        int prev = -1;
        while (true) {
            int b = in.read();
            if (b == -1) {
                // Stream đóng
                if (lineBuffer.size() == 0) return null;
                break;
            }
            if (b == '\n') {
                // Bỏ ký tự \r cuối nếu có
                break;
            }
            if (b != '\r') {
                lineBuffer.write(b);
            }
        }
        return lineBuffer.toString(StandardCharsets.UTF_8.name());
    }

    /**
     * Bắt tay nâng cấp kết nối HTTP sang WebSocket (RFC 6455)
     */
    public static boolean sendHandshake(OutputStream out, String clientKey) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] hash = sha1.digest((clientKey + WS_MAGIC_GUID).getBytes(StandardCharsets.UTF_8));
            String acceptKey = Base64.getEncoder().encodeToString(hash);

            String response = "HTTP/1.1 101 Switching Protocols\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";

            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Đọc 1 Frame từ Client theo RFC 6455 (Client bắt buộc phải MASK dữ liệu)
     */
    public static Frame readFrame(InputStream in) throws IOException {
        int b1 = in.read();
        if (b1 == -1) return null;
        int opcode = b1 & 0x0F;

        int b2 = in.read();
        if (b2 == -1) return null;
        boolean masked = (b2 & 0x80) != 0;
        long length = b2 & 0x7F;

        if (length == 126) {
            int d1 = in.read();
            int d2 = in.read();
            if (d1 == -1 || d2 == -1) return null;
            length = ((d1 & 0xFF) << 8) | (d2 & 0xFF);
        } else if (length == 127) {
            long l = 0;
            for (int i = 0; i < 8; i++) {
                int di = in.read();
                if (di == -1) return null;
                l = (l << 8) | (di & 0xFF);
            }
            length = l;
        }

        byte[] mask = new byte[4];
        if (masked) {
            readFully(in, mask);
        }

        if (length > 20 * 1024 * 1024) { // Bảo vệ chống DoS tràn RAM (>20MB)
            throw new IOException("Frame size too large: " + length);
        }

        byte[] payload = new byte[(int) length];
        readFully(in, payload);

        if (masked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (payload[i] ^ mask[i % 4]);
            }
        }

        return new Frame(opcode, payload);
    }

    /**
     * Gửi khung tin nhắn Text tới Client (Unmasked theo RFC 6455)
     * KHÔNG dùng synchronized ở đây vì lock sẽ là trên class WebSocketHelper,
     * không phải trên OutputStream instance. Đồng bộ hóa được thực hiện
     * bởi syncOut wrapper trong runWebSocketSession.
     */
    public static void sendTextFrame(OutputStream out, String text) throws IOException {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        synchronized (out) {
            writeFrame(out, OPCODE_TEXT, payload);
        }
    }

    /**
     * Gửi khung tin nhắn Binary tới Client (Unmasked theo RFC 6455)
     */
    public static void sendBinaryFrame(OutputStream out, byte[] payload) throws IOException {
        synchronized (out) {
            writeFrame(out, OPCODE_BINARY, payload);
        }
    }

    /**
     * Gửi khung PONG phản hồi PING từ Client (RFC 6455)
     */
    public static void sendPongFrame(OutputStream out, byte[] payload) throws IOException {
        synchronized (out) {
            writeFrame(out, OPCODE_PONG, payload != null ? payload : new byte[0]);
        }
    }


    /**
     * Ghi khung dữ liệu ra OutputStream
     * LƯU Ý QUAN TRỌNG: Phải dùng `long` khi shift cho 8-byte extended length.
     * Trong Java, shift trên `int` bị giới hạn modulo 32 — nên int >>> 56
     * thực ra là int >>> 24, làm sai hoàn toàn 8-byte header của WebSocket frame.
     * Browser sẽ nhận header báo payload = hàng trăm Petabytes → đóng kết nối ngay.
     */
    private static void writeFrame(OutputStream out, int opcode, byte[] payload) throws IOException {
        ByteArrayOutputStream frameBuffer = new ByteArrayOutputStream(payload.length + 10);
        int b1 = 0x80 | (opcode & 0x0F); // FIN bit set
        frameBuffer.write(b1);

        int len = payload.length;
        if (len <= 125) {
            frameBuffer.write(len);
        } else if (len <= 65535) {
            frameBuffer.write(126);
            frameBuffer.write((len >> 8) & 0xFF);
            frameBuffer.write(len & 0xFF);
        } else {
            frameBuffer.write(127);
            // PHẢI cast sang long trước khi shift! int >>> 56 = int >>> (56%32) = int >>> 24 → SAI.
            long longLen = (long) len;
            for (int i = 7; i >= 0; i--) {
                frameBuffer.write((int) ((longLen >>> (i * 8)) & 0xFF));
            }
        }

        frameBuffer.write(payload);
        out.write(frameBuffer.toByteArray());
        out.flush();
    }

    private static void readFully(InputStream in, byte[] b) throws IOException {
        int offset = 0;
        while (offset < b.length) {
            int count = in.read(b, offset, b.length - offset);
            if (count == -1) {
                throw new EOFException("Connection closed while reading frame payload");
            }
            offset += count;
        }
    }
}
