package banano.bananominecraft.bananoeconomy.trackers;

import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BukkitTaskTrackerTest
{
    /**
     * Uses only the cancelled flag to decide "done", so the tests don't depend on
     * whether some other test left a (Mock)Bukkit server installed.
     */
    private static BukkitTaskTracker newTracker()
    {
        return new BukkitTaskTracker(BukkitTask::isCancelled);
    }

    @Test
    void cancelAll_cancelsOnlyRunningTasks()
    {
        BukkitTaskTracker tracker = newTracker();

        BukkitTask running   = mock(BukkitTask.class);
        BukkitTask cancelled = mock(BukkitTask.class);
        when(running.isCancelled()).thenReturn(false);
        when(cancelled.isCancelled()).thenReturn(true);

        tracker.track(running);
        tracker.track(cancelled);

        tracker.cancelAll();

        verify(running).cancel();
        verify(cancelled, never()).cancel();
    }

    @Test
    void cancelAll_isIdempotent_afterClear()
    {
        BukkitTaskTracker tracker = newTracker();
        BukkitTask task = mock(BukkitTask.class);
        when(task.isCancelled()).thenReturn(false);
        tracker.track(task);

        tracker.cancelAll();
        tracker.cancelAll(); // list already cleared — must not touch the task again

        verify(task, times(1)).cancel();
    }

    @Test
    void track_prunesDoneTasks()
    {
        Set<BukkitTask> done = new HashSet<>();
        BukkitTaskTracker tracker = new BukkitTaskTracker(done::contains);

        BukkitTask first  = mock(BukkitTask.class);
        BukkitTask second = mock(BukkitTask.class);
        BukkitTask third  = mock(BukkitTask.class);

        tracker.track(first);
        tracker.track(second);
        assertEquals(2, tracker.size());

        // first finishes; tracking another task drops it
        done.add(first);
        tracker.track(third);
        assertEquals(2, tracker.size());

        tracker.cancelAll();
        verify(first, never()).cancel();
        verify(second).cancel();
        verify(third).cancel();
    }
}
