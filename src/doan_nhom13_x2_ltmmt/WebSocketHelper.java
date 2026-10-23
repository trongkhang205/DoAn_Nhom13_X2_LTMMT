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
     * Đọc 1 Frame từ Client theo RFC 6455 (Client bắt buộc phải MASK dữ liệu).
     * Hỗ trợ đầy đủ Fragmented Frames (FIN bit = 0 và các Continuation Frame kế tiếp),
     * giúp các tin nhắn lớn (>64KB) do Chrome / Safari phân mảnh không bị mất hoặc hỏng.
     */
    public static Frame readFrame(InputStream in) throws IOException {
        int b1 = in.read();
        if (b1 == -1) return null;
        boolean fin = (b1 & 0x80) != 0;
        int opcode = b1 & 0x0F;

        byte[] payload = readPayload(in);

        // Khung điều khiển (Close, Ping, Pong) hoặc khung hoàn chỉnh (fin == true)
        if (fin || opcode == OPCODE_CLOSE || opcode == OPCODE_PING || opcode == OPCODE_PONG) {
            return new Frame(opcode, payload);
        }

        // Tái lắp ráp các khung phân mảnh (Continuation frames: opcode 0) cho tới khi fin == true
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(payload.length * 2);
        buffer.write(payload);

        while (!fin) {
            int nb1 = in.read();
            if (nb1 == -1) return null;
            fin = (nb1 & 0x80) != 0;
            int nOpcode = nb1 & 0x0F;

            // Xử lý khung điều khiển xen kẽ (Ping/Close) theo RFC 6455
            if (nOpcode == OPCODE_PING || nOpcode == OPCODE_CLOSE) {
                byte[] ctrlPayload = readPayload(in);
                return new Frame(nOpcode, ctrlPayload);
            }

            byte[] nextPayload = readPayload(in);
            buffer.write(nextPayload);
            if (buffer.size() > 50 * 1024 * 1024) { // Bảo vệ chống DoS tràn RAM (>50MB)
                throw new IOException("Fragmented message payload exceeded 50MB limit");
            }
        }

        return new Frame(opcode, buffer.toByteArray());
    }

    private static byte[] readPayload(InputStream in) throws IOException {
        int b2 = in.read();
        if (b2 == -1) throw new EOFException("Connection closed while reading length");
        boolean masked = (b2 & 0x80) != 0;
        long length = b2 & 0x7F;

        if (length == 126) {
            int d1 = in.read();
            int d2 = in.read();
            if (d1 == -1 || d2 == -1) throw new EOFException("Connection closed while reading length16");
            length = ((d1 & 0xFF) << 8) | (d2 & 0xFF);
        } else if (length == 127) {
            long l = 0;
            for (int i = 0; i < 8; i++) {
                int di = in.read();
                if (di == -1) throw new EOFException("Connection closed while reading length64");
                l = (l << 8) | (di & 0xFF);
            }
            length = l;
        }

        if (length > 20 * 1024 * 1024) { // Bảo vệ chống DoS tràn RAM (>20MB)
            throw new IOException("Frame size too large: " + length);
        }

        byte[] mask = new byte[4];
        if (masked) {
            readFully(in, mask);
        } else {
            // RFC 6455 Mục 5.1: Bắt buộc Client phải Mask toàn bộ khung tin nhắn gửi lên Server
            throw new IOException("RFC 6455 Protocol Error: Khung tin nhắn Client bắt buộc phải có Mask");
        }

        byte[] payload = new byte[(int) length];
        readFully(in, payload);

        if (masked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (payload[i] ^ mask[i % 4]);
            }
        }

        return payload;
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
        int b1 = 0x80 | (opcode & 0x0F); // FIN bit set
        int len = payload.length;
        byte[] header;
        int headerLen;

        if (len <= 125) {
            header = new byte[2];
            header[0] = (byte) b1;
            header[1] = (byte) len;
            headerLen = 2;
        } else if (len <= 65535) {
            header = new byte[4];
            header[0] = (byte) b1;
            header[1] = (byte) 126;
            header[2] = (byte) ((len >> 8) & 0xFF);
            header[3] = (byte) (len & 0xFF);
            headerLen = 4;
        } else {
            header = new byte[10];
            header[0] = (byte) b1;
            header[1] = (byte) 127;
            long longLen = (long) len;
            for (int i = 7; i >= 0; i--) {
                header[2 + (7 - i)] = (byte) ((longLen >>> (i * 8)) & 0xFF);
            }
            headerLen = 10;
        }

        out.write(header, 0, headerLen);
        out.write(payload);
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
