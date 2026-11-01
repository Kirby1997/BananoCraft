package banano.bananominecraft.bananoeconomy.wallet;

import banano.bananominecraft.bananoeconomy.wallet.light.PendingBlockStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link PendingBlockStore} that keeps one file per unresolved block,
 * {@code <directory>/<kind>-<index>.json} ({@code kind} is {@code player} or {@code bank}), so a
 * restart can never forget a published-but-unconfirmed block.
 *
 * <p>Writes go to a temporary file in the same directory (owner-only permissions, fsync) and are
 * moved over the target atomically, so a crash leaves either the old or the new record, never a torn
 * one. The files hold signed blocks only - no secrets - but are still owner-only.</p>
 */
public final class FilePendingBlockStore implements PendingBlockStore
{
    private final Path directory;
    private final String kind;
    private final Logger log;
    private final Pattern fileName;

    /**
     * @param directory the folder for the record files (created, owner-only, on first use)
     * @param kind      file name prefix, e.g. {@code player} or {@code bank}
     */
    public FilePendingBlockStore(Path directory, String kind, Logger log)
    {
        this.directory = directory;
        this.kind = kind;
        this.log = log;
        this.fileName = Pattern.compile("^" + Pattern.quote(kind) + "-(\\d{1,10})\\.json$");
    }

    private Path fileFor(int index)
    {
        return directory.resolve(kind + "-" + index + ".json");
    }

    @Override
    public void save(int index, String blockJson)
    {
        Path target = fileFor(index);
        Path temp = null;
        try
        {
            ensureDirectory();
            temp = directory.resolve(kind + "-" + index + ".json.tmp");
            Files.deleteIfExists(temp);
            createOwnerOnlyFile(temp);

            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE))
            {
                ByteBuffer data = ByteBuffer.wrap(blockJson.getBytes(StandardCharsets.UTF_8));
                while (data.hasRemaining())
                {
                    channel.write(data);
                }
                channel.force(true);
            }

            try
            {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException e)
            {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (IOException e)
        {
            if (temp != null)
            {
                try
                {
                    Files.deleteIfExists(temp);
                }
                catch (IOException ignored)
                {
                    // nothing more to do
                }
            }
            throw new UncheckedIOException("Could not store the pending block of " + kind + " account " + index, e);
        }
    }

    @Override
    public void clear(int index)
    {
        try
        {
            Files.deleteIfExists(fileFor(index));
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Could not clear the pending block of " + kind + " account " + index, e);
        }
    }

    @Override
    public Map<Integer, String> loadAll()
    {
        Map<Integer, String> records = new HashMap<>();
        if (!Files.isDirectory(directory))
        {
            return records;
        }

        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory))
        {
            for (Path file : files)
            {
                Matcher matcher = fileName.matcher(file.getFileName().toString());
                if (!matcher.matches())
                {
                    continue;
                }

                try
                {
                    records.put(Integer.parseInt(matcher.group(1)),
                            Files.readString(file, StandardCharsets.UTF_8));
                }
                catch (IOException | NumberFormatException e)
                {
                    log.log(Level.SEVERE, "Could not read the pending block file " + file
                            + " - that account may have an unresolved block.", e);
                }
            }
        }
        catch (IOException e)
        {
            log.log(Level.SEVERE, "Could not list the pending block folder " + directory, e);
        }

        return records;
    }

    private void ensureDirectory() throws IOException
    {
        if (Files.isDirectory(directory))
        {
            return;
        }
        if (directory.getFileSystem().supportedFileAttributeViews().contains("posix"))
        {
            Files.createDirectories(directory, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rwx------")));
        }
        else
        {
            Files.createDirectories(directory);
        }
    }

    private static void createOwnerOnlyFile(Path path) throws IOException
    {
        if (path.getFileSystem().supportedFileAttributeViews().contains("posix"))
        {
            Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            return;
        }

        Files.createFile(path);
        java.io.File file = path.toFile();
        file.setReadable(false, false);
        file.setReadable(true, true);
        file.setWritable(false, false);
        file.setWritable(true, true);
        file.setExecutable(false, false);
    }
}
