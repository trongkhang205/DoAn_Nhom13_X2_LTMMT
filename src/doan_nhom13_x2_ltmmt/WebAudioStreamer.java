package doan_nhom13_x2_ltmmt;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import javax.sound.sampled.*;

/**
 * Luồng truyền tải Âm Thanh Máy Tính Từ Xa (Remote Audio Streaming) - Đồ Án Nhóm 13 (X2).
 * - Bắt âm thanh hệ thống (PCM 44.1kHz, 16-bit, Mono) qua DirectSound Capture / Primary Sound Line.
 * - Đóng gói Frame nhị phân tối ưu (Header 6 bytes):
 *   [magic=0xAA (1 byte), sampleRate (4 bytes), channels (1 byte), rawPCM (N bytes)]
 * - Hỗ trợ bật/tắt (Mute/Unmute) động từ xa để tiết kiệm băng thông mạng.
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

    private static final float SAMPLE_RATE = 44100.0f;
    private static final int SAMPLE_SIZE_IN_BITS = 16;
    private static final int CHANNELS = 1; // Mono giúp giảm 50% băng thông mà vẫn rõ nét
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
        AudioFormat format = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE_IN_BITS, CHANNELS, SIGNED, BIG_ENDIAN);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        try {
            // 1. Quét tìm thiết bị thu âm phù hợp nhất (Ưu tiên Stereo Mix / What U Hear)
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
                    logger.appendLog("WebAudioStreamer: Đã kết nối nguồn âm thanh hệ thống [" + selectedMixerInfo.getName() + "].");
                }
            } else if (AudioSystem.isLineSupported(info)) {
                audioLine = (TargetDataLine) AudioSystem.getLine(info);
                if (logger != null) {
                    logger.appendLog("WebAudioStreamer: Đã kết nối nguồn thu âm mặc định (Primary Sound Capture).");
                }
            } else {
                if (logger != null) {
                    logger.appendLog("WebAudioStreamer: Không tìm thấy thiết bị thu âm hỗ trợ 44.1kHz.");
                }
                return;
            }

            audioLine.open(format, 4410 * 2); // Bộ đệm 100ms
            audioLine.start();

            // Đọc các gói 3528 bytes (~40ms âm thanh ở 44.1kHz 16-bit Mono)
            byte[] pcmBuffer = new byte[3528];
            int silentSkipCount = 0;
            int consecutiveAudioErrors = 0;

            while (running && !Thread.currentThread().isInterrupted()) {
                if (!enabled) {
                    // Khi tắt âm thanh, ngủ nhẹ để tiết kiệm CPU và không gửi dữ liệu
                    Thread.sleep(50);
                    continue;
                }

                int bytesRead = audioLine.read(pcmBuffer, 0, pcmBuffer.length);
                if (bytesRead <= 0) {
                    Thread.sleep(5);
                    continue;
                }

                // Phát hiện im lặng (Noise Gate): nếu không có âm thanh thực tế, bỏ qua để nhường 100% băng thông cho màn hình
                boolean isSilent = true;
                int threshold = 180;
                for (int i = 0; i < bytesRead - 1; i += 2) {
                    short sample = (short) ((pcmBuffer[i + 1] << 8) | (pcmBuffer[i] & 0xFF));
                    if (Math.abs(sample) > threshold) {
                        isSilent = false;
                        break;
                    }
                }

                if (isSilent) {
                    silentSkipCount++;
                    // Cứ mỗi 25 gói im lặng (~1 giây) gửi 1 gói giữ nhịp, các gói còn lại bỏ qua
                    if (silentSkipCount % 25 != 0) {
                        continue;
                    }
                } else {
                    silentSkipCount = 0;
                }

                // Đóng gói Frame nhị phân âm thanh (Header 6 bytes):
                // [magic=0xAA (1 byte), sampleRate (4 bytes), channels (1 byte), PCM bytes]
                ByteBuffer buffer = ByteBuffer.allocate(6 + bytesRead);
                buffer.put((byte) 0xAA); // Magic byte nhận diện Audio Frame
                buffer.putInt((int) SAMPLE_RATE);
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
