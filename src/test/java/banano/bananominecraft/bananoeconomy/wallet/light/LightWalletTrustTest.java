package banano.bananominecraft.bananoeconomy.wallet.light;

import banano.bananominecraft.bananoeconomy.wallet.crypto.Hex;
import banano.bananominecraft.bananoeconomy.wallet.crypto.NanoKeys;
import banano.bananominecraft.bananoeconomy.wallet.crypto.StateBlock;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link LightWallet} against a lying node (balance, frontier, signature and representative lies must never lead to a
 * signed block built on forged values), error classification behaviour, and persistence of blocks with unknown
 * outcome across restarts via {@link PendingBlockStore}.
 */
class LightWalletTrustTest
{
    private static final long TEST_THRESHOLD = 0xf000000000000000L;
    private static final byte[] SEED = new byte[32];
    private static final byte[] OTHER_SEED = new byte[32];

    static
    {
        for (int i = 0; i < 32; i++)
        {
            SEED[i] = (byte) (i * 7 + 1);
            OTHER_SEED[i] = (byte) (0xA0 + i);
        }
    }

    private static final String DEFAULT_REP = addr(NanoKeys.privateKey(OTHER_SEED, 0));
    private static final String STRANGER = addr(NanoKeys.privateKey(OTHER_SEED, 2));
    private static final byte[] STRANGER_KEY = NanoKeys.privateKey(OTHER_SEED, 2);

    private FakeLedger ledger;
    private LightWallet wallet;
    private final List<LightWallet> extra = new ArrayList<>();
    private final SecureRandom random = new SecureRandom();

    @BeforeEach
    void setUp()
    {
        ledger = new FakeLedger(TEST_THRESHOLD);
        wallet = newWallet(PendingBlockStore.inMemory(), ledger);
    }

    @AfterEach
    void tearDown()
    {
        wallet.shutdown();
        extra.forEach(LightWallet::shutdown);
    }

    private LightWallet newWallet(PendingBlockStore store, LightRpc rpc)
    {
        LightWallet w = new LightWallet(SEED, "ban_", DEFAULT_REP, rpc, new CountingWorkGenerator(), TEST_THRESHOLD,
                Logger.getLogger("LightWalletTrustTest"), store);
        w.setWorkTimeoutMillis(20_000);
        if (wallet != null)
        {
            extra.add(w);
        }
        return w;
    }

    private static String addr(byte[] privateKey)
    {
        return NanoKeys.address(NanoKeys.publicKey(privateKey), "ban_");
    }

    private static BigInteger raw(long v)
    {
        return BigInteger.valueOf(v);
    }

    private String open(int index, long amount) throws Exception
    {
        return wallet.receive(index, ledger.fund(wallet.address(index), raw(amount)));
    }

    private static LightWalletException.Kind kindOf(ThrowingRunnable r)
    {
        return assertThrows(LightWalletException.class, r::run).kind();
    }

    @FunctionalInterface
    private interface ThrowingRunnable
    {
        void run() throws Exception;
    }

    private static boolean is(JsonObject request, String action)
    {
        return action.equals(request.get("action").getAsString());
    }

    private static boolean isInfoFor(JsonObject request, String address)
    {
        return is(request, "account_info") && address.equals(request.get("account").getAsString());
    }

    private static boolean isBlockInfo(JsonObject request, String hashHex)
    {
        return is(request, "block_info") && hashHex.equalsIgnoreCase(request.get("hash").getAsString());
    }

    private void lie(BiFunction<JsonObject, JsonObject, JsonObject> filter)
    {
        ledger.responseFilter = filter;
    }

    private String frontierHex(int index)
    {
        return Hex.encode(ledger.account(wallet.address(index)).frontier);
    }

    private JsonObject lastProcessedBlock()
    {
        synchronized (ledger.requests)
        {
            for (int i = ledger.requests.size() - 1; i >= 0; i--)
            {
                if (is(ledger.requests.get(i), "process"))
                {
                    return ledger.requests.get(i).getAsJsonObject("block");
                }
            }
        }
        fail("no process request");
        return null;
    }

    /** Every operation on account {@code index} fails UNAVAILABLE and nothing reaches {@code process}. */
    private void assertNothingSigned(int index) throws Exception
    {
        int processBefore = ledger.count("process");
        BigInteger balanceBefore = ledger.balance(wallet.address(index));
        String source = ledger.fund(wallet.address(index), raw(3));
        assertEquals(LightWalletException.Kind.UNAVAILABLE, kindOf(() -> wallet.send(index, STRANGER, raw(1))));
        assertEquals(LightWalletException.Kind.UNAVAILABLE, kindOf(() -> wallet.receive(index, source)));
        assertEquals(LightWalletException.Kind.UNAVAILABLE, kindOf(() -> wallet.receivePending(index, 5)));
        assertEquals(LightWalletException.Kind.UNAVAILABLE,
                kindOf(() -> wallet.changeRepresentative(index, STRANGER)));
        assertEquals(processBefore, ledger.count("process"), "no block may be published");
        assertEquals(balanceBefore, ledger.balance(wallet.address(index)));
        assertFalse(wallet.hasUnresolved(index));
    }

    /** Contents of a state block for {@code account}, signed by {@code signer} (which may not own the account). */
    private static StateBlock forged(String account, byte[] previous, String rep, BigInteger balance, byte[] link,
                                     byte[] signer)
    {
        StateBlock unsigned = new StateBlock(account, previous, rep, balance, link, null, null);
        return new StateBlock(account, previous, rep, balance, link, NanoKeys.sign(signer, unsigned.hash()), 0L);
    }

    private static JsonObject info(StateBlock block, String subtype, BigInteger amount)
    {
        JsonObject o = new JsonObject();
        o.addProperty("block_account", NanoKeys.address(block.getAccountPublicKey(), "ban_"));
        o.addProperty("amount", amount.toString());
        o.addProperty("balance", block.getBalance().toString());
        o.add("contents", block.toRpcJson("ban_"));
        o.addProperty("subtype", subtype);
        return o;
    }

    // ------------------------------------------------------------------------------------------ balance lies

    @Test
    void balanceLie_lowerReported_signsFromVerifiedFrontier() throws Exception
    {
        open(0, 1000);
        String me = wallet.address(0);
        lie((req, resp) ->
        {
            if (isInfoFor(req, me))
            {
                resp.addProperty("balance", "100");
            }
            return resp;
        });

        wallet.send(0, STRANGER, raw(10));
        assertEquals("990", lastProcessedBlock().get("balance").getAsString(), "balance from the verified block");
        assertEquals(raw(990), ledger.balance(me));
        // 500 > the lied 100 but within the real balance.
        wallet.send(0, STRANGER, raw(500));
        assertEquals(raw(490), ledger.balance(me));
    }

    @Test
    void balanceLie_higherReported_cannotOverspend() throws Exception
    {
        open(0, 1000);
        String me = wallet.address(0);
        lie((req, resp) ->
        {
            if (isInfoFor(req, me))
            {
                resp.addProperty("balance", "5000");
            }
            return resp;
        });
        int processBefore = ledger.count("process");
        assertEquals(LightWalletException.Kind.INSUFFICIENT_BALANCE, kindOf(() -> wallet.send(0, STRANGER, raw(2000))));
        assertEquals(processBefore, ledger.count("process"));
    }

    @Test
    void balanceLie_inFrontierContents_isUnavailable() throws Exception
    {
        open(0, 1000);
        String frontier = frontierHex(0);
        lie((req, resp) ->
        {
            if (isBlockInfo(req, frontier))
            {
                resp.getAsJsonObject("contents").addProperty("balance", "100");
                resp.addProperty("balance", "100");
            }
            return resp;
        });
        assertNothingSigned(0);
    }

    // ------------------------------------------------------------------------------------------ frontier lies

    @Test
    void frontierHashLie_unknownBlock_isUnavailable() throws Exception
    {
        open(0, 1000);
        String me = wallet.address(0);
        byte[] fake = new byte[32];
        random.nextBytes(fake);
        lie((req, resp) ->
        {
            if (isInfoFor(req, me))
            {
                resp.addProperty("frontier", Hex.encode(fake));
            }
            return resp;
        });
        assertNothingSigned(0);
    }

    @Test
    void frontierHashLie_withOtherContents_isUnavailable() throws Exception
    {
        open(0, 1000);
        String me = wallet.address(0);
        String realFrontier = frontierHex(0);
        JsonObject realInfo = ledger.call(blockInfoRequest(realFrontier));
        byte[] fake = new byte[32];
        random.nextBytes(fake);
        String fakeHex = Hex.encode(fake);
        lie((req, resp) ->
        {
            if (isInfoFor(req, me))
            {
                resp.addProperty("frontier", fakeHex);
                resp.addProperty("balance", "999999");
            }
            if (isBlockInfo(req, fakeHex))
            {
                return realInfo.deepCopy(); // genuine, signed block, but not the claimed hash
            }
            return resp;
        });
        assertNothingSigned(0);
    }

    @Test
    void foreignSignedFrontier_isUnavailable() throws Exception
    {
        open(0, 1000);
        String me = wallet.address(0);
        // A block on our account crediting a million raw, signed by someone else's key.
        StateBlock fake = forged(me, ledger.account(me).frontier.clone(), DEFAULT_REP, raw(1_000_000),
                new byte[32], STRANGER_KEY);
        lie((req, resp) ->
        {
            if (isInfoFor(req, me))
            {
                resp.addProperty("frontier", fake.hashHex());
                resp.addProperty("balance", "1000000");
            }
            if (isBlockInfo(req, fake.hashHex()))
            {
                return info(fake, "receive", raw(999_000));
            }
            return resp;
        });
        assertNothingSigned(0);
    }

    @Test
    void frontierOfAnotherAccount_isUnavailable() throws Exception
    {
        open(0, 1000);
        String me = wallet.address(0);
        // Validly signed, but by and for the stranger's account.
        byte[] previous = new byte[32];
        random.nextBytes(previous);
        StateBlock theirs = forged(STRANGER, previous, DEFAULT_REP, raw(1_000_000), new byte[32], STRANGER_KEY);
        assertTrue(theirs.hasValidSignature());
        lie((req, resp) ->
        {
            if (isInfoFor(req, me))
            {
                resp.addProperty("frontier", theirs.hashHex());
                resp.addProperty("balance", "1000000");
            }
            if (isBlockInfo(req, theirs.hashHex()))
            {
                return info(theirs, "change", raw(0));
            }
            return resp;
        });
        assertNothingSigned(0);
    }

    @Test
    void legacyFrontierBlock_isUnavailable() throws Exception
    {
        open(0, 1000);
        String frontier = frontierHex(0);
        lie((req, resp) ->
        {
            if (isBlockInfo(req, frontier))
            {
                resp.getAsJsonObject("contents").addProperty("type", "receive");
            }
            return resp;
        });
        LightWalletException ex = assertThrows(LightWalletException.class, () -> wallet.send(0, STRANGER, raw(1)));
        assertEquals(LightWalletException.Kind.UNAVAILABLE, ex.kind());
        assertTrue(ex.getMessage().contains("legacy"), ex.getMessage());
        assertNothingSigned(0);
    }

    @Test
    void frontierBlockInfoUnavailable_isUnavailable() throws Exception
    {
        open(0, 1000);
        String frontier = frontierHex(0);
        lie((req, resp) ->
        {
            if (isBlockInfo(req, frontier))
            {
                JsonObject o = new JsonObject();
                o.addProperty("error", "Internal server error");
                return o;
            }
            return resp;
        });
        assertNothingSigned(0);
    }

    // ------------------------------------------------------------------------------------------ representative lies

    @Test
    void representativeLie_inAccountInfo_isIgnored() throws Exception
    {
        open(0, 1000);
        String me = wallet.address(0);
        lie((req, resp) ->
        {
            if (isInfoFor(req, me))
            {
                resp.addProperty("representative", STRANGER);
            }
            return resp;
        });
        wallet.send(0, STRANGER, raw(10));
        assertEquals(DEFAULT_REP, lastProcessedBlock().get("representative").getAsString());
    }

    @Test
    void representativeLie_inFrontierContents_isUnavailable() throws Exception
    {
        open(0, 1000);
        String frontier = frontierHex(0);
        lie((req, resp) ->
        {
            if (isBlockInfo(req, frontier))
            {
                resp.getAsJsonObject("contents").addProperty("representative", STRANGER);
            }
            return resp;
        });
        assertNothingSigned(0);
    }

    // ------------------------------------------------------------------------------------------ source block lies

    @Test
    void sourceLie_redirectedLink_isUnavailable() throws Exception
    {
        open(0, 1000);
        String source = ledger.fund(STRANGER, raw(500));
        String myKey = Hex.encode(NanoKeys.publicKeyFromAddress(wallet.address(0)));
        lie((req, resp) ->
        {
            if (isBlockInfo(req, source))
            {
                resp.getAsJsonObject("contents").addProperty("link", myKey);
            }
            return resp;
        });
        int processBefore = ledger.count("process");
        assertEquals(LightWalletException.Kind.UNAVAILABLE, kindOf(() -> wallet.receive(0, source)));
        assertEquals(processBefore, ledger.count("process"));
    }

    @Test
    void sourceLie_forgedSignature_isUnavailable() throws Exception
    {
        open(0, 1000);
        byte[] myKey = NanoKeys.publicKeyFromAddress(wallet.address(0));
        byte[] previous = new byte[32];
        random.nextBytes(previous);
        String victim = addr(NanoKeys.privateKey(OTHER_SEED, 7));
        // "Send" from the victim to us, signed by the stranger.
        StateBlock fake = forged(victim, previous, DEFAULT_REP, raw(0), myKey, STRANGER_KEY);
        lie((req, resp) -> isBlockInfo(req, fake.hashHex()) ? info(fake, "send", raw(5000)) : resp);
        int processBefore = ledger.count("process");
        assertEquals(LightWalletException.Kind.UNAVAILABLE, kindOf(() -> wallet.receive(0, fake.hashHex())));
        assertEquals(processBefore, ledger.count("process"));
    }

    @Test
    void sourceLie_amount_isRejectedByNodeNeverCredited() throws Exception
    {
        String source = ledger.fund(wallet.address(0), raw(10));
        lie((req, resp) ->
        {
            if (isBlockInfo(req, source))
            {
                resp.addProperty("amount", "1000");
            }
            return resp;
        });
        LightWalletException ex = assertThrows(LightWalletException.class, () -> wallet.receive(0, source));
        assertEquals(LightWalletException.Kind.REJECTED, ex.kind());
        assertTrue(ex.getMessage().contains("Balance and amount delta"), ex.getMessage());
        assertNull(ledger.account(wallet.address(0)));
        assertFalse(wallet.hasUnresolved(0));
    }

    @Test
    void receivableAmountDisagreesWithBlockInfo_isUnavailable() throws Exception
    {
        String source = ledger.fund(wallet.address(0), raw(10));
        lie((req, resp) ->
        {
            if (is(req, "receivable"))
            {
                resp.getAsJsonObject("blocks").getAsJsonObject(source).addProperty("amount", "999");
            }
            return resp;
        });
        assertEquals(LightWalletException.Kind.UNAVAILABLE, kindOf(() -> wallet.receivePending(0, 5)));
        assertEquals(0, ledger.count("process"));
    }

    // ------------------------------------------------------------------------------------------ classification

    @Test
    void gapAnswer_isUnknownOutcome_keptAndRepublished() throws Exception
    {
        open(0, 100);
        ledger.addFaults(FakeLedger.Fault.GAP);
        LightWalletException ex = assertThrows(LightWalletException.class, () -> wallet.send(0, STRANGER, raw(10)));
        assertEquals(LightWalletException.Kind.UNKNOWN_OUTCOME, ex.kind());
        assertTrue(wallet.hasUnresolved(0));
        String gapHash = ledger.processAttempts.get(ledger.processAttempts.size() - 1);

        int attempts = ledger.processAttempts.size();
        wallet.send(0, STRANGER, raw(5));
        assertEquals(gapHash, ledger.processAttempts.get(attempts), "the same block is republished first");
        assertTrue(ledger.blocks.containsKey(gapHash));
        assertEquals(raw(85), ledger.balance(wallet.address(0)));
        assertFalse(wallet.hasUnresolved(0));
    }

    @Test
    void unrecognisedInvalidAnswer_isUnknownOutcomeNotRejected() throws Exception
    {
        open(0, 100);
        ledger.addFaults(FakeLedger.Fault.ODD_INVALID);
        assertEquals(LightWalletException.Kind.UNKNOWN_OUTCOME, kindOf(() -> wallet.send(0, STRANGER, raw(10))));
        assertTrue(wallet.hasUnresolved(0));
        // Republish fails the same way: the account stays blocked, no different block is built.
        ledger.addFaults(FakeLedger.Fault.ODD_INVALID);
        assertEquals(LightWalletException.Kind.UNAVAILABLE, kindOf(() -> wallet.send(0, STRANGER, raw(3))));
        assertTrue(wallet.hasUnresolved(0));
        assertEquals(raw(100), ledger.balance(wallet.address(0)));
    }

    // ------------------------------------------------------------------------------------------ persistence

    @Test
    void unknownOutcome_isPersisted_andRepublishedAfterRestart() throws Exception
    {
        PendingBlockStore store = PendingBlockStore.inMemory();
        LightWallet first = newWallet(store, ledger);
        first.receive(0, ledger.fund(first.address(0), raw(100)));
        assertTrue(store.loadAll().isEmpty());

        ledger.addFaults(FakeLedger.Fault.FAIL_BEFORE, FakeLedger.Fault.FAIL_BEFORE);
        assertEquals(LightWalletException.Kind.UNKNOWN_OUTCOME, kindOf(() -> first.send(0, STRANGER, raw(30))));
        String unknownHash = ledger.processAttempts.get(ledger.processAttempts.size() - 1);

        Map<Integer, String> saved = store.loadAll();
        assertEquals(1, saved.size());
        JsonObject record = JsonParser.parseString(saved.get(0)).getAsJsonObject();
        assertEquals("send", record.get("subtype").getAsString());
        assertEquals(unknownHash, StateBlock.fromRpcJson(record.getAsJsonObject("block")).hashHex());
        first.shutdown();

        // "Restart": a fresh wallet must settle the stored block before building anything else.
        LightWallet second = newWallet(store, ledger);
        assertTrue(second.hasUnresolved(0));
        int attempts = ledger.processAttempts.size();
        second.send(0, STRANGER, raw(20));
        assertEquals(unknownHash, ledger.processAttempts.get(attempts));
        assertEquals(raw(50), ledger.balance(second.address(0)));
        assertFalse(second.hasUnresolved(0));
        assertTrue(store.loadAll().isEmpty(), "cleared once resolved");
    }

    @Test
    void persistedBlockThatLanded_isResolvedFromFrontierAfterRestart() throws Exception
    {
        PendingBlockStore store = PendingBlockStore.inMemory();
        LightWallet first = newWallet(store, ledger);
        first.receive(0, ledger.fund(first.address(0), raw(100)));
        ledger.addFaults(FakeLedger.Fault.FAIL_AFTER_AND_NEXT_INFO);
        assertEquals(LightWalletException.Kind.UNKNOWN_OUTCOME, kindOf(() -> first.send(0, STRANGER, raw(10))));
        assertEquals(1, store.loadAll().size());
        first.shutdown();

        LightWallet second = newWallet(store, ledger);
        int processBefore = ledger.count("process");
        second.send(0, STRANGER, raw(5));
        assertEquals(processBefore + 1, ledger.count("process"), "resolved via frontier, not republished");
        assertEquals(raw(85), ledger.balance(second.address(0)));
        assertTrue(store.loadAll().isEmpty());
    }

    @Test
    void unreadableOrForeignStoredRecord_blocksOnlyThatAccount() throws Exception
    {
        open(0, 100);
        open(1, 100);
        open(2, 100);
        // A genuine record of account 0 placed under index 1 must not be trusted for account 1.
        ledger.addFaults(FakeLedger.Fault.FAIL_BEFORE, FakeLedger.Fault.FAIL_BEFORE);
        PendingBlockStore source = PendingBlockStore.inMemory();
        LightWallet recorder = newWallet(source, ledger);
        assertEquals(LightWalletException.Kind.UNKNOWN_OUTCOME, kindOf(() -> recorder.send(0, STRANGER, raw(1))));
        String account0Record = source.loadAll().get(0);
        recorder.shutdown();

        PendingBlockStore store = PendingBlockStore.inMemory();
        store.save(1, account0Record);
        store.save(2, "{not json");
        LightWallet restarted = newWallet(store, ledger);
        assertTrue(restarted.hasUnresolved(1));
        assertTrue(restarted.hasUnresolved(2));

        int processBefore = ledger.count("process");
        assertEquals(LightWalletException.Kind.UNAVAILABLE, kindOf(() -> restarted.send(1, STRANGER, raw(1))));
        assertEquals(LightWalletException.Kind.UNAVAILABLE, kindOf(() -> restarted.send(2, STRANGER, raw(1))));
        assertEquals(processBefore, ledger.count("process"));
        assertEquals(2, store.loadAll().size(), "records stay until an operator removes them");

        // Account 3 is unaffected.
        restarted.receive(3, ledger.fund(restarted.address(3), raw(4)));
        assertEquals(raw(4), ledger.balance(restarted.address(3)));
    }

    @Test
    void storeSaveFailure_stillHoldsTheBlockInMemory() throws Exception
    {
        PendingBlockStore failing = new PendingBlockStore()
        {
            @Override
            public void save(int index, String blockJson)
            {
                throw new IllegalStateException("disk full");
            }

            @Override
            public void clear(int index)
            {
            }

            @Override
            public Map<Integer, String> loadAll()
            {
                return Map.of();
            }
        };
        LightWallet w = newWallet(failing, ledger);
        w.receive(0, ledger.fund(w.address(0), raw(100)));
        ledger.addFaults(FakeLedger.Fault.FAIL_BEFORE, FakeLedger.Fault.FAIL_BEFORE);
        assertEquals(LightWalletException.Kind.UNKNOWN_OUTCOME, kindOf(() -> w.send(0, STRANGER, raw(10))));
        assertTrue(w.hasUnresolved(0));
    }

    @Test
    void storeLoadFailure_failsConstruction()
    {
        PendingBlockStore broken = new PendingBlockStore()
        {
            @Override
            public void save(int index, String blockJson)
            {
            }

            @Override
            public void clear(int index)
            {
            }

            @Override
            public Map<Integer, String> loadAll()
            {
                throw new IllegalStateException("unreadable file");
            }
        };
        assertThrows(IllegalStateException.class, () -> newWallet(broken, ledger));
    }

    // ------------------------------------------------------------------------------------------ precompute

    @Test
    void precomputeWork_neverBlocksTheCaller() throws Exception
    {
        CountDownLatch release = new CountDownLatch(1);
        LightRpc stuck = request ->
        {
            try
            {
                release.await(10, TimeUnit.SECONDS);
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
                throw new IOException(ex);
            }
            return ledger.call(request);
        };
        LightWallet w = newWallet(PendingBlockStore.inMemory(), stuck);
        long start = System.nanoTime();
        for (int i = 0; i < 1000; i++)
        {
            w.precomputeWork(i % 5);
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        release.countDown();
        assertTrue(elapsedMs < 1000, "precomputeWork took " + elapsedMs + " ms");
    }

    private static JsonObject blockInfoRequest(String hash)
    {
        JsonObject request = new JsonObject();
        request.addProperty("action", "block_info");
        request.addProperty("hash", hash);
        request.addProperty("json_block", "true");
        return request;
    }
}
