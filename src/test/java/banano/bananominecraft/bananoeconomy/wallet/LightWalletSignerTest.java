package banano.bananominecraft.bananoeconomy.wallet;

import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import banano.bananominecraft.bananoeconomy.exceptions.UnknownOutcomeError;
import banano.bananominecraft.bananoeconomy.io.HttpTransport;
import banano.bananominecraft.bananoeconomy.io.RPC;
import banano.bananominecraft.bananoeconomy.wallet.crypto.NanoKeys;
import banano.bananominecraft.bananoeconomy.wallet.light.LightWallet;
import banano.bananominecraft.bananoeconomy.wallet.light.LightWalletException;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link LightWalletSigner} against mocked {@link LightWallet}s: address to index mapping, the
 * "never sign for the wrong account" checks, and the LightWalletException to TransactionError mapping.
 */
class LightWalletSignerTest
{
    private static String address(int n)
    {
        return NanoKeys.address(NanoKeys.publicKey(NanoKeys.privateKey(new byte[32], n)), "ban_");
    }

    private static final String MASTER      = address(0);
    private static final String PLAYER_ONE  = address(1);
    private static final String PLAYER_TWO  = address(2);
    private static final String LEGACY      = address(3);
    private static final String BANK_ADDR   = address(4);
    private static final String RECIPIENT   = address(5);
    private static final BigInteger AMOUNT  = new BigInteger("150000000000000000000000000000");

    private LightWallet playerWallet;
    private LightWallet bankWallet;
    private LightWalletSigner.IndexLookup lookup;
    private LightWalletSigner signer;

    @BeforeEach
    void setUp()
    {
        playerWallet = mock(LightWallet.class);
        bankWallet = mock(LightWallet.class);
        // Unstubbed addresses must read as "no stored index" (null), not Mockito's default 0 for Integer.
        lookup = mock(LightWalletSigner.IndexLookup.class, invocation -> null);

        when(playerWallet.address(0)).thenReturn(MASTER);
        when(playerWallet.address(1)).thenReturn(PLAYER_ONE);
        when(playerWallet.address(2)).thenReturn(PLAYER_TWO);
        when(playerWallet.address(3)).thenReturn(LEGACY);
        when(bankWallet.address(0)).thenReturn(MASTER); // deliberately the same string: must not be special-cased
        when(bankWallet.address(7)).thenReturn(BANK_ADDR);

        when(lookup.playerIndex(PLAYER_ONE)).thenReturn(1);
        when(lookup.playerIndex(PLAYER_TWO)).thenReturn(2);
        when(lookup.bankIndex(BANK_ADDR)).thenReturn(7);

        signer = new LightWalletSigner(playerWallet, bankWallet, lookup, Logger.getLogger("test"));
    }

    // -------------------------------------------------------------------------
    // wallet lifecycle: local only
    // -------------------------------------------------------------------------

    @Test
    void isLocal()
    {
        assertTrue(signer.isLocal());
    }

    @Test
    void createWallet_isALocalNoOp_returningTheLightWalletMarkers()
    {
        assertEquals(WalletSigner.LIGHT_PLAYER_WALLET_ID, signer.createWallet(WalletSigner.Wallet.PLAYER));
        assertEquals(WalletSigner.LIGHT_BANK_WALLET_ID, signer.createWallet(WalletSigner.Wallet.BANK));
        verifyNoInteractions(playerWallet, bankWallet, lookup);
    }

    @Test
    void createWallet_fails_whenTheSeedIsUnavailable()
    {
        LightWalletSigner noSeed = new LightWalletSigner(null, null, lookup, Logger.getLogger("test"));

        assertNull(noSeed.createWallet(WalletSigner.Wallet.PLAYER));
        assertNull(noSeed.createWallet(WalletSigner.Wallet.BANK));
        assertEquals(WalletSigner.Status.UNREACHABLE, noSeed.walletStatus(WalletSigner.Wallet.PLAYER));
    }

    @Test
    void walletStatus_exists_whenSeedUsable()
    {
        assertEquals(WalletSigner.Status.EXISTS, signer.walletStatus(WalletSigner.Wallet.PLAYER));
        assertEquals(WalletSigner.Status.EXISTS, signer.walletStatus(WalletSigner.Wallet.BANK));
    }

    @Test
    void accountCreate_derivesLocally_atTheExplicitIndex()
    {
        assertEquals(PLAYER_TWO, signer.accountCreate(WalletSigner.LIGHT_PLAYER_WALLET_ID, 2));
        assertEquals(BANK_ADDR, signer.accountCreate(WalletSigner.LIGHT_BANK_WALLET_ID, 7));
        assertEquals(MASTER, signer.accountCreate(WalletSigner.LIGHT_PLAYER_WALLET_ID, 0));
    }

    @Test
    void accountCreate_returnsNull_forNoIndexOrUnknownWalletOrMissingSeed()
    {
        assertNull(signer.accountCreate(WalletSigner.LIGHT_PLAYER_WALLET_ID, -1));
        assertNull(signer.accountCreate("a-node-wallet-id", 1));
        assertNull(new LightWalletSigner(null, null, lookup, Logger.getLogger("test"))
                .accountCreate(WalletSigner.LIGHT_PLAYER_WALLET_ID, 1));
    }

    // -------------------------------------------------------------------------
    // index resolution
    // -------------------------------------------------------------------------

    @Test
    void send_fromMasterWallet_usesIndexZero() throws Exception
    {
        when(playerWallet.send(0, RECIPIENT, AMOUNT)).thenReturn("HASH");

        assertEquals("HASH", signer.send(WalletSigner.LIGHT_PLAYER_WALLET_ID, MASTER, RECIPIENT, AMOUNT, "id"));
        verify(playerWallet).send(0, RECIPIENT, AMOUNT);
        verifyNoInteractions(lookup);
    }

    @Test
    void send_fromPlayerWallet_usesTheStoredWalletIndex() throws Exception
    {
        when(playerWallet.send(2, RECIPIENT, AMOUNT)).thenReturn("HASH");

        assertEquals("HASH", signer.send(WalletSigner.LIGHT_PLAYER_WALLET_ID, PLAYER_TWO, RECIPIENT, AMOUNT, "id"));
        verify(playerWallet).send(2, RECIPIENT, AMOUNT);
        verify(bankWallet, never()).send(anyInt(), anyString(), any());
    }

    @Test
    void send_fromBankAccount_usesTheBankWalletAndBankIndex() throws Exception
    {
        when(bankWallet.send(7, MASTER, AMOUNT)).thenReturn("BANK-HASH");

        assertEquals("BANK-HASH", signer.send(WalletSigner.LIGHT_BANK_WALLET_ID, BANK_ADDR, MASTER, AMOUNT, "id"));
        verify(playerWallet, never()).send(anyInt(), anyString(), any());
    }

    @Test
    void send_unmatchedLegacyPlayer_failsClearly_andNeverSigns() throws Exception
    {
        // LEGACY is derivable from the seed but its record was never backfilled: lookup returns null.
        TransactionError error = assertThrows(TransactionError.class,
                () -> signer.send(WalletSigner.LIGHT_PLAYER_WALLET_ID, LEGACY, RECIPIENT, AMOUNT, "id"));

        assertEquals(LightWalletSigner.UNMATCHED_MESSAGE, error.getUserError());
        assertFalse(error instanceof UnknownOutcomeError);
        verify(playerWallet, never()).send(anyInt(), anyString(), any());
    }

    @Test
    void receive_unmatchedLegacyPlayer_failsClearly() throws Exception
    {
        TransactionError error = assertThrows(TransactionError.class,
                () -> signer.receive(WalletSigner.LIGHT_PLAYER_WALLET_ID, LEGACY, "BLOCK"));

        assertEquals(LightWalletSigner.UNMATCHED_MESSAGE, error.getUserError());
        verify(playerWallet, never()).receive(anyInt(), anyString());
    }

    @Test
    void send_refusesAStoredIndexThatDoesNotDeriveTheAddress() throws Exception
    {
        when(lookup.playerIndex(PLAYER_ONE)).thenReturn(2); // wrong: index 2 derives PLAYER_TWO

        assertThrows(TransactionError.class,
                () -> signer.send(WalletSigner.LIGHT_PLAYER_WALLET_ID, PLAYER_ONE, RECIPIENT, AMOUNT, "id"));
        verify(playerWallet, never()).send(anyInt(), anyString(), any());
    }

    @Test
    void anAddressOfTheOtherSeedIsNeverSigned() throws Exception
    {
        // A bank address presented with the player wallet id has no player index.
        assertThrows(TransactionError.class,
                () -> signer.send(WalletSigner.LIGHT_PLAYER_WALLET_ID, BANK_ADDR, RECIPIENT, AMOUNT, "id"));
        verify(playerWallet, never()).send(anyInt(), anyString(), any());
    }

    @Test
    void unknownWalletId_isRefused() throws Exception
    {
        assertThrows(TransactionError.class,
                () -> signer.send("some-node-wallet-id", PLAYER_ONE, RECIPIENT, AMOUNT, "id"));
        assertThrows(TransactionError.class,
                () -> signer.receive("", PLAYER_ONE, "BLOCK"));
        assertThrows(TransactionError.class,
                () -> signer.setRepresentative(null, PLAYER_ONE, RECIPIENT));
    }

    @Test
    void missingSeed_failsWithAClearError_forEveryAction()
    {
        LightWalletSigner noSeed = new LightWalletSigner(null, null, lookup, Logger.getLogger("test"));

        assertEquals(LightWalletSigner.UNAVAILABLE_MESSAGE, assertThrows(TransactionError.class,
                () -> noSeed.send(WalletSigner.LIGHT_PLAYER_WALLET_ID, MASTER, RECIPIENT, AMOUNT, "id")).getUserError());
        assertThrows(TransactionError.class,
                () -> noSeed.receive(WalletSigner.LIGHT_PLAYER_WALLET_ID, MASTER, "BLOCK"));
        assertThrows(TransactionError.class,
                () -> noSeed.setRepresentative(WalletSigner.LIGHT_BANK_WALLET_ID, BANK_ADDR, RECIPIENT));
    }

    // -------------------------------------------------------------------------
    // receive / change representative
    // -------------------------------------------------------------------------

    @Test
    void receive_delegatesWithTheResolvedIndex() throws Exception
    {
        when(playerWallet.receive(1, "SEND-HASH")).thenReturn("RECEIVE-HASH");

        assertEquals("RECEIVE-HASH", signer.receive(WalletSigner.LIGHT_PLAYER_WALLET_ID, PLAYER_ONE, "SEND-HASH"));
    }

    @Test
    void setRepresentative_delegatesWithTheResolvedIndex() throws Exception
    {
        when(playerWallet.changeRepresentative(0, RECIPIENT)).thenReturn("CHANGE-HASH");

        assertEquals("CHANGE-HASH",
                signer.setRepresentative(WalletSigner.LIGHT_PLAYER_WALLET_ID, MASTER, RECIPIENT));
    }

    // -------------------------------------------------------------------------
    // LightWalletException -> TransactionError
    // -------------------------------------------------------------------------

    private TransactionError sendFailing(LightWalletException.Kind kind) throws Exception
    {
        when(playerWallet.send(anyInt(), anyString(), any(BigInteger.class)))
                .thenThrow(new LightWalletException(kind, "detail-" + kind));

        return assertThrows(TransactionError.class,
                () -> signer.send(WalletSigner.LIGHT_PLAYER_WALLET_ID, PLAYER_ONE, RECIPIENT, AMOUNT, "id"));
    }

    @Test
    void unknownOutcome_isNeverAPlainFailure() throws Exception
    {
        TransactionError error = sendFailing(LightWalletException.Kind.UNKNOWN_OUTCOME);

        assertInstanceOf(UnknownOutcomeError.class, error);
        assertTrue(error.getUserError().contains("/history"), error.getUserError());
        assertTrue(error.getUserError().toLowerCase().contains("unknown"), error.getUserError());
    }

    @Test
    void insufficientBalance_mapsToTheNodeStyleMessage() throws Exception
    {
        TransactionError error = sendFailing(LightWalletException.Kind.INSUFFICIENT_BALANCE);

        assertEquals("Insufficient balance", error.getUserError());
        assertFalse(error instanceof UnknownOutcomeError);
    }

    @Test
    void unavailable_asksToTryAgainLater() throws Exception
    {
        TransactionError error = sendFailing(LightWalletException.Kind.UNAVAILABLE);

        assertTrue(error.getUserError().toLowerCase().contains("try again"), error.getUserError());
        assertFalse(error instanceof UnknownOutcomeError);
    }

    @Test
    void rejected_carriesTheReason() throws Exception
    {
        TransactionError error = sendFailing(LightWalletException.Kind.REJECTED);

        assertTrue(error.getUserError().contains("detail-REJECTED"), error.getUserError());
        assertFalse(error instanceof UnknownOutcomeError);
    }

    @Test
    void invalid_carriesTheReason() throws Exception
    {
        TransactionError error = sendFailing(LightWalletException.Kind.INVALID);

        assertTrue(error.getUserError().contains("detail-INVALID"), error.getUserError());
        assertFalse(error instanceof UnknownOutcomeError);
    }

    @Test
    void receiveAndRepresentativeFailuresAreMappedToo() throws Exception
    {
        when(playerWallet.receive(anyInt(), anyString()))
                .thenThrow(new LightWalletException(LightWalletException.Kind.UNKNOWN_OUTCOME, "lost"));
        when(playerWallet.changeRepresentative(anyInt(), anyString()))
                .thenThrow(new LightWalletException(LightWalletException.Kind.REJECTED, "bad rep"));

        assertInstanceOf(UnknownOutcomeError.class, assertThrows(TransactionError.class,
                () -> signer.receive(WalletSigner.LIGHT_PLAYER_WALLET_ID, PLAYER_ONE, "BLOCK")));
        assertTrue(assertThrows(TransactionError.class,
                () -> signer.setRepresentative(WalletSigner.LIGHT_PLAYER_WALLET_ID, PLAYER_ONE, RECIPIENT))
                .getUserError().contains("bad rep"));
    }

    // -------------------------------------------------------------------------
    // Through RPC: BAN -> raw conversion is exactly RPC.toRaw, and no seed/key ever hits the transport
    // -------------------------------------------------------------------------

    @Test
    void viaRpc_convertsBanToRawWithTheConfiguredMultiplier_andNeverTouchesTheNodeTransport() throws Exception
    {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        ConfigEngine config = mock(ConfigEngine.class);
        when(config.getMultiplier()).thenReturn("100000000000000000000000000000");
        when(config.getWalletId()).thenReturn(WalletSigner.LIGHT_PLAYER_WALLET_ID);
        HttpTransport http = mock(HttpTransport.class);
        RPC rpc = new RPC(plugin, config, http, signer);

        when(playerWallet.send(1, PLAYER_TWO, AMOUNT)).thenReturn("HASH");

        assertEquals("HASH", rpc.sendTransaction(PLAYER_ONE, PLAYER_TWO, 1.5));
        verify(playerWallet).send(1, PLAYER_TWO, AMOUNT);

        assertTrue(rpc.walletCreate());
        assertTrue(rpc.bankWalletCreate());
        assertEquals(PLAYER_TWO, rpc.accountCreate(2));
        assertEquals(RPC.WalletStatus.EXISTS, rpc.walletStatus());

        verifyNoInteractions(http);
        verify(config, never()).setWalletId(anyString());
        verify(config, never()).save();
    }

    // -------------------------------------------------------------------------
    // work precompute hint
    // -------------------------------------------------------------------------

    @Test
    void precomputeWork_resolvesTheIndex_andAsksTheLightWallet()
    {
        signer.precomputeWork(WalletSigner.LIGHT_PLAYER_WALLET_ID, MASTER);
        signer.precomputeWork(WalletSigner.LIGHT_PLAYER_WALLET_ID, PLAYER_ONE);
        signer.precomputeWork(WalletSigner.LIGHT_BANK_WALLET_ID, BANK_ADDR);

        verify(playerWallet).precomputeWork(0);
        verify(playerWallet).precomputeWork(1);
        verify(bankWallet).precomputeWork(7);
    }

    @Test
    void precomputeWork_isBestEffort_forUnmatchedAccountsAndMissingSeeds()
    {
        assertDoesNotThrow(() -> signer.precomputeWork(WalletSigner.LIGHT_PLAYER_WALLET_ID, LEGACY));
        assertDoesNotThrow(() -> signer.precomputeWork("nonsense", MASTER));
        verify(playerWallet, never()).precomputeWork(anyInt());

        LightWalletSigner noSeed = new LightWalletSigner(null, null, lookup, Logger.getLogger("test"));
        assertDoesNotThrow(() -> noSeed.precomputeWork(WalletSigner.LIGHT_PLAYER_WALLET_ID, MASTER));
    }
}
