package doan_nhom13_x2_ltmmt;

import java.io.*;
import java.net.*;
import java.util.Random;
import java.util.Enumeration;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Giai đoạn 1 & 5: ServerDashboard - Màn hình điều khiển phía Server (Máy bị điều khiển)
 * Giao diện tương tự AnyDesk / TeamViewer:
 * - Hiển thị IP máy chủ hiện tại
 * - Port kết nối (Mặc định 9999, Port điều khiển sự kiện 10000)
 * - Mật khẩu bảo mật (ngẫu nhiên 6 ký tự)
 * - Nút Start/Stop Server
 * - Khung Log hiển thị trạng thái kết nối
 */
public class ServerDashboard extends JFrame implements ServerLogger {

    private JTextField txtIp;
    private JTextField txtPort;
    private JTextField txtPassword;
    private JTextField txtWebUrl;
    private JButton btnOpenWeb;
    private JButton btnGeneratePass;
    private JButton btnStart;
    private JButton btnStop;
    private JTextArea txtLog;
    private JLabel lblStatus;

    // Quản lý kết nối mạng
    private ServerSocket screenServerSocket;
    private ServerSocket commandServerSocket;
    private DatagramSocket discoverySocket;
    private Thread discoveryThread;
    private volatile boolean isRunning = false;
    private Thread serverAcceptThread;

    // Máy chủ Web tích hợp (Web Platform)
    private WebRemoteServer webRemoteServer;
    private final int webPort = 8080;

    // Luồng truyền hình ảnh và xử lý lệnh điều khiển
    private ScreenCaster currentScreenCaster;
    private EventHandler currentEventHandler;
    private Socket clientScreenSocket;
    private Socket clientCommandSocket;

    public ServerDashboard() {
        initComponents();
        loadLocalIP();
        generateRandomPassword();
        updateWebUrl();
    }

    private void initComponents() {
        setTitle("Remote Desktop - Server Dashboard (Máy bị điều khiển)");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopServer();
            }
        });
        setSize(640, 570);
        setLocationRelativeTo(null);

        // Thiết lập giao diện hiện đại
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        mainPanel.setBackground(new Color(245, 247, 250));

        // Header Panel
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        JLabel lblTitle = new JLabel("MÁY CHỦ ĐIỀU KHIỂN TỪ XA (SERVER)");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTitle.setForeground(new Color(30, 41, 59));
        
        lblStatus = new JLabel("● Trạng thái: Chưa hoạt động");
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblStatus.setForeground(new Color(220, 38, 38));

        headerPanel.add(lblTitle, BorderLayout.WEST);
        headerPanel.add(lblStatus, BorderLayout.EAST);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Center Panel: Thông số cấu hình & Nút bấm
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.setOpaque(false);

        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225)), 
                " Thông tin kết nối & Bảo mật ", 
                0, 0, new Font("Segoe UI", Font.BOLD, 13), new Color(71, 85, 105)));
        configPanel.setBackground(Color.WHITE);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 12, 8, 12);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Dòng 1: Địa chỉ IP
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.2; gbc.gridwidth = 1;
        JLabel lblIpText = new JLabel("IP Máy này:");
        lblIpText.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        configPanel.add(lblIpText, gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.55;
        txtIp = new JTextField();
        txtIp.setEditable(false);
        txtIp.setFont(new Font("Segoe UI", Font.BOLD, 13));
        txtIp.setBackground(new Color(241, 245, 249));
        configPanel.add(txtIp, gbc);

        gbc.gridx = 2; gbc.gridy = 0; gbc.weightx = 0.25;
        JButton btnCopyIp = new JButton("Sao chép");
        btnCopyIp.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnCopyIp.setToolTipText("Sao chép IP để gửi sang Máy Khách (Client)");
        btnCopyIp.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(txtIp.getText()), null);
            JOptionPane.showMessageDialog(this, "Đã sao chép IP máy chủ: " + txtIp.getText() + "\nHãy nhập IP này vào Máy Khách (Client).", "Sao chép thành công", JOptionPane.INFORMATION_MESSAGE);
        });
        configPanel.add(btnCopyIp, gbc);

        // Dòng 2: Port
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1; gbc.weightx = 0.2;
        JLabel lblPortText = new JLabel("Port:");
        lblPortText.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        configPanel.add(lblPortText, gbc);

        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 0.8; gbc.gridwidth = 2;
        txtPort = new JTextField("9999");
        txtPort.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        configPanel.add(txtPort, gbc);

        // Dòng 3: Password & Nút tạo mới
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1; gbc.weightx = 0.2;
        JLabel lblPassText = new JLabel("Mật khẩu:");
        lblPassText.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        configPanel.add(lblPassText, gbc);

        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 0.55;
        txtPassword = new JTextField();
        txtPassword.setFont(new Font("Segoe UI", Font.BOLD, 14));
        txtPassword.setForeground(new Color(37, 99, 235));
        txtPassword.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyReleased(java.awt.event.KeyEvent e) {
                String text = txtPassword.getText();
                if (!text.equals(text.toLowerCase())) {
                    int pos = txtPassword.getCaretPosition();
                    txtPassword.setText(text.toLowerCase());
                    txtPassword.setCaretPosition(Math.min(pos, txtPassword.getText().length()));
                }
            }
        });
        configPanel.add(txtPassword, gbc);

        gbc.gridx = 2; gbc.gridy = 2; gbc.weightx = 0.25;
        btnGeneratePass = new JButton("Đổi Pass");
        btnGeneratePass.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnGeneratePass.addActionListener(e -> generateRandomPassword());
        configPanel.add(btnGeneratePass, gbc);

        // Dòng 4: Web Platform URL
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.2; gbc.gridwidth = 1;
        JLabel lblWebText = new JLabel("Web Platform:");
        lblWebText.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblWebText.setForeground(new Color(79, 70, 229));
        configPanel.add(lblWebText, gbc);

        gbc.gridx = 1; gbc.gridy = 3; gbc.weightx = 0.55;
        txtWebUrl = new JTextField("http://127.0.0.1:" + webPort);
        txtWebUrl.setEditable(false);
        txtWebUrl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        txtWebUrl.setForeground(new Color(30, 41, 59));
        txtWebUrl.setBackground(new Color(238, 242, 255));
        configPanel.add(txtWebUrl, gbc);

        gbc.gridx = 2; gbc.gridy = 3; gbc.weightx = 0.25;
        btnOpenWeb = new JButton("Mở Web Client");
        btnOpenWeb.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnOpenWeb.setForeground(Color.BLACK);
        btnOpenWeb.addActionListener(e -> openWebInBrowser());
        configPanel.add(btnOpenWeb, gbc);

        // Dòng 5: Nút Start và Stop
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 3; gbc.weightx = 1.0;
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 5));
        btnPanel.setOpaque(false);

        btnStart = new JButton("Khởi động Server");
        btnStart.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnStart.setForeground(Color.BLACK);
        btnStart.setFocusPainted(false);
        btnStart.setPreferredSize(new Dimension(170, 36));
        btnStart.addActionListener(e -> startServer());

        btnStop = new JButton("Dừng Server");
        btnStop.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnStop.setForeground(Color.BLACK);
        btnStop.setFocusPainted(false);
        btnStop.setEnabled(false);
        btnStop.setPreferredSize(new Dimension(170, 36));
        btnStop.addActionListener(e -> stopServer());

        btnPanel.add(btnStart);
        btnPanel.add(btnStop);
        configPanel.add(btnPanel, gbc);

        centerPanel.add(configPanel, BorderLayout.NORTH);

        // Phần nhật ký Log
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225)), 
                " Nhật ký kết nối (Logs) ", 
                0, 0, new Font("Segoe UI", Font.BOLD, 13), new Color(71, 85, 105)));
        logPanel.setOpaque(false);

        txtLog = new JTextArea();
        txtLog.setEditable(false);
        txtLog.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtLog.setBackground(new Color(248, 250, 252));
        JScrollPane scrollLog = new JScrollPane(txtLog);
        logPanel.add(scrollLog, BorderLayout.CENTER);

        centerPanel.add(logPanel, BorderLayout.CENTER);
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        setContentPane(mainPanel);
    }

    /**
     * Tự động lấy địa chỉ IP LAN thật của máy, lọc bỏ card mạng ảo
     */
    private void loadLocalIP() {
        try {
            String bestIp = null;
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface nif = interfaces.nextElement();
                if (nif.isLoopback() || !nif.isUp()) continue;
                String dName = nif.getDisplayName().toLowerCase();
                String name = nif.getName().toLowerCase();
                // Bỏ qua card ảo
                if (dName.contains("virtual") || dName.contains("vmware") || dName.contains("vbox")
                        || dName.contains("wsl") || dName.contains("docker") || name.contains("veth")) {
                    continue;
                }
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address) {
                        String ipStr = addr.getHostAddress();
                        if (ipStr.startsWith("192.168.") || ipStr.startsWith("10.") || ipStr.startsWith("172.")) {
                            bestIp = ipStr;
                            break;
                        } else if (bestIp == null) {
                            bestIp = ipStr;
                        }
                    }
                }
                if (bestIp != null && (bestIp.startsWith("192.168.") || bestIp.startsWith("10."))) {
                    break;
                }
            }
            if (bestIp == null) {
                bestIp = InetAddress.getLocalHost().getHostAddress();
            }
            txtIp.setText(bestIp);
            updateWebUrl();
        } catch (Exception e) {
            txtIp.setText("127.0.0.1");
            updateWebUrl();
        }
    }

    private void updateWebUrl() {
        if (txtWebUrl != null && txtIp != null) {
            txtWebUrl.setText("http://" + txtIp.getText().trim() + ":" + webPort);
        }
    }

    public void openWebInBrowser() {
        try {
            Desktop.getDesktop().browse(new java.net.URI("http://localhost:" + webPort));
            appendLog("Đã mở Web Remote Client trên trình duyệt mặc định.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Không thể tự mở trình duyệt: " + ex.getMessage());
        }
    }

    /**
     * Khởi động luồng UDP lắng nghe yêu cầu dò tìm tự động từ Máy Khách trong cùng mạng LAN
     */
    private void startDiscoveryResponder(int serverPort) {
        try {
            discoverySocket = new DatagramSocket(8888);
            discoveryThread = new Thread(() -> {
                byte[] buffer = new byte[1024];
                while (isRunning && discoverySocket != null && !discoverySocket.isClosed()) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        discoverySocket.receive(packet);
                        String msg = new String(packet.getData(), 0, packet.getLength()).trim();
                        if ("DISCOVER_SERVER".equals(msg)) {
                            String hostName = InetAddress.getLocalHost().getHostName();
                            String serverIp = txtIp.getText().trim();
                            String response = "SERVER_FOUND|" + hostName + "|" + serverIp + "|" + serverPort;
                            byte[] respBytes = response.getBytes();
                            DatagramPacket respPacket = new DatagramPacket(respBytes, respBytes.length, packet.getAddress(), packet.getPort());
                            discoverySocket.send(respPacket);
                            appendLog("Đã phản hồi yêu cầu dò tìm LAN từ: " + packet.getAddress().getHostAddress());
                        }
                    } catch (IOException e) {
                        break;
                    }
                }
            }, "Server-LAN-Discovery-Responder");
            discoveryThread.setDaemon(true);
            discoveryThread.start();
            appendLog("Kênh phát hiện tự động trong LAN (UDP 8888) đã sẵn sàng.");
        } catch (Exception ex) {
            appendLog("Cổng dò tìm LAN (8888) không thể mở: " + ex.getMessage());
        }
    }

    /**
     * Tạo mật khẩu ngẫu nhiên 6 ký tự (các ký tự chữ luôn được viết thường)
     */
    private void generateRandomPassword() {
        String chars = "abcdefghjklmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder();
        Random rnd = new Random();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        txtPassword.setText(sb.toString().toLowerCase());
        appendLog("Đã tạo mật khẩu mới: " + txtPassword.getText());
    }

    public void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            String time = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
            txtLog.append("[" + time + "] " + message + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    /**
     * Bắt đầu mở ServerSocket
     */
    private void startServer() {
        int screenPort;
        try {
            screenPort = Integer.parseInt(txtPort.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Cổng (Port) không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        final int cmdPort = screenPort + 1; // Mở 2 port độc lập để tránh stream blocking
        final String serverPassword = txtPassword.getText().trim();

        isRunning = true;
        btnStart.setEnabled(false);
        btnStop.setEnabled(true);
        btnGeneratePass.setEnabled(false);
        txtPort.setEditable(false);
        txtPassword.setEditable(false);

        lblStatus.setText("● Đang lắng nghe cổng: " + screenPort + " & " + cmdPort);
        lblStatus.setForeground(new Color(22, 163, 74));
        appendLog("Server đang khởi động trên Port ảnh: " + screenPort + ", Port lệnh: " + cmdPort);

        serverAcceptThread = new Thread(() -> {
            try {
                screenServerSocket = new ServerSocket(screenPort);
                commandServerSocket = new ServerSocket(cmdPort);

                while (isRunning) {
                    appendLog("Chờ kết nối từ Client...");
                    Socket screenSock = screenServerSocket.accept();
                    appendLog("Client kết nối tới cổng màn hình từ: " + screenSock.getInetAddress().getHostAddress());

                    // Đọc mật khẩu xác thực từ Client trước khi truyền hình ảnh
                    DataInputStream dis = new DataInputStream(screenSock.getInputStream());
                    DataOutputStream dos = new DataOutputStream(screenSock.getOutputStream());
                    String clientPass = dis.readUTF();

                    if (!serverPassword.equalsIgnoreCase(clientPass.trim())) {
                        appendLog("Từ chối: Mật khẩu từ " + screenSock.getInetAddress().getHostAddress() + " không đúng!");
                        dos.writeUTF("AUTH_FAILED");
                        dos.flush();
                        screenSock.close();
                        continue;
                    }

                    dos.writeUTF("AUTH_SUCCESS");
                    
                    // Gửi kích thước màn hình của Server để Client ánh xạ tọa độ chuẩn xác
                    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                    dos.writeInt(screenSize.width);
                    dos.writeInt(screenSize.height);
                    dos.flush();

                    appendLog("Xác thực THÀNH CÔNG! Đang kết nối kênh lệnh...");
                    Socket cmdSock = commandServerSocket.accept();
                    appendLog("Kênh lệnh đã kết nối từ: " + cmdSock.getInetAddress().getHostAddress());

                    clientScreenSocket = screenSock;
                    clientCommandSocket = cmdSock;

                    // Khởi động luồng chụp & truyền màn hình (ScreenCaster)
                    currentScreenCaster = new ScreenCaster(dos, this);
                    new Thread(currentScreenCaster, "ScreenCaster-Thread").start();

                    // Khởi động luồng nhận và thực thi sự kiện chuột/bàn phím (EventHandler)
                    currentEventHandler = new EventHandler(cmdSock.getInputStream(), this);
                    new Thread(currentEventHandler, "EventHandler-Thread").start();
                }
            } catch (IOException e) {
                if (isRunning) {
                    appendLog("Lỗi Socket Server: " + e.getMessage());
                }
            }
        }, "Server-Accept-Thread");

        startDiscoveryResponder(screenPort);
        serverAcceptThread.start();

        // Khởi động Web Remote Server (WebSocket + HTTP)
        try {
            webRemoteServer = new WebRemoteServer(webPort, serverPassword, this);
            new Thread(webRemoteServer, "WebRemoteServer-Thread").start();
            appendLog("Nền tảng Web Client sẵn sàng tại: http://" + txtIp.getText().trim() + ":" + webPort);
        } catch (Exception ex) {
            appendLog("Lỗi khởi động Web Server: " + ex.getMessage());
        }
    }

    /**
     * Dừng Server
     */
    public void stopServer() {
        isRunning = false;
        try {
            if (webRemoteServer != null) {
                webRemoteServer.stop();
                webRemoteServer = null;
            }
            if (discoverySocket != null && !discoverySocket.isClosed()) {
                discoverySocket.close();
                discoverySocket = null;
            }
            if (currentScreenCaster != null) {
                currentScreenCaster.stop();
                currentScreenCaster = null;
            }
            if (currentEventHandler != null) {
                currentEventHandler.stop();
                currentEventHandler = null;
            }
            if (clientScreenSocket != null && !clientScreenSocket.isClosed()) {
                clientScreenSocket.close();
            }
            if (clientCommandSocket != null && !clientCommandSocket.isClosed()) {
                clientCommandSocket.close();
            }
            if (screenServerSocket != null && !screenServerSocket.isClosed()) {
                screenServerSocket.close();
            }
            if (commandServerSocket != null && !commandServerSocket.isClosed()) {
                commandServerSocket.close();
            }
        } catch (Exception ex) {
            appendLog("Lỗi khi dừng server: " + ex.getMessage());
        }

        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        btnGeneratePass.setEnabled(true);
        txtPort.setEditable(true);
        txtPassword.setEditable(true);
        lblStatus.setText("● Trạng thái: Đã dừng");
        lblStatus.setForeground(new Color(220, 38, 38));
        appendLog("Server đã dừng.");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ServerDashboard().setVisible(true);
        });
    }
}
