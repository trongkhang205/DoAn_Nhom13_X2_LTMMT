/**
 * Nền tảng Điều Khiển Máy Tính Từ Xa - Engine Web Toàn Diện
 * Đồ Án Lập Trình Mạng Máy Tính • Nhóm 13 (X2)
 */

(function () {
    'use strict';

    // ================= DOM ELEMENTS =================
    // Navigation
    const navItems = document.querySelectorAll('.nav-item');
    const viewPanels = document.querySelectorAll('.view-panel');
    const navServerBadge = document.getElementById('navServerBadge');
    const serverStateDot = document.getElementById('serverStateDot');
    const navServerStatusText = document.getElementById('navServerStatusText');

    // View 1: Menu Launcher Cards
    const btnGoServer = document.getElementById('btnGoServer');
    const btnGoClient = document.getElementById('btnGoClient');
    const btnGoTest = document.getElementById('btnGoTest');

    // View 2: Server Dashboard
    const btnToggleServerShare = document.getElementById('btnToggleServerShare');
    const lblServerPower = document.getElementById('lblServerPower');
    const dispServerIp = document.getElementById('dispServerIp');
    const dispServerPort = document.getElementById('dispServerPort');
    const dispServerPass = document.getElementById('dispServerPass');
    const dispActiveClients = document.getElementById('dispActiveClients');
    const dispWebDirectUrl = document.getElementById('dispWebDirectUrl');
    const btnCopyIp = document.getElementById('btnCopyIp');
    const btnCopyPass = document.getElementById('btnCopyPass');
    const btnCopyWebUrl = document.getElementById('btnCopyWebUrl');
    const btnGenNewPass = document.getElementById('btnGenNewPass');
    const btnClearLogs = document.getElementById('btnClearLogs');
    const serverLogsConsole = document.getElementById('serverLogsConsole');

    // View 3: Client Connect
    const clientTargetIp = document.getElementById('clientTargetIp');
    const clientTargetPort = document.getElementById('clientTargetPort');
    const clientTargetPass = document.getElementById('clientTargetPass');
    const btnToggleClientEye = document.getElementById('btnToggleClientEye');
    const btnScanLan = document.getElementById('btnScanLan');
    const discoveredContainer = document.getElementById('discoveredContainer');
    const discoveredList = document.getElementById('discoveredList');
    const btnClientStartConnect = document.getElementById('btnClientStartConnect');
    const btnConnectSpinner = document.getElementById('btnConnectSpinner');
    const btnConnectLabel = document.getElementById('btnConnectLabel');
    const clientErrorMsg = document.getElementById('clientErrorMsg');

    // View 4: Remote Viewer Modal
    const remoteViewerModal = document.getElementById('remoteViewerModal');
    const floatingToolbar = document.getElementById('floatingToolbar');
    const canvasViewport = document.getElementById('canvasViewport');
    const remoteCanvas = document.getElementById('remoteCanvas');
    const ctx = remoteCanvas.getContext('2d', { alpha: false });
    const canvasLoading = document.getElementById('canvasLoading');
    const btnCloseViewer = document.getElementById('btnCloseViewer');

    // Stats & Toolbar Controls
    const valLatency = document.getElementById('valLatency');
    const latencyDot = document.getElementById('latencyDot');
    const valFps = document.getElementById('valFps');
    const valResolution = document.getElementById('valResolution');
    const btnToggleControl = document.getElementById('btnToggleControl');
    const lblControlMode = document.getElementById('lblControlMode');
    const btnQualityDropdown = document.getElementById('btnQualityDropdown');
    const menuQuality = document.getElementById('menuQuality');
    const lblQuality = document.getElementById('lblQuality');
    const btnSpecialKeys = document.getElementById('btnSpecialKeys');
    const menuSpecialKeys = document.getElementById('menuSpecialKeys');
    const btnFullscreen = document.getElementById('btnFullscreen');
    const btnScaleMode = document.getElementById('btnScaleMode');
    const lblScaleMode = document.getElementById('lblScaleMode');

    // Clipboard Modal
    const btnOpenClipboard = document.getElementById('btnOpenClipboard');
    const clipboardModal = document.getElementById('clipboardModal');
    const btnCloseClipboard = document.getElementById('btnCloseClipboard');
    const txtClipboard = document.getElementById('txtClipboard');
    const btnGetRemoteClip = document.getElementById('btnGetRemoteClip');
    const btnSendLocalClip = document.getElementById('btnSendLocalClip');
    const toastContainer = document.getElementById('toastContainer');

    // ================= STATE VARIABLES =================
    let ws = null;
    let isConnected = false;
    let isAuthenticated = false;
    let isControlEnabled = true;
    let isFitMode = true;
    let serverWidth = 1920;
    let serverHeight = 1080;
    let pingInterval = null;
    let logPollInterval = null;

    let frameCount = 0;
    let lastFpsTime = performance.now();
    let currentFps = 0;
    let lastMouseMoveTime = 0;

    let cachedServerInfo = null;

    // ================= VIEW SWITCHER =================
    function switchView(viewId) {
        viewPanels.forEach(panel => {
            panel.classList.toggle('active', panel.id === viewId);
        });

        navItems.forEach(item => {
            item.classList.toggle('active', item.getAttribute('data-view') === viewId);
        });

        // Nếu chuyển sang tab Server thì cập nhật logs và info ngay
        if (viewId === 'viewServer') {
            fetchServerInfo();
            fetchServerLogs();
        }
    }

    navItems.forEach(item => {
        item.addEventListener('click', () => {
            switchView(item.getAttribute('data-view'));
        });
    });

    // Card Actions
    btnGoServer.addEventListener('click', () => switchView('viewServer'));
    btnGoClient.addEventListener('click', () => switchView('viewClient'));
    btnGoTest.addEventListener('click', () => {
        // Chạy Test Localhost tự động
        startLocalhostTest();
    });

    // ================= SERVER DASHBOARD LOGIC =================
    async function fetchServerInfo() {
        try {
            const res = await fetch('/api/server-info');
            if (res.ok) {
                cachedServerInfo = await res.json();
                updateServerInfoUI(cachedServerInfo);
            }
        } catch (e) {
            console.warn('Không thể lấy server-info:', e);
        }
    }

    function updateServerInfoUI(info) {
        dispServerIp.textContent = info.ip;
        dispServerPort.textContent = info.port;
        dispServerPass.textContent = info.password;
        dispActiveClients.textContent = `${info.activeClients || 0} máy khách`;

        const fullWebUrl = `http://${info.ip}:${info.port}`;
        dispWebDirectUrl.textContent = fullWebUrl;

        // Trạng thái chia sẻ
        if (info.isSharingEnabled) {
            btnToggleServerShare.className = 'btn-power active';
            lblServerPower.textContent = 'ĐANG BẬT CHIA SẺ';
            serverStateDot.className = 'status-dot dot-green';
            navServerStatusText.textContent = `Server: Sẵn Sàng (${info.ip}:${info.port})`;
        } else {
            btnToggleServerShare.className = 'btn-power off';
            lblServerPower.textContent = 'ĐÃ TẮT CHIA SẺ';
            serverStateDot.className = 'status-dot dot-red';
            navServerStatusText.textContent = 'Server: Tắt Chia Sẻ';
        }

        // Tự động điền trước cho Client Form
        if (!clientTargetIp.value) {
            clientTargetIp.value = info.ip !== '127.0.0.1' ? info.ip : '127.0.0.1';
        }
        if (!clientTargetPort.value) {
            clientTargetPort.value = info.port;
        }
    }

    // Đổi mật khẩu
    btnGenNewPass.addEventListener('click', async () => {
        try {
            const res = await fetch('/api/server-genpass', { method: 'POST' });
            if (res.ok) {
                const data = await res.json();
                dispServerPass.textContent = data.password;
                showToast(`Đã tạo mật khẩu bảo mật mới: ${data.password}`, 'success');
                fetchServerLogs();
            }
        } catch (e) {
            showToast('Lỗi khi đổi mật khẩu', 'error');
        }
    });

    // Bật / Tắt chia sẻ
    btnToggleServerShare.addEventListener('click', async () => {
        try {
            const res = await fetch('/api/server-toggle', { method: 'POST' });
            if (res.ok) {
                const data = await res.json();
                if (cachedServerInfo) {
                    cachedServerInfo.isSharingEnabled = data.isSharingEnabled;
                    updateServerInfoUI(cachedServerInfo);
                }
                showToast(`Chế độ chia sẻ màn hình đã ${data.isSharingEnabled ? 'BẬT' : 'TẮT'}`, 'info');
                fetchServerLogs();
            }
        } catch (e) {
            showToast('Lỗi thay đổi trạng thái', 'error');
        }
    });

    // Hàm sao chép clipboard có fallback cho môi trường HTTP/LAN
    function copyTextToClipboard(text, successMsg) {
        if (!text || text === '------') return;
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).then(() => {
                showToast(successMsg, 'success');
            }).catch(() => {
                fallbackCopy(text, successMsg);
            });
        } else {
            fallbackCopy(text, successMsg);
        }
    }

    function fallbackCopy(text, successMsg) {
        const temp = document.createElement('textarea');
        temp.value = text;
        temp.style.position = 'fixed';
        temp.style.opacity = '0';
        document.body.appendChild(temp);
        temp.select();
        try {
            document.execCommand('copy');
            showToast(successMsg, 'success');
        } catch (e) {
            showToast('Không thể sao chép!', 'error');
        }
        document.body.removeChild(temp);
    }

    // Sao chép IP, Mật khẩu & Web Link
    btnCopyIp.addEventListener('click', () => {
        copyTextToClipboard(dispServerIp.textContent, 'Đã sao chép IP máy chủ vào bộ nhớ tạm');
    });

    if (btnCopyPass) {
        btnCopyPass.addEventListener('click', () => {
            copyTextToClipboard(dispServerPass.textContent, 'Đã sao chép mật khẩu vào bộ nhớ tạm');
        });
    }

    btnCopyWebUrl.addEventListener('click', () => {
        copyTextToClipboard(dispWebDirectUrl.textContent, 'Đã sao chép đường dẫn Web vào bộ nhớ tạm');
    });

    // Logs Polling
    async function fetchServerLogs() {
        try {
            const res = await fetch('/api/server-logs');
            if (res.ok) {
                const logs = await res.json();
                if (Array.isArray(logs) && logs.length > 0) {
                    serverLogsConsole.innerHTML = logs.map(l => `<div class="log-line">${escapeHtml(l)}</div>`).join('');
                    serverLogsConsole.scrollTop = serverLogsConsole.scrollHeight;
                } else if (Array.isArray(logs) && logs.length === 0) {
                    // Server đã xóa logs → đồng bộ UI về trống
                    serverLogsConsole.innerHTML = '<div class="log-line text-muted">[Chưa có nhật ký]</div>';
                }
            }
        } catch (e) {}
    }

    btnClearLogs.addEventListener('click', async () => {
        try {
            const res = await fetch('/api/server-clearlogs', { method: 'POST' });
            if (res.ok) {
                // Chờ server confirm rồi fetch lại để UI đồng bộ thật sự
                serverLogsConsole.innerHTML = '<div class="log-line text-muted">[Đã xóa nhật ký]</div>';
                setTimeout(fetchServerLogs, 300);
            }
        } catch (e) {
            serverLogsConsole.innerHTML = '<div class="log-line text-muted">[Đã xóa nhật ký]</div>';
        }
    });

    // Định kỳ poll log mỗi 2 giây
    logPollInterval = setInterval(fetchServerLogs, 2500);

    // ================= CLIENT DASHBOARD LOGIC =================
    btnToggleClientEye.addEventListener('click', () => {
        const type = clientTargetPass.getAttribute('type') === 'password' ? 'text' : 'password';
        clientTargetPass.setAttribute('type', type);
    });

    clientTargetPass.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') btnClientStartConnect.click();
    });

    // Quét tìm máy chủ trong mạng LAN
    btnScanLan.addEventListener('click', async () => {
        showToast('Đang quét các máy chủ trong mạng Wi-Fi/LAN...', 'info');
        discoveredContainer.style.display = 'block';
        discoveredList.innerHTML = '<span class="text-muted" style="font-size:12px;">Đang dò tìm...</span>';

        try {
            const res = await fetch('/api/scan-lan');
            if (res.ok) {
                const servers = await res.json();
                if (Array.isArray(servers) && servers.length > 0) {
                    discoveredList.innerHTML = '';
                    servers.forEach(srv => {
                        const chip = document.createElement('div');
                        chip.className = 'discovered-chip';
                        chip.textContent = `${srv.name} (${srv.ip})`;
                        chip.title = 'Bấm để tự điền thông tin';
                        chip.addEventListener('click', () => {
                            clientTargetIp.value = srv.ip;
                            clientTargetPort.value = srv.port || 8080;
                            clientTargetPass.focus();
                            showToast(`Đã chọn máy chủ: ${srv.ip}`, 'info');
                        });
                        discoveredList.appendChild(chip);
                    });
                    showToast(`Tìm thấy ${servers.length} máy chủ sẵn sàng!`, 'success');
                } else {
                    discoveredList.innerHTML = '<span class="text-muted" style="font-size:12px;">Không tìm thấy máy chủ nào khác.</span>';
                }
            }
        } catch (e) {
            discoveredList.innerHTML = '<span class="text-muted" style="font-size:12px;">Lỗi khi quét mạng LAN.</span>';
        }
    });

    // Chạy Test Localhost tức thì
    async function startLocalhostTest() {
        // Nếu đang kết nối → không kết nối trùng lặp
        if (isConnected) {
            showToast('Đang có phiên kết nối. Hãy ngắt kết nối trước.', 'error');
            return;
        }
        showToast('Đang khởi tạo phiên thử nghiệm Localhost...', 'info');
        if (!cachedServerInfo) {
            await fetchServerInfo();
        }

        const host = '127.0.0.1';
        const port = (cachedServerInfo && cachedServerInfo.port) || 8080;
        const pass = (cachedServerInfo && cachedServerInfo.password) || '';

        if (!pass) {
            showToast('Không lấy được thông tin server. Server có đang chạy không?', 'error');
            return;
        }

        // Chuyển sang tab Client để người dùng thấy quá trình kết nối
        switchView('viewClient');
        clientTargetIp.value = host;
        clientTargetPort.value = port;
        clientTargetPass.value = pass;

        connectToRemoteServer(host, port, pass);
    }

    // Bắt đầu kết nối Client
    btnClientStartConnect.addEventListener('click', () => {
        const host = clientTargetIp.value.trim();
        const port = clientTargetPort.value.trim() || '8080';
        const password = clientTargetPass.value.trim();

        if (!host) {
            showClientError('Vui lòng nhập địa chỉ IP của Máy Chủ!');
            return;
        }

        connectToRemoteServer(host, port, password);
    });

    function connectToRemoteServer(host, port, password) {
        clearClientError();
        setConnectingButton(true);

        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${host}:${port}/ws`;

        try {
            // Ngắt kết nối cũ mà KHÔNG kích hoạt handleDisconnect()
            // (tránh đóng modal viewer nếu đang reconnect)
            if (ws) {
                ws.onclose = null;
                ws.onerror = null;
                ws.onmessage = null;
                ws.close();
                ws = null;
            }

            ws = new WebSocket(wsUrl);
            ws.binaryType = 'arraybuffer';

            ws.onopen = () => {
                showToast('Kết nối WebSocket thành công. Đang gửi xác thực...', 'info');
                sendJson({
                    type: 'auth',
                    password: password
                });
            };

            ws.onmessage = (event) => {
                if (typeof event.data === 'string') {
                    handleTextMessage(event.data);
                } else if (event.data instanceof ArrayBuffer) {
                    handleBinaryFrame(event.data);
                }
            };

            ws.onerror = () => {
                // Chỉ hiện lỗi nếu chưa xác thực thành công
                if (!isAuthenticated) {
                    showClientError('Không thể kết nối tới máy chủ! Kiểm tra IP, cổng và Server đã bật chưa.');
                    setConnectingButton(false);
                }
            };

            ws.onclose = (event) => {
                // Nếu đã auth thành công mà mất kết nối → báo lỗi rồi disconnect
                if (isAuthenticated) {
                    showToast('Mất kết nối với máy chủ từ xa!', 'error');
                }
                handleDisconnect();
            };
        } catch (e) {
            showClientError('Lỗi khởi tạo WebSocket: ' + e.message);
            setConnectingButton(false);
        }
    }

    function setConnectingButton(isConnecting) {
        if (isConnecting) {
            btnConnectSpinner.style.display = 'inline-block';
            btnConnectLabel.textContent = 'ĐANG KẾT NỐI...';
            btnClientStartConnect.disabled = true;
        } else {
            btnConnectSpinner.style.display = 'none';
            btnConnectLabel.textContent = 'KẾT NỐI NGAY (CONNECT)';
            btnClientStartConnect.disabled = false;
        }
    }

    function showClientError(msg) {
        clientErrorMsg.textContent = msg;
        clientErrorMsg.style.display = 'block';
    }

    function clearClientError() {
        clientErrorMsg.style.display = 'none';
    }

    // ================= REMOTE VIEWER LOGIC =================
    function handleTextMessage(text) {
        try {
            const msg = JSON.parse(text);
            if (msg.type === 'auth_result') {
                if (msg.success) {
                    isAuthenticated = true;
                    isConnected = true;
                    setConnectingButton(false);

                    // Mở cửa sổ Remote Viewer toàn màn hình
                    remoteViewerModal.classList.add('active');
                    showToast('Xác thực THÀNH CÔNG! Bắt đầu điều khiển máy tính.', 'success');

                    if (msg.width && msg.height) {
                        serverWidth = msg.width;
                        serverHeight = msg.height;
                        remoteCanvas.width = serverWidth;
                        remoteCanvas.height = serverHeight;
                        valResolution.textContent = `${serverWidth}x${serverHeight}`;
                    }

                    startPingHeartbeat();
                    remoteCanvas.focus();
                } else {
                    showClientError(msg.message || 'Mật khẩu xác thực không đúng!');
                    setConnectingButton(false);
                    if (ws) ws.close();
                }
            } else if (msg.type === 'pong') {
                const rtt = Date.now() - msg.t;
                valLatency.textContent = `${rtt} ms`;
                if (rtt < 60) latencyDot.className = 'stat-dot dot-green';
                else if (rtt < 160) latencyDot.className = 'stat-dot dot-yellow';
                else latencyDot.className = 'stat-dot dot-red';

                // Tự động thích ứng chất lượng theo độ trễ RTT nếu người dùng chưa chọn thủ công
                if (!window.isManualQualitySelected) {
                    if (rtt > 180 && window.currentAdaptiveQuality !== 0.40) {
                        window.currentAdaptiveQuality = 0.40;
                        sendJson({ type: 'quality', value: '0.40' });
                        sendJson({ type: 'target_fps', value: '20' });
                    } else if (rtt < 60 && window.currentAdaptiveQuality !== 0.75) {
                        window.currentAdaptiveQuality = 0.75;
                        sendJson({ type: 'quality', value: '0.75' });
                        sendJson({ type: 'target_fps', value: '35' });
                    } else if (rtt >= 60 && rtt <= 180 && window.currentAdaptiveQuality !== 0.60) {
                        window.currentAdaptiveQuality = 0.60;
                        sendJson({ type: 'quality', value: '0.60' });
                        sendJson({ type: 'target_fps', value: '28' });
                    }
                }
            } else if (msg.type === 'clipboard_data') {
                txtClipboard.value = msg.text || '';
                showToast('Đã nhận nội dung Clipboard từ máy từ xa', 'info');
            }
        } catch (e) {
            console.error('Error parsing text frame:', e);
        }
    }

    function handleBinaryFrame(arrayBuffer) {
        if (!isAuthenticated) return;

        const dataView = new DataView(arrayBuffer);
        let frameType = 0;
        let w, h, targetX = 0, targetY = 0, targetW, targetH;
        let jpegBytes;

        // Nhận diện Header 33-byte (hỗ trợ Delta Frame vi sai)
        if (arrayBuffer.byteLength >= 33 && (dataView.getUint8(0) === 0 || dataView.getUint8(0) === 1)) {
            frameType = dataView.getUint8(0);
            w = dataView.getInt32(1);
            h = dataView.getInt32(5);
            targetX = dataView.getInt32(9);
            targetY = dataView.getInt32(13);
            targetW = dataView.getInt32(17);
            targetH = dataView.getInt32(21);
            jpegBytes = arrayBuffer.slice(33);
        } else {
            // Tương thích ngược với Header 16-byte cũ
            w = dataView.getInt32(0);
            h = dataView.getInt32(4);
            targetX = 0;
            targetY = 0;
            targetW = w;
            targetH = h;
            jpegBytes = arrayBuffer.slice(16);
        }

        if (w !== serverWidth || h !== serverHeight) {
            serverWidth = w;
            serverHeight = h;
            remoteCanvas.width = w;
            remoteCanvas.height = h;
            valResolution.textContent = `${w}x${h}`;
        }

        const blob = new Blob([jpegBytes], { type: 'image/jpeg' });

        createImageBitmap(blob)
            .then(bitmap => {
                // Vẽ đúng vùng thay đổi (Delta Frame) hoặc vẽ toàn bộ khung nhìn (Full Frame)
                ctx.drawImage(bitmap, targetX, targetY, targetW, targetH);
                bitmap.close();

                if (canvasLoading.style.display !== 'none') {
                    canvasLoading.style.display = 'none';
                }

                // Đo FPS thời gian thực
                frameCount++;
                const now = performance.now();
                if (now - lastFpsTime >= 1000) {
                    currentFps = Math.round((frameCount * 1000) / (now - lastFpsTime));
                    valFps.textContent = currentFps;
                    frameCount = 0;
                    lastFpsTime = now;
                }
            })
            .catch(() => {});
    }

    // Đóng Viewer / Ngắt kết nối
    btnCloseViewer.addEventListener('click', () => {
        if (ws) ws.close();
        handleDisconnect();
        showToast('Đã ngắt kết nối và quay lại Dashboard.', 'info');
    });

    function handleDisconnect() {
        isConnected = false;
        isAuthenticated = false;
        if (pingInterval) {
            clearInterval(pingInterval);
            pingInterval = null;
        }
        setConnectingButton(false);
        remoteViewerModal.classList.remove('active');
        canvasLoading.style.display = 'flex';
        valLatency.textContent = '-- ms';
        valFps.textContent = '--';
    }

    function startPingHeartbeat() {
        if (pingInterval) clearInterval(pingInterval);
        pingInterval = setInterval(() => {
            if (isConnected && ws && ws.readyState === WebSocket.OPEN) {
                sendJson({ type: 'ping', t: Date.now() });
            }
        }, 1800);
    }

    function sendJson(obj) {
        if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify(obj));
        }
    }

    // ================= TOOLBAR CONTROLS =================
    // Dropdowns
    function setupDropdown(btn, menu) {
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            const wrapper = btn.closest('.dropdown-wrapper');
            const isOpen = wrapper.classList.contains('open');
            document.querySelectorAll('.dropdown-wrapper').forEach(w => w.classList.remove('open'));
            if (!isOpen) wrapper.classList.add('open');
        });
    }
    setupDropdown(btnQualityDropdown, menuQuality);
    setupDropdown(btnSpecialKeys, menuSpecialKeys);

    document.addEventListener('click', () => {
        document.querySelectorAll('.dropdown-wrapper').forEach(w => w.classList.remove('open'));
    });

    menuQuality.querySelectorAll('.menu-item').forEach(item => {
        item.addEventListener('click', () => {
            window.isManualQualitySelected = true;
            const qualityVal = parseFloat(item.getAttribute('data-quality'));
            menuQuality.querySelectorAll('.menu-item').forEach(i => i.classList.remove('active'));
            item.classList.add('active');
            lblQuality.textContent = item.textContent.split(' ')[0] + ` (${Math.round(qualityVal * 100)}%)`;
            sendJson({ type: 'quality', value: qualityVal });
            showToast(`Đã đổi chất lượng hình ảnh: ${Math.round(qualityVal * 100)}% (Thủ công)`, 'info');
        });
    });

    menuSpecialKeys.querySelectorAll('.menu-item').forEach(item => {
        item.addEventListener('click', () => {
            const cmd = item.getAttribute('data-cmd');
            sendJson({ type: 'special', command: cmd });
            showToast(`Đã gửi lệnh hệ thống: ${item.textContent}`, 'info');
        });
    });

    // Quyền điều khiển (Full Control vs View Only)
    btnToggleControl.addEventListener('click', () => {
        isControlEnabled = !isControlEnabled;
        if (isControlEnabled) {
            btnToggleControl.classList.add('active');
            lblControlMode.textContent = 'Đang Điều Khiển';
            showToast('Đã BẬT tương tác chuột & phím', 'success');
        } else {
            btnToggleControl.classList.remove('active');
            lblControlMode.textContent = 'Chỉ Xem';
            showToast('Đã TẮT tương tác - Chế độ Chỉ Xem', 'info');
        }
    });

    // Co Dãn / Tỉ Lệ Gốc
    remoteCanvas.classList.add('fit-mode');
    btnScaleMode.addEventListener('click', () => {
        isFitMode = !isFitMode;
        if (isFitMode) {
            remoteCanvas.classList.add('fit-mode');
            lblScaleMode.textContent = 'Co Dãn';
        } else {
            remoteCanvas.classList.remove('fit-mode');
            lblScaleMode.textContent = 'Tỉ Lệ 1:1';
        }
    });

    btnFullscreen.addEventListener('click', () => {
        if (!document.fullscreenElement) {
            document.documentElement.requestFullscreen().catch(() => {});
        } else {
            document.exitFullscreen().catch(() => {});
        }
    });

    // Clipboard Modal
    btnOpenClipboard.addEventListener('click', () => {
        clipboardModal.classList.add('active');
        txtClipboard.focus();
    });

    btnCloseClipboard.addEventListener('click', () => {
        clipboardModal.classList.remove('active');
    });

    clipboardModal.addEventListener('click', (e) => {
        if (e.target === clipboardModal) clipboardModal.classList.remove('active');
    });

    btnSendLocalClip.addEventListener('click', () => {
        const text = txtClipboard.value;
        if (text) {
            sendJson({ type: 'clipboard_set', text: text });
            showToast('Đã gửi nội dung tới máy từ xa', 'success');
            clipboardModal.classList.remove('active');
        }
    });

    btnGetRemoteClip.addEventListener('click', () => {
        sendJson({ type: 'clipboard_get' });
        showToast('Đang yêu cầu lấy nội dung Clipboard...', 'info');
    });

    // ================= MOUSE & KEYBOARD CAPTURE =================
    function getCanvasCoordinates(e) {
        const rect = remoteCanvas.getBoundingClientRect();
        const scaleX = serverWidth / rect.width;
        const scaleY = serverHeight / rect.height;

        const clientX = e.clientX - rect.left;
        const clientY = e.clientY - rect.top;

        const x = Math.max(0, Math.min(serverWidth, Math.round(clientX * scaleX)));
        const y = Math.max(0, Math.min(serverHeight, Math.round(clientY * scaleY)));

        return { x, y };
    }

    remoteCanvas.addEventListener('mousemove', (e) => {
        if (!isControlEnabled || !isAuthenticated) return;
        const now = performance.now();
        if (now - lastMouseMoveTime < 18) return;
        lastMouseMoveTime = now;

        const coords = getCanvasCoordinates(e);
        sendJson({ type: 'mouse', action: 'move', x: coords.x, y: coords.y });
    });

    remoteCanvas.addEventListener('mousedown', (e) => {
        if (!isControlEnabled || !isAuthenticated) return;
        e.preventDefault();
        remoteCanvas.focus();

        const coords = getCanvasCoordinates(e);
        sendJson({ type: 'mouse', action: 'move', x: coords.x, y: coords.y });
        sendJson({ type: 'mouse', action: 'down', button: e.button });
    });

    remoteCanvas.addEventListener('mouseup', (e) => {
        if (!isControlEnabled || !isAuthenticated) return;
        e.preventDefault();
        sendJson({ type: 'mouse', action: 'up', button: e.button });
    });

    remoteCanvas.addEventListener('contextmenu', (e) => e.preventDefault());

    remoteCanvas.addEventListener('wheel', (e) => {
        if (!isControlEnabled || !isAuthenticated) return;
        e.preventDefault();
        sendJson({ type: 'mouse', action: 'wheel', deltaY: e.deltaY });
    }, { passive: false });

    window.addEventListener('keydown', (e) => {
        if (!isControlEnabled || !isAuthenticated || !remoteViewerModal.classList.contains('active')) return;
        if (document.activeElement === txtClipboard || document.activeElement.tagName === 'INPUT') return;

        const blockedKeys = ['Tab', 'Alt', 'F5', 'Backspace'];
        if (blockedKeys.includes(e.key) || (e.ctrlKey && !['c', 'v'].includes(e.key.toLowerCase()))) {
            e.preventDefault();
        }

        sendJson({
            type: 'key',
            action: 'down',
            code: e.code,
            keyCode: e.keyCode
        });
    });

    window.addEventListener('keyup', (e) => {
        if (!isControlEnabled || !isAuthenticated || !remoteViewerModal.classList.contains('active')) return;
        if (document.activeElement === txtClipboard || document.activeElement.tagName === 'INPUT') return;

        sendJson({
            type: 'key',
            action: 'up',
            code: e.code,
            keyCode: e.keyCode
        });
    });

    // ================= TOAST NOTIFICATION UTILITY =================
    function showToast(message, type = 'info') {
        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        toast.textContent = message;
        toastContainer.appendChild(toast);
        setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transform = 'translateY(10px)';
            toast.style.transition = 'all 0.3s ease';
            setTimeout(() => toast.remove(), 300);
        }, 3200);
    }

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    // Khởi chạy ban đầu: Lấy server-info
    fetchServerInfo();
})();
