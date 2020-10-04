package banano.bananominecraft.bananoeconomy;

import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jdk.internal.jline.internal.Log;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;


public class RPC{
//private Plugin plugin = banano.bananominecraft.BananoEconomy.getPlugin(BananoEconomy.class);
    static Plugin plugin = Main.getPlugin(Main.class);

    public static URL getURL() throws Exception{
        URL url = new URL(plugin.getConfig().getString("IP"));
        return url;
    }


    public static String sendPost(String payload) throws Exception {

        URL url = getURL();

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

    public static String accountCreate(int index){


        String payload = "{\"action\": \"account_create\"," +
                    "\"wallet\": \"" + getWalletID() + "\"}";

        if(index != -1){
            payload = "{\"action\": \"account_create\"," +
                    "\"wallet\": \"" + getWalletID() + "\"," +
                    "\"index\": \"" + index + "\"}";
        }

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

    private static BigInteger toRaw(double value){

        BigDecimal bValue = new BigDecimal(Double.toString(value));
        BigDecimal multiplier = new BigDecimal("100000000000000000000000000000");
        BigDecimal raw = bValue.multiply(multiplier);

        return raw.toBigInteger();
    }

    private static Double fromRaw(BigDecimal bigDecimal){
        BigDecimal divisor = new BigDecimal("100000000000000000000000000000");
        BigDecimal result = bigDecimal.divide(divisor);


        return result.doubleValue();

    }

    public static String sendTransaction(String sender, String recipient, double value) throws TransactionError {
        final String payload = "{\"action\": \"send\"," +
                "\"wallet\": \"" + getWalletID() + "\"," +
                "\"source\": \"" + sender + "\"," +
                "\"destination\": \"" + recipient + "\"," +
                "\"amount\": \"" + toRaw(value) + "\"}";

        final JsonElement accountJson;
        try {
            final String sendResponse = sendPost(payload);
            accountJson = new JsonParser().parse(sendResponse);
        } catch (final Exception e) {
            plugin.getLogger().info(() -> String.format("Failed to sendTransaction with payload: '%s' because: '%s'", payload, e.getLocalizedMessage()));
            throw new TransactionError("Send transaction failed");
        }

        final JsonObject json = accountJson.getAsJsonObject();
        final JsonElement error = json.get("error");
        if (error != null) {
            throw new TransactionError(error.getAsString());
        }

        return Optional
                .ofNullable(json.get("block"))
                .map(JsonElement::getAsString)
                .orElseThrow(() -> new TransactionError("Send transaction resulted in missing block"));
    }

    public static Double getBalance(String account){

        String payload = "{\"action\": \"account_info\"," +
                "\"account\": \"" + account + "\"}";
        try{

            String balanceResponse = sendPost(payload);
            JsonElement accountJson = new JsonParser().parse(balanceResponse);
            try{
                BigDecimal bigDecimal = accountJson.getAsJsonObject().get("balance").getAsBigDecimal();
                return fromRaw(bigDecimal);
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

    public static List<String> getBlockCount(){
        String payload = "{\"action\": \"block_count\"}";

        try {
            JsonElement blocksJson = new JsonParser().parse(sendPost(payload));
            String checked = blocksJson.getAsJsonObject().get("count").toString();
            String unchecked = blocksJson.getAsJsonObject().get("unchecked").toString();
            return Arrays.asList(checked, unchecked);
        }
        catch (Exception e){
            System.out.println(e);
        }
        return Arrays.asList("Null", "Null");
    }

    public static Boolean wallet_exists(){
        String walletID = getWalletID();

        String payload = "{\"action\": \"wallet_balances\"," +
                "\"wallet\": \"" + walletID + "\"}";
        try {
            JsonElement existsJson = new JsonParser().parse(sendPost(payload));
            try{
                String exists = existsJson.getAsJsonObject().get("error").getAsString();
                if (exists.equals("Wallet not found") || exists.equals("Bad wallet number")){
                    return false;
                }
                else {
                    System.out.println(payload);
                }
            }
            catch (Exception e){
                System.out.println("Master wallet found!");
            }

        }
        catch (Exception e){
            System.out.println("Master wallet found!");
        }
        return true;
    }


    public static Boolean wallet_contains(String account){
        String walletID = getWalletID();

        String payload = "{\"action\": \"account_contains\"," +
                "\"wallet\": \"" + walletID + "\"," +
                "\"account\": \"" + account + "\"}";
        try{
            JsonElement existsJson = new JsonParser().parse(sendPost(payload));
            int exists = existsJson.getAsJsonObject().get("exists").getAsInt();

            if(exists == 1){
                System.out.println("Account is in wallet");
                return true;
            }
            else if (exists == 0){
                return false;
            }

        }
        catch (Exception e){
            System.out.println(e);
        }
        return false;
    }

    public static void walletCreate(){

        String seed = plugin.getConfig().getString("walletSeed");

        String payload = "{\"action\": \"wallet_create\"," +
                "\"seed\": \"" + seed + "\"}";
        try{
        JsonElement blocksJson = new JsonParser().parse(sendPost(payload));
        String walletID = blocksJson.getAsJsonObject().get("wallet").toString().replaceAll("\"", "");

        System.out.println("Wallet ID generated: " + walletID);
        plugin.getConfig().set("walletID", walletID);
        plugin.saveConfig();
        }
        catch (Exception e){
            System.out.println("Payload\n" + payload);
            System.out.println(e);
        }
    }

    private static String getWalletID(){

        return plugin.getConfig().getString("walletID");
    }

    public static String getMasterWallet(){

        return plugin.getConfig().getString("masterWallet");
    }
}
