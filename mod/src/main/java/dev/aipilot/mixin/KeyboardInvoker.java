package dev.aipilot.mixin;

import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the private char-typed handler of Keyboard so the bridge can type
 * into GUI text fields (chat, signs, anvil...) exactly like real typing.
 */
@Mixin(Keyboard.class)
public interface KeyboardInvoker {
    @Invoker("onChar")
    void invokeOnChar(long window, int codePoint, int modifiers);
}
