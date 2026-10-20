package doan_nhom13_x2_ltmmt;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Iterator;
import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;

/**
 * Giai đoạn 2: ScreenCaster - Phía Server (Máy bị điều khiển)
 * Chạy trên một Thread riêng biệt.
 * Nhiệm vụ:
 * 1. Dùng java.awt.Robot chụp toàn bộ màn hình liên tục trong vòng lặp while.
 * 2. Nén ảnh chụp được thành định dạng JPEG (sử dụng ImageIO và ImageWriteParam chất lượng ~0.65).
 * 3. Gửi kích thước (size) của ảnh trước (writeInt), sau đó gửi mảng byte của ảnh qua DataOutputStream.
 * 4. Xử lý Exception tốt, không bị crash khi Client ngắt kết nối.
 */
public class ScreenCaster implements Runnable {

    private final DataOutputStream dos;
    private final ServerLogger dashboard;
    private volatile boolean running = true;
    private Robot robot;
    private Rectangle screenRect;
    private ImageWriter jpegWriter;
    private ImageWriteParam writeParam;

    public ScreenCaster(DataOutputStream dos, ServerLogger dashboard) {
        this.dos = dos;
        this.dashboard = dashboard;

        try {
            this.robot = new Robot();
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            this.screenRect = new Rectangle(screenSize);

            // Cấu hình bộ nén JPEG với chất lượng khoảng 0.65f để cân bằng tốc độ và chất lượng
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (writers.hasNext()) {
                jpegWriter = writers.next();
                writeParam = jpegWriter.getDefaultWriteParam();
                writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                writeParam.setCompressionQuality(0.65f); // 65% quality tối ưu hóa đường truyền
            }
        } catch (AWTException e) {
            dashboard.appendLog("Lỗi khởi tạo Robot chụp màn hình: " + e.getMessage());
        }
    }

    private final Object writerLock = new Object();

    @Override
    public void run() {
        dashboard.appendLog("ScreenCaster: Bắt đầu truyền luồng màn hình tới Client...");

        ByteArrayOutputStream baos = new ByteArrayOutputStream(64 * 1024);

        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                long startTime = System.currentTimeMillis();
                try {
                    // 1. Chụp màn hình máy Server
                    BufferedImage screenshot = robot.createScreenCapture(screenRect);
                    if (!running) break;

                    // 2. Nén ảnh thành chuẩn JPEG vào luồng byte tạm thời
                    baos.reset();
                    synchronized (writerLock) {
                        if (!running) break;
                        if (jpegWriter != null) {
                            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                                jpegWriter.setOutput(ios);
                                jpegWriter.write(null, new IIOImage(screenshot, null, null), writeParam);
                            }
                        } else {
                            ImageIO.write(screenshot, "jpeg", baos);
                        }
                    }

                    byte[] imageBytes = baos.toByteArray();
                    if (!running) break;

                    // 3. Gửi kích thước ảnh trước, sau đó gửi mảng byte ảnh
                    dos.writeInt(imageBytes.length);
                    dos.write(imageBytes);
                    dos.flush();

                    // Kiểm soát tốc độ khung hình (khoảng 30-40 FPS)
                    long elapsed = System.currentTimeMillis() - startTime;
                    if (elapsed < 30) {
                        Thread.sleep(30 - elapsed);
                    }
                } catch (IOException e) {
                    if (running) {
                        dashboard.appendLog("ScreenCaster: Client đã ngắt kết nối màn hình (" + e.getMessage() + ")");
                    }
                    break;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (IllegalStateException e) {
                    if (running) {
                        dashboard.appendLog("ScreenCaster cảnh báo: " + e.getMessage());
                    }
                    break;
                } catch (Exception e) {
                    if (running) {
                        dashboard.appendLog("ScreenCaster cảnh báo: " + e.getMessage());
                    }
                }
            }
        } finally {
            cleanup();
            dashboard.appendLog("ScreenCaster: Đã dừng luồng truyền màn hình.");
        }
    }

    public void stop() {
        running = false;
    }

    private void cleanup() {
        running = false;
        synchronized (writerLock) {
            if (jpegWriter != null) {
                try {
                    jpegWriter.dispose();
                } catch (Exception ignored) {}
                jpegWriter = null;
            }
        }
    }
}
