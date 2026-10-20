package doan_nhom13_x2_ltmmt;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * Lớp khởi chạy chính của Đồ Án Lập Trình Mạng Máy Tính (Nhóm 13)
 * Cung cấp menu lựa chọn nhanh:
 * 1. Khởi chạy Server (Máy bị điều khiển)
 * 2. Khởi chạy Client (Máy đi điều khiển)
 * 3. Chạy thử nghiệm Local (Mở cả 2 phía để test trực tiếp trên cùng một máy)
 */
public class DoAn_Nhom13_X2_LTMMT {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            JFrame launcherFrame = new JFrame("Đồ Án Nhóm 13 - Ứng Dụng Remote Desktop");
            launcherFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            launcherFrame.setSize(540, 390);
            launcherFrame.setLocationRelativeTo(null);
            launcherFrame.setResizable(false);

            JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
            mainPanel.setBorder(new EmptyBorder(25, 25, 25, 25));
            mainPanel.setBackground(new Color(248, 250, 252));

            // Header
            JPanel header = new JPanel(new GridLayout(2, 1, 6, 6));
            header.setOpaque(false);
            JLabel title = new JLabel("HỆ THỐNG ĐIỀU KHIỂN MÁY TÍNH TỪ XA", SwingConstants.CENTER);
            title.setFont(new Font("Segoe UI", Font.BOLD, 18));
            title.setForeground(new Color(15, 23, 42));

            JLabel sub = new JLabel("Nền tảng Desktop & Web Remote Engine (Nhóm 13 - Mạng Máy Tính)", SwingConstants.CENTER);
            sub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            sub.setForeground(new Color(100, 116, 139));

            header.add(title);
            header.add(sub);
            mainPanel.add(header, BorderLayout.NORTH);

            // Nút chức năng
            JPanel center = new JPanel(new GridLayout(4, 1, 10, 10));
            center.setOpaque(false);

            JButton btnWeb = new JButton("🌐 KHỞI CHẠY NỀN TẢNG WEB TOÀN DIỆN (HTTP://LOCALHOST:8080)");
            btnWeb.setFont(new Font("Segoe UI", Font.BOLD, 13));
            btnWeb.setForeground(new Color(67, 56, 202));
            btnWeb.setFocusPainted(false);
            btnWeb.addActionListener(e -> {
                new Thread(() -> {
                    WebRemoteServer webServer = new WebRemoteServer(8080, null, null);
                    webServer.run();
                }, "WebRemoteServer-Thread").start();

                try {
                    Thread.sleep(600);
                    Desktop.getDesktop().browse(new java.net.URI("http://localhost:8080"));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(launcherFrame, "Vui lòng mở trình duyệt tại: http://localhost:8080");
                }
            });

            JButton btnServer = new JButton("► KHỞI CHẠY MÁY CHỦ (SERVER - MÁY BỊ ĐIỀU KHIỂN)");
            btnServer.setFont(new Font("Segoe UI", Font.BOLD, 13));
            btnServer.setForeground(Color.BLACK);
            btnServer.setFocusPainted(false);
            btnServer.addActionListener(e -> {
                new ServerDashboard().setVisible(true);
            });

            JButton btnClient = new JButton("► KHỞI CHẠY MÁY KHÁCH (CLIENT - MÁY ĐI ĐIỀU KHIỂN)");
            btnClient.setFont(new Font("Segoe UI", Font.BOLD, 13));
            btnClient.setForeground(Color.BLACK);
            btnClient.setFocusPainted(false);
            btnClient.addActionListener(e -> {
                new ClientDashboard().setVisible(true);
            });

            JButton btnBoth = new JButton("⇄ CHẠY TEST LOCALHOST (MỞ CẢ SERVER & CLIENT)");
            btnBoth.setFont(new Font("Segoe UI", Font.BOLD, 13));
            btnBoth.setForeground(Color.BLACK);
            btnBoth.setFocusPainted(false);
            btnBoth.addActionListener(e -> {
                ServerDashboard server = new ServerDashboard();
                server.setVisible(true);
                server.setLocation(100, 150);

                ClientDashboard client = new ClientDashboard();
                client.setVisible(true);
                client.setLocation(750, 150);
            });

            center.add(btnWeb);
            center.add(btnServer);
            center.add(btnClient);
            center.add(btnBoth);
            mainPanel.add(center, BorderLayout.CENTER);

            // Footer
            JLabel footer = new JLabel("Đồ án Lập trình Mạng Máy Tính - Nhóm 13 (X2)", SwingConstants.CENTER);
            footer.setFont(new Font("Segoe UI", Font.ITALIC, 11));
            footer.setForeground(new Color(148, 163, 184));
            mainPanel.add(footer, BorderLayout.SOUTH);

            launcherFrame.setContentPane(mainPanel);
            launcherFrame.setVisible(true);
        });
    }
}
