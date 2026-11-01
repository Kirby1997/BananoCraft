package banano.bananominecraft.bananoeconomy.io;

import banano.bananominecraft.bananoeconomy.classes.TransactionRecord;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.enums.TransactionDirection;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import banano.bananominecraft.bananoeconomy.exceptions.UnknownOutcomeError;
import banano.bananominecraft.bananoeconomy.wallet.NodeWalletSigner;
import banano.bananominecraft.bananoeconomy.wallet.WalletSigner;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Exercises the JSON-RPC parsing and error-handling logic in {@link RPC}.
 *
 * <p>The HTTP transport is injected as an {@link HttpTransport} mock, so every
 * branch is reachable with canned node responses — no live socket and, unlike the
 * previous design, no Mockito spy on the class under test.</p>
 */
@MockitoSettings(strictness = Strictness.LENIENT)
class RPCTest
{
    // Two genuinely Validator-valid ban_ addresses (first char 1/3, allowed alphabet).
    private static final String SENDER    = "ban_3t6k35gi95xu6tergt6p69ck76ogmitsa8mnijtpxm9fkcm736xtoncuohr3";
    private static final String RECIPIENT = "ban_1t6k35gi95xu6tergt6p69ck76ogmitsa8mnijtpxm9fkcm736xtoncuohr3";

    /** 1 BAN expressed in raw, given the default multiplier below. */
    private static final String ONE_BAN_RAW = "100000000000000000000000000000";
    private static final String MULTIPLIER  = "100000000000000000000000000000";

    @Mock private Plugin plugin;
    @Mock private ConfigEngine configEngine;
    @Mock private HttpTransport http;

    private RPC rpc;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(configEngine.getMultiplier()).thenReturn(MULTIPLIER);
        when(configEngine.getWalletId()).thenReturn("WALLET_ID");
        when(configEngine.getMasterWallet()).thenReturn(SENDER);
        when(configEngine.getNodeAddress()).thenReturn("http://localhost:7072");
        when(configEngine.getWalletSeed()).thenReturn("SEED");

        rpc = new RPC(plugin, configEngine, http);
    }

    /** Stub the HTTP seam so the next RPC call sees {@code json}. */
    private void stubResponse(String json) throws Exception
    {
        when(http.post(anyString(), anyString())).thenReturn(json);
    }

    private void stubFailure() throws Exception
    {
        when(http.post(anyString(), anyString())).thenThrow(new java.io.IOException("network down"));
    }

    // -------------------------------------------------------------------------
    // raw <-> BAN conversion
    // -------------------------------------------------------------------------

    @Test
    void fromRaw_convertsUsingMultiplier()
    {
        assertEquals(1.0, rpc.fromRaw(new BigDecimal(ONE_BAN_RAW)));
        assertEquals(0.0, rpc.fromRaw(BigDecimal.ZERO));
        assertEquals(2.5, rpc.fromRaw(new BigDecimal("250000000000000000000000000000")));
    }

    @Test
    void fromRaw_roundsDown_soBalanceIsNeverOverstated()
    {
        // 1.99999999999999999999999999999 BAN: HALF_UP at 10 dp would report 2.0.
        assertEquals(1.9999999999, rpc.fromRaw(new BigDecimal("199999999999999999999999999999")));
    }

    // -------------------------------------------------------------------------
    // accountCreate
    // -------------------------------------------------------------------------

    @Test
    void accountCreate_returnsAccount_onSuccess() throws Exception
    {
        stubResponse("{\"account\":\"" + RECIPIENT + "\"}");
        assertEquals(RECIPIENT, rpc.accountCreate(0));
    }

    @Test
    void accountCreate_withoutIndex_returnsAccount() throws Exception
    {
        stubResponse("{\"account\":\"" + RECIPIENT + "\"}");
        assertEquals(RECIPIENT, rpc.accountCreate(-1));
    }

    @Test
    void accountCreate_returnsSentinel_onTransportFailure() throws Exception
    {
        stubFailure();
        assertEquals(RPC.ACCOUNT_CREATION_FAILED, rpc.accountCreate(0));
    }

    @Test
    void accountCreate_returnsSentinel_onMalformedJson() throws Exception
    {
        stubResponse("not json");
        assertEquals(RPC.ACCOUNT_CREATION_FAILED, rpc.accountCreate(0));
    }

    @Test
    void accountCreate_returnsSentinel_whenRpcControlDisabled() throws Exception
    {
        stubResponse("{\"error\":\"RPC control is disabled\"}");
        assertEquals(RPC.ACCOUNT_CREATION_FAILED, rpc.accountCreate(0));
    }

    @Test
    void accountCreate_returnsSentinel_whenAccountMissing() throws Exception
    {
        stubResponse("{}");
        assertEquals(RPC.ACCOUNT_CREATION_FAILED, rpc.accountCreate(0));
    }

    // -------------------------------------------------------------------------
    // sendTransaction
    // -------------------------------------------------------------------------

    @Test
    void sendTransaction_returnsBlockHash_onSuccess() throws Exception
    {
        stubResponse("{\"block\":\"ABC123\"}");
        assertEquals("ABC123", rpc.sendTransaction(SENDER, RECIPIENT, 1.0));
    }

    @Test
    void sendTransaction_throws_whenAmountNotPositive()
    {
        TransactionError ex = assertThrows(TransactionError.class,
                () -> rpc.sendTransaction(SENDER, RECIPIENT, 0));
        assertTrue(ex.getMessage().toLowerCase().contains("greater than zero"));
    }

    @Test
    void sendTransaction_throws_whenAmountNotFinite()
    {
        assertThrows(TransactionError.class,
                () -> rpc.sendTransaction(SENDER, RECIPIENT, Double.NaN));
        assertThrows(TransactionError.class,
                () -> rpc.sendTransaction(SENDER, RECIPIENT, Double.POSITIVE_INFINITY));
    }

    @Test
    void sendTransaction_throws_whenAmountRoundsToZeroRaw()
    {
        // Smaller than 1 raw with the test multiplier.
        assertThrows(TransactionError.class,
                () -> rpc.sendTransaction(SENDER, RECIPIENT, 1e-40));
    }

    @Test
    void sendTransaction_throws_TransactionError_whenMultiplierMisconfigured()
    {
        when(configEngine.getMultiplier()).thenReturn("not a number");
        assertThrows(TransactionError.class,
                () -> rpc.sendTransaction(SENDER, RECIPIENT, 1.0));
    }

    @Test
    void sendTransaction_includesUniqueIdempotencyId() throws Exception
    {
        stubResponse("{\"block\":\"ABC123\"}");
        rpc.sendTransaction(SENDER, RECIPIENT, 1.0);
        rpc.sendTransaction(SENDER, RECIPIENT, 1.0);

        org.mockito.ArgumentCaptor<String> payloads = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(http, times(2)).post(anyString(), payloads.capture());

        String id1 = com.google.gson.JsonParser.parseString(payloads.getAllValues().get(0))
                .getAsJsonObject().get("id").getAsString();
        String id2 = com.google.gson.JsonParser.parseString(payloads.getAllValues().get(1))
                .getAsJsonObject().get("id").getAsString();
        assertFalse(id1.isEmpty());
        assertNotEquals(id1, id2);
    }

    @Test
    void sendTransaction_throws_TransactionError_onNonObjectResponse() throws Exception
    {
        stubResponse("[1,2,3]");
        TransactionError ex = assertThrows(TransactionError.class,
                () -> rpc.sendTransaction(SENDER, RECIPIENT, 1.0));
        assertEquals("Send transaction failed", ex.getMessage());
    }

    @Test
    void sendTransaction_throws_TransactionError_whenBlockNotAString() throws Exception
    {
        stubResponse("{\"block\":{}}");
        assertThrows(TransactionError.class,
                () -> rpc.sendTransaction(SENDER, RECIPIENT, 1.0));
    }

    @Test
    void sendTransaction_throws_whenAddressInvalid()
    {
        assertThrows(TransactionError.class,
                () -> rpc.sendTransaction("not-an-address", RECIPIENT, 1.0));
        assertThrows(TransactionError.class,
                () -> rpc.sendTransaction(SENDER, "not-an-address", 1.0));
    }

    @Test
    void sendTransaction_throws_onTransportFailure() throws Exception
    {
        stubFailure();
        TransactionError ex = assertThrows(TransactionError.class,
                () -> rpc.sendTransaction(SENDER, RECIPIENT, 1.0));
        assertEquals("Send transaction failed", ex.getMessage());
    }

    @Test
    void sendTransaction_throws_whenNodeReturnsError() throws Exception
    {
        stubResponse("{\"error\":\"Insufficient balance\"}");
        TransactionError ex = assertThrows(TransactionError.class,
                () -> rpc.sendTransaction(SENDER, RECIPIENT, 1.0));
        assertEquals("Insufficient balance", ex.getMessage());
    }

    @Test
    void sendTransaction_throws_whenBlockMissing() throws Exception
    {
        stubResponse("{\"something\":\"else\"}");
        TransactionError ex = assertThrows(TransactionError.class,
                () -> rpc.sendTransaction(SENDER, RECIPIENT, 1.0));
        assertTrue(ex.getMessage().toLowerCase().contains("missing block"));
    }

    // -------------------------------------------------------------------------
    // setRepresentative
    // -------------------------------------------------------------------------

    @Test
    void setRepresentative_returnsBlockHash_onSuccess() throws Exception
    {
        stubResponse("{\"block\":\"REP123\"}");
        assertEquals("REP123", rpc.setRepresentative(SENDER, RECIPIENT, "WALLET_ID"));
    }

    @Test
    void setRepresentative_throws_whenAddressInvalid()
    {
        assertThrows(TransactionError.class,
                () -> rpc.setRepresentative("not-an-address", RECIPIENT, "WALLET_ID"));
        assertThrows(TransactionError.class,
                () -> rpc.setRepresentative(SENDER, "not-an-address", "WALLET_ID"));
    }

    @Test
    void setRepresentative_throws_onTransportFailure() throws Exception
    {
        stubFailure();
        TransactionError ex = assertThrows(TransactionError.class,
                () -> rpc.setRepresentative(SENDER, RECIPIENT, "WALLET_ID"));
        assertEquals("Set representative failed", ex.getMessage());
    }

    @Test
    void setRepresentative_throws_whenNodeReturnsError() throws Exception
    {
        stubResponse("{\"error\":\"Representative not found\"}");
        TransactionError ex = assertThrows(TransactionError.class,
                () -> rpc.setRepresentative(SENDER, RECIPIENT, "WALLET_ID"));
        assertEquals("Representative not found", ex.getMessage());
    }

    @Test
    void setRepresentative_throws_whenBlockMissing() throws Exception
    {
        stubResponse("{\"something\":\"else\"}");
        TransactionError ex = assertThrows(TransactionError.class,
                () -> rpc.setRepresentative(SENDER, RECIPIENT, "WALLET_ID"));
        assertTrue(ex.getMessage().toLowerCase().contains("missing block"));
    }

    // -------------------------------------------------------------------------
    // receivablePending
    // -------------------------------------------------------------------------

    @Test
    void receivablePending_returnsHashes_onSuccess() throws Exception
    {
        stubResponse("{\"blocks\":[\"HASH1\",\"HASH2\"]}");
        assertEquals(List.of("HASH1", "HASH2"), rpc.receivablePending(SENDER));
    }

    @Test
    void receivablePending_includesActiveUnconfirmedBlocks() throws Exception
    {
        // Without include_active, the node excludes not-yet-confirmed blocks by default —
        // which is exactly the case where a block explorer shows a deposit that /receive can't
        // see yet. Assert we opt in to including them.
        stubResponse("{\"blocks\":[\"HASH1\"]}");
        rpc.receivablePending(SENDER);
        verify(http).post(anyString(), contains("\"include_active\":true"));
    }

    @Test
    void receivablePending_boundsTheBatchSize() throws Exception
    {
        // Each block costs a node receive with proof-of-work and dust can be sent by anyone.
        stubResponse("{\"blocks\":[\"HASH1\"]}");
        rpc.receivablePending(SENDER);
        verify(http).post(anyString(), contains("\"count\":\"" + RPC.MAX_RECEIVABLE_PER_CALL + "\""));
    }

    @Test
    void receivablePending_returnsEmpty_whenNodeReportsEmptyBacklogAsBareString() throws Exception
    {
        // The node reports "nothing pending" as a bare string rather than an empty array.
        stubResponse("{\"blocks\":\"\"}");
        assertTrue(rpc.receivablePending(SENDER).isEmpty());
    }

    @Test
    void receivablePending_returnsEmpty_whenKeyMissing() throws Exception
    {
        stubResponse("{}");
        assertTrue(rpc.receivablePending(SENDER).isEmpty());
    }

    @Test
    void receivablePending_returnsEmpty_whenNodeReturnsError() throws Exception
    {
        stubResponse("{\"error\":\"Account not found\"}");
        assertTrue(rpc.receivablePending(SENDER).isEmpty());
    }

    @Test
    void receivablePending_returnsEmpty_onTransportFailure() throws Exception
    {
        stubFailure();
        assertTrue(rpc.receivablePending(SENDER).isEmpty());
    }

    // -------------------------------------------------------------------------
    // receiveBlock
    // -------------------------------------------------------------------------

    @Test
    void receiveBlock_returnsBlockHash_onSuccess() throws Exception
    {
        stubResponse("{\"block\":\"RECEIVE123\"}");
        assertEquals("RECEIVE123", rpc.receiveBlock("WALLET_ID", SENDER, "PENDINGHASH"));
    }

    @Test
    void receiveBlock_twoArgOverload_usesConfiguredWalletId() throws Exception
    {
        stubResponse("{\"block\":\"RECEIVE123\"}");
        assertEquals("RECEIVE123", rpc.receiveBlock(SENDER, "PENDINGHASH"));
        verify(http).post(anyString(), contains("\"wallet\":\"WALLET_ID\""));
    }

    @Test
    void receiveBlock_throws_whenAddressInvalid()
    {
        assertThrows(TransactionError.class,
                () -> rpc.receiveBlock("WALLET_ID", "not-an-address", "PENDINGHASH"));
    }

    @Test
    void receiveBlock_throws_onTransportFailure() throws Exception
    {
        stubFailure();
        TransactionError ex = assertThrows(TransactionError.class,
                () -> rpc.receiveBlock("WALLET_ID", SENDER, "PENDINGHASH"));
        assertEquals("Receive transaction failed", ex.getMessage());
    }

    @Test
    void receiveBlock_throws_whenNodeReturnsError() throws Exception
    {
        stubResponse("{\"error\":\"Block not found\"}");
        TransactionError ex = assertThrows(TransactionError.class,
                () -> rpc.receiveBlock("WALLET_ID", SENDER, "PENDINGHASH"));
        assertEquals("Block not found", ex.getMessage());
    }

    @Test
    void receiveBlock_throws_TransactionError_onNonObjectResponse() throws Exception
    {
        stubResponse("\"just a string\"");
        assertThrows(TransactionError.class,
                () -> rpc.receiveBlock("WALLET_ID", SENDER, "PENDINGHASH"));
    }

    @Test
    void receiveBlock_throws_whenBlockMissing() throws Exception
    {
        stubResponse("{\"something\":\"else\"}");
        TransactionError ex = assertThrows(TransactionError.class,
                () -> rpc.receiveBlock("WALLET_ID", SENDER, "PENDINGHASH"));
        assertTrue(ex.getMessage().toLowerCase().contains("missing block"));
    }

    // -------------------------------------------------------------------------
    // representativesOnline
    // -------------------------------------------------------------------------

    @Test
    void representativesOnline_returnsAddresses_onSuccess() throws Exception
    {
        stubResponse("{\"representatives\":[\"" + SENDER + "\",\"" + RECIPIENT + "\"]}");
        assertEquals(List.of(SENDER, RECIPIENT), rpc.representativesOnline());
    }

    @Test
    void representativesOnline_returnsEmpty_whenKeyMissing() throws Exception
    {
        stubResponse("{}");
        assertTrue(rpc.representativesOnline().isEmpty());
    }

    @Test
    void representativesOnline_returnsEmpty_onTransportFailure() throws Exception
    {
        stubFailure();
        assertTrue(rpc.representativesOnline().isEmpty());
    }

    // -------------------------------------------------------------------------
    // getRepresentative
    // -------------------------------------------------------------------------

    @Test
    void getRepresentative_returnsAddress_onSuccess() throws Exception
    {
        stubResponse("{\"representative\":\"" + RECIPIENT + "\"}");
        assertEquals(RECIPIENT, rpc.getRepresentative(SENDER));
    }

    @Test
    void getRepresentative_returnsNull_whenAccountNotFound() throws Exception
    {
        stubResponse("{\"error\":\"Account not found\"}");
        assertNull(rpc.getRepresentative(SENDER));
    }

    @Test
    void getRepresentative_returnsNull_whenRepresentativeKeyMissing() throws Exception
    {
        stubResponse("{}");
        assertNull(rpc.getRepresentative(SENDER));
    }

    @Test
    void getRepresentative_returnsNull_onTransportFailure() throws Exception
    {
        stubFailure();
        assertNull(rpc.getRepresentative(SENDER));
    }

    // -------------------------------------------------------------------------
    // getBalance
    // -------------------------------------------------------------------------

    @Test
    void getBalance_convertsRawBalance() throws Exception
    {
        stubResponse("{\"balance\":\"" + ONE_BAN_RAW + "\"}");
        assertEquals(1.0, rpc.getBalance(SENDER));
    }

    @Test
    void getBalance_returnsZero_whenAccountNotFound() throws Exception
    {
        stubResponse("{\"error\":\"Account not found\"}");
        assertEquals(0.0, rpc.getBalance(SENDER));
    }

    @Test
    void getBalance_returnsZero_onTransportFailure() throws Exception
    {
        stubFailure();
        assertEquals(0.0, rpc.getBalance(SENDER));
    }

    @Test
    void getBalance_returnsZero_onUnexpectedError() throws Exception
    {
        stubResponse("{\"error\":\"Some other error\"}");
        assertEquals(0.0, rpc.getBalance(SENDER));
    }

    // -------------------------------------------------------------------------
    // getTransactionHistory
    // -------------------------------------------------------------------------

    @Test
    void getTransactionHistory_parsesSendAndReceive() throws Exception
    {
        String json = "{\"history\":["
                + "{\"type\":\"send\",\"account\":\"" + RECIPIENT + "\",\"amount\":\"" + ONE_BAN_RAW
                + "\",\"local_timestamp\":\"1700000000\",\"hash\":\"HASH_SEND\",\"confirmed\":\"true\"},"
                + "{\"type\":\"receive\",\"account\":\"" + SENDER + "\",\"amount\":\"" + ONE_BAN_RAW
                + "\",\"local_timestamp\":\"1700000001\",\"hash\":\"HASH_RECV\"}"
                + "]}";
        stubResponse(json);

        List<TransactionRecord> records = rpc.getTransactionHistory(SENDER, 10);

        assertNotNull(records);
        assertEquals(2, records.size());

        TransactionRecord send = records.get(0);
        assertEquals(TransactionDirection.Send, send.direction());
        assertEquals(1.0, send.amount());
        assertEquals("HASH_SEND", send.transactionHash());
        assertTrue(send.confirmed());

        TransactionRecord receive = records.get(1);
        assertEquals(TransactionDirection.Receive, receive.direction());
        assertEquals("HASH_RECV", receive.transactionHash());
        assertFalse(receive.confirmed());
    }

    @Test
    void getTransactionHistory_skipsItemsWithoutType() throws Exception
    {
        String json = "{\"history\":["
                + "{\"account\":\"" + RECIPIENT + "\",\"amount\":\"" + ONE_BAN_RAW + "\"}"
                + "]}";
        stubResponse(json);

        List<TransactionRecord> records = rpc.getTransactionHistory(SENDER, 10);
        assertNotNull(records);
        assertTrue(records.isEmpty());
    }

    @Test
    void getTransactionHistory_returnsEmpty_whenNoHistoryKey() throws Exception
    {
        stubResponse("{\"account\":\"" + SENDER + "\"}");
        List<TransactionRecord> records = rpc.getTransactionHistory(SENDER, 10);
        assertNotNull(records);
        assertTrue(records.isEmpty());
    }

    @Test
    void getTransactionHistory_returnsEmpty_whenAccountNotFound() throws Exception
    {
        // Quirk: an error-only response has no "history" key, so the happy path returns
        // an empty list before the "Account not found" -> null branch is ever reached.
        // That null branch only fires if parsing an individual history item throws.
        stubResponse("{\"error\":\"Account not found\"}");
        List<TransactionRecord> records = rpc.getTransactionHistory(SENDER, 10);
        assertNotNull(records);
        assertTrue(records.isEmpty());
    }

    @Test
    void getTransactionHistory_returnsNull_onTransportFailure() throws Exception
    {
        stubFailure();
        assertNull(rpc.getTransactionHistory(SENDER, 10));
    }

    // -------------------------------------------------------------------------
    // getBlockCount
    // -------------------------------------------------------------------------

    @Test
    void getBlockCount_returnsCheckedAndUnchecked() throws Exception
    {
        stubResponse("{\"count\":\"123\",\"unchecked\":\"4\"}");
        assertEquals(List.of("123", "4"), rpc.getBlockCount());
    }

    @Test
    void getBlockCount_returnsNullPlaceholders_onFailure() throws Exception
    {
        stubFailure();
        assertEquals(List.of("Null", "Null"), rpc.getBlockCount());
    }

    // -------------------------------------------------------------------------
    // wallet_exists
    // -------------------------------------------------------------------------

    @Test
    void walletExists_false_whenWalletNotFound() throws Exception
    {
        stubResponse("{\"error\":\"Wallet not found\"}");
        assertFalse(rpc.wallet_exists());
    }

    @Test
    void walletExists_false_whenBadWalletNumber() throws Exception
    {
        stubResponse("{\"error\":\"Bad wallet number\"}");
        assertFalse(rpc.wallet_exists());
    }

    @Test
    void walletExists_true_whenBalancesReturned() throws Exception
    {
        // No "error" key and a "balances" object -> the node confirmed the wallet.
        stubResponse("{\"balances\":{}}");
        assertTrue(rpc.wallet_exists());
    }

    @Test
    void walletExists_false_onTransportFailure() throws Exception
    {
        // Must not claim the wallet exists when the node can't be reached.
        stubFailure();
        assertFalse(rpc.wallet_exists());
    }

    @Test
    void walletStatus_exists_whenBalancesReturned() throws Exception
    {
        stubResponse("{\"balances\":{}}");
        assertEquals(RPC.WalletStatus.EXISTS, rpc.walletStatus());
    }

    @Test
    void walletStatus_missing_whenWalletNotFound() throws Exception
    {
        stubResponse("{\"error\":\"Wallet not found\"}");
        assertEquals(RPC.WalletStatus.MISSING, rpc.walletStatus());
    }

    @Test
    void walletStatus_unreachable_onTransportFailure() throws Exception
    {
        stubFailure();
        assertEquals(RPC.WalletStatus.UNREACHABLE, rpc.walletStatus());
    }

    @Test
    void walletStatus_unreachable_onOtherNodeError() throws Exception
    {
        stubResponse("{\"error\":\"RPC control is disabled\"}");
        assertEquals(RPC.WalletStatus.UNREACHABLE, rpc.walletStatus());
    }

    @Test
    void walletStatus_unreachable_onMalformedResponse() throws Exception
    {
        stubResponse("[]");
        assertEquals(RPC.WalletStatus.UNREACHABLE, rpc.walletStatus());
    }

    @Test
    void bankWalletStatus_missing_whenNotConfigured() throws Exception
    {
        when(configEngine.getBankWalletId()).thenReturn("");
        assertEquals(RPC.WalletStatus.MISSING, rpc.bankWalletStatus());
        verify(http, never()).post(anyString(), anyString());
    }

    @Test
    void bankWalletStatus_unreachable_onTransportFailure() throws Exception
    {
        when(configEngine.getBankWalletId()).thenReturn("BANK_WALLET_ID");
        stubFailure();
        assertEquals(RPC.WalletStatus.UNREACHABLE, rpc.bankWalletStatus());
        assertFalse(rpc.bankWalletExists());
    }

    @Test
    void bankWalletStatus_exists_whenBalancesReturned() throws Exception
    {
        when(configEngine.getBankWalletId()).thenReturn("BANK_WALLET_ID");
        stubResponse("{\"balances\":{}}");
        assertEquals(RPC.WalletStatus.EXISTS, rpc.bankWalletStatus());
    }

    // -------------------------------------------------------------------------
    // ping
    // -------------------------------------------------------------------------

    @Test
    void ping_true_onValidBlockCount() throws Exception
    {
        stubResponse("{\"count\":\"100\",\"unchecked\":\"0\"}");
        assertTrue(rpc.ping());
    }

    @Test
    void ping_false_onTransportFailure() throws Exception
    {
        stubFailure();
        assertFalse(rpc.ping());
    }

    @Test
    void ping_false_onErrorOrMalformedResponse() throws Exception
    {
        stubResponse("{\"error\":\"Unknown command\"}");
        assertFalse(rpc.ping());
    }

    // -------------------------------------------------------------------------
    // wallet_contains
    // -------------------------------------------------------------------------

    @Test
    void walletContains_true_whenExistsOne() throws Exception
    {
        stubResponse("{\"exists\":\"1\"}");
        assertTrue(rpc.wallet_contains(SENDER));
    }

    @Test
    void walletContains_false_whenExistsZero() throws Exception
    {
        stubResponse("{\"exists\":\"0\"}");
        assertFalse(rpc.wallet_contains(SENDER));
    }

    @Test
    void walletContains_false_onTransportFailure() throws Exception
    {
        stubFailure();
        assertFalse(rpc.wallet_contains(SENDER));
    }

    // -------------------------------------------------------------------------
    // walletCreate
    // -------------------------------------------------------------------------

    @Test
    void walletCreate_persistsWalletId_onSuccess() throws Exception
    {
        stubResponse("{\"wallet\":\"NEW_WALLET_ID\"}");
        when(configEngine.save()).thenReturn(true);
        assertTrue(rpc.walletCreate());
        verify(configEngine).setWalletId("NEW_WALLET_ID");
        verify(configEngine).save();
    }

    @Test
    void walletCreate_refusesBlankSeed() throws Exception
    {
        when(configEngine.getWalletSeed()).thenReturn("");
        assertFalse(rpc.walletCreate());
        verify(http, never()).post(anyString(), anyString());
    }

    @Test
    void walletCreate_refusesToSendSeedToPublicPlainHttpNode() throws Exception
    {
        when(configEngine.getNodeAddress()).thenReturn("http://203.0.113.5:7072"); // TEST-NET-3, public
        assertFalse(rpc.walletCreate());
        verify(http, never()).post(anyString(), anyString());
        verify(configEngine, never()).setWalletId(anyString());
    }

    @Test
    void isSeedSafeEndpoint_allowsHttpsAndPrivateHttpOnly()
    {
        assertTrue(RPC.isSeedSafeEndpoint("https://node.example.org/"));
        assertTrue(RPC.isSeedSafeEndpoint("http://127.0.0.1:7072"));
        assertTrue(RPC.isSeedSafeEndpoint("http://192.168.1.20:7072"));
        assertTrue(RPC.isSeedSafeEndpoint("http://10.0.0.5:7072/"));
        assertFalse(RPC.isSeedSafeEndpoint("http://203.0.113.5:7072"));
        assertFalse(RPC.isSeedSafeEndpoint("ftp://127.0.0.1/"));
        assertFalse(RPC.isSeedSafeEndpoint("not a url"));
    }

    @Test
    void walletCreate_doesNothing_onFailure() throws Exception
    {
        stubFailure();
        assertFalse(rpc.walletCreate());
        verify(configEngine, never()).setWalletId(anyString());
        verify(configEngine, never()).save();
    }

    @Test
    void walletCreate_doesNothing_whenNodeReturnsError() throws Exception
    {
        stubResponse("{\"error\":\"RPC control is disabled\"}");
        assertFalse(rpc.walletCreate());
        verify(configEngine, never()).setWalletId(anyString());
        verify(configEngine, never()).save();
    }

    @Test
    void bankWalletCreate_persistsWalletId_onSuccess() throws Exception
    {
        when(configEngine.getBankWalletSeed()).thenReturn("BANK_SEED");
        when(configEngine.save()).thenReturn(true);
        stubResponse("{\"wallet\":\"BANK_WALLET_ID\"}");
        assertTrue(rpc.bankWalletCreate());
        verify(configEngine).setBankWalletId("BANK_WALLET_ID");
    }

    // -------------------------------------------------------------------------
    // Wallet actions are delegated to the injected WalletSigner
    // -------------------------------------------------------------------------

    @Test
    void defaultConstructor_usesTheNodeWalletSigner()
    {
        assertInstanceOf(NodeWalletSigner.class, rpc.getSigner());
    }

    private RPC rpcWith(WalletSigner signer)
    {
        return new RPC(plugin, configEngine, http, signer);
    }

    @Test
    void sendTransaction_delegatesToSigner_withRawAmountWalletAndSendId() throws Exception
    {
        WalletSigner signer = mock(WalletSigner.class);
        when(signer.send(anyString(), anyString(), anyString(), any(BigInteger.class), anyString()))
                .thenReturn("HASH");

        String hash = rpcWith(signer).sendTransaction(SENDER, RECIPIENT, 1.5, "W-ID", "SEND-ID");

        assertEquals("HASH", hash);
        verify(signer).send("W-ID", SENDER, RECIPIENT, new BigInteger("150000000000000000000000000000"), "SEND-ID");
        verifyNoInteractions(http);
    }

    @Test
    void sendTransaction_defaultWalletId_comesFromConfig() throws Exception
    {
        WalletSigner signer = mock(WalletSigner.class);
        when(signer.send(anyString(), anyString(), anyString(), any(BigInteger.class), anyString()))
                .thenReturn("HASH");

        rpcWith(signer).sendTransaction(SENDER, RECIPIENT, 1.0);

        verify(signer).send(eq("WALLET_ID"), eq(SENDER), eq(RECIPIENT), eq(new BigInteger(ONE_BAN_RAW)), anyString());
    }

    @Test
    void sendTransaction_validatesBeforeCallingSigner() throws Exception
    {
        WalletSigner signer = mock(WalletSigner.class);
        RPC delegating = rpcWith(signer);

        assertThrows(TransactionError.class, () -> delegating.sendTransaction(SENDER, RECIPIENT, 0.0));
        assertThrows(TransactionError.class, () -> delegating.sendTransaction(SENDER, RECIPIENT, Double.NaN));
        assertThrows(TransactionError.class, () -> delegating.sendTransaction("nope", RECIPIENT, 1.0));
        assertThrows(TransactionError.class, () -> delegating.sendTransaction(SENDER, RECIPIENT, 1e-40));
        verifyNoInteractions(signer);
    }

    @Test
    void sendTransaction_propagatesSignerErrors_includingUnknownOutcome() throws Exception
    {
        WalletSigner signer = mock(WalletSigner.class);
        when(signer.send(anyString(), anyString(), anyString(), any(BigInteger.class), anyString()))
                .thenThrow(new UnknownOutcomeError());

        TransactionError thrown = assertThrows(TransactionError.class,
                () -> rpcWith(signer).sendTransaction(SENDER, RECIPIENT, 1.0));

        assertInstanceOf(UnknownOutcomeError.class, thrown);
        assertTrue(thrown.getUserError().toLowerCase().contains("unknown"));
    }

    @Test
    void receiveBlock_delegatesToSigner() throws Exception
    {
        WalletSigner signer = mock(WalletSigner.class);
        when(signer.receive("W-ID", SENDER, "BLOCK")).thenReturn("RECEIVE-HASH");

        assertEquals("RECEIVE-HASH", rpcWith(signer).receiveBlock("W-ID", SENDER, "BLOCK"));
        verifyNoInteractions(http);
    }

    @Test
    void receiveBlock_twoArgOverload_usesConfiguredWalletId_viaSigner() throws Exception
    {
        WalletSigner signer = mock(WalletSigner.class);
        when(signer.receive("WALLET_ID", SENDER, "BLOCK")).thenReturn("RECEIVE-HASH");

        assertEquals("RECEIVE-HASH", rpcWith(signer).receiveBlock(SENDER, "BLOCK"));
    }

    @Test
    void receiveBlock_validatesAccountBeforeCallingSigner()
    {
        WalletSigner signer = mock(WalletSigner.class);

        assertThrows(TransactionError.class, () -> rpcWith(signer).receiveBlock("W-ID", "nope", "BLOCK"));
        verifyNoInteractions(signer);
    }

    @Test
    void setRepresentative_delegatesToSigner() throws Exception
    {
        WalletSigner signer = mock(WalletSigner.class);
        when(signer.setRepresentative("W-ID", SENDER, RECIPIENT)).thenReturn("CHANGE-HASH");

        assertEquals("CHANGE-HASH", rpcWith(signer).setRepresentative(SENDER, RECIPIENT, "W-ID"));
    }

    @Test
    void setRepresentative_validatesBeforeCallingSigner()
    {
        WalletSigner signer = mock(WalletSigner.class);

        assertThrows(TransactionError.class, () -> rpcWith(signer).setRepresentative(SENDER, "nope", "W-ID"));
        verifyNoInteractions(signer);
    }

    @Test
    void accountCreate_delegatesToSigner_andMapsNullToSentinel()
    {
        WalletSigner signer = mock(WalletSigner.class);
        when(signer.accountCreate("WALLET_ID", 7)).thenReturn(RECIPIENT);
        when(signer.accountCreate("BANK_ID", 3)).thenReturn(null);
        RPC delegating = rpcWith(signer);

        assertEquals(RECIPIENT, delegating.accountCreate(7));
        assertEquals(RPC.ACCOUNT_CREATION_FAILED, delegating.accountCreate(3, "BANK_ID"));
        verifyNoInteractions(http);
    }

    @Test
    void walletStatus_mapsSignerStatus_forPlayerAndBankWallets()
    {
        WalletSigner signer = mock(WalletSigner.class);
        when(signer.walletStatus(WalletSigner.Wallet.PLAYER)).thenReturn(WalletSigner.Status.EXISTS);
        when(signer.walletStatus(WalletSigner.Wallet.BANK)).thenReturn(WalletSigner.Status.MISSING);
        RPC delegating = rpcWith(signer);

        assertEquals(RPC.WalletStatus.EXISTS, delegating.walletStatus());
        assertTrue(delegating.wallet_exists());
        assertEquals(RPC.WalletStatus.MISSING, delegating.bankWalletStatus());
        assertFalse(delegating.bankWalletExists());

        when(signer.walletStatus(WalletSigner.Wallet.PLAYER)).thenReturn(WalletSigner.Status.UNREACHABLE);
        assertEquals(RPC.WalletStatus.UNREACHABLE, delegating.walletStatus());
    }

    @Test
    void walletCreate_nodeSigner_persistsReturnedWalletId()
    {
        WalletSigner signer = mock(WalletSigner.class);
        when(signer.isLocal()).thenReturn(false);
        when(signer.createWallet(WalletSigner.Wallet.PLAYER)).thenReturn("NODE-ID");
        when(configEngine.save()).thenReturn(true);

        assertTrue(rpcWith(signer).walletCreate());
        verify(configEngine).setWalletId("NODE-ID");
        verify(configEngine).save();
    }

    @Test
    void walletCreate_localSigner_isANoOpThatSucceeds_andStoresNothing()
    {
        WalletSigner signer = mock(WalletSigner.class);
        when(signer.isLocal()).thenReturn(true);
        when(signer.createWallet(WalletSigner.Wallet.PLAYER)).thenReturn(WalletSigner.LIGHT_PLAYER_WALLET_ID);
        when(signer.createWallet(WalletSigner.Wallet.BANK)).thenReturn(WalletSigner.LIGHT_BANK_WALLET_ID);
        RPC light = rpcWith(signer);

        assertTrue(light.walletCreate());
        assertTrue(light.bankWalletCreate());

        verify(configEngine, never()).setWalletId(anyString());
        verify(configEngine, never()).setBankWalletId(anyString());
        verify(configEngine, never()).save();
        verifyNoInteractions(http); // the seed (or anything else) is never sent over the network
    }

    @Test
    void walletCreate_returnsFalse_whenSignerFails()
    {
        WalletSigner signer = mock(WalletSigner.class);
        when(signer.createWallet(any())).thenReturn(null);

        assertFalse(rpcWith(signer).walletCreate());
        assertFalse(rpcWith(signer).bankWalletCreate());
        verify(configEngine, never()).save();
    }

    @Test
    void readOnlyCalls_stillUseTheNodeTransport_andNeverTheSigner() throws Exception
    {
        WalletSigner signer = mock(WalletSigner.class);
        stubResponse("{\"balance\":\"" + ONE_BAN_RAW + "\"}");

        assertEquals(1.0, rpcWith(signer).getBalance(SENDER));

        verify(http).post(anyString(), anyString());
        verifyNoInteractions(signer);
    }

    @Test
    void precomputeWork_delegatesToSigner_withTheConfiguredWallet()
    {
        WalletSigner signer = mock(WalletSigner.class);

        rpcWith(signer).precomputeWork(SENDER);

        verify(signer).precomputeWork("WALLET_ID", SENDER);
    }

    @Test
    void precomputeWork_ignoresInvalidAddresses_andNeverThrows()
    {
        WalletSigner signer = mock(WalletSigner.class);
        doThrow(new IllegalStateException("boom")).when(signer).precomputeWork(anyString(), anyString());

        RPC delegating = rpcWith(signer);
        delegating.precomputeWork("nope");
        verifyNoInteractions(signer);

        assertDoesNotThrow(() -> delegating.precomputeWork("W", SENDER));
    }
}
