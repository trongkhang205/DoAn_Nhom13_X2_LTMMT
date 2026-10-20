package doan_nhom13_x2_ltmmt;

import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * Ánh xạ mã phím từ W3C DOM KeyboardEvent (JavaScript trên trình duyệt) 
 * sang java.awt.event.KeyEvent VK_codes để Robot.keyPress() và Robot.keyRelease() 
 * thực thi chính xác trên hệ điều hành của Server.
 */
public class KeyMapper {

    private static final Map<String, Integer> CODE_TO_VK = new HashMap<>();

    static {
        // Phím chữ cái (A-Z)
        for (char c = 'A'; c <= 'Z'; c++) {
            CODE_TO_VK.put("Key" + c, KeyEvent.VK_A + (c - 'A'));
        }

        // Phím số hàng trên (0-9)
        for (int i = 0; i <= 9; i++) {
            CODE_TO_VK.put("Digit" + i, KeyEvent.VK_0 + i);
        }

        // Numpad (0-9)
        for (int i = 0; i <= 9; i++) {
            CODE_TO_VK.put("Numpad" + i, KeyEvent.VK_NUMPAD0 + i);
        }
        CODE_TO_VK.put("NumpadAdd", KeyEvent.VK_ADD);
        CODE_TO_VK.put("NumpadSubtract", KeyEvent.VK_SUBTRACT);
        CODE_TO_VK.put("NumpadMultiply", KeyEvent.VK_MULTIPLY);
        CODE_TO_VK.put("NumpadDivide", KeyEvent.VK_DIVIDE);
        CODE_TO_VK.put("NumpadDecimal", KeyEvent.VK_DECIMAL);
        CODE_TO_VK.put("NumpadEnter", KeyEvent.VK_ENTER);

        // Phím chức năng F1 - F12
        for (int i = 1; i <= 12; i++) {
            CODE_TO_VK.put("F" + i, KeyEvent.VK_F1 + (i - 1));
        }

        // Phím điều khiển thông dụng
        CODE_TO_VK.put("Space", KeyEvent.VK_SPACE);
        CODE_TO_VK.put("Enter", KeyEvent.VK_ENTER);
        CODE_TO_VK.put("Escape", KeyEvent.VK_ESCAPE);
        CODE_TO_VK.put("Backspace", KeyEvent.VK_BACK_SPACE);
        CODE_TO_VK.put("Tab", KeyEvent.VK_TAB);
        CODE_TO_VK.put("CapsLock", KeyEvent.VK_CAPS_LOCK);

        // Phím bổ trợ
        CODE_TO_VK.put("ShiftLeft", KeyEvent.VK_SHIFT);
        CODE_TO_VK.put("ShiftRight", KeyEvent.VK_SHIFT);
        CODE_TO_VK.put("ControlLeft", KeyEvent.VK_CONTROL);
        CODE_TO_VK.put("ControlRight", KeyEvent.VK_CONTROL);
        CODE_TO_VK.put("AltLeft", KeyEvent.VK_ALT);
        CODE_TO_VK.put("AltRight", KeyEvent.VK_ALT);
        CODE_TO_VK.put("MetaLeft", KeyEvent.VK_WINDOWS);
        CODE_TO_VK.put("MetaRight", KeyEvent.VK_WINDOWS);
        CODE_TO_VK.put("ContextMenu", KeyEvent.VK_CONTEXT_MENU);

        // Phím điều hướng
        CODE_TO_VK.put("ArrowUp", KeyEvent.VK_UP);
        CODE_TO_VK.put("ArrowDown", KeyEvent.VK_DOWN);
        CODE_TO_VK.put("ArrowLeft", KeyEvent.VK_LEFT);
        CODE_TO_VK.put("ArrowRight", KeyEvent.VK_RIGHT);
        CODE_TO_VK.put("Insert", KeyEvent.VK_INSERT);
        CODE_TO_VK.put("Delete", KeyEvent.VK_DELETE);
        CODE_TO_VK.put("Home", KeyEvent.VK_HOME);
        CODE_TO_VK.put("End", KeyEvent.VK_END);
        CODE_TO_VK.put("PageUp", KeyEvent.VK_PAGE_UP);
        CODE_TO_VK.put("PageDown", KeyEvent.VK_PAGE_DOWN);

        // Ký tự đặc biệt & Dấu câu
        CODE_TO_VK.put("Backquote", KeyEvent.VK_BACK_QUOTE);
        CODE_TO_VK.put("Minus", KeyEvent.VK_MINUS);
        CODE_TO_VK.put("Equal", KeyEvent.VK_EQUALS);
        CODE_TO_VK.put("BracketLeft", KeyEvent.VK_OPEN_BRACKET);
        CODE_TO_VK.put("BracketRight", KeyEvent.VK_CLOSE_BRACKET);
        CODE_TO_VK.put("Backslash", KeyEvent.VK_BACK_SLASH);
        CODE_TO_VK.put("Semicolon", KeyEvent.VK_SEMICOLON);
        CODE_TO_VK.put("Quote", KeyEvent.VK_QUOTE);
        CODE_TO_VK.put("Comma", KeyEvent.VK_COMMA);
        CODE_TO_VK.put("Period", KeyEvent.VK_PERIOD);
        CODE_TO_VK.put("Slash", KeyEvent.VK_SLASH);
    }

    /**
     * Chuyển đổi mã phím từ Web JS sang Java KeyEvent VK code
     * @param code Mã event.code từ JavaScript (ví dụ: "KeyA", "Enter", "Space")
     * @param fallbackKeyCode Mã event.keyCode truyền thống từ JS nếu không tìm thấy code
     * @return Mã phím Java VK hoặc KeyEvent.VK_UNDEFINED nếu không tìm thấy
     */
    public static int getVkCode(String code, int fallbackKeyCode) {
        if (code != null && CODE_TO_VK.containsKey(code)) {
            return CODE_TO_VK.get(code);
        }
        if (fallbackKeyCode > 0) {
            return fallbackKeyCode;
        }
        return KeyEvent.VK_UNDEFINED;
    }
}
