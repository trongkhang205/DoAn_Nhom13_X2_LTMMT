package doan_nhom13_x2_ltmmt;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.PrintWriter;
import java.net.Socket;
import javax.swing.*;

/**
 * Giai đoạn 3 & 4: RemoteViewerFrame - Màn hình hiển thị & Điều khiển phía Client
 * Nhiệm vụ:
 * 1. Chứa ViewerPanel với override paintComponent để vẽ ảnh nhận được từ Server.
 * 2. Tự động co dãn (scale) theo tỷ lệ để hình ảnh luôn hiển thị đẹp và sắc nét.
 * 3. Gắn MouseListener, MouseMotionListener, MouseWheelListener, KeyListener.
 * 4. Mapping tỷ lệ tọa độ chuột từ Panel phía Client sang độ phân giải thực của Server.
 * 5. Đóng gói lệnh (MOUSE_MOVE, MOUSE_PRESS, KEY_PRESS,...) và gửi qua PrintWriter tới Server.
 */
public class RemoteViewerFrame extends JFrame {

    private final ViewerCallback clientDashboard;
    private final Socket screenSocket;
    private final Socket commandSocket;
    private final PrintWriter commandWriter;

    private final int serverWidth;
    private final int serverHeight;

    private final ViewerPanel viewerPanel;
    private final JLabel lblStatus;
    private ScreenReceiver screenReceiver;

    public RemoteViewerFrame(ViewerCallback clientDashboard, Socket screenSocket, Socket commandSocket, 
                             PrintWriter commandWriter, int serverWidth, int serverHeight) {
        this.clientDashboard = clientDashboard;
        this.screenSocket = screenSocket;
        this.commandSocket = commandSocket;
        this.commandWriter = commandWriter;
        this.serverWidth = serverWidth;
        this.serverHeight = serverHeight;

        setTitle("Remote Desktop Viewer - Đang điều khiển máy từ xa (" + serverWidth + "x" + serverHeight + ")");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1024, 680);
        setLocationRelativeTo(null);

        // Bố cục giao diện
        JPanel mainContainer = new JPanel(new BorderLayout());
        viewerPanel = new ViewerPanel();
        mainContainer.add(viewerPanel, BorderLayout.CENTER);

        // Thanh trạng thái bên dưới
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        statusPanel.setBackground(new Color(241, 245, 249));

        lblStatus = new JLabel("Đang kết nối... Độ phân giải Server: " + serverWidth + "x" + serverHeight + " | FPS: 0");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusPanel.add(lblStatus, BorderLayout.WEST);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setOpaque(false);

        JButton btnSendFile = new JButton("📁 Truyền tệp");
        btnSendFile.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnSendFile.setForeground(new Color(30, 41, 59));
        btnSendFile.setFocusPainted(false);
        btnSendFile.setToolTipText("Chọn và gửi tệp tin từ máy này lên máy chủ từ xa");
        btnSendFile.addActionListener(e -> chooseAndSendFile(btnSendFile));
        buttonPanel.add(btnSendFile);

        JButton btnDisconnect = new JButton("Ngắt kết nối");
        btnDisconnect.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnDisconnect.setForeground(Color.BLACK);
        btnDisconnect.setFocusPainted(false);
        btnDisconnect.addActionListener(e -> dispose());
        buttonPanel.add(btnDisconnect);

        statusPanel.add(buttonPanel, BorderLayout.EAST);

        mainContainer.add(statusPanel, BorderLayout.SOUTH);
        setContentPane(mainContainer);

        // Đăng ký bộ lắng nghe sự kiện đóng cửa sổ để giải phóng tài nguyên
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeConnection();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                closeConnection();
            }
        });

        // Thiết lập sự kiện điều khiển chuột và phím
        setupEventListeners();
    }

    /**
     * Bắt đầu nhận dữ liệu hình ảnh
     */
    public void startReceiving() {
        screenReceiver = new ScreenReceiver(screenSocket, this);
        new Thread(screenReceiver, "ScreenReceiver-Thread").start();
    }

    /**
     * Cập nhật hình ảnh mới và vẽ lại giao diện
     */
    public void updateScreen(BufferedImage image, int fps) {
        viewerPanel.setImage(image);
        SwingUtilities.invokeLater(() -> {
            lblStatus.setText(String.format("Đang kết nối: %s | Server: %dx%d | FPS: %d", 
                    screenSocket.getInetAddress().getHostAddress(), serverWidth, serverHeight, fps));
        });
    }

    /**
     * Đăng ký Mouse & Key Listeners cho ViewerPanel (Giai đoạn 4)
     */
    private void setupEventListeners() {
        viewerPanel.setFocusable(true);

        // Lấy focus khi chuột rê vào hoặc click vào panel
        viewerPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                viewerPanel.requestFocusInWindow();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                viewerPanel.requestFocusInWindow();
                if (e.getClickCount() == 2) {
                    Point serverPoint = viewerPanel.mapToServerCoordinates(e.getX(), e.getY());
                    if (serverPoint != null) {
                        sendCommand("MOUSE_MOVE," + serverPoint.x + "," + serverPoint.y);
                        int mask = getButtonMask(e.getButton());
                        sendCommand("MOUSE_DBLCLICK," + mask);
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                viewerPanel.requestFocusInWindow();
                sendMousePress(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                sendMouseRelease(e);
            }
        });

        // Di chuyển chuột
        viewerPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                sendMouseMove(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                sendMouseMove(e);
            }
        });

        // Cuộn chuột
        viewerPanel.addMouseWheelListener(e -> {
            sendCommand("MOUSE_WHEEL," + e.getWheelRotation());
        });

        // Gõ bàn phím
        viewerPanel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                sendCommand("KEY_PRESS," + e.getKeyCode());
            }

            @Override
            public void keyReleased(KeyEvent e) {
                sendCommand("KEY_RELEASE," + e.getKeyCode());
            }
        });
    }

    /**
     * Ánh xạ tọa độ chuột từ Client sang Server và gửi lệnh MOUSE_MOVE
     */
    private void sendMouseMove(MouseEvent e) {
        Point serverPoint = viewerPanel.mapToServerCoordinates(e.getX(), e.getY());
        if (serverPoint != null) {
            sendCommand("MOUSE_MOVE," + serverPoint.x + "," + serverPoint.y);
        }
    }

    private void sendMousePress(MouseEvent e) {
        Point serverPoint = viewerPanel.mapToServerCoordinates(e.getX(), e.getY());
        if (serverPoint != null) {
            // Đảm bảo chuột ở đúng vị trí trước khi nhấn
            sendCommand("MOUSE_MOVE," + serverPoint.x + "," + serverPoint.y);
            int mask = getButtonMask(e.getButton());
            sendCommand("MOUSE_PRESS," + mask);
        }
    }

    private void sendMouseRelease(MouseEvent e) {
        Point serverPoint = viewerPanel.mapToServerCoordinates(e.getX(), e.getY());
        if (serverPoint != null) {
            sendCommand("MOUSE_MOVE," + serverPoint.x + "," + serverPoint.y);
        }
        int mask = getButtonMask(e.getButton());
        sendCommand("MOUSE_RELEASE," + mask);
    }

    private int getButtonMask(int button) {
        switch (button) {
            case MouseEvent.BUTTON1:
                return InputEvent.BUTTON1_DOWN_MASK;
            case MouseEvent.BUTTON2:
                return InputEvent.BUTTON2_DOWN_MASK;
            case MouseEvent.BUTTON3:
                return InputEvent.BUTTON3_DOWN_MASK;
            default:
                return InputEvent.BUTTON1_DOWN_MASK;
        }
    }

    /**
     * Gửi chuỗi lệnh text qua luồng Socket điều khiển (Port thứ 2)
     */
    private void sendCommand(String cmd) {
        if (commandWriter != null) {
            synchronized (commandWriter) {
                commandWriter.println(cmd);
            }
        }
    }

    /**
     * Đóng tất cả kết nối an toàn
     */
    public void closeConnection() {
        if (screenReceiver != null) {
            screenReceiver.stop();
        }
        try {
            if (commandWriter != null) commandWriter.close();
            if (screenSocket != null && !screenSocket.isClosed()) screenSocket.close();
            if (commandSocket != null && !commandSocket.isClosed()) commandSocket.close();
        } catch (Exception ignored) {}

        if (clientDashboard != null) {
            clientDashboard.onViewerClosed();
        }
    }

    /**
     * Panel chuyên biệt dùng vẽ đè liên tục ảnh màn hình nhận được
     */
    private class ViewerPanel extends JPanel {
        private BufferedImage currentImage;
        private int drawX = 0, drawY = 0, drawW = 0, drawH = 0;

        public ViewerPanel() {
            setBackground(Color.BLACK);
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }

        public void setImage(BufferedImage img) {
            this.currentImage = img;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (currentImage != null) {
                int panelW = getWidth();
                int panelH = getHeight();
                int imgW = currentImage.getWidth();
                int imgH = currentImage.getHeight();

                if (panelW <= 0 || panelH <= 0 || imgW <= 0 || imgH <= 0) return;

                // Tính toán tỷ lệ giữ nguyên khung hình (Aspect Ratio)
                double scale = Math.min((double) panelW / imgW, (double) panelH / imgH);
                drawW = (int) (imgW * scale);
                drawH = (int) (imgH * scale);
                drawX = (panelW - drawW) / 2;
                drawY = (panelH - drawH) / 2;

                Graphics2D g2d = (Graphics2D) g;
                // Bật chế độ vẽ mượt mà
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.drawImage(currentImage, drawX, drawY, drawW, drawH, null);
            } else {
                g.setColor(Color.WHITE);
                g.setFont(new Font("Segoe UI", Font.BOLD, 16));
                String text = "Đang chờ luồng hình ảnh từ máy chủ...";
                FontMetrics fm = g.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(text)) / 2;
                int y = getHeight() / 2;
                g.drawString(text, x, y);
            }
        }

        /**
         * Chuyển đổi tọa độ chuột từ Client sang độ phân giải thực của Server
         */
        public Point mapToServerCoordinates(int mouseX, int mouseY) {
            if (drawW <= 0 || drawH <= 0) return null;

            int relX = mouseX - drawX;
            int relY = mouseY - drawY;

            // Kiểm tra chuột có nằm trong khung ảnh không
            if (relX < 0 || relX >= drawW || relY < 0 || relY >= drawH) {
                return null;
            }

            int mappedX = (int) Math.round((double) relX * serverWidth / drawW);
            int mappedY = (int) Math.round((double) relY * serverHeight / drawH);

            // Giới hạn trong biên màn hình Server
            mappedX = Math.max(0, Math.min(mappedX, serverWidth - 1));
            mappedY = Math.max(0, Math.min(mappedY, serverHeight - 1));

            return new Point(mappedX, mappedY);
        }
    }

    /**
     * Chọn và gửi tệp tin từ máy khách Desktop lên máy chủ từ xa qua commandWriter
     */
    private void chooseAndSendFile(JButton triggerBtn) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn tệp tin để gửi lên máy chủ từ xa");
        chooser.setMultiSelectionEnabled(true);
        int ret = chooser.showOpenDialog(this);
        if (ret != JFileChooser.APPROVE_OPTION) return;

        java.io.File[] selected = chooser.getSelectedFiles();
        if ((selected == null || selected.length == 0) && chooser.getSelectedFile() != null) {
            selected = new java.io.File[] { chooser.getSelectedFile() };
        }
        if (selected == null || selected.length == 0) return;
        final java.io.File[] files = selected;

        new Thread(() -> {
            SwingUtilities.invokeLater(() -> triggerBtn.setEnabled(false));
            int successCount = 0;
            for (java.io.File file : files) {
                if (!file.isFile()) continue;
                String safeName = file.getName();
                long totalSize = file.length();
                try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                    SwingUtilities.invokeLater(() -> lblStatus.setText("Đang gửi tệp: " + safeName + " (" + formatFileSize(totalSize) + ")..."));
                    
                    String b64Name = java.util.Base64.getEncoder().encodeToString(safeName.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    synchronized (commandWriter) {
                        commandWriter.println("FILE_START," + b64Name + "," + totalSize);
                        commandWriter.flush();
                    }

                    byte[] buffer = new byte[32 * 1024];
                    int bytesRead;
                    long sent = 0;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        byte[] chunk = buffer;
                        if (bytesRead < buffer.length) {
                            chunk = java.util.Arrays.copyOf(buffer, bytesRead);
                        }
                        String b64 = java.util.Base64.getEncoder().encodeToString(chunk);
                        synchronized (commandWriter) {
                            commandWriter.println("FILE_CHUNK," + b64);
                            commandWriter.flush();
                        }
                        sent += bytesRead;
                        final int percent = totalSize > 0 ? (int) Math.min(99, (sent * 100 / totalSize)) : 100;
                        SwingUtilities.invokeLater(() -> lblStatus.setText("Đang gửi: " + safeName + " (" + percent + "%)"));
                        Thread.sleep(5);
                    }

                    synchronized (commandWriter) {
                        commandWriter.println("FILE_END");
                        commandWriter.flush();
                    }
                    successCount++;
                    SwingUtilities.invokeLater(() -> lblStatus.setText("✅ Đã gửi thành công tệp: " + safeName));
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Lỗi gửi tệp [" + safeName + "]: " + ex.getMessage(), "Lỗi truyền tệp", JOptionPane.ERROR_MESSAGE));
                }
            }

            final int finalSuccess = successCount;
            SwingUtilities.invokeLater(() -> {
                triggerBtn.setEnabled(true);
                JOptionPane.showMessageDialog(this, 
                        "Đã hoàn tất truyền " + finalSuccess + " tệp tin lên máy chủ!\nTệp được lưu an toàn tại thư mục Downloads/RemoteDesktop_Received của máy chủ.", 
                        "Truyền tệp thành công", JOptionPane.INFORMATION_MESSAGE);
            });
        }, "Desktop-FileSender-Thread").start();
    }

    private static String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[] { "B", "KB", "MB", "GB" };
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format(java.util.Locale.US, "%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
