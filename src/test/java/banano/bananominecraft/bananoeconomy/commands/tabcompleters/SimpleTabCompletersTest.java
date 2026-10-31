package banano.bananominecraft.bananoeconomy.commands.tabcompleters;

import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tab completers that do not touch the Bukkit singleton — they only build a
 * candidate list and filter it via {@link org.bukkit.util.StringUtil}, which is a
 * pure utility. No MockBukkit required.
 */
class SimpleTabCompletersTest
{
    // --- WithdrawTabCompleter ---

    @Test
    void withdraw_firstArg_offersAllAndAmount_whenEmpty()
    {
        List<String> r = new WithdrawTabCompleter().onTabComplete(null, null, "w", new String[] { "" });
        assertTrue(r.contains("all"));
        assertTrue(r.contains("[amount]"));
    }

    @Test
    void withdraw_firstArg_filtersByPrefix()
    {
        List<String> r = new WithdrawTabCompleter().onTabComplete(null, null, "w", new String[] { "a" });
        assertEquals(List.of("all"), r); // "[amount]" filtered out, and only added when blank anyway
    }

    @Test
    void withdraw_secondArg_offersAddressPrefix_whenAmountBlank()
    {
        List<String> r = new WithdrawTabCompleter().onTabComplete(null, null, "w", new String[] { "", "" });
        assertTrue(r.contains("ban_"));
    }

    // --- DepositTabCompleter ---

    @Test
    void deposit_offersServer()
    {
        List<String> r = new DepositTabCompleter().onTabComplete(null, null, "d", new String[] { "" });
        assertEquals(List.of("server"), r);
    }

    @Test
    void deposit_filtersOutNonMatching()
    {
        List<String> r = new DepositTabCompleter().onTabComplete(null, null, "d", new String[] { "x" });
        assertTrue(r.isEmpty());
    }

    // --- TransactionHistoryTabCompleter ---

    @Test
    void history_firstArg_offersAllAndRange_whenEmpty()
    {
        ConfigEngine configEngine = mock(ConfigEngine.class);
        when(configEngine.getMaximumTransactionHistoryCount()).thenReturn(25);

        List<String> r = new TransactionHistoryTabCompleter(configEngine)
                .onTabComplete(null, null, "h", new String[] { "" });

        assertTrue(r.contains("all"));
        assertTrue(r.stream().anyMatch(s -> s.contains("MAX:25")));
    }

    @Test
    void history_secondArg_returnsNothing()
    {
        ConfigEngine configEngine = mock(ConfigEngine.class);
        List<String> r = new TransactionHistoryTabCompleter(configEngine)
                .onTabComplete(null, null, "h", new String[] { "all", "" });
        assertTrue(r.isEmpty());
    }
}
