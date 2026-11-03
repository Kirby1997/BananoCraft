package banano.bananominecraft.bananoeconomy.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Default {@link HttpTransport} backed by {@link HttpURLConnection}.
 *
 * <p>This is a thin I/O adapter — it contains no business logic, so it is exempt
 * from unit testing (and from the coverage gate) in the same way as the other
 * infrastructure classes. All testable behaviour lives in {@link RPC}.</p>
 */
public class HttpUrlConnectionTransport implements HttpTransport
{
    // HttpURLConnection defaults both timeouts to 0 (infinite) unless set explicitly. Now that
    // EconomyFuncs serializes deposits/withdrawals behind a lock, an unbounded hang here would
    // stall every other queued transaction indefinitely instead of just its own caller - so this
    // caps the worst case rather than leaving it open-ended.
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    // The read timeout only bounds silence between reads, so also bound the whole response:
    // a slow or hostile node must not hold a caller (or a wallet lock) or memory indefinitely.
    private static final long TOTAL_DEADLINE_MS = 30_000;
    private static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;

    @Override
    public String post(String url, String jsonPayload) throws IOException
    {
        return doPost(url, jsonPayload, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS, TOTAL_DEADLINE_MS);
    }

    /**
     * Honours both timeouts; the total-response deadline is {@code min(TOTAL_DEADLINE_MS, readTimeoutMs)}.
     */
    @Override
    public String post(String url, String jsonPayload, int connectTimeoutMs, int readTimeoutMs) throws IOException
    {
        if (connectTimeoutMs < 0 || readTimeoutMs < 0)
        {
            throw new IllegalArgumentException("timeouts must not be negative");
        }
        return doPost(url, jsonPayload, connectTimeoutMs, readTimeoutMs,
                Math.min(TOTAL_DEADLINE_MS, readTimeoutMs == 0 ? TOTAL_DEADLINE_MS : readTimeoutMs));
    }

    private String doPost(String url, String jsonPayload, int connectTimeoutMs, int readTimeoutMs,
                          long totalDeadlineMs) throws IOException
    {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json; utf-8");
        con.setRequestProperty("Accept", "application/json");
        con.setConnectTimeout(connectTimeoutMs);
        con.setReadTimeout(readTimeoutMs);
        // Never let a response redirect a request (some carry the wallet seed) to another host.
        con.setInstanceFollowRedirects(false);
        con.setDoOutput(true);

        // Phase 1: connect and buffer the body. HttpURLConnection opens the socket in getOutputStream()
        // but only puts the request on the wire when the response is first requested, so a failure here
        // means the node cannot have received (let alone acted on) the request. Plain IOException.
        try (OutputStream os = con.getOutputStream())
        {
            byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        catch (IOException ex)
        {
            con.disconnect();
            throw ex;
        }

        // Phase 2: from here the request may have reached the node, so every failure (read timeout,
        // reset, error status, oversized or too slow response) is a RequestSentIOException: the caller
        // cannot know whether the node acted on it.
        long deadline = System.nanoTime() + totalDeadlineMs * 1_000_000L;
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        try (InputStream in = con.getInputStream())
        {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1)
            {
                response.write(buffer, 0, read);

                if (response.size() > MAX_RESPONSE_BYTES)
                {
                    throw new IOException("Node response exceeded " + MAX_RESPONSE_BYTES + " bytes");
                }
                if (System.nanoTime() > deadline)
                {
                    throw new IOException("Node response exceeded " + totalDeadlineMs + " ms");
                }
            }
        }
        catch (IOException ex)
        {
            // Only drop the socket on failure: after a fully read, closed response the JDK returns the
            // connection to its keep-alive pool, so the next RPC skips a new TCP/TLS handshake.
            con.disconnect();
            throw new RequestSentIOException(ex);
        }

        return response.toString(StandardCharsets.UTF_8).trim();
    }
}
