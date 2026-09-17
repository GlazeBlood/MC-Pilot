package dev.aipilot;

import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Maps friendly key names to GLFW key codes. */
public final class KeyNames {
    private static final Map<String, Integer> KEYS = new HashMap<>();

    static {
        for (int i = 0; i < 26; i++) {
            KEYS.put("" + (char) ('A' + i), GLFW.GLFW_KEY_A + i);
        }
        for (int i = 0; i < 10; i++) {
            KEYS.put("DIGIT_" + i, GLFW.GLFW_KEY_0 + i);
            KEYS.put("" + i, GLFW.GLFW_KEY_0 + i);
        }
        for (int i = 1; i <= 12; i++) {
            KEYS.put("F" + i, GLFW.GLFW_KEY_F1 + (i - 1));
        }
        put("SPACE", GLFW.GLFW_KEY_SPACE);
        put("ENTER", GLFW.GLFW_KEY_ENTER);
        put("RETURN", GLFW.GLFW_KEY_ENTER);
        put("ESCAPE", GLFW.GLFW_KEY_ESCAPE);
        put("ESC", GLFW.GLFW_KEY_ESCAPE);
        put("TAB", GLFW.GLFW_KEY_TAB);
        put("BACKSPACE", GLFW.GLFW_KEY_BACKSPACE);
        put("SHIFT", GLFW.GLFW_KEY_LEFT_SHIFT);
        put("LEFT_SHIFT", GLFW.GLFW_KEY_LEFT_SHIFT);
        put("RIGHT_SHIFT", GLFW.GLFW_KEY_RIGHT_SHIFT);
        put("CTRL", GLFW.GLFW_KEY_LEFT_CONTROL);
        put("LEFT_CONTROL", GLFW.GLFW_KEY_LEFT_CONTROL);
        put("RIGHT_CONTROL", GLFW.GLFW_KEY_RIGHT_CONTROL);
        put("ALT", GLFW.GLFW_KEY_LEFT_ALT);
        put("LEFT_ALT", GLFW.GLFW_KEY_LEFT_ALT);
        put("RIGHT_ALT", GLFW.GLFW_KEY_RIGHT_ALT);
        put("UP", GLFW.GLFW_KEY_UP);
        put("DOWN", GLFW.GLFW_KEY_DOWN);
        put("LEFT", GLFW.GLFW_KEY_LEFT);
        put("RIGHT", GLFW.GLFW_KEY_RIGHT);
        put("DELETE", GLFW.GLFW_KEY_DELETE);
        put("DEL", GLFW.GLFW_KEY_DELETE);
        put("HOME", GLFW.GLFW_KEY_HOME);
        put("END", GLFW.GLFW_KEY_END);
        put("PAGE_UP", GLFW.GLFW_KEY_PAGE_UP);
        put("PAGE_DOWN", GLFW.GLFW_KEY_PAGE_DOWN);
        put("INSERT", GLFW.GLFW_KEY_INSERT);
        put("CAPS_LOCK", GLFW.GLFW_KEY_CAPS_LOCK);
        put("MINUS", GLFW.GLFW_KEY_MINUS);
        put("EQUAL", GLFW.GLFW_KEY_EQUAL);
        put("LEFT_BRACKET", GLFW.GLFW_KEY_LEFT_BRACKET);
        put("RIGHT_BRACKET", GLFW.GLFW_KEY_RIGHT_BRACKET);
        put("SEMICOLON", GLFW.GLFW_KEY_SEMICOLON);
        put("APOSTROPHE", GLFW.GLFW_KEY_APOSTROPHE);
        put("GRAVE", GLFW.GLFW_KEY_GRAVE_ACCENT);
        put("BACKSLASH", GLFW.GLFW_KEY_BACKSLASH);
        put("COMMA", GLFW.GLFW_KEY_COMMA);
        put("PERIOD", GLFW.GLFW_KEY_PERIOD);
        put("SLASH", GLFW.GLFW_KEY_SLASH);
        put("PRINT_SCREEN", GLFW.GLFW_KEY_PRINT_SCREEN);
        for (int i = 0; i < 10; i++) {
            KEYS.put("KP_" + i, GLFW.GLFW_KEY_KP_0 + i);
        }
    }

    private KeyNames() {
    }

    private static void put(String name, int code) {
        KEYS.put(name, code);
    }

    /** @return the GLFW key code, or null if the name is unknown. */
    static Integer code(String name) {
        if (name == null) {
            return null;
        }
        return KEYS.get(name.trim().toUpperCase(Locale.ROOT));
    }
}
