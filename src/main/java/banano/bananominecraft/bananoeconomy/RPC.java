package banano.bananominecraft.bananoeconomy;

import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.json.simple.JSONObject;


public class RPC{
    static Plugin plugin = Main.getPlugin(Main.class);

    public static URL getURL() throws Exception{
        URL url = new URL(plugin.getConfig().getString("IP"));
        return url;
    }

    public static BigDecimal getMultiplier() {
        BigDecimal multiplier = new BigDecimal(plugin.getConfig().getString("multiplier"));
        return multiplier;
    }

    //TODO: Check sendPost references for validation
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

    // TODO: JSON Factory for payload construction, not vulnerable unless config is compromised.
    public static String accountCreate(int index){
        JSONObject json_payload = new JSONObject();
        StringWriter out_payload = new StringWriter();
        String payload = "";

        String wallID = getWalletID();

        json_payload.put("action", "account_create");
        json_payload.put("wallet", wallID);

        if(index != -1){
            json_payload.put("index", index);
        }

        try{
            json_payload.writeJSONString(out_payload);
            payload = out_payload.toString();
        } catch (java.io.IOException e) {
            System.out.println(json_payload);
            e.printStackTrace();
        }

        try{
            String accountResponse = sendPost(payload);
            JsonElement accountJson = JsonParser.parseString(accountResponse);
            return accountJson.getAsJsonObject().get("account").getAsString();
        }
        catch (Exception e){
            System.out.println(payload);
            e.printStackTrace();
        }
        return "Account Creation Failed";
    }

    private static BigInteger toRaw(double value){
        BigDecimal multiplier = getMultiplier();
        BigDecimal bValue = new BigDecimal(Double.toString(value));
        BigDecimal raw = bValue.multiply(multiplier);

        return raw.toBigInteger();
    }

    private static Double fromRaw(BigDecimal bigDecimal){
        BigDecimal divisor = getMultiplier();
        BigDecimal result = bigDecimal.divide(divisor);

        return result.doubleValue();
    }

    // TODO: Extensive validation on sendTransaction
    public static String sendTransaction(String sender, String recipient, double value) throws TransactionError {
        final JSONObject json_payload = new JSONObject();
        final StringWriter out_payload = new StringWriter();
        final JsonElement accountJson;

        String payload = "";

        //Validate Sender and Recipient
        if(!Validator.validateAddress(sender) || !Validator.validateAddress(recipient)){
            throw new TransactionError("Invalid address for sender or recipient.");
        }

        json_payload.put("action", "send");
        json_payload.put("source", sender);
        json_payload.put("destination", recipient);
        json_payload.put("amount", toRaw(value));

        try{
            json_payload.writeJSONString(out_payload);
            payload = out_payload.toString();
        } catch (java.io.IOException e) {
            System.out.println(json_payload);
            e.printStackTrace();
        }

        try {
            final String sendResponse = sendPost(payload);
            accountJson = JsonParser.parseString(sendResponse);
        } catch (final Exception e) {
            String logString = String.format("Failed to sendTransaction with payload: '%s' because: '%s'", payload, e.getLocalizedMessage());
            plugin.getLogger().info(logString);
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
        final JSONObject json_payload = new JSONObject();
        final StringWriter out_payload = new StringWriter();
        final JsonElement accountJson;

        String payload = "";
        json_payload.put("action", "account_info");
        json_payload.put("account", account);

        try{
            json_payload.writeJSONString(out_payload);
            payload = out_payload.toString();
        } catch (java.io.IOException e) {
            System.out.println(json_payload);
            e.printStackTrace();
        }

        try{

            String balanceResponse = sendPost(payload);
            accountJson = JsonParser.parseString(balanceResponse);
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
        final JSONObject json_payload = new JSONObject();
        final StringWriter out_payload = new StringWriter();

        String payload = "";
        json_payload.put("action", "block_count");

        try{
            json_payload.writeJSONString(out_payload);
            payload = out_payload.toString();
        } catch (java.io.IOException e) {
            System.out.println(json_payload);
            e.printStackTrace();
        }

        try {
            JsonElement blocksJson = JsonParser.parseString(sendPost(payload));
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
        final JSONObject json_payload = new JSONObject();
        final StringWriter out_payload = new StringWriter();
        final String walletID = getWalletID();

        String payload = "";
        json_payload.put("action", "wallet_balances");
        json_payload.put("wallet", walletID);

        try{
            json_payload.writeJSONString(out_payload);
            payload = out_payload.toString();
        } catch (java.io.IOException e) {
            System.out.println(json_payload);
            e.printStackTrace();
        }


        try {
            JsonElement existsJson = JsonParser.parseString(sendPost(payload));
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
        final JSONObject json_payload = new JSONObject();
        final StringWriter out_payload = new StringWriter();
        final String walletID = getWalletID();
        String payload = "";

        json_payload.put("action", "account_contains");
        json_payload.put("wallet", walletID);
        json_payload.put("account", account);

        try{
            json_payload.writeJSONString(out_payload);
            payload = out_payload.toString();
        } catch (java.io.IOException e) {
            System.out.println(json_payload);
            e.printStackTrace();
        }


        try{
            JsonElement existsJson = JsonParser.parseString(sendPost(payload));
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

        final JSONObject json_payload = new JSONObject();
        final StringWriter out_payload = new StringWriter();
        String payload = "";

        json_payload.put("action", "wallet_create");
        json_payload.put("seed", seed);

        try{
            json_payload.writeJSONString(out_payload);
            payload = out_payload.toString();
        } catch (java.io.IOException e) {
            System.out.println(json_payload);
            e.printStackTrace();
        }


        try{
            JsonElement blocksJson = JsonParser.parseString(sendPost(payload));

            String walletID = blocksJson.getAsJsonObject().get("wallet").toString().replaceAll("\"", "");

            System.out.println("Wallet ID generated: " + walletID);
            plugin.getConfig().set("walletID", walletID);
            plugin.saveConfig();
        } catch (Exception e){
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
