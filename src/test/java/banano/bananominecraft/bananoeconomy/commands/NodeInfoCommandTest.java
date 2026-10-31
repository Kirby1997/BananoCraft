package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.io.RPC;
import banano.bananominecraft.bananoeconomy.trackers.TaskTracker;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.net.URL;
import java.util.List;

import static org.mockito.Mockito.*;

class NodeInfoCommandTest
{
    private ServerMock server;
    private PluginMock plugin;
    private RPC rpc;
    private TaskTracker taskTracker;
    private NodeInfoCommand command;

    @BeforeEach
    void setUp() throws Exception
    {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        rpc = mock(RPC.class);
        taskTracker = mock(TaskTracker.class);

        when(rpc.getBlockCount()).thenReturn(List.of("100", "2"));
        when(rpc.getURL()).thenReturn(new URL("http://node:7072"));
        when(rpc.getMasterWallet()).thenReturn("ban_master");
        when(rpc.getBalance("ban_master")).thenReturn(5.0);

        command = new NodeInfoCommand(plugin, rpc, taskTracker);
    }

    @AfterEach
    void tearDown()
    {
        MockBukkit.unmock();
    }

    @Test
    void opPlayer_receivesNodeInfo()
    {
        PlayerMock player = server.addPlayer("Admin");
        player.setOp(true);

        command.onCommand(player, null, "nodeinfo", new String[0]);
        server.getScheduler().waitAsyncTasksFinished();

        verify(rpc).getBlockCount();
        verify(rpc).getBalance("ban_master");
    }

    @Test
    void nonOpPlayer_stillReceivesBlockCount()
    {
        PlayerMock player = server.addPlayer("Peasant"); // not op

        command.onCommand(player, null, "nodeinfo", new String[0]);
        server.getScheduler().waitAsyncTasksFinished();

        verify(rpc).getBlockCount();
    }

    @Test
    void consoleSender_receivesNodeInfo()
    {
        ConsoleCommandSender console = server.getConsoleSender();

        command.onCommand(console, null, "nodeinfo", new String[0]);
        server.getScheduler().waitAsyncTasksFinished();

        verify(rpc).getBlockCount();
        verify(rpc, atLeastOnce()).getMasterWallet();
    }

    @Test
    void playerWithIpPermission_seesNodeUrlWithoutCredentials() throws Exception
    {
        when(rpc.getURL()).thenReturn(new URL("http://user:secret@node:7072"));
        PlayerMock player = server.addPlayer("Viewer");
        player.addAttachment(plugin, NodeInfoCommand.PERMISSION_NODE_IP, true);

        command.onCommand(player, null, "nodeinfo", new String[0]);
        server.getScheduler().waitAsyncTasksFinished();

        String first = player.nextMessage();
        org.junit.jupiter.api.Assertions.assertNotNull(first);
        org.junit.jupiter.api.Assertions.assertTrue(first.contains("http://node:7072"), first);
        org.junit.jupiter.api.Assertions.assertFalse(first.contains("secret"), first);
    }

    @Test
    void playerWithoutIpPermission_doesNotSeeNodeUrl()
    {
        PlayerMock player = server.addPlayer("Peasant");
        player.addAttachment(plugin, NodeInfoCommand.PERMISSION_NODE_IP, false);

        command.onCommand(player, null, "nodeinfo", new String[0]);
        server.getScheduler().waitAsyncTasksFinished();

        String message;
        while ((message = player.nextMessage()) != null)
        {
            org.junit.jupiter.api.Assertions.assertFalse(message.contains("node:7072"), message);
        }
    }
}
