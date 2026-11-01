package banano.bananominecraft.bananoeconomy.wallet.light;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HttpLightRpcTest
{
    private static JsonObject request()
    {
        JsonObject r = new JsonObject();
        r.addProperty("action", "account_info");
        return r;
    }

    @Test
    void postsRequestToSuppliedUrlAndParsesObject() throws Exception
    {
        AtomicReference<String> seenUrl = new AtomicReference<>();
        AtomicReference<String> seenBody = new AtomicReference<>();
        HttpLightRpc rpc = new HttpLightRpc((url, body) ->
        {
            seenUrl.set(url);
            seenBody.set(body);
            return "{\"error\":\"Account not found\"}";
        }, () -> "http://node:7072");

        JsonObject response = rpc.call(request());
        assertEquals("Account not found", response.get("error").getAsString());
        assertEquals("http://node:7072", seenUrl.get());
        assertEquals("{\"action\":\"account_info\"}", seenBody.get());
    }

    @Test
    void nonObjectOrMalformedResponseIsIOException()
    {
        assertThrows(IOException.class, () -> new HttpLightRpc((u, b) -> "[1,2]", () -> "http://x").call(request()));
        assertThrows(IOException.class, () -> new HttpLightRpc((u, b) -> "{oops", () -> "http://x").call(request()));
        assertThrows(IOException.class, () -> new HttpLightRpc((u, b) -> "", () -> "http://x").call(request()));
        assertThrows(IOException.class, () -> new HttpLightRpc((u, b) -> null, () -> "http://x").call(request()));
    }

    @Test
    void missingUrlIsIOException()
    {
        assertThrows(IOException.class, () -> new HttpLightRpc((u, b) -> "{}", () -> " ").call(request()));
        assertThrows(IOException.class, () -> new HttpLightRpc((u, b) -> "{}", () -> null).call(request()));
    }

    @Test
    void transportFailurePropagates()
    {
        assertThrows(IOException.class, () -> new HttpLightRpc((u, b) ->
        {
            throw new IOException("refused");
        }, () -> "http://x").call(request()));
    }
}
