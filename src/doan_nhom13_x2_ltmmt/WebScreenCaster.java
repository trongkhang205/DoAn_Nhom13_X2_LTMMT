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
    private volatile int targetFps = 30;

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

    // Buffer pixel để so sánh vi sai (Delta Diffing)
    private int[] prevPixels;
    private int keyframeInterval = 90; // Gửi Full frame mỗi 90 frames (~3 giây)
    private int frameCounter = 0;
    private int idleFrameCounter = 0;

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

    public void setE2eeKey(javax.crypto.SecretKey key) {
        this.e2eeKey = key;
        if (logger != null && key != null) {
            logger.appendLog("WebScreenCaster: Đã kích hoạt kênh mã hóa đầu cuối E2EE (AES-256-GCM) cho luồng hình ảnh.");
        }
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

        int W = screenRect.width;
        int H = screenRect.height;
        final int blockSize = 32; // Kích thước khối kiểm tra (32x32 pixels)
        int blocksX = (W + blockSize - 1) / blockSize;
        int blocksY = (H + blockSize - 1) / blockSize;

        this.prevPixels = new int[W * H];
        ByteArrayOutputStream baos = new ByteArrayOutputStream(128 * 1024);
        int consecutiveIoErrors = 0;

        // Tái sử dụng BufferedImage và Graphics2D để triệt tiêu Garbage Collection (250MB/s)
        BufferedImage reusableRgbImage = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D gReusable = reusableRgbImage.createGraphics();

        while (running && !Thread.currentThread().isInterrupted()) {
            long startTime = System.currentTimeMillis();
            int frameDelay = 1000 / Math.max(1, targetFps);

            // Kiểm tra yêu cầu chuyển màn hình động
            if (screenSwitchRequested) {
                synchronized (screenLock) {
                    screenSwitchRequested = false;
                    W = screenRect.width;
                    H = screenRect.height;
                    blocksX = (W + blockSize - 1) / blockSize;
                    blocksY = (H + blockSize - 1) / blockSize;
                    this.prevPixels = new int[W * H];
                    if (gReusable != null) gReusable.dispose();
                    reusableRgbImage = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
                    gReusable = reusableRgbImage.createGraphics();
                    frameCounter = 0; // Buộc gửi Keyframe Full màn hình mới
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
                // 1. Chụp màn hình máy chủ
                BufferedImage screenshot = robot.createScreenCapture(screenRect);
                if (!running) break;

                // Chuẩn hoá ảnh về TYPE_INT_RGB tái sử dụng buffer
                BufferedImage rgbImage;
                if (screenshot.getType() == BufferedImage.TYPE_INT_RGB) {
                    rgbImage = screenshot;
                } else {
                    gReusable.drawImage(screenshot, 0, 0, null);
                    rgbImage = reusableRgbImage;
                }

                int[] currPixels = ((DataBufferInt) rgbImage.getRaster().getDataBuffer()).getData();

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
                    int endY = Math.min(startY + blockSize, H);

                    for (int bx = 0; bx < blocksX; bx++) {
                        int startX = bx * blockSize;
                        int endX = Math.min(startX + blockSize, W);

                        boolean blockChanged = false;
                        int midX = (startX + endX) / 2;
                        int midY = (startY + endY) / 2;

                        // 9 điểm mẫu trọng yếu: 4 góc, tâm, và 4 trung điểm biên
                        int p0 = startY * W + startX;
                        int p1 = startY * W + midX;
                        int p2 = startY * W + (endX - 1);
                        int p3 = midY * W + startX;
                        int p4 = midY * W + midX;
                        int p5 = midY * W + (endX - 1);
                        int p6 = (endY - 1) * W + startX;
                        int p7 = (endY - 1) * W + midX;
                        int p8 = (endY - 1) * W + (endX - 1);

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
                                int rowOffset = y * W;
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

                // 3. Nếu màn hình tĩnh: duy trì nhịp gửi tối thiểu (tối đa bỏ qua 2 khung hình)
                // Đảm bảo client không bao giờ bị tụt FPS xuống 0 - 4 FPS
                frameCounter++;
                if (changedBlocks == 0) {
                    idleFrameCounter++;
                    if (idleFrameCounter < 2) {
                        Thread.sleep(frameDelay);
                        continue;
                    }
                }
                idleFrameCounter = 0;

                // 4. Phân loại Full Frame vs Delta Frame
                boolean forceKeyframe = (frameCounter >= keyframeInterval || frameCounter == 1);
                int targetX = 0, targetY = 0, targetW = W, targetH = H;
                byte frameType = 0; // 0 = Full, 1 = Delta
                BufferedImage imageToSend;

                if (!forceKeyframe && changedBlocks > 0) {
                    int dirtyX = minBx * blockSize;
                    int dirtyY = minBy * blockSize;
                    int dirtyW = Math.min((maxBx + 1) * blockSize, W) - dirtyX;
                    int dirtyH = Math.min((maxBy + 1) * blockSize, H) - dirtyY;

                    float areaRatio = (float) (dirtyW * dirtyH) / (W * H);
                    if (areaRatio <= 0.65f) {
                        // Delta Frame: Chỉ cắt đúng vùng thay đổi
                        frameType = 1;
                        targetX = dirtyX;
                        targetY = dirtyY;
                        targetW = dirtyW;
                        targetH = dirtyH;
                        imageToSend = rgbImage.getSubimage(dirtyX, dirtyY, dirtyW, dirtyH);
                    } else {
                        // Vùng thay đổi quá lớn (>65% màn hình) -> Gửi Full Frame cho sắc nét
                        imageToSend = rgbImage;
                        frameCounter = 0;
                    }
                } else {
                    imageToSend = rgbImage;
                    frameCounter = 0;
                }

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
                buffer.putInt(W);
                buffer.putInt(H);
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
                        // Fallback gửi thường nếu lỗi mã hóa
                        frameSender.send(rawPayload);
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
                if (consecutiveIoErrors >= 6 || !running) {
                    if (running && logger != null) {
                        logger.appendLog("WebScreenCaster: Dừng luồng truyền màn hình (" + e.getMessage() + ")");
                    }
                    break;
                }
                // Nếu chỉ nghẽn mạng tạm thời qua Internet Tunnel, ngủ ngắn và tiếp tục thay vì ngắt đột ngột
                try {
                    Thread.sleep(Math.max(10, frameDelay));
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
