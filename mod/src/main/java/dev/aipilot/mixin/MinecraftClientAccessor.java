package dev.aipilot.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the client resource pack manager for structured state reporting. */
@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    @Accessor("resourcePackManager")
    ResourcePackManager resourcePackManager();
}
