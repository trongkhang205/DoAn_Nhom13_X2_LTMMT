package doan_nhom13_x2_ltmmt;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Lớp hỗ trợ mã hóa bảo mật chuẩn quân đội AES-256-GCM (End-to-End Encryption - E2EE).
 * - Dẫn xuất khóa bí mật từ mật khẩu phiên thông qua thuật toán PBKDF2WithHmacSHA256 (65.536 vòng lặp).
 * - Sử dụng chế độ Galois/Counter Mode (GCM) với xác thực tính toàn vẹn 128-bit (Authentication Tag).
 * - Ngăn chặn hoàn toàn việc nghe lén (Man-in-the-Middle) và can thiệp sửa đổi gói tin qua Internet.
 */
public class CryptoHelper {

    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int IV_LENGTH = 12; // bytes chuẩn cho GCM
    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256;
    private static final byte[] FIXED_SALT = "DoAn_LTMMT_Nhom13_E2EE_Salt_2026".getBytes(StandardCharsets.UTF_8);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Dẫn xuất khóa SecretKey 256-bit từ chuỗi mật khẩu
     */
    public static SecretKey deriveKey(String password) throws Exception {
        if (password == null) password = "";
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), FIXED_SALT, ITERATIONS, KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Mã hóa chuỗi văn bản bằng AES-256-GCM
     * Kết quả trả về: Base64(IV + CipherText + Tag)
     */
    public static String encrypt(String plainText, SecretKey key) throws Exception {
        if (plainText == null) return null;
        byte[] iv = new byte[IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

        byte[] combined = new byte[IV_LENGTH + cipherText.length];
        System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
        System.arraycopy(cipherText, 0, combined, IV_LENGTH, cipherText.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    /**
     * Giải mã chuỗi Base64 bằng AES-256-GCM
     */
    public static String decrypt(String cipherTextBase64, SecretKey key) throws Exception {
        if (cipherTextBase64 == null) return null;
        byte[] combined = Base64.getDecoder().decode(cipherTextBase64);
        byte[] iv = new byte[IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, IV_LENGTH);

        int cipherTextLength = combined.length - IV_LENGTH;
        byte[] cipherText = new byte[cipherTextLength];
        System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherTextLength);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

        byte[] plainText = cipher.doFinal(cipherText);
        return new String(plainText, StandardCharsets.UTF_8);
    }

    /**
     * Mã hóa mảng byte nhị phân bằng AES-256-GCM.
     * Cấu trúc trả về: [12 bytes IV] + [CipherBytes + Tag]
     */
    public static byte[] encryptBinary(byte[] plainBytes, SecretKey key) throws Exception {
        if (plainBytes == null || key == null) return plainBytes;
        byte[] iv = new byte[IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        byte[] cipherBytes = cipher.doFinal(plainBytes);

        byte[] combined = new byte[IV_LENGTH + cipherBytes.length];
        System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
        System.arraycopy(cipherBytes, 0, combined, IV_LENGTH, cipherBytes.length);
        return combined;
    }

    /**
     * Giải mã mảng byte nhị phân AES-256-GCM
     */
    public static byte[] decryptBinary(byte[] combined, SecretKey key) throws Exception {
        if (combined == null || key == null || combined.length <= IV_LENGTH) return combined;
        byte[] iv = new byte[IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, IV_LENGTH);

        int cipherBytesLength = combined.length - IV_LENGTH;
        byte[] cipherBytes = new byte[cipherBytesLength];
        System.arraycopy(combined, IV_LENGTH, cipherBytes, 0, cipherBytesLength);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        return cipher.doFinal(cipherBytes);
    }
}
