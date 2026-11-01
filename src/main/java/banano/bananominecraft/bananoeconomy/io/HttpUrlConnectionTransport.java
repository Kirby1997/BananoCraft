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
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json; utf-8");
        con.setRequestProperty("Accept", "application/json");
        con.setConnectTimeout(CONNECT_TIMEOUT_MS);
        con.setReadTimeout(READ_TIMEOUT_MS);
        // Never let a response redirect a request (some carry the wallet seed) to another host.
        con.setInstanceFollowRedirects(false);
        con.setDoOutput(true);

        // Write request — let the exception propagate so callers don't attempt
        // to read a response that will never arrive.
        try (OutputStream os = con.getOutputStream())
        {
            byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        long deadline = System.nanoTime() + TOTAL_DEADLINE_MS * 1_000_000L;
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
                    throw new IOException("Node response exceeded " + TOTAL_DEADLINE_MS + " ms");
                }
            }
        }
        catch (IOException ex)
        {
            // Only drop the socket on failure: after a fully read, closed response the JDK returns the
            // connection to its keep-alive pool, so the next RPC skips a new TCP/TLS handshake.
            con.disconnect();
            throw ex;
        }

        return response.toString(StandardCharsets.UTF_8).trim();
    }
}
