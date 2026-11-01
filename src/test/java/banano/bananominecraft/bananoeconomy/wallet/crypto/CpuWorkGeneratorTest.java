package banano.bananominecraft.bananoeconomy.wallet.crypto;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CpuWorkGeneratorTest
{
    private static final long EASY = 0xffc0000000000000L; // ~1024 attempts on average
    private static final long IMPOSSIBLE = 0xffffffffffffffffL;

    private CpuWorkGenerator gen;

    @AfterEach
    void tearDown()
    {
        if (gen != null)
        {
            gen.shutdown();
        }
    }

    private static byte[] root(int seed)
    {
        byte[] r = new byte[32];
        new Random(seed).nextBytes(r);
        return r;
    }

    @Test
    void generate_easyThreshold_returnsValidWork() throws Exception
    {
        gen = new CpuWorkGenerator(2);
        for (int i = 0; i < 20; i++)
        {
            byte[] root = root(i);
            long work = gen.generate(root, EASY).get(30, TimeUnit.SECONDS);
            assertTrue(Work.isValid(root, work, EASY));
        }
    }

    @Test
    void generate_bananoThreshold_returnsValidWork() throws Exception
    {
        gen = new CpuWorkGenerator(Runtime.getRuntime().availableProcessors());
        byte[] root = root(99);
        long work = gen.generate(root, Work.BANANO_THRESHOLD).get(180, TimeUnit.SECONDS);
        assertTrue(Work.isValid(root, work, Work.BANANO_THRESHOLD));
    }

    @Test
    void generate_randomStartNonces_differBetweenRequests() throws Exception
    {
        gen = new CpuWorkGenerator(1);
        byte[] root = root(5);
        long a = gen.generate(root, EASY).get(30, TimeUnit.SECONDS);
        long b = gen.generate(root, EASY).get(30, TimeUnit.SECONDS);
        assertNotEquals(a, b);
    }

    @Test
    void cancel_stopsSearch_andLaterRequestsStillComplete() throws Exception
    {
        gen = new CpuWorkGenerator(1);
        CompletableFuture<Long> hopeless = gen.generate(root(1), IMPOSSIBLE);
        Thread.sleep(50);
        assertTrue(hopeless.cancel(true));
        // With one thread, this only completes if the cancelled search released it
        long work = gen.generate(root(2), EASY).get(30, TimeUnit.SECONDS);
        assertTrue(Work.isValid(root(2), work, EASY));
    }

    @Test
    void shutdown_failsPendingAndLaterRequests()
    {
        gen = new CpuWorkGenerator(1);
        CompletableFuture<Long> running = gen.generate(root(1), IMPOSSIBLE);
        CompletableFuture<Long> queued = gen.generate(root(2), IMPOSSIBLE);
        gen.shutdown();
        assertThrows(ExecutionException.class, () -> running.get(5, TimeUnit.SECONDS));
        assertThrows(ExecutionException.class, () -> queued.get(5, TimeUnit.SECONDS));
        CompletableFuture<Long> after = gen.generate(root(3), EASY);
        ExecutionException e = assertThrows(ExecutionException.class, () -> after.get(5, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, e.getCause());
    }

    @Test
    void invalidRoot_failsFuture()
    {
        gen = new CpuWorkGenerator(1);
        ExecutionException e = assertThrows(ExecutionException.class,
                () -> gen.generate(new byte[31], EASY).get(5, TimeUnit.SECONDS));
        assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertThrows(ExecutionException.class, () -> gen.generate(null, EASY).get(5, TimeUnit.SECONDS));
    }

    @Test
    void threads_areBoundedDaemonMinPriority() throws Exception
    {
        assertEquals(1, new CpuWorkGenerator(0).threads());
        CpuWorkGenerator big = new CpuWorkGenerator(10_000);
        assertEquals(Runtime.getRuntime().availableProcessors(), big.threads());
        big.shutdown();

        gen = new CpuWorkGenerator(1);
        CompletableFuture<Long> hopeless = gen.generate(root(1), IMPOSSIBLE);
        Thread.sleep(50);
        Thread worker = Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().startsWith("BananoEconomy-Work-") && t.isAlive())
                .findFirst().orElseThrow();
        assertTrue(worker.isDaemon());
        assertEquals(Thread.MIN_PRIORITY, worker.getPriority());
        hopeless.cancel(true);
    }
}
