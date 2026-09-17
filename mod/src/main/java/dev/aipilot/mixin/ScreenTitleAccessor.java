package dev.aipilot.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the GUI title. In 1.20.6 the field lives on Screen (class_437), not on
 * HandledScreen — targeting the declaring class is required for the accessor to bind.
 */
@Mixin(Screen.class)
public interface ScreenTitleAccessor {
    @Accessor("title")
    Text title();
}
