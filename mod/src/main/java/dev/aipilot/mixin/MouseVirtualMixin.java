package dev.aipilot.mixin;

import dev.aipilot.VirtualPointer;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Replaces incoming real cursor coordinates with the virtual pointer while it is armed, so the
 * human can keep using their own mouse without displacing the assistant's GUI position.
 */
@Mixin(Mouse.class)
public class MouseVirtualMixin {

    @ModifyVariable(method = "onCursorPos", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double aipilot$virtualCursorX(double value) {
        return VirtualPointer.shouldOverride() ? VirtualPointer.x() : value;
    }

    @ModifyVariable(method = "onCursorPos", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double aipilot$virtualCursorY(double value) {
        return VirtualPointer.shouldOverride() ? VirtualPointer.y() : value;
    }
}
