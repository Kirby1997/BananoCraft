package banano.bananominecraft.bananoeconomy.wallet.light;

import com.google.gson.JsonObject;

import java.io.IOException;

/**
 * The network seam of the light wallet: one JSON-RPC round trip to a Banano node.
 *
 * <p>Implementations must be thread-safe. A node-level error is NOT an exception: it is returned as a normal
 * response object carrying an {@code "error"} member, which {@link LightWallet} interprets. An
 * {@link IOException} means the outcome of the request is unknown to the caller (it may or may not have reached
 * the node), which matters for {@code process}.</p>
 */
public interface LightRpc
{
    /**
     * Send {@code request} to the node and return its JSON response.
     *
     * @param request the JSON-RPC request object (must contain {@code "action"})
     * @return the parsed response object (possibly containing {@code "error"})
     * @throws IOException if the request could not be sent, timed out, or the response was not a JSON object
     */
    JsonObject call(JsonObject request) throws IOException;
}
