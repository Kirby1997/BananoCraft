package banano.bananominecraft.bananoeconomy.wallet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class FilePendingBlockStoreTest
{
    private static final Logger LOG = Logger.getLogger("test");

    @TempDir Path temp;

    private FilePendingBlockStore store(String kind)
    {
        return new FilePendingBlockStore(temp.resolve("pending-blocks"), kind, LOG);
    }

    @Test
    void saveThenLoadAll_roundTrips_andUsesKindDashIndexFileNames() throws IOException
    {
        FilePendingBlockStore players = store("player");
        players.save(7, "{\"action\":\"process\"}");
        players.save(0, "{\"a\":1}");

        assertTrue(Files.exists(temp.resolve("pending-blocks/player-7.json")));
        assertTrue(Files.exists(temp.resolve("pending-blocks/player-0.json")));
        assertEquals(Map.of(7, "{\"action\":\"process\"}", 0, "{\"a\":1}"), players.loadAll());
    }

    @Test
    void playerAndBankRecordsAreSeparate()
    {
        store("player").save(3, "P");
        store("bank").save(3, "B");

        assertEquals(Map.of(3, "P"), store("player").loadAll());
        assertEquals(Map.of(3, "B"), store("bank").loadAll());
    }

    @Test
    void saveReplacesThePreviousRecord_andLeavesNoTempFile() throws IOException
    {
        FilePendingBlockStore s = store("player");
        s.save(4, "first");
        s.save(4, "second");

        assertEquals(Map.of(4, "second"), s.loadAll());
        try (Stream<Path> files = Files.list(temp.resolve("pending-blocks")))
        {
            assertEquals(1, files.count(), "only the record file may remain (no .tmp)");
        }
    }

    @Test
    void clearRemovesTheRecord_andIsIdempotent()
    {
        FilePendingBlockStore s = store("player");
        s.save(5, "x");
        s.clear(5);
        s.clear(5);
        assertTrue(s.loadAll().isEmpty());
    }

    @Test
    void loadAll_onAMissingFolder_isEmpty()
    {
        assertTrue(store("player").loadAll().isEmpty());
    }

    @Test
    void loadAll_ignoresStrayFiles() throws IOException
    {
        FilePendingBlockStore s = store("player");
        s.save(1, "ok");
        Path dir = temp.resolve("pending-blocks");
        Files.writeString(dir.resolve("player-2.json.tmp"), "torn");
        Files.writeString(dir.resolve("notes.txt"), "hi");
        Files.writeString(dir.resolve("bank-9.json"), "other");
        Files.writeString(dir.resolve("player-x.json"), "bad");

        assertEquals(Map.of(1, "ok"), s.loadAll());
    }

    @Test
    void filesAndFolderAreOwnerOnly() throws IOException
    {
        if (!temp.getFileSystem().supportedFileAttributeViews().contains("posix"))
        {
            return;
        }
        FilePendingBlockStore s = store("player");
        s.save(1, "secret-free but private");

        Set<PosixFilePermission> file = Files.getPosixFilePermissions(temp.resolve("pending-blocks/player-1.json"));
        Set<PosixFilePermission> dir = Files.getPosixFilePermissions(temp.resolve("pending-blocks"));
        assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), file);
        assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE), dir);
    }

    @Test
    void survivesAStaleTempFileFromACrash() throws IOException
    {
        FilePendingBlockStore s = store("player");
        s.save(1, "old");
        Files.writeString(temp.resolve("pending-blocks/player-1.json.tmp"), "torn write");

        s.save(1, "new");

        assertEquals(Map.of(1, "new"), s.loadAll());
    }

    @Test
    void aNewInstanceSeesWhatAnEarlierOneSaved()
    {
        store("bank").save(2, "persisted");
        assertEquals(Map.of(2, "persisted"), store("bank").loadAll());
    }
}
