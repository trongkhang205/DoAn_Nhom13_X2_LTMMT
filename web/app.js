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
    const dispServerDeviceId = document.getElementById('dispServerDeviceId');
    const dispServerPort = document.getElementById('dispServerPort');
    const dispServerPass = document.getElementById('dispServerPass');
    const dispActiveClients = document.getElementById('dispActiveClients');
    const dispWebDirectUrl = document.getElementById('dispWebDirectUrl');
    const btnCopyIp = document.getElementById('btnCopyIp');
    const btnCopyDeviceId = document.getElementById('btnCopyDeviceId');
    const btnCopyPass = document.getElementById('btnCopyPass');
    const btnCopyWebUrl = document.getElementById('btnCopyWebUrl');
    const btnGenNewPass = document.getElementById('btnGenNewPass');
    const btnClearLogs = document.getElementById('btnClearLogs');
    const serverLogsConsole = document.getElementById('serverLogsConsole');

    // Tunnel 4G Controls
    const btnToggleTunnel = document.getElementById('btnToggleTunnel');
    const lblTunnelPower = document.getElementById('lblTunnelPower');
    const badgeTunnelStatus = document.getElementById('badgeTunnelStatus');
    const boxTunnelLink = document.getElementById('boxTunnelLink');
    const dispTunnelUrl = document.getElementById('dispTunnelUrl');
    const btnCopyTunnelUrl = document.getElementById('btnCopyTunnelUrl');
    const btnOpenTunnelUrl = document.getElementById('btnOpenTunnelUrl');

    // View 3: Client Connect
    const tabModeLan = document.getElementById('tabModeLan');
    const tabModeInternet = document.getElementById('tabModeInternet');
    const groupDeviceId = document.getElementById('groupDeviceId');
    const groupIpPort = document.getElementById('groupIpPort');
    const clientTargetDeviceId = document.getElementById('clientTargetDeviceId');
    const clientGatewayUrl = document.getElementById('clientGatewayUrl');
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
    let clientConnectionMode = 'lan'; // 'lan' | 'internet'

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
        if (dispServerDeviceId) {
            dispServerDeviceId.textContent = info.formattedDeviceId || info.deviceId || '--- --- ---';
        }

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

        // Cập nhật trạng thái Cầu Nối Tunnel 4G
        if (btnToggleTunnel) {
            if (info.isTunnelActive) {
                btnToggleTunnel.className = 'btn-power tunnel-power-btn active';
                if (lblTunnelPower) lblTunnelPower.textContent = 'TẮT CẦU NỐI 4G';
                if (boxTunnelLink) boxTunnelLink.style.display = 'block';

                if (info.tunnelUrl && info.tunnelUrl.trim() !== '') {
                    if (badgeTunnelStatus) {
                        badgeTunnelStatus.className = 'tunnel-status-badge tunnel-badge-on';
                        badgeTunnelStatus.textContent = 'Đang Hoạt Động';
                    }
                    const quickUrl = `${info.tunnelUrl}/?id=${info.deviceId || ''}`;
                    if (dispTunnelUrl) {
                        dispTunnelUrl.textContent = quickUrl;
                        dispTunnelUrl.setAttribute('data-quick-url', quickUrl);
                    }
                    if (btnOpenTunnelUrl) {
                        btnOpenTunnelUrl.href = quickUrl;
                        btnOpenTunnelUrl.style.display = 'inline-flex';
                    }
                } else {
                    if (badgeTunnelStatus) {
                        badgeTunnelStatus.className = 'tunnel-status-badge tunnel-badge-pending';
                        badgeTunnelStatus.textContent = 'Đang Lấy URL...';
                    }
                    if (dispTunnelUrl) dispTunnelUrl.textContent = 'Đang khởi tạo đường truyền Cloudflare... Vui lòng chờ 3s';
                    if (btnOpenTunnelUrl) btnOpenTunnelUrl.style.display = 'none';
                }
            } else {
                btnToggleTunnel.className = 'btn-power tunnel-power-btn off';
                if (lblTunnelPower) lblTunnelPower.textContent = 'BẬT CẦU NỐI 4G';
                if (badgeTunnelStatus) {
                    badgeTunnelStatus.className = 'tunnel-status-badge tunnel-badge-off';
                    badgeTunnelStatus.textContent = 'Chưa Kích Hoạt';
                }
                if (boxTunnelLink) boxTunnelLink.style.display = 'none';
            }
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

    if (btnCopyDeviceId) {
        btnCopyDeviceId.addEventListener('click', () => {
            const raw = dispServerDeviceId ? dispServerDeviceId.textContent.replace(/\s+/g, '') : '';
            copyTextToClipboard(raw, 'Đã sao chép Mã Thiết Bị Internet (Device ID) vào bộ nhớ tạm');
        });
    }

    if (btnCopyPass) {
        btnCopyPass.addEventListener('click', () => {
            copyTextToClipboard(dispServerPass.textContent, 'Đã sao chép mật khẩu vào bộ nhớ tạm');
        });
    }

    btnCopyWebUrl.addEventListener('click', () => {
        copyTextToClipboard(dispWebDirectUrl.textContent, 'Đã sao chép đường dẫn Web vào bộ nhớ tạm');
    });

    // Bật / Tắt Cầu Nối Internet 4G (Cloudflare Tunnel)
    let tunnelPollTimer = null;
    if (btnToggleTunnel) {
        btnToggleTunnel.addEventListener('click', async () => {
            btnToggleTunnel.disabled = true;
            const isCurrentlyActive = btnToggleTunnel.classList.contains('active');
            if (lblTunnelPower) {
                lblTunnelPower.textContent = isCurrentlyActive ? 'Đang tắt cầu nối...' : 'Đang khởi chạy...';
            }

            try {
                const res = await fetch('/api/tunnel-toggle', { method: 'POST' });
                if (res.ok) {
                    const data = await res.json();
                    if (cachedServerInfo) {
                        cachedServerInfo.isTunnelActive = data.isTunnelActive;
                        cachedServerInfo.tunnelUrl = data.tunnelUrl;
                        updateServerInfoUI(cachedServerInfo);
                    } else {
                        fetchServerInfo();
                    }

                    showToast(data.message || (data.isTunnelActive ? 'Cầu nối Internet 4G đã bật!' : 'Đã tắt cầu nối Internet 4G'), 'info');
                    fetchServerLogs();

                    // Nếu vừa bật và chưa có URL, hẹn giờ poll liên tục vài lần để lấy URL nhanh nhất
                    if (data.isTunnelActive && !data.tunnelUrl) {
                        if (tunnelPollTimer) clearInterval(tunnelPollTimer);
                        let pollAttempts = 0;
                        tunnelPollTimer = setInterval(async () => {
                            pollAttempts++;
                            await fetchServerInfo();
                            if ((cachedServerInfo && cachedServerInfo.tunnelUrl) || pollAttempts > 8) {
                                clearInterval(tunnelPollTimer);
                                tunnelPollTimer = null;
                            }
                        }, 1800);
                    }
                } else {
                    showToast('Lỗi khi chuyển trạng thái Cầu Nối', 'error');
                }
            } catch (e) {
                console.error(e);
                showToast('Không thể kết nối API máy chủ', 'error');
            } finally {
                btnToggleTunnel.disabled = false;
            }
        });
    }

    if (btnCopyTunnelUrl) {
        btnCopyTunnelUrl.addEventListener('click', () => {
            if (dispTunnelUrl) {
                const urlToCopy = dispTunnelUrl.getAttribute('data-quick-url') || dispTunnelUrl.textContent;
                copyTextToClipboard(urlToCopy, 'Đã sao chép liên kết Internet 4G (Kèm Mã Thiết Bị) vào bộ nhớ tạm');
            }
        });
    }

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

            // Nếu Tunnel đang bật mà chưa có URL, tự động cập nhật ServerInfo
            if (cachedServerInfo && cachedServerInfo.isTunnelActive && !cachedServerInfo.tunnelUrl) {
                fetchServerInfo();
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

    // Chuyển đổi tab Mạng LAN vs Xuyên Internet
    if (tabModeLan && tabModeInternet) {
        tabModeLan.addEventListener('click', () => {
            clientConnectionMode = 'lan';
            tabModeLan.classList.add('active');
            tabModeInternet.classList.remove('active');
            if (groupIpPort) groupIpPort.style.display = 'block';
            if (groupDeviceId) groupDeviceId.style.display = 'none';
        });

        tabModeInternet.addEventListener('click', () => {
            clientConnectionMode = 'internet';
            tabModeInternet.classList.add('active');
            tabModeLan.classList.remove('active');
            if (groupIpPort) groupIpPort.style.display = 'none';
            if (groupDeviceId) groupDeviceId.style.display = 'block';
            if (clientTargetDeviceId) clientTargetDeviceId.focus();
        });
    }

    if (clientTargetDeviceId) {
        // Tự động định dạng khoảng trắng 3-3-3 khi gõ số (Ví dụ: 123 456 789)
        clientTargetDeviceId.addEventListener('input', (e) => {
            let digits = e.target.value.replace(/\D/g, '').slice(0, 9);
            let formatted = '';
            for (let i = 0; i < digits.length; i++) {
                if (i > 0 && i % 3 === 0) formatted += ' ';
                formatted += digits[i];
            }
            e.target.value = formatted;
        });

        clientTargetDeviceId.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') clientTargetPass.focus();
        });
    }

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

    // Bắt đầu kết nối Client (Mạng LAN hoặc Xuyên Internet)
    btnClientStartConnect.addEventListener('click', () => {
        const password = clientTargetPass.value.trim();

        if (clientConnectionMode === 'internet') {
            const devIdRaw = clientTargetDeviceId ? clientTargetDeviceId.value.trim().replace(/\s+/g, '') : '';
            if (!devIdRaw || !/^\d{9}$/.test(devIdRaw)) {
                showClientError('Vui lòng nhập đúng 9 chữ số Mã Thiết Bị (Device ID) để kết nối qua Internet!');
                return;
            }
            connectViaInternetGateway(devIdRaw, password);
        } else {
            const host = clientTargetIp.value.trim();
            const port = clientTargetPort.value.trim() || '8080';

            if (!host) {
                showClientError('Vui lòng nhập địa chỉ IP của Máy Chủ!');
                return;
            }

            connectToRemoteServer(host, port, password);
        }
    });

    function connectViaInternetGateway(deviceId, password) {
        clearClientError();
        setConnectingButton(true);

        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        let gatewayWsUrl;

        const customGw = clientGatewayUrl ? clientGatewayUrl.value.trim() : '';
        if (customGw) {
            if (customGw.startsWith('ws://') || customGw.startsWith('wss://')) {
                gatewayWsUrl = customGw;
            } else {
                gatewayWsUrl = `${protocol}//${customGw.replace(/^https?:\/\//, '')}`;
            }
        } else if (window.location.port === '3000' || window.location.port === '8080') {
            gatewayWsUrl = `${protocol}//${window.location.hostname}:3000`;
        } else {
            // Trường hợp chạy qua Cloud Public Domain (Render, Railway, tunnel)
            gatewayWsUrl = `${protocol}//${window.location.host}`;
        }

        try {
            if (ws) {
                ws.onclose = null;
                ws.onerror = null;
                ws.onmessage = null;
                ws.close();
                ws = null;
            }

            showToast(`Đang kết nối tới Cloud Relay Gateway (${gatewayWsUrl})...`, 'info');
            ws = new WebSocket(gatewayWsUrl);
            ws.binaryType = 'arraybuffer';

            ws.onopen = () => {
                showToast(`Đã nối Gateway. Đang ghép cặp với Thiết Bị [${deviceId}]...`, 'info');
                sendJson({
                    type: 'join_host',
                    id: deviceId,
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
                if (!isAuthenticated) {
                    showClientError(`Không thể kết nối Cloud Relay Gateway (${gatewayWsUrl})! Hãy đảm bảo Gateway đang chạy.`);
                    setConnectingButton(false);
                }
            };

            ws.onclose = (event) => {
                if (isAuthenticated) {
                    showToast('Mất kết nối với máy chủ từ xa qua Cloud Relay!', 'error');
                }
                handleDisconnect();
            };
        } catch (e) {
            showClientError('Lỗi khởi tạo kết nối Cloud Relay: ' + e.message);
            setConnectingButton(false);
        }
    }

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
            } else if (msg.type === 'file_upload_success') {
                showToast(`Đã gửi tệp [${msg.name}] lên máy chủ thành công!`, 'success');
                if (uploadProgressBar) uploadProgressBar.style.width = '100%';
                if (lblUploadProgress) lblUploadProgress.textContent = 'Hoàn tất 100%!';
                setTimeout(() => {
                    if (uploadProgressContainer) uploadProgressContainer.style.display = 'none';
                    if (btnStartUpload) btnStartUpload.disabled = false;
                }, 1500);
            } else if (msg.type === 'file_list_resp') {
                renderRemoteFilesList(msg.files || []);
            } else if (msg.type === 'file_download_chunk') {
                handleDownloadChunk(msg);
            } else if (msg.type === 'file_download_complete') {
                finalizeFileDownload(msg.name);
            } else if (msg.type === 'file_download_error') {
                showToast(msg.message || 'Lỗi tải tệp!', 'error');
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

    // ================= FILE TRANSFER 2-WAY (QUẢN LÝ TỆP TIN HAI CHIỀU) =================
    const btnFileTransfer = document.getElementById('btnFileTransfer');
    const fileModal = document.getElementById('fileModal');
    const btnCloseFileModal = document.getElementById('btnCloseFileModal');
    const btnCancelFileModal = document.getElementById('btnCancelFileModal');
    const tabUploadBtn = document.getElementById('tabUploadBtn');
    const tabDownloadBtn = document.getElementById('tabDownloadBtn');
    const paneUpload = document.getElementById('paneUpload');
    const paneDownload = document.getElementById('paneDownload');
    const fileDropZone = document.getElementById('fileDropZone');
    const fileInputUpload = document.getElementById('fileInputUpload');
    const fileInfoBox = document.getElementById('fileInfoBox');
    const lblSelectedFileName = document.getElementById('lblSelectedFileName');
    const lblSelectedFileSize = document.getElementById('lblSelectedFileSize');
    const uploadProgressContainer = document.getElementById('uploadProgressContainer');
    const uploadProgressBar = document.getElementById('uploadProgressBar');
    const lblUploadProgress = document.getElementById('lblUploadProgress');
    const btnStartUpload = document.getElementById('btnStartUpload');
    const btnRefreshFiles = document.getElementById('btnRefreshFiles');
    const remoteFilesList = document.getElementById('remoteFilesList');

    let selectedUploadFile = null;
    let downloadFileChunks = {};

    if (btnFileTransfer && fileModal) {
        btnFileTransfer.addEventListener('click', () => {
            fileModal.classList.add('active');
        });

        const closeFileModal = () => fileModal.classList.remove('active');
        if (btnCloseFileModal) btnCloseFileModal.addEventListener('click', closeFileModal);
        if (btnCancelFileModal) btnCancelFileModal.addEventListener('click', closeFileModal);

        tabUploadBtn.addEventListener('click', () => {
            tabUploadBtn.classList.add('active');
            tabDownloadBtn.classList.remove('active');
            paneUpload.classList.add('active');
            paneDownload.classList.remove('active');
        });

        tabDownloadBtn.addEventListener('click', () => {
            tabDownloadBtn.classList.add('active');
            tabUploadBtn.classList.remove('active');
            paneDownload.classList.add('active');
            paneUpload.classList.remove('active');
            sendJson({ type: 'file_list' });
        });

        if (btnRefreshFiles) {
            btnRefreshFiles.addEventListener('click', () => sendJson({ type: 'file_list' }));
        }

        // Kéo thả tệp tin
        if (fileDropZone) {
            fileDropZone.addEventListener('dragover', (e) => {
                e.preventDefault();
                fileDropZone.classList.add('dragover');
            });
            fileDropZone.addEventListener('dragleave', () => fileDropZone.classList.remove('dragover'));
            fileDropZone.addEventListener('drop', (e) => {
                e.preventDefault();
                fileDropZone.classList.remove('dragover');
                if (e.dataTransfer.files.length > 0) {
                    selectUploadFile(e.dataTransfer.files[0]);
                }
            });
        }

        if (fileInputUpload) {
            fileInputUpload.addEventListener('change', () => {
                if (fileInputUpload.files.length > 0) {
                    selectUploadFile(fileInputUpload.files[0]);
                }
            });
        }

        function selectUploadFile(file) {
            selectedUploadFile = file;
            lblSelectedFileName.textContent = file.name;
            lblSelectedFileSize.textContent = formatBytes(file.size);
            fileInfoBox.style.display = 'flex';
            btnStartUpload.disabled = false;
        }

        // Gửi tệp lên máy chủ
        if (btnStartUpload) {
            btnStartUpload.addEventListener('click', () => {
                if (!selectedUploadFile) return;
                btnStartUpload.disabled = true;
                uploadProgressContainer.style.display = 'block';
                uploadProgressBar.style.width = '0%';
                lblUploadProgress.textContent = '0%';

                const file = selectedUploadFile;
                sendJson({ type: 'file_upload_start', name: file.name, size: file.size });

                const reader = new FileReader();
                reader.onload = () => {
                    const arrayBuffer = reader.result;
                    const chunkSize = 32 * 1024; // 32KB
                    const totalChunks = Math.ceil(arrayBuffer.byteLength / chunkSize) || 1;
                    let currentChunk = 0;

                    function sendNextChunk() {
                        if (currentChunk < totalChunks) {
                            const start = currentChunk * chunkSize;
                            const end = Math.min(start + chunkSize, arrayBuffer.byteLength);
                            const chunkSlice = arrayBuffer.slice(start, end);
                            const b64 = arrayBufferToBase64(chunkSlice);

                            sendJson({ type: 'file_upload_chunk', data: b64 });
                            currentChunk++;
                            const percent = Math.round((currentChunk / totalChunks) * 100);
                            uploadProgressBar.style.width = `${percent}%`;
                            lblUploadProgress.textContent = `Đang gửi... ${percent}%`;
                            setTimeout(sendNextChunk, 15);
                        } else {
                            sendJson({ type: 'file_upload_end' });
                        }
                    }
                    sendNextChunk();
                };
                reader.readAsArrayBuffer(file);
            });
        }
    }

    function renderRemoteFilesList(files) {
        if (!remoteFilesList) return;
        if (!files || files.length === 0) {
            remoteFilesList.innerHTML = '<p class="empty-files-text">Thư mục nhận trên máy chủ hiện đang trống.</p>';
            return;
        }

        remoteFilesList.innerHTML = files.map(f => `
            <div class="remote-file-item">
                <div class="file-meta">
                    <span class="file-name">${escapeHtml(f.name)}</span>
                    <span class="file-sub">${formatBytes(f.size)} • ${escapeHtml(f.date)}</span>
                </div>
                <button class="btn-dl-file" data-name="${escapeHtml(f.name)}">⬇ Tải Về</button>
            </div>
        `).join('');

        remoteFilesList.querySelectorAll('.btn-dl-file').forEach(btn => {
            btn.addEventListener('click', () => {
                const name = btn.getAttribute('data-name');
                downloadFileChunks[name] = [];
                sendJson({ type: 'file_download_req', name: name });
                showToast(`Đang yêu cầu tải tệp [${name}] từ máy chủ...`, 'info');
            });
        });
    }

    function handleDownloadChunk(msg) {
        if (!downloadFileChunks[msg.name]) downloadFileChunks[msg.name] = [];
        downloadFileChunks[msg.name].push(msg.data);
    }

    function finalizeFileDownload(fileName) {
        const chunks = downloadFileChunks[fileName];
        if (!chunks) return;

        const byteArrays = [];
        for (const b64 of chunks) {
            const binaryString = atob(b64);
            const len = binaryString.length;
            const bytes = new Uint8Array(len);
            for (let i = 0; i < len; i++) {
                bytes[i] = binaryString.charCodeAt(i);
            }
            byteArrays.push(bytes);
        }

        const blob = new Blob(byteArrays);
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = fileName;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        delete downloadFileChunks[fileName];
        showToast(`Đã tải xong tệp [${fileName}] về máy của bạn!`, 'success');
    }

    function arrayBufferToBase64(buffer) {
        let binary = '';
        const bytes = new Uint8Array(buffer);
        const len = bytes.byteLength;
        for (let i = 0; i < len; i++) {
            binary += String.fromCharCode(bytes[i]);
        }
        return btoa(binary);
    }

    function formatBytes(bytes) {
        if (!bytes || bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
    }

    // ================= MOBILE TOUCH & VIRTUAL KEYBOARD CONTROLS =================
    const btnMobileKeyboard = document.getElementById('btnMobileKeyboard');
    const mobileKeyInput = document.getElementById('mobileKeyInput');

    if (btnMobileKeyboard && mobileKeyInput) {
        btnMobileKeyboard.addEventListener('click', () => {
            mobileKeyInput.focus();
            showToast('Đã kích hoạt bàn phím ảo điện thoại', 'info');
        });

        mobileKeyInput.addEventListener('input', () => {
            if (!isControlEnabled || !isAuthenticated) return;
            const text = mobileKeyInput.value;
            if (text.length > 0) {
                const char = text.charAt(text.length - 1);
                sendJson({ type: 'clipboard_set', text: char });
                sendJson({ type: 'special', command: 'enter' });
                mobileKeyInput.value = '';
            }
        });
    }

    let touchStartTime = 0;
    let touchStartX = 0;
    let touchStartY = 0;
    let longPressTimer = null;
    let lastTapTime = 0;

    remoteCanvas.addEventListener('touchstart', (e) => {
        if (!isControlEnabled || !isAuthenticated) return;
        if (e.touches.length === 1) {
            const touch = e.touches[0];
            const coords = getCanvasCoordinates({ clientX: touch.clientX, clientY: touch.clientY });
            touchStartTime = Date.now();
            touchStartX = coords.x;
            touchStartY = coords.y;

            // Nhấn giữ 550ms -> Chuột phải
            longPressTimer = setTimeout(() => {
                sendJson({ type: 'mouse', action: 'move', x: coords.x, y: coords.y });
                sendJson({ type: 'mouse', action: 'down', button: 2 });
                setTimeout(() => sendJson({ type: 'mouse', action: 'up', button: 2 }), 40);
                showToast('Chuột phải (Nhấn giữ)', 'info');
                longPressTimer = null;
            }, 550);
        }
    }, { passive: true });

    remoteCanvas.addEventListener('touchmove', (e) => {
        if (!isControlEnabled || !isAuthenticated) return;
        if (longPressTimer) {
            clearTimeout(longPressTimer);
            longPressTimer = null;
        }

        if (e.touches.length === 1) {
            const touch = e.touches[0];
            const coords = getCanvasCoordinates({ clientX: touch.clientX, clientY: touch.clientY });
            sendJson({ type: 'mouse', action: 'move', x: coords.x, y: coords.y });
        } else if (e.touches.length === 2) {
            // Cuộn trang bằng 2 ngón
            e.preventDefault();
            const deltaY = (e.touches[0].clientY - touchStartY) * 0.15;
            sendJson({ type: 'mouse', action: 'wheel', deltaY: -deltaY });
        }
    }, { passive: false });

    remoteCanvas.addEventListener('touchend', (e) => {
        if (!isControlEnabled || !isAuthenticated) return;
        if (longPressTimer) {
            clearTimeout(longPressTimer);
            longPressTimer = null;
        }

        const duration = Date.now() - touchStartTime;
        if (duration < 280) {
            const now = Date.now();
            if (now - lastTapTime < 300) {
                // Double tap -> Double click
                sendJson({ type: 'mouse', action: 'down', button: 0 });
                sendJson({ type: 'mouse', action: 'up', button: 0 });
                setTimeout(() => {
                    sendJson({ type: 'mouse', action: 'down', button: 0 });
                    sendJson({ type: 'mouse', action: 'up', button: 0 });
                }, 50);
                lastTapTime = 0;
            } else {
                // Chạm nhẹ -> Click chuột trái
                lastTapTime = now;
                sendJson({ type: 'mouse', action: 'move', x: touchStartX, y: touchStartY });
                sendJson({ type: 'mouse', action: 'down', button: 0 });
                setTimeout(() => sendJson({ type: 'mouse', action: 'up', button: 0 }), 30);
            }
        }
    }, { passive: true });

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

    // Xử lý tự động cấu hình khi mở qua đường dẫn Internet hoặc Quick Link
    try {
        const urlParams = new URLSearchParams(window.location.search);
        const targetId = urlParams.get('id') || urlParams.get('device');
        const targetPass = urlParams.get('pass') || urlParams.get('password');
        const isPublicDomain = window.location.hostname.includes('trycloudflare.com')
                            || window.location.hostname.includes('loca.lt')
                            || window.location.hostname.includes('ngrok')
                            || (!window.location.hostname.startsWith('192.168.') && !window.location.hostname.startsWith('10.') && window.location.hostname !== 'localhost' && window.location.hostname !== '127.0.0.1');

        if (targetId) {
            // Có ID trong URL -> Tự động chuyển sang tab Client và chế độ Internet
            switchView('viewClient');
            if (tabModeInternet) tabModeInternet.click();
            if (clientTargetDeviceId) {
                let digits = targetId.replace(/\D/g, '').slice(0, 9);
                let formatted = '';
                for (let i = 0; i < digits.length; i++) {
                    if (i > 0 && i % 3 === 0) formatted += ' ';
                    formatted += digits[i];
                }
                clientTargetDeviceId.value = formatted;
            }
            if (targetPass && clientTargetPass) {
                clientTargetPass.value = targetPass;
            }
            if (clientTargetPass) {
                clientTargetPass.focus();
            }
            showToast('Đã tự động tải Mã Thiết Bị: ' + targetId, 'info');
        } else if (isPublicDomain) {
            // Khi mở từ tên miền Internet công cộng, ưu tiên chuyển chế độ Client Internet
            switchView('viewClient');
            if (tabModeInternet) tabModeInternet.click();
        }
    } catch (e) {
        console.warn('Lỗi phân tích URL params:', e);
    }

    // Khởi chạy ban đầu: Lấy server-info
    fetchServerInfo();
})();
