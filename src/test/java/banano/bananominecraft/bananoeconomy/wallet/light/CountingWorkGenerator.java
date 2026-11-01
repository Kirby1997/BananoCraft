package banano.bananominecraft.bananoeconomy.wallet.light;

import banano.bananominecraft.bananoeconomy.wallet.crypto.Hex;
import banano.bananominecraft.bananoeconomy.wallet.crypto.Work;
import banano.bananominecraft.bananoeconomy.wallet.crypto.WorkGenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/** Brute-force work generator for low test thresholds that records every requested root. */
final class CountingWorkGenerator implements WorkGenerator
{
    final List<String> roots = Collections.synchronizedList(new ArrayList<>());
    volatile boolean shutdown;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r ->
    {
        Thread t = new Thread(r, "test-work");
        t.setDaemon(true);
        return t;
    });

    @Override
    public CompletableFuture<Long> generate(byte[] root32, long threshold)
    {
        byte[] root = root32.clone();
        roots.add(Hex.encode(root));
        CompletableFuture<Long> future = new CompletableFuture<>();
        executor.execute(() ->
        {
            long nonce = ThreadLocalRandom.current().nextLong();
            while (!future.isDone())
            {
                if (Work.isValid(root, nonce, threshold))
                {
                    future.complete(nonce);
                    return;
                }
                nonce++;
            }
        });
        return future;
    }

    int count(byte[] root)
    {
        String hex = Hex.encode(root);
        synchronized (roots)
        {
            return (int) roots.stream().filter(hex::equals).count();
        }
    }

    @Override
    public void shutdown()
    {
        shutdown = true;
        executor.shutdownNow();
    }
}
