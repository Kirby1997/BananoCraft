package banano.bananominecraft.bananoeconomy.wallet.light;

import banano.bananominecraft.bananoeconomy.wallet.crypto.CpuWorkGenerator;
import banano.bananominecraft.bananoeconomy.wallet.crypto.Hex;
import banano.bananominecraft.bananoeconomy.wallet.crypto.NanoKeys;
import banano.bananominecraft.bananoeconomy.wallet.crypto.StateBlock;
import banano.bananominecraft.bananoeconomy.wallet.crypto.Work;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link LightWallet} against {@link FakeLedger}, an in-memory ledger that validates signatures (with the
 * real ed25519-blake2b), work (with a low test threshold), frontiers and balance deltas.
 */
class LightWalletTest
{
    /** 1 in 16 nonces pass: cheap to brute-force, still checked by the fake node. */
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

    private static final String DEFAULT_REP = NanoKeys.address(NanoKeys.publicKey(NanoKeys.privateKey(OTHER_SEED, 0)),
            "ban_");
    private static final String OTHER_REP = NanoKeys.address(NanoKeys.publicKey(NanoKeys.privateKey(OTHER_SEED, 1)),
            "ban_");
    private static final String STRANGER = NanoKeys.address(NanoKeys.publicKey(NanoKeys.privateKey(OTHER_SEED, 2)),
            "ban_");

    private FakeLedger ledger;
    private CountingWorkGenerator work;
    private LightWallet wallet;

    @BeforeEach
    void setUp()
    {
        ledger = new FakeLedger(TEST_THRESHOLD);
        work = new CountingWorkGenerator();
        wallet = new LightWallet(SEED, "ban_", DEFAULT_REP, ledger, work, TEST_THRESHOLD,
                Logger.getLogger("LightWalletTest"));
        wallet.setWorkTimeoutMillis(20_000);
    }

    @AfterEach
    void tearDown()
    {
        wallet.shutdown();
    }

    private static BigInteger raw(long v)
    {
        return BigInteger.valueOf(v);
    }

    /** Open account {@code index} with {@code amount} raw via an external send + receive. */
    private String open(int index, long amount) throws Exception
    {
        String source = ledger.fund(wallet.address(index), raw(amount));
        return wallet.receive(index, source);
    }

    private static void waitFor(BooleanSupplier condition) throws InterruptedException
    {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean())
        {
            assertTrue(System.nanoTime() < deadline, "condition not reached in time");
            Thread.sleep(5);
        }
    }

    private static LightWalletException.Kind kindOf(ThrowingRunnable r)
    {
        LightWalletException ex = assertThrows(LightWalletException.class, r::run);
        return ex.kind();
    }

    @FunctionalInterface
    private interface ThrowingRunnable
    {
        void run() throws Exception;
    }

    // ------------------------------------------------------------------------------------------ basics

    @Test
    void address_matchesKeyDerivation()
    {
        String expected = NanoKeys.address(NanoKeys.publicKey(NanoKeys.privateKey(SEED, 5)), "ban_");
        assertEquals(expected, wallet.address(5));
        assertTrue(NanoKeys.isValidAddress(wallet.address(0), "ban_"));
        assertThrows(IllegalArgumentException.class, () -> wallet.address(-1));
    }

    @Test
    void accountState_unopenedAccount() throws Exception
    {
        AccountState state = wallet.accountState(3);
        assertFalse(state.opened());
        assertEquals(BigInteger.ZERO, state.balance());
        assertArrayEquals(new byte[32], state.frontier());
        assertNull(state.representative());
    }

    @Test
    void receive_opensAccountWithDefaultRepresentative() throws Exception
    {
        String hash = open(1, 1000);

        FakeLedger.Account account = ledger.account(wallet.address(1));
        assertNotNull(account);
        assertEquals(hash, Hex.encode(account.frontier));
        assertEquals(raw(1000), account.balance);
        assertEquals(DEFAULT_REP, NanoKeys.address(account.representative, "ban_"));

        AccountState state = wallet.accountState(1);
        assertTrue(state.opened());
        assertEquals(raw(1000), state.balance());
        assertEquals(DEFAULT_REP, state.representative());

        // The open block was published with subtype "open" and previous = 0.
        JsonObject process = lastRequest("process");
        assertEquals("open", process.get("subtype").getAsString());
        assertEquals("true", process.get("json_block").getAsString());
        assertEquals(Hex.encode(new byte[32]), process.getAsJsonObject("block").get("previous").getAsString());
    }

    @Test
    void receive_onOpenedAccountIsStateReceive() throws Exception
    {
        open(0, 100);
        String source = ledger.fund(wallet.address(0), raw(50));
        String hash = wallet.receive(0, source);

        assertEquals(raw(150), ledger.balance(wallet.address(0)));
        assertEquals(hash, Hex.encode(ledger.account(wallet.address(0)).frontier));
        assertEquals("receive", lastRequest("process").get("subtype").getAsString());
    }

    @Test
    void receive_rejectsSendForAnotherAccount() throws Exception
    {
        String source = ledger.fund(STRANGER, raw(10));
        assertEquals(LightWalletException.Kind.INVALID, kindOf(() -> wallet.receive(0, source)));
        assertEquals(0, ledger.count("process"));
    }

    @Test
    void receive_unknownOrMalformedHashIsInvalid()
    {
        assertEquals(LightWalletException.Kind.INVALID, kindOf(() -> wallet.receive(0, "XYZ")));
        assertEquals(LightWalletException.Kind.INVALID, kindOf(() -> wallet.receive(0, Hex.encode(new byte[32]))));
    }

    @Test
    void receive_alreadyReceivedIsRejectedNotSuccess() throws Exception
    {
        String source = ledger.fund(wallet.address(0), raw(10));
        wallet.receive(0, source);
        assertEquals(LightWalletException.Kind.REJECTED, kindOf(() -> wallet.receive(0, source)));
        assertEquals(raw(10), ledger.balance(wallet.address(0)));
        assertFalse(wallet.hasUnresolved(0));
    }

    @Test
    void send_movesFundsAndLinksDestinationKey() throws Exception
    {
        open(0, 1000);
        String hash = wallet.send(0, STRANGER, raw(400));

        assertEquals(raw(600), ledger.balance(wallet.address(0)));
        FakeLedger.Pending p = ledger.pending.get(hash);
        assertNotNull(p, "send must create a receivable entry");
        assertArrayEquals(NanoKeys.publicKeyFromAddress(STRANGER), p.destination);
        assertEquals(raw(400), p.amount);

        JsonObject process = lastRequest("process");
        assertEquals("send", process.get("subtype").getAsString());
        assertEquals(STRANGER, process.getAsJsonObject("block").get("link_as_account").getAsString());
        assertEquals(DEFAULT_REP, process.getAsJsonObject("block").get("representative").getAsString());
    }

    @Test
    void send_toOwnAccountThenReceive() throws Exception
    {
        open(0, 1000);
        String sendHash = wallet.send(0, wallet.address(1), raw(250));
        wallet.receive(1, sendHash);
        assertEquals(raw(750), ledger.balance(wallet.address(0)));
        assertEquals(raw(250), ledger.balance(wallet.address(1)));
    }

    @Test
    void send_insufficientBalance() throws Exception
    {
        open(0, 100);
        int before = ledger.count("process");
        assertEquals(LightWalletException.Kind.INSUFFICIENT_BALANCE, kindOf(() -> wallet.send(0, STRANGER, raw(101))));
        assertEquals(LightWalletException.Kind.INSUFFICIENT_BALANCE, kindOf(() -> wallet.send(7, STRANGER, raw(1))));
        assertEquals(before, ledger.count("process"));
        assertEquals(raw(100), ledger.balance(wallet.address(0)));

        // Exactly the whole balance is allowed.
        wallet.send(0, STRANGER, raw(100));
        assertEquals(BigInteger.ZERO, ledger.balance(wallet.address(0)));
    }

    @Test
    void send_invalidArguments()
    {
        assertEquals(LightWalletException.Kind.INVALID, kindOf(() -> wallet.send(0, "ban_notanaddress", raw(1))));
        assertEquals(LightWalletException.Kind.INVALID, kindOf(() -> wallet.send(0, null, raw(1))));
        String nano = "nano_" + STRANGER.substring(4);
        assertEquals(LightWalletException.Kind.INVALID, kindOf(() -> wallet.send(0, nano, raw(1))));
        assertEquals(LightWalletException.Kind.INVALID, kindOf(() -> wallet.send(0, STRANGER, BigInteger.ZERO)));
        assertEquals(LightWalletException.Kind.INVALID, kindOf(() -> wallet.send(0, STRANGER, raw(-5))));
        assertEquals(LightWalletException.Kind.INVALID, kindOf(() -> wallet.send(0, STRANGER, null)));
        assertEquals(LightWalletException.Kind.INVALID, kindOf(() -> wallet.send(-1, STRANGER, raw(1))));
        assertEquals(0, ledger.count("account_info"));
    }

    @Test
    void changeRepresentative_publishesChangeBlock() throws Exception
    {
        open(0, 500);
        String hash = wallet.changeRepresentative(0, OTHER_REP);

        FakeLedger.Account account = ledger.account(wallet.address(0));
        assertEquals(OTHER_REP, NanoKeys.address(account.representative, "ban_"));
        assertEquals(raw(500), account.balance);
        assertEquals(hash, Hex.encode(account.frontier));
        assertEquals("change", lastRequest("process").get("subtype").getAsString());

        // Later blocks keep the new representative.
        wallet.send(0, STRANGER, raw(1));
        assertEquals(OTHER_REP, lastRequest("process").getAsJsonObject("block").get("representative").getAsString());
    }

    @Test
    void changeRepresentative_unopenedOrInvalid()
    {
        assertEquals(LightWalletException.Kind.INVALID, kindOf(() -> wallet.changeRepresentative(0, OTHER_REP)));
        assertEquals(LightWalletException.Kind.INVALID, kindOf(() -> wallet.changeRepresentative(0, "ban_bad")));
    }

    // ------------------------------------------------------------------------------------------ concurrency

    @Test
    void concurrentSends_fromOneAccount_noForksExactBalance() throws Exception
    {
        open(0, 10_000);
        int threads = 8;
        int perThread = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<List<String>>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++)
        {
            final int amount = t + 1;
            futures.add(pool.submit(() ->
            {
                start.await();
                List<String> hashes = new ArrayList<>();
                for (int i = 0; i < perThread; i++)
                {
                    hashes.add(wallet.send(0, STRANGER, raw(amount)));
                }
                return hashes;
            }));
        }
        start.countDown();

        List<String> all = new ArrayList<>();
        long expectedSpent = 0;
        for (int t = 0; t < threads; t++)
        {
            all.addAll(futures.get(t).get(60, TimeUnit.SECONDS));
            expectedSpent += (long) (t + 1) * perThread;
        }
        pool.shutdownNow();

        assertEquals(threads * perThread, new HashSet<>(all).size(), "every send is a distinct block");
        assertEquals(raw(10_000 - expectedSpent), ledger.balance(wallet.address(0)));
        assertTrue(ledger.processErrors.isEmpty(), "no fork/gap: " + ledger.processErrors);
        assertEquals(threads * perThread + 1, ledger.count("process"));
    }

    @Test
    void concurrentSends_overspendIsRefusedExactly() throws Exception
    {
        open(0, 10);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < 12; i++)
        {
            futures.add(pool.submit(() ->
            {
                try
                {
                    wallet.send(0, STRANGER, raw(3));
                    return true;
                }
                catch (LightWalletException ex)
                {
                    assertEquals(LightWalletException.Kind.INSUFFICIENT_BALANCE, ex.kind());
                    return false;
                }
            }));
        }
        int ok = 0;
        for (Future<Boolean> f : futures)
        {
            ok += f.get(60, TimeUnit.SECONDS) ? 1 : 0;
        }
        pool.shutdownNow();
        assertEquals(3, ok);
        assertEquals(raw(1), ledger.balance(wallet.address(0)));
        assertTrue(ledger.processErrors.isEmpty());
    }

    // ------------------------------------------------------------------------------------------ outcome handling

    @Test
    void timeoutAfterProcess_detectedAsSuccess() throws Exception
    {
        open(0, 100);
        int before = ledger.count("process");
        ledger.addFaults(FakeLedger.Fault.FAIL_AFTER);

        String hash = wallet.send(0, STRANGER, raw(30));

        assertEquals(hash, Hex.encode(ledger.account(wallet.address(0)).frontier));
        assertEquals(raw(70), ledger.balance(wallet.address(0)));
        assertEquals(before + 1, ledger.count("process"), "must not resend once the frontier shows the block");
        assertFalse(wallet.hasUnresolved(0));
    }

    @Test
    void timeoutBeforeProcess_resendsSameBlock() throws Exception
    {
        open(0, 100);
        int attemptsBefore = ledger.processAttempts.size();
        ledger.addFaults(FakeLedger.Fault.FAIL_BEFORE);

        String hash = wallet.send(0, STRANGER, raw(30));

        List<String> attempts = ledger.processAttempts.subList(attemptsBefore, ledger.processAttempts.size());
        assertEquals(Arrays.asList(hash, hash), attempts, "the same signed block (same hash) is resent once");
        assertEquals(raw(70), ledger.balance(wallet.address(0)));
    }

    @Test
    void timeoutOnOpen_resendsSameOpenBlock() throws Exception
    {
        String source = ledger.fund(wallet.address(2), raw(9));
        ledger.addFaults(FakeLedger.Fault.FAIL_BEFORE);
        String hash = wallet.receive(2, source);
        assertEquals(Arrays.asList(hash, hash), ledger.processAttempts);
        assertEquals(raw(9), ledger.balance(wallet.address(2)));
    }

    @Test
    void timeoutTwice_isUnknownOutcome_thenRepublishedBeforeNextBlock() throws Exception
    {
        open(0, 100);
        int attemptsBefore = ledger.processAttempts.size();
        ledger.addFaults(FakeLedger.Fault.FAIL_BEFORE, FakeLedger.Fault.FAIL_BEFORE);

        LightWalletException ex = assertThrows(LightWalletException.class, () -> wallet.send(0, STRANGER, raw(30)));
        assertEquals(LightWalletException.Kind.UNKNOWN_OUTCOME, ex.kind());
        assertEquals(raw(100), ledger.balance(wallet.address(0)));
        assertTrue(wallet.hasUnresolved(0));
        String unknownHash = ledger.processAttempts.get(attemptsBefore);
        assertEquals(unknownHash, ledger.processAttempts.get(attemptsBefore + 1));

        // The next operation first completes the unknown block (same hash), then builds on top of it.
        String second = wallet.send(0, STRANGER, raw(20));
        assertFalse(wallet.hasUnresolved(0));
        assertEquals(unknownHash, ledger.processAttempts.get(attemptsBefore + 2));
        assertTrue(ledger.blocks.containsKey(unknownHash));
        assertEquals(second, Hex.encode(ledger.account(wallet.address(0)).frontier));
        assertEquals(raw(50), ledger.balance(wallet.address(0)));
        assertTrue(ledger.processErrors.isEmpty());
    }

    @Test
    void unknownOutcome_whenAccountInfoAlsoFails_thenResolvedFromFrontier() throws Exception
    {
        open(0, 100);
        ledger.addFaults(FakeLedger.Fault.FAIL_AFTER_AND_NEXT_INFO);

        assertEquals(LightWalletException.Kind.UNKNOWN_OUTCOME, kindOf(() -> wallet.send(0, STRANGER, raw(10))));
        assertTrue(wallet.hasUnresolved(0));
        assertEquals(raw(90), ledger.balance(wallet.address(0)), "the block did land");

        int processBefore = ledger.count("process");
        wallet.send(0, STRANGER, raw(5));
        assertEquals(processBefore + 1, ledger.count("process"), "resolved via frontier, not republished");
        assertFalse(wallet.hasUnresolved(0));
        assertEquals(raw(85), ledger.balance(wallet.address(0)));
    }

    @Test
    void unknownOutcome_republishFailsAgain_newOperationRefused() throws Exception
    {
        open(0, 100);
        ledger.addFaults(FakeLedger.Fault.FAIL_BEFORE, FakeLedger.Fault.FAIL_BEFORE);
        assertEquals(LightWalletException.Kind.UNKNOWN_OUTCOME, kindOf(() -> wallet.send(0, STRANGER, raw(10))));

        ledger.addFaults(FakeLedger.Fault.FAIL_BEFORE, FakeLedger.Fault.FAIL_BEFORE);
        assertEquals(LightWalletException.Kind.UNAVAILABLE, kindOf(() -> wallet.send(0, STRANGER, raw(7))));
        assertTrue(wallet.hasUnresolved(0));
        assertEquals(raw(100), ledger.balance(wallet.address(0)));
        // No block other than the unknown one was ever offered on this frontier.
        assertEquals(1, new HashSet<>(ledger.processAttempts.subList(1, ledger.processAttempts.size())).size());
    }

    @Test
    void unrecognisedErrorButBlockOnLedger_isSuccess() throws Exception
    {
        open(0, 100);
        ledger.addFaults(FakeLedger.Fault.APPLY_AND_ODD_ERROR);
        String hash = wallet.send(0, STRANGER, raw(10));
        assertEquals(hash, Hex.encode(ledger.account(wallet.address(0)).frontier));
    }

    @Test
    void fork_isRejectedNeverSuccess() throws Exception
    {
        open(0, 100);
        // Another signer (same seed elsewhere) appends a block between our account_info and process.
        ledger.beforeNextProcess = () -> externalChange(0, OTHER_REP);

        LightWalletException ex = assertThrows(LightWalletException.class, () -> wallet.send(0, STRANGER, raw(10)));
        assertEquals(LightWalletException.Kind.REJECTED, ex.kind());
        assertTrue(ex.getMessage().contains("Fork"), ex.getMessage());
        assertEquals(raw(100), ledger.balance(wallet.address(0)));
        assertFalse(wallet.hasUnresolved(0));

        // The next send builds on the real frontier.
        wallet.send(0, STRANGER, raw(10));
        assertEquals(raw(90), ledger.balance(wallet.address(0)));
    }

    @Test
    void insufficientWorkAnswer_regeneratesOnce() throws Exception
    {
        open(0, 100);
        byte[] root = ledger.account(wallet.address(0)).frontier.clone();
        ledger.addFaults(FakeLedger.Fault.REJECT_WORK);
        String hash = wallet.send(0, STRANGER, raw(10));
        assertEquals(hash, Hex.encode(ledger.account(wallet.address(0)).frontier));
        assertTrue(work.count(root) >= 2, "work regenerated for the same root");

        byte[] root2 = ledger.account(wallet.address(0)).frontier.clone();
        ledger.addFaults(FakeLedger.Fault.REJECT_WORK, FakeLedger.Fault.REJECT_WORK);
        assertEquals(LightWalletException.Kind.REJECTED, kindOf(() -> wallet.send(0, STRANGER, raw(10))));
        assertEquals(raw(90), ledger.balance(wallet.address(0)));
        assertTrue(work.count(root2) >= 2);
    }

    @Test
    void nodeUnreachable_beforePublishing_isUnavailable() throws Exception
    {
        LightWallet broken = new LightWallet(SEED, "ban_", DEFAULT_REP, request ->
        {
            throw new IOException("down");
        }, new CountingWorkGenerator(), TEST_THRESHOLD, Logger.getLogger("t"));
        try
        {
            assertEquals(LightWalletException.Kind.UNAVAILABLE, kindOf(() -> broken.send(0, STRANGER, raw(1))));
            assertEquals(LightWalletException.Kind.UNAVAILABLE, kindOf(() -> broken.accountState(0)));
        }
        finally
        {
            broken.shutdown();
        }
    }

    // ------------------------------------------------------------------------------------------ work

    @Test
    void precomputedWork_isReusedForNextBlock() throws Exception
    {
        String openHash = open(0, 100);
        byte[] nextRoot = Hex.decode(openHash);
        waitFor(() -> work.count(nextRoot) == 1);
        assertArrayEquals(nextRoot, wallet.precomputedRoot(0));

        wallet.send(0, STRANGER, raw(1));
        assertEquals(1, work.count(nextRoot), "precomputed work reused, not regenerated");
    }

    @Test
    void precomputeWork_publicApi_forUnopenedAccount() throws Exception
    {
        byte[] pub = NanoKeys.publicKeyFromAddress(wallet.address(4));
        wallet.precomputeWork(4);
        waitFor(() -> work.count(pub) == 1);
        open(4, 10);
        assertEquals(1, work.count(pub), "open block used the precomputed work (root = account key)");
    }

    @Test
    void precomputedWork_discardedWhenFrontierMoved() throws Exception
    {
        String openHash = open(0, 100);
        byte[] staleRoot = Hex.decode(openHash);
        waitFor(() -> work.count(staleRoot) == 1);

        StateBlock external = externalChange(0, OTHER_REP);
        wallet.send(0, STRANGER, raw(1));

        assertEquals(1, work.count(external.hash()), "fresh work generated for the moved frontier");
        assertEquals(raw(99), ledger.balance(wallet.address(0)));
        assertTrue(ledger.processErrors.isEmpty());
    }

    @Test
    void realCpuWorkGenerator_endToEnd() throws Exception
    {
        LightWallet cpuWallet = new LightWallet(SEED, "ban_", DEFAULT_REP, ledger, new CpuWorkGenerator(2),
                TEST_THRESHOLD, Logger.getLogger("t"));
        try
        {
            String source = ledger.fund(cpuWallet.address(9), raw(77));
            cpuWallet.receive(9, source);
            cpuWallet.send(9, STRANGER, raw(7));
            assertEquals(raw(70), ledger.balance(cpuWallet.address(9)));
        }
        finally
        {
            cpuWallet.shutdown();
        }
    }

    // ------------------------------------------------------------------------------------------ receivePending

    @Test
    void receivePending_capsAt25_largestFirst() throws Exception
    {
        long total = 0;
        for (int i = 1; i <= 30; i++)
        {
            ledger.fund(wallet.address(0), raw(i));
            total += i;
        }

        List<String> first = wallet.receivePending(0, 100);
        assertEquals(25, first.size());
        assertEquals("25", lastRequest("receivable").get("count").getAsString());
        assertEquals("true", lastRequest("receivable").get("source").getAsString());
        long topTwentyFive = total - (1 + 2 + 3 + 4 + 5);
        assertEquals(raw(topTwentyFive), ledger.balance(wallet.address(0)));
        assertEquals(25, new HashSet<>(first).size());

        List<String> rest = wallet.receivePending(0, 100);
        assertEquals(5, rest.size());
        assertEquals(raw(total), ledger.balance(wallet.address(0)));
        assertTrue(wallet.receivePending(0, 100).isEmpty());
        assertTrue(ledger.processErrors.isEmpty());
    }

    @Test
    void receivePending_respectsSmallMax() throws Exception
    {
        for (int i = 0; i < 4; i++)
        {
            ledger.fund(wallet.address(0), raw(10));
        }
        assertEquals(2, wallet.receivePending(0, 2).size());
        assertEquals("2", lastRequest("receivable").get("count").getAsString());
        assertEquals(Collections.emptyList(), wallet.receivePending(0, 0));
        assertEquals(raw(20), ledger.balance(wallet.address(0)));
    }

    @Test
    void receivePending_returnsPartialOnLaterFailure() throws Exception
    {
        ledger.fund(wallet.address(0), raw(10));
        ledger.fund(wallet.address(0), raw(5));
        // First receive (open) succeeds; second one is rejected for work twice.
        ledger.addFaults(FakeLedger.Fault.NONE, FakeLedger.Fault.REJECT_WORK, FakeLedger.Fault.REJECT_WORK);
        List<String> received = wallet.receivePending(0, 25);
        assertEquals(1, received.size());
        assertEquals(raw(10), ledger.balance(wallet.address(0)));
    }

    // ------------------------------------------------------------------------------------------ lifecycle

    @Test
    void shutdown_stopsGeneratorAndRefusesOperations() throws Exception
    {
        wallet.shutdown();
        assertTrue(work.shutdown);
        assertEquals(LightWalletException.Kind.UNAVAILABLE, kindOf(() -> wallet.send(0, STRANGER, raw(1))));
        wallet.precomputeWork(0); // no-op, no exception
    }

    @Test
    void constructor_validatesArguments()
    {
        assertThrows(IllegalArgumentException.class,
                () -> new LightWallet(new byte[31], "ban_", DEFAULT_REP, ledger, work, TEST_THRESHOLD, null));
        assertThrows(IllegalArgumentException.class,
                () -> new LightWallet(SEED, "ban_", "ban_bad", ledger, work, TEST_THRESHOLD, null));
    }

    @Test
    void errorClassification()
    {
        assertTrue(LightWallet.isDefinitiveRejection("Fork"));
        assertTrue(LightWallet.isDefinitiveRejection("fork."));
        assertTrue(LightWallet.isDefinitiveRejection("Balance and amount delta do not match"));
        assertTrue(LightWallet.isDefinitiveRejection("Bad signature"));
        assertTrue(LightWallet.isDefinitiveRejection("Negative spend"));
        assertTrue(LightWallet.isDefinitiveRejection("Unreceivable"));
        assertTrue(LightWallet.isDefinitiveRejection("Invalid block"));
        assertTrue(LightWallet.isDefinitiveRejection("Bad block hash"));
        assertTrue(LightWallet.isDefinitiveRejection("Block work is insufficient"));
        assertTrue(LightWallet.isWorkError("Block work is insufficient"));
        assertTrue(LightWallet.isWorkError("Block work is less than threshold"));

        // A Gap means the node may keep the block and apply it later: never definitive.
        assertFalse(LightWallet.isDefinitiveRejection("Gap previous block"));
        assertFalse(LightWallet.isDefinitiveRejection("Gap source block"));
        assertFalse(LightWallet.isDefinitiveRejection("Gap epoch open pending"));
        assertFalse(LightWallet.isDefinitiveRejection("Old block"));
        assertFalse(LightWallet.isDefinitiveRejection("Something unexpected happened"));
        // Only exact strings: partial matches are not proof of rejection.
        assertFalse(LightWallet.isDefinitiveRejection("Block is invalid for some new reason"));
        assertFalse(LightWallet.isDefinitiveRejection("Invalid block subtype"));
        assertFalse(LightWallet.isDefinitiveRejection("Fork detected? proxy timeout"));
        assertFalse(LightWallet.isWorkError("invalid work header from proxy"));
        assertFalse(LightWallet.isDefinitiveRejection(""));
    }

    // ------------------------------------------------------------------------------------------ helpers

    private JsonObject lastRequest(String action)
    {
        synchronized (ledger.requests)
        {
            for (int i = ledger.requests.size() - 1; i >= 0; i--)
            {
                if (action.equals(ledger.requests.get(i).get("action").getAsString()))
                {
                    return ledger.requests.get(i);
                }
            }
        }
        fail("no " + action + " request");
        return null;
    }

    /** Publish a change block for {@code index} directly on the ledger, as another signer would. */
    private StateBlock externalChange(int index, String rep)
    {
        try
        {
            FakeLedger.Account account = ledger.account(wallet.address(index));
            byte[] priv = NanoKeys.privateKey(SEED, index);
            StateBlock block = new StateBlock(wallet.address(index), account.frontier.clone(), rep, account.balance,
                    new byte[32], null, null).signed(priv);
            long nonce = 0;
            while (!Work.isValid(block.workRoot(), nonce, TEST_THRESHOLD))
            {
                nonce++;
            }
            block = block.withWork(nonce);
            JsonObject request = new JsonObject();
            request.addProperty("action", "process");
            request.addProperty("subtype", "change");
            request.add("block", block.toRpcJson("ban_"));
            JsonObject response = ledger.call(request);
            assertEquals(block.hashHex(), response.get("hash").getAsString(), response.toString());
            return block;
        }
        catch (IOException ex)
        {
            throw new AssertionError(ex);
        }
    }
}
