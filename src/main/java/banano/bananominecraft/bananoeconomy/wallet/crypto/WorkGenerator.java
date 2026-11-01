package banano.bananominecraft.bananoeconomy.wallet.crypto;

import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous proof-of-work source.
 *
 * <p>Implementations never compute on the calling thread. Callers must not block the Bukkit
 * main thread on the returned future.</p>
 */
public interface WorkGenerator
{
    /**
     * Start generating work for {@code root32}.
     *
     * @param root32    the 32-byte work root
     * @param threshold the minimum work value (unsigned)
     * @return a future completing with a nonce that is valid for the root and threshold;
     *         cancelling it stops the search
     */
    CompletableFuture<Long> generate(byte[] root32, long threshold);

    /** Stop all searches and release threads; pending futures complete exceptionally. */
    void shutdown();
}
