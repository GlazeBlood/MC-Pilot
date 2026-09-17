package dev.aipilot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.aipilot.mixin.KeyboardInvoker;
import dev.aipilot.mixin.MinecraftClientAccessor;
import dev.aipilot.mixin.MouseInvoker;
import dev.aipilot.mixin.ScreenTitleAccessor;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.nio.file.Files;

/** All game-thread operations, one method per endpoint. */
public final class Actions {
    static boolean autoDisablePause = true;

    private Actions() {
    }

    static void autoOptions() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (autoDisablePause && mc.options != null) {
            // Keep the game running while the human works in other windows.
            mc.options.pauseOnLostFocus = false;
        }
    }

    static JsonObject ok(String message, JsonObject data) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        if (message != null) {
            o.addProperty("message", message);
        }
        if (data != null) {
            o.add("data", data);
        }
        return o;
    }

    static JsonObject err(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", false);
        o.addProperty("error", message);
        return o;
    }

    static String str(JsonObject o, String key, String def) {
        return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
    }

    static double num(JsonObject o, String key, double def) {
        return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsDouble() : def;
    }

    static boolean bool(JsonObject o, String key, boolean def) {
        return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsBoolean() : def;
    }

    // ---------------------------------------------------------------- health

    static JsonObject health() {
        MinecraftClient mc = MinecraftClient.getInstance();
        JsonObject d = new JsonObject();
        d.addProperty("mod", "aipilot");
        d.addProperty("mc", "1.20.6");
        d.addProperty("player", mc.player != null);
        d.addProperty("world", mc.world != null);
        d.addProperty("focused", mc.isWindowFocused());
        d.addProperty("screen", mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName());
        d.addProperty("window", mc.getWindow().getFramebufferWidth() + "x" + mc.getWindow().getFramebufferHeight());
        d.addProperty("pauseOnLostFocus", mc.options != null && mc.options.pauseOnLostFocus);
        return ok("alive", d);
    }

    // ------------------------------------------------------------ screenshot

    static byte[] screenshot() throws Exception {
        MinecraftClient mc = MinecraftClient.getInstance();
        Framebuffer fb = mc.getFramebuffer();
        NativeImage img = ScreenshotRecorder.takeScreenshot(fb);
        try {
            File tmp = File.createTempFile("aipilot-shot", ".png");
            try {
                img.writeTo(tmp);
                return Files.readAllBytes(tmp.toPath());
            } finally {
                tmp.delete();
            }
        } finally {
            img.close();
        }
    }

    // ----------------------------------------------------------------- state

    static JsonObject state() {
        MinecraftClient mc = MinecraftClient.getInstance();
        JsonObject s = new JsonObject();
        s.addProperty("mc", "1.20.6");
        s.addProperty("focused", mc.isWindowFocused());
        s.addProperty("screen", mc.currentScreen == null ? null : mc.currentScreen.getClass().getSimpleName());
        s.addProperty("windowWidth", mc.getWindow().getFramebufferWidth());
        s.addProperty("windowHeight", mc.getWindow().getFramebufferHeight());
        if (mc.world != null) {
            s.addProperty("dimension", mc.world.getRegistryKey().getValue().toString());
            s.addProperty("timeOfDay", mc.world.getTimeOfDay());
        }
        if (mc.getCurrentServerEntry() != null) {
            s.addProperty("server", mc.getCurrentServerEntry().address);
        } else {
            s.addProperty("server", "singleplayer/menu");
        }
        try {
            JsonArray enabled = new JsonArray();
            for (ResourcePackProfile p : ((MinecraftClientAccessor) mc).resourcePackManager().getEnabledProfiles()) {
                enabled.add(p.getId() + " | " + p.getDisplayName().getString());
            }
            s.add("resourcepacks", enabled);
        } catch (Exception ignored) {
            // resource pack info is best-effort
        }
        if (mc.currentScreen instanceof HandledScreen) {
            HandledScreen hs = (HandledScreen) mc.currentScreen;
            JsonObject gui = new JsonObject();
            gui.addProperty("title", ((ScreenTitleAccessor) hs).title().getString());
            ScreenHandler handler = hs.getScreenHandler();
            if (handler instanceof GenericContainerScreenHandler) {
                GenericContainerScreenHandler gc = (GenericContainerScreenHandler) handler;
                gui.addProperty("rows", gc.getRows());
                JsonArray slots = new JsonArray();
                Inventory inv = gc.getInventory();
                int cap = Math.min(gc.getRows() * 9, inv.size());
                for (int i = 0; i < cap; i++) {
                    ItemStack st = inv.getStack(i);
                    if (!st.isEmpty()) {
                        JsonObject it = itemFull(st);
                        it.addProperty("slot", i);
                        slots.add(it);
                    }
                }
                gui.add("slots", slots);
            }
            s.add("gui", gui);
        }
        if (mc.player != null) {
            ClientPlayerEntity p = mc.player;
            JsonObject pl = new JsonObject();
            pl.addProperty("x", p.getX());
            pl.addProperty("y", p.getY());
            pl.addProperty("z", p.getZ());
            pl.addProperty("yaw", p.getYaw());
            pl.addProperty("pitch", p.getPitch());
            pl.addProperty("health", p.getHealth());
            pl.addProperty("maxHealth", p.getMaxHealth());
            pl.addProperty("food", p.getHungerManager().getFoodLevel());
            pl.addProperty("air", p.getAir());
            pl.addProperty("xpLevel", p.experienceLevel);
            pl.addProperty("xpProgress", p.experienceProgress);
            pl.addProperty("gamemode", mc.interactionManager == null ? "unknown" : mc.interactionManager.getCurrentGameMode().getName());
            pl.addProperty("selectedSlot", p.getInventory().selectedSlot);
            JsonArray hotbar = new JsonArray();
            for (int i = 0; i < 9; i++) {
                hotbar.add(itemJson(p.getInventory().getStack(i)));
            }
            pl.add("hotbar", hotbar);
            JsonArray inv = new JsonArray();
            for (int i = 9; i < 36; i++) {
                ItemStack st = p.getInventory().getStack(i);
                if (!st.isEmpty()) {
                    JsonObject it = itemJson(st);
                    it.addProperty("slot", i);
                    inv.add(it);
                }
            }
            pl.add("inventory", inv);
            s.add("player", pl);
            try {
                s.addProperty("biome", mc.world.getBiome(p.getBlockPos()).getKey().map(k -> k.getValue().toString()).orElse("unknown"));
            } catch (Exception ignored) {
                // biome lookup is best-effort
            }
            HitResult t = mc.crosshairTarget;
            if (t != null) {
                JsonObject tg = new JsonObject();
                if (t instanceof BlockHitResult b && mc.world != null) {
                    BlockPos pos = b.getBlockPos();
                    tg.addProperty("type", "block");
                    tg.addProperty("pos", pos.getX() + " " + pos.getY() + " " + pos.getZ());
                    tg.addProperty("block", Registries.BLOCK.getId(mc.world.getBlockState(pos).getBlock()).toString());
                } else if (t instanceof EntityHitResult e) {
                    Entity en = e.getEntity();
                    tg.addProperty("type", "entity");
                    tg.addProperty("entity", Registries.ENTITY_TYPE.getId(en.getType()).toString());
                    tg.addProperty("name", en.getDisplayName().getString());
                    tg.addProperty("id", en.getId());
                } else {
                    tg.addProperty("type", t.getType().toString());
                }
                s.add("target", tg);
            }
        }
        return ok(null, s);
    }

    private static JsonObject itemJson(ItemStack st) {
        JsonObject it = new JsonObject();
        if (st.isEmpty()) {
            it.addProperty("id", "minecraft:air");
            it.addProperty("count", 0);
        } else {
            it.addProperty("id", Registries.ITEM.getId(st.getItem()).toString());
            it.addProperty("count", st.getCount());
        }
        return it;
    }

    /** Full item info for GUI verification: id, count, custom name, CustomModelData, lore. */
    private static JsonObject itemFull(ItemStack st) {
        JsonObject it = itemJson(st);
        Text name = st.get(DataComponentTypes.CUSTOM_NAME);
        if (name != null) {
            it.addProperty("name", name.getString());
        }
        CustomModelDataComponent cmd = st.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        if (cmd != null) {
            it.addProperty("cmd", cmd.value());
        }
        LoreComponent lore = st.get(DataComponentTypes.LORE);
        if (lore != null && !lore.lines().isEmpty()) {
            JsonArray arr = new JsonArray();
            for (Text line : lore.lines()) {
                arr.add(line.getString());
            }
            it.add("lore", arr);
        }
        return it;
    }

    // ------------------------------------------------------------------ look

    static JsonObject look(JsonObject req) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null) {
            return err("not in a world");
        }
        boolean rel = bool(req, "relative", false);
        double yaw = num(req, "yaw", Double.NaN);
        double pitch = num(req, "pitch", Double.NaN);
        if (!Double.isNaN(yaw)) {
            p.setYaw(rel ? p.getYaw() + (float) yaw : (float) yaw);
        }
        if (!Double.isNaN(pitch)) {
            float np = rel ? p.getPitch() + (float) pitch : (float) pitch;
            p.setPitch(Math.max(-90.0f, Math.min(90.0f, np)));
        }
        JsonObject d = new JsonObject();
        d.addProperty("yaw", p.getYaw());
        d.addProperty("pitch", p.getPitch());
        return ok(null, d);
    }

    // ------------------------------------------------------------------ move

    static JsonObject move(JsonObject req) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return err("not in a world");
        }
        String key = str(req, "key", "");
        boolean pressed = bool(req, "pressed", true);
        GameOptions o = mc.options;
        KeyBinding kb;
        switch (key) {
            case "forward": kb = o.forwardKey; break;
            case "back": kb = o.backKey; break;
            case "left": kb = o.leftKey; break;
            case "right": kb = o.rightKey; break;
            case "jump": kb = o.jumpKey; break;
            case "sneak": kb = o.sneakKey; break;
            case "sprint": kb = o.sprintKey; break;
            default: return err("unknown move key: " + key + " (forward|back|left|right|jump|sneak|sprint)");
        }
        kb.setPressed(pressed);
        return ok(key + (pressed ? " down" : " up"), null);
    }

    // ------------------------------------------------------------------- key

    static JsonObject key(JsonObject req) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Integer code = KeyNames.code(str(req, "key", ""));
        if (code == null) {
            return err("unknown key name (examples: W A S D E T F3 SPACE ENTER ESC SHIFT CTRL)");
        }
        String action = str(req, "action", "tap");
        long window = mc.getWindow().getHandle();
        boolean tap;
        int act;
        switch (action) {
            case "press": case "down": act = GLFW.GLFW_PRESS; tap = false; break;
            case "release": case "up": act = GLFW.GLFW_RELEASE; tap = false; break;
            case "tap": act = GLFW.GLFW_PRESS; tap = true; break;
            default: return err("action must be press|release|tap");
        }
        mc.keyboard.onKey(window, code, 0, act, 0);
        if (tap) {
            mc.keyboard.onKey(window, code, 0, GLFW.GLFW_RELEASE, 0);
        }
        return ok("key " + str(req, "key", "") + " " + action, null);
    }

    static JsonObject charInput(JsonObject req) {
        String text = str(req, "text", "");
        if (text.isEmpty()) {
            return err("text required (typed into the open text field, e.g. chat)");
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        long window = mc.getWindow().getHandle();
        text.codePoints().forEach(cp -> ((KeyboardInvoker) mc.keyboard).invokeOnChar(window, cp, 0));
        return ok("typed " + text.codePointCount(0, text.length()) + " chars", null);
    }

    // ----------------------------------------------------------------- mouse

    static JsonObject mouse(JsonObject req) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String button = str(req, "button", "left");
        int btn;
        switch (button) {
            case "left": case "0": btn = GLFW.GLFW_MOUSE_BUTTON_1; break;
            case "right": case "1": btn = GLFW.GLFW_MOUSE_BUTTON_2; break;
            case "middle": case "2": btn = GLFW.GLFW_MOUSE_BUTTON_3; break;
            default: return err("button must be left|right|middle");
        }
        String action = str(req, "action", "click");
        boolean tap;
        int act;
        switch (action) {
            case "press": case "down": act = GLFW.GLFW_PRESS; tap = false; break;
            case "release": case "up": act = GLFW.GLFW_RELEASE; tap = false; break;
            case "click": act = GLFW.GLFW_PRESS; tap = true; break;
            default: return err("action must be press|release|click");
        }
        if (!mc.isWindowFocused() && !VirtualPointer.isEnabled()) {
            return err("MC window is not focused; POST /focus first, or arm the virtual pointer with POST /virtual");
        }
        long window = mc.getWindow().getHandle();
        MouseInvoker mouse = (MouseInvoker) mc.mouse;
        mouse.invokeOnMouseButton(window, btn, act, 0);
        if (tap) {
            mouse.invokeOnMouseButton(window, btn, GLFW.GLFW_RELEASE, 0);
        }
        return ok(button + " " + action, null);
    }

    static JsonObject cursor(JsonObject req) {
        MinecraftClient mc = MinecraftClient.getInstance();
        double x = num(req, "x", Double.NaN);
        double y = num(req, "y", Double.NaN);
        if (Double.isNaN(x) || Double.isNaN(y)) {
            return err("x,y required (screenshot pixel coordinates, e.g. from /screenshot)");
        }
        long window = mc.getWindow().getHandle();
        VirtualPointer.move(x, y);
        ((MouseInvoker) mc.mouse).invokeOnCursorPos(window, x, y);
        return ok("cursor " + x + "," + y + (VirtualPointer.isEnabled() ? " (virtual)" : ""), null);
    }

    /** Cursor move plus click in one call, so nothing can move the pointer in between. */
    static JsonObject clickAt(JsonObject req) {
        MinecraftClient mc = MinecraftClient.getInstance();
        double x = num(req, "x", Double.NaN);
        double y = num(req, "y", Double.NaN);
        if (Double.isNaN(x) || Double.isNaN(y)) {
            return err("x,y required");
        }
        JsonObject moveResult = cursor(req);
        if (moveResult.has("ok") && !moveResult.get("ok").getAsBoolean()) {
            return moveResult;
        }
        JsonObject clickResult = mouse(req);
        if (clickResult.has("ok") && !clickResult.get("ok").getAsBoolean()) {
            return clickResult;
        }
        return ok("clicked at " + x + "," + y, null);
    }

    static JsonObject virtual(JsonObject req) {
        if (req.has("enabled")) {
            VirtualPointer.setEnabled(bool(req, "enabled", true));
        }
        if (req.has("guiOnly")) {
            VirtualPointer.setGuiOnly(bool(req, "guiOnly", true));
        }
        if (req.has("x") && req.has("y")) {
            VirtualPointer.move(num(req, "x", 0.0), num(req, "y", 0.0));
        }
        JsonObject d = new JsonObject();
        d.addProperty("enabled", VirtualPointer.isEnabled());
        d.addProperty("guiOnly", VirtualPointer.isGuiOnly());
        d.addProperty("x", VirtualPointer.x());
        d.addProperty("y", VirtualPointer.y());
        return ok("virtual pointer", d);
    }

    static JsonObject scroll(JsonObject req) {
        MinecraftClient mc = MinecraftClient.getInstance();
        double dy = num(req, "dy", 0.0);
        double dx = num(req, "dx", 0.0);
        if (dy == 0.0 && dx == 0.0) {
            return err("dy or dx required");
        }
        long window = mc.getWindow().getHandle();
        ((MouseInvoker) mc.mouse).invokeOnMouseScroll(window, dx, dy);
        return ok("scrolled", null);
    }

    // ------------------------------------------------------------------ chat

    static JsonObject chat(JsonObject req) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayNetworkHandler h = mc.getNetworkHandler();
        if (h == null) {
            return err("not connected to a server/world");
        }
        String msg = str(req, "message", "");
        if (msg.isEmpty()) {
            return err("message required");
        }
        if (msg.startsWith("/")) {
            h.sendChatCommand(msg.substring(1));
        } else {
            h.sendChatMessage(msg);
        }
        return ok("sent", null);
    }

    // ---------------------------------------------------------------- hotbar

    static JsonObject hotbar(JsonObject req) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return err("not in a world");
        }
        int slot = (int) num(req, "slot", -1);
        if (slot < 0 || slot > 8) {
            return err("slot must be 0..8");
        }
        mc.player.getInventory().selectedSlot = slot;
        return ok("hotbar slot " + slot, null);
    }

    // ------------------------------------------------------------------- gui

    static JsonObject gui(JsonObject req) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (bool(req, "close", false)) {
            mc.setScreen(null);
            return ok("screen closed", null);
        }
        String open = str(req, "open", "");
        switch (open) {
            case "inventory":
                if (mc.player == null) {
                    return err("not in a world");
                }
                mc.setScreen(new InventoryScreen(mc.player));
                return ok("inventory open", null);
            case "chat":
                mc.setScreen(new ChatScreen(str(req, "text", "")));
                return ok("chat open", null);
            default:
                return err("open must be inventory|chat, or pass close:true");
        }
    }

    // ----------------------------------------------------------------- focus

    static JsonObject focus() {
        MinecraftClient mc = MinecraftClient.getInstance();
        GLFW.glfwFocusWindow(mc.getWindow().getHandle());
        return ok("focus requested", null);
    }

    // ----------------------------------------------------------------- pause

    static JsonObject pause(JsonObject req) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean enabled = bool(req, "enabled", false);
        mc.options.pauseOnLostFocus = enabled;
        autoDisablePause = false;
        return ok("pauseOnLostFocus=" + enabled, null);
    }
}
