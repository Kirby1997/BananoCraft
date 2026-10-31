package banano.bananominecraft.bananoeconomy.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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

    @Override
    public String post(String url, String jsonPayload) throws IOException
    {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json; utf-8");
        con.setRequestProperty("Accept", "application/json");
        con.setConnectTimeout(CONNECT_TIMEOUT_MS);
        con.setReadTimeout(READ_TIMEOUT_MS);
        con.setDoOutput(true);

        // Write request — let the exception propagate so callers don't attempt
        // to read a response that will never arrive.
        try (OutputStream os = con.getOutputStream())
        {
            byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8)))
        {
            String responseLine;
            while ((responseLine = br.readLine()) != null)
            {
                response.append(responseLine.trim());
            }
        }

        return response.toString();
    }
}
