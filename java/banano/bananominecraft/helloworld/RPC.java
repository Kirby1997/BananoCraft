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
//private Plugin plugin = banano.bananominecraft.HelloWorld.getPlugin(HelloWorld.class);

    public static String sendPost(String payload) throws Exception {
        Plugin plugin = banano.bananominecraft.helloworld.HelloWorld.getPlugin(HelloWorld.class);
        URL url = new URL(plugin.getConfig().getString("IP"));


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

            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
        }
        return response.toString();
    }

    public static String accountCreate(){

        String payload = "{\"action\": \"account_create\"," +
                "\"wallet\": \"" + getWalletID() + "\"}";
        try{

            String accountResponse = sendPost(payload);
            JsonElement accountJson = new JsonParser().parse(accountResponse);
            String account = accountJson.getAsJsonObject().get("account").getAsString();

            return account;
        }
        catch (Exception e){
            System.out.println(payload);
            e.printStackTrace();
        }
        return "Account Creation Failed";
    }

    private static BigInteger toRaw(int value){

        Double draw = value * Math.pow(10,29);


        return BigDecimal.valueOf(draw).toBigInteger();
    }

    private static Double fromRaw(BigInteger bigInteger){
        Double draw = bigInteger.doubleValue();
        double value = draw / Math.pow(10,29);

        return value;

    }

    public static String sendTransaction(String sender, String recipient, int value){
        String payload = "{\"action\": \"send\"," +
                "\"wallet\": \"" + getWalletID() + "\"," +
                "\"source\": \"" + sender + "\"," +
                "\"destination\": \"" + recipient + "\"," +
                "\"amount\": \"" + toRaw(value) + "\"}";

        try{
            String sendResponse = sendPost(payload);
            JsonElement accountJson = new JsonParser().parse(sendResponse);
            String blockHash = accountJson.getAsJsonObject().get("block").getAsString();
            System.out.println(blockHash);

            return blockHash;
        }
        catch (Exception e){
            System.out.println(payload);
            e.printStackTrace();
        }
        return "Transaction send failed";
    }

    public static Double getBalance(String account){

        String payload = "{\"action\": \"account_info\"," +
                "\"account\": \"" + account + "\"}";
        try{

            String sendResponse = sendPost(payload);
            JsonElement accountJson = new JsonParser().parse(sendResponse);
            try{
                BigInteger bigInteger = accountJson.getAsJsonObject().get("balance").getAsBigInteger();
                System.out.println("Balance: " + bigInteger);
                return fromRaw(bigInteger);
            }
            catch(Exception e){
                String error = accountJson.getAsJsonObject().get("error").toString();
                if (error.equals("Account not found")){
                return 0.0;
                }

            }

        }
        catch (Exception e){
            System.out.println(payload);
            e.printStackTrace();
        }
        return 0.0;

    }

    private static String getWalletID(){
        return "INSERT WALLET ID HERE";
    }
}
