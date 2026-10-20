package doan_nhom13_x2_ltmmt;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import javax.imageio.ImageIO;

/**
 * Giai đoạn 3: ScreenReceiver - Phía Client (Máy đi điều khiển)
 * Chạy trên một Thread riêng biệt.
 * Nhiệm vụ:
 * 1. Liên tục đọc kích thước ảnh từ DataInputStream của Socket (readInt).
 * 2. Đọc đúng số byte đó bằng readFully để nhận trọn vẹn khung hình.
 * 3. Dùng ImageIO.read để giải nén mảng byte thành BufferedImage.
 * 4. Cập nhật BufferedImage lên RemoteViewerFrame và gọi repaint() liên tục.
 * 5. Đo lường tốc độ khung hình (FPS) thời gian thực.
 */
public class ScreenReceiver implements Runnable {

    private final Socket socket;
    private final RemoteViewerFrame viewerFrame;
    private volatile boolean running = true;

    public ScreenReceiver(Socket socket, RemoteViewerFrame viewerFrame) {
        this.socket = socket;
        this.viewerFrame = viewerFrame;
    }

    @Override
    public void run() {
        int frameCount = 0;
        int currentFps = 0;
        long lastFpsTime = System.currentTimeMillis();

        try (DataInputStream dis = new DataInputStream(socket.getInputStream())) {
            while (running && !Thread.currentThread().isInterrupted()) {
                // 1. Đọc kích thước của ảnh (số byte)
                int imageSize = dis.readInt();
                if (imageSize <= 0 || imageSize > 50 * 1024 * 1024) {
                    // Giới hạn an toàn tối đa 50MB tránh tràn bộ nhớ nếu packet lỗi
                    break;
                }

                // 2. Đọc trọn vẹn mảng byte của ảnh
                byte[] imageBytes = new byte[imageSize];
                dis.readFully(imageBytes);

                // 3. Giải nén mảng byte thành BufferedImage
                ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
                BufferedImage img = ImageIO.read(bais);

                if (img != null) {
                    // 4. Tính toán FPS
                    frameCount++;
                    long now = System.currentTimeMillis();
                    if (now - lastFpsTime >= 1000) {
                        currentFps = frameCount;
                        frameCount = 0;
                        lastFpsTime = now;
                    }

                    // 5. Cập nhật giao diện vẽ đè liên tục
                    viewerFrame.updateScreen(img, currentFps);
                }
            }
        } catch (IOException e) {
            // Mất kết nối tới Server
        }

        stop();
    }

    public void stop() {
        running = false;
    }
}
