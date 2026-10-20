package doan_nhom13_x2_ltmmt;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;

/**
 * Luồng chụp màn hình và truyền trực tiếp tới Web Client qua WebSocket Binary Frames.
 * Cấu trúc Binary Payload:
 * - Bytes 0..3:   int width     (Độ phân giải màn hình Server)
 * - Bytes 4..7:   int height    (Độ phân giải màn hình Server)
 * - Bytes 8..15:  long timestamp (Thời gian Server chụp ảnh tính bằng ms để đo độ trễ)
 * - Bytes 16..end: Dữ liệu JPEG của ảnh chụp màn hình
 *
 * FIX: Không dùng ImageWriter.setOutput() bên trong try-with-resources để tránh
 * IllegalStateException "Attempting to use writer after dispose()".
 * Thay vào đó dùng ImageIO.write() thông thường hoặc tạo lại writer mỗi frame.
 */
public class WebScreenCaster implements Runnable {

    private final OutputStream out;
    private final ServerLogger logger;
    private volatile boolean running = true;
    private volatile float quality = 0.65f; // Mặc định 65% quality
    private volatile int targetFps = 30;

    private Robot robot;
    private Rectangle screenRect;

    public WebScreenCaster(OutputStream out, ServerLogger logger) {
        this.out = out;
        this.logger = logger;

        try {
            this.robot = new Robot();
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            this.screenRect = new Rectangle(screenSize);
        } catch (AWTException e) {
            if (logger != null) {
                logger.appendLog("WebScreenCaster: Lỗi khởi tạo Robot chụp màn hình: " + e.getMessage());
            }
        }
    }

    public void setQuality(float newQuality) {
        this.quality = Math.max(0.2f, Math.min(0.95f, newQuality));
    }

    public void setTargetFps(int fps) {
        this.targetFps = Math.max(5, Math.min(60, fps));
    }

    @Override
    public void run() {
        if (logger != null) {
            logger.appendLog("WebScreenCaster: Đang truyền luồng màn hình tới Web Client...");
        }

        if (robot == null) {
            if (logger != null) {
                logger.appendLog("WebScreenCaster: Robot chưa được khởi tạo, không thể truyền màn hình.");
            }
            return;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream(128 * 1024);

        while (running && !Thread.currentThread().isInterrupted()) {
            long startTime = System.currentTimeMillis();
            int frameDelay = 1000 / Math.max(1, targetFps);

            try {
                // 1. Chụp màn hình
                BufferedImage screenshot = robot.createScreenCapture(screenRect);
                if (!running) break;

                // 2. Nén JPEG - dùng ImageIO.write() đơn giản, tránh lỗi dispose()
                //    Để điều chỉnh quality, dùng JPEGImageWriteParam với ImageIO stream riêng
                baos.reset();
                encodeJpeg(screenshot, baos, quality);

                if (!running) break;

                byte[] jpegBytes = baos.toByteArray();

                // 3. Đóng gói Binary Frame: [width(4), height(4), timestamp(8), jpegBytes]
                int totalSize = 16 + jpegBytes.length;
                ByteBuffer buffer = ByteBuffer.allocate(totalSize);
                buffer.putInt(screenRect.width);
                buffer.putInt(screenRect.height);
                buffer.putLong(startTime);
                buffer.put(jpegBytes);

                // 4. Gửi qua WebSocket Binary Frame
                WebSocketHelper.sendBinaryFrame(out, buffer.array());

                // 5. Điều tiết FPS
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed < frameDelay) {
                    Thread.sleep(frameDelay - elapsed);
                }

            } catch (IOException e) {
                if (running) {
                    if (logger != null) {
                        logger.appendLog("WebScreenCaster: Kết nối Web Client đã đóng (" + e.getMessage() + ")");
                    }
                }
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (running) {
                    if (logger != null) {
                        logger.appendLog("WebScreenCaster cảnh báo: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    }
                }
                // Không break - tiếp tục frame kế tiếp để tránh ngắt kết nối do lỗi nhất thời
                try { Thread.sleep(50); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        running = false;
        if (logger != null) {
            logger.appendLog("WebScreenCaster: Luồng truyền màn hình web đã dừng.");
        }
    }

    /**
     * Mã hoá ảnh thành JPEG với quality chỉ định.
     * Tạo mới ImageWriter mỗi lần gọi để tránh lỗi "writer after dispose()".
     */
    private static void encodeJpeg(BufferedImage image, ByteArrayOutputStream baos, float quality) throws IOException {
        // Đảm bảo không có alpha channel (JPEG không hỗ trợ alpha)
        BufferedImage rgbImage = image;
        if (image.getType() != BufferedImage.TYPE_INT_RGB) {
            rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = rgbImage.createGraphics();
            g.drawImage(image, 0, 0, null);
            g.dispose();
        }

        // Tạo mới ImageWriter mỗi lần để tránh tái sử dụng writer bị dispose
        javax.imageio.ImageWriter writer = javax.imageio.ImageIO.getImageWritersByFormatName("jpeg").next();
        javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);

        try (javax.imageio.stream.ImageOutputStream ios = javax.imageio.ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(rgbImage, null, null), param);
            ios.flush();
        } finally {
            writer.dispose(); // Dispose writer MỚI này (không ảnh hưởng frame tiếp theo)
        }
    }

    public void stop() {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }
}
