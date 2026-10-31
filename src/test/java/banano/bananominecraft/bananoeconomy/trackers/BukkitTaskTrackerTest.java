package banano.bananominecraft.bananoeconomy.trackers;

import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class BukkitTaskTrackerTest
{
    @Test
    void cancelAll_cancelsOnlyRunningTasks()
    {
        BukkitTaskTracker tracker = new BukkitTaskTracker();

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
        BukkitTaskTracker tracker = new BukkitTaskTracker();
        BukkitTask task = mock(BukkitTask.class);
        when(task.isCancelled()).thenReturn(false);
        tracker.track(task);

        tracker.cancelAll();
        tracker.cancelAll(); // list already cleared — must not touch the task again

        verify(task, times(1)).cancel();
    }
}
