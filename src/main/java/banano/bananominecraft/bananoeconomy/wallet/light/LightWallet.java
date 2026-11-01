package banano.bananominecraft.bananoeconomy.wallet.light;

import banano.bananominecraft.bananoeconomy.wallet.crypto.Hex;
import banano.bananominecraft.bananoeconomy.wallet.crypto.NanoKeys;
import banano.bananominecraft.bananoeconomy.wallet.crypto.StateBlock;
import banano.bananominecraft.bananoeconomy.wallet.crypto.Work;
import banano.bananominecraft.bananoeconomy.wallet.crypto.WorkGenerator;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A custodial "light" wallet: one seed, many accounts by index. Keys are derived, blocks are built and signed and
 * proof-of-work is generated locally; the node (possibly a public RPC) only ever sees signed blocks via
 * {@code process}. The seed never leaves this object.
 *
 * <h2>Concurrency and safety</h2>
 * <ul>
 *   <li>Every block-producing operation on an account index holds that index's lock across
 *       {@code account_info} &rarr; build &rarr; sign &rarr; work &rarr; {@code process}, so two blocks are never
 *       built on the same frontier by this instance. There is deliberately no balance cache: the chain head is
 *       re-read under the lock immediately before each block.</li>
 *   <li>If {@code process} fails without a definitive answer, the account is re-read: our hash as frontier means
 *       success; an unchanged frontier means the SAME signed block (same hash, idempotent) is sent once more;
 *       anything else is {@link LightWalletException.Kind#UNKNOWN_OUTCOME}. A block with an unknown outcome is
 *       remembered (in memory and in the {@link PendingBlockStore}, so it survives a restart) and republished or
 *       resolved before any other block is built on that account, so a second, different block is never built on
 *       its previous.</li>
 *   <li>The node is not trusted for the values that go into a signed block. Before building on an opened account
 *       the frontier block is fetched with {@code block_info} and rebuilt locally: its hash must equal the reported
 *       frontier, its account must be ours and its signature must verify with our public key. Balance and
 *       representative are taken only from that verified block; any mismatch is
 *       {@link LightWalletException.Kind#UNAVAILABLE} and nothing is signed. Source blocks of receives are
 *       likewise checked to be validly signed state sends to this account.</li>
 *   <li>A rejected or unknown outcome is never reported as success.</li>
 *   <li>Methods block the calling thread for network round trips and, when no precomputed work is available, for
 *       proof-of-work generation. Call them from async threads only (never the server main thread). No Bukkit
 *       API is used.</li>
 * </ul>
 */
public final class LightWallet
{
    /** Upper bound on receivable blocks pocketed by one {@link #receivePending(int, int)} call. */
    static final int MAX_RECEIVE_BATCH = 25;

    private static final long DEFAULT_WORK_TIMEOUT_MS = 180_000L;
    private static final BigInteger MAX_BALANCE = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE);
    private static final byte[] ZERO32 = new byte[32];
    private static final String ACCOUNT_NOT_FOUND = "Account not found";
    private static final String BLOCK_NOT_FOUND = "Block not found";

    private final byte[] seed;
    private final String prefix;
    private final String defaultRepresentative;
    private final LightRpc rpc;
    private final WorkGenerator work;
    private final long workThreshold;
    private final Logger log;
    private final PendingBlockStore store;

    private final Map<Integer, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final Map<Integer, Precomputed> precomputed = new ConcurrentHashMap<>();
    private final Map<Integer, Unresolved> unresolved = new ConcurrentHashMap<>();
    private final Map<Integer, byte[]> publicKeys = new ConcurrentHashMap<>();
    private final Set<Integer> precomputeQueued = ConcurrentHashMap.newKeySet();
    private final ExecutorService background;

    private volatile boolean shutdown;
    private volatile long workTimeoutMillis = DEFAULT_WORK_TIMEOUT_MS;

    /**
     * @param seed32                the 32-byte wallet seed (copied)
     * @param prefix                address prefix, e.g. {@code "ban_"}
     * @param defaultRepresentative representative used in open blocks of new accounts
     * @param rpc                   node access used for reads and {@code process}
     * @param work                  proof-of-work generator (owned by this wallet: stopped by {@link #shutdown()})
     * @param workThreshold         minimum work value, e.g. {@link Work#BANANO_THRESHOLD}
     * @param log                   logger for operational messages
     * @throws IllegalArgumentException if the seed is not 32 bytes or the default representative is not a valid
     *                                  address for {@code prefix}
     */
    public LightWallet(byte[] seed32, String prefix, String defaultRepresentative, LightRpc rpc, WorkGenerator work,
                       long workThreshold, Logger log)
    {
        this(seed32, prefix, defaultRepresentative, rpc, work, workThreshold, log, PendingBlockStore.inMemory());
    }

    /**
     * Like {@link #LightWallet(byte[], String, String, LightRpc, WorkGenerator, long, Logger)}, with a durable store
     * for blocks whose outcome is unknown. Every record in {@code store} is loaded now and treated as unresolved: it
     * is republished or resolved against the ledger before any new block is built on that account. A record that
     * cannot be parsed, or is not a validly signed block of the account at its index, blocks that account
     * ({@code UNAVAILABLE}) until it is removed from the store by an operator.
     *
     * @param store durable store of unresolved blocks
     * @throws IllegalArgumentException as for the other constructor
     * @throws IllegalStateException    if {@code store.loadAll()} fails
     */
    public LightWallet(byte[] seed32, String prefix, String defaultRepresentative, LightRpc rpc, WorkGenerator work,
                       long workThreshold, Logger log, PendingBlockStore store)
    {
        if (seed32 == null || seed32.length != 32)
        {
            throw new IllegalArgumentException("Seed must be 32 bytes");
        }
        this.prefix = Objects.requireNonNull(prefix, "prefix");
        if (defaultRepresentative == null || !NanoKeys.isValidAddress(defaultRepresentative, prefix))
        {
            throw new IllegalArgumentException("Invalid default representative: " + defaultRepresentative);
        }
        this.seed = seed32.clone();
        this.defaultRepresentative = defaultRepresentative;
        this.rpc = Objects.requireNonNull(rpc, "rpc");
        this.work = Objects.requireNonNull(work, "work");
        this.workThreshold = workThreshold;
        this.log = log != null ? log : Logger.getLogger(LightWallet.class.getName());
        this.store = Objects.requireNonNull(store, "store");
        loadUnresolved();

        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 30L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), runnable ->
        {
            Thread thread = new Thread(runnable, "BananoLightWallet-precompute");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
        executor.allowCoreThreadTimeOut(true);
        this.background = executor;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------------------------------------------------

    /**
     * @param index account index (0 .. 2^31-1)
     * @return the address of the account at {@code index}
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public String address(int index)
    {
        if (index < 0)
        {
            throw new IllegalArgumentException("Account index must not be negative: " + index);
        }
        return NanoKeys.address(publicKey(index), prefix);
    }

    /**
     * Read the account's current chain head from the node (no locking, no caching). The values are as reported by
     * the node and are NOT verified; use them for display only. Block-producing operations re-read and verify the
     * frontier block themselves.
     *
     * @param index account index
     * @return the account state; unopened accounts are reported with {@code opened() == false}
     * @throws LightWalletException {@code INVALID} for a negative index, {@code UNAVAILABLE} if the node cannot be
     *                              queried
     */
    public AccountState accountState(int index) throws LightWalletException
    {
        checkIndex(index);
        return readAccount(index);
    }

    /**
     * Send {@code amountRaw} from account {@code fromIndex} to {@code toAddress}.
     *
     * @param fromIndex sending account index
     * @param toAddress destination address (must use this wallet's prefix)
     * @param amountRaw amount in raw, &gt; 0
     * @return the hash (upper-case hex) of the published send block
     * @throws LightWalletException {@code INVALID}, {@code INSUFFICIENT_BALANCE}, {@code UNAVAILABLE},
     *                              {@code REJECTED} or {@code UNKNOWN_OUTCOME}
     */
    public String send(int fromIndex, String toAddress, BigInteger amountRaw) throws LightWalletException
    {
        checkIndex(fromIndex);
        byte[] destination = parseAddress(toAddress, "destination");
        if (amountRaw == null || amountRaw.signum() <= 0 || amountRaw.compareTo(MAX_BALANCE) > 0)
        {
            throw new LightWalletException(LightWalletException.Kind.INVALID, "Invalid amount: " + amountRaw);
        }

        return locked(fromIndex, () ->
        {
            AccountState head = prepareHead(fromIndex);
            if (!head.opened() || head.balance().compareTo(amountRaw) < 0)
            {
                throw new LightWalletException(LightWalletException.Kind.INSUFFICIENT_BALANCE,
                        "Account " + fromIndex + " balance " + head.balance() + " raw is below " + amountRaw + " raw");
            }

            byte[] previous = head.frontier();
            StateBlock block = new StateBlock(address(fromIndex), previous, representativeOf(head),
                    head.balance().subtract(amountRaw), destination, null, null);
            return publishNew(fromIndex, block, "send", previous);
        });
    }

    /**
     * Pocket the send block {@code sourceSendHash} into account {@code index} (an open block when the account is
     * unopened, using the default representative).
     *
     * @param index          receiving account index
     * @param sourceSendHash hash of a send block whose destination is this account
     * @return the hash of the published receive/open block
     * @throws LightWalletException {@code INVALID} if the hash is malformed, unknown, not a send or not destined to
     *                              this account; {@code UNAVAILABLE}, {@code REJECTED} (e.g. already received) or
     *                              {@code UNKNOWN_OUTCOME}
     */
    public String receive(int index, String sourceSendHash) throws LightWalletException
    {
        checkIndex(index);
        byte[] source = parseHash(sourceSendHash);

        return locked(index, () ->
        {
            AccountState head = prepareHead(index);
            BigInteger amount = sendAmountFor(index, source);
            return receiveLocked(index, head, source, amount);
        });
    }

    /**
     * Pocket up to {@code max} (capped at 25) receivable blocks for account {@code index}, largest first, one after
     * another while holding the account lock.
     *
     * <p>If a receive fails after at least one succeeded, the loop stops, the failure is logged and the hashes
     * already published are returned; if the very first one fails, its exception is thrown.</p>
     *
     * @param index account index
     * @param max   maximum number of blocks to receive (values above 25 are capped; &le; 0 returns nothing)
     * @return hashes of the published receive/open blocks, in order
     * @throws LightWalletException see {@link #receive(int, String)}
     */
    public List<String> receivePending(int index, int max) throws LightWalletException
    {
        checkIndex(index);
        if (max <= 0)
        {
            return Collections.emptyList();
        }
        int count = Math.min(max, MAX_RECEIVE_BATCH);

        return locked(index, () ->
        {
            AccountState head = prepareHead(index);
            Map<String, BigInteger> receivable = fetchReceivable(index, count);

            List<String> received = new ArrayList<>();
            for (Map.Entry<String, BigInteger> entry : receivable.entrySet())
            {
                try
                {
                    if (!received.isEmpty())
                    {
                        // No balance cache: re-read the chain head under the lock before every block.
                        head = prepareHead(index);
                    }
                    byte[] source = Hex.decode(entry.getKey());
                    // Always verify the source block itself; the receivable listing is only a hint.
                    BigInteger amount = sendAmountFor(index, source);
                    if (entry.getValue() != null && entry.getValue().compareTo(amount) != 0)
                    {
                        throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE, "Node reported "
                                + "inconsistent amounts for source block " + entry.getKey());
                    }
                    received.add(receiveLocked(index, head, source, amount));
                }
                catch (LightWalletException ex)
                {
                    if (received.isEmpty())
                    {
                        throw ex;
                    }
                    log.log(Level.WARNING, "Light wallet: stopped receiving for account " + index + " after "
                            + received.size() + " block(s): " + ex.getMessage());
                    break;
                }
            }
            return received;
        });
    }

    /**
     * Publish a change block setting the representative of an opened account.
     *
     * @param index          account index
     * @param representative new representative address
     * @return the hash of the published change block
     * @throws LightWalletException {@code INVALID} for a bad address or an unopened account; {@code UNAVAILABLE},
     *                              {@code REJECTED} or {@code UNKNOWN_OUTCOME}
     */
    public String changeRepresentative(int index, String representative) throws LightWalletException
    {
        checkIndex(index);
        parseAddress(representative, "representative");

        return locked(index, () ->
        {
            AccountState head = prepareHead(index);
            if (!head.opened())
            {
                throw new LightWalletException(LightWalletException.Kind.INVALID,
                        "Account " + index + " is not opened; its representative is set by its open block");
            }
            byte[] previous = head.frontier();
            StateBlock block = new StateBlock(address(index), previous, representative, head.balance(), ZERO32.clone(),
                    null, null);
            return publishNew(index, block, "change", previous);
        });
    }

    /**
     * Start generating proof-of-work for the account's next block in the background (best effort, returns
     * immediately). Skipped if the account is busy; a block-producing operation precomputes for its successor
     * itself.
     *
     * @param index account index
     */
    public void precomputeWork(int index)
    {
        if (index < 0 || shutdown || !precomputeQueued.add(index))
        {
            return;
        }
        try
        {
            background.execute(() ->
            {
                precomputeQueued.remove(index);
                precomputeNow(index);
            });
        }
        catch (RejectedExecutionException ex)
        {
            precomputeQueued.remove(index);
        }
    }

    /**
     * Stop background precomputation and the work generator. Subsequent operations fail with
     * {@code UNAVAILABLE}; an operation already waiting for work fails the same way.
     */
    public void shutdown()
    {
        shutdown = true;
        background.shutdownNow();
        for (Precomputed entry : precomputed.values())
        {
            entry.future.cancel(true);
        }
        precomputed.clear();
        try
        {
            work.shutdown();
        }
        catch (RuntimeException ex)
        {
            log.log(Level.FINE, "Light wallet: work generator shutdown failed", ex);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Package-private hooks (tests)
    // ------------------------------------------------------------------------------------------------------------

    void setWorkTimeoutMillis(long millis)
    {
        this.workTimeoutMillis = millis;
    }

    /** @return whether a block with unknown outcome is pending resolution for {@code index} */
    boolean hasUnresolved(int index)
    {
        return unresolved.containsKey(index);
    }

    /** @return the root of the currently cached precomputed work for {@code index}, or {@code null} */
    byte[] precomputedRoot(int index)
    {
        Precomputed entry = precomputed.get(index);
        return entry == null ? null : entry.root.clone();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Block building
    // ------------------------------------------------------------------------------------------------------------

    private String receiveLocked(int index, AccountState head, byte[] source, BigInteger amount)
            throws LightWalletException
    {
        if (amount == null || amount.signum() <= 0)
        {
            throw new LightWalletException(LightWalletException.Kind.INVALID,
                    "Source block " + Hex.encode(source) + " has no positive amount");
        }
        BigInteger newBalance = head.balance().add(amount);
        if (newBalance.compareTo(MAX_BALANCE) > 0)
        {
            throw new LightWalletException(LightWalletException.Kind.INVALID, "Balance would overflow");
        }

        if (!head.opened())
        {
            StateBlock open = new StateBlock(address(index), ZERO32.clone(), defaultRepresentative, newBalance,
                    source.clone(), null, null);
            return publishNew(index, open, "open", ZERO32);
        }

        byte[] previous = head.frontier();
        StateBlock block = new StateBlock(address(index), previous, representativeOf(head), newBalance, source.clone(),
                null, null);
        return publishNew(index, block, "receive", previous);
    }

    private String representativeOf(AccountState head)
    {
        String rep = head.representative();
        if (rep != null && NanoKeys.isValidAddress(rep, prefix))
        {
            return rep;
        }
        log.warning("Light wallet: node reported no usable representative (" + rep + "); using the default");
        return defaultRepresentative;
    }

    /**
     * Read the chain head and settle any block of this account whose outcome is still unknown, so that a new
     * block is never built on the same previous as a block that may yet be confirmed. The returned head of an opened
     * account is verified (see {@link #verifyHead}). Caller holds the lock.
     */
    private AccountState prepareHead(int index) throws LightWalletException
    {
        Unresolved pending = unresolved.get(index);
        if (pending != null && pending.block == null)
        {
            throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE, "Account " + index + " has an "
                    + "unreadable stored block with unknown outcome (" + pending.problem + "); not building on it "
                    + "until the record is checked and removed by an operator");
        }

        AccountState state = readAccount(index);
        if (pending == null)
        {
            return verifyHead(index, state);
        }

        byte[] pendingHash = pending.block.hash();
        if (Arrays.equals(state.frontier(), pendingHash))
        {
            removeUnresolved(index, pending);
            log.info("Light wallet: block " + pending.block.hashHex() + " of account " + index
                    + " (outcome was unknown) is on the ledger");
            return verifyHead(index, state);
        }

        if (Arrays.equals(state.frontier(), pending.previous))
        {
            log.warning("Light wallet: republishing block " + pending.block.hashHex() + " of account " + index
                    + " whose outcome was unknown before building a new block");
            try
            {
                publish(index, pending.block, pending.subtype, pending.previous);
            }
            catch (LightWalletException ex)
            {
                // Only an exact, known node rejection (see isDefinitiveRejection) proves the block will never be on
                // the ledger; a Gap, an unrecognised error or no answer keeps it pending.
                if (ex.kind() != LightWalletException.Kind.REJECTED)
                {
                    throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE, "Earlier block "
                            + pending.block.hashHex() + " still has an unknown outcome; not building on account "
                            + index, ex);
                }
                log.warning("Light wallet: earlier block " + pending.block.hashHex() + " was rejected: "
                        + ex.getMessage());
            }
            return verifyHead(index, readAccount(index));
        }

        // The chain moved past our previous without our block as frontier: it is either buried under later
        // blocks or lost to a competing block. Either way a new block will not share its previous.
        Boolean present = blockExists(pendingHash);
        removeUnresolved(index, pending);
        log.warning("Light wallet: earlier block " + pending.block.hashHex() + " of account " + index
                + (Boolean.TRUE.equals(present) ? " is on the ledger" : " is not the frontier and was superseded"));
        return verifyHead(index, state);
    }

    /**
     * Replace the node-reported balance and representative of an opened account with values from its verified
     * frontier block: fetched with {@code block_info}, rebuilt locally, its hash must equal the reported frontier,
     * its account must be ours and its signature must verify with our public key. A lying node can then only make
     * us build on a stale (but genuine) frontier, which the network rejects as a fork, never on a forged balance.
     * Any mismatch is UNAVAILABLE. Unopened accounts have nothing to verify.
     */
    private AccountState verifyHead(int index, AccountState state) throws LightWalletException
    {
        if (!state.opened())
        {
            return state;
        }
        byte[] frontier = state.frontier();
        String frontierHex = Hex.encode(frontier);
        JsonObject contents = blockContents(frontier);
        if (contents == null)
        {
            throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE, "Frontier block " + frontierHex
                    + " of account " + index + " was not found by block_info");
        }
        String type = optString(contents, "type");
        if (!"state".equals(type))
        {
            throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE, "Frontier block " + frontierHex
                    + " of account " + index + " is a legacy '" + type + "' block; only state-block chains are "
                    + "supported");
        }

        StateBlock block;
        try
        {
            block = StateBlock.fromRpcJson(contents);
        }
        catch (RuntimeException ex)
        {
            throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE, "Malformed frontier block "
                    + frontierHex + " of account " + index, ex);
        }
        byte[] ours = publicKey(index);
        if (!Arrays.equals(block.hash(), frontier))
        {
            throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE, "Frontier block contents of "
                    + "account " + index + " hash to " + block.hashHex() + ", not the reported " + frontierHex);
        }
        if (!Arrays.equals(block.getAccountPublicKey(), ours))
        {
            throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE, "Frontier block " + frontierHex
                    + " does not belong to account " + index);
        }
        byte[] signature = block.getSignature();
        if (signature == null || !NanoKeys.verify(ours, frontier, signature))
        {
            throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE, "Frontier block " + frontierHex
                    + " of account " + index + " is not signed by this account's key");
        }

        String representative = NanoKeys.address(NanoKeys.publicKeyFromAddress(block.getRepresentative()), prefix);
        if (state.balance().compareTo(block.getBalance()) != 0)
        {
            log.warning("Light wallet: node reported balance " + state.balance() + " raw for account " + index
                    + " but its verified frontier block says " + block.getBalance() + " raw; using the latter");
        }
        return new AccountState(true, frontier, block.getBalance(), representative);
    }

    /** @return the {@code contents} object of a block via {@code block_info}, or null if the node does not know it */
    private JsonObject blockContents(byte[] hash) throws LightWalletException
    {
        String hex = Hex.encode(hash);
        JsonObject request = new JsonObject();
        request.addProperty("action", "block_info");
        request.addProperty("hash", hex);
        request.addProperty("json_block", "true");
        JsonObject response = call(request);
        String error = errorOf(response);
        if (error != null)
        {
            if (error.equalsIgnoreCase(BLOCK_NOT_FOUND))
            {
                return null;
            }
            throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE, "block_info failed: " + error);
        }
        try
        {
            return contentsOf(response);
        }
        catch (RuntimeException ex)
        {
            throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE,
                    "Malformed block_info response for " + hex, ex);
        }
    }

    private static JsonObject contentsOf(JsonObject response)
    {
        JsonElement c = response.get("contents");
        if (c == null || c.isJsonNull())
        {
            throw new IllegalArgumentException("Missing contents");
        }
        return c.isJsonObject() ? c.getAsJsonObject() : JsonParser.parseString(c.getAsString()).getAsJsonObject();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Publishing and outcome handling
    // ------------------------------------------------------------------------------------------------------------

    private String publishNew(int index, StateBlock unsigned, String subtype, byte[] previous)
            throws LightWalletException
    {
        byte[] key = privateKey(index);
        StateBlock signed;
        try
        {
            signed = unsigned.signed(key);
        }
        finally
        {
            Arrays.fill(key, (byte) 0);
        }
        StateBlock ready = signed.withWork(obtainWork(index, signed.workRoot()));
        return publish(index, ready, subtype, previous);
    }

    /**
     * Process a fully signed block with work and determine its outcome. Returns only when the block is known to be
     * on the ledger.
     */
    private String publish(int index, StateBlock ready, String subtype, byte[] previous) throws LightWalletException
    {
        final byte[] hash = ready.hash();
        final String hashHex = ready.hashHex();
        boolean resent = false;
        boolean regenerated = false;

        while (true)
        {
            Reply reply = process(ready, subtype);

            if (reply.hash != null)
            {
                if (hashHex.equalsIgnoreCase(reply.hash))
                {
                    return succeeded(index, ready);
                }
                log.warning("Light wallet: node answered hash " + reply.hash + " for block " + hashHex);
                // Fall through to the no-answer path: verify against the ledger.
            }
            else if (reply.error != null)
            {
                String error = reply.error;
                if (isWorkError(error) && !regenerated)
                {
                    regenerated = true;
                    discardPrecomputed(index);
                    log.warning("Light wallet: node rejected work for " + hashHex + " (" + error + "); regenerating");
                    ready = ready.withWork(generateWork(ready.workRoot()));
                    continue;
                }
                if (isDefinitiveRejection(error))
                {
                    throw rejected(index, hash, "Node rejected " + subtype + " block " + hashHex + ": " + error);
                }

                // "Old block" (the exact block already exists) or an unrecognised error: ask the ledger.
                Boolean present = confirmPresent(index, hash);
                if (Boolean.TRUE.equals(present))
                {
                    return succeeded(index, ready);
                }
                // Not (yet) on the ledger, but an unrecognised answer does not prove it never will be (e.g. a
                // proxy timing out while the node still processes it): stay conservative.
                throw unknown(index, ready, subtype, previous, "Node answered '" + error + "' for " + hashHex
                        + (Boolean.FALSE.equals(present) ? " and it is not on the ledger yet"
                        : " and the ledger could not be checked"), null);
            }

            // No usable answer: the block may or may not have reached the node.
            AccountState now;
            try
            {
                now = readAccount(index);
            }
            catch (LightWalletException ex)
            {
                throw unknown(index, ready, subtype, previous, "No answer to process of " + hashHex
                        + " and account_info failed", reply.cause != null ? reply.cause : ex);
            }

            if (Arrays.equals(now.frontier(), hash))
            {
                log.info("Light wallet: block " + hashHex + " confirmed as frontier after a failed process call");
                return succeeded(index, ready);
            }

            boolean unchanged = Arrays.equals(now.frontier(), previous);
            if (unchanged && !resent)
            {
                resent = true;
                log.warning("Light wallet: no answer to process of " + hashHex + ", frontier unchanged; resending "
                        + "the same block");
                continue;
            }

            if (!unchanged && Boolean.TRUE.equals(blockExistsQuietly(hash)))
            {
                return succeeded(index, ready);
            }

            throw unknown(index, ready, subtype, previous, "Outcome of " + subtype + " block " + hashHex
                    + " is unknown", reply.cause);
        }
    }

    private JsonObject processRequest(StateBlock block, String subtype)
    {
        JsonObject request = new JsonObject();
        request.addProperty("action", "process");
        request.addProperty("json_block", "true");
        request.addProperty("subtype", subtype);
        request.add("block", block.toRpcJson(prefix));
        return request;
    }

    private Reply process(StateBlock block, String subtype)
    {
        JsonObject request = processRequest(block, subtype);
        JsonObject response;
        try
        {
            response = rpc.call(request);
        }
        catch (IOException | RuntimeException ex)
        {
            log.log(Level.WARNING, "Light wallet: process of " + block.hashHex() + " failed: " + ex);
            return Reply.noAnswer(ex instanceof IOException ? (IOException) ex : new IOException(ex));
        }

        if (response == null)
        {
            return Reply.noAnswer(new IOException("Null process response"));
        }
        String error = errorOf(response);
        if (error != null)
        {
            return Reply.error(error);
        }
        JsonElement hash = response.get("hash");
        if (hash != null && hash.isJsonPrimitive())
        {
            return Reply.accepted(hash.getAsString());
        }
        return Reply.noAnswer(new IOException("process response has neither hash nor error: " + response));
    }

    private String succeeded(int index, StateBlock block)
    {
        if (unresolved.remove(index) != null)
        {
            clearStored(index);
        }
        discardPrecomputed(index);
        schedulePrecompute(index, block.hash());
        log.fine("Light wallet: account " + index + " published " + block.hashHex());
        return block.hashHex();
    }

    private LightWalletException rejected(int index, byte[] hash, String message)
    {
        Unresolved pending = unresolved.get(index);
        if (pending != null && pending.block != null && Arrays.equals(pending.block.hash(), hash))
        {
            removeUnresolved(index, pending);
        }
        discardPrecomputed(index);
        log.warning("Light wallet: " + message);
        return new LightWalletException(LightWalletException.Kind.REJECTED, message);
    }

    private LightWalletException unknown(int index, StateBlock block, String subtype, byte[] previous, String message,
                                         Throwable cause)
    {
        unresolved.put(index, new Unresolved(block, subtype, previous));
        try
        {
            store.save(index, processRequest(block, subtype).toString());
        }
        catch (RuntimeException ex)
        {
            log.log(Level.SEVERE, "Light wallet: could not persist block " + block.hashHex() + " of account " + index
                    + " with unknown outcome; it is only remembered until restart", ex);
        }
        log.severe("Light wallet: " + message + " (account " + index + "); it will be republished before the next "
                + "block on this account");
        return new LightWalletException(LightWalletException.Kind.UNKNOWN_OUTCOME, message, cause);
    }

    /** @return TRUE if the block is on the ledger, FALSE if definitely not, null if that could not be determined */
    private Boolean confirmPresent(int index, byte[] hash)
    {
        try
        {
            if (Arrays.equals(readAccount(index).frontier(), hash))
            {
                return Boolean.TRUE;
            }
        }
        catch (LightWalletException ex)
        {
            return null;
        }
        return blockExistsQuietly(hash);
    }

    private Boolean blockExistsQuietly(byte[] hash)
    {
        try
        {
            return blockExists(hash);
        }
        catch (LightWalletException ex)
        {
            return null;
        }
    }

    /** @return whether {@code block_info} knows the block; throws UNAVAILABLE if that cannot be determined */
    private Boolean blockExists(byte[] hash) throws LightWalletException
    {
        JsonObject request = new JsonObject();
        request.addProperty("action", "block_info");
        request.addProperty("hash", Hex.encode(hash));
        JsonObject response = call(request);
        String error = errorOf(response);
        if (error == null)
        {
            return response.has("contents") || response.has("block_account");
        }
        if (error.equalsIgnoreCase(BLOCK_NOT_FOUND))
        {
            return Boolean.FALSE;
        }
        throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE, "block_info failed: " + error);
    }

    /** Exact node answers for insufficient proof-of-work (the block was not processed; work is regenerated once). */
    static boolean isWorkError(String error)
    {
        String e = normalizeError(error);
        return e.equals("block work is insufficient") || e.equals("block work is less than threshold");
    }

    /**
     * Node verdicts that mean the block is definitely not (and will not be) on the ledger as sent. Only exact
     * {@code process} error strings of the Nano/Banano node are listed: {@code Fork}, {@code Bad signature},
     * {@code Balance and amount delta do not match}, {@code Negative spend}, {@code Unreceivable},
     * {@code Invalid block}, {@code Bad block hash} and the work errors (after one regeneration).
     *
     * <p>Deliberately NOT definitive: {@code Gap previous block} / {@code Gap source block} (the node may keep the
     * block as unchecked and apply it once the dependency arrives), {@code Old block} (the exact block already
     * exists; verified against the ledger) and every other or partially matching string. Those are checked against
     * the ledger and otherwise reported as {@code UNKNOWN_OUTCOME}, keeping the block pending.</p>
     */
    static boolean isDefinitiveRejection(String error)
    {
        switch (normalizeError(error))
        {
            case "fork":
            case "bad signature":
            case "balance and amount delta do not match":
            case "negative spend":
            case "unreceivable":
            case "invalid block":
            case "bad block hash":
                return true;
            default:
                return isWorkError(error);
        }
    }

    private static String normalizeError(String error)
    {
        String e = error == null ? "" : error.trim().toLowerCase(Locale.ROOT);
        while (e.endsWith("."))
        {
            e = e.substring(0, e.length() - 1).trim();
        }
        return e;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Proof of work
    // ------------------------------------------------------------------------------------------------------------

    /** Precomputed work for this root if present and valid, else freshly generated work. Caller holds the lock. */
    private long obtainWork(int index, byte[] root) throws LightWalletException
    {
        Precomputed entry = precomputed.get(index);
        if (entry != null)
        {
            precomputed.remove(index, entry);
            if (Arrays.equals(entry.root, root))
            {
                Long value = awaitWork(entry.future, true);
                if (value != null && Work.isValid(root, value, workThreshold))
                {
                    return value;
                }
                log.fine("Light wallet: precomputed work for account " + index + " unusable; generating");
            }
            else
            {
                entry.future.cancel(true);
            }
        }
        return generateWork(root);
    }

    private long generateWork(byte[] root) throws LightWalletException
    {
        for (int attempt = 0; attempt < 2; attempt++)
        {
            if (shutdown)
            {
                throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE, "Wallet is shut down");
            }
            CompletableFuture<Long> future;
            try
            {
                future = work.generate(root.clone(), workThreshold);
            }
            catch (RuntimeException ex)
            {
                throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE, "Work generation failed", ex);
            }
            Long value = awaitWork(future, false);
            if (value != null && Work.isValid(root, value, workThreshold))
            {
                return value;
            }
            log.warning("Light wallet: work generator returned invalid work " + value + " for root " + Hex.encode(root));
        }
        throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE, "Could not generate valid work");
    }

    /**
     * Wait for a work future. With {@code quiet} a failed future yields {@code null} (caller falls back to fresh
     * generation); otherwise failures and timeouts raise UNAVAILABLE.
     */
    private Long awaitWork(CompletableFuture<Long> future, boolean quiet) throws LightWalletException
    {
        try
        {
            return future.get(workTimeoutMillis, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException ex)
        {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE, "Interrupted waiting for work", ex);
        }
        catch (TimeoutException ex)
        {
            future.cancel(true);
            throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE, "Timed out waiting for work", ex);
        }
        catch (ExecutionException | java.util.concurrent.CancellationException ex)
        {
            if (quiet && !shutdown)
            {
                return null;
            }
            throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE, "Work generation failed", ex);
        }
    }

    /** Start generating work for {@code root} in the background. Caller holds the account lock. */
    private void schedulePrecompute(int index, byte[] root)
    {
        if (shutdown)
        {
            return;
        }
        Precomputed existing = precomputed.get(index);
        if (existing != null)
        {
            if (Arrays.equals(existing.root, root) && !existing.future.isCompletedExceptionally())
            {
                return;
            }
            precomputed.remove(index, existing);
            existing.future.cancel(true);
        }
        try
        {
            CompletableFuture<Long> future = work.generate(root.clone(), workThreshold);
            precomputed.put(index, new Precomputed(root.clone(), future));
        }
        catch (RuntimeException ex)
        {
            log.log(Level.FINE, "Light wallet: could not start work precompute for account " + index, ex);
        }
    }

    private void discardPrecomputed(int index)
    {
        Precomputed entry = precomputed.remove(index);
        if (entry != null)
        {
            entry.future.cancel(true);
        }
    }

    private void precomputeNow(int index)
    {
        ReentrantLock lock = lockFor(index);
        if (shutdown || !lock.tryLock())
        {
            return;
        }
        try
        {
            if (shutdown || unresolved.containsKey(index))
            {
                return;
            }
            AccountState state = readAccount(index);
            schedulePrecompute(index, state.opened() ? state.frontier() : publicKey(index));
        }
        catch (LightWalletException | RuntimeException ex)
        {
            log.log(Level.FINE, "Light wallet: work precompute for account " + index + " skipped", ex);
        }
        finally
        {
            lock.unlock();
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // RPC reads
    // ------------------------------------------------------------------------------------------------------------

    private AccountState readAccount(int index) throws LightWalletException
    {
        JsonObject request = new JsonObject();
        request.addProperty("action", "account_info");
        request.addProperty("account", address(index));
        request.addProperty("representative", "true");
        JsonObject response = call(request);

        String error = errorOf(response);
        if (error != null)
        {
            if (error.equalsIgnoreCase(ACCOUNT_NOT_FOUND))
            {
                return AccountState.unopened();
            }
            throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE,
                    "account_info for account " + index + " failed: " + error);
        }

        try
        {
            byte[] frontier = parseHex32(stringField(response, "frontier"));
            BigInteger balance = new BigInteger(stringField(response, "balance"));
            if (balance.signum() < 0 || balance.compareTo(MAX_BALANCE) > 0 || Arrays.equals(frontier, ZERO32))
            {
                throw new IllegalArgumentException("out of range");
            }
            JsonElement rep = response.get("representative");
            String representative = rep != null && rep.isJsonPrimitive() ? rep.getAsString() : null;
            return new AccountState(true, frontier, balance, representative);
        }
        catch (RuntimeException ex)
        {
            throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE,
                    "Malformed account_info response for account " + index + ": " + response, ex);
        }
    }

    /**
     * Amount of the send block {@code source}, after checking that it is a state send destined to account
     * {@code index}. Uses {@code block_info} with {@code json_block}: the {@code contents} are rebuilt locally and
     * must hash to {@code source}, verify with the signature of their own account and link to our public key.
     *
     * <p>The amount itself is the node-reported top-level {@code amount}: computing it locally would need the
     * sender's previous block (and so on). That is acceptable because the network enforces it exactly: a receive
     * whose balance delta differs from the real send amount is invalid everywhere ("Balance and amount delta do not
     * match"), so a lying amount can only get our block rejected, never credit or burn funds. The balance it is
     * added to comes from our own verified frontier (see {@link #verifyHead}).</p>
     */
    private BigInteger sendAmountFor(int index, byte[] source) throws LightWalletException
    {
        String sourceHex = Hex.encode(source);
        JsonObject request = new JsonObject();
        request.addProperty("action", "block_info");
        request.addProperty("hash", sourceHex);
        request.addProperty("json_block", "true");
        JsonObject response = call(request);

        String error = errorOf(response);
        if (error != null)
        {
            if (error.equalsIgnoreCase(BLOCK_NOT_FOUND))
            {
                throw new LightWalletException(LightWalletException.Kind.INVALID, "Source block " + sourceHex
                        + " not found");
            }
            throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE, "block_info failed: " + error);
        }

        JsonObject contents;
        BigInteger amount;
        try
        {
            contents = contentsOf(response);
            amount = new BigInteger(stringField(response, "amount"));
        }
        catch (RuntimeException ex)
        {
            throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE,
                    "Malformed block_info response for " + sourceHex, ex);
        }

        String type = optString(contents, "type");
        if (!"state".equals(type))
        {
            throw new LightWalletException(LightWalletException.Kind.INVALID,
                    "Source block " + sourceHex + " is not a state send (type " + type + ")");
        }
        String subtype = optString(response, "subtype");
        if (!"send".equals(subtype))
        {
            throw new LightWalletException(LightWalletException.Kind.INVALID,
                    "Source block " + sourceHex + " is not a send (subtype " + subtype + ")");
        }

        StateBlock block;
        try
        {
            block = StateBlock.fromRpcJson(contents);
        }
        catch (RuntimeException ex)
        {
            throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE,
                    "Malformed block_info contents for " + sourceHex, ex);
        }
        if (!Arrays.equals(block.hash(), source) || !block.hasValidSignature())
        {
            throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE, "block_info contents for "
                    + sourceHex + " do not match the hash or are not validly signed");
        }
        if (!Arrays.equals(block.getLink(), publicKey(index)))
        {
            throw new LightWalletException(LightWalletException.Kind.INVALID,
                    "Source block " + sourceHex + " is not destined to account " + index);
        }
        if (amount.signum() <= 0 || amount.compareTo(MAX_BALANCE) > 0)
        {
            throw new LightWalletException(LightWalletException.Kind.INVALID,
                    "Source block " + sourceHex + " has no positive amount");
        }
        return amount;
    }

    /**
     * Receivable sends for the account, largest first, as hash &rarr; amount (amount {@code null} if the node
     * did not report it). Requests {@code source=true}, whose documented shape is
     * {@code {"blocks": {"<hash>": {"amount": "<raw>", "source": "<account>"}}}}; also accepts
     * {@code {"blocks": {"<hash>": "<raw>"}}} (threshold form), {@code {"blocks": ["<hash>", ...]}} and the empty
     * {@code {"blocks": ""}}.
     */
    private Map<String, BigInteger> fetchReceivable(int index, int count) throws LightWalletException
    {
        JsonObject request = new JsonObject();
        request.addProperty("action", "receivable");
        request.addProperty("account", address(index));
        request.addProperty("count", String.valueOf(count));
        request.addProperty("source", "true");
        request.addProperty("include_active", "true");
        request.addProperty("sorting", "true");
        JsonObject response = call(request);

        String error = errorOf(response);
        if (error != null)
        {
            throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE, "receivable failed: " + error);
        }

        Map<String, BigInteger> result = new LinkedHashMap<>();
        JsonElement blocks = response.get("blocks");
        if (blocks == null || blocks.isJsonNull() || blocks.isJsonPrimitive())
        {
            return result;
        }

        if (blocks.isJsonArray())
        {
            JsonArray array = blocks.getAsJsonArray();
            for (JsonElement element : array)
            {
                if (result.size() >= count)
                {
                    break;
                }
                String hash = element.isJsonPrimitive() ? element.getAsString() : null;
                if (isHash(hash))
                {
                    result.put(hash.toUpperCase(Locale.ROOT), null);
                }
            }
            return result;
        }

        if (blocks.isJsonObject())
        {
            for (Map.Entry<String, JsonElement> entry : blocks.getAsJsonObject().entrySet())
            {
                if (result.size() >= count)
                {
                    break;
                }
                if (!isHash(entry.getKey()))
                {
                    continue;
                }
                BigInteger amount = null;
                try
                {
                    JsonElement value = entry.getValue();
                    if (value.isJsonObject() && value.getAsJsonObject().has("amount"))
                    {
                        amount = new BigInteger(value.getAsJsonObject().get("amount").getAsString());
                    }
                    else if (value.isJsonPrimitive())
                    {
                        amount = new BigInteger(value.getAsString());
                    }
                }
                catch (RuntimeException ex)
                {
                    amount = null; // looked up via block_info instead
                }
                result.put(entry.getKey().toUpperCase(Locale.ROOT), amount);
            }
        }
        return result;
    }

    private JsonObject call(JsonObject request) throws LightWalletException
    {
        try
        {
            JsonObject response = rpc.call(request);
            if (response == null)
            {
                throw new IOException("Null response");
            }
            return response;
        }
        catch (IOException | RuntimeException ex)
        {
            throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE,
                    request.get("action").getAsString() + " failed: " + ex.getMessage(), ex);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------------------------

    private void removeUnresolved(int index, Unresolved pending)
    {
        if (unresolved.remove(index, pending))
        {
            clearStored(index);
        }
    }

    private void clearStored(int index)
    {
        try
        {
            store.clear(index);
        }
        catch (RuntimeException ex)
        {
            log.log(Level.WARNING, "Light wallet: could not clear the stored unresolved block of account " + index
                    + "; it will be re-checked against the ledger after a restart", ex);
        }
    }

    /** Load blocks with unknown outcome left by a previous run; each must be republished or resolved first. */
    private void loadUnresolved()
    {
        Map<Integer, String> records;
        try
        {
            records = store.loadAll();
        }
        catch (RuntimeException ex)
        {
            throw new IllegalStateException("Could not load unresolved light-wallet blocks", ex);
        }
        if (records == null)
        {
            return;
        }
        for (Map.Entry<Integer, String> record : records.entrySet())
        {
            Integer index = record.getKey();
            if (index == null || index < 0)
            {
                log.severe("Light wallet: ignoring stored unresolved block with invalid account index " + index);
                continue;
            }
            Unresolved loaded;
            try
            {
                JsonObject json = JsonParser.parseString(record.getValue()).getAsJsonObject();
                String subtype = stringField(json, "subtype");
                if (!subtype.equals("send") && !subtype.equals("receive") && !subtype.equals("open")
                        && !subtype.equals("change"))
                {
                    throw new IllegalArgumentException("unknown subtype " + subtype);
                }
                StateBlock block = StateBlock.fromRpcJson(json.getAsJsonObject("block"));
                if (!Arrays.equals(block.getAccountPublicKey(), publicKey(index)))
                {
                    throw new IllegalArgumentException("block does not belong to this account");
                }
                if (!block.hasValidSignature() || block.getWork() == null)
                {
                    throw new IllegalArgumentException("block is not signed or has no work");
                }
                loaded = new Unresolved(block, subtype, block.getPrevious());
                log.warning("Light wallet: block " + block.hashHex() + " of account " + index + " had an unknown "
                        + "outcome before restart; it will be resolved before the next block on this account");
            }
            catch (RuntimeException ex)
            {
                loaded = Unresolved.unreadable(String.valueOf(ex.getMessage()));
                log.log(Level.SEVERE, "Light wallet: stored unresolved block of account " + index + " is unreadable;"
                        + " the account is blocked until the record is removed", ex);
            }
            unresolved.put(index, loaded);
        }
    }

    private <T> T locked(int index, LockedAction<T> action) throws LightWalletException
    {
        if (shutdown)
        {
            throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE, "Wallet is shut down");
        }
        ReentrantLock lock = lockFor(index);
        try
        {
            lock.lockInterruptibly();
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE, "Interrupted waiting for account "
                    + index, ex);
        }
        try
        {
            if (shutdown)
            {
                throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE, "Wallet is shut down");
            }
            return action.run();
        }
        catch (RuntimeException ex)
        {
            // Every path after a process call is handled explicitly, so an unexpected runtime failure happened
            // before anything was published.
            throw new LightWalletException(LightWalletException.Kind.UNAVAILABLE, "Unexpected failure: " + ex, ex);
        }
        finally
        {
            lock.unlock();
        }
    }

    private ReentrantLock lockFor(int index)
    {
        return locks.computeIfAbsent(index, k -> new ReentrantLock(true));
    }

    private byte[] publicKey(int index)
    {
        return publicKeys.computeIfAbsent(index, i ->
        {
            byte[] key = privateKey(i);
            try
            {
                return NanoKeys.publicKey(key);
            }
            finally
            {
                Arrays.fill(key, (byte) 0);
            }
        }).clone();
    }

    private byte[] privateKey(int index)
    {
        return NanoKeys.privateKey(seed, index);
    }

    private static void checkIndex(int index) throws LightWalletException
    {
        if (index < 0)
        {
            throw new LightWalletException(LightWalletException.Kind.INVALID, "Negative account index " + index);
        }
    }

    private byte[] parseAddress(String address, String what) throws LightWalletException
    {
        if (address == null || !NanoKeys.isValidAddress(address, prefix))
        {
            throw new LightWalletException(LightWalletException.Kind.INVALID, "Invalid " + what + " address: "
                    + address);
        }
        try
        {
            return NanoKeys.publicKeyFromAddress(address);
        }
        catch (IllegalArgumentException ex)
        {
            throw new LightWalletException(LightWalletException.Kind.INVALID, "Invalid " + what + " address: "
                    + address, ex);
        }
    }

    private static byte[] parseHash(String hash) throws LightWalletException
    {
        if (!isHash(hash))
        {
            throw new LightWalletException(LightWalletException.Kind.INVALID, "Invalid block hash: " + hash);
        }
        return Hex.decode(hash);
    }

    private static boolean isHash(String hash)
    {
        if (hash == null || hash.length() != 64)
        {
            return false;
        }
        for (int i = 0; i < hash.length(); i++)
        {
            if (Character.digit(hash.charAt(i), 16) < 0)
            {
                return false;
            }
        }
        return true;
    }

    private static byte[] parseHex32(String hex)
    {
        if (!isHash(hex))
        {
            throw new IllegalArgumentException("Not a 32-byte hex value: " + hex);
        }
        return Hex.decode(hex);
    }

    private static String stringField(JsonObject object, String name)
    {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonPrimitive())
        {
            throw new IllegalArgumentException("Missing field " + name);
        }
        return element.getAsString();
    }

    private static String optString(JsonObject object, String name)
    {
        JsonElement element = object.get(name);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static String errorOf(JsonObject response)
    {
        JsonElement error = response.get("error");
        if (error == null || error.isJsonNull())
        {
            return null;
        }
        return error.isJsonPrimitive() ? error.getAsString() : error.toString();
    }

    @FunctionalInterface
    private interface LockedAction<T>
    {
        T run() throws LightWalletException;
    }

    private static final class Precomputed
    {
        final byte[] root;
        final CompletableFuture<Long> future;

        Precomputed(byte[] root, CompletableFuture<Long> future)
        {
            this.root = root;
            this.future = future;
        }
    }

    /** A block with unknown outcome, or (block == null) a stored record that could not be read. */
    private static final class Unresolved
    {
        final StateBlock block;
        final String subtype;
        final byte[] previous;
        final String problem;

        Unresolved(StateBlock block, String subtype, byte[] previous)
        {
            this.block = block;
            this.subtype = subtype;
            this.previous = previous.clone();
            this.problem = null;
        }

        private Unresolved(String problem)
        {
            this.block = null;
            this.subtype = null;
            this.previous = null;
            this.problem = problem;
        }

        static Unresolved unreadable(String problem)
        {
            return new Unresolved(problem);
        }
    }

    private static final class Reply
    {
        final String hash;
        final String error;
        final IOException cause;

        private Reply(String hash, String error, IOException cause)
        {
            this.hash = hash;
            this.error = error;
            this.cause = cause;
        }

        static Reply accepted(String hash)
        {
            return new Reply(hash, null, null);
        }

        static Reply error(String error)
        {
            return new Reply(null, error, null);
        }

        static Reply noAnswer(IOException cause)
        {
            return new Reply(null, null, cause);
        }
    }
}
