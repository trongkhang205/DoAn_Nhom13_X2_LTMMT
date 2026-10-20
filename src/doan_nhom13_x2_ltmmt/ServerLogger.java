package doan_nhom13_x2_ltmmt;

/**
 * Interface cho phép ghi log từ các luồng mạng (ScreenCaster, EventHandler)
 * về giao diện ServerDashboard hoặc PlayViewConnect
 */
public interface ServerLogger {
    void appendLog(String message);
}
