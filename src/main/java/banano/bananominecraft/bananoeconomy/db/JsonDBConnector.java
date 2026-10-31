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

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.regex.Pattern;

public class JsonDBConnector extends BaseDBConnector
{
    /** Sub-directory inside the plugin data folder used for all JSON files. */
    private static final String DATA_DIRECTORY = "data";

    /** Repeating auto-save interval in ticks (18 000 t = 15 minutes). */
    private static final long SAVE_INTERVAL_TICKS = 18_000L;

    /** Player files are named {@code <uuid>.json}; anything else (banks.json etc.) is not a player. */
    private static final Pattern PLAYER_FILE_NAME = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.json$");

    private final Plugin plugin;
    private final File   dataLocation;

    /**
     * wallet address → player UUID string for every wallet that has ever been
     * assigned.  Used by {@link #isAlreadyAssignedToOtherPlayer} to prevent
     * duplicate wallet assignment.
     *
     * <p>Accessed from async tasks AND from the main thread, so a
     * {@link ConcurrentHashMap} is required.</p>
     */
    private final ConcurrentHashMap<String, String> claimedWallets = new ConcurrentHashMap<>();

    /**
     * One monitor per target file so that concurrent saves of the same file are
     * serialised. Only one lock is ever held at a time, so this cannot deadlock.
     */
    private final ConcurrentHashMap<String, Object> fileLocks = new ConcurrentHashMap<>();

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

        if (!writeJsonAtomically(file, new GsonBuilder().create(), () -> playerRecord))
        {
            plugin.getLogger().severe("Player file save failed: " + file.getName());
            return false;
        }

        if (playerRecord.getWallet() != null)
        {
            claimedWallets.put(playerRecord.getWallet(), playerRecord.getPlayerUUID());
        }

        plugin.getLogger().info("Player file '" + file.getName() + "' saved successfully.");
        return true;
    }

    @Override
    public boolean updatePlayerRecord(PlayerRecord playerRecord)
    {
        // Overwrite the entire file — same as insert for the JSON backend.
        return insertPlayerRecord(playerRecord);
    }

    @Override
    public boolean updatePlayerWallet(Player player, String wallet)
    {
        PlayerRecord existing = player == null ? null : getPlayerRecord(player);
        String oldWallet = existing == null ? null : existing.getWallet();

        boolean updated = super.updatePlayerWallet(player, wallet);

        // insertPlayerRecord has already indexed the new wallet; drop the stale entry.
        if (updated && oldWallet != null && !oldWallet.equals(wallet))
        {
            claimedWallets.remove(oldWallet, player.getUniqueId().toString());
        }

        return updated;
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

        // Persist immediately so a crash cannot lose the payment notification. The
        // record stays in memory even if this write fails and is retried by the
        // periodic flush / shutdown save.
        saveOfflinePaymentRecords();

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
        if (forPlayer != null
                && offlinePaymentRecords.removeIf(r -> r.targetPlayerUUID().equals(forPlayer.getUniqueId())))
        {
            saveOfflinePaymentRecords();
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
     * Reads every player JSON file ({@code <uuid>.json}) in the data directory and
     * builds the {@link #claimedWallets} map so that duplicate wallet detection
     * works even for offline players. Runs synchronously in the constructor so
     * the index is complete before any player can join.
     */
    private void loadClaimedWallets()
    {
        File[] files = dataLocation.listFiles((dir, name) -> PLAYER_FILE_NAME.matcher(name).matches());

        if (files == null)
        {
            return; // directory I/O error or not a directory
        }

        Gson gson = new GsonBuilder().create();

        for (File file : files)
        {
            if (file.length() == 0)
            {
                continue;
            }

            try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8))
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

    private PlayerRecord getPlayerRecord(UUID playerUUID, boolean cacheRecord)
    {
        PlayerRecord cached = playerRecords.get(playerUUID);

        if (cached != null)
        {
            return cached;
        }

        File file = new File(this.dataLocation, playerUUID + ".json");

        // A missing or empty file means "no record". Never create the file here:
        // an empty file would later make hasPlayerRecord() lie.
        if (!file.isFile() || file.length() == 0)
        {
            return null;
        }

        plugin.getLogger().info("Loading player file: " + file.getName());

        PlayerRecord record = readPlayerFile(file);

        if (record != null)
        {
            if (cacheRecord)
            {
                // One instance per player: a concurrent load must not replace a record that
                // something else (e.g. a freeze) is already using.
                PlayerRecord cached = playerRecords.putIfAbsent(playerUUID, record);
                if (cached != null)
                {
                    record = cached;
                }
            }
            plugin.getLogger().info("Player loaded: " + record.getPlayerName());
        }

        return record;
    }

    /**
     * Parse a player file. Returns {@code null} (and logs) if the file cannot be
     * read or does not contain a valid player record.
     */
    private PlayerRecord readPlayerFile(File file)
    {
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8))
        {
            PlayerRecord record = new GsonBuilder().create().fromJson(reader, PlayerRecord.class);

            if (record == null || record.getPlayerUUID() == null)
            {
                return null;
            }

            return record;
        }
        catch (IOException | JsonParseException ex)
        {
            plugin.getLogger().log(Level.SEVERE, "Loading player file failed: " + file.getName(), ex);
        }

        return null;
    }

    private boolean hasPlayerRecord(UUID playerUUID)
    {
        if (playerRecords.containsKey(playerUUID))
        {
            return true;
        }

        File file = new File(this.dataLocation, playerUUID + ".json");

        // Empty or unparseable files (e.g. left behind by older versions) count as "no record".
        return file.isFile() && file.length() > 0 && readPlayerFile(file) != null;
    }

    /**
     * Serialise the value produced by {@code payload} to {@code target} via a temp
     * file in the same directory followed by an atomic rename, so a crash mid-write
     * can never leave a truncated file. Writes to the same file are serialised; the
     * payload is evaluated inside the lock so the newest snapshot always wins.
     *
     * @return {@code true} if the file was written successfully
     */
    private boolean writeJsonAtomically(File target, Gson gson, Supplier<Object> payload)
    {
        Object lock = fileLocks.computeIfAbsent(target.getAbsolutePath(), k -> new Object());

        synchronized (lock)
        {
            Path targetPath = target.toPath();
            Path tmp = null;

            try
            {
                tmp = Files.createTempFile(targetPath.getParent(), target.getName() + ".", ".tmp");

                try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8))
                {
                    gson.toJson(payload.get(), writer);
                }

                try
                {
                    Files.move(tmp, targetPath,
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                }
                catch (AtomicMoveNotSupportedException ex)
                {
                    Files.move(tmp, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }

                tmp = null; // moved into place; nothing to clean up
                return true;
            }
            catch (IOException | JsonIOException ex)
            {
                plugin.getLogger().log(Level.SEVERE, "Failed to write " + target.getName() + ".", ex);
            }
            finally
            {
                if (tmp != null)
                {
                    try
                    {
                        Files.deleteIfExists(tmp);
                    }
                    catch (IOException ignored)
                    {
                        // best effort
                    }
                }
            }
        }

        return false;
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

        writeJsonAtomically(file, new GsonBuilder().create(), () -> bankRecords.values().toArray());
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

        writeJsonAtomically(file, new GsonBuilder().create(), () ->
        {
            Map<String, List<String>> serializable = new HashMap<>();
            for (Map.Entry<String, Set<String>> entry : bankMembers.entrySet())
            {
                serializable.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            return serializable;
        });
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

        if (writeJsonAtomically(file, gson, offlinePaymentRecords::toArray))
        {
            plugin.getLogger().info("Offline payments file saved successfully.");
        }
        else
        {
            plugin.getLogger().severe("Offline payments file save failed.");
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

        if (!file.isFile() || file.length() == 0)
        {
            return; // nothing to load
        }

        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8))
        {
            OfflinePaymentRecord[] records = gson.fromJson(reader, OfflinePaymentRecord[].class);
            if (records != null)
            {
                for (OfflinePaymentRecord record : records)
                {
                    if (record != null)
                    {
                        offlinePaymentRecords.add(record);
                    }
                }
            }
        }
        catch (JsonParseException ex)
        {
            // Keep the unreadable file aside: payments are now persisted on every
            // change, so otherwise the next save would silently overwrite it.
            File backup = new File(this.dataLocation, file.getName() + ".corrupt-" + System.currentTimeMillis());
            boolean kept = file.renameTo(backup);
            plugin.getLogger().log(Level.SEVERE, "Offline payments file is corrupt"
                    + (kept ? "; moved to " + backup.getName() : "") + ".", ex);
        }
        catch (IOException ex)
        {
            plugin.getLogger().log(Level.SEVERE, "Loading offline payments file failed!", ex);
        }
    }
}
