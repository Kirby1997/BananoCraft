package banano.bananominecraft.bananoeconomy.wallet.crypto;

import java.security.SecureRandom;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Local CPU proof-of-work.
 *
 * <p>Uses a fixed pool of daemon threads at {@link Thread#MIN_PRIORITY}, so it never competes
 * with the server tick for long and never keeps the JVM alive. Each request is searched by
 * every pool thread from independent random start nonces; requests are served in order.
 * A result is re-checked with {@link Work#isValid} before the future completes. Cancelling the
 * future (or {@link #shutdown()}) stops the search within a few milliseconds.</p>
 */
public final class CpuWorkGenerator implements WorkGenerator
{
    /** Nonces tried between checks for cancellation. */
    private static final int BATCH = 1 << 14;
    private static final AtomicInteger POOL_IDS = new AtomicInteger();

    private final int threads;
    private final ThreadPoolExecutor executor;
    private final SecureRandom random = new SecureRandom();
    private final Set<CompletableFuture<Long>> pending = ConcurrentHashMap.newKeySet();
    private volatile boolean shutdown;

    /**
     * Create a generator.
     *
     * @param threads requested worker threads; clamped to 1..{@code availableProcessors()}
     */
    public CpuWorkGenerator(int threads)
    {
        this.threads = Math.max(1, Math.min(threads, Runtime.getRuntime().availableProcessors()));
        int poolId = POOL_IDS.incrementAndGet();
        AtomicInteger threadIds = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(this.threads, this.threads, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), r ->
                {
                    Thread t = new Thread(r, "BananoEconomy-Work-" + poolId + "-" + threadIds.incrementAndGet());
                    t.setDaemon(true);
                    t.setPriority(Thread.MIN_PRIORITY);
                    return t;
                });
    }

    /** @return the effective number of worker threads */
    public int threads()
    {
        return threads;
    }

    @Override
    public CompletableFuture<Long> generate(byte[] root32, long threshold)
    {
        CompletableFuture<Long> result = new CompletableFuture<>();
        byte[] root;
        try
        {
            root = root32.clone();
            NanoKeys.requireLength(root, 32, "work root");
        }
        catch (RuntimeException e)
        {
            result.completeExceptionally(new IllegalArgumentException("Invalid work root", e));
            return result;
        }
        if (shutdown)
        {
            result.completeExceptionally(new IllegalStateException("Work generator is shut down"));
            return result;
        }
        pending.add(result);
        result.whenComplete((v, e) -> pending.remove(result));
        try
        {
            for (int i = 0; i < threads; i++)
            {
                long start;
                synchronized (random)
                {
                    start = random.nextLong();
                }
                executor.execute(() -> search(root, threshold, start, result));
            }
        }
        catch (RejectedExecutionException e)
        {
            result.completeExceptionally(new IllegalStateException("Work generator is shut down", e));
        }
        return result;
    }

    private static void search(byte[] root, long threshold, long start, CompletableFuture<Long> result)
    {
        if (result.isDone())
        {
            return;
        }
        WorkHasher hasher = new WorkHasher(root);
        long nonce = start;
        Thread self = Thread.currentThread();
        while (!result.isDone() && !self.isInterrupted())
        {
            for (int i = 0; i < BATCH; i++, nonce++)
            {
                if (Long.compareUnsigned(hasher.value(nonce), threshold) >= 0)
                {
                    // Independent check with the reference BLAKE2b before anything relies on it
                    if (Work.isValid(root, nonce, threshold))
                    {
                        result.complete(nonce);
                    }
                    else
                    {
                        result.completeExceptionally(new IllegalStateException(
                                "Work self-check failed for nonce " + Long.toHexString(nonce)));
                    }
                    return;
                }
            }
        }
    }

    @Override
    public void shutdown()
    {
        shutdown = true;
        executor.shutdownNow();
        for (CompletableFuture<Long> f : pending)
        {
            f.completeExceptionally(new IllegalStateException("Work generator is shut down"));
        }
    }
}
