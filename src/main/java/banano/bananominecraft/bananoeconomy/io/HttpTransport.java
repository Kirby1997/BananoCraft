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
}
