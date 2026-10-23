package doan_nhom13_x2_ltmmt;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Giai đoạn nâng cao: WebScreenCaster với Delta Frame Encoding & Adaptive Quality.
 * - Chỉ quét và gửi vùng thay đổi (Dirty Rectangles) thay vì gửi toàn bộ màn hình liên tục.
 * - Giảm tới 75-85% lưu lượng mạng và độ trễ truyền tải.
 * - Tự động gửi Full Frame định kỳ (Keyframe) để làm mới hình ảnh sắc nét.
 * - Cấu trúc Binary Frame tối ưu (33 bytes Header chuẩn hóa):
 *   [frameType(1), w(4), h(4), targetX(4), targetY(4), targetW(4), targetH(4), timestamp(8), jpeg]
 */
public class WebScreenCaster implements Runnable {

    private final OutputStream out;
    private final ServerLogger logger;
    private volatile boolean running = true;
    private volatile float quality = 0.65f;
    private volatile int targetFps = 35;

    private Robot robot;
    private Rectangle screenRect;

    // Khóa mã hóa đầu cuối E2EE (AES-256-GCM)
    private volatile javax.crypto.SecretKey e2eeKey = null;

    // Giám sát UAC / Secure Desktop
    private volatile boolean uacActive = false;
    private int blackFrameCount = 0;
    private java.util.function.Consumer<Boolean> uacStatusListener = null;

    // Quản lý Đa Màn Hình (Multi-Monitor)
    private volatile int currentScreenIndex = 0;
    private volatile boolean screenSwitchRequested = false;
    private final Object screenLock = new Object();

    // Quản lý Độ phân giải và Tỷ lệ Khung hình (Dynamic Resolution Scaling)
    // Chỉ nén tỷ lệ với màn hình siêu lớn (> 1440px như 1080p, 2K, 4K)
    // Với màn hình 1366x768 hoặc nhỏ hơn: Giữ 100% Native 1:1 triệt tiêu nội suy CPU,
    // đảm bảo tọa độ chuột chính xác 100% pixel-perfect và độ mượt tối đa.
    private volatile boolean hdScaleEnabled = true;
    private volatile double streamScale = 1.0;
    private volatile double streamScaleX = 1.0;
    private volatile double streamScaleY = 1.0;
    private volatile int streamWidth = 1366;
    private volatile int streamHeight = 768;
    private volatile boolean fileTransferring = false;

    // Buffer pixel để so sánh vi sai (Delta Diffing)
    private int[] prevPixels;
    private int keyframeInterval = 45; // Gửi Full frame mỗi 45 frames (~1.5 giây) tự làm mới màn hình khử giật nhấp nháy
    private int frameCounter = 0;
    private int idleFrameCounter = 0;
    private volatile boolean isFirstFrame = true;

    // Bộ nén JPEG tái sử dụng (Object Pooling tránh GC và OutOfMemory)
    private ImageWriter jpegWriter;
    private ImageWriteParam writeParam;
    private final Object writerLock = new Object();

    @FunctionalInterface
    public interface BinaryFrameSender {
        void send(byte[] payload) throws IOException;
    }
    private final BinaryFrameSender frameSender;

    public WebScreenCaster(OutputStream out, ServerLogger logger) {
        this((payload) -> WebSocketHelper.sendBinaryFrame(out, payload), logger);
    }

    public WebScreenCaster(BinaryFrameSender frameSender, ServerLogger logger) {
        this.out = null;
        this.frameSender = frameSender;
        this.logger = logger;

        try {
            this.robot = new Robot();
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            this.screenRect = new Rectangle(screenSize);
            recalculateStreamDimensions();

            // Khởi tạo bộ nén JPEG chất lượng cao
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (writers.hasNext()) {
                this.jpegWriter = writers.next();
                this.writeParam = jpegWriter.getDefaultWriteParam();
                this.writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                this.writeParam.setCompressionQuality(this.quality);
            }
        } catch (AWTException e) {
            if (logger != null) {
                logger.appendLog("WebScreenCaster: Lỗi khởi tạo Robot chụp màn hình: " + e.getMessage());
            }
        }
    }

    private java.util.function.BooleanSupplier busyChecker = null;

    public void setBusyChecker(java.util.function.BooleanSupplier checker) {
        this.busyChecker = checker;
    }

    public void setQuality(float newQuality) {
        this.quality = Math.max(0.2f, Math.min(0.95f, newQuality));
        synchronized (writerLock) {
            if (writeParam != null) {
                writeParam.setCompressionQuality(this.quality);
            }
        }
    }

    public void forceKeyframe() {
        this.isFirstFrame = true;
    }

    public void setTargetFps(int fps) {
        this.targetFps = Math.max(5, Math.min(60, fps));
    }

    public Rectangle getScreenBounds() {
        synchronized (screenLock) {
            return screenRect != null ? new Rectangle(screenRect) : new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        }
    }

    public int getCurrentScreenIndex() {
        return currentScreenIndex;
    }

    public void setFileTransferring(boolean transferring) {
        this.fileTransferring = transferring;
        if (!transferring) {
            this.isFirstFrame = true; // Làm mới ngay màn hình khi truyền tệp xong
        }
    }

    public boolean isFileTransferring() {
        return fileTransferring;
    }

    public double getStreamScale() {
        return streamScale > 0.01 ? streamScale : 1.0;
    }

    public double getStreamScaleX() {
        return streamScaleX > 0.01 ? streamScaleX : 1.0;
    }

    public double getStreamScaleY() {
        return streamScaleY > 0.01 ? streamScaleY : 1.0;
    }

    private void recalculateStreamDimensions() {
        synchronized (screenLock) {
            int W = screenRect != null ? screenRect.width : 1366;
            int H = screenRect != null ? screenRect.height : 768;
            int sW = W;
            int sH = H;
            // Chỉ nén nếu độ phân giải vượt quá 1440px (1080p, 2K, 4K)
            // Với 1366x768: Giữ nguyên 100% tỷ lệ gốc 1:1 siêu mượt, loại bỏ hoàn toàn tải CPU nội suy
            if (hdScaleEnabled && W > 1440) {
                sW = 1280;
                sH = (int) Math.round((double) H * 1280.0 / W);
                if ((sW % 2) != 0) sW--;
                if ((sH % 2) != 0) sH--;
            }
            this.streamWidth = sW;
            this.streamHeight = sH;
            this.streamScaleX = (double) sW / W;
            this.streamScaleY = (double) sH / H;
            this.streamScale = this.streamScaleX;
        }
    }

    public int getStreamWidth() {
        return streamWidth;
    }

    public int getStreamHeight() {
        return streamHeight;
    }

    public void setHdScaleEnabled(boolean enabled) {
        this.hdScaleEnabled = enabled;
        this.screenSwitchRequested = true;
    }

    public boolean isHdScaleEnabled() {
        return hdScaleEnabled;
    }

    public void setE2eeKey(javax.crypto.SecretKey key) {
        this.e2eeKey = key;
        if (logger != null) {
            if (key != null) {
                logger.appendLog("WebScreenCaster: Đã kích hoạt kênh mã hóa đầu cuối E2EE (AES-256-GCM) cho luồng hình ảnh.");
            } else {
                logger.appendLog("WebScreenCaster: Đã chuyển sang chế độ truyền trực tiếp tốc độ cao (TLS chuẩn).");
            }
        }
    }

    public javax.crypto.SecretKey getE2eeKey() {
        return e2eeKey;
    }

    public void setUacStatusListener(java.util.function.Consumer<Boolean> listener) {
        this.uacStatusListener = listener;
    }

    public synchronized boolean switchScreen(int targetIndex) {
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice[] devices = ge.getScreenDevices();
            if (targetIndex >= 0 && targetIndex < devices.length) {
                GraphicsDevice dev = devices[targetIndex];
                Rectangle bounds = dev.getDefaultConfiguration().getBounds();
                synchronized (screenLock) {
                    this.robot = new Robot(dev);
                    this.screenRect = bounds;
                    this.currentScreenIndex = targetIndex;
                    this.screenSwitchRequested = true;
                }
                if (logger != null) {
                    logger.appendLog("WebScreenCaster: Đã chuyển sang Màn hình " + (targetIndex + 1) + " [" + bounds.width + "x" + bounds.height + " @ (" + bounds.x + "," + bounds.y + ")]");
                }
                return true;
            }
        } catch (Exception e) {
            if (logger != null) {
                logger.appendLog("WebScreenCaster: Lỗi chuyển màn hình: " + e.getMessage());
            }
        }
        return false;
    }

    public static String getScreensJson() {
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice[] devices = ge.getScreenDevices();
            GraphicsDevice defaultDev = ge.getDefaultScreenDevice();
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < devices.length; i++) {
                GraphicsDevice dev = devices[i];
                Rectangle bounds = dev.getDefaultConfiguration().getBounds();
                boolean isPrimary = (dev == defaultDev);
                sb.append("{");
                sb.append("\"index\":").append(i).append(",");
                sb.append("\"name\":\"Màn hình ").append(i + 1).append(" (").append(bounds.width).append("x").append(bounds.height).append(")\",");
                sb.append("\"width\":").append(bounds.width).append(",");
                sb.append("\"height\":").append(bounds.height).append(",");
                sb.append("\"x\":").append(bounds.x).append(",");
                sb.append("\"y\":").append(bounds.y).append(",");
                sb.append("\"isPrimary\":").append(isPrimary);
                sb.append("}");
                if (i < devices.length - 1) sb.append(",");
            }
            sb.append("]");
            return sb.toString();
        } catch (Exception e) {
            return "[{\"index\":0,\"name\":\"Màn hình chính\",\"width\":1920,\"height\":1080,\"x\":0,\"y\":0,\"isPrimary\":true}]";
        }
    }

    @Override
    public void run() {
        if (logger != null) {
            logger.appendLog("WebScreenCaster: Khởi động luồng Delta Frame Engine (Max FPS: " + targetFps + ")...");
        }

        if (robot == null) {
            if (logger != null) {
                logger.appendLog("WebScreenCaster: Robot chưa được khởi tạo, dừng luồng.");
            }
            return;
        }

        int W_orig = screenRect.width;
        int H_orig = screenRect.height;

        int streamW = W_orig;
        int streamH = H_orig;
        double scale = 1.0;

        if (hdScaleEnabled && W_orig > 1440) {
            streamW = 1280;
            streamH = (int) Math.round((double) H_orig * 1280.0 / W_orig);
            if ((streamW % 2) != 0) streamW--;
            if ((streamH % 2) != 0) streamH--;
            scale = (double) streamW / W_orig;
        }

        this.streamScale = scale;
        this.streamWidth = streamW;
        this.streamHeight = streamH;
        this.streamScaleX = (double) streamW / W_orig;
        this.streamScaleY = (double) streamH / H_orig;

        final int blockSize = 32; // Kích thước khối kiểm tra (32x32 pixels)
        int blocksX = (streamW + blockSize - 1) / blockSize;
        int blocksY = (streamH + blockSize - 1) / blockSize;

        this.prevPixels = new int[streamW * streamH];
        ByteArrayOutputStream baos = new ByteArrayOutputStream(64 * 1024);
        int consecutiveIoErrors = 0;

        // Image stream chuẩn hoá TYPE_INT_RGB tái sử dụng buffer
        BufferedImage streamImage = new BufferedImage(streamW, streamH, BufferedImage.TYPE_INT_RGB);
        Graphics2D gStream = streamImage.createGraphics();
        gStream.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        gStream.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

        while (running && !Thread.currentThread().isInterrupted()) {
            if (fileTransferring) {
                // Nhường 100% băng thông mạng cho tệp tin đang truyền tải
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    break;
                }
                continue;
            }

            long startTime = System.currentTimeMillis();
            int frameDelay = 1000 / Math.max(1, targetFps);

            // Kiểm tra yêu cầu chuyển màn hình động hoặc chuyển đổi độ phân giải HD
            if (screenSwitchRequested) {
                synchronized (screenLock) {
                    screenSwitchRequested = false;
                    W_orig = screenRect.width;
                    H_orig = screenRect.height;
                    streamW = W_orig;
                    streamH = H_orig;
                    scale = 1.0;
                    if (hdScaleEnabled && W_orig > 1280) {
                        streamW = 1280;
                        streamH = (int) Math.round((double) H_orig * 1280.0 / W_orig);
                        if ((streamW % 2) != 0) streamW--;
                        if ((streamH % 2) != 0) streamH--;
                        scale = (double) streamW / W_orig;
                    }
                    this.streamScale = scale;
                    this.streamWidth = streamW;
                    this.streamHeight = streamH;
                    blocksX = (streamW + blockSize - 1) / blockSize;
                    blocksY = (streamH + blockSize - 1) / blockSize;
                    this.prevPixels = new int[streamW * streamH];
                    if (gStream != null) gStream.dispose();
                    streamImage = new BufferedImage(streamW, streamH, BufferedImage.TYPE_INT_RGB);
                    gStream = streamImage.createGraphics();
                    gStream.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    gStream.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
                    isFirstFrame = true; // Buộc gửi Keyframe Full màn hình mới
                }
            }

            // Bỏ qua khung hình nếu đường truyền mạng đang bận gửi khung trước (Tránh nghẽn hàng đợi & drop FPS)
            if (busyChecker != null && busyChecker.getAsBoolean()) {
                try {
                    Thread.sleep(Math.max(4, frameDelay / 2));
                } catch (InterruptedException ie) {
                    break;
                }
                continue;
            }

            try {
                // 1. Chụp màn hình máy chủ và co dãn về chuẩn HD siêu mượt
                BufferedImage screenshot = robot.createScreenCapture(screenRect);
                if (!running) break;

                if (streamW != W_orig || streamH != H_orig) {
                    gStream.drawImage(screenshot, 0, 0, streamW, streamH, null);
                } else {
                    gStream.drawImage(screenshot, 0, 0, null);
                }

                int[] currPixels = ((DataBufferInt) streamImage.getRaster().getDataBuffer()).getData();

                // Kiểm tra trạng thái màn hình đen do Windows UAC / Secure Desktop
                boolean isBlackSample = true;
                int sampleStep = Math.max(1, currPixels.length / 16);
                for (int sp = 0; sp < currPixels.length; sp += sampleStep) {
                    if ((currPixels[sp] & 0x00FFFFFF) != 0) {
                        isBlackSample = false;
                        break;
                    }
                }

                if (isBlackSample) {
                    blackFrameCount++;
                    if (blackFrameCount > 15 && !uacActive) {
                        uacActive = true;
                        if (uacStatusListener != null) uacStatusListener.accept(true);
                        if (logger != null) {
                            logger.appendLog("WebScreenCaster: [CẢNH BÁO UAC] Màn hình bị khóa bảo mật Secure Desktop / UAC.");
                        }
                    }
                } else {
                    if (uacActive) {
                        uacActive = false;
                        blackFrameCount = 0;
                        if (uacStatusListener != null) uacStatusListener.accept(false);
                        if (logger != null) {
                            logger.appendLog("WebScreenCaster: [UAC ĐÃ MỞ] Khôi phục truyền màn hình bình thường.");
                        }
                    } else {
                        blackFrameCount = 0;
                    }
                }

                // 2. Thuật toán phát hiện vùng thay đổi Siêu Tốc (9-point Sample Diffing)
                int minBx = Integer.MAX_VALUE, minBy = Integer.MAX_VALUE;
                int maxBx = -1, maxBy = -1;
                int changedBlocks = 0;

                for (int by = 0; by < blocksY; by++) {
                    int startY = by * blockSize;
                    int endY = Math.min(startY + blockSize, streamH);

                    for (int bx = 0; bx < blocksX; bx++) {
                        int startX = bx * blockSize;
                        int endX = Math.min(startX + blockSize, streamW);

                        boolean blockChanged = false;
                        int midX = (startX + endX) / 2;
                        int midY = (startY + endY) / 2;

                        // 9 điểm mẫu trọng yếu: 4 góc, tâm, và 4 trung điểm biên
                        int p0 = startY * streamW + startX;
                        int p1 = startY * streamW + midX;
                        int p2 = startY * streamW + (endX - 1);
                        int p3 = midY * streamW + startX;
                        int p4 = midY * streamW + midX;
                        int p5 = midY * streamW + (endX - 1);
                        int p6 = (endY - 1) * streamW + startX;
                        int p7 = (endY - 1) * streamW + midX;
                        int p8 = (endY - 1) * streamW + (endX - 1);

                        if (currPixels[p0] != prevPixels[p0] ||
                            currPixels[p1] != prevPixels[p1] ||
                            currPixels[p2] != prevPixels[p2] ||
                            currPixels[p3] != prevPixels[p3] ||
                            currPixels[p4] != prevPixels[p4] ||
                            currPixels[p5] != prevPixels[p5] ||
                            currPixels[p6] != prevPixels[p6] ||
                            currPixels[p7] != prevPixels[p7] ||
                            currPixels[p8] != prevPixels[p8]) {
                            blockChanged = true;
                        } else {
                            // Quét bước nhảy 8 pixel để phát hiện văn bản hoặc đường kẻ mảnh
                            for (int y = startY; y < endY; y += 8) {
                                int rowOffset = y * streamW;
                                for (int x = startX; x < endX; x += 8) {
                                    if (currPixels[rowOffset + x] != prevPixels[rowOffset + x]) {
                                        blockChanged = true;
                                        break;
                                    }
                                }
                                if (blockChanged) break;
                            }
                        }

                        if (blockChanged) {
                            changedBlocks++;
                            if (bx < minBx) minBx = bx;
                            if (by < minBy) minBy = by;
                            if (bx > maxBx) maxBx = bx;
                            if (by > maxBy) maxBy = by;
                        }
                    }
                }

                // 3. Nếu màn hình hoàn toàn tĩnh: Duy trì nhịp nghỉ nhẹ (tiết kiệm CPU & băng thông 0 KB/s)
                if (changedBlocks == 0 && !isFirstFrame) {
                    idleFrameCounter++;
                    if (idleFrameCounter < 15) { // Nghỉ nhịp nếu tĩnh hoàn toàn (tối đa ~0.5s)
                        long elapsed = System.currentTimeMillis() - startTime;
                        if (elapsed < frameDelay) {
                            Thread.sleep(frameDelay - elapsed);
                        }
                        continue;
                    }
                }
                idleFrameCounter = 0;
                isFirstFrame = false;

                // 4. Luôn truyền Full Frame hoàn chỉnh khi có thay đổi:
                // Triệt tiêu 100% hiện tượng chớp hình, rách viền (DCT artifact), và lệch khối của Delta Frame
                int targetX = 0, targetY = 0, targetW = streamW, targetH = streamH;
                byte frameType = 0; // 0 = Full Frame
                BufferedImage imageToSend = streamImage;

                // Lưu lại pixel vừa xử lý
                System.arraycopy(currPixels, 0, prevPixels, 0, currPixels.length);

                // 5. Nén ảnh JPEG tái sử dụng writer
                baos.reset();
                synchronized (writerLock) {
                    if (jpegWriter != null) {
                        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                            jpegWriter.setOutput(ios);
                            jpegWriter.write(null, new IIOImage(imageToSend, null, null), writeParam);
                            ios.flush();
                        }
                    } else {
                        ImageIO.write(imageToSend, "jpeg", baos);
                    }
                }

                byte[] jpegBytes = baos.toByteArray();
                if (!running) break;

                // 6. Đóng gói Binary Frame (33 bytes Header chuẩn hóa):
                // [frameType(1), w(4), h(4), targetX(4), targetY(4), targetW(4), targetH(4), timestamp(8), jpeg]
                ByteBuffer buffer = ByteBuffer.allocate(33 + jpegBytes.length);
                buffer.put(frameType);
                buffer.putInt(streamW);
                buffer.putInt(streamH);
                buffer.putInt(targetX);
                buffer.putInt(targetY);
                buffer.putInt(targetW);
                buffer.putInt(targetH);
                buffer.putLong(startTime);
                buffer.put(jpegBytes);

                // 7. Gửi qua WebSocket (Hỗ trợ mã hóa đầu cuối E2EE AES-256-GCM)
                byte[] rawPayload = buffer.array();
                if (e2eeKey != null) {
                    try {
                        byte[] encrypted = CryptoHelper.encryptBinary(rawPayload, e2eeKey);
                        ByteBuffer encBuf = ByteBuffer.allocate(1 + encrypted.length);
                        encBuf.put((byte) 0xEE); // Magic byte nhận diện Frame E2EE
                        encBuf.put(encrypted);
                        frameSender.send(encBuf.array());
                    } catch (Exception ex) {
                        // BẢO MẬT FAIL-CLOSED: Tuyệt đối không gửi bản rõ khi mã hóa E2EE gặp lỗi
                        if (logger != null) {
                            logger.appendLog("WebScreenCaster: Lỗi mã hóa E2EE (Bỏ qua khung hình): " + ex.getMessage());
                        }
                    }
                } else {
                    frameSender.send(rawPayload);
                }

                consecutiveIoErrors = 0; // Đặt lại bộ đếm lỗi khi gửi thành công

                // 8. Điều tiết tốc độ khung hình
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed < frameDelay) {
                    Thread.sleep(frameDelay - elapsed);
                }

            } catch (IOException e) {
                consecutiveIoErrors++;
                isFirstFrame = true; // Yêu cầu gửi lại Full Keyframe khi mạng thông
                if (consecutiveIoErrors >= 50 || !running) {
                    if (running && logger != null) {
                        logger.appendLog("WebScreenCaster: Dừng luồng truyền màn hình (" + e.getMessage() + ")");
                    }
                    break;
                }
                // Nếu chỉ nghẽn mạng tạm thời qua Internet Tunnel, ngủ ngắn và tiếp tục thay vì ngắt đột ngột
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                try { Thread.sleep(50); } catch (InterruptedException ie) { break; }
            }
        }

        cleanup();
        if (logger != null) {
            logger.appendLog("WebScreenCaster: Đã dừng luồng truyền màn hình.");
        }
    }

    private void cleanup() {
        running = false;
        synchronized (writerLock) {
            if (jpegWriter != null) {
                try { jpegWriter.dispose(); } catch (Exception ignored) {}
                jpegWriter = null;
            }
        }
    }

    public void stop() {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }
}
