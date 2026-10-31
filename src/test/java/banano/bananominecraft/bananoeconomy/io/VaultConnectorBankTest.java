package banano.bananominecraft.bananoeconomy.io;

import banano.bananominecraft.bananoeconomy.classes.BankRecord;
import banano.bananominecraft.bananoeconomy.services.BankService;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.util.List;
import java.util.UUID;

import static net.milkbowl.vault.economy.EconomyResponse.ResponseType.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VaultConnectorBankTest
{
    private static final String BANK_NAME   = "treasury";
    private static final String OWNER_UUID  = "00000000-0000-0000-0000-000000000001";

    private ServerMock      server;
    private PluginMock      plugin;
    private EconomyFuncs    economyFuncs;
    private RPC             rpc;
    private BankService     bankService;
    private VaultConnector  connector;

    @BeforeEach
    void setUp()
    {
        server       = MockBukkit.mock();
        plugin       = MockBukkit.createMockPlugin();
        economyFuncs = mock(EconomyFuncs.class);
        rpc          = mock(RPC.class);
        bankService  = mock(BankService.class);
        connector    = new VaultConnector(plugin, economyFuncs, rpc, bankService);
    }

    @AfterEach
    void tearDown()
    {
        MockBukkit.unmock();
    }

    // -------------------------------------------------------------------------
    // hasBankSupport
    // -------------------------------------------------------------------------

    @Test
    void HasBankSupportTrueWhenEnabled()
    {
        when(bankService.isBankingEnabled()).thenReturn(true);
        assertTrue(connector.hasBankSupport());
    }

    @Test
    void HasBankSupportFalseWhenDisabled()
    {
        when(bankService.isBankingEnabled()).thenReturn(false);
        assertFalse(connector.hasBankSupport());
    }

    // -------------------------------------------------------------------------
    // createBank (OfflinePlayer variant)
    // -------------------------------------------------------------------------

    @Test
    void CreateBankOfflinePlayerDisabledReturnsBanksDisabled()
    {
        when(bankService.isBankingEnabled()).thenReturn(false);
        OfflinePlayer player = offlinePlayer(OWNER_UUID);

        EconomyResponse r = connector.createBank(BANK_NAME, player);
        assertEquals(FAILURE, r.type);
        assertTrue(r.errorMessage.contains("not enabled"), r.errorMessage);
    }

    @Test
    void CreateBankOfflinePlayerSuccess()
    {
        when(bankService.isBankingEnabled()).thenReturn(true);
        when(bankService.createBank(eq(BANK_NAME), anyString()))
                .thenReturn(new BankRecord(BANK_NAME, "ban_3address", OWNER_UUID, 0));
        OfflinePlayer player = offlinePlayer(OWNER_UUID);

        EconomyResponse r = connector.createBank(BANK_NAME, player);
        assertEquals(SUCCESS, r.type);
    }

    @Test
    void CreateBankOfflinePlayerAlreadyExistsReturnsFailure()
    {
        when(bankService.isBankingEnabled()).thenReturn(true);
        when(bankService.createBank(eq(BANK_NAME), anyString())).thenReturn(null);
        OfflinePlayer player = offlinePlayer(OWNER_UUID);

        EconomyResponse r = connector.createBank(BANK_NAME, player);
        assertEquals(FAILURE, r.type);
    }

    // -------------------------------------------------------------------------
    // createBank (deprecated String variant — needs online player)
    // -------------------------------------------------------------------------

    @Test
    void CreateBankStringNotOnlineReturnsNotOnline()
    {
        when(bankService.isBankingEnabled()).thenReturn(true);
        // "ghost" player is not in the server
        EconomyResponse r = connector.createBank(BANK_NAME, "ghost");
        assertEquals(FAILURE, r.type);
        assertTrue(r.errorMessage.contains("not online"), r.errorMessage);
    }

    @Test
    void CreateBankStringOnlineSuccess()
    {
        PlayerMock player = server.addPlayer("alice");
        when(bankService.isBankingEnabled()).thenReturn(true);
        when(bankService.createBank(eq(BANK_NAME), eq(player.getUniqueId().toString())))
                .thenReturn(new BankRecord(BANK_NAME, "ban_3address", player.getUniqueId().toString(), 0));

        EconomyResponse r = connector.createBank(BANK_NAME, "alice");
        assertEquals(SUCCESS, r.type);
    }

    // -------------------------------------------------------------------------
    // deleteBank — always unsupported
    // -------------------------------------------------------------------------

    @Test
    void DeleteBankAlwaysReturnsFailure()
    {
        EconomyResponse r = connector.deleteBank(BANK_NAME);
        assertEquals(FAILURE, r.type);
        assertTrue(r.errorMessage.contains("not supported"), r.errorMessage);
    }

    // -------------------------------------------------------------------------
    // bankBalance
    // -------------------------------------------------------------------------

    @Test
    void BankBalanceDisabledReturnsBanksDisabled()
    {
        when(bankService.isBankingEnabled()).thenReturn(false);
        EconomyResponse r = connector.bankBalance(BANK_NAME);
        assertEquals(FAILURE, r.type);
    }

    @Test
    void BankBalanceNotFoundReturnsBankNotFound()
    {
        when(bankService.isBankingEnabled()).thenReturn(true);
        when(bankService.bankExists(BANK_NAME)).thenReturn(false);
        EconomyResponse r = connector.bankBalance(BANK_NAME);
        assertEquals(FAILURE, r.type);
        assertTrue(r.errorMessage.contains("not found"), r.errorMessage);
    }

    @Test
    void BankBalanceReturnsBalance()
    {
        when(bankService.isBankingEnabled()).thenReturn(true);
        when(bankService.bankExists(BANK_NAME)).thenReturn(true);
        when(bankService.getBankBalance(BANK_NAME)).thenReturn(123.0);

        EconomyResponse r = connector.bankBalance(BANK_NAME);
        assertEquals(SUCCESS, r.type);
        assertEquals(123.0, r.balance);
    }

    // -------------------------------------------------------------------------
    // bankHas
    // -------------------------------------------------------------------------

    @Test
    void BankHasSufficientReturnsSuccess()
    {
        when(bankService.isBankingEnabled()).thenReturn(true);
        when(bankService.bankExists(BANK_NAME)).thenReturn(true);
        when(bankService.getBankBalance(BANK_NAME)).thenReturn(200.0);

        EconomyResponse r = connector.bankHas(BANK_NAME, 100.0);
        assertEquals(SUCCESS, r.type);
        assertEquals(200.0, r.balance);
    }

    @Test
    void BankHasInsufficientReturnsFailure()
    {
        when(bankService.isBankingEnabled()).thenReturn(true);
        when(bankService.bankExists(BANK_NAME)).thenReturn(true);
        when(bankService.getBankBalance(BANK_NAME)).thenReturn(10.0);

        EconomyResponse r = connector.bankHas(BANK_NAME, 100.0);
        assertEquals(FAILURE, r.type);
    }

    // -------------------------------------------------------------------------
    // bankWithdraw
    // -------------------------------------------------------------------------

    @Test
    void BankWithdrawDisabledReturnsBanksDisabled()
    {
        when(bankService.isBankingEnabled()).thenReturn(false);
        assertEquals(FAILURE, connector.bankWithdraw(BANK_NAME, 50.0).type);
    }

    @Test
    void BankWithdrawZeroAmountReturnsFailure()
    {
        when(bankService.isBankingEnabled()).thenReturn(true);
        EconomyResponse r = connector.bankWithdraw(BANK_NAME, 0);
        assertEquals(FAILURE, r.type);
        assertTrue(r.errorMessage.contains("positive"), r.errorMessage);
        verify(bankService, never()).bankExists(anyString());
    }

    @Test
    void BankWithdrawNegativeAmountReturnsFailure()
    {
        when(bankService.isBankingEnabled()).thenReturn(true);
        EconomyResponse r = connector.bankWithdraw(BANK_NAME, -10.0);
        assertEquals(FAILURE, r.type);
        assertTrue(r.errorMessage.contains("positive"), r.errorMessage);
        verify(bankService, never()).bankExists(anyString());
    }

    @Test
    void BankWithdrawInsufficientFundsReturnsFailure()
    {
        when(bankService.isBankingEnabled()).thenReturn(true);
        when(bankService.bankExists(BANK_NAME)).thenReturn(true);
        when(bankService.getBankBalance(BANK_NAME)).thenReturn(10.0);

        EconomyResponse r = connector.bankWithdraw(BANK_NAME, 50.0);
        assertEquals(FAILURE, r.type);
        verify(bankService, never()).withdraw(anyString(), anyDouble());
    }

    @Test
    void BankWithdrawSuccess()
    {
        when(bankService.isBankingEnabled()).thenReturn(true);
        when(bankService.bankExists(BANK_NAME)).thenReturn(true);
        when(bankService.getBankBalance(BANK_NAME)).thenReturn(100.0, 50.0);
        when(bankService.withdraw(BANK_NAME, 50.0)).thenReturn(true);

        EconomyResponse r = connector.bankWithdraw(BANK_NAME, 50.0);
        assertEquals(SUCCESS, r.type);
        assertEquals(50.0, r.amount);
        assertEquals(50.0, r.balance);
    }

    @Test
    void BankWithdrawServiceFailureReturnsFailure()
    {
        when(bankService.isBankingEnabled()).thenReturn(true);
        when(bankService.bankExists(BANK_NAME)).thenReturn(true);
        when(bankService.getBankBalance(BANK_NAME)).thenReturn(100.0, 100.0);
        when(bankService.withdraw(BANK_NAME, 50.0)).thenReturn(false);

        EconomyResponse r = connector.bankWithdraw(BANK_NAME, 50.0);
        assertEquals(FAILURE, r.type);
    }

    // -------------------------------------------------------------------------
    // bankDeposit
    // -------------------------------------------------------------------------

    @Test
    void BankDepositDisabledReturnsBanksDisabled()
    {
        when(bankService.isBankingEnabled()).thenReturn(false);
        assertEquals(FAILURE, connector.bankDeposit(BANK_NAME, 50.0).type);
    }

    @Test
    void BankDepositZeroAmountReturnsFailure()
    {
        when(bankService.isBankingEnabled()).thenReturn(true);
        EconomyResponse r = connector.bankDeposit(BANK_NAME, 0);
        assertEquals(FAILURE, r.type);
        assertTrue(r.errorMessage.contains("positive"), r.errorMessage);
        verify(bankService, never()).bankExists(anyString());
    }

    @Test
    void BankDepositNegativeAmountReturnsFailure()
    {
        when(bankService.isBankingEnabled()).thenReturn(true);
        EconomyResponse r = connector.bankDeposit(BANK_NAME, -5.0);
        assertEquals(FAILURE, r.type);
        assertTrue(r.errorMessage.contains("positive"), r.errorMessage);
        verify(bankService, never()).bankExists(anyString());
    }

    @Test
    void BankDepositNotFoundReturnsBankNotFound()
    {
        when(bankService.isBankingEnabled()).thenReturn(true);
        when(bankService.bankExists(BANK_NAME)).thenReturn(false);
        EconomyResponse r = connector.bankDeposit(BANK_NAME, 50.0);
        assertEquals(FAILURE, r.type);
    }

    @Test
    void BankDepositSuccess()
    {
        when(bankService.isBankingEnabled()).thenReturn(true);
        when(bankService.bankExists(BANK_NAME)).thenReturn(true);
        when(bankService.deposit(BANK_NAME, 50.0)).thenReturn(true);
        when(bankService.getBankBalance(BANK_NAME)).thenReturn(150.0);

        EconomyResponse r = connector.bankDeposit(BANK_NAME, 50.0);
        assertEquals(SUCCESS, r.type);
        assertEquals(50.0, r.amount);
        assertEquals(150.0, r.balance);
    }

    @Test
    void BankDepositServiceFailureReturnsFailure()
    {
        when(bankService.isBankingEnabled()).thenReturn(true);
        when(bankService.bankExists(BANK_NAME)).thenReturn(true);
        when(bankService.deposit(BANK_NAME, 50.0)).thenReturn(false);
        when(bankService.getBankBalance(BANK_NAME)).thenReturn(100.0);

        assertEquals(FAILURE, connector.bankDeposit(BANK_NAME, 50.0).type);
    }

    // -------------------------------------------------------------------------
    // isBankOwner (OfflinePlayer)
    // -------------------------------------------------------------------------

    @Test
    void IsBankOwnerOfflinePlayerDisabledReturnsBanksDisabled()
    {
        when(bankService.isBankingEnabled()).thenReturn(false);
        assertEquals(FAILURE, connector.isBankOwner(BANK_NAME, offlinePlayer(OWNER_UUID)).type);
    }

    @Test
    void IsBankOwnerOfflinePlayerIsOwnerReturnsSuccess()
    {
        when(bankService.isBankingEnabled()).thenReturn(true);
        OfflinePlayer player = offlinePlayer(OWNER_UUID);
        when(bankService.isBankOwner(BANK_NAME, OWNER_UUID)).thenReturn(true);

        assertEquals(SUCCESS, connector.isBankOwner(BANK_NAME, player).type);
    }

    @Test
    void IsBankOwnerOfflinePlayerNotOwnerReturnsFailure()
    {
        when(bankService.isBankingEnabled()).thenReturn(true);
        OfflinePlayer player = offlinePlayer(OWNER_UUID);
        when(bankService.isBankOwner(BANK_NAME, OWNER_UUID)).thenReturn(false);

        assertEquals(FAILURE, connector.isBankOwner(BANK_NAME, player).type);
    }

    // -------------------------------------------------------------------------
    // isBankOwner (deprecated String variant)
    // -------------------------------------------------------------------------

    @Test
    void IsBankOwnerStringNotOnlineReturnsNotOnline()
    {
        when(bankService.isBankingEnabled()).thenReturn(true);
        EconomyResponse r = connector.isBankOwner(BANK_NAME, "ghost");
        assertEquals(FAILURE, r.type);
        assertTrue(r.errorMessage.contains("not online"), r.errorMessage);
    }

    @Test
    void IsBankOwnerStringOnlineIsOwner()
    {
        PlayerMock player = server.addPlayer("bob");
        when(bankService.isBankingEnabled()).thenReturn(true);
        when(bankService.isBankOwner(BANK_NAME, player.getUniqueId().toString())).thenReturn(true);

        assertEquals(SUCCESS, connector.isBankOwner(BANK_NAME, "bob").type);
    }

    // -------------------------------------------------------------------------
    // isBankMember (OfflinePlayer)
    // -------------------------------------------------------------------------

    @Test
    void IsBankMemberOfflinePlayerIsMemberReturnsSuccess()
    {
        when(bankService.isBankingEnabled()).thenReturn(true);
        OfflinePlayer player = offlinePlayer(OWNER_UUID);
        when(bankService.isBankMember(BANK_NAME, OWNER_UUID)).thenReturn(true);

        assertEquals(SUCCESS, connector.isBankMember(BANK_NAME, player).type);
    }

    @Test
    void IsBankMemberOfflinePlayerNotMemberReturnsFailure()
    {
        when(bankService.isBankingEnabled()).thenReturn(true);
        OfflinePlayer player = offlinePlayer(OWNER_UUID);
        when(bankService.isBankMember(BANK_NAME, OWNER_UUID)).thenReturn(false);

        assertEquals(FAILURE, connector.isBankMember(BANK_NAME, player).type);
    }

    // -------------------------------------------------------------------------
    // isBankMember (deprecated String variant)
    // -------------------------------------------------------------------------

    @Test
    void IsBankMemberStringNotOnlineReturnsNotOnline()
    {
        when(bankService.isBankingEnabled()).thenReturn(true);
        EconomyResponse r = connector.isBankMember(BANK_NAME, "ghost");
        assertEquals(FAILURE, r.type);
        assertTrue(r.errorMessage.contains("not online"), r.errorMessage);
    }

    @Test
    void IsBankMemberStringOnlineIsMember()
    {
        PlayerMock player = server.addPlayer("carol");
        when(bankService.isBankingEnabled()).thenReturn(true);
        when(bankService.isBankMember(BANK_NAME, player.getUniqueId().toString())).thenReturn(true);

        assertEquals(SUCCESS, connector.isBankMember(BANK_NAME, "carol").type);
    }

    // -------------------------------------------------------------------------
    // getBanks
    // -------------------------------------------------------------------------

    @Test
    void GetBanksDisabledReturnsEmptyList()
    {
        when(bankService.isBankingEnabled()).thenReturn(false);
        assertTrue(connector.getBanks().isEmpty());
    }

    @Test
    void GetBanksEnabledReturnsList()
    {
        when(bankService.isBankingEnabled()).thenReturn(true);
        when(bankService.getBanks()).thenReturn(List.of("treasury", "guild"));

        assertEquals(List.of("treasury", "guild"), connector.getBanks());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private OfflinePlayer offlinePlayer(String uuid)
    {
        OfflinePlayer p = mock(OfflinePlayer.class);
        when(p.getUniqueId()).thenReturn(UUID.fromString(uuid));
        return p;
    }
}
