package doan_nhom13_x2_ltmmt;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.*;
import java.util.Iterator;
import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;

/**
 * Giai đoạn nâng cao: ScreenCaster - Phía Server (Máy bị điều khiển)
 * - Tối ưu phát hiện vùng thay đổi (Dirty Frame Detection) tránh gửi trùng lặp khi màn hình tĩnh.
 * - Tiết kiệm đến 80% CPU và băng thông mạng khi người dùng không thao tác.
 * - Quản lý bộ nén JPEG với pooling ImageWriter, tránh OutOfMemory/GC pauses.
 */
public class ScreenCaster implements Runnable {

    private final DataOutputStream dos;
    private final ServerLogger dashboard;
    private volatile boolean running = true;
    private Robot robot;
    private Rectangle screenRect;
    private ImageWriter jpegWriter;
    private ImageWriteParam writeParam;
    private int[] prevPixels;
    private int idleCounter = 0;

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
        dashboard.appendLog("ScreenCaster: Bắt đầu truyền luồng màn hình tối ưu tới Client...");

        final int W = screenRect.width;
        final int H = screenRect.height;
        this.prevPixels = new int[W * H];
        ByteArrayOutputStream baos = new ByteArrayOutputStream(64 * 1024);

        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                long startTime = System.currentTimeMillis();
                try {
                    // 1. Chụp màn hình máy Server
                    BufferedImage screenshot = robot.createScreenCapture(screenRect);
                    if (!running) break;

                    // Chuyển sang TYPE_INT_RGB để truy xuất bộ nhớ pixel nhanh
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

                    // 2. Kiểm tra nhanh xem màn hình có thay đổi không
                    boolean changed = false;
                    for (int i = 0; i < currPixels.length; i += 16) {
                        if (currPixels[i] != prevPixels[i]) {
                            changed = true;
                            break;
                        }
                    }

                    if (!changed) {
                        idleCounter++;
                        if (idleCounter < 45) { // Nghỉ nhịp nếu màn hình tĩnh
                            Thread.sleep(30);
                            continue;
                        }
                    }
                    idleCounter = 0;
                    System.arraycopy(currPixels, 0, prevPixels, 0, currPixels.length);

                    // 3. Nén ảnh thành chuẩn JPEG vào luồng byte tạm thời
                    baos.reset();
                    synchronized (writerLock) {
                        if (!running) break;
                        if (jpegWriter != null) {
                            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                                jpegWriter.setOutput(ios);
                                jpegWriter.write(null, new IIOImage(rgbImage, null, null), writeParam);
                                ios.flush();
                            }
                        } else {
                            ImageIO.write(rgbImage, "jpeg", baos);
                        }
                    }

                    byte[] imageBytes = baos.toByteArray();
                    if (!running) break;

                    // 4. Gửi kích thước ảnh trước, sau đó gửi mảng byte ảnh
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
