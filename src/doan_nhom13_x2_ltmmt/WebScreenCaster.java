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

    // Buffer pixel để so sánh vi sai (Delta Diffing)
    private int[] prevPixels;
    private int keyframeInterval = 90; // Gửi Full frame mỗi 90 frames (~3 giây)
    private int frameCounter = 0;
    private int idleFrameCounter = 0;

    // Bộ nén JPEG tái sử dụng (Object Pooling tránh GC và OutOfMemory)
    private ImageWriter jpegWriter;
    private ImageWriteParam writeParam;
    private final Object writerLock = new Object();

    public WebScreenCaster(OutputStream out, ServerLogger logger) {
        this.out = out;
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

        final int W = screenRect.width;
        final int H = screenRect.height;
        final int blockSize = 32; // Kích thước khối kiểm tra (32x32 pixels)
        final int blocksX = (W + blockSize - 1) / blockSize;
        final int blocksY = (H + blockSize - 1) / blockSize;

        this.prevPixels = new int[W * H];
        ByteArrayOutputStream baos = new ByteArrayOutputStream(128 * 1024);

        while (running && !Thread.currentThread().isInterrupted()) {
            long startTime = System.currentTimeMillis();
            int frameDelay = 1000 / Math.max(1, targetFps);

            try {
                // 1. Chụp màn hình máy chủ
                BufferedImage screenshot = robot.createScreenCapture(screenRect);
                if (!running) break;

                // Chuẩn hoá ảnh về TYPE_INT_RGB để truy xuất bộ nhớ pixel cực nhanh
                BufferedImage rgbImage;
                if (screenshot.getType() == BufferedImage.TYPE_INT_RGB) {
                    rgbImage = screenshot;
                } else {
                    rgbImage = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = rgbImage.createGraphics();
                    g.drawImage(screenshot, 0, 0, null);
                    g.dispose();
                }

                int[] currPixels = ((DataBufferInt) rgbImage.getRaster().getDataBuffer()).getData();

                // 2. Thuật toán phát hiện vùng thay đổi (Dirty Rectangle Detection)
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
                        // Kiểm tra các điểm mẫu trong khối (4 góc và tâm)
                        int midX = (startX + endX) / 2;
                        int midY = (startY + endY) / 2;

                        if (currPixels[startY * W + startX] != prevPixels[startY * W + startX] ||
                            currPixels[(endY - 1) * W + (endX - 1)] != prevPixels[(endY - 1) * W + (endX - 1)] ||
                            currPixels[midY * W + midX] != prevPixels[midY * W + midX]) {
                            blockChanged = true;
                        } else {
                            // Quét chi tiết theo bước nhảy 4 pixel
                            for (int y = startY; y < endY; y += 4) {
                                int rowOffset = y * W;
                                for (int x = startX; x < endX; x += 4) {
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

                // 3. Nếu màn hình hoàn toàn tĩnh (không thay đổi)
                frameCounter++;
                if (changedBlocks == 0) {
                    idleFrameCounter++;
                    // Bỏ qua gửi để tiết kiệm 100% băng thông, giữ nhịp mỗi 60 frames
                    if (idleFrameCounter < 60) {
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

                // 7. Gửi qua WebSocket
                WebSocketHelper.sendBinaryFrame(out, buffer.array());

                // 8. Điều tiết tốc độ khung hình
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed < frameDelay) {
                    Thread.sleep(frameDelay - elapsed);
                }

            } catch (IOException e) {
                if (running && logger != null) {
                    logger.appendLog("WebScreenCaster: Client đóng kết nối (" + e.getMessage() + ")");
                }
                break;
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
