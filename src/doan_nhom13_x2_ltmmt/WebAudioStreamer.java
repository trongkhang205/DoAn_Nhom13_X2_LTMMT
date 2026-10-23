package doan_nhom13_x2_ltmmt;

import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.sound.sampled.*;

/**
 * Luồng truyền tải Âm Thanh Máy Tính Từ Xa (Remote Audio Streaming) - Đồ Án Nhóm 13 (X2).
 * 1. ƯU TIÊN 1 (Windows Loopback): Chạy AudioLoopback.exe (WASAPI Loopback) để thu trực tiếp
 *    100% âm thanh kỹ thuật số phát ra từ loa/tai nghe (YouTube, Video, Spotify, Game).
 *    Tuyệt đối không lẫn tạp âm môi trường và không phụ thuộc vào Micro!
 * 2. ƯU TIÊN 2 (Fallback): Sử dụng Java AudioSystem (Stereo Mix hoặc Micro) nếu không có AudioLoopback.exe.
 * 3. Đóng gói Frame nhị phân tối ưu (Header 6 bytes):
 *    [magic=0xAA (1 byte), sampleRate (4 bytes), channels (1 byte), rawPCM (N bytes)]
 */
public class WebAudioStreamer implements Runnable {

    @FunctionalInterface
    public interface BinaryAudioSender {
        void send(byte[] payload) throws IOException;
    }

    private final BinaryAudioSender audioSender;
    private final ServerLogger logger;
    private volatile boolean running = true;
    private volatile boolean enabled = true; // Mặc định BẬT âm thanh
    private TargetDataLine audioLine;
    private Process loopbackProcess;

    private static final float DEFAULT_SAMPLE_RATE = 44100.0f;
    private static final int SAMPLE_SIZE_IN_BITS = 16;
    private static final int CHANNELS = 1; // Mono giúp giảm 50% băng thông mà vẫn sắc nét
    private static final boolean SIGNED = true;
    private static final boolean BIG_ENDIAN = false;

    public WebAudioStreamer(OutputStream out, ServerLogger logger) {
        this((payload) -> WebSocketHelper.sendBinaryFrame(out, payload), logger);
    }

    public WebAudioStreamer(BinaryAudioSender sender, ServerLogger logger) {
        this.audioSender = sender;
        this.logger = logger;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (logger != null) {
            logger.appendLog("WebAudioStreamer: Âm thanh máy chủ đã " + (enabled ? "BẬT" : "TẮT (Mute)"));
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void stop() {
        this.running = false;
        if (loopbackProcess != null) {
            try {
                loopbackProcess.destroyForcibly();
            } catch (Exception ignored) {}
            loopbackProcess = null;
        }
        if (audioLine != null) {
            try {
                audioLine.stop();
                audioLine.close();
            } catch (Exception ignored) {}
            audioLine = null;
        }
    }

    @Override
    public void run() {
        // =========================================================================
        // CHIẾN LƯỢC 1: Windows WASAPI Loopback (Thu trực tiếp âm thanh phát từ Loa)
        // =========================================================================
        File loopbackExe = findLoopbackExe();
        if (loopbackExe != null && loopbackExe.exists()) {
            boolean loopbackSuccess = runWasapiLoopback(loopbackExe);
            if (loopbackSuccess || !running) {
                return;
            }
        }

        // =========================================================================
        // CHIẾN LƯỢC 2: Fallback sang Java AudioSystem (Microphone / Stereo Mix)
        // =========================================================================
        runJavaSoundFallback();
    }

    private File findLoopbackExe() {
        String[] candidates = {
            "AudioLoopback.exe",
            "dist/AudioLoopback.exe",
            "../AudioLoopback.exe",
            "DoAn_Nhom13_X2_LTMMT/AudioLoopback.exe"
        };
        for (String c : candidates) {
            File f = new File(c);
            if (f.exists()) return f;
        }
        try {
            Path codeLoc = Paths.get(WebAudioStreamer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path dir = Files.isDirectory(codeLoc) ? codeLoc : codeLoc.getParent();
            for (int i = 0; i < 3 && dir != null; i++) {
                Path test = dir.resolve("AudioLoopback.exe");
                if (Files.exists(test)) return test.toFile();
                dir = dir.getParent();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean runWasapiLoopback(File exeFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder(exeFile.getAbsolutePath());
            pb.redirectErrorStream(false);
            loopbackProcess = pb.start();

            // Đọc thông tin thiết bị từ Stderr
            BufferedReader errReader = new BufferedReader(new InputStreamReader(loopbackProcess.getErrorStream()));
            String line = errReader.readLine();
            int sampleRate = 48000;
            if (line != null && line.contains("RATE=")) {
                int idx = line.indexOf("RATE=");
                int end = line.indexOf('|', idx);
                if (end == -1) end = line.length();
                try {
                    sampleRate = Integer.parseInt(line.substring(idx + 5, end).trim());
                } catch (Exception ignored) {}
            }

            if (logger != null) {
                logger.appendLog("WebAudioStreamer: ĐÃ KÍCH HOẠT THU ÂM HỆ THỐNG TRỰC TIẾP (Windows WASAPI " + sampleRate + "Hz) - Âm thanh video/nhạc từ Loa không qua Micro!");
            }

            InputStream pcmStream = new BufferedInputStream(loopbackProcess.getInputStream(), 16384);
            // Gửi các gói ~40ms (ví dụ 48000 * 2 * 0.04 = 3840 bytes)
            int targetBytes = (int) (sampleRate * 2 * 0.04);
            if (targetBytes <= 0) targetBytes = 3840;
            byte[] pcmBuffer = new byte[targetBytes];

            int silentSkipCount = 0;
            int consecutiveErrors = 0;

            while (running && loopbackProcess.isAlive() && !Thread.currentThread().isInterrupted()) {
                if (!enabled) {
                    Thread.sleep(50);
                    continue;
                }

                int totalRead = 0;
                while (totalRead < pcmBuffer.length && running) {
                    int r = pcmStream.read(pcmBuffer, totalRead, pcmBuffer.length - totalRead);
                    if (r <= 0) break;
                    totalRead += r;
                }

                if (totalRead <= 0) {
                    Thread.sleep(5);
                    continue;
                }

                // Noise Gate: Kiểm tra độ lớn âm thanh để tiết kiệm băng thông khi không có nhạc
                boolean isSilent = true;
                for (int i = 0; i < totalRead - 1; i += 2) {
                    short sample = (short) ((pcmBuffer[i + 1] << 8) | (pcmBuffer[i] & 0xFF));
                    if (Math.abs(sample) > 30) {
                        isSilent = false;
                        break;
                    }
                }

                if (isSilent) {
                    silentSkipCount++;
                    if (silentSkipCount % 25 != 0) {
                        continue; // Bỏ qua gói im lặng để nhường băng thông cho màn hình
                    }
                } else {
                    silentSkipCount = 0;
                }

                // Đóng gói Frame nhị phân âm thanh (Header 6 bytes):
                // [magic=0xAA (1 byte), sampleRate (4 bytes), channels (1 byte), PCM bytes]
                ByteBuffer buffer = ByteBuffer.allocate(6 + totalRead);
                buffer.put((byte) 0xAA);
                buffer.putInt(sampleRate);
                buffer.put((byte) 1); // Mono
                buffer.put(pcmBuffer, 0, totalRead);

                try {
                    audioSender.send(buffer.array());
                    consecutiveErrors = 0;
                } catch (Exception sendErr) {
                    consecutiveErrors++;
                    if (consecutiveErrors > 15 || !running) {
                        break;
                    }
                    Thread.sleep(20);
                }
            }
            return true;
        } catch (Exception e) {
            if (logger != null) {
                logger.appendLog("WebAudioStreamer: Lỗi chạy WASAPI Loopback (" + e.getMessage() + "), chuyển sang Java AudioSystem.");
            }
            return false;
        } finally {
            if (loopbackProcess != null) {
                try { loopbackProcess.destroyForcibly(); } catch (Exception ignored) {}
                loopbackProcess = null;
            }
        }
    }

    private void runJavaSoundFallback() {
        AudioFormat format = new AudioFormat(DEFAULT_SAMPLE_RATE, SAMPLE_SIZE_IN_BITS, CHANNELS, SIGNED, BIG_ENDIAN);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        try {
            Mixer.Info selectedMixerInfo = null;
            Mixer.Info[] mixers = AudioSystem.getMixerInfo();
            for (Mixer.Info mInfo : mixers) {
                String name = mInfo.getName().toLowerCase();
                if (name.contains("stereo mix") || name.contains("what u hear") || name.contains("wave out")) {
                    try {
                        Mixer m = AudioSystem.getMixer(mInfo);
                        if (m.isLineSupported(info)) {
                            selectedMixerInfo = mInfo;
                            break;
                        }
                    } catch (Exception ignored) {}
                }
            }

            if (selectedMixerInfo != null) {
                audioLine = (TargetDataLine) AudioSystem.getMixer(selectedMixerInfo).getLine(info);
                if (logger != null) {
                    logger.appendLog("WebAudioStreamer: Kết nối nguồn âm thanh hệ thống Stereo Mix [" + selectedMixerInfo.getName() + "].");
                }
            } else if (AudioSystem.isLineSupported(info)) {
                audioLine = (TargetDataLine) AudioSystem.getLine(info);
                if (logger != null) {
                    logger.appendLog("WebAudioStreamer: Kết nối nguồn thu âm mặc định (Microphone).");
                }
            } else {
                if (logger != null) {
                    logger.appendLog("WebAudioStreamer: Không tìm thấy thiết bị thu âm hỗ trợ 44.1kHz.");
                }
                return;
            }

            audioLine.open(format, 4410 * 2);
            audioLine.start();

            byte[] pcmBuffer = new byte[3528];
            int silentSkipCount = 0;
            int consecutiveAudioErrors = 0;

            while (running && !Thread.currentThread().isInterrupted()) {
                if (!enabled) {
                    Thread.sleep(50);
                    continue;
                }

                int bytesRead = audioLine.read(pcmBuffer, 0, pcmBuffer.length);
                if (bytesRead <= 0) {
                    Thread.sleep(5);
                    continue;
                }

                boolean isSilent = true;
                int threshold = 50;
                for (int i = 0; i < bytesRead - 1; i += 2) {
                    short sample = (short) ((pcmBuffer[i + 1] << 8) | (pcmBuffer[i] & 0xFF));
                    if (Math.abs(sample) > threshold) {
                        isSilent = false;
                        break;
                    }
                }

                if (isSilent) {
                    silentSkipCount++;
                    if (silentSkipCount % 25 != 0) {
                        continue;
                    }
                } else {
                    silentSkipCount = 0;
                }

                ByteBuffer buffer = ByteBuffer.allocate(6 + bytesRead);
                buffer.put((byte) 0xAA);
                buffer.putInt((int) DEFAULT_SAMPLE_RATE);
                buffer.put((byte) CHANNELS);
                buffer.put(pcmBuffer, 0, bytesRead);

                try {
                    audioSender.send(buffer.array());
                    consecutiveAudioErrors = 0;
                } catch (Exception sendErr) {
                    consecutiveAudioErrors++;
                    if (consecutiveAudioErrors > 10 || !running) {
                        break;
                    }
                    Thread.sleep(30);
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (logger != null) {
                logger.appendLog("WebAudioStreamer lỗi: " + e.getMessage());
            }
        } finally {
            stop();
            if (logger != null) {
                logger.appendLog("WebAudioStreamer: Đã dừng luồng truyền âm thanh.");
            }
        }
    }
}
