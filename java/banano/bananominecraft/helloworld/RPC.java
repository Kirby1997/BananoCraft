package banano.bananominecraft.helloworld;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;


public class RPC{

    Plugin plugin = HelloWorld.getPlugin();
    String walletID = "8F0A6F3CBFE07C05F2867E5D713BD050BAF8AC16457C4CFA57A709F5DB440678";

    public String sendPost(String payload) throws Exception {
        //String nodeURL = plugin.getConfig().getString("IP"); WHY DOESN'T THIS WORK, KODY??

        URL url = new URL ("http://127.0.0.1:7072");
        HttpURLConnection con = (HttpURLConnection)url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json; utf-8");
        con.setRequestProperty("Accept", "application/json");
        con.setDoOutput(true);
        StringBuilder response = new StringBuilder();
        try(OutputStream os = con.getOutputStream()) {
            byte[] input = payload.getBytes("utf-8");
            os.write(input, 0, input.length);
        }
        try(BufferedReader br = new BufferedReader(
                new InputStreamReader(con.getInputStream(), "utf-8"))) {

            String responseLine = null;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            System.out.println(response.toString());

        }

        System.out.println(response.toString());
        return response.toString();
    }

    public String accountCreate(){
        try{
            String payload = "{\"action\": \"account_create\"," +
                    "\"wallet\": \"" + walletID + "\"}";
            //System.out.println(payload);

            String accountResponse = sendPost(payload);
            JsonElement accountJson = new JsonParser().parse(accountResponse);
            String account = accountJson.getAsJsonObject().get("account").getAsString();

            return account;
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return "Account Creation Failed";
    }

    public BigInteger toRaw(int value){

        Double draw = value * Math.pow(10,29);
        BigInteger raw = BigDecimal.valueOf(draw).toBigInteger();

        return raw;
    }

    public int fromRaw(BigInteger bigInteger){
        Double draw = bigInteger.doubleValue();
        double value = draw / Math.pow(10,29);

        return (int)value;

    }

    public String sendTransaction(String sender, String recipient, int value){
        try{
            String payload = "{\"action\": \"send\"," +
                    "\"wallet\": \"" + walletID + "\"," +
                    "\"source\": \"" + sender + "\"," +
                    "\"destination\": \"" + recipient + "\"," +
                    "\"amount\": \"" + toRaw(value) + "\"}";

            System.out.println(payload);
            String sendResponse = sendPost(payload);
            JsonElement accountJson = new JsonParser().parse(sendResponse);
            String blockHash = accountJson.getAsJsonObject().get("block").getAsString();
            System.out.println(blockHash);

            return blockHash;
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return "Transaction send failed";
    }

    public int getBalance(String account){
        try{
            String payload = "{\"action\": \"account_info\"," +
                    "\"account\": \"" + account + "\"}";

            System.out.println(payload);
            String sendResponse = sendPost(payload);
            JsonElement accountJson = new JsonParser().parse(sendResponse);
            BigInteger bigInteger = accountJson.getAsJsonObject().get("balance").getAsBigInteger();
            System.out.println(bigInteger);
            int response = fromRaw(bigInteger);
            return response;
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return 0;

    }
}
