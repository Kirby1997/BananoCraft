package banano.bananominecraft.bananoeconomy.io;

import banano.bananominecraft.bananoeconomy.validation.Validator;
import banano.bananominecraft.bananoeconomy.classes.TransactionRecord;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.enums.TransactionDirection;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import banano.bananominecraft.bananoeconomy.wallet.NodeWalletSigner;
import banano.bananominecraft.bananoeconomy.wallet.WalletSigner;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Level;

/**
 * Communicates with the Banano node over HTTP/JSON-RPC.
 *
 * <p>Read-only queries (balance, history, receivable, representatives, block count) go straight to
 * the node. The wallet actions that sign blocks (seed import, account creation, send, receive,
 * representative change) are delegated to the injected {@link WalletSigner}: the node's own wallet
 * ({@code walletMode: node}) or local signing ({@code walletMode: light}). Public method signatures
 * do not depend on the mode.</p>
 *
 * <p>All configuration values (node URL, wallet ID, master wallet, multiplier)
 * are read from {@link ConfigEngine} rather than directly from the Bukkit config
 * file, so that admin changes made at runtime are always reflected immediately
 * without requiring a plugin reload.</p>
 */
public class RPC
{
    /**
     * Sentinel returned by {@link #accountCreate} when the node call fails.
     * Callers should compare against this constant rather than a bare string literal.
     */
    public static final String ACCOUNT_CREATION_FAILED = "Account Creation Failed";

    /**
     * Result of asking the node whether a wallet exists.
     *
     * <p>{@link #MISSING} is only reported when the node explicitly says the wallet is unknown;
     * any transport, parsing or unexpected node error is {@link #UNREACHABLE}, so callers never
     * mistake a flaky node for a missing wallet (and create a new one) or for a healthy one.</p>
     */
    public enum WalletStatus
    {
        EXISTS,
        MISSING,
        UNREACHABLE
    }

    private final Plugin plugin;
    private final ConfigEngine configEngine;
    private final HttpTransport http;
    private final WalletSigner signer;

    public RPC(Plugin plugin, ConfigEngine configEngine)
    {
        this(plugin, configEngine, new HttpUrlConnectionTransport());
    }

    /**
     * Transport-injecting constructor — pass a mock {@link HttpTransport} in tests to
     * exercise the JSON-RPC logic without touching the network. Wallet actions use the node's
     * wallet ({@link NodeWalletSigner}) over the same transport.
     */
    public RPC(Plugin plugin, ConfigEngine configEngine, HttpTransport http)
    {
        this(plugin, configEngine, http, new NodeWalletSigner(plugin, configEngine, http));
    }

    /**
     * Fully injecting constructor: {@code signer} performs the wallet actions that sign blocks
     * ({@link NodeWalletSigner} in node mode, {@code LightWalletSigner} in light mode).
     */
    public RPC(Plugin plugin, ConfigEngine configEngine, HttpTransport http, WalletSigner signer)
    {
        this.plugin = plugin;
        this.configEngine = configEngine;
        this.http = http;
        this.signer = signer;
    }

    /** The signer performing this instance's wallet actions. */
    public WalletSigner getSigner()
    {
        return signer;
    }

    public URL getURL() throws Exception
    {
        return new URL(configEngine.getNodeAddress());
    }

    private BigDecimal getMultiplier()
    {
        return new BigDecimal(configEngine.getMultiplier());
    }

    private String getWalletID()
    {
        return configEngine.getWalletId();
    }

    public String getMasterWallet()
    {
        return configEngine.getMasterWallet();
    }

    // -------------------------------------------------------------------------
    // HTTP transport
    // -------------------------------------------------------------------------

    public String sendPost(String payload) throws Exception
    {
        return http.post(configEngine.getNodeAddress(), payload);
    }

    // -------------------------------------------------------------------------
    // Account / wallet operations
    // -------------------------------------------------------------------------

    public String accountCreate(int index)
    {
        return accountCreate(index, getWalletID());
    }

    public String accountCreate(int index, String walletId)
    {
        String account = signer.accountCreate(walletId, index);
        return account != null ? account : ACCOUNT_CREATION_FAILED;
    }

    /**
     * Returns the node's {@code "error"} text from {@code json}, or {@code null} if there is none.
     */
    private static String errorText(JsonObject json)
    {
        JsonElement error = json.get("error");
        if (error == null || error.isJsonNull())
        {
            return null;
        }
        return error.isJsonPrimitive() ? error.getAsString() : error.toString();
    }

    private BigInteger toRaw(double value)
    {
        BigDecimal multiplier = getMultiplier();
        BigDecimal bValue = new BigDecimal(Double.toString(value));
        BigDecimal raw = bValue.multiply(multiplier);
        return raw.toBigInteger();
    }

    /**
     * Convert a raw Banano amount (as BigDecimal) to the human-readable BAN value.
     * Public so that other classes (e.g. BananoWebSocket) can reuse the conversion.
     */
    public Double fromRaw(BigDecimal bigDecimal)
    {
        BigDecimal divisor = getMultiplier();
        // Round DOWN so a reported balance never exceeds the real one — otherwise "send all"
        // could ask the node for slightly more raw than the account holds.
        BigDecimal result = bigDecimal.divide(divisor, 10, RoundingMode.DOWN);
        return result.doubleValue();
    }

    public String sendTransaction(String sender, String recipient, double value) throws TransactionError
    {
        return sendTransaction(sender, recipient, value, getWalletID());
    }

    /**
     * Sends a Banano transaction, signing with the specified wallet, under a fresh idempotency id.
     *
     * @param walletId the node wallet ID whose seed controls the {@code sender} account
     */
    public String sendTransaction(String sender, String recipient, double value, String walletId)
            throws TransactionError
    {
        return sendTransaction(sender, recipient, value, walletId, UUID.randomUUID().toString());
    }

    /**
     * Sends a Banano transaction, signing with the specified wallet.
     *
     * @param walletId the node wallet ID whose seed controls the {@code sender} account
     * @param sendId   unique id for this logical send (the node's {@code "id"} field): re-sending
     *                 with the same id returns the original block instead of sending twice, so a
     *                 caller that retries one logical send must reuse its id
     */
    public String sendTransaction(String sender, String recipient, double value, String walletId, String sendId)
            throws TransactionError
    {
        if (!Double.isFinite(value) || value <= 0)
        {
            throw new TransactionError("Transaction amount must be greater than zero.");
        }

        if (!Validator.validateAddress(sender) || !Validator.validateAddress(recipient))
        {
            throw new TransactionError("Invalid address for sender or recipient.");
        }

        final BigInteger raw;

        try
        {
            raw = toRaw(value);
        }
        catch (final RuntimeException e)
        {
            plugin.getLogger().log(Level.SEVERE, "Failed to convert send amount to raw: " + value, e);
            throw new TransactionError("Invalid transaction amount.");
        }

        if (raw.signum() <= 0)
        {
            throw new TransactionError("Transaction amount must be greater than zero.");
        }

        return signer.send(walletId, sender, recipient, raw, sendId);
    }

    /**
     * Changes the representative of {@code account}, signing with the specified wallet.
     *
     * @param walletId the node wallet ID whose seed controls the {@code account}
     * @return the resulting block hash
     */
    public String setRepresentative(String account, String representative, String walletId) throws TransactionError
    {
        if (!Validator.validateAddress(account) || !Validator.validateAddress(representative))
        {
            throw new TransactionError("Invalid address for account or representative.");
        }

        return signer.setRepresentative(walletId, account, representative);
    }

    /**
     * Returns the block hashes of not-yet-pocketed ("pending"/"receivable") sends waiting for
     * {@code account}. Returns an empty list on any RPC/parsing failure, or when nothing is
     * pending — the node reports an empty backlog as {@code "blocks": ""} (a bare string) rather
     * than an empty array, so a non-array response is treated the same as "nothing pending".
     */
    /** Upper bound on pending blocks fetched (and therefore pocketed) per receive run. */
    public static final int MAX_RECEIVABLE_PER_CALL = 25;

    public List<String> receivablePending(String account)
    {
        final JsonObject json_payload = new JsonObject();
        json_payload.addProperty("action", "receivable");
        json_payload.addProperty("account", account);
        // The node excludes not-yet-confirmed ("active") blocks by default, even though block
        // explorers typically show a send as soon as it's broadcast — without this, a deposit
        // can be visible externally for a moment while still invisible to this call.
        json_payload.addProperty("include_active", true);
        // Each returned block costs a node receive (with proof-of-work), and anyone can create
        // pending blocks by sending dust to a public deposit address, so bound the batch.
        json_payload.addProperty("count", String.valueOf(MAX_RECEIVABLE_PER_CALL));

        try
        {
            String response = sendPost(json_payload.toString());
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();

            JsonElement error = json.get("error");
            if (error != null)
            {
                plugin.getLogger().warning(
                        "Failed to retrieve receivable blocks for account " + account + ": " + error.getAsString());
                return Collections.emptyList();
            }

            JsonElement blocksEl = json.get("blocks");
            if (blocksEl == null || !blocksEl.isJsonArray())
            {
                return Collections.emptyList();
            }

            List<String> hashes = new ArrayList<>();
            for (JsonElement element : blocksEl.getAsJsonArray())
            {
                hashes.add(element.getAsString());
            }
            return hashes;
        }
        catch (Exception e)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to retrieve receivable blocks for account: " + account, e);
        }

        return Collections.emptyList();
    }

    /**
     * Pockets a specific pending {@code blockHash} into {@code account}, signing with the main
     * wallet.
     *
     * @return the resulting receive-block hash
     */
    public String receiveBlock(String account, String blockHash) throws TransactionError
    {
        return receiveBlock(getWalletID(), account, blockHash);
    }

    /**
     * Pockets a specific pending {@code blockHash} into {@code account}, signing with the
     * specified wallet.
     *
     * @return the resulting receive-block hash
     */
    public String receiveBlock(String walletId, String account, String blockHash) throws TransactionError
    {
        if (!Validator.validateAddress(account))
        {
            throw new TransactionError("Invalid address for account.");
        }

        return signer.receive(walletId, account, blockHash);
    }

    /**
     * Returns the addresses of representatives the node currently sees as online/voting.
     * Returns an empty list (rather than throwing) on any RPC or parsing failure, since callers
     * use this as one candidate source among possibly others (e.g. an admin-curated list).
     */
    public List<String> representativesOnline()
    {
        final JsonObject json_payload = new JsonObject();
        json_payload.addProperty("action", "representatives_online");

        try
        {
            String response = sendPost(json_payload.toString());
            JsonElement responseJson = JsonParser.parseString(response);
            JsonArray repsArray = responseJson.getAsJsonObject().getAsJsonArray("representatives");

            if (repsArray == null)
            {
                return Collections.emptyList();
            }

            List<String> representatives = new ArrayList<>();

            for (JsonElement element : repsArray)
            {
                representatives.add(element.getAsString());
            }

            return representatives;
        }
        catch (Exception e)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to retrieve online representatives.", e);
        }

        return Collections.emptyList();
    }

    /**
     * Returns the current representative address for {@code account}, or {@code null} if it
     * could not be determined (account not found, or any RPC/parsing failure).
     */
    public String getRepresentative(String account)
    {
        final JsonObject json_payload = new JsonObject();
        json_payload.addProperty("action", "account_representative");
        json_payload.addProperty("account", account);

        try
        {
            String response = sendPost(json_payload.toString());
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            JsonElement error = json.get("error");

            if (error != null)
            {
                plugin.getLogger().warning(
                        "Failed to retrieve representative for account " + account + ": " + error.getAsString());
                return null;
            }

            JsonElement representative = json.get("representative");
            return representative != null ? representative.getAsString() : null;
        }
        catch (Exception e)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to retrieve representative for account: " + account, e);
        }

        return null;
    }

    public Double getBalance(String account)
    {
        final JsonObject json_payload = new JsonObject();

        json_payload.addProperty("action", "account_info");
        json_payload.addProperty("account", account);

        // NOTE: 0.0 doubles as the failure value here (callers rely on the Double signature), so
        // every failure path is at least logged at WARNING to distinguish it from a real zero.
        try
        {
            String balanceResponse = sendPost(json_payload.toString());
            JsonObject json = JsonParser.parseString(balanceResponse).getAsJsonObject();

            String error = errorText(json);
            if (error != null)
            {
                // An unopened account genuinely has a zero balance — not a failure.
                if (!error.equals("Account not found"))
                {
                    plugin.getLogger().warning("Failed to retrieve balance for account " + account + ": " + error);
                }
                return 0.0;
            }

            JsonElement balance = json.get("balance");
            if (balance == null || !balance.isJsonPrimitive())
            {
                plugin.getLogger().warning("Failed to retrieve balance for account " + account
                        + ": node response did not contain a balance.");
                return 0.0;
            }

            return fromRaw(balance.getAsBigDecimal());
        }
        catch (Exception e)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to retrieve balance for account: " + account, e);
        }
        return 0.0;
    }

    public List<TransactionRecord> getTransactionHistory(String account, int numberOfRecords)
    {
        final JsonObject json_payload = new JsonObject();

        json_payload.addProperty("action", "account_history");
        json_payload.addProperty("account", account);
        json_payload.addProperty("count", numberOfRecords);

        try
        {
            String transactionHistoryResponse = sendPost(json_payload.toString());
            JsonElement transactionHistoryJson = JsonParser.parseString(transactionHistoryResponse);

            try
            {
                List<TransactionRecord> records = new ArrayList<TransactionRecord>();

                if (transactionHistoryJson.getAsJsonObject().has("history")
                      && transactionHistoryJson.getAsJsonObject().get("history").isJsonArray())
                {
                    for (JsonElement arrayItem : transactionHistoryJson.getAsJsonObject().get("history").getAsJsonArray())
                    {
                        JsonObject recordJson = arrayItem.getAsJsonObject();

                        if (recordJson.has("type"))
                        {
                            TransactionDirection direction = TransactionDirection.Send;

                            if (recordJson.get("type").getAsString().equals("send"))
                            {
                                direction = TransactionDirection.Send;
                            }
                            else if (recordJson.get("type").getAsString().equals("receive"))
                            {
                                direction = TransactionDirection.Receive;
                            }

                            String address = recordJson.get("account").getAsString();
                            double amount = fromRaw(recordJson.get("amount").getAsBigDecimal());
                            LocalDateTime timestamp = LocalDateTime.ofInstant(Instant.ofEpochSecond(recordJson.get("local_timestamp").getAsLong()),
                                                                                TimeZone.getDefault().toZoneId());
                            String hash = recordJson.get("hash").getAsString();
                            boolean confirmed = false;

                            if (recordJson.has("confirmed"))
                            {
                                confirmed = recordJson.get("confirmed").getAsBoolean();
                            }

                            TransactionRecord record = new TransactionRecord(
                                    timestamp,
                                    address,
                                    direction,
                                    amount,
                                    hash,
                                    confirmed
                            );

                            records.add(record);
                        }
                    }
                }

                return records;
            }
            catch (Exception e)
            {
                plugin.getLogger().log(Level.WARNING, "Failed to retrieve history for account: " + account, e);

                JsonElement errorElement = transactionHistoryJson.getAsJsonObject().get("error");
                if (errorElement != null && errorElement.getAsString().equals("Account not found"))
                {
                    return null;
                }
            }
        }
        catch (Exception e)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to retrieve transaction history for account: " + account, e);
        }

        return null;
    }

    public List<String> getBlockCount()
    {
        final JsonObject json_payload = new JsonObject();
        json_payload.addProperty("action", "block_count");

        try
        {
            JsonElement blocksJson = JsonParser.parseString(sendPost(json_payload.toString()));
            String checked   = blocksJson.getAsJsonObject().get("count").getAsString();
            String unchecked = blocksJson.getAsJsonObject().get("unchecked").getAsString();
            return Arrays.asList(checked, unchecked);
        }
        catch (Exception e)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to retrieve block count.", e);
        }
        return Arrays.asList("Null", "Null");
    }

    /**
     * Cheap liveness check against the node ({@code block_count}).
     *
     * @return {@code true} only if the node answered with a valid block count
     */
    public boolean ping()
    {
        final JsonObject json_payload = new JsonObject();
        json_payload.addProperty("action", "block_count");

        try
        {
            JsonObject json = JsonParser.parseString(sendPost(json_payload.toString())).getAsJsonObject();
            JsonElement count = json.get("count");
            return errorText(json) == null && count != null && count.isJsonPrimitive();
        }
        catch (Exception e)
        {
            plugin.getLogger().log(Level.FINE, "Node ping failed.", e);
        }
        return false;
    }

    /** Whether the configured main wallet exists on the node — see {@link WalletStatus}. */
    public WalletStatus walletStatus()
    {
        return toStatus(signer.walletStatus(WalletSigner.Wallet.PLAYER));
    }

    private static WalletStatus toStatus(WalletSigner.Status status)
    {
        return WalletStatus.valueOf(status.name());
    }

    /**
     * Returns {@code true} only if the node confirms the main wallet exists. A transport or
     * unexpected node error yields {@code false}; use {@link #walletStatus()} to tell
     * "missing" apart from "node unreachable" before creating anything.
     */
    public Boolean wallet_exists()
    {
        return walletStatus() == WalletStatus.EXISTS;
    }

    public Boolean wallet_contains(String account)
    {
        final JsonObject json_payload = new JsonObject();
        final String walletID = getWalletID();

        json_payload.addProperty("action", "account_contains");
        json_payload.addProperty("wallet", walletID);
        json_payload.addProperty("account", account);

        try
        {
            JsonElement existsJson = JsonParser.parseString(sendPost(json_payload.toString()));
            int exists = existsJson.getAsJsonObject().get("exists").getAsInt();

            if (exists == 1)
            {
                plugin.getLogger().info("Account is in wallet: " + account);
                return true;
            }
            else if (exists == 0)
            {
                return false;
            }
        }
        catch (Exception e)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to check wallet_contains for account: " + account, e);
        }
        return false;
    }

    /**
     * Creates the main wallet from the configured seed and persists its ID. Node mode imports the
     * seed into the node and saves the returned wallet ID; light mode is a local no-op that
     * succeeds (nothing is sent anywhere and no wallet ID is stored).
     *
     * @return {@code true} only if the wallet is ready (node: ID returned and saved to config)
     */
    public boolean walletCreate()
    {
        String walletID = signer.createWallet(WalletSigner.Wallet.PLAYER);
        if (walletID == null)
        {
            return false;
        }

        if (signer.isLocal())
        {
            return true;
        }

        plugin.getLogger().info("Wallet ID generated: " + walletID);

        // Persist through ConfigEngine so save() writes all fields consistently.
        configEngine.setWalletId(walletID);
        return configEngine.save();
    }

    /** https anywhere, or plain http only when every address of the host is loopback/link-local/private. */
    static boolean isSeedSafeEndpoint(String nodeAddress)
    {
        return NodeWalletSigner.isSeedSafeEndpoint(nodeAddress);
    }

    // -------------------------------------------------------------------------
    // Bank wallet lifecycle
    // -------------------------------------------------------------------------

    /**
     * Whether the configured bank wallet exists — see {@link WalletStatus}. An unconfigured
     * (empty) bank wallet ID is reported as {@link WalletStatus#MISSING}.
     */
    public WalletStatus bankWalletStatus()
    {
        return toStatus(signer.walletStatus(WalletSigner.Wallet.BANK));
    }

    /** Returns {@code true} only if the bank wallet is confirmed to exist. */
    public Boolean bankWalletExists()
    {
        return bankWalletStatus() == WalletStatus.EXISTS;
    }

    /**
     * Creates the bank wallet from the configured bank seed and persists its ID (node mode); a
     * local no-op that succeeds in light mode.
     *
     * @return {@code true} only if the wallet is ready (node: ID returned and saved to config)
     */
    public boolean bankWalletCreate()
    {
        String walletId = signer.createWallet(WalletSigner.Wallet.BANK);
        if (walletId == null)
        {
            return false;
        }

        if (signer.isLocal())
        {
            return true;
        }

        plugin.getLogger().info("Bank wallet ID generated: " + walletId);

        configEngine.setBankWalletId(walletId);
        return configEngine.save();
    }
}
