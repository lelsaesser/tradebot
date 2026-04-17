package org.tradelite.client.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tradelite.repository.OhlcvRepository;
import org.tradelite.service.OhlcvFetcher;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataResetCommandProcessor implements TelegramCommandProcessor<DataResetCommand> {

    private final OhlcvRepository ohlcvRepository;
    private final OhlcvFetcher ohlcvFetcher;
    private final TelegramGateway telegramGateway;

    @Override
    public boolean canProcess(TelegramCommand command) {
        return command instanceof DataResetCommand;
    }

    @Override
    public void processCommand(DataResetCommand command) {
        String ticker = command.getTicker().toUpperCase();
        try {
            int deleted = ohlcvRepository.deleteBySymbol(ticker);
            int fetched = ohlcvFetcher.backfillSymbol(ticker);
            telegramGateway.sendMessage(
                    String.format(
                            "Data reset complete for %s%nDeleted %d records, re-fetched %d"
                                    + " records.",
                            ticker, deleted, fetched));
        } catch (Exception e) {
            log.error("Data reset failed for {}", ticker, e);
            telegramGateway.sendMessage(
                    String.format("Data reset failed for %s: %s", ticker, e.getMessage()));
        }
    }
}
