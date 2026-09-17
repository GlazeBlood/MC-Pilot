package dev.aipilot;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AiPilotClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("aipilot");

    @Override
    public void onInitializeClient() {
        int port = Integer.getInteger("aipilot.port", 8765);
        String token = System.getProperty("aipilot.token", "");
        try {
            PilotServer.start(port, token);
        } catch (Throwable t) {
            LOGGER.error("[aipilot] failed to start HTTP bridge on port {}: {}", port, t.toString());
        }
    }
}
