package dev.aipilot.mixin;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the private GLFW-event handlers of Mouse so the bridge can inject
 * synthetic input exactly like real callbacks do.
 */
@Mixin(Mouse.class)
public interface MouseInvoker {
    @Invoker("onMouseButton")
    void invokeOnMouseButton(long window, int button, int action, int mods);

    @Invoker("onCursorPos")
    void invokeOnCursorPos(long window, double x, double y);

    @Invoker("onMouseScroll")
    void invokeOnMouseScroll(long window, double horizontal, double vertical);
}
