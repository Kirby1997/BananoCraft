package banano.bananominecraft.bananoeconomy.wallet;

import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import banano.bananominecraft.bananoeconomy.io.HttpTransport;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.Plugin;

import java.math.BigInteger;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.logging.Level;

/**
 * {@link WalletSigner} backed by a wallet held on the Banano node ({@code walletMode: node}): the
 * node's wallet-control RPC actions ({@code wallet_create}, {@code wallet_balances}, {@code account_create},
 * {@code send}, {@code receive}, {@code account_representative_set}) do the signing.
 *
 * <p>This is the node-wallet code that used to live in {@code RPC}, moved here unchanged in behaviour.</p>
 */
public class NodeWalletSigner implements WalletSigner
{
    /** Node error text returned when the RPC "enable_control" setting is off. */
    private static final String RPC_CONTROL_DISABLED = "RPC control is disabled";

    private final Plugin plugin;
    private final ConfigEngine configEngine;
    private final HttpTransport http;

    public NodeWalletSigner(Plugin plugin, ConfigEngine configEngine, HttpTransport http)
    {
        this.plugin = plugin;
        this.configEngine = configEngine;
        this.http = http;
    }

    private String sendPost(String payload) throws Exception
    {
        return http.post(configEngine.getNodeAddress(), payload);
    }

    @Override
    public boolean isLocal()
    {
        return false;
    }

    // -------------------------------------------------------------------------
    // Account / wallet operations
    // -------------------------------------------------------------------------

    @Override
    public String accountCreate(String walletId, int index)
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
            JsonObject json = JsonParser.parseString(accountResponse).getAsJsonObject();

            String error = errorText(json);
            if (error != null)
            {
                logNodeError("account_create", error);
                return null;
            }

            JsonElement account = json.get("account");
            if (account == null || !account.isJsonPrimitive())
            {
                plugin.getLogger().severe("Account creation failed: node response did not contain an account.");
                return null;
            }

            return account.getAsString();
        }
        catch (Exception e)
        {
            plugin.getLogger().log(Level.SEVERE, "Account creation failed.", e);
        }
        return null;
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

    /**
     * Logs a node-reported error for a wallet-control action at SEVERE, adding a concrete hint
     * when the node refuses because RPC control is switched off.
     */
    private void logNodeError(String action, String error)
    {
        if (RPC_CONTROL_DISABLED.equalsIgnoreCase(error.trim()))
        {
            plugin.getLogger().severe("Node rejected '" + action + "': " + error
                    + ". Set enable_control = true in the node's RPC config (config-rpc.toml) and restart the node.");
        }
        else
        {
            plugin.getLogger().severe("Node rejected '" + action + "': " + error);
        }
    }

    /**
     * Returns the {@code "block"} hash from a parsed node response, throwing a
     * {@link TransactionError} carrying the node's error text, or {@code missingBlockMessage}
     * if no usable block hash is present.
     */
    private static String extractBlock(JsonObject json, String missingBlockMessage) throws TransactionError
    {
        String error = errorText(json);
        if (error != null)
        {
            throw new TransactionError(error);
        }

        JsonElement block = json.get("block");
        if (block == null || !block.isJsonPrimitive())
        {
            throw new TransactionError(missingBlockMessage);
        }
        return block.getAsString();
    }

    @Override
    public String send(String walletId, String sender, String recipient, BigInteger amountRaw, String sendId)
            throws TransactionError
    {
        final JsonObject json_payload = new JsonObject();

        json_payload.addProperty("action",      "send");
        json_payload.addProperty("wallet",      walletId);
        json_payload.addProperty("source",      sender);
        json_payload.addProperty("destination", recipient);
        json_payload.addProperty("amount",      amountRaw);
        // Idempotency key: if this logical send is retried (e.g. after a timeout where the node
        // did process it), the node returns the original block instead of sending twice.
        json_payload.addProperty("id",          sendId);

        final JsonObject json;

        try
        {
            final String sendResponse = sendPost(json_payload.toString());
            json = JsonParser.parseString(sendResponse).getAsJsonObject();
        }
        catch (final Exception e)
        {
            plugin.getLogger().log(Level.SEVERE, "Failed to sendTransaction.", e);
            throw new TransactionError("Send transaction failed");
        }

        return extractBlock(json, "Send transaction resulted in missing block");
    }

    @Override
    public String setRepresentative(String walletId, String account, String representative) throws TransactionError
    {
        final JsonObject json_payload = new JsonObject();

        json_payload.addProperty("action",         "account_representative_set");
        json_payload.addProperty("wallet",         walletId);
        json_payload.addProperty("account",        account);
        json_payload.addProperty("representative", representative);

        final JsonObject json;

        try
        {
            final String response = sendPost(json_payload.toString());
            json = JsonParser.parseString(response).getAsJsonObject();
        }
        catch (final Exception e)
        {
            plugin.getLogger().log(Level.SEVERE, "Failed to setRepresentative.", e);
            throw new TransactionError("Set representative failed");
        }

        return extractBlock(json, "Set representative resulted in missing block");
    }

    @Override
    public String receive(String walletId, String account, String blockHash) throws TransactionError
    {
        final JsonObject json_payload = new JsonObject();

        json_payload.addProperty("action",  "receive");
        json_payload.addProperty("wallet",  walletId);
        json_payload.addProperty("account", account);
        json_payload.addProperty("block",   blockHash);

        final JsonObject json;

        try
        {
            final String response = sendPost(json_payload.toString());
            json = JsonParser.parseString(response).getAsJsonObject();
        }
        catch (final Exception e)
        {
            plugin.getLogger().log(Level.SEVERE, "Failed to receiveBlock.", e);
            throw new TransactionError("Receive transaction failed");
        }

        return extractBlock(json, "Receive transaction resulted in missing block");
    }

    // -------------------------------------------------------------------------
    // Wallet existence / creation
    // -------------------------------------------------------------------------

    @Override
    public Status walletStatus(Wallet wallet)
    {
        if (wallet == Wallet.BANK)
        {
            // An unconfigured (empty) bank wallet ID is reported as MISSING.
            final String bankWalletId = configEngine.getBankWalletId();
            if (bankWalletId == null || bankWalletId.isEmpty())
            {
                return Status.MISSING;
            }
            return queryWalletStatus(bankWalletId, "Bank");
        }
        return queryWalletStatus(configEngine.getWalletId(), "Master");
    }

    /**
     * Asks the node for {@code walletId}'s balances. {@code wallet_balances} succeeds for any
     * existing wallet, so it doubles as an existence check.
     */
    private Status queryWalletStatus(String walletId, String label)
    {
        final JsonObject json_payload = new JsonObject();
        json_payload.addProperty("action", "wallet_balances");
        json_payload.addProperty("wallet", walletId == null ? "" : walletId);

        try
        {
            JsonObject json = JsonParser.parseString(sendPost(json_payload.toString())).getAsJsonObject();
            String error = errorText(json);

            if (error != null)
            {
                if (error.equals("Wallet not found") || error.equals("Bad wallet number"))
                {
                    return Status.MISSING;
                }

                plugin.getLogger().warning("Unexpected " + label + " wallet_balances error: " + error);
                return Status.UNREACHABLE;
            }

            if (json.has("balances"))
            {
                plugin.getLogger().info(label + " wallet found.");
                return Status.EXISTS;
            }

            plugin.getLogger().warning("Unexpected " + label + " wallet_balances response: " + json);
        }
        catch (Exception e)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to check " + label + " wallet existence.", e);
        }
        return Status.UNREACHABLE;
    }

    @Override
    public String createWallet(Wallet wallet)
    {
        if (wallet == Wallet.BANK)
        {
            return createWalletFromSeed(configEngine.getBankWalletSeed(), "bankWalletCreate");
        }
        return createWalletFromSeed(configEngine.getWalletSeed(), "walletCreate");
    }

    /**
     * Sends {@code wallet_create} with {@code seed}; returns the new wallet ID, or {@code null}
     * (after logging why) on any failure.
     */
    private String createWalletFromSeed(String seed, String label)
    {
        if (seed == null || seed.isBlank())
        {
            plugin.getLogger().severe(label + " refused: no wallet seed is configured.");
            return null;
        }

        // The seed gives custody of every account in the wallet, so never send it in cleartext
        // across a public network.
        if (!isSeedSafeEndpoint(configEngine.getNodeAddress()))
        {
            plugin.getLogger().severe(label + " refused: the wallet seed is only sent to an https:// node, or to an"
                    + " http:// node on loopback or a private network. Use your own node, never a public RPC API"
                    + " (or set walletMode: light so the seed never leaves this server).");
            return null;
        }

        final JsonObject json_payload = new JsonObject();
        json_payload.addProperty("action", "wallet_create");
        json_payload.addProperty("seed",   seed);

        try
        {
            JsonObject json = JsonParser.parseString(sendPost(json_payload.toString())).getAsJsonObject();

            String error = errorText(json);
            if (error != null)
            {
                logNodeError("wallet_create", error);
                return null;
            }

            JsonElement walletElement = json.get("wallet");
            if (walletElement == null || !walletElement.isJsonPrimitive() || walletElement.getAsString().isEmpty())
            {
                plugin.getLogger().severe(label + " failed: node response did not contain a wallet ID.");
                return null;
            }

            return walletElement.getAsString();
        }
        catch (Exception e)
        {
            plugin.getLogger().log(Level.SEVERE, label + " failed.", e);
        }
        return null;
    }

    /** https anywhere, or plain http only when every address of the host is loopback/link-local/private. */
    public static boolean isSeedSafeEndpoint(String nodeAddress)
    {
        try
        {
            URI uri = new URI(nodeAddress);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);

            if (scheme.equals("https"))
            {
                return true;
            }
            if (!scheme.equals("http") || uri.getHost() == null)
            {
                return false;
            }

            for (InetAddress address : InetAddress.getAllByName(uri.getHost()))
            {
                boolean uniqueLocalV6 = address instanceof Inet6Address
                        && (address.getAddress()[0] & 0xfe) == 0xfc;   // fc00::/7

                if (!(address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || uniqueLocalV6))
                {
                    return false;
                }
            }
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }
}
