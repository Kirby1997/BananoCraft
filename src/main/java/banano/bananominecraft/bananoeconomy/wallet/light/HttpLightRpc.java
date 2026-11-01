package banano.bananominecraft.bananoeconomy.wallet.light;

import banano.bananominecraft.bananoeconomy.io.HttpTransport;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * {@link LightRpc} over the plugin's existing {@link HttpTransport} (HTTP POST of the JSON request).
 *
 * <p>The URL is read through a {@link Supplier} on every call so a configuration reload takes effect without
 * rebuilding the wallet. Any response that is not a JSON object is reported as an {@link IOException}, i.e. as an
 * unknown outcome, never as a node verdict.</p>
 */
public final class HttpLightRpc implements LightRpc
{
    private final HttpTransport http;
    private final Supplier<String> url;

    /**
     * @param http the transport performing the POST (thread-safe)
     * @param url  supplies the node RPC URL, e.g. {@code http://127.0.0.1:7072} or a public RPC endpoint
     */
    public HttpLightRpc(HttpTransport http, Supplier<String> url)
    {
        this.http = Objects.requireNonNull(http, "http");
        this.url = Objects.requireNonNull(url, "url");
    }

    @Override
    public JsonObject call(JsonObject request) throws IOException
    {
        String target = url.get();
        if (target == null || target.isBlank())
        {
            throw new IOException("No node RPC URL configured");
        }

        String body = http.post(target, request.toString());
        if (body == null)
        {
            throw new IOException("Empty node response");
        }

        try
        {
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonObject())
            {
                throw new IOException("Node response is not a JSON object");
            }
            return parsed.getAsJsonObject();
        }
        catch (JsonParseException | IllegalStateException ex)
        {
            throw new IOException("Malformed node response", ex);
        }
    }
}
