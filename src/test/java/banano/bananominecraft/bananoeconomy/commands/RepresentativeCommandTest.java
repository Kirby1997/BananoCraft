package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.i18n.I18n;
import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.services.RepresentativeService;
import banano.bananominecraft.bananoeconomy.trackers.TaskTracker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import static org.mockito.Mockito.*;

class RepresentativeCommandTest
{
    private static final String WALLET = "ban_3t6k35gi95xu6tergt6p69ck76ogmitsa8mnijtpxm9fkcm736xtoncuohr3";
    private static final String REP    = "ban_1t6k35gi95xu6tergt6p69ck76ogmitsa8mnijtpxm9fkcm736xtoncuohr3";

    private ServerMock server;
    private PluginMock plugin;

    private EconomyFuncs economyFuncs;
    private TaskTracker taskTracker;
    private I18n i18n;
    private RepresentativeService representativeService;
    private RepresentativeCommand command;
    private PlayerMock player;

    @BeforeEach
    void setUp()
    {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();

        economyFuncs = mock(EconomyFuncs.class);
        taskTracker  = mock(TaskTracker.class);
        representativeService = mock(RepresentativeService.class);
        // Real I18n: never returns null, so PlayerMock (which rejects null messages) is happy.
        i18n = new I18n(getClass().getClassLoader());

        when(economyFuncs.getWallet(any())).thenReturn(WALLET);
        when(economyFuncs.isFrozen(any())).thenReturn(false);

        command = new RepresentativeCommand(plugin, economyFuncs, taskTracker, i18n, representativeService);
        player = server.addPlayer("Alice");
    }

    @AfterEach
    void tearDown()
    {
        MockBukkit.unmock();
    }

    private void runAndDrain(String... args)
    {
        command.onCommand(player, null, "representative", args);
        server.getScheduler().waitAsyncTasksFinished();
    }

    // -------------------------------------------------------------------------
    // Dispatch / frozen / usage
    // -------------------------------------------------------------------------

    @Test
    void frozenPlayer_cannotUseAnySubcommand()
    {
        when(economyFuncs.isFrozen(player)).thenReturn(true);

        runAndDrain("set", REP);

        verifyNoInteractions(representativeService);
    }

    @Test
    void missingSubcommand_showsUsage()
    {
        runAndDrain();

        verifyNoInteractions(representativeService);
    }

    @Test
    void unknownSubcommand_showsUsage()
    {
        runAndDrain("bogus");

        verifyNoInteractions(representativeService);
    }

    // -------------------------------------------------------------------------
    // set
    // -------------------------------------------------------------------------

    @Test
    void set_withMissingAddress_showsUsage()
    {
        runAndDrain("set");

        verifyNoInteractions(representativeService);
    }

    @Test
    void set_withTooManyArgs_showsUsage()
    {
        runAndDrain("set", REP, "extra-arg");

        verifyNoInteractions(representativeService);
    }

    @Test
    void set_withExplicitAddress_setsRepresentative()
    {
        when(representativeService.setRepresentative(WALLET, REP))
                .thenReturn(RepresentativeService.RepresentativeResult.succeeded());

        runAndDrain("set", REP);

        verify(representativeService).setRepresentative(WALLET, REP);
        verify(representativeService, never()).setRandomRepresentative(anyString());
    }

    @Test
    void set_withRandomKeyword_picksRandomRepresentative()
    {
        when(representativeService.setRandomRepresentative(WALLET))
                .thenReturn(RepresentativeService.RepresentativeResult.succeeded());

        runAndDrain("set", "random");

        verify(representativeService).setRandomRepresentative(WALLET);
        verify(representativeService, never()).setRepresentative(anyString(), anyString());
    }

    @Test
    void set_randomKeyword_isCaseInsensitive()
    {
        when(representativeService.setRandomRepresentative(WALLET))
                .thenReturn(RepresentativeService.RepresentativeResult.succeeded());

        runAndDrain("SET", "RANDOM");

        verify(representativeService).setRandomRepresentative(WALLET);
    }

    @Test
    void set_serviceFailure_isHandledGracefully()
    {
        when(representativeService.setRepresentative(WALLET, REP))
                .thenReturn(RepresentativeService.RepresentativeResult.failed("Invalid representative address."));

        runAndDrain("set", REP);

        verify(representativeService).setRepresentative(WALLET, REP);
    }

    // -------------------------------------------------------------------------
    // get
    // -------------------------------------------------------------------------

    @Test
    void get_returnsCurrentRepresentative()
    {
        when(representativeService.getCurrentRepresentative(WALLET))
                .thenReturn(RepresentativeService.RepresentativeInfoResult.succeeded(REP));

        runAndDrain("get");

        verify(representativeService).getCurrentRepresentative(WALLET);
    }

    @Test
    void get_isCaseInsensitive()
    {
        when(representativeService.getCurrentRepresentative(WALLET))
                .thenReturn(RepresentativeService.RepresentativeInfoResult.succeeded(REP));

        runAndDrain("GET");

        verify(representativeService).getCurrentRepresentative(WALLET);
    }

    @Test
    void get_handlesLookupFailureGracefully()
    {
        when(representativeService.getCurrentRepresentative(WALLET))
                .thenReturn(RepresentativeService.RepresentativeInfoResult.failed(
                        "Could not retrieve representative information."));

        runAndDrain("get");

        verify(representativeService).getCurrentRepresentative(WALLET);
    }

    @Test
    void get_ignoresExtraArgs()
    {
        when(representativeService.getCurrentRepresentative(WALLET))
                .thenReturn(RepresentativeService.RepresentativeInfoResult.succeeded(REP));

        runAndDrain("get", "extra-arg");

        verify(representativeService).getCurrentRepresentative(WALLET);
    }
}
