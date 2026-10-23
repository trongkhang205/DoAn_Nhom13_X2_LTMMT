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
    if (ctx) {
        ctx.imageSmoothingEnabled = true;
        ctx.imageSmoothingQuality = 'high';
    }
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
    const wrapScreenSelector = document.getElementById('wrapScreenSelector');
    const btnScreenDropdown = document.getElementById('btnScreenDropdown');
    const menuScreens = document.getElementById('menuScreens');
    const lblActiveScreen = document.getElementById('lblActiveScreen');
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
    let e2eeCryptoKey = null;
    let activeSessionPassword = '';
    let totalBinaryFramesReceived = 0;
    let isE2eeActive = false;
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

        // Cập nhật điểm nhấn mạng lưới hạt theo từng chế độ
        if (typeof window.setNetworkTheme === 'function') {
            window.setNetworkTheme(viewId);
        }

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
        if (info.ip === 'Cloud-Relay') {
            if (info.isSharingEnabled) {
                btnToggleServerShare.className = 'btn-power active';
                lblServerPower.textContent = 'MÁY CHỦ TRỰC TUYẾN';
                serverStateDot.className = 'status-dot dot-green';
                navServerStatusText.textContent = `Thiết Bị: Sẵn Sàng (${info.formattedDeviceId || info.deviceId})`;
            } else {
                btnToggleServerShare.className = 'btn-power off';
                lblServerPower.textContent = 'MÁY CHỦ NGOẠI TUYẾN';
                serverStateDot.className = 'status-dot dot-red';
                navServerStatusText.textContent = 'Gateway: Chưa Có Máy Chủ Online';
            }
        } else {
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
        }

        // Tự động điền trước Device ID nếu form đang trống và Gateway có thiết bị online
        if (clientTargetDeviceId && !clientTargetDeviceId.value && info.deviceId) {
            clientTargetDeviceId.value = info.formattedDeviceId || info.deviceId;
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

                    // Nếu vừa bật và chưa có URL, hẹn giờ poll liên tục đến khi có URL (tối đa 40s)
                    if (data.isTunnelActive && !data.tunnelUrl) {
                        if (tunnelPollTimer) clearInterval(tunnelPollTimer);
                        let pollAttempts = 0;
                        tunnelPollTimer = setInterval(async () => {
                            pollAttempts++;
                            if (dispTunnelUrl && (!cachedServerInfo || !cachedServerInfo.tunnelUrl)) {
                                dispTunnelUrl.textContent = `Đang kết nối Cloudflare Edge... Vui lòng chờ (${pollAttempts}s)`;
                            }
                            await fetchServerInfo();
                            if ((cachedServerInfo && cachedServerInfo.tunnelUrl) || pollAttempts >= 35) {
                                clearInterval(tunnelPollTimer);
                                tunnelPollTimer = null;
                            }
                        }, 1200);
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
        if (remoteViewerModal && remoteViewerModal.classList.contains('active')) {
            // Khi dang xem man hinh tu xa, tam dung poll log de tiet kiem bang thong
            return;
        }
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
        initAudioContext();
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
        activeSessionPassword = (password || '').trim().toLowerCase();
        totalBinaryFramesReceived = 0;

        // Khởi tạo trước khóa mã hóa đầu cuối E2EE (AES-256-GCM) ngay khi bấm kết nối
        if (password) {
            deriveE2eeKey(password);
        }

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
                    password: (password || '').trim().toLowerCase()
                });
            };

            ws.onmessage = async (event) => {
                if (typeof event.data === 'string') {
                    handleTextMessage(event.data);
                } else if (event.data instanceof ArrayBuffer) {
                    handleBinaryFrame(event.data);
                } else if (event.data instanceof Blob) {
                    try {
                        const buf = await event.data.arrayBuffer();
                        handleBinaryFrame(buf);
                    } catch (e) {
                        console.error('Lỗi chuyển đổi Blob sang ArrayBuffer:', e);
                    }
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

        // Khởi tạo trước khóa mã hóa đầu cuối E2EE (AES-256-GCM) ngay khi bấm kết nối
        if (password) {
            deriveE2eeKey(password);
        }

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

            ws.onmessage = async (event) => {
                if (typeof event.data === 'string') {
                    handleTextMessage(event.data);
                } else if (event.data instanceof ArrayBuffer) {
                    handleBinaryFrame(event.data);
                } else if (event.data instanceof Blob) {
                    try {
                        const buf = await event.data.arrayBuffer();
                        handleBinaryFrame(buf);
                    } catch (e) {
                        console.error('Lỗi chuyển đổi Blob sang ArrayBuffer:', e);
                    }
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
                    document.body.classList.add('viewer-connected-mode');
                    if (typeof window.pauseNetworkAnimation === 'function') {
                        window.pauseNetworkAnimation(true);
                    }
                    if (logPollInterval) {
                        clearInterval(logPollInterval);
                        logPollInterval = null;
                    }
                    setTimeout(updateCanvasRect, 50);
                    showToast('Xác thực THÀNH CÔNG! Bắt đầu điều khiển máy tính.', 'success');

                    if (msg.width && msg.height) {
                        serverWidth = msg.width;
                        serverHeight = msg.height;
                        remoteCanvas.width = serverWidth;
                        remoteCanvas.height = serverHeight;
                        valResolution.textContent = `${serverWidth}x${serverHeight}`;
                    }

                    if (msg.e2ee) {
                        isE2eeActive = true;
                        const statE2ee = document.getElementById('statE2ee');
                        if (statE2ee) {
                            statE2ee.style.display = 'inline-flex';
                            statE2ee.style.cursor = 'pointer';
                            statE2ee.title = 'Bấm để Bật/Tắt Mã Hóa E2EE';
                            statE2ee.onclick = () => {
                                isE2eeActive = !isE2eeActive;
                                sendJson({ type: 'set_e2ee', enabled: isE2eeActive });
                                showToast(isE2eeActive ? 'Đã kích hoạt E2EE AES-256-GCM' : 'Đã chuyển sang luồng trực tiếp TLS', 'info');
                            };
                        }
                        if (!e2eeCryptoKey) {
                            const pass = clientTargetPass ? clientTargetPass.value.trim().toLowerCase() : '';
                            if (pass) deriveE2eeKey(pass);
                        }
                    }

                    startPingHeartbeat();
                    remoteCanvas.focus();
                    // Yêu cầu danh sách màn hình hiển thị (Đa màn hình)
                    sendJson({ type: 'get_screens' });
                } else {
                    showClientError(msg.message || 'Mật khẩu xác thực không đúng!');
                    showToast(msg.message || 'Kết nối không thành công!', 'error');
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
                    if (rtt > 260 && window.currentAdaptiveQuality !== 0.35) {
                        window.currentAdaptiveQuality = 0.35;
                        sendJson({ type: 'quality', value: '0.35' });
                        sendJson({ type: 'target_fps', value: '18' });
                    } else if (rtt > 120 && rtt <= 260 && window.currentAdaptiveQuality !== 0.50) {
                        window.currentAdaptiveQuality = 0.50;
                        sendJson({ type: 'quality', value: '0.50' });
                        sendJson({ type: 'target_fps', value: '24' });
                    } else if (rtt <= 120 && window.currentAdaptiveQuality !== 0.65) {
                        window.currentAdaptiveQuality = 0.65;
                        sendJson({ type: 'quality', value: '0.65' });
                        sendJson({ type: 'target_fps', value: '30' });
                    }
                }
            } else if (msg.type === 'screen_list') {
                renderScreensMenu(msg.screens, msg.current);
            } else if (msg.type === 'e2ee_status') {
                isE2eeActive = !!msg.enabled;
                const statE2ee = document.getElementById('statE2ee');
                if (statE2ee) statE2ee.style.display = isE2eeActive ? 'inline-flex' : 'none';
            } else if (msg.type === 'uac_status') {
                const uacBanner = document.getElementById('uacWarningBanner');
                if (uacBanner) {
                    uacBanner.style.display = msg.active ? 'flex' : 'none';
                }
                if (msg.active) {
                    showToast('Phát hiện màn hình xác nhận quyền Windows UAC / Admin trên máy chủ!', 'warning');
                } else {
                    showToast('Đã mở khóa UAC, tiếp tục điều khiển máy tính!', 'success');
                }
            } else if (msg.type === 'screen_switched') {
                if (msg.success) {
                    showToast(`Đã chuyển sang Màn hình ${msg.index + 1} (${msg.width}x${msg.height})`, 'success');
                } else {
                    showToast(`Không thể chuyển sang Màn hình ${msg.index + 1}`, 'error');
                }
            } else if (msg.type === 'clipboard_data') {
                txtClipboard.value = msg.text || '';
                showToast('Đã nhận nội dung Clipboard từ máy từ xa', 'info');
            } else if (msg.type === 'file_upload_ready') {
                if (typeof window.onFileUploadReady === 'function') window.onFileUploadReady(msg);
            } else if (msg.type === 'file_upload_success') {
                if (typeof window.onFileUploadSuccess === 'function') {
                    window.onFileUploadSuccess(msg);
                } else {
                    showToast(`Đã gửi tệp [${msg.name}] lên máy chủ thành công!`, 'success');
                    if (uploadProgressBar) uploadProgressBar.style.width = '100%';
                    if (lblUploadProgress) lblUploadProgress.textContent = 'Hoàn tất 100%!';
                }
            } else if (msg.type === 'file_upload_error') {
                if (typeof window.onFileUploadError === 'function') {
                    window.onFileUploadError(msg);
                } else {
                    showToast(msg.message || 'Lỗi tải tệp lên!', 'error');
                }
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

    // ================= STRICT FIFO FRAME RENDERING QUEUE =================
    // Loại bỏ hoàn toàn lỗi hình giật/nhấp nháy do giải mã Promise bất đồng bộ ngoài thứ tự
    const frameQueue = [];
    let isProcessingFrame = false;

    function enqueueFrame(frameTask) {
        frameQueue.push(frameTask);
        if (!isProcessingFrame) {
            processFrameQueue();
        }
    }

    async function processFrameQueue() {
        if (isProcessingFrame) return;
        isProcessingFrame = true;

        while (frameQueue.length > 0) {
            // Tự động triệt tiêu độ trễ: Nếu hàng đợi bị ứ đọng (> 3 frames), tự động nhảy cóc tới Keyframe mới nhất
            if (frameQueue.length > 3) {
                let newestKeyIdx = -1;
                for (let i = frameQueue.length - 1; i >= 0; i--) {
                    if (frameQueue[i].isKeyframe) {
                        newestKeyIdx = i;
                        break;
                    }
                }
                if (newestKeyIdx > 0) {
                    frameQueue.splice(0, newestKeyIdx);
                }
            }

            const task = frameQueue.shift();
            if (!task) break;

            try {
                if (window.createImageBitmap) {
                    const bitmap = await createImageBitmap(task.blob);
                    ctx.drawImage(bitmap, task.targetX, task.targetY, task.targetW, task.targetH);
                    bitmap.close();
                } else {
                    await new Promise((resolve) => {
                        const url = URL.createObjectURL(task.blob);
                        const img = new Image();
                        img.onload = () => {
                            ctx.drawImage(img, task.targetX, task.targetY, task.targetW, task.targetH);
                            URL.revokeObjectURL(url);
                            resolve();
                        };
                        img.onerror = () => {
                            URL.revokeObjectURL(url);
                            resolve();
                        };
                        img.src = url;
                    });
                }

                if (canvasLoading.style.display !== 'none') {
                    canvasLoading.style.display = 'none';
                }
                updateFpsCounter();
            } catch (renderErr) {
                console.warn('[Render Queue Error]:', renderErr);
            }
        }

        isProcessingFrame = false;
    }

    async function handleBinaryFrame(arrayBuffer) {
        if (!isAuthenticated) return;

        totalBinaryFramesReceived++;
        if (canvasLoading && canvasLoading.style.display !== 'none') {
            const lbl = canvasLoading.querySelector('p');
            if (lbl) lbl.textContent = `Đang đồng bộ luồng màn hình... (${totalBinaryFramesReceived} gói tin)`;
        }

        const firstByte = new Uint8Array(arrayBuffer, 0, 1)[0];

        // 1. Nhận diện và phát Frame Âm Thanh (Audio Frame 0xAA)
        if (firstByte === 0xAA) {
            if (arrayBuffer.byteLength > 6) {
                const audioView = new DataView(arrayBuffer);
                const sampleRate = audioView.getInt32(1);
                const pcmView = new DataView(arrayBuffer, 6);
                playAudioPcmChunk(pcmView, sampleRate);
            }
            return;
        }

        // 2. Nhận diện và giải mã gói tin E2EE AES-256-GCM
        if (firstByte === 0xEE) {
            if (!isE2eeActive) {
                // Yêu cầu server tắt E2EE để gửi trực tiếp
                sendJson({ type: 'set_e2ee', enabled: false });
                return;
            }
            if (!e2eeCryptoKey) {
                const pass = activeSessionPassword || (clientTargetPass ? clientTargetPass.value.trim().toLowerCase() : '');
                if (pass) {
                    await deriveE2eeKey(pass);
                }
            }
            if (e2eeCryptoKey && window.crypto && window.crypto.subtle) {
                try {
                    const iv = new Uint8Array(arrayBuffer, 1, 12);
                    const ciphertext = new Uint8Array(arrayBuffer, 13);
                    const decrypted = await window.crypto.subtle.decrypt(
                        { name: 'AES-GCM', iv: iv },
                        e2eeCryptoKey,
                        ciphertext
                    );
                    arrayBuffer = decrypted;
                } catch (decErr) {
                    console.warn('[E2EE Fallback] Lỗi giải mã AES-GCM, tự động chuyển chế độ TLS trực tiếp:', decErr);
                    isE2eeActive = false;
                    const statE2ee = document.getElementById('statE2ee');
                    if (statE2ee) statE2ee.style.display = 'none';
                    sendJson({ type: 'set_e2ee', enabled: false });
                    return;
                }
            } else {
                console.warn('[E2EE Fallback] Không có khóa WebCrypto, tự động chuyển chế độ TLS trực tiếp');
                isE2eeActive = false;
                const statE2ee = document.getElementById('statE2ee');
                if (statE2ee) statE2ee.style.display = 'none';
                sendJson({ type: 'set_e2ee', enabled: false });
                return;
            }
        }

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

        function updateFpsCounter() {
            frameCount++;
            const now = performance.now();
            if (now - lastFpsTime >= 1000) {
                currentFps = Math.round((frameCount * 1000) / (now - lastFpsTime));
                valFps.textContent = currentFps;
                frameCount = 0;
                lastFpsTime = now;
            }
        }

        const blob = new Blob([jpegBytes], { type: 'image/jpeg' });
        enqueueFrame({
            blob: blob,
            targetX: targetX,
            targetY: targetY,
            targetW: targetW,
            targetH: targetH,
            isKeyframe: (frameType === 0)
        });
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
        e2eeCryptoKey = null;
        isE2eeActive = false;
        const statE2ee = document.getElementById('statE2ee');
        if (statE2ee) statE2ee.style.display = 'none';
        if (pingInterval) {
            clearInterval(pingInterval);
            pingInterval = null;
        }
        setConnectingButton(false);
        remoteViewerModal.classList.remove('active');
        document.body.classList.remove('viewer-connected-mode');
        if (typeof window.pauseNetworkAnimation === 'function') {
            window.pauseNetworkAnimation(false);
        }
        if (!logPollInterval && document.getElementById('viewServer').classList.contains('active')) {
            logPollInterval = setInterval(fetchServerLogs, 2500);
        }
        frameQueue.length = 0;
        isProcessingFrame = false;
        const toolbarContainer = document.getElementById('floatingToolbarContainer');
        if (toolbarContainer) toolbarContainer.classList.remove('expanded');
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
    // Nút tròn hoa chữ "T" (Tool Orb) mở / đóng thanh công cụ
    const btnToolOrb = document.getElementById('btnToolOrb');
    const floatingToolbarContainer = document.getElementById('floatingToolbarContainer');
    if (btnToolOrb && floatingToolbarContainer) {
        btnToolOrb.addEventListener('click', (e) => {
            e.stopPropagation();
            floatingToolbarContainer.classList.toggle('expanded');
        });

        // Click bên ngoài thanh công cụ để tự động thu gọn lại thành nút tròn T
        document.addEventListener('click', (e) => {
            if (!floatingToolbarContainer.contains(e.target)) {
                floatingToolbarContainer.classList.remove('expanded');
            }
        });
    }

    setupDropdown(btnQualityDropdown, menuQuality);
    setupDropdown(btnSpecialKeys, menuSpecialKeys);
    if (btnScreenDropdown && menuScreens) {
        setupDropdown(btnScreenDropdown, menuScreens);
    }

    document.addEventListener('click', () => {
        document.querySelectorAll('.dropdown-wrapper').forEach(w => w.classList.remove('open'));
    });

    menuQuality.querySelectorAll('.menu-item').forEach(item => {
        item.addEventListener('click', () => {
            window.isManualQualitySelected = true;
            const qualityVal = parseFloat(item.getAttribute('data-quality'));
            const fpsVal = parseInt(item.getAttribute('data-fps')) || 30;
            menuQuality.querySelectorAll('.menu-item').forEach(i => i.classList.remove('active'));
            item.classList.add('active');
            lblQuality.textContent = item.textContent;
            sendJson({ type: 'quality', value: qualityVal });
            sendJson({ type: 'target_fps', value: fpsVal });
            sendJson({ type: 'force_keyframe' }); // Ép làm mới tức thì toàn bộ màn hình siêu sắc nét
            showToast(`Đã chuyển: ${item.textContent}`, 'info');
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
    // Cache getBoundingClientRect to eliminate layout thrashing during mousemove
    let cachedCanvasRect = null;
    function updateCanvasRect() {
        if (remoteCanvas && remoteViewerModal && remoteViewerModal.classList.contains('active')) {
            cachedCanvasRect = remoteCanvas.getBoundingClientRect();
        }
    }
    window.addEventListener('resize', updateCanvasRect);
    window.addEventListener('scroll', updateCanvasRect);

    function getCanvasCoordinates(e) {
        if (!cachedCanvasRect || cachedCanvasRect.width === 0) {
            cachedCanvasRect = remoteCanvas.getBoundingClientRect();
        }
        const rect = cachedCanvasRect;
        const scaleX = serverWidth / (rect.width || 1);
        const scaleY = serverHeight / (rect.height || 1);

        const clientX = e.clientX - rect.left;
        const clientY = e.clientY - rect.top;

        const x = Math.max(0, Math.min(serverWidth, Math.round(clientX * scaleX)));
        const y = Math.max(0, Math.min(serverHeight, Math.round(clientY * scaleY)));

        return { x, y };
    }


    remoteCanvas.addEventListener('mousemove', (e) => {
        if (!isControlEnabled || !isAuthenticated) return;
        const now = performance.now();
        if (now - lastMouseMoveTime < 32) return;
        if (ws && ws.bufferedAmount > 32 * 1024) return;
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

    let wheelAccumulator = 0;
    let wheelTimer = null;
    remoteCanvas.addEventListener('wheel', (e) => {
        if (!isControlEnabled || !isAuthenticated) return;
        e.preventDefault();
        wheelAccumulator += e.deltaY;
        if (!wheelTimer) {
            wheelTimer = setTimeout(() => {
                if (wheelAccumulator !== 0) {
                    const notches = Math.round(wheelAccumulator / 45) || (wheelAccumulator > 0 ? 1 : -1);
                    sendJson({ type: 'mouse', action: 'wheel', deltaY: notches });
                    wheelAccumulator = 0;
                }
                wheelTimer = null;
            }, 35); // Điều tiết 35ms dồn bước cuộn mượt mà, triệt tiêu delay và nghẽn gói tin
        }
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
    // ================= FILE TRANSFER 2-WAY (QUẢN LÝ TỆP TIN HAI CHIỀU SIÊU TỐC) =================
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
    const uploadQueueContainer = document.getElementById('uploadQueueContainer');
    const uploadQueueList = document.getElementById('uploadQueueList');
    const lblQueueCount = document.getElementById('lblQueueCount');
    const btnClearQueue = document.getElementById('btnClearQueue');
    const uploadProgressContainer = document.getElementById('uploadProgressContainer');
    const uploadProgressBar = document.getElementById('uploadProgressBar');
    const lblUploadSpeed = document.getElementById('lblUploadSpeed');
    const lblUploadProgress = document.getElementById('lblUploadProgress');
    const btnStartUpload = document.getElementById('btnStartUpload');
    const btnRefreshFiles = document.getElementById('btnRefreshFiles');
    const remoteFilesList = document.getElementById('remoteFilesList');
    const chkSelectAllDl = document.getElementById('chkSelectAllDl');
    const btnDownloadSelected = document.getElementById('btnDownloadSelected');

    let uploadQueue = []; // [{ id, file, status: 'pending'|'uploading'|'success'|'error', percent: 0 }]
    let isUploadingQueue = false;
    let uploadAckWaiters = {};
    let downloadFileChunks = {};
    let downloadResolvers = {};
    let remoteFilesData = [];

    // Lắng nghe tín hiệu ACK xác thực từ máy chủ
    window.onFileUploadSuccess = function (msg) {
        const fileId = msg.fileId;
        if (fileId && uploadAckWaiters[fileId]) {
            uploadAckWaiters[fileId](msg);
            delete uploadAckWaiters[fileId];
        }
    };

    window.onFileUploadError = function (msg) {
        const fileId = msg.fileId;
        if (fileId && uploadAckWaiters[fileId]) {
            uploadAckWaiters[fileId]({ error: true, message: msg.message });
            delete uploadAckWaiters[fileId];
        }
    };

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

        // Kéo thả một hoặc nhiều tệp tin
        if (fileDropZone) {
            fileDropZone.addEventListener('dragover', (e) => {
                e.preventDefault();
                fileDropZone.classList.add('dragover');
            });
            fileDropZone.addEventListener('dragleave', () => fileDropZone.classList.remove('dragover'));
            fileDropZone.addEventListener('drop', (e) => {
                e.preventDefault();
                fileDropZone.classList.remove('dragover');
                if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
                    addFilesToUploadQueue(e.dataTransfer.files);
                }
            });
        }

        if (fileInputUpload) {
            fileInputUpload.addEventListener('change', () => {
                if (fileInputUpload.files && fileInputUpload.files.length > 0) {
                    addFilesToUploadQueue(fileInputUpload.files);
                    fileInputUpload.value = ''; // Cho phép chọn lại cùng tệp
                }
            });
        }

        if (btnClearQueue) {
            btnClearQueue.addEventListener('click', () => {
                if (isUploadingQueue) return;
                uploadQueue = [];
                renderUploadQueue();
            });
        }

        function addFilesToUploadQueue(fileList) {
            for (let i = 0; i < fileList.length; i++) {
                const file = fileList[i];
                uploadQueue.push({
                    id: 'f_' + Date.now() + '_' + Math.random().toString(36).substring(2, 7),
                    file: file,
                    status: 'pending',
                    percent: 0
                });
            }
            renderUploadQueue();
        }

        function renderUploadQueue() {
            if (uploadQueue.length === 0) {
                if (uploadQueueContainer) uploadQueueContainer.style.display = 'none';
                if (btnStartUpload) btnStartUpload.disabled = true;
                return;
            }

            if (uploadQueueContainer) uploadQueueContainer.style.display = 'block';
            if (lblQueueCount) lblQueueCount.textContent = `Hàng đợi gửi: ${uploadQueue.length} tệp`;
            if (btnStartUpload && !isUploadingQueue) btnStartUpload.disabled = false;

            if (!uploadQueueList) return;
            uploadQueueList.innerHTML = uploadQueue.map((item, idx) => {
                let badgeClass = 'pending';
                let badgeText = 'Chờ gửi';
                if (item.status === 'uploading') {
                    badgeClass = 'uploading';
                    badgeText = `${item.percent}%`;
                } else if (item.status === 'success') {
                    badgeClass = 'success';
                    badgeText = 'Hoàn tất';
                } else if (item.status === 'error') {
                    badgeClass = 'error';
                    badgeText = 'Lỗi';
                }

                return `
                    <div class="queue-file-item" id="queue_item_${item.id}">
                        <div class="queue-file-meta">
                            <span class="queue-file-name" title="${escapeHtml(item.file.name)}">${escapeHtml(item.file.name)}</span>
                            <span class="queue-file-size">${formatBytes(item.file.size)}</span>
                        </div>
                        <div class="queue-file-right">
                            <span class="queue-badge ${badgeClass}" id="badge_${item.id}">${badgeText}</span>
                            ${!isUploadingQueue && item.status !== 'uploading' ? `<button type="button" class="btn-queue-remove" data-id="${item.id}">✕</button>` : ''}
                        </div>
                    </div>
                `;
            }).join('');

            uploadQueueList.querySelectorAll('.btn-queue-remove').forEach(btn => {
                btn.addEventListener('click', () => {
                    const id = btn.getAttribute('data-id');
                    uploadQueue = uploadQueue.filter(item => item.id !== id);
                    renderUploadQueue();
                });
            });
        }

        // Bắt đầu truyền toàn bộ hàng đợi tệp tin với bộ đệm 128KB & Backpressure
        if (btnStartUpload) {
            btnStartUpload.addEventListener('click', async () => {
                if (isUploadingQueue || uploadQueue.length === 0) return;
                isUploadingQueue = true;
                btnStartUpload.disabled = true;
                if (btnClearQueue) btnClearQueue.style.display = 'none';

                if (uploadProgressContainer) uploadProgressContainer.style.display = 'block';
                if (uploadProgressBar) uploadProgressBar.style.width = '0%';
                if (lblUploadSpeed) lblUploadSpeed.textContent = '0.0 MB/s';
                if (lblUploadProgress) lblUploadProgress.textContent = 'Khởi động truyền tệp...';

                let successCount = 0;
                const totalFiles = uploadQueue.length;

                for (let fIdx = 0; fIdx < totalFiles; fIdx++) {
                    const item = uploadQueue[fIdx];
                    if (item.status === 'success') continue;

                    item.status = 'uploading';
                    renderUploadQueue();

                    const file = item.file;
                    const fileId = item.id;
                    const totalSize = file.size;

                    // 1. Thông báo bắt đầu tệp
                    sendJson({
                        type: 'file_upload_start',
                        fileId: fileId,
                        name: file.name,
                        size: totalSize
                    });

                    // 2. Cắt lát và gửi từng chunk 128KB
                    const chunkSize = 128 * 1024; // 128KB tối ưu
                    const totalChunks = Math.ceil(totalSize / chunkSize) || 1;
                    let sentBytes = 0;
                    const uploadStartTime = performance.now();

                    for (let cIdx = 0; cIdx < totalChunks; cIdx++) {
                        // Kiểm soát dòng dữ liệu (Backpressure) tránh nghẽn bộ đệm WebSocket
                        while (ws && ws.bufferedAmount > 384 * 1024) {
                            await new Promise(r => setTimeout(r, 4));
                        }

                        const start = cIdx * chunkSize;
                        const end = Math.min(start + chunkSize, totalSize);
                        const slice = file.slice(start, end);
                        const b64 = await readBlobAsBase64(slice);

                        sendJson({
                            type: 'file_upload_chunk',
                            fileId: fileId,
                            data: b64
                        });

                        sentBytes += (end - start);
                        const percent = Math.min(99, Math.round((sentBytes / totalSize) * 100));
                        item.percent = percent;

                        // Cập nhật giao diện tốc độ MB/s
                        const elapsedSec = (performance.now() - uploadStartTime) / 1000;
                        const speedMBps = elapsedSec > 0 ? ((sentBytes / (1024 * 1024)) / elapsedSec).toFixed(1) : '0.0';
                        if (lblUploadSpeed) lblUploadSpeed.textContent = `${speedMBps} MB/s`;

                        const overallPercent = Math.min(99, Math.round(((fIdx + (sentBytes / totalSize)) / totalFiles) * 100));
                        if (uploadProgressBar) uploadProgressBar.style.width = `${overallPercent}%`;
                        if (lblUploadProgress) {
                            lblUploadProgress.textContent = `Tệp ${fIdx + 1}/${totalFiles} (${escapeHtml(file.name)}): ${percent}%`;
                        }

                        const badgeEl = document.getElementById(`badge_${item.id}`);
                        if (badgeEl) badgeEl.textContent = `${percent}%`;
                    }

                    // 3. Gửi lệnh kết thúc tệp và CHỜ XÁC THỰC LƯU XONG TỪ MÁY CHỦ (ACK)
                    if (lblUploadProgress) {
                        lblUploadProgress.textContent = `Đang xác thực lưu tệp [${escapeHtml(file.name)}] trên máy chủ...`;
                    }
                    sendJson({ type: 'file_upload_end', fileId: fileId });

                    // Chờ Server phản hồi file_upload_success
                    const ackResult = await waitForUploadAck(fileId);
                    if (ackResult && !ackResult.error) {
                        item.status = 'success';
                        item.percent = 100;
                        successCount++;
                    } else {
                        item.status = 'error';
                    }
                    renderUploadQueue();
                }

                // Hoàn tất toàn bộ hàng đợi
                isUploadingQueue = false;
                if (btnClearQueue) btnClearQueue.style.display = 'inline-block';
                if (uploadProgressBar) uploadProgressBar.style.width = '100%';
                if (lblUploadProgress) {
                    lblUploadProgress.textContent = `Hoàn tất gửi ${successCount}/${totalFiles} tệp tin!`;
                }

                showToast(`Đã gửi thành công ${successCount}/${totalFiles} tệp tin lên máy chủ!`, 'success');
                sendJson({ type: 'file_list' }); // Cập nhật lại danh sách tệp

                setTimeout(() => {
                    if (uploadProgressContainer) uploadProgressContainer.style.display = 'none';
                    if (btnStartUpload) btnStartUpload.disabled = false;
                }, 3000);
            });
        }
    }

    // Helper: Chờ phản hồi ACK từ máy chủ với timeout 45s
    function waitForUploadAck(fileId) {
        return new Promise((resolve) => {
            const timer = setTimeout(() => {
                delete uploadAckWaiters[fileId];
                resolve({ error: false }); // Quá thời gian thì tiếp tục
            }, 45000);

            uploadAckWaiters[fileId] = (msg) => {
                clearTimeout(timer);
                resolve(msg);
            };
        });
    }

    // Helper: Đọc Blob thành chuỗi Base64 siêu tốc qua FileReader native
    function readBlobAsBase64(blob) {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onloadend = () => {
                const res = reader.result;
                const commaIdx = res.indexOf(',');
                resolve(commaIdx !== -1 ? res.substring(commaIdx + 1) : res);
            };
            reader.onerror = reject;
            reader.readAsDataURL(blob);
        });
    }

    // ================= TẢI TỆP TIN TỪ MÁY CHỦ (BATCH DOWNLOAD) =================
    function renderRemoteFilesList(files) {
        remoteFilesData = files || [];
        if (!remoteFilesList) return;
        if (!files || files.length === 0) {
            remoteFilesList.innerHTML = '<p class="empty-files-text">Thư mục nhận trên máy chủ hiện đang trống.</p>';
            if (btnDownloadSelected) btnDownloadSelected.style.display = 'none';
            if (chkSelectAllDl) chkSelectAllDl.checked = false;
            return;
        }

        remoteFilesList.innerHTML = files.map(f => `
            <div class="remote-file-item">
                <div class="remote-file-left">
                    <input type="checkbox" class="chk-dl-file" data-name="${escapeHtml(f.name)}">
                    <div class="file-meta">
                        <span class="file-name">${escapeHtml(f.name)}</span>
                        <span class="file-sub">${formatBytes(f.size)} • ${escapeHtml(f.date)}</span>
                    </div>
                </div>
                <button class="btn-dl-file" data-name="${escapeHtml(f.name)}">⬇ Tải Về</button>
            </div>
        `).join('');

        // Cập nhật trạng thái nút tải nhiều tệp
        const checkBoxes = remoteFilesList.querySelectorAll('.chk-dl-file');
        const updateBatchBtn = () => {
            const selected = Array.from(checkBoxes).filter(cb => cb.checked);
            if (btnDownloadSelected) {
                if (selected.length > 0) {
                    btnDownloadSelected.style.display = 'inline-block';
                    btnDownloadSelected.textContent = `⬇ Tải các mục đã chọn (${selected.length})`;
                } else {
                    btnDownloadSelected.style.display = 'none';
                }
            }
            if (chkSelectAllDl) {
                chkSelectAllDl.checked = selected.length > 0 && selected.length === checkBoxes.length;
            }
        };

        checkBoxes.forEach(cb => cb.addEventListener('change', updateBatchBtn));

        if (chkSelectAllDl) {
            chkSelectAllDl.onchange = () => {
                checkBoxes.forEach(cb => cb.checked = chkSelectAllDl.checked);
                updateBatchBtn();
            };
        }

        if (btnDownloadSelected) {
            btnDownloadSelected.onclick = async () => {
                const selectedNames = Array.from(checkBoxes)
                    .filter(cb => cb.checked)
                    .map(cb => cb.getAttribute('data-name'));

                if (selectedNames.length === 0) return;
                showToast(`Bắt đầu tải ${selectedNames.length} tệp tin từ máy chủ...`, 'info');

                for (const name of selectedNames) {
                    await startDownloadFile(name);
                }
            };
        }

        remoteFilesList.querySelectorAll('.btn-dl-file').forEach(btn => {
            btn.addEventListener('click', () => {
                const name = btn.getAttribute('data-name');
                startDownloadFile(name);
            });
        });
    }

    function startDownloadFile(name) {
        return new Promise((resolve) => {
            downloadFileChunks[name] = [];
            downloadResolvers[name] = resolve;
            sendJson({ type: 'file_download_req', name: name });
            showToast(`Đang tải tệp [${name}] từ máy chủ...`, 'info');
        });
    }

    function base64ToUint8Array(b64) {
        const bin = atob(b64);
        const len = bin.length;
        const bytes = new Uint8Array(len);
        for (let i = 0; i < len; i++) {
            bytes[i] = bin.charCodeAt(i);
        }
        return bytes;
    }

    function handleDownloadChunk(msg) {
        if (!downloadFileChunks[msg.name]) downloadFileChunks[msg.name] = [];
        try {
            downloadFileChunks[msg.name].push(base64ToUint8Array(msg.data));
        } catch (e) {
            console.error('Lỗi giải mã chunk tệp:', e);
        }
    }

    function finalizeFileDownload(fileName) {
        const chunks = downloadFileChunks[fileName];
        if (!chunks || chunks.length === 0) return;

        // Tạo trực tiếp Blob từ mảng Uint8Array trong 1ms, không bị đơ 1-2 phút
        const blob = new Blob(chunks, { type: 'application/octet-stream' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = fileName;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        setTimeout(() => URL.revokeObjectURL(url), 15000);
        delete downloadFileChunks[fileName];

        if (downloadResolvers[fileName]) {
            downloadResolvers[fileName]();
            delete downloadResolvers[fileName];
        }

        showToast(`Đã tải xong tệp [${fileName}] về máy!`, 'success');
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
            const now = performance.now();
            if (now - lastMouseMoveTime < 32) return;
            if (ws && ws.bufferedAmount > 32 * 1024) return;
            lastMouseMoveTime = now;
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


    // ================= THEME TOGGLE (LIGHT & DARK MODE) =================
    const btnThemeToggle = document.getElementById('btnThemeToggle');
    const initTheme = () => {
        const savedTheme = localStorage.getItem('remote_app_theme') || 'light';
        document.documentElement.setAttribute('data-theme', savedTheme);
    };
    initTheme();

    if (btnThemeToggle) {
        btnThemeToggle.addEventListener('click', () => {
            const current = document.documentElement.getAttribute('data-theme') || 'dark';
            const next = current === 'dark' ? 'light' : 'dark';
            document.documentElement.setAttribute('data-theme', next);
            localStorage.setItem('remote_app_theme', next);
            showToast(`Đã chuyển sang giao diện ${next === 'dark' ? 'Tối (Dark Mode)' : 'Sáng (Light Mode)'}`, 'info');
        });
    }

    // ================= HERO CTA BUTTON SCROLL =================
    const btnHeroStart = document.getElementById('btnHeroStart');
    if (btnHeroStart) {
        btnHeroStart.addEventListener('click', () => {
            const headerEl = document.getElementById('launcherSectionHeader');
            if (headerEl) {
                headerEl.scrollIntoView({ behavior: 'smooth' });
            }
        });
    }

    // ================= 3D TILT EFFECT ON CARDS =================
    const launcherCards = document.querySelectorAll('.launcher-card');
    launcherCards.forEach(card => {
        card.addEventListener('mousemove', (e) => {
            const rect = card.getBoundingClientRect();
            const x = e.clientX - rect.left;
            const y = e.clientY - rect.top;
            const xPct = (x / rect.width) - 0.5;
            const yPct = (y / rect.height) - 0.5;
            card.style.transform = `perspective(1000px) rotateX(${-yPct * 14}deg) rotateY(${xPct * 14}deg) translateY(-4px)`;
        });

        card.addEventListener('mouseleave', () => {
            card.style.transform = 'perspective(1000px) rotateX(0deg) rotateY(0deg) translateY(0px)';
        });
    });

    // Khởi chạy ban đầu: Lấy server-info
    fetchServerInfo();
})();

    // ================= MULTI-MONITOR SUPPORT =================
    function renderScreensMenu(screens, currentIndex) {
        if (!screens || !menuScreens) return;
        menuScreens.innerHTML = '';
        screens.forEach(s => {
            const btn = document.createElement('button');
            btn.className = `menu-item ${s.index === currentIndex ? 'active' : ''}`;
            btn.dataset.screenIndex = s.index;
            btn.innerHTML = `🖥️ ${escapeHtml(s.name)} ${s.isPrimary ? '<span style="opacity:0.7; font-size:11px;">(Chính)</span>' : ''}`;
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                menuScreens.querySelectorAll('.menu-item').forEach(m => m.classList.remove('active'));
                btn.classList.add('active');
                if (lblActiveScreen) lblActiveScreen.textContent = `Màn hình ${s.index + 1}`;
                if (wrapScreenSelector) wrapScreenSelector.classList.remove('open');
                sendJson({ type: 'switch_screen', index: s.index });
            });
            menuScreens.appendChild(btn);
        });

        const activeScreen = screens.find(s => s.index === currentIndex) || screens[0];
        if (activeScreen && lblActiveScreen) {
            lblActiveScreen.textContent = `Màn hình ${activeScreen.index + 1}`;
        }

        if (wrapScreenSelector) {
            wrapScreenSelector.style.display = screens.length > 0 ? 'inline-flex' : 'none';
        }
    }

    // ================= END-TO-END ENCRYPTION (E2EE) =================
    async function deriveE2eeKey(password) {
        try {
            if (!window.crypto || !window.crypto.subtle) return null;
            const enc = new TextEncoder();
            const normalized = (password || '').trim().toLowerCase();
            const baseKey = await window.crypto.subtle.importKey(
                'raw',
                enc.encode(normalized),
                { name: 'PBKDF2' },
                false,
                ['deriveKey']
            );
            const salt = enc.encode('DoAn_LTMMT_Nhom13_E2EE_Salt_2026');
            e2eeCryptoKey = await window.crypto.subtle.deriveKey(
                {
                    name: 'PBKDF2',
                    salt: salt,
                    iterations: 65536,
                    hash: 'SHA-256'
                },
                baseKey,
                { name: 'AES-GCM', length: 256 },
                false,
                ['encrypt', 'decrypt']
            );
            return e2eeCryptoKey;
        } catch (err) {
            console.warn('Lỗi dẫn xuất khóa E2EE:', err);
            return null;
        }
    }

    // ================= REMOTE AUDIO STREAMING =================
    let isAudioEnabled = true;
    let audioCtx = null;
    let audioGainNode = null;
    let nextAudioPlayTime = 0;

    function initAudioContext() {
        if (!audioCtx) {
            try {
                const AudioContextClass = window.AudioContext || window.webkitAudioContext;
                audioCtx = new AudioContextClass({ sampleRate: 44100 });
                audioGainNode = audioCtx.createGain();
                audioGainNode.gain.value = 1.0;
                audioGainNode.connect(audioCtx.destination);
            } catch (e) {
                console.warn('Web Audio API not supported:', e);
            }
        }
        if (audioCtx && audioCtx.state === 'suspended') {
            audioCtx.resume().catch(e => console.warn('Audio resume error:', e));
        }
    }

    // Tự động mở khóa âm thanh trên mọi thiết bị di động / desktop ngay khi có tương tác người dùng
    const unlockAudioGesture = () => {
        initAudioContext();
    };
    ['click', 'touchstart', 'touchend', 'pointerdown'].forEach(evt => {
        document.addEventListener(evt, unlockAudioGesture, { passive: true });
    });

    function playAudioPcmChunk(pcmDataView, sampleRate) {
        if (!isAudioEnabled) return;
        initAudioContext();
        if (!audioCtx || audioCtx.state !== 'running') return;

        const numSamples = Math.floor(pcmDataView.byteLength / 2);
        if (numSamples <= 0) return;

        const floatSamples = new Float32Array(numSamples);
        for (let i = 0; i < numSamples; i++) {
            floatSamples[i] = pcmDataView.getInt16(i * 2, true) / 32768.0;
        }

        const audioBuffer = audioCtx.createBuffer(1, numSamples, sampleRate || 44100);
        audioBuffer.copyToChannel(floatSamples, 0);

        const source = audioCtx.createBufferSource();
        source.buffer = audioBuffer;
        source.connect(audioGainNode);

        const now = audioCtx.currentTime;
        if (nextAudioPlayTime < now) {
            nextAudioPlayTime = now;
        }
        source.start(nextAudioPlayTime);
        nextAudioPlayTime += audioBuffer.duration;
    }

    // Nút Bật/Tắt Âm Thanh trên Toolbar
    const btnToggleAudio = document.getElementById('btnToggleAudio');
    const iconAudioOn = document.getElementById('iconAudioOn');
    const iconAudioOff = document.getElementById('iconAudioOff');
    const lblAudioStatus = document.getElementById('lblAudioStatus');

    if (btnToggleAudio) {
        btnToggleAudio.addEventListener('click', () => {
            isAudioEnabled = !isAudioEnabled;
            if (iconAudioOn) iconAudioOn.style.display = isAudioEnabled ? 'inline-block' : 'none';
            if (iconAudioOff) iconAudioOff.style.display = isAudioEnabled ? 'none' : 'inline-block';
            if (lblAudioStatus) lblAudioStatus.textContent = isAudioEnabled ? 'Âm Thanh: Bật' : 'Âm Thanh: Tắt';
            btnToggleAudio.classList.toggle('active', isAudioEnabled);

            if (isAudioEnabled) {
                initAudioContext();
            }
            sendJson({ type: 'audio_toggle', enabled: isAudioEnabled });
            showToast(isAudioEnabled ? 'Đã bật âm thanh máy từ xa' : 'Đã tắt tiếng (Mute) âm thanh', 'info');
        });
    }


    // ================= CYBER NETWORK CONSTELLATION CANVAS (MẠNG LƯỚI KẾT NỐI CÔNG NGHỆ 60 FPS) =================
    function initNetworkCanvas() {
        const canvas = document.getElementById('networkCanvas');
        if (!canvas) return;
        const ctx = canvas.getContext('2d');
        if (!ctx) return;

        let width = canvas.width = window.innerWidth;
        let height = canvas.height = window.innerHeight;

        window.addEventListener('resize', () => {
            width = canvas.width = window.innerWidth;
            height = canvas.height = window.innerHeight;
        });

        const colorPalettes = {
            viewMenu: { node: '#38bdf8', link: 'rgba(56, 189, 248, ' },
            viewServer: { node: '#34d399', link: 'rgba(52, 211, 153, ' },
            viewClient: { node: '#818cf8', link: 'rgba(129, 140, 248, ' }
        };

        let currentPalette = colorPalettes.viewMenu;
        let isPaused = false;
        let animFrameId = null;

        window.setNetworkTheme = function(viewId) {
            if (colorPalettes[viewId]) {
                currentPalette = colorPalettes[viewId];
            }
        };
        window.setPetalsTheme = window.setNetworkTheme;

        window.pauseNetworkAnimation = function(paused) {
            isPaused = paused;
            if (paused) {
                if (animFrameId) {
                    cancelAnimationFrame(animFrameId);
                    animFrameId = null;
                }
                ctx.clearRect(0, 0, width, height);
                canvas.style.display = 'none';
            } else {
                canvas.style.display = 'block';
                if (!animFrameId) {
                    animateNetwork();
                }
            }
        };
        window.pausePetalsAnimation = window.pauseNetworkAnimation;

        const NODE_COUNT = window.innerWidth < 768 ? 20 : 40;
        const nodes = [];

        let mouseX = -1000;
        let mouseY = -1000;

        window.addEventListener('mousemove', (e) => {
            if (isPaused) return;
            mouseX = e.clientX;
            mouseY = e.clientY;
        });

        window.addEventListener('mouseleave', () => {
            mouseX = -1000;
            mouseY = -1000;
        });

        for (let i = 0; i < NODE_COUNT; i++) {
            nodes.push({
                x: Math.random() * width,
                y: Math.random() * height,
                vx: (Math.random() - 0.5) * 0.7,
                vy: (Math.random() - 0.5) * 0.7,
                radius: 1.5 + Math.random() * 2.0,
                baseAlpha: 0.35 + Math.random() * 0.35
            });
        }

        const MAX_DIST = 120;
        const MOUSE_RADIUS = 130;

        function animateNetwork() {
            if (isPaused) {
                animFrameId = null;
                return;
            }
            animFrameId = requestAnimationFrame(animateNetwork);

            ctx.clearRect(0, 0, width, height);

            // Cập nhật vị trí
            for (let i = 0; i < nodes.length; i++) {
                const n = nodes[i];
                n.x += n.vx;
                n.y += n.vy;

                if (n.x < 0 || n.x > width) n.vx *= -1;
                if (n.y < 0 || n.y > height) n.vy *= -1;

                if (mouseX > 0 && mouseY > 0) {
                    const dxm = mouseX - n.x;
                    const dym = mouseY - n.y;
                    const distM = Math.hypot(dxm, dym);
                    if (distM < MOUSE_RADIUS && distM > 0) {
                        const force = (1 - distM / MOUSE_RADIUS) * 0.035;
                        n.vx += (dxm / distM) * force;
                        n.vy += (dym / distM) * force;
                    }
                }

                const speed = Math.hypot(n.vx, n.vy);
                if (speed > 1.1) {
                    n.vx = (n.vx / speed) * 1.1;
                    n.vy = (n.vy / speed) * 1.1;
                }
            }

            // Vẽ các đường nối
            for (let i = 0; i < nodes.length; i++) {
                const a = nodes[i];
                for (let j = i + 1; j < nodes.length; j++) {
                    const b = nodes[j];
                    const dist = Math.hypot(a.x - b.x, a.y - b.y);
                    if (dist < MAX_DIST) {
                        const alpha = (1 - dist / MAX_DIST) * 0.32;
                        ctx.beginPath();
                        ctx.strokeStyle = currentPalette.link + alpha + ')';
                        ctx.lineWidth = 0.75;
                        ctx.moveTo(a.x, a.y);
                        ctx.lineTo(b.x, b.y);
                        ctx.stroke();
                    }
                }
            }

            // Vẽ các node
            for (let i = 0; i < nodes.length; i++) {
                const n = nodes[i];
                ctx.beginPath();
                ctx.arc(n.x, n.y, n.radius, 0, Math.PI * 2);
                ctx.fillStyle = currentPalette.node;
                ctx.globalAlpha = n.baseAlpha;
                ctx.fill();
            }
            ctx.globalAlpha = 1;
        }

        animateNetwork();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initNetworkCanvas);
    } else {
        setTimeout(initNetworkCanvas, 100);
    }
