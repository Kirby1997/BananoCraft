package banano.bananominecraft.bananoeconomy.io;

import banano.bananominecraft.bananoeconomy.validation.Validator;
import banano.bananominecraft.bananoeconomy.classes.TransactionRecord;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.enums.TransactionDirection;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
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

    private final Plugin plugin;
    private final ConfigEngine configEngine;
    private final HttpTransport http;

    public RPC(Plugin plugin, ConfigEngine configEngine)
    {
        this(plugin, configEngine, new HttpUrlConnectionTransport());
    }

    /**
     * Transport-injecting constructor — pass a mock {@link HttpTransport} in tests to
     * exercise the JSON-RPC logic without touching the network.
     */
    public RPC(Plugin plugin, ConfigEngine configEngine, HttpTransport http)
    {
        this.plugin = plugin;
        this.configEngine = configEngine;
        this.http = http;
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
        JsonObject json_payload = new JsonObject();

        json_payload.addProperty("action", "account_create");
        json_payload.addProperty("wallet", walletId);

        if (index != -1)
        {
            json_payload.addProperty("index", index);
        }

        try
        {
            String accountResponse = sendPost(json_payload.toString());
            JsonElement accountJson = JsonParser.parseString(accountResponse);
            return accountJson.getAsJsonObject().get("account").getAsString();
        }
        catch (Exception e)
        {
            plugin.getLogger().log(Level.SEVERE, "Account creation failed.", e);
        }
        return ACCOUNT_CREATION_FAILED;
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
        BigDecimal result = bigDecimal.divide(divisor, 10, RoundingMode.HALF_UP);
        return result.doubleValue();
    }

    public String sendTransaction(String sender, String recipient, double value) throws TransactionError
    {
        return sendTransaction(sender, recipient, value, getWalletID());
    }

    /**
     * Sends a Banano transaction, signing with the specified wallet.
     *
     * @param walletId the node wallet ID whose seed controls the {@code sender} account
     */
    public String sendTransaction(String sender, String recipient, double value, String walletId)
            throws TransactionError
    {
        if (value <= 0)
        {
            throw new TransactionError("Transaction amount must be greater than zero.");
        }

        if (!Validator.validateAddress(sender) || !Validator.validateAddress(recipient))
        {
            throw new TransactionError("Invalid address for sender or recipient.");
        }

        final JsonObject json_payload = new JsonObject();

        json_payload.addProperty("action",      "send");
        json_payload.addProperty("wallet",      walletId);
        json_payload.addProperty("source",      sender);
        json_payload.addProperty("destination", recipient);
        json_payload.addProperty("amount",      toRaw(value));

        final JsonElement accountJson;

        try
        {
            final String sendResponse = sendPost(json_payload.toString());
            accountJson = JsonParser.parseString(sendResponse);
        }
        catch (final Exception e)
        {
            plugin.getLogger().log(Level.SEVERE, "Failed to sendTransaction.", e);
            throw new TransactionError("Send transaction failed");
        }

        final JsonObject json  = accountJson.getAsJsonObject();
        final JsonElement error = json.get("error");
        if (error != null)
        {
            throw new TransactionError(error.getAsString());
        }

        return Optional
                .ofNullable(json.get("block"))
                .map(JsonElement::getAsString)
                .orElseThrow(() -> new TransactionError("Send transaction resulted in missing block"));
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

        final JsonObject json_payload = new JsonObject();

        json_payload.addProperty("action",         "account_representative_set");
        json_payload.addProperty("wallet",         walletId);
        json_payload.addProperty("account",        account);
        json_payload.addProperty("representative", representative);

        final JsonElement responseJson;

        try
        {
            final String response = sendPost(json_payload.toString());
            responseJson = JsonParser.parseString(response);
        }
        catch (final Exception e)
        {
            plugin.getLogger().log(Level.SEVERE, "Failed to setRepresentative.", e);
            throw new TransactionError("Set representative failed");
        }

        final JsonObject json  = responseJson.getAsJsonObject();
        final JsonElement error = json.get("error");
        if (error != null)
        {
            throw new TransactionError(error.getAsString());
        }

        return Optional
                .ofNullable(json.get("block"))
                .map(JsonElement::getAsString)
                .orElseThrow(() -> new TransactionError("Set representative resulted in missing block"));
    }

    /**
     * Returns the block hashes of not-yet-pocketed ("pending"/"receivable") sends waiting for
     * {@code account}. Returns an empty list on any RPC/parsing failure, or when nothing is
     * pending — the node reports an empty backlog as {@code "blocks": ""} (a bare string) rather
     * than an empty array, so a non-array response is treated the same as "nothing pending".
     */
    public List<String> receivablePending(String account)
    {
        final JsonObject json_payload = new JsonObject();
        json_payload.addProperty("action", "receivable");
        json_payload.addProperty("account", account);
        // The node excludes not-yet-confirmed ("active") blocks by default, even though block
        // explorers typically show a send as soon as it's broadcast — without this, a deposit
        // can be visible externally for a moment while still invisible to this call.
        json_payload.addProperty("include_active", true);

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

        final JsonObject json_payload = new JsonObject();

        json_payload.addProperty("action",  "receive");
        json_payload.addProperty("wallet",  walletId);
        json_payload.addProperty("account", account);
        json_payload.addProperty("block",   blockHash);

        final JsonElement responseJson;

        try
        {
            final String response = sendPost(json_payload.toString());
            responseJson = JsonParser.parseString(response);
        }
        catch (final Exception e)
        {
            plugin.getLogger().log(Level.SEVERE, "Failed to receiveBlock.", e);
            throw new TransactionError("Receive transaction failed");
        }

        final JsonObject json  = responseJson.getAsJsonObject();
        final JsonElement error = json.get("error");
        if (error != null)
        {
            throw new TransactionError(error.getAsString());
        }

        return Optional
                .ofNullable(json.get("block"))
                .map(JsonElement::getAsString)
                .orElseThrow(() -> new TransactionError("Receive transaction resulted in missing block"));
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

        try
        {
            String balanceResponse = sendPost(json_payload.toString());
            JsonElement accountJson = JsonParser.parseString(balanceResponse);
            try
            {
                BigDecimal bigDecimal = accountJson.getAsJsonObject().get("balance").getAsBigDecimal();
                return fromRaw(bigDecimal);
            }
            catch (Exception e)
            {
                JsonElement errorElement = accountJson.getAsJsonObject().get("error");
                if (errorElement != null && errorElement.getAsString().equals("Account not found"))
                {
                    return 0.0;
                }
            }
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

    public Boolean wallet_exists()
    {
        final JsonObject json_payload = new JsonObject();
        final String walletID = getWalletID();

        json_payload.addProperty("action", "wallet_balances");
        json_payload.addProperty("wallet", walletID);

        try
        {
            JsonElement existsJson = JsonParser.parseString(sendPost(json_payload.toString()));
            try
            {
                String exists = existsJson.getAsJsonObject().get("error").getAsString();
                if (exists.equals("Wallet not found") || exists.equals("Bad wallet number"))
                {
                    return false;
                }
                else
                {
                    plugin.getLogger().warning("Unexpected wallet_balances error: " + exists);
                }
            }
            catch (Exception e)
            {
                plugin.getLogger().info("Master wallet found.");
            }
        }
        catch (Exception e)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to check wallet existence.", e);
        }
        return true;
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

    public void walletCreate()
    {
        String seed = configEngine.getWalletSeed();

        final JsonObject json_payload = new JsonObject();
        json_payload.addProperty("action", "wallet_create");
        json_payload.addProperty("seed", seed);

        try
        {
            JsonElement blocksJson = JsonParser.parseString(sendPost(json_payload.toString()));
            String walletID = blocksJson.getAsJsonObject().get("wallet").getAsString();

            plugin.getLogger().info("Wallet ID generated: " + walletID);

            // Persist through ConfigEngine so save() writes all fields consistently.
            configEngine.setWalletId(walletID);
            configEngine.save();
        }
        catch (Exception e)
        {
            plugin.getLogger().log(Level.SEVERE, "walletCreate failed.", e);
        }
    }

    // -------------------------------------------------------------------------
    // Bank wallet lifecycle
    // -------------------------------------------------------------------------

    public Boolean bankWalletExists()
    {
        final String bankWalletId = configEngine.getBankWalletId();
        if (bankWalletId == null || bankWalletId.isEmpty())
        {
            return false;
        }

        final JsonObject json_payload = new JsonObject();
        json_payload.addProperty("action", "wallet_balances");
        json_payload.addProperty("wallet", bankWalletId);

        try
        {
            JsonElement existsJson = JsonParser.parseString(sendPost(json_payload.toString()));
            try
            {
                String error = existsJson.getAsJsonObject().get("error").getAsString();
                if (error.equals("Wallet not found") || error.equals("Bad wallet number"))
                {
                    return false;
                }
                else
                {
                    plugin.getLogger().warning("Unexpected bank wallet_balances error: " + error);
                }
            }
            catch (Exception e)
            {
                plugin.getLogger().info("Bank wallet found.");
            }
        }
        catch (Exception e)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to check bank wallet existence.", e);
        }
        return true;
    }

    public void bankWalletCreate()
    {
        String seed = configEngine.getBankWalletSeed();

        final JsonObject json_payload = new JsonObject();
        json_payload.addProperty("action", "wallet_create");
        json_payload.addProperty("seed",   seed);

        try
        {
            JsonElement blocksJson = JsonParser.parseString(sendPost(json_payload.toString()));
            String walletId = blocksJson.getAsJsonObject().get("wallet").getAsString();

            plugin.getLogger().info("Bank wallet ID generated: " + walletId);

            configEngine.setBankWalletId(walletId);
            configEngine.save();
        }
        catch (Exception e)
        {
            plugin.getLogger().log(Level.SEVERE, "bankWalletCreate failed.", e);
        }
    }
}
