package banano.bananominecraft.bananoeconomy.wallet.light;

import banano.bananominecraft.bananoeconomy.wallet.crypto.Hex;
import banano.bananominecraft.bananoeconomy.wallet.crypto.NanoKeys;
import banano.bananominecraft.bananoeconomy.wallet.crypto.StateBlock;
import banano.bananominecraft.bananoeconomy.wallet.crypto.Work;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal in-memory Banano ledger behind {@link LightRpc}: accounts with frontier/balance/representative, a block
 * store, receivable (pending) sends, and {@code process} validation (signature via NanoKeys.verify, work against a
 * low test threshold, frontier/fork/gap checks, balance deltas per subtype). Supports one-shot fault injection.
 */
final class FakeLedger implements LightRpc
{
    static final String PREFIX = "ban_";
    private static final byte[] ZERO = new byte[32];

    /** Fault applied to the next {@code process} calls, in order. */
    enum Fault
    {
        /** Behave normally. */
        NONE,
        /** Throw IOException without applying the block (request lost before reaching the node). */
        FAIL_BEFORE,
        /** Apply the block, then throw IOException (response lost). */
        FAIL_AFTER,
        /** Apply the block, then throw; the following account_info call also throws. */
        FAIL_AFTER_AND_NEXT_INFO,
        /** Apply the block but answer with an unrecognised error string. */
        APPLY_AND_ODD_ERROR,
        /** Do not apply; answer "Block work is insufficient". */
        REJECT_WORK,
        /** Do not apply; answer "Gap previous block" (node may still apply it later). */
        GAP,
        /** Do not apply; answer an unrecognised error containing "invalid". */
        ODD_INVALID
    }

    static final class Account
    {
        byte[] frontier;
        BigInteger balance;
        byte[] representative;
    }

    static final class Block
    {
        byte[] account;
        String subtype;
        BigInteger amount;
        BigInteger balance;
        JsonObject contents;
    }

    static final class Pending
    {
        byte[] destination;
        BigInteger amount;
        String source;
    }

    final long threshold;
    final Map<String, Account> accounts = new HashMap<>();
    final Map<String, Block> blocks = new HashMap<>();
    final Map<String, Pending> pending = new LinkedHashMap<>();
    final Deque<Fault> processFaults = new ArrayDeque<>();
    final List<String> processAttempts = Collections.synchronizedList(new ArrayList<>());
    final List<String> processErrors = Collections.synchronizedList(new ArrayList<>());
    final List<JsonObject> requests = Collections.synchronizedList(new ArrayList<>());
    final Map<String, AtomicInteger> actionCounts = new ConcurrentHashMap<>();
    /** Runs once inside the next process call before validation (e.g. to append a competing block). */
    Runnable beforeNextProcess;
    /** If set, rewrites every response (request, honest response) &rarr; response: a lying node. */
    volatile java.util.function.BiFunction<JsonObject, JsonObject, JsonObject> responseFilter;
    private boolean failNextInfo;
    private final SecureRandom random = new SecureRandom();

    FakeLedger(long threshold)
    {
        this.threshold = threshold;
    }

    @Override
    public JsonObject call(JsonObject request) throws IOException
    {
        Runnable hook = null;
        synchronized (this)
        {
            String action = request.get("action").getAsString();
            requests.add(request.deepCopy());
            actionCounts.computeIfAbsent(action, a -> new AtomicInteger()).incrementAndGet();
            if ("process".equals(action) && beforeNextProcess != null)
            {
                hook = beforeNextProcess;
                beforeNextProcess = null;
            }
        }
        if (hook != null)
        {
            hook.run();
        }
        JsonObject response;
        synchronized (this)
        {
            switch (request.get("action").getAsString())
            {
                case "account_info":
                    response = accountInfo(request);
                    break;
                case "receivable":
                    response = receivable(request);
                    break;
                case "block_info":
                    response = blockInfo(request);
                    break;
                case "process":
                    response = process(request);
                    break;
                default:
                    response = error("Unknown command");
            }
        }
        java.util.function.BiFunction<JsonObject, JsonObject, JsonObject> filter = responseFilter;
        return filter == null ? response : filter.apply(request.deepCopy(), response);
    }

    // ---------------------------------------------------------------- test helpers

    /** Create an external (validly signed) state send to {@code destination} and return its hash. */
    synchronized String fund(String destination, BigInteger amount)
    {
        byte[] destKey = NanoKeys.publicKeyFromAddress(destination);
        byte[] senderPrivate = new byte[32];
        random.nextBytes(senderPrivate);
        byte[] sender = NanoKeys.publicKey(senderPrivate);
        byte[] previous = new byte[32];
        random.nextBytes(previous);
        String senderAddress = NanoKeys.address(sender, PREFIX);
        StateBlock send = new StateBlock(senderAddress, previous, senderAddress, BigInteger.ZERO, destKey, null, null)
                .signed(senderPrivate).withWork(0L);
        String hex = send.hashHex();

        Block block = new Block();
        block.account = sender;
        block.subtype = "send";
        block.amount = amount;
        block.balance = BigInteger.ZERO;
        block.contents = send.toRpcJson(PREFIX);
        blocks.put(hex, block);

        Pending p = new Pending();
        p.destination = destKey;
        p.amount = amount;
        p.source = NanoKeys.address(sender, PREFIX);
        pending.put(hex, p);
        return hex;
    }

    synchronized Account account(String address)
    {
        return accounts.get(Hex.encode(NanoKeys.publicKeyFromAddress(address)));
    }

    synchronized BigInteger balance(String address)
    {
        Account a = account(address);
        return a == null ? BigInteger.ZERO : a.balance;
    }

    synchronized void addFaults(Fault... faults)
    {
        processFaults.addAll(Arrays.asList(faults));
    }

    int count(String action)
    {
        AtomicInteger c = actionCounts.get(action);
        return c == null ? 0 : c.get();
    }

    // ---------------------------------------------------------------- RPC actions

    private JsonObject accountInfo(JsonObject request) throws IOException
    {
        if (failNextInfo)
        {
            failNextInfo = false;
            throw new IOException("simulated account_info timeout");
        }
        byte[] key = NanoKeys.publicKeyFromAddress(request.get("account").getAsString());
        Account a = accounts.get(Hex.encode(key));
        if (a == null)
        {
            return error("Account not found");
        }
        JsonObject o = new JsonObject();
        o.addProperty("frontier", Hex.encode(a.frontier));
        o.addProperty("balance", a.balance.toString());
        o.addProperty("block_count", "1");
        if (request.has("representative"))
        {
            o.addProperty("representative", NanoKeys.address(a.representative, PREFIX));
        }
        return o;
    }

    private JsonObject receivable(JsonObject request)
    {
        byte[] key = NanoKeys.publicKeyFromAddress(request.get("account").getAsString());
        int count = Integer.parseInt(request.get("count").getAsString());
        boolean source = request.has("source") && "true".equals(request.get("source").getAsString());
        List<Map.Entry<String, Pending>> mine = new ArrayList<>();
        for (Map.Entry<String, Pending> e : pending.entrySet())
        {
            if (Arrays.equals(e.getValue().destination, key))
            {
                mine.add(e);
            }
        }
        mine.sort((x, y) -> y.getValue().amount.compareTo(x.getValue().amount));
        JsonObject o = new JsonObject();
        if (mine.isEmpty())
        {
            o.addProperty("blocks", "");
            return o;
        }
        JsonObject blocksJson = new JsonObject();
        for (int i = 0; i < Math.min(count, mine.size()); i++)
        {
            Map.Entry<String, Pending> e = mine.get(i);
            if (source)
            {
                JsonObject entry = new JsonObject();
                entry.addProperty("amount", e.getValue().amount.toString());
                entry.addProperty("source", e.getValue().source);
                blocksJson.add(e.getKey(), entry);
            }
            else
            {
                blocksJson.addProperty(e.getKey(), e.getValue().amount.toString());
            }
        }
        o.add("blocks", blocksJson);
        return o;
    }

    private JsonObject blockInfo(JsonObject request)
    {
        String hash = request.get("hash").getAsString().toUpperCase();
        Block b = blocks.get(hash);
        if (b == null)
        {
            return error("Block not found");
        }
        JsonObject o = new JsonObject();
        o.addProperty("block_account", NanoKeys.address(b.account, PREFIX));
        o.addProperty("amount", b.amount.toString());
        o.addProperty("balance", b.balance.toString());
        o.addProperty("height", "1");
        o.addProperty("confirmed", "true");
        o.add("contents", b.contents.deepCopy());
        o.addProperty("subtype", b.subtype);
        return o;
    }

    private JsonObject process(JsonObject request) throws IOException
    {
        Fault fault = processFaults.poll();
        JsonObject json = request.getAsJsonObject("block");
        String subtype = request.has("subtype") ? request.get("subtype").getAsString() : null;
        StateBlock block = new StateBlock(json.get("account").getAsString(),
                Hex.decode(json.get("previous").getAsString()), json.get("representative").getAsString(),
                new BigInteger(json.get("balance").getAsString()), Hex.decode(json.get("link").getAsString()),
                Hex.decode(json.get("signature").getAsString()),
                Long.parseUnsignedLong(json.get("work").getAsString(), 16));
        processAttempts.add(block.hashHex());

        if (fault == Fault.FAIL_BEFORE)
        {
            throw new IOException("simulated timeout before process");
        }
        if (fault == Fault.REJECT_WORK)
        {
            return error("Block work is insufficient");
        }
        if (fault == Fault.GAP)
        {
            return error("Gap previous block");
        }
        if (fault == Fault.ODD_INVALID)
        {
            return error("Block is invalid for some new reason");
        }

        String err = validateAndApply(block, subtype, json);
        if (err != null)
        {
            processErrors.add(err);
            return error(err);
        }
        if (fault == Fault.FAIL_AFTER)
        {
            throw new IOException("simulated timeout after process");
        }
        if (fault == Fault.FAIL_AFTER_AND_NEXT_INFO)
        {
            failNextInfo = true;
            throw new IOException("simulated timeout after process");
        }
        if (fault == Fault.APPLY_AND_ODD_ERROR)
        {
            return error("Something unexpected happened");
        }
        JsonObject o = new JsonObject();
        o.addProperty("hash", block.hashHex());
        return o;
    }

    private String validateAndApply(StateBlock block, String subtype, JsonObject json)
    {
        String hash = block.hashHex();
        if (blocks.containsKey(hash))
        {
            return "Old block";
        }
        byte[] key = NanoKeys.publicKeyFromAddress(json.get("account").getAsString());
        if (!NanoKeys.verify(key, block.hash(), Hex.decode(json.get("signature").getAsString())))
        {
            return "Bad signature";
        }
        long work = Long.parseUnsignedLong(json.get("work").getAsString(), 16);
        if (!Work.isValid(block.workRoot(), work, threshold))
        {
            return "Block work is insufficient";
        }

        byte[] previous = Hex.decode(json.get("previous").getAsString());
        byte[] link = Hex.decode(json.get("link").getAsString());
        BigInteger balance = new BigInteger(json.get("balance").getAsString());
        Account account = accounts.get(Hex.encode(key));
        BigInteger amount;

        if (Arrays.equals(previous, ZERO))
        {
            if (account != null)
            {
                return "Fork";
            }
            Pending p = pending.get(Hex.encode(link));
            if (p == null || !Arrays.equals(p.destination, key))
            {
                return "Unreceivable";
            }
            if (!balance.equals(p.amount))
            {
                return "Balance and amount delta do not match";
            }
            if (!"open".equals(subtype))
            {
                return "Invalid block subtype";
            }
            pending.remove(Hex.encode(link));
            account = new Account();
            accounts.put(Hex.encode(key), account);
            amount = p.amount;
        }
        else
        {
            if (account == null)
            {
                return "Gap previous block";
            }
            if (!Arrays.equals(previous, account.frontier))
            {
                return blocks.containsKey(Hex.encode(previous)) ? "Fork" : "Gap previous block";
            }
            int cmp = balance.compareTo(account.balance);
            if (cmp < 0)
            {
                if (!"send".equals(subtype))
                {
                    return "Invalid block subtype";
                }
                amount = account.balance.subtract(balance);
                Pending p = new Pending();
                p.destination = link;
                p.amount = amount;
                p.source = json.get("account").getAsString();
                pending.put(hash, p);
            }
            else if (cmp > 0)
            {
                Pending p = pending.get(Hex.encode(link));
                if (p == null || !Arrays.equals(p.destination, key))
                {
                    return "Unreceivable";
                }
                if (!balance.subtract(account.balance).equals(p.amount))
                {
                    return "Balance and amount delta do not match";
                }
                if (!"receive".equals(subtype))
                {
                    return "Invalid block subtype";
                }
                pending.remove(Hex.encode(link));
                amount = p.amount;
            }
            else
            {
                if (!Arrays.equals(link, ZERO) || !"change".equals(subtype))
                {
                    return "Invalid block subtype";
                }
                amount = BigInteger.ZERO;
            }
        }

        account.frontier = block.hash();
        account.balance = balance;
        account.representative = NanoKeys.publicKeyFromAddress(json.get("representative").getAsString());

        Block stored = new Block();
        stored.account = key;
        stored.subtype = subtype;
        stored.amount = amount;
        stored.balance = balance;
        JsonObject contents = json.deepCopy();
        stored.contents = contents;
        blocks.put(hash, stored);
        return null;
    }

    private static JsonObject error(String message)
    {
        JsonObject o = new JsonObject();
        o.addProperty("error", message);
        return o;
    }
}
