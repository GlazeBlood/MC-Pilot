package dev.aipilot;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Minimal HTTP server bound to 127.0.0.1 that exposes the game to a local AI agent.
 *
 * Endpoints:
 *   GET  /health                 liveness + focus/screen info
 *   GET  /state                  full structured game state (JSON)
 *   GET  /screenshot             PNG of the current framebuffer (includes HUD/GUI)
 *   POST /look    {yaw, pitch, relative}
 *   POST /move    {key: forward|back|left|right|jump|sneak|sprint, pressed}
 *   POST /key     {key: "W"|"E"|"F3"|..., action: press|release|tap}
 *   POST /char    {text}          type characters into an open text field
 *   POST /mouse   {button: left|right|middle, action: press|release|click}
 *   POST /cursor  {x, y}          move the cursor (screenshot pixel coords; for GUIs)
 *   POST /scroll  {dy, dx}
 *   POST /chat    {message}       send chat ("/cmd" runs a command)
 *   POST /hotbar  {slot: 0..8}
 *   POST /gui     {open: inventory|chat, text?, close?}
 *   POST /focus                   bring the MC window to the foreground
 *   POST /pause   {enabled}       toggle pause-on-lost-focus (off by default)
 */
public final class PilotServer {
    private static volatile String token = "";

    private PilotServer() {
    }

    public static void start(int port, String tokenArg) throws IOException {
        token = tokenArg == null ? "" : tokenArg;
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", PilotServer::handle);
        server.setExecutor(Executors.newFixedThreadPool(3));
        server.start();
        AiPilotClient.LOGGER.info("[aipilot] HTTP bridge listening on http://127.0.0.1:{} ({})",
                port, token.isEmpty() ? "no token; localhost only" : "token required");
    }

    private static void handle(HttpExchange ex) throws IOException {
        long t0 = System.nanoTime();
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        int status = 200;
        byte[] body;
        String ctype = "application/json; charset=utf-8";
        try {
            if (!token.isEmpty() && !token.equals(ex.getRequestHeaders().getFirst("X-Ai-Pilot-Token"))) {
                status = 401;
                body = jsonBytes(err("bad or missing token"));
            } else if ("GET".equals(method)) {
                switch (path) {
                    case "/": status = 200; ctype = "text/html; charset=utf-8"; body = INDEX_HTML; break;
                    case "/health": body = jsonBytes(onClient(Actions::health)); break;
                    case "/state": body = jsonBytes(onClient(Actions::state)); break;
                    case "/screenshot": ctype = "image/png"; body = onClient(Actions::screenshot); break;
                    default: status = 404; body = jsonBytes(err("no such endpoint: GET " + path));
                }
            } else if ("POST".equals(method)) {
                JsonObject req = readJson(ex);
                if (req == null) {
                    status = 400;
                    body = jsonBytes(err("invalid JSON body"));
                } else {
                    switch (path) {
                        case "/look": body = jsonBytes(jsonOp(() -> Actions.look(req))); break;
                        case "/move": body = jsonBytes(jsonOp(() -> Actions.move(req))); break;
                        case "/key": body = jsonBytes(jsonOp(() -> Actions.key(req))); break;
                        case "/char": body = jsonBytes(jsonOp(() -> Actions.charInput(req))); break;
                        case "/mouse": body = jsonBytes(jsonOp(() -> Actions.mouse(req))); break;
                        case "/cursor": body = jsonBytes(jsonOp(() -> Actions.cursor(req))); break;
                        case "/clickAt": body = jsonBytes(jsonOp(() -> Actions.clickAt(req))); break;
                        case "/virtual": body = jsonBytes(jsonOp(() -> Actions.virtual(req))); break;
                        case "/scroll": body = jsonBytes(jsonOp(() -> Actions.scroll(req))); break;
                        case "/chat": body = jsonBytes(jsonOp(() -> Actions.chat(req))); break;
                        case "/hotbar": body = jsonBytes(jsonOp(() -> Actions.hotbar(req))); break;
                        case "/gui": body = jsonBytes(jsonOp(() -> Actions.gui(req))); break;
                        case "/focus": body = jsonBytes(jsonOp(Actions::focus)); break;
                        case "/pause": body = jsonBytes(jsonOp(() -> Actions.pause(req))); break;
                        default: status = 404; body = jsonBytes(err("no such endpoint: POST " + path));
                    }
                }
            } else {
                status = 405;
                body = jsonBytes(err("method not allowed"));
            }
        } catch (Throwable t) {
            status = 500;
            body = jsonBytes(err(String.valueOf(t.getCause() != null ? t.getCause() : t)));
        }
        ex.getResponseHeaders().set("Content-Type", ctype);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        if (!"/screenshot".equals(path)) {
            AiPilotClient.LOGGER.info("{} {} -> {} ({} ms)", method, path, status, ms);
        }
    }

    /** Run a game-thread task; throws on timeout or task failure (outer catch turns it into a 500). */
    private static <T> T onClient(Callable<T> task) throws Exception {
        MinecraftClient mc = MinecraftClient.getInstance();
        CompletableFuture<T> f = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                Actions.autoOptions();
                f.complete(task.call());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f.get(15, TimeUnit.SECONDS);
    }

    /** JSON endpoint variant that reports failures inside the JSON body instead of a 500. */
    private static JsonObject jsonOp(Callable<JsonObject> task) {
        try {
            return onClient(task);
        } catch (Throwable t) {
            Throwable c = t.getCause() != null ? t.getCause() : t;
            return err(String.valueOf(c));
        }
    }

    private static JsonObject readJson(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            byte[] raw = is.readNBytes(1 << 20);
            if (raw.length == 0) {
                return new JsonObject();
            }
            return JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] jsonBytes(JsonObject o) {
        return o.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static JsonObject err(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", false);
        o.addProperty("error", message);
        return o;
    }

    private static final byte[] INDEX_HTML = ("<html><body><h3>AI Pilot bridge</h3>"
            + "<pre>GET  /health\nGET  /state\nGET  /screenshot\n"
            + "POST /look /move /key /char /mouse /cursor /scroll /chat /hotbar /gui /focus /pause</pre>"
            + "</body></html>").getBytes(StandardCharsets.UTF_8);
}
