package banano.bananominecraft.bananoeconomy.io;

import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.wallet.MainThreadGuard;

import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * Decides which timeouts a node HTTP call uses: short ones on the server's primary thread (so a
 * synchronous Vault call can never freeze the server for long), the transport's normal ones
 * otherwise. {@code mainThreadReadTimeoutMs: 0} disables the short timeouts.
 *
 * <p>This is the single place the decision is made. {@link #wrap(HttpTransport)} applies it to every
 * request of a transport, so {@link RPC}, the node wallet signer and the light wallet's RPC adapter
 * all pick it up by sharing the wrapped transport.</p>
 */
public final class TimeoutPolicy
{
    private final BooleanSupplier primaryThread;
    private final IntSupplier connectMs;
    private final IntSupplier readMs;

    public TimeoutPolicy(BooleanSupplier primaryThread, IntSupplier connectMs, IntSupplier readMs)
    {
        this.primaryThread = primaryThread;
        this.connectMs = connectMs;
        this.readMs = readMs;
    }

    public static TimeoutPolicy forConfig(ConfigEngine config, MainThreadGuard guard)
    {
        return new TimeoutPolicy(guard::isPrimaryThread,
                config::getMainThreadConnectTimeoutMs, config::getMainThreadReadTimeoutMs);
    }

    /** The short connect timeout, or -1 when the normal timeouts apply to the calling thread. */
    public int connectTimeoutMs()
    {
        return useShortTimeouts() ? connectMs.getAsInt() : -1;
    }

    /** The short read timeout, or -1 when the normal timeouts apply to the calling thread. */
    public int readTimeoutMs()
    {
        return useShortTimeouts() ? readMs.getAsInt() : -1;
    }

    /** True when on the primary thread and the feature is enabled (read timeout &gt; 0). */
    public boolean useShortTimeouts()
    {
        return readMs.getAsInt() > 0 && connectMs.getAsInt() > 0 && primaryThread.getAsBoolean();
    }

    /** A transport that applies this policy to every call made through it. */
    public HttpTransport wrap(HttpTransport delegate)
    {
        return new HttpTransport()
        {
            @Override
            public String post(String url, String jsonPayload) throws IOException
            {
                int connect = connectMs.getAsInt();
                int read = readMs.getAsInt();
                if (connect > 0 && read > 0 && primaryThread.getAsBoolean())
                {
                    return delegate.post(url, jsonPayload, connect, read);
                }
                return delegate.post(url, jsonPayload);
            }

            @Override
            public String post(String url, String jsonPayload, int connectTimeoutMs, int readTimeoutMs)
                    throws IOException
            {
                return delegate.post(url, jsonPayload, connectTimeoutMs, readTimeoutMs);
            }
        };
    }
}
