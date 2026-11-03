package banano.bananominecraft.bananoeconomy.io;

import java.io.IOException;

/**
 * The single seam between {@link RPC} and the network.
 *
 * <p>Extracting the HTTP call behind this interface means {@link RPC}'s JSON-RPC
 * request/response logic can be unit-tested against a plain mock — no live node and
 * no Mockito spy required. The production implementation is
 * {@link HttpUrlConnectionTransport}.</p>
 */
public interface HttpTransport
{
    /**
     * POST {@code jsonPayload} to {@code url} and return the response body as a string.
     *
     * @param url         the fully-qualified node URL (e.g. {@code http://host:7072})
     * @param jsonPayload the JSON-RPC request body
     * @return the response body
     * @throws IOException if the URL is malformed or the request/response fails
     */
    String post(String url, String jsonPayload) throws IOException;

    /**
     * Like {@link #post(String, String)} but with explicit timeouts. Used for calls made on the server's
     * primary thread, where a long wait would freeze the whole server.
     *
     * <p>The default implementation ignores the timeouts and delegates to the two-argument method;
     * transports that can honour them (see {@link HttpUrlConnectionTransport}) override it.</p>
     *
     * @param connectTimeoutMs maximum time to establish the connection, in milliseconds
     * @param readTimeoutMs    maximum silence while waiting for the response, in milliseconds; also bounds
     *                         the total time spent reading the response
     * @throws IOException as {@link #post(String, String)}; a {@link RequestSentIOException} when the failure
     *                     happened after the request was handed to the network
     */
    default String post(String url, String jsonPayload, int connectTimeoutMs, int readTimeoutMs) throws IOException
    {
        return post(url, jsonPayload);
    }
}
