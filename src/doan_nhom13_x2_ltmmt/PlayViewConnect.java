/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
package doan_nhom13_x2_ltmmt;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Random;
import java.awt.Desktop;
import java.io.File;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * PlayViewConnect - Giao diện hợp nhất TeamViewer / AnyDesk Style
 * Hỗ trợ đồng thời 2 vai trò:
 * 1. Máy Chủ (Server - Máy bị điều khiển): Hiển thị IP, Port, Random Pass, Start/Stop và Khung Log.
 * 2. Máy Khách (Client - Đi điều khiển): Nhập IP, Port, Pass và Kết nối để mở RemoteViewerFrame.
 * 
 * Thiết kế chuẩn NetBeans JFrame Form (tương thích hoàn toàn với file PlayViewConnect.form).
 */
public class PlayViewConnect extends javax.swing.JFrame implements ServerLogger, ViewerCallback {

    // Quản lý kết nối mạng phía Server
    private ServerSocket screenServerSocket;
    private ServerSocket commandServerSocket;
    private volatile boolean isServerRunning = false;
    private Thread serverAcceptThread;
    private ScreenCaster currentScreenCaster;
    private EventHandler currentEventHandler;
    private Socket clientScreenSocket;
    private Socket clientCommandSocket;

    // Máy chủ Web tích hợp (Web Platform)
    private WebRemoteServer webRemoteServer;
    private final int webPort = 8080;

    // Quản lý cửa sổ điều khiển phía Client
    private RemoteViewerFrame currentViewerFrame;

    /**
     * Creates new form PlayViewConnect
     */
    public PlayViewConnect() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        initComponents();
        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                stopServer();
            }
        });
        setLocationRelativeTo(null);

        // Đảm bảo thư mục nhận file ở Downloads của máy chủ luôn sẵn sàng và KHÔNG bị ẩn
        File receivedDir = new File(System.getProperty("user.home"), "Downloads" + File.separator + "RemoteDesktop_Received");
        if (!receivedDir.exists()) {
            receivedDir.mkdirs();
        }
        WebInputHandler.ensureVisible(receivedDir);

        // Khởi tạo thông tin mặc định cho Server
        loadLocalIP();
        generateRandomPassword();
    }

    /**
     * Tự động quét và hiển thị IP của máy
     */
    private void loadLocalIP() {
        try {
            StringBuilder sb = new StringBuilder();
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface nif = interfaces.nextElement();
                if (nif.isLoopback() || !nif.isUp()) continue;
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address) {
                        if (sb.length() > 0) sb.append(" / ");
                        sb.append(addr.getHostAddress());
                    }
                }
            }
            if (sb.length() == 0) {
                txtServerIp.setText(InetAddress.getLocalHost().getHostAddress());
            } else {
                txtServerIp.setText(sb.toString());
            }
        } catch (Exception e) {
            txtServerIp.setText("127.0.0.1");
        }
    }

    /**
     * Sinh mật khẩu ngẫu nhiên 6 ký tự
     */
    private void generateRandomPassword() {
        String chars = "abcdefghjklmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder();
        Random rnd = new Random();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        txtServerPassword.setText(sb.toString().toLowerCase());
        appendLog("Đã tạo mật khẩu bảo mật mới: " + txtServerPassword.getText());
    }

    @Override
    public void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
            txtLog.append("[" + time + "] " + message + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    /**
     * Callback khi cửa sổ RemoteViewerFrame bị đóng
     */
    @Override
    public void onViewerClosed() {
        SwingUtilities.invokeLater(() -> {
            btnClientConnect.setEnabled(true);
            lblClientStatus.setText("Đã ngắt kết nối với máy chủ.");
            lblClientStatus.setForeground(new Color(71, 85, 105));
            setVisible(true);
        });
    }

    /**
     * Bắt đầu chạy Server
     */
    private void startServer() {
        int screenPort;
        try {
            screenPort = Integer.parseInt(txtServerPort.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Cổng (Port) không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        final int cmdPort = screenPort + 1;
        final String serverPassword = txtServerPassword.getText().trim();

        isServerRunning = true;
        btnStartServer.setEnabled(false);
        btnStopServer.setEnabled(true);
        btnGenPass.setEnabled(false);
        txtServerPort.setEditable(false);
        txtServerPassword.setEditable(false);

        lblServerStatus.setText("● Đang lắng nghe: Port " + screenPort + " & " + cmdPort);
        lblServerStatus.setForeground(new Color(22, 163, 74));
        appendLog("Khởi động Server thành công trên Port ảnh: " + screenPort + ", Port lệnh: " + cmdPort);
        File recDir = new File(System.getProperty("user.home"), "Downloads" + File.separator + "RemoteDesktop_Received");
        appendLog("📁 Thư mục lưu tệp nhận: " + recDir.getAbsolutePath());

        serverAcceptThread = new Thread(() -> {
            try {
                screenServerSocket = new ServerSocket(screenPort);
                commandServerSocket = new ServerSocket(cmdPort);

                while (isServerRunning) {
                    appendLog("Đang chờ kết nối từ Client...");
                    Socket screenSock = screenServerSocket.accept();
                    appendLog("Nhận kết nối từ Client: " + screenSock.getInetAddress().getHostAddress());

                    DataInputStream dis = new DataInputStream(screenSock.getInputStream());
                    DataOutputStream dos = new DataOutputStream(screenSock.getOutputStream());
                    String clientPass = dis.readUTF();

                    if (!serverPassword.equalsIgnoreCase(clientPass.trim())) {
                        appendLog("Từ chối kết nối: Mật khẩu từ " + screenSock.getInetAddress().getHostAddress() + " không đúng!");
                        dos.writeUTF("AUTH_FAILED");
                        dos.flush();
                        screenSock.close();
                        continue;
                    }

                    dos.writeUTF("AUTH_SUCCESS");

                    // Gửi kích thước màn hình Server
                    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                    dos.writeInt(screenSize.width);
                    dos.writeInt(screenSize.height);
                    dos.flush();

                    appendLog("Xác thực THÀNH CÔNG! Đang kết nối kênh lệnh...");
                    Socket cmdSock = commandServerSocket.accept();
                    if (!cmdSock.getInetAddress().equals(screenSock.getInetAddress())) {
                        appendLog("[CẢNH BÁO BẢO MẬT] Từ chối kênh lệnh từ IP lạ (" + cmdSock.getInetAddress().getHostAddress() + ")");
                        cmdSock.close();
                        continue;
                    }
                    appendLog("Kênh lệnh đã kết nối thành công từ: " + cmdSock.getInetAddress().getHostAddress());

                    clientScreenSocket = screenSock;
                    clientCommandSocket = cmdSock;

                    // Khởi chạy ScreenCaster và EventHandler
                    currentScreenCaster = new ScreenCaster(dos, this);
                    new Thread(currentScreenCaster, "PlayView-ScreenCaster").start();

                    currentEventHandler = new EventHandler(cmdSock.getInputStream(), this);
                    new Thread(currentEventHandler, "PlayView-EventHandler").start();
                }
            } catch (IOException e) {
                if (isServerRunning) {
                    appendLog("Lỗi ServerSocket: " + e.getMessage());
                }
            }
        }, "PlayView-ServerAccept-Thread");

        serverAcceptThread.start();

        // Khởi động Web Remote Platform
        try {
            webRemoteServer = new WebRemoteServer(webPort, serverPassword, this);
            new Thread(webRemoteServer, "PlayView-WebRemoteServer").start();
            appendLog("Nền tảng Web Client sẵn sàng tại: http://" + txtServerIp.getText().trim() + ":" + webPort);
        } catch (Exception ex) {
            appendLog("Lỗi mở Web Server: " + ex.getMessage());
        }
    }

    /**
     * Dừng Server
     */
    private void stopServer() {
        isServerRunning = false;
        try {
            if (webRemoteServer != null) {
                webRemoteServer.stop();
                webRemoteServer = null;
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

        btnStartServer.setEnabled(true);
        btnStopServer.setEnabled(false);
        btnGenPass.setEnabled(true);
        txtServerPort.setEditable(true);
        txtServerPassword.setEditable(true);
        lblServerStatus.setText("● Trạng thái: Đã dừng");
        lblServerStatus.setForeground(new Color(220, 38, 38));
        appendLog("Server đã dừng.");
    }

    /**
     * Khởi động kết nối Client đến Server
     */
    private void startClientConnect() {
        final String host = txtClientHost.getText().trim();
        final String portStr = txtClientPort.getText().trim();
        final String password = new String(txtClientPassword.getPassword()).trim().toLowerCase();

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

        btnClientConnect.setEnabled(false);
        lblClientStatus.setText("Đang kết nối tới " + host + ":" + port + "...");
        lblClientStatus.setForeground(new Color(37, 99, 235));

        new Thread(() -> {
            try {
                // 1. Kết nối kênh truyền màn hình
                Socket screenSocket = new Socket(host, port);
                DataOutputStream dos = new DataOutputStream(screenSocket.getOutputStream());
                DataInputStream dis = new DataInputStream(screenSocket.getInputStream());

                dos.writeUTF(password);
                dos.flush();

                String authResponse = dis.readUTF();
                if (!"AUTH_SUCCESS".equals(authResponse)) {
                    screenSocket.close();
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, "Mật khẩu không chính xác!", "Lỗi xác thực", JOptionPane.ERROR_MESSAGE);
                        btnClientConnect.setEnabled(true);
                        lblClientStatus.setText("Xác thực thất bại.");
                        lblClientStatus.setForeground(new Color(220, 38, 38));
                    });
                    return;
                }

                int serverWidth = dis.readInt();
                int serverHeight = dis.readInt();

                // 2. Kết nối kênh truyền lệnh (Port + 1)
                Socket commandSocket = new Socket(host, port + 1);
                PrintWriter commandWriter = new PrintWriter(new OutputStreamWriter(commandSocket.getOutputStream()), true);

                SwingUtilities.invokeLater(() -> {
                    lblClientStatus.setText("Kết nối thành công! Đang mở cửa sổ điều khiển...");
                    lblClientStatus.setForeground(new Color(22, 163, 74));

                    currentViewerFrame = new RemoteViewerFrame(this, screenSocket, commandSocket, commandWriter, serverWidth, serverHeight);
                    currentViewerFrame.setVisible(true);
                    currentViewerFrame.startReceiving();

                    setVisible(false);
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Không thể kết nối đến Server:\n" + ex.getMessage(), "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
                    btnClientConnect.setEnabled(true);
                    lblClientStatus.setText("Lỗi kết nối.");
                    lblClientStatus.setForeground(new Color(220, 38, 38));
                });
            }
        }, "PlayView-ClientConnect-Thread").start();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gbc;

        tabbedPane = new javax.swing.JTabbedPane();
        panelServer = new javax.swing.JPanel();
        panelServerHeader = new javax.swing.JPanel();
        lblServerTitle = new javax.swing.JLabel();
        lblServerStatus = new javax.swing.JLabel();
        panelServerCenter = new javax.swing.JPanel();
        panelServerForm = new javax.swing.JPanel();
        lblServerIp = new javax.swing.JLabel();
        txtServerIp = new javax.swing.JTextField();
        lblServerPort = new javax.swing.JLabel();
        txtServerPort = new javax.swing.JTextField();
        lblServerPass = new javax.swing.JLabel();
        txtServerPassword = new javax.swing.JTextField();
        btnGenPass = new javax.swing.JButton();
        panelServerButtons = new javax.swing.JPanel();
        btnStartServer = new javax.swing.JButton();
        btnStopServer = new javax.swing.JButton();
        scrollLog = new javax.swing.JScrollPane();
        txtLog = new javax.swing.JTextArea();
        panelClient = new javax.swing.JPanel();
        panelClientHeader = new javax.swing.JPanel();
        lblClientTitle = new javax.swing.JLabel();
        lblClientStatus = new javax.swing.JLabel();
        panelClientCenter = new javax.swing.JPanel();
        panelClientForm = new javax.swing.JPanel();
        lblClientHost = new javax.swing.JLabel();
        txtClientHost = new javax.swing.JTextField();
        lblClientPort = new javax.swing.JLabel();
        txtClientPort = new javax.swing.JTextField();
        lblClientPass = new javax.swing.JLabel();
        txtClientPassword = new javax.swing.JPasswordField();
        panelClientButton = new javax.swing.JPanel();
        btnClientConnect = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Remote Desktop - PlayViewConnect (TeamViewer / AnyDesk Style)");
        setPreferredSize(new java.awt.Dimension(680, 560));

        tabbedPane.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 13));

        // ================= TAB SERVER =================
        panelServer.setBorder(new javax.swing.border.EmptyBorder(15, 15, 15, 15));
        panelServer.setLayout(new java.awt.BorderLayout(10, 10));

        panelServerHeader.setLayout(new java.awt.BorderLayout());
        lblServerTitle.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 16));
        lblServerTitle.setForeground(new java.awt.Color(30, 41, 59));
        lblServerTitle.setText("THÔNG TIN MÁY CHỦ (SERVER)");
        panelServerHeader.add(lblServerTitle, java.awt.BorderLayout.WEST);

        lblServerStatus.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        lblServerStatus.setForeground(new java.awt.Color(220, 38, 38));
        lblServerStatus.setText("● Trạng thái: Chưa hoạt động");
        panelServerHeader.add(lblServerStatus, java.awt.BorderLayout.EAST);

        panelServer.add(panelServerHeader, java.awt.BorderLayout.NORTH);

        panelServerCenter.setLayout(new java.awt.BorderLayout(8, 8));

        panelServerForm.setBorder(javax.swing.BorderFactory.createTitledBorder(
                null, "Cấu hình kết nối & Bảo mật", 
                javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, 
                javax.swing.border.TitledBorder.DEFAULT_POSITION, 
                new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12)));
        panelServerForm.setLayout(new java.awt.GridBagLayout());

        // IP
        lblServerIp.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 13));
        lblServerIp.setText("IP máy này:");
        gbc = new java.awt.GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.anchor = java.awt.GridBagConstraints.WEST;
        gbc.insets = new java.awt.Insets(6, 8, 6, 8);
        panelServerForm.add(lblServerIp, gbc);

        txtServerIp.setEditable(false);
        txtServerIp.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 13));
        gbc = new java.awt.GridBagConstraints();
        gbc.gridx = 1; gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new java.awt.Insets(6, 8, 6, 8);
        panelServerForm.add(txtServerIp, gbc);

        // Port
        lblServerPort.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 13));
        lblServerPort.setText("Port:");
        gbc = new java.awt.GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.anchor = java.awt.GridBagConstraints.WEST;
        gbc.insets = new java.awt.Insets(6, 8, 6, 8);
        panelServerForm.add(lblServerPort, gbc);

        txtServerPort.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 13));
        txtServerPort.setText("9999");
        gbc = new java.awt.GridBagConstraints();
        gbc.gridx = 1; gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new java.awt.Insets(6, 8, 6, 8);
        panelServerForm.add(txtServerPort, gbc);

        // Password
        lblServerPass.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 13));
        lblServerPass.setText("Mật khẩu:");
        gbc = new java.awt.GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.anchor = java.awt.GridBagConstraints.WEST;
        gbc.insets = new java.awt.Insets(6, 8, 6, 8);
        panelServerForm.add(lblServerPass, gbc);

        txtServerPassword.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 14));
        txtServerPassword.setForeground(new java.awt.Color(37, 99, 235));
        txtServerPassword.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyReleased(java.awt.event.KeyEvent e) {
                String text = txtServerPassword.getText();
                if (!text.equals(text.toLowerCase())) {
                    int pos = txtServerPassword.getCaretPosition();
                    txtServerPassword.setText(text.toLowerCase());
                    txtServerPassword.setCaretPosition(Math.min(pos, txtServerPassword.getText().length()));
                }
            }
        });
        gbc = new java.awt.GridBagConstraints();
        gbc.gridx = 1; gbc.gridy = 2;
        gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.7;
        gbc.insets = new java.awt.Insets(6, 8, 6, 8);
        panelServerForm.add(txtServerPassword, gbc);

        btnGenPass.setText("Đổi Pass");
        btnGenPass.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
        btnGenPass.addActionListener(evt -> btnGenPassActionPerformed(evt));
        gbc = new java.awt.GridBagConstraints();
        gbc.gridx = 2; gbc.gridy = 2;
        gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.3;
        gbc.insets = new java.awt.Insets(6, 8, 6, 8);
        panelServerForm.add(btnGenPass, gbc);

        // Buttons
        panelServerButtons.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 15, 5));
        btnStartServer.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 13));
        btnStartServer.setText("Khởi Động Server");
        btnStartServer.setPreferredSize(new java.awt.Dimension(170, 36));
        btnStartServer.addActionListener(evt -> btnStartServerActionPerformed(evt));
        panelServerButtons.add(btnStartServer);

        btnStopServer.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 13));
        btnStopServer.setText("Dừng Server");
        btnStopServer.setEnabled(false);
        btnStopServer.setPreferredSize(new java.awt.Dimension(170, 36));
        btnStopServer.addActionListener(evt -> btnStopServerActionPerformed(evt));
        panelServerButtons.add(btnStopServer);

        btnOpenReceivedFiles = new javax.swing.JButton();
        btnOpenReceivedFiles.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 13));
        btnOpenReceivedFiles.setText("📁 Tệp Đã Nhận");
        btnOpenReceivedFiles.setToolTipText("Mở thư mục Downloads/RemoteDesktop_Received lưu các file nhận từ Client");
        btnOpenReceivedFiles.setPreferredSize(new java.awt.Dimension(150, 36));
        btnOpenReceivedFiles.setBackground(new java.awt.Color(241, 245, 249));
        btnOpenReceivedFiles.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnOpenReceivedFiles.addActionListener(evt -> {
            try {
                File dir = new File(System.getProperty("user.home"), "Downloads" + File.separator + "RemoteDesktop_Received");
                if (!dir.exists()) dir.mkdirs();
                Desktop.getDesktop().open(dir);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Không thể mở thư mục: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });
        panelServerButtons.add(btnOpenReceivedFiles);

        gbc = new java.awt.GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.gridwidth = 3;
        gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gbc.insets = new java.awt.Insets(10, 8, 10, 8);
        panelServerForm.add(panelServerButtons, gbc);

        panelServerCenter.add(panelServerForm, java.awt.BorderLayout.NORTH);

        // Log
        scrollLog.setBorder(javax.swing.BorderFactory.createTitledBorder(
                null, "Nhật ký kết nối (Logs)", 
                javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, 
                javax.swing.border.TitledBorder.DEFAULT_POSITION, 
                new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12)));
        txtLog.setEditable(false);
        txtLog.setColumns(20);
        txtLog.setFont(new java.awt.Font("Consolas", java.awt.Font.PLAIN, 12));
        txtLog.setRows(6);
        scrollLog.setViewportView(txtLog);
        panelServerCenter.add(scrollLog, java.awt.BorderLayout.CENTER);

        panelServer.add(panelServerCenter, java.awt.BorderLayout.CENTER);

        tabbedPane.addTab("Máy Chủ (Server - Bị điều khiển)", panelServer);

        // ================= TAB CLIENT =================
        panelClient.setBorder(new javax.swing.border.EmptyBorder(15, 15, 15, 15));
        panelClient.setLayout(new java.awt.BorderLayout(10, 10));

        panelClientHeader.setLayout(new java.awt.BorderLayout());
        lblClientTitle.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 16));
        lblClientTitle.setForeground(new java.awt.Color(30, 41, 59));
        lblClientTitle.setText("KẾT NỐI MÁY TÍNH TỪ XA (CLIENT)");
        panelClientHeader.add(lblClientTitle, java.awt.BorderLayout.WEST);

        lblClientStatus.setFont(new java.awt.Font("Segoe UI", java.awt.Font.ITALIC, 12));
        lblClientStatus.setForeground(new java.awt.Color(71, 85, 105));
        lblClientStatus.setText("Sẵn sàng kết nối...");
        panelClientHeader.add(lblClientStatus, java.awt.BorderLayout.EAST);

        panelClient.add(panelClientHeader, java.awt.BorderLayout.NORTH);

        panelClientCenter.setLayout(new java.awt.BorderLayout(10, 10));

        panelClientForm.setBorder(javax.swing.BorderFactory.createTitledBorder(
                null, "Nhập thông tin kết nối đến Server", 
                javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, 
                javax.swing.border.TitledBorder.DEFAULT_POSITION, 
                new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12)));
        panelClientForm.setLayout(new java.awt.GridBagLayout());

        lblClientHost.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 13));
        lblClientHost.setText("Địa chỉ IP Server:");
        gbc = new java.awt.GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.anchor = java.awt.GridBagConstraints.WEST;
        gbc.insets = new java.awt.Insets(8, 8, 8, 8);
        panelClientForm.add(lblClientHost, gbc);

        txtClientHost.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 13));
        txtClientHost.setText("127.0.0.1");
        gbc = new java.awt.GridBagConstraints();
        gbc.gridx = 1; gbc.gridy = 0;
        gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new java.awt.Insets(8, 8, 8, 8);
        panelClientForm.add(txtClientHost, gbc);

        lblClientPort.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 13));
        lblClientPort.setText("Cổng (Port):");
        gbc = new java.awt.GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.anchor = java.awt.GridBagConstraints.WEST;
        gbc.insets = new java.awt.Insets(8, 8, 8, 8);
        panelClientForm.add(lblClientPort, gbc);

        txtClientPort.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 13));
        txtClientPort.setText("9999");
        gbc = new java.awt.GridBagConstraints();
        gbc.gridx = 1; gbc.gridy = 1;
        gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new java.awt.Insets(8, 8, 8, 8);
        panelClientForm.add(txtClientPort, gbc);

        lblClientPass.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 13));
        lblClientPass.setText("Mật khẩu:");
        gbc = new java.awt.GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.anchor = java.awt.GridBagConstraints.WEST;
        gbc.insets = new java.awt.Insets(8, 8, 8, 8);
        panelClientForm.add(lblClientPass, gbc);

        txtClientPassword.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 13));
        gbc = new java.awt.GridBagConstraints();
        gbc.gridx = 1; gbc.gridy = 2;
        gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new java.awt.Insets(8, 8, 8, 8);
        panelClientForm.add(txtClientPassword, gbc);

        panelClientCenter.add(panelClientForm, java.awt.BorderLayout.NORTH);

        panelClientButton.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 15, 20));
        btnClientConnect = new javax.swing.JButton();
        btnClientConnect.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 14));
        btnClientConnect.setText("KẾT NỐI NGAY (CONNECT)");
        btnClientConnect.setPreferredSize(new java.awt.Dimension(240, 42));
        btnClientConnect.addActionListener(evt -> btnClientConnectActionPerformed(evt));
        panelClientButton.add(btnClientConnect);

        panelClientCenter.add(panelClientButton, java.awt.BorderLayout.CENTER);

        panelClient.add(panelClientCenter, java.awt.BorderLayout.CENTER);

        tabbedPane.addTab("Máy Khách (Client - Đi điều khiển)", panelClient);

        getContentPane().add(tabbedPane);

        pack();
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void btnGenPassActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnGenPassActionPerformed
        generateRandomPassword();
    }//GEN-LAST:event_btnGenPassActionPerformed

    private void btnStartServerActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnStartServerActionPerformed
        startServer();
    }//GEN-LAST:event_btnStartServerActionPerformed

    private void btnStopServerActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnStopServerActionPerformed
        stopServer();
    }//GEN-LAST:event_btnStopServerActionPerformed

    private void btnClientConnectActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnClientConnectActionPerformed
        startClientConnect();
    }//GEN-LAST:event_btnClientConnectActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the System / Nimbus look and feel */
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new PlayViewConnect().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnClientConnect;
    private javax.swing.JButton btnGenPass;
    private javax.swing.JButton btnOpenReceivedFiles;
    private javax.swing.JButton btnStartServer;
    private javax.swing.JButton btnStopServer;
    private javax.swing.JLabel lblClientHost;
    private javax.swing.JLabel lblClientPass;
    private javax.swing.JLabel lblClientPort;
    private javax.swing.JLabel lblClientStatus;
    private javax.swing.JLabel lblClientTitle;
    private javax.swing.JLabel lblServerIp;
    private javax.swing.JLabel lblServerPass;
    private javax.swing.JLabel lblServerPort;
    private javax.swing.JLabel lblServerStatus;
    private javax.swing.JLabel lblServerTitle;
    private javax.swing.JPanel panelClient;
    private javax.swing.JPanel panelClientButton;
    private javax.swing.JPanel panelClientCenter;
    private javax.swing.JPanel panelClientForm;
    private javax.swing.JPanel panelClientHeader;
    private javax.swing.JPanel panelServer;
    private javax.swing.JPanel panelServerButtons;
    private javax.swing.JPanel panelServerCenter;
    private javax.swing.JPanel panelServerForm;
    private javax.swing.JPanel panelServerHeader;
    private javax.swing.JScrollPane scrollLog;
    private javax.swing.JTabbedPane tabbedPane;
    private javax.swing.JTextField txtClientHost;
    private javax.swing.JPasswordField txtClientPassword;
    private javax.swing.JTextField txtClientPort;
    private javax.swing.JTextArea txtLog;
    private javax.swing.JTextField txtServerIp;
    private javax.swing.JTextField txtServerPassword;
    private javax.swing.JTextField txtServerPort;
    // End of variables declaration//GEN-END:variables
}
