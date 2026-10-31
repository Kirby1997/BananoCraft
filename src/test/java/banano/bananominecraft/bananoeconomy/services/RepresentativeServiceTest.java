package banano.bananominecraft.bananoeconomy.services;

import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import banano.bananominecraft.bananoeconomy.io.RPC;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RepresentativeServiceTest
{
    // Two genuinely Validator-valid ban_ addresses (first char 1/3, allowed alphabet),
    // matching the same constants used in RPCTest.
    private static final String ACCOUNT = "ban_3t6k35gi95xu6tergt6p69ck76ogmitsa8mnijtpxm9fkcm736xtoncuohr3";
    private static final String REP     = "ban_1t6k35gi95xu6tergt6p69ck76ogmitsa8mnijtpxm9fkcm736xtoncuohr3";
    private static final String WALLET  = "WALLET_ID";

    private RPC rpc;
    private ConfigEngine configEngine;
    private RepresentativeService service;

    @BeforeEach
    void setUp()
    {
        rpc = mock(RPC.class);
        configEngine = mock(ConfigEngine.class);
        when(configEngine.getRepresentatives()).thenReturn(List.of());
        service = new RepresentativeService(rpc, configEngine);
    }

    @Test
    void getCandidateRepresentatives_prefersCuratedListWhenConfigured()
    {
        when(configEngine.getRepresentatives()).thenReturn(List.of(REP));

        assertEquals(List.of(REP), service.getCandidateRepresentatives());
        verify(rpc, never()).representativesOnline();
    }

    @Test
    void getCandidateRepresentatives_fallsBackToOnlineList_whenNoCuratedList()
    {
        when(rpc.representativesOnline()).thenReturn(List.of("ban_online1", "ban_online2"));

        assertEquals(List.of("ban_online1", "ban_online2"), service.getCandidateRepresentatives());
    }

    @Test
    void getCandidateRepresentatives_filtersOutMalformedCuratedEntries()
    {
        when(configEngine.getRepresentatives()).thenReturn(List.of(REP, "not-a-valid-address"));

        assertEquals(List.of(REP), service.getCandidateRepresentatives());
        verify(rpc, never()).representativesOnline();
    }

    @Test
    void getCandidateRepresentatives_fallsBackToOnlineList_whenCuratedListIsEntirelyMalformed()
    {
        when(configEngine.getRepresentatives()).thenReturn(List.of("not-a-valid-address"));
        when(rpc.representativesOnline()).thenReturn(List.of("ban_online1"));

        assertEquals(List.of("ban_online1"), service.getCandidateRepresentatives());
    }

    @Test
    void setRepresentative_success() throws TransactionError
    {
        when(rpc.setRepresentative(ACCOUNT, REP, WALLET)).thenReturn("BLOCK");

        RepresentativeService.RepresentativeResult result = service.setRepresentative(ACCOUNT, REP, WALLET);

        assertTrue(result.success());
        assertNull(result.userError());
    }

    @Test
    void setRepresentative_rejectsInvalidAddress_withoutCallingRpc() throws TransactionError
    {
        RepresentativeService.RepresentativeResult result =
                service.setRepresentative(ACCOUNT, "not-an-address", WALLET);

        assertFalse(result.success());
        assertNotNull(result.userError());
        verify(rpc, never()).setRepresentative(anyString(), anyString(), anyString());
    }

    @Test
    void setRepresentative_transactionError_isReportedAsFailure() throws TransactionError
    {
        when(rpc.setRepresentative(ACCOUNT, REP, WALLET))
                .thenThrow(new TransactionError("Representative not found"));

        RepresentativeService.RepresentativeResult result = service.setRepresentative(ACCOUNT, REP, WALLET);

        assertFalse(result.success());
        assertEquals("Representative not found", result.userError());
    }

    @Test
    void setRandomRepresentative_picksFromCandidates_andDelegates() throws TransactionError
    {
        when(configEngine.getRepresentatives()).thenReturn(List.of(REP));
        when(rpc.setRepresentative(ACCOUNT, REP, WALLET)).thenReturn("BLOCK");

        RepresentativeService.RepresentativeResult result = service.setRandomRepresentative(ACCOUNT, WALLET);

        assertTrue(result.success());
        verify(rpc).setRepresentative(ACCOUNT, REP, WALLET);
    }

    @Test
    void setRandomRepresentative_failsCleanly_whenNoCandidatesAvailable()
    {
        when(rpc.representativesOnline()).thenReturn(List.of());

        RepresentativeService.RepresentativeResult result = service.setRandomRepresentative(ACCOUNT, WALLET);

        assertFalse(result.success());
        assertNotNull(result.userError());
    }

    @Test
    void getCurrentRepresentative_returnsAddress_onSuccess()
    {
        when(rpc.getRepresentative(ACCOUNT)).thenReturn(REP);

        RepresentativeService.RepresentativeInfoResult result = service.getCurrentRepresentative(ACCOUNT);

        assertTrue(result.success());
        assertEquals(REP, result.representative());
        assertNull(result.userError());
    }

    @Test
    void getCurrentRepresentative_failsCleanly_whenRpcReturnsNull()
    {
        when(rpc.getRepresentative(ACCOUNT)).thenReturn(null);

        RepresentativeService.RepresentativeInfoResult result = service.getCurrentRepresentative(ACCOUNT);

        assertFalse(result.success());
        assertNull(result.representative());
        assertNotNull(result.userError());
    }
}
