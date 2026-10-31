package banano.bananominecraft.bananoeconomy.trackers;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Thread-safe {@link TaskTracker} implementation backed by a
 * {@link CopyOnWriteArrayList}.
 *
 * <p>A single instance is created in {@code Main.onEnable()} and injected into
 * every class that schedules async work.  {@link #cancelAll()} is called from
 * {@code Main.onDisable()} to drain all in-flight tasks before the plugin
 * unloads.</p>
 *
 * <p>Tasks that have already finished or been cancelled are pruned whenever a
 * new task is tracked, so the list stays bounded by the number of tasks that
 * are actually still pending or running.</p>
 */
public class BukkitTaskTracker implements TaskTracker
{
    private final List<BukkitTask> activeTasks = new CopyOnWriteArrayList<>();

    /** Decides whether a tracked task is done (finished or cancelled) and can be dropped. */
    private final Predicate<BukkitTask> isDone;

    public BukkitTaskTracker()
    {
        this(BukkitTaskTracker::isDoneOnScheduler);
    }

    /** Test seam — lets unit tests decide which tasks count as done without a running server. */
    BukkitTaskTracker(Predicate<BukkitTask> isDone)
    {
        this.isDone = isDone;
    }

    @Override
    public void track(BukkitTask task)
    {
        activeTasks.removeIf(isDone);
        activeTasks.add(task);
    }

    /** Number of tasks currently held — exposed for tests. */
    int size()
    {
        return activeTasks.size();
    }

    /**
     * Cancel every tracked task that has not already been cancelled, then clear
     * the list so the references can be garbage-collected. Cancelling a task that
     * has already finished is a harmless no-op.
     */
    public void cancelAll()
    {
        activeTasks.forEach(task ->
        {
            if (!task.isCancelled())
            {
                task.cancel();
            }
        });
        activeTasks.clear();
    }

    /**
     * A task is done once it is cancelled, or once the scheduler neither has it
     * queued nor is currently running it (i.e. a one-shot task that completed).
     * Without a server (unit tests) only the cancelled flag is consulted.
     */
    private static boolean isDoneOnScheduler(BukkitTask task)
    {
        if (task.isCancelled())
        {
            return true;
        }

        if (Bukkit.getServer() == null)
        {
            return false;
        }

        BukkitScheduler scheduler = Bukkit.getScheduler();
        int taskId = task.getTaskId();
        return !scheduler.isQueued(taskId) && !scheduler.isCurrentlyRunning(taskId);
    }
}
