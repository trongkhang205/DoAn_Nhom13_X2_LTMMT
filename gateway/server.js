/**
 * Central Cloud Relay & Gateway Server
 * Đồ Án Lập Trình Mạng Máy Tính - Nhóm 13 (X2)
 *
 * Nhiệm vụ:
 * 1. Đóng vai trò máy chủ trung gian (Relay Broker) kết nối Máy Khách (Web Client)
 *    và Máy Bị Điều Khiển (Host Agent) qua Internet mà không cần mở port modem.
 * 2. Quản lý danh sách thiết bị trực tuyến (Device ID 9 chữ số như TeamViewer / AnyDesk).
 * 3. Chuyển tiếp luồng nhị phân (Binary Screen Streams) và lệnh điều khiển (JSON Commands).
 */

const http = require('http');
const fs = require('fs');
const path = require('path');
const { WebSocketServer, WebSocket } = require('ws');

const PORT = process.env.PORT || 3000;

// Bộ nhớ lưu các Agent đang kết nối: Map<deviceId, { ws, name, info, clients: Set<ws> }>
const hosts = new Map();

// Tạo HTTP Server
const server = http.createServer((req, res) => {
    const url = new URL(req.url, `http://${req.headers.host}`);

    // API: Lấy danh sách thiết bị đang Online
    if (url.pathname === '/api/devices') {
        const list = Array.from(hosts.entries()).map(([id, host]) => ({
            id,
            name: host.name || 'Máy tính từ xa',
            connectedAt: host.connectedAt,
            clientCount: host.clients.size
        }));
        res.writeHead(200, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
        return res.end(JSON.stringify(list));
    }

    // API: Trạng thái Gateway
    if (url.pathname === '/api/status') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({
            status: 'online',
            onlineHosts: hosts.size,
            uptime: process.uptime()
        }));
    }

    // API tương thích cho client từ xa
    if (url.pathname === '/api/server-info') {
        const firstEntry = hosts.entries().next().value;
        const onlineDeviceId = firstEntry ? firstEntry[0] : null;
        const hostName = firstEntry ? firstEntry[1].name : null;

        res.writeHead(200, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
        return res.end(JSON.stringify({
            ip: 'Cloud-Relay',
            port: PORT,
            isSharingEnabled: hosts.size > 0,
            onlineHosts: hosts.size,
            deviceId: onlineDeviceId,
            formattedDeviceId: onlineDeviceId ? (onlineDeviceId.slice(0, 3) + ' ' + onlineDeviceId.slice(3, 6) + ' ' + onlineDeviceId.slice(6, 9)) : null,
            hostName: hostName || 'Máy tính nhóm 13',
            activeClients: hosts.size,
            isTunnelActive: true
        }));
    }

    if (url.pathname === '/api/server-logs') {
        res.writeHead(200, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
        return res.end(JSON.stringify([]));
    }

    // Phục vụ giao diện Web (Chia sẻ thư mục web/ của dự án)
    let filePath = path.join(__dirname, '..', 'web', url.pathname === '/' ? 'index.html' : url.pathname);
    if (!fs.existsSync(filePath)) {
        filePath = path.join(__dirname, '..', 'web', 'index.html');
    }

    const ext = path.extname(filePath).toLowerCase();
    const mimeTypes = {
        '.html': 'text/html; charset=UTF-8',
        '.css': 'text/css; charset=UTF-8',
        '.js': 'application/javascript; charset=UTF-8',
        '.json': 'application/json',
        '.png': 'image/png',
        '.jpg': 'image/jpeg',
        '.svg': 'image/svg+xml'
    };

    fs.readFile(filePath, (err, content) => {
        if (err) {
            res.writeHead(404, { 'Content-Type': 'text/plain' });
            return res.end('404 Not Found');
        }
        res.writeHead(200, { 'Content-Type': mimeTypes[ext] || 'application/octet-stream' });
        res.end(content);
    });
});

// Tạo WebSocket Server
const wss = new WebSocketServer({ server });

wss.on('connection', (ws, req) => {
    const url = new URL(req.url, `http://${req.headers.host}`);
    let role = null; // 'host' hoặc 'client'
    let currentDeviceId = null;
    let pairedWs = null;

    console.log(`[+] Kết nối mới từ: ${req.socket.remoteAddress} (Path: ${url.pathname})`);

    ws.on('error', (err) => {
        console.error(`[WebSocket Error] Role: ${role || 'unknown'}, ID: ${currentDeviceId || 'none'}:`, err.message);
    });

    ws.on('message', (data, isBinary) => {
        // Nếu là Binary Stream (ảnh màn hình nén):
        if (isBinary) {
            if (role === 'host' && currentDeviceId) {
                // Host gửi màn hình -> chuyển tiếp tới tất cả máy khách đang kết nối tới Host này
                const host = hosts.get(currentDeviceId);
                if (host && host.clients) {
                    host.clients.forEach(client => {
                        if (client.readyState === WebSocket.OPEN) {
                            client.send(data, { binary: true });
                        }
                    });
                }
            } else if (role === 'client' && pairedWs && pairedWs.readyState === WebSocket.OPEN) {
                // Client gửi binary (nếu có) -> chuyển tiếp tới Host
                pairedWs.send(data, { binary: true });
            }
            return;
        }

        // Nếu là Text JSON Frame:
        try {
            const text = data.toString('utf-8');
            const msg = JSON.parse(text);

            // 1. Máy Host (Java Agent) Đăng ký Device ID
            if (msg.type === 'register_host') {
                role = 'host';
                currentDeviceId = String(msg.id).trim().replace(/\s+/g, '');
                const oldHost = hosts.get(currentDeviceId);
                const clients = (oldHost && oldHost.clients) ? oldHost.clients : new Set();
                hosts.set(currentDeviceId, {
                    ws: ws,
                    name: msg.name || 'Remote Host',
                    connectedAt: new Date().toISOString(),
                    clients: clients
                });
                console.log(`[Host Registered] ID: ${currentDeviceId} (${msg.name}) | Online hosts: ${hosts.size}`);
                ws.send(JSON.stringify({ type: 'registered', id: currentDeviceId, success: true }));
                return;
            }

            // 2. Máy Khách (Web Client) Yêu cầu kết nối vào Device ID
            if (msg.type === 'join_host') {
                role = 'client';
                currentDeviceId = String(msg.id).trim().replace(/\s+/g, '');
                const host = hosts.get(currentDeviceId);

                if (!host || !host.ws || host.ws.readyState !== WebSocket.OPEN) {
                    console.log(`[Join Failed] Host ID: ${currentDeviceId} không tìm thấy hoặc offline. Total hosts: ${hosts.size}`);
                    ws.send(JSON.stringify({
                        type: 'auth_result',
                        success: false,
                        message: `Không tìm thấy thiết bị có ID ${currentDeviceId} hoặc thiết bị đang ngoại tuyến!`
                    }));
                    return;
                }

                pairedWs = host.ws;
                host.clients.add(ws);

                // Chuyển tiếp yêu cầu xác thực sang Host
                pairedWs.send(JSON.stringify({
                    type: 'auth',
                    password: msg.password,
                    fromClient: true
                }));
                console.log(`[Relay Paired] Client paired to Host ID: ${currentDeviceId}`);
                return;
            }

            // 3. Chuyển tiếp thông điệp thông thường (auth_result, chuột, bàn phím, stats)
            if (role === 'host' && currentDeviceId) {
                // Host phản hồi auth_result hoặc ping -> gửi tới các client
                const host = hosts.get(currentDeviceId);
                if (host && host.clients) {
                    host.clients.forEach(client => {
                        if (client.readyState === WebSocket.OPEN) {
                            client.send(text);
                        }
                    });
                }
            } else if (role === 'client' && pairedWs && pairedWs.readyState === WebSocket.OPEN) {
                // Client gửi chuột, bàn phím -> gửi tới Host
                pairedWs.send(text);
            }
        } catch (e) {
            console.error('[Error handling message]', e.message);
        }
    });

    ws.on('close', () => {
        if (role === 'host' && currentDeviceId) {
            const host = hosts.get(currentDeviceId);
            // Chỉ gỡ bỏ khi chính websocket này là websocket hiện tại của host
            if (host && host.ws === ws) {
                console.log(`[-] Host ${currentDeviceId} đã ngắt kết nối.`);
                host.clients.forEach(client => {
                    if (client.readyState === WebSocket.OPEN) {
                        client.send(JSON.stringify({
                            type: 'disconnected',
                            message: 'Máy chủ từ xa đã ngắt kết nối khỏi Cloud Relay.'
                        }));
                        client.close();
                    }
                });
                hosts.delete(currentDeviceId);
            } else {
                console.log(`[-] Socket cũ của Host ${currentDeviceId} đóng (Host đã kết nối lại bằng socket mới).`);
            }
        } else if (role === 'client' && currentDeviceId) {
            console.log(`[-] Client ngắt kết nối khỏi Host ${currentDeviceId}.`);
            const host = hosts.get(currentDeviceId);
            if (host && host.clients) {
                host.clients.delete(ws);
            }
        }
    });
});

server.listen(PORT, () => {
    console.log('================================================================');
    console.log(`🚀 CLOUD RELAY & WEB GATEWAY SERVER ĐANG CHẠY TẠI: http://localhost:${PORT}`);
    console.log('   - Web Platform Portal: http://localhost:' + PORT);
    console.log('   - API Devices:        http://localhost:' + PORT + '/api/devices');
    console.log('   - WebSocket Relay:    ws://localhost:' + PORT);
    console.log('================================================================');
});
