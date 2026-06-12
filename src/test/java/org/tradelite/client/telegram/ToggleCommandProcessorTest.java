package org.tradelite.client.telegram;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.common.FeatureToggle;
import org.tradelite.service.FeatureToggleService;

@ExtendWith(MockitoExtension.class)
class ToggleCommandProcessorTest {

    @Mock private FeatureToggleService featureToggleService;
    @Mock private TelegramGateway telegramGateway;

    private ToggleCommandProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ToggleCommandProcessor(featureToggleService, telegramGateway);
    }

    @Test
    void processCommand_showAll_sendsAllTogglesStatus() {
        when(featureToggleService.isEnabled(FeatureToggle.FINNHUB_PRICE_COLLECTION))
                .thenReturn(true);
        when(featureToggleService.isEnabled(FeatureToggle.EMA_REPORT)).thenReturn(true);
        when(featureToggleService.isEnabled(FeatureToggle.VFI_REPORT)).thenReturn(false);
        when(featureToggleService.isEnabled(FeatureToggle.PULLBACK_BUY_ALERT)).thenReturn(true);
        when(featureToggleService.isEnabled(FeatureToggle.EARNINGS_CALENDAR_ALERT))
                .thenReturn(true);
        when(featureToggleService.isEnabled(FeatureToggle.ACCUMULATION_DETECTION))
                .thenReturn(false);
        when(featureToggleService.isEnabled(FeatureToggle.RSI_REPORT)).thenReturn(true);
        when(featureToggleService.isEnabled(FeatureToggle.YAHOO_INTRADAY_PRICE_FETCH))
                .thenReturn(true);

        ToggleCommand command = new ToggleCommand(null, null);
        processor.processCommand(command);

        verify(telegramGateway)
                .sendMessage(
                        """
                        Feature Toggles:
                        finnhubPriceCollection: ON
                        emaReport: ON
                        vfiReport: OFF
                        pullbackBuyAlert: ON
                        earningsCalendarAlert: ON
                        accumulationDetection: OFF
                        rsiReport: ON
                        yahooIntradayPriceFetch: ON""");
    }

    @Test
    void processCommand_enableValidFeature_callsSetToggleAndSendsConfirmation() {
        ToggleCommand command = new ToggleCommand("emaReport", true);
        processor.processCommand(command);

        verify(featureToggleService).setToggle(FeatureToggle.EMA_REPORT, true);
        verify(telegramGateway).sendMessage("emaReport: ON");
    }

    @Test
    void processCommand_disableValidFeature_callsSetToggleAndSendsConfirmation() {
        ToggleCommand command = new ToggleCommand("vfiReport", false);
        processor.processCommand(command);

        verify(featureToggleService).setToggle(FeatureToggle.VFI_REPORT, false);
        verify(telegramGateway).sendMessage("vfiReport: OFF");
    }

    @Test
    void processCommand_unknownFeature_sendsErrorMessage() {
        ToggleCommand command = new ToggleCommand("nonExistentFeature", true);
        processor.processCommand(command);

        verify(featureToggleService, never()).setToggle(any(), anyBoolean());
        verify(telegramGateway).sendMessage("Unknown feature: nonExistentFeature");
    }

    @Test
    void canProcess_returnsTrue_forToggleCommand() {
        assertThat(processor.canProcess(new ToggleCommand(null, null)), is(true));
    }

    @Test
    void canProcess_returnsFalse_forOtherCommands() {
        assertThat(processor.canProcess(new ShowCommand("all")), is(false));
    }
}
