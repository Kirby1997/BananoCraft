package banano.bananominecraft.bananoeconomy.db;

import banano.bananominecraft.bananoeconomy.classes.BankRecord;
import banano.bananominecraft.bananoeconomy.classes.OfflinePaymentRecord;
import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

public class JsonDBConnector extends BaseDBConnector
{
    /** Sub-directory inside the plugin data folder used for all JSON files. */
    private static final String DATA_DIRECTORY = "data";

    /** Repeating auto-save interval in ticks (18 000 t = 15 minutes). */
    private static final long SAVE_INTERVAL_TICKS = 18_000L;

    private final Plugin plugin;
    private final File   dataLocation;

    /**
     * wallet address → player UUID string for every wallet that has ever been
     * assigned.  Used by {@link #isAlreadyAssignedToOtherPlayer} to prevent
     * duplicate wallet assignment.
     *
     * <p>Accessed from the async wallet-loader task AND from the main thread,
     * so a {@link ConcurrentHashMap} is required.</p>
     */
    private final ConcurrentHashMap<String, String> claimedWallets = new ConcurrentHashMap<>();

    /**
     * In-memory list of pending offline payment notifications.
     *
     * <p>Accessed from the async tip / show-offline-tips tasks and from the
     * main-thread auto-save task, so a {@link CopyOnWriteArrayList} is used.</p>
     */
    private final List<OfflinePaymentRecord> offlinePaymentRecords = new CopyOnWriteArrayList<>();

    public JsonDBConnector(Plugin plugin)
    {
        this.plugin = plugin;
        this.dataLocation = initialiseDataDirectory();
        loadClaimedWallets();
        loadOfflinePayments();
        loadBanks();
        loadBankMembers();

        // Persist offline payments every 15 minutes in case of an unclean shutdown.
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::saveOfflinePaymentRecords,
                SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);
    }

    // -------------------------------------------------------------------------
    // IDBConnector — lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void close()
    {
        // Flush in-memory offline payments to disk so they survive a clean shutdown.
        saveOfflinePaymentRecords();
    }

    // -------------------------------------------------------------------------
    // IDBConnector — player records
    // -------------------------------------------------------------------------

    @Override
    protected PlayerRecord loadPlayerRecord(Player player)
    {
        return getPlayerRecord(player.getUniqueId(), true);
    }

    @Override
    public PlayerRecord getOfflinePlayerRecord(OfflinePlayer player)
    {
        return getPlayerRecord(player.getUniqueId(), false);
    }

    @Override
    protected boolean insertPlayerRecord(PlayerRecord playerRecord)
    {
        File file = new File(this.dataLocation, playerRecord.getPlayerUUID() + ".json");
        plugin.getLogger().info("Saving player file: " + file.getName());

        try
        {
            if (!file.exists())
            {
                file.createNewFile();
            }

            try (Writer fileWriter = new FileWriter(file, false))
            {
                new GsonBuilder().create().toJson(playerRecord, fileWriter);
            }

            plugin.getLogger().info("Player file '" + file.getName() + "' saved successfully.");
            return true;
        }
        catch (IOException ex)
        {
            plugin.getLogger().log(Level.SEVERE, "Player file save failed.", ex);
        }
        return false;
    }

    @Override
    public boolean updatePlayerRecord(PlayerRecord playerRecord)
    {
        // Overwrite the entire file — same as insert for the JSON backend.
        return insertPlayerRecord(playerRecord);
    }

    @Override
    public boolean hasPlayerRecord(Player player)
    {
        return hasPlayerRecord(player.getUniqueId());
    }

    @Override
    public boolean hasPlayerRecord(OfflinePlayer player)
    {
        return hasPlayerRecord(player.getUniqueId());
    }

    @Override
    public boolean isAlreadyAssignedToOtherPlayer(String walletAddress, Player currentPlayer)
    {
        String stored = claimedWallets.get(walletAddress);
        return stored != null && !stored.equalsIgnoreCase(currentPlayer.getUniqueId().toString());
    }

    // -------------------------------------------------------------------------
    // IDBConnector — offline payments
    // -------------------------------------------------------------------------

    @Override
    public boolean saveOfflinePayment(OfflinePaymentRecord paymentRecord)
    {
        if (paymentRecord == null)
        {
            return false;
        }

        offlinePaymentRecords.add(paymentRecord);

        return true;
    }

    @Override
    public List<OfflinePaymentRecord> getOfflinePaymentRecords(Player forPlayer)
    {
        if (forPlayer == null)
        {
            return new ArrayList<>();
        }

        return offlinePaymentRecords.stream()
                .filter(r -> r.targetPlayerUUID().equals(forPlayer.getUniqueId()))
                .toList();
    }

    @Override
    public void deleteOfflinePaymentRecords(Player forPlayer)
    {
        if (forPlayer != null)
        {
            offlinePaymentRecords.removeIf(r -> r.targetPlayerUUID().equals(forPlayer.getUniqueId()));
        }
    }

    @Override
    public double getOfflinePaymentsTotal(Player forPlayer)
    {
        if (forPlayer == null)
        {
            return 0;
        }

        return offlinePaymentRecords.stream()
                .filter(r -> r.targetPlayerUUID().equals(forPlayer.getUniqueId()))
                .mapToDouble(OfflinePaymentRecord::paymentAmount)
                .sum();
    }

    // -------------------------------------------------------------------------
    // IDBConnector — bank accounts
    // -------------------------------------------------------------------------

    @Override
    public boolean createBankRecord(BankRecord bank)
    {
        if (bankRecords.putIfAbsent(bank.getBankName(), bank) != null)
        {
            return false;
        }

        saveBanks();
        return true;
    }

    @Override
    public boolean bankNameExists(String bankName)
    {
        return bankRecords.containsKey(bankName);
    }

    @Override
    public boolean deleteBankRecord(String bankName)
    {
        BankRecord removed = bankRecords.remove(bankName);

        if (removed == null)
        {
            return false;
        }

        bankMembers.remove(bankName);
        saveBanks();
        saveBankMembers();

        return true;
    }

    @Override
    public List<String> getAllBankNames()
    {
        return new ArrayList<>(bankRecords.keySet());
    }

    @Override
    public boolean addBankMember(String bankName, String playerUuid)
    {
        bankMembers.computeIfAbsent(bankName, k -> ConcurrentHashMap.newKeySet()).add(playerUuid);
        saveBankMembers();

        return true;
    }

    @Override
    public boolean removeBankMember(String bankName, String playerUuid)
    {
        Set<String> members = bankMembers.get(bankName);

        if (members == null)
        {
            return false;
        }

        boolean removed = members.remove(playerUuid);

        if (removed)
        {
            saveBankMembers();
        }

        return removed;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private File initialiseDataDirectory()
    {
        File dir = new File(plugin.getDataFolder(), DATA_DIRECTORY);

        if (!dir.exists())
        {
            dir.mkdirs();
        }

        return dir;
    }

    /**
     * Asynchronously reads every player JSON file in the data directory and
     * builds the {@link #claimedWallets} map so that duplicate wallet detection
     * works even for offline players.
     */
    private void loadClaimedWallets()
    {
        new BukkitRunnable()
        {
            @Override
            public void run()
            {
                File[] files = dataLocation.listFiles();

                if (files == null)
                {
                    return; // directory I/O error or not a directory
                }

                Gson gson = new GsonBuilder().create();

                for (File file : files)
                {
                    try (Reader reader = new FileReader(file))
                    {
                        PlayerRecord record = gson.fromJson(reader, PlayerRecord.class);
                        if (record != null && record.getWallet() != null)
                        {
                            claimedWallets.put(record.getWallet(), record.getPlayerUUID());
                        }
                    }
                    catch (Exception ex)
                    {
                        plugin.getLogger().log(Level.WARNING,
                                "Failed to read player file during wallet index load: " + file.getName(), ex);
                    }
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private PlayerRecord getPlayerRecord(UUID playerUUID, boolean cacheRecord)
    {
        PlayerRecord cached = playerRecords.get(playerUUID);

        if (cached != null)
        {
            return cached;
        }

        File file = new File(this.dataLocation, playerUUID + ".json");
        plugin.getLogger().info("Loading player file: " + file.getName());

        try
        {
            if (!file.exists())
            {
                file.createNewFile();
            }

            PlayerRecord record;
            try (Reader reader = new FileReader(file))
            {
                record = new GsonBuilder().create().fromJson(reader, PlayerRecord.class);
            }

            if (record != null)
            {
                if (cacheRecord)
                {
                    playerRecords.put(playerUUID, record);
                }
                plugin.getLogger().info("Player loaded: " + record.getPlayerName());

                return record;
            }
        }
        catch (IOException ex)
        {
            plugin.getLogger().log(Level.SEVERE, "Loading player file failed!", ex);
        }

        return null;
    }

    private boolean hasPlayerRecord(UUID playerUUID)
    {
        return new File(this.dataLocation, playerUUID + ".json").exists();
    }

    private void loadBanks()
    {
        File file = new File(dataLocation, "banks.json");
        if (!file.exists())
        {
            return;
        }

        try (Reader reader = new FileReader(file))
        {
            BankRecord[] records = new GsonBuilder().create().fromJson(reader, BankRecord[].class);

            if (records != null)
            {
                for (BankRecord record : records)
                {
                    bankRecords.put(record.getBankName(), record);
                }
            }
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.SEVERE, "Failed to load banks.json.", ex);
        }
    }

    private void saveBanks()
    {
        File file = new File(dataLocation, "banks.json");

        try
        {
            if (!file.exists())
            {
                file.createNewFile();
            }

            try (Writer writer = new FileWriter(file, false))
            {
                new GsonBuilder().create().toJson(bankRecords.values().toArray(), writer);
            }
        }
        catch (IOException ex)
        {
            plugin.getLogger().log(Level.SEVERE, "Failed to save banks.json.", ex);
        }
    }

    private void loadBankMembers()
    {
        File file = new File(dataLocation, "bank_members.json");
        if (!file.exists())
        {
            return;
        }

        try (Reader reader = new FileReader(file))
        {
            Type type = new TypeToken<Map<String, List<String>>>() {}.getType();
            Map<String, List<String>> loaded = new GsonBuilder().create().fromJson(reader, type);

            if (loaded != null)
            {
                for (Map.Entry<String, List<String>> entry : loaded.entrySet())
                {
                    Set<String> memberSet = ConcurrentHashMap.newKeySet();
                    memberSet.addAll(entry.getValue());
                    bankMembers.put(entry.getKey(), memberSet);
                }
            }
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.SEVERE, "Failed to load bank_members.json.", ex);
        }
    }

    private void saveBankMembers()
    {
        File file = new File(dataLocation, "bank_members.json");

        try
        {
            if (!file.exists())
            {
                file.createNewFile();
            }

            Map<String, List<String>> serializable = new HashMap<>();
            for (Map.Entry<String, Set<String>> entry : bankMembers.entrySet())
            {
                serializable.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }

            try (Writer writer = new FileWriter(file, false))
            {
                new GsonBuilder().create().toJson(serializable, writer);
            }
        }
        catch (IOException ex)
        {
            plugin.getLogger().log(Level.SEVERE, "Failed to save bank_members.json.", ex);
        }
    }

    /** Persist the current in-memory offline payments list to disk. */
    private void saveOfflinePaymentRecords()
    {
        File file = new File(this.dataLocation, "offlinepayments.json");
        plugin.getLogger().info("Saving offline payments file.");

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, (JsonSerializer<LocalDateTime>)
                        (src, typeOfSrc, context) ->
                                new JsonPrimitive(src.toInstant(ZoneOffset.UTC).toEpochMilli()))
                .create();

        try
        {
            if (!file.exists())
            {
                file.createNewFile();
            }

            try (Writer writer = new FileWriter(file, false))
            {
                gson.toJson(offlinePaymentRecords.toArray(), writer);
            }

            plugin.getLogger().info("Offline payments file saved successfully.");
        }
        catch (IOException ex)
        {
            plugin.getLogger().log(Level.SEVERE, "Offline payments file save failed.", ex);
        }
    }

    private void loadOfflinePayments()
    {
        File file = new File(this.dataLocation, "offlinepayments.json");

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, (JsonDeserializer<LocalDateTime>)
                        (json, type, ctx) ->
                        {
                            Instant instant = Instant.ofEpochMilli(json.getAsJsonPrimitive().getAsLong());
                            return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
                        })
                .create();

        try
        {
            if (!file.exists())
            {
                file.createNewFile();

                return; // nothing to load from a new file
            }

            try (Reader reader = new FileReader(file))
            {
                OfflinePaymentRecord[] records = gson.fromJson(reader, OfflinePaymentRecord[].class);
                if (records != null)
                {
                    for (OfflinePaymentRecord record : records)
                    {
                        offlinePaymentRecords.add(record);
                    }
                }
            }
        }
        catch (IOException ex)
        {
            plugin.getLogger().log(Level.SEVERE, "Loading offline payments file failed!", ex);
        }
    }
}
