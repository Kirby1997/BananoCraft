package banano.bananominecraft.bananoeconomy.io;

import banano.bananominecraft.bananoeconomy.classes.MessageGenerator;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.i18n.I18n;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages a persistent WebSocket connection to the Banano network and fires
 * in-game notifications whenever a player's wallet receives a confirmed deposit.
 *
 * <p>This class is a <em>wrapper</em> around a {@link WebSocketClient} rather
 * than a subclass.  The inner client can therefore be swapped at runtime when
 * the target URL is changed via an admin command, without any other part of
 * the plugin needing to update its reference to this object.</p>
 *
 * <p>Lifecycle: call {@link #connect()} from {@code onEnable} and
 * {@link #shutdown()} from {@code onDisable}.</p>
 */
public class BananoWebSocket
{
    private static final int INITIAL_RECONNECT_TICKS = 100;  // 5 s
    private static final int MAX_RECONNECT_TICKS     = 1200; // 60 s

    private final Plugin plugin;
    private final RPC rpc;
    private final ConfigEngine configEngine;
    private final MessageGenerator messageGenerator;

    /**
     * wallet address → player UUID for every account currently being watched.
     * Lives in the wrapper so it survives inner-client swaps.
     */
    private final ConcurrentHashMap<String, UUID> watchedAccounts = new ConcurrentHashMap<>();

    /** Set to true only by {@link #shutdown()} to suppress the reconnect loop. */
    private volatile boolean intentionallyClosed = false;

    private int reconnectDelayTicks = INITIAL_RECONNECT_TICKS;

    /**
     * The live inner client.  May be replaced by {@link #swapClient(URI)}.
     * All callbacks check {@code this == BananoWebSocket.this.client} and
     * return early if they belong to a stale (swapped-out) client.
     */
    private volatile WebSocketClient client;

    public BananoWebSocket(Plugin plugin, RPC rpc, ConfigEngine configEngine, MessageGenerator messageGenerator)
    {
        this.plugin = plugin;
        this.rpc = rpc;
        this.configEngine = configEngine;
        this.messageGenerator = messageGenerator;
        this.client = buildClient(URI.create(configEngine.getWebsocketUrl()));
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** Connect to the configured WebSocket URL. */
    public void connect()
    {
        intentionallyClosed = false;
        client.connect();
    }

    /**
     * Gracefully close the connection and suppress the automatic reconnect
     * loop.  Call this from {@code onDisable}.
     */
    public void shutdown()
    {
        intentionallyClosed = true;

        if (!client.isClosed())
        {
            client.close();
        }
    }

    // -------------------------------------------------------------------------
    // Admin operations
    // -------------------------------------------------------------------------

    /**
     * Drop the current connection and reconnect to the same URL.
     * Called by the {@code /be websocket reconnect} command.
     */
    public void reconnect()
    {
        plugin.getLogger().info("[WebSocket] Admin-initiated reconnect.");
        swapClient(URI.create(configEngine.getWebsocketUrl()));
    }

    /**
     * Update the WebSocket URL, persist it to config, then reconnect.
     * Called by the {@code /be websocket set [url]} command.
     *
     * @param newUrl the new {@code wss://} or {@code ws://} URL
     */
    public void setUrl(String newUrl)
    {
        configEngine.setWebsocketUrl(newUrl);
        configEngine.save();
        plugin.getLogger().info("[WebSocket] URL updated to " + newUrl + " — reconnecting.");
        swapClient(URI.create(newUrl));
    }

    /** Returns the URL the inner client is currently targeting. */
    public String getCurrentUrl()
    {
        return client.getURI().toString();
    }

    // -------------------------------------------------------------------------
    // Account watch management
    // -------------------------------------------------------------------------

    /**
     * Begin watching {@code wallet} for inbound deposit confirmations.
     * Should be called after a player's wallet has been successfully set up.
     */
    public void watchAccount(String wallet, UUID playerUUID)
    {
        if (wallet == null || wallet.isEmpty())
        {
            return;
        }

        watchedAccounts.put(wallet, playerUUID);

        if (client.isOpen())
        {
            JsonArray add = new JsonArray();
            add.add(wallet);
            JsonObject options = new JsonObject();
            options.add("accounts_add", add);
            client.send(buildUpdate(options));
        }
    }

    /**
     * Stop watching {@code wallet}.  Typically called when the player leaves.
     */
    public void unwatchAccount(String wallet)
    {
        if (wallet == null || wallet.isEmpty())
        {
            return;
        }

        watchedAccounts.remove(wallet);

        if (client.isOpen())
        {
            JsonArray del = new JsonArray();
            del.add(wallet);
            JsonObject options = new JsonObject();
            options.add("accounts_del", del);
            client.send(buildUpdate(options));
        }
    }

    // -------------------------------------------------------------------------
    // Internal — client construction and swapping
    // -------------------------------------------------------------------------

    /**
     * Close the current inner client (if open) and replace it with a fresh one
     * pointing at {@code newUri}, then connect immediately.
     *
     * <p>Old-client callbacks detect the swap via the identity check
     * {@code this == BananoWebSocket.this.client} and become no-ops, so there
     * is no risk of the old client triggering a second reconnect loop.</p>
     */
    private void swapClient(URI newUri)
    {
        intentionallyClosed = false;
        reconnectDelayTicks = INITIAL_RECONNECT_TICKS;

        WebSocketClient old = this.client;
        this.client = buildClient(newUri);

        // Close old after swapping so its onClose fires but is silently ignored.
        if (old != null && !old.isClosed())
        {
            old.close();
        }

        this.client.connect();
    }

    /**
     * Build a fresh inner {@link WebSocketClient} whose callbacks delegate
     * back to this wrapper.  The identity check at the top of each callback
     * ensures stale clients (swapped out by {@link #swapClient}) are ignored.
     */
    private WebSocketClient buildClient(URI uri)
    {
        return new WebSocketClient(uri)
        {
            @Override
            public void onOpen(ServerHandshake handshake)
            {
                if (this != BananoWebSocket.this.client)
                {
                    return;
                }

                plugin.getLogger().info("[WebSocket] Connected to " + uri);
                reconnectDelayTicks = INITIAL_RECONNECT_TICKS;
                sendSubscription(this);
            }

            @Override
            public void onMessage(String rawMessage)
            {
                if (this != BananoWebSocket.this.client)
                {
                    return;
                }
                handleMessage(rawMessage);
            }

            @Override
            public void onClose(int code, String reason, boolean remote)
            {
                if (this != BananoWebSocket.this.client)
                {
                    return;
                }

                plugin.getLogger().warning(String.format(
                        "[WebSocket] Connection closed (code=%d, reason='%s', remote=%b)",
                        code, reason, remote));

                if (!intentionallyClosed)
                {
                    scheduleReconnect();
                }
            }

            @Override
            public void onError(Exception ex)
            {
                if (this != BananoWebSocket.this.client)
                {
                    return;
                }
                plugin.getLogger().log(Level.WARNING, "[WebSocket] Connection error.", ex);
            }
        };
    }

    // -------------------------------------------------------------------------
    // Internal — message handling, subscription, reconnect
    // -------------------------------------------------------------------------

    private void handleMessage(String rawMessage)
    {
        try
        {
            JsonObject root = JsonParser.parseString(rawMessage).getAsJsonObject();

            if (root.has("ack"))
            {
                return;
            }

            JsonElement topicEl = root.get("topic");
            if (topicEl == null || !"confirmation".equals(topicEl.getAsString()))
            {
                return;
            }

            JsonObject message = root.getAsJsonObject("message");
            if (message == null)
            {
                return;
            }

            JsonObject block = message.getAsJsonObject("block");
            if (block == null)
            {
                return;
            }

            // "receive" blocks are created by the node when it pockets an
            // incoming send into the player's wallet — that is the deposit event.
            JsonElement subtypeEl = block.get("subtype");
            if (subtypeEl == null || !"receive".equals(subtypeEl.getAsString()))
            {
                return;
            }

            String account = message.get("account").getAsString();
            UUID playerUUID = watchedAccounts.get(account);
            if (playerUUID == null)
            {
                return;
            }

            double amount    = rpc.fromRaw(new BigDecimal(message.get("amount").getAsString()));
            String blockHash = message.get("hash").getAsString();

            notifyPlayer(playerUUID, amount, blockHash);
        }
        catch (Exception e)
        {
            plugin.getLogger().log(Level.WARNING, "[WebSocket] Failed to process confirmation message.", e);
        }
    }

    /**
     * Send the initial confirmation subscription, re-including all currently
     * watched accounts so they are restored after a reconnect.
     */
    private void sendSubscription(WebSocketClient c)
    {
        JsonObject options = new JsonObject();
        options.addProperty("include_block", "true");

        if (!watchedAccounts.isEmpty())
        {
            JsonArray accounts = new JsonArray();
            watchedAccounts.keySet().forEach(accounts::add);
            options.add("accounts", accounts);
        }

        JsonObject subscribe = new JsonObject();
        subscribe.addProperty("action", "subscribe");
        subscribe.addProperty("topic", "confirmation");
        subscribe.add("options", options);

        c.send(subscribe.toString());
    }

    private String buildUpdate(JsonObject options)
    {
        JsonObject update = new JsonObject();
        update.addProperty("action", "update");
        update.addProperty("topic", "confirmation");
        update.add("options", options);

        return update.toString();
    }

    /** Schedule a reconnect with exponential back-off via the Bukkit scheduler. */
    private void scheduleReconnect()
    {
        int delay = reconnectDelayTicks;
        reconnectDelayTicks = Math.min(reconnectDelayTicks * 2, MAX_RECONNECT_TICKS);

        plugin.getLogger().info(String.format(
                "[WebSocket] Reconnecting in %d s...", delay / 20));

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () ->
        {
            if (intentionallyClosed || !plugin.isEnabled())
            {
                return;
            }
            swapClient(URI.create(configEngine.getWebsocketUrl()));
        }, delay);
    }

    /** Deliver the deposit notification on the main server thread. */
    private void notifyPlayer(UUID playerUUID, double amount, String blockHash)
    {
        Bukkit.getScheduler().runTask(plugin, () ->
        {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player == null || !player.isOnline())
            {
                return;
            }

            player.spigot().sendMessage(
                    new ComponentBuilder("Deposit confirmed: ")
                            .color(ChatColor.GOLD)
                            .append(String.format("%.4f BAN", amount))
                            .color(ChatColor.WHITE).bold(true)
                            .append(" received!")
                            .color(ChatColor.GOLD).bold(false)
                            .create()
            );

            player.spigot().sendMessage(
                    messageGenerator.generateBlockExplorerLink(
                            I18n.parseMinecraftLocale(player.getLocale()), blockHash)
            );
        });
    }
}
