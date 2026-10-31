package banano.bananominecraft.bananoeconomy.services;

import banano.bananominecraft.bananoeconomy.io.RPC;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NodeInfoServiceTest
{
    private RPC rpc;
    private NodeInfoService service;

    @BeforeEach
    void setUp()
    {
        rpc = mock(RPC.class);
        service = new NodeInfoService(rpc);
    }

    @Test
    void gather_assemblesSnapshotFromRpc() throws Exception
    {
        when(rpc.getBlockCount()).thenReturn(List.of("100", "3"));
        when(rpc.getMasterWallet()).thenReturn("ban_master");
        when(rpc.getURL()).thenReturn(new URL("http://node:7072"));
        when(rpc.getBalance("ban_master")).thenReturn(42.0);

        NodeInfoService.NodeInfo info = service.gather();

        assertEquals("100", info.checkedBlocks());
        assertEquals("3", info.uncheckedBlocks());
        assertEquals("http://node:7072", info.nodeUrl());
        assertEquals("ban_master", info.masterWallet());
        assertEquals(42.0, info.balance());
    }

    @Test
    void gather_propagatesUrlError() throws Exception
    {
        when(rpc.getBlockCount()).thenReturn(List.of("100", "3"));
        when(rpc.getMasterWallet()).thenReturn("ban_master");
        when(rpc.getURL()).thenThrow(new Exception("bad url"));

        assertThrows(Exception.class, () -> service.gather());
    }
}
