package doan_nhom13_x2_ltmmt;

import java.awt.*;
import java.io.*;
import java.net.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * Giai đoạn 1 & 5: ClientDashboard - Màn hình kết nối phía Client (Máy đi điều khiển)
 * Giao diện chuyên nghiệp:
 * - Ô nhập IP Server (Mặc định: 127.0.0.1)
 * - Ô nhập Port Server (Mặc định: 9999)
 * - Ô nhập Mật khẩu Server
 * - Nút "Kết nối (Connect)"
 * - Trạng thái kết nối và xử lý mở RemoteViewerFrame
 */
public class ClientDashboard extends JFrame implements ViewerCallback {

    private JTextField txtHost;
    private JTextField txtPort;
    private JPasswordField txtPassword;
    private JButton btnConnect;
    private JLabel lblStatus;

    private RemoteViewerFrame currentViewerFrame;

    public ClientDashboard() {
        initComponents();
    }

    private void initComponents() {
        setTitle("Remote Desktop - Client Dashboard (Máy đi điều khiển)");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(520, 380);
        setLocationRelativeTo(null);
        setResizable(false);

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        mainPanel.setBackground(new Color(245, 247, 250));

        // Header Panel
        JPanel headerPanel = new JPanel(new GridLayout(2, 1, 4, 4));
        headerPanel.setOpaque(false);

        JLabel lblTitle = new JLabel("KẾT NỐI MÁY TÍNH TỪ XA", SwingConstants.CENTER);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTitle.setForeground(new Color(30, 41, 59));

        JLabel lblSub = new JLabel("Nhập địa chỉ IP, cổng và mật khẩu của máy cần điều khiển", SwingConstants.CENTER);
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(new Color(100, 116, 139));

        headerPanel.add(lblTitle);
        headerPanel.add(lblSub);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Center Panel: Form thông tin
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225)),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        formPanel.setBackground(Color.WHITE);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Dòng 1: Server IP
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.28; gbc.gridwidth = 1;
        JLabel lblHost = new JLabel("Địa chỉ IP Server:");
        lblHost.setFont(new Font("Segoe UI", Font.BOLD, 13));
        formPanel.add(lblHost, gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.44;
        txtHost = new JTextField();
        txtHost.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txtHost.setToolTipText("Ví dụ: 192.168.1.7 (Bấm 'Quét tìm LAN' để tự động dò)");
        formPanel.add(txtHost, gbc);

        gbc.gridx = 2; gbc.gridy = 0; gbc.weightx = 0.28;
        JButton btnScan = new JButton("Quét tìm LAN");
        btnScan.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnScan.setToolTipText("Tự động tìm kiếm Server đang bật trong cùng mạng Wi-Fi/LAN");
        btnScan.addActionListener(e -> scanLANServers(btnScan));
        formPanel.add(btnScan, gbc);

        // Dòng 2: Port
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.28; gbc.gridwidth = 1;
        JLabel lblPort = new JLabel("Cổng (Port):");
        lblPort.setFont(new Font("Segoe UI", Font.BOLD, 13));
        formPanel.add(lblPort, gbc);

        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 0.44;
        txtPort = new JTextField("9999");
        txtPort.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        formPanel.add(txtPort, gbc);

        gbc.gridx = 2; gbc.gridy = 1; gbc.weightx = 0.28;
        JButton btnLocal = new JButton("Localhost");
        btnLocal.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnLocal.setToolTipText("Điền nhanh 127.0.0.1 để test trên cùng 1 máy");
        btnLocal.addActionListener(e -> txtHost.setText("127.0.0.1"));
        formPanel.add(btnLocal, gbc);

        // Dòng 3: Password
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.28; gbc.gridwidth = 1;
        JLabel lblPass = new JLabel("Mật khẩu truy cập:");
        lblPass.setFont(new Font("Segoe UI", Font.BOLD, 13));
        formPanel.add(lblPass, gbc);

        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 0.72; gbc.gridwidth = 2;
        txtPassword = new JPasswordField();
        txtPassword.setFont(new Font("Segoe UI", Font.BOLD, 13));
        formPanel.add(txtPassword, gbc);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        // Bottom Panel: Nút Connect & Trạng thái
        JPanel bottomPanel = new JPanel(new BorderLayout(8, 8));
        bottomPanel.setOpaque(false);

        lblStatus = new JLabel("Sẵn sàng kết nối...", SwingConstants.CENTER);
        lblStatus.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        lblStatus.setForeground(new Color(71, 85, 105));
        bottomPanel.add(lblStatus, BorderLayout.NORTH);

        btnConnect = new JButton("KẾT NỐI NGAY (CONNECT)");
        btnConnect.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnConnect.setForeground(Color.BLACK);
        btnConnect.setFocusPainted(false);
        btnConnect.setPreferredSize(new Dimension(200, 40));
        btnConnect.addActionListener(e -> startConnection());
        bottomPanel.add(btnConnect, BorderLayout.SOUTH);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        setContentPane(mainPanel);
    }

    /**
     * Khởi động quá trình kết nối tới Server trên Thread riêng để không bị đơ giao diện
     */
    private void startConnection() {
        final String host = txtHost.getText().trim();
        final String portStr = txtPort.getText().trim();
        final String password = new String(txtPassword.getPassword()).trim().toLowerCase();

        if (host.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập địa chỉ IP của Server!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Cổng (Port) phải là số nguyên hợp lệ!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập mật khẩu xác thực!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        btnConnect.setEnabled(false);
        lblStatus.setText("Đang kết nối tới " + host + ":" + port + "...");
        lblStatus.setForeground(new Color(37, 99, 235));

        new Thread(() -> {
            try {
                // 1. Kết nối kênh truyền hình ảnh (Screen Socket)
                Socket screenSocket = new Socket(host, port);
                screenSocket.setTcpNoDelay(true);
                DataOutputStream dos = new DataOutputStream(screenSocket.getOutputStream());
                DataInputStream dis = new DataInputStream(screenSocket.getInputStream());

                // Gửi mật khẩu xác thực
                dos.writeUTF(password);
                dos.flush();

                // Nhận kết quả xác thực từ Server
                String authResponse = dis.readUTF();
                if (!"AUTH_SUCCESS".equals(authResponse)) {
                    screenSocket.close();
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, "Mật khẩu không chính xác!", "Lỗi xác thực", JOptionPane.ERROR_MESSAGE);
                        btnConnect.setEnabled(true);
                        lblStatus.setText("Xác thực thất bại.");
                        lblStatus.setForeground(new Color(220, 38, 38));
                    });
                    return;
                }

                // Nhận kích thước màn hình Server
                int serverWidth = dis.readInt();
                int serverHeight = dis.readInt();

                // 2. Kết nối kênh truyền sự kiện điều khiển (Command Socket = Port + 1)
                Socket commandSocket = new Socket(host, port + 1);
                commandSocket.setTcpNoDelay(true);
                PrintWriter commandWriter = new PrintWriter(new OutputStreamWriter(commandSocket.getOutputStream()), true);

                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("Kết nối thành công! Đang mở cửa sổ điều khiển...");
                    lblStatus.setForeground(new Color(22, 163, 74));

                    // Mở cửa sổ điều khiển RemoteViewerFrame
                    currentViewerFrame = new RemoteViewerFrame(this, screenSocket, commandSocket, commandWriter, serverWidth, serverHeight);
                    currentViewerFrame.setVisible(true);
                    currentViewerFrame.startReceiving();

                    // Thu nhỏ hoặc ẩn cửa sổ Dashboard kết nối
                    setVisible(false);
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Không thể kết nối đến Server:\n" + ex.getMessage(), "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
                    btnConnect.setEnabled(true);
                    lblStatus.setText("Lỗi kết nối.");
                    lblStatus.setForeground(new Color(220, 38, 38));
                });
            }
        }, "Client-Connect-Thread").start();
    }

    /**
     * Được gọi lại khi RemoteViewerFrame bị đóng để hiển thị lại ClientDashboard
     */
    public void onViewerClosed() {
        SwingUtilities.invokeLater(() -> {
            btnConnect.setEnabled(true);
            lblStatus.setText("Đã ngắt kết nối với máy chủ.");
            lblStatus.setForeground(new Color(71, 85, 105));
            setVisible(true);
        });
    }

    /**
     * Tự động quét và tìm kiếm máy chủ đang mở trong mạng LAN bằng UDP Broadcast
     */
    private void scanLANServers(JButton btnScan) {
        btnScan.setEnabled(false);
        lblStatus.setText("Đang gửi gói tin tìm kiếm Server trong mạng LAN...");
        lblStatus.setForeground(new Color(37, 99, 235));

        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                socket.setSoTimeout(3000);

                byte[] sendData = "DISCOVER_SERVER".getBytes();
                DatagramPacket sendPacket = new DatagramPacket(
                        sendData, sendData.length,
                        InetAddress.getByName("255.255.255.255"), 8888
                );
                socket.send(sendPacket);

                byte[] recvBuf = new byte[1024];
                DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);
                socket.receive(recvPacket);

                String msg = new String(recvPacket.getData(), 0, recvPacket.getLength()).trim();
                if (msg.startsWith("SERVER_FOUND|")) {
                    String[] parts = msg.split("\\|");
                    String hostName = parts.length > 1 ? parts[1] : "Máy Chủ";
                    String foundIp = parts.length > 2 ? parts[2] : recvPacket.getAddress().getHostAddress();
                    String port = parts.length > 3 ? parts[3] : "9999";

                    SwingUtilities.invokeLater(() -> {
                        txtHost.setText(foundIp);
                        txtPort.setText(port);
                        lblStatus.setText("Đã tìm thấy: " + hostName + " (" + foundIp + ")");
                        lblStatus.setForeground(new Color(22, 163, 74));
                        JOptionPane.showMessageDialog(this,
                                "Tìm thấy máy chủ trong mạng LAN!\n\n"
                                + "• Tên máy: " + hostName + "\n"
                                + "• Địa chỉ IP: " + foundIp + "\n"
                                + "• Cổng Port: " + port + "\n\n"
                                + "Đã tự động điền IP. Vui lòng nhập Mật khẩu và bấm Kết nối!",
                                "Dò tìm LAN thành công", JOptionPane.INFORMATION_MESSAGE);
                        btnScan.setEnabled(true);
                    });
                    return;
                }
            } catch (SocketTimeoutException te) {
                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("Không tìm thấy Server nào trong mạng LAN.");
                    lblStatus.setForeground(new Color(220, 38, 38));
                    JOptionPane.showMessageDialog(this,
                            "Không nhận được phản hồi từ Server nào trong mạng LAN!\n\n"
                            + "Vui lòng kiểm tra:\n"
                            + "1. Máy Server đã bấm 'Khởi động Server' chưa?\n"
                            + "2. Cả 2 máy đã kết nối chung 1 mạng Wi-Fi/LAN chưa?\n"
                            + "3. Bạn cũng có thể xem IP trên máy Server rồi gõ trực tiếp vào ô IP.\n"
                            + "4. Tường lửa (Firewall) trên máy Server có đang chặn Java không?",
                            "Không tìm thấy Server", JOptionPane.WARNING_MESSAGE);
                    btnScan.setEnabled(true);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("Lỗi quét LAN: " + ex.getMessage());
                    btnScan.setEnabled(true);
                });
            }
        }, "Client-LAN-Scan-Thread").start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ClientDashboard().setVisible(true);
        });
    }
}
