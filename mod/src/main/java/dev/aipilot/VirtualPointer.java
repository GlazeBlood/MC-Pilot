package dev.aipilot;

import net.minecraft.client.MinecraftClient;

/**
 * A virtual GUI pointer.
 *
 * <p>Minecraft keeps exactly one cursor position inside {@code Mouse}. Injecting a position there
 * (which is what the bridge has always done) does not move the OS cursor, but the human's real
 * mouse motion keeps overwriting it, so the two fight over the same value.
 *
 * <p>When this pointer is armed, {@code MouseVirtualMixin} rewrites every incoming real cursor
 * position to the stored virtual one, so the assistant's position survives the human moving their
 * mouse. By default it only applies while a screen is open, which leaves in-world camera look
 * (which is driven by the same accumulated motion) completely untouched.
 */
public final class VirtualPointer {
    private static volatile boolean enabled = false;
    private static volatile boolean guiOnly = true;
    private static volatile double x = 0.0D;
    private static volatile double y = 0.0D;

    private VirtualPointer() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isGuiOnly() {
        return guiOnly;
    }

    public static void setGuiOnly(boolean value) {
        guiOnly = value;
    }

    public static void move(double newX, double newY) {
        x = newX;
        y = newY;
    }

    public static double x() {
        return x;
    }

    public static double y() {
        return y;
    }

    /** True when real cursor events must be replaced by the virtual position. */
    public static boolean shouldOverride() {
        if (!enabled) {
            return false;
        }
        if (!guiOnly) {
            return true;
        }
        return MinecraftClient.getInstance().currentScreen != null;
    }
}
